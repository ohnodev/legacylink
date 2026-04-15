package dev.ohno.legacylink.mapping;

import dev.ohno.legacylink.LegacyLinkConstants;
import dev.ohno.legacylink.LegacyLinkMod;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side 26.2 → 26.1 numeric remaps used on the wire. Same role as Via’s per-protocol mapping tables, but
 * scoped to this version pair: explicit {@link LegacyLinkConstants} entries plus range guards so unknown high ids
 * never reach a 26.1 client.
 */
public final class RegistryRemapper {

    private static final Gson GSON = new Gson();
    private static final String LEGACY_STATE_MAP_OVERRIDE = System.getProperty("legacylink.legacyStateMap");
    private static final String SNAPSHOT_STATE_MAP_OVERRIDE = System.getProperty("legacylink.snapshotStateMap");
    private static final String LEGACY_ITEM_MAP_OVERRIDE = System.getProperty("legacylink.legacyItemMap");
    private static final Int2IntMap BLOCK_STATE_REMAP = new Int2IntOpenHashMap();
    private static final Int2IntMap ITEM_REMAP = new Int2IntOpenHashMap();
    private static int[] blockStateToLegacy = new int[0];
    private static Map<String, Integer> legacyStateIdByString = Map.of();
    private static Map<String, String> snapshotStateById = Map.of();
    private static Map<String, Integer> legacyItemIdByString = Map.of();

    /** Legacy wire-id fallback (mapped stone id) for 26.1 clients. */
    private static int fallbackBlockStateId = 1;
    private static final Item FALLBACK_ITEM = Items.STONE;

    public static void buildMappings() {
        legacyStateIdByString = loadLegacyStateIdMap();
        snapshotStateById = loadSnapshotStateByIdMap();
        legacyItemIdByString = loadLegacyItemIdMap();

        BLOCK_STATE_REMAP.clear();
        ITEM_REMAP.clear();

        int stateRegistrySize = Block.BLOCK_STATE_REGISTRY.size();
        int[] legacyTable = new int[stateRegistrySize];

        int stoneStateId = Block.BLOCK_STATE_REGISTRY.getId(Blocks.STONE.defaultBlockState());
        if (stoneStateId < 0 || stoneStateId >= legacyTable.length) {
            stoneStateId = 1;
        }
        fallbackBlockStateId = stoneStateId;

        BLOCK_STATE_REMAP.clear();
        int legacyMaxStateId = LegacyLinkConstants.MAX_26_1_BLOCKSTATE_ID;
        for (int stateId = 0; stateId < legacyTable.length; stateId++) {
            legacyTable[stateId] = stoneStateId;
        }

        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                int stateId = Block.BLOCK_STATE_REGISTRY.getId(state);
                String stateKey = snapshotStateById.getOrDefault(Integer.toString(stateId), state.toString());
                Integer mappedLegacyId = legacyStateIdByString.get(stateKey);
                int target = mappedLegacyId == null ? stoneStateId : mappedLegacyId;
                if (target < 0 || target > legacyMaxStateId) {
                    target = stoneStateId;
                }
                legacyTable[stateId] = target;
                if (target != stateId) {
                    BLOCK_STATE_REMAP.put(stateId, target);
                }
            }
        }

        blockStateToLegacy = legacyTable;

        int stoneItemId = legacyItemIdByString.getOrDefault("minecraft:stone", Item.getId(Items.STONE));
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            int serverItemId = Item.getId(item);
            String key = itemId == null ? "" : itemId.toString();
            int mapped = legacyItemIdByString.getOrDefault(key, stoneItemId);
            if (mapped < 0 || mapped > LegacyLinkConstants.MAX_26_1_ITEM_ID) {
                mapped = stoneItemId;
            }
            if (mapped != serverItemId) {
                ITEM_REMAP.put(serverItemId, mapped);
            }
        }

        LegacyItemIdTable.rebuild();
        LegacyAttributeWireTable.rebuild();
        LegacyEntityTypeWireRemapper.rebuild();

        LegacyLinkMod.LOGGER.info("[LegacyLink] Registry mappings built: {} block states, {} items",
                BLOCK_STATE_REMAP.size(), ITEM_REMAP.size());
    }

    public static int remapBlockState(int stateId) {
        int[] table = blockStateToLegacy;
        if (stateId < 0 || stateId >= table.length) {
            return fallbackBlockStateId;
        }
        int mapped = table[stateId];
        if (mapped < 0 || mapped > LegacyLinkConstants.MAX_26_1_BLOCKSTATE_ID) {
            return fallbackBlockStateId;
        }
        return mapped;
    }

    /**
     * Map a 26.2 item registry id to one the legacy client understands: known mod replacements first, otherwise
     * pass-through if {@code <= MAX_26_1_ITEM_ID}, else {@link Items#STONE}.
     */
    public static int remapItem(int itemId) {
        int explicit = ITEM_REMAP.getOrDefault(itemId, itemId);
        if (explicit != itemId) {
            return explicit;
        }
        if (itemId > LegacyLinkConstants.MAX_26_1_ITEM_ID) {
            return Item.getId(FALLBACK_ITEM);
        }
        return itemId;
    }

    public static boolean needsBlockRemap(int stateId) {
        int[] table = blockStateToLegacy;
        if (stateId < 0 || stateId >= table.length) {
            return true;
        }
        return table[stateId] != stateId;
    }

    public static int blockStateRemapCount() {
        return BLOCK_STATE_REMAP.size();
    }

    public static int itemRemapCount() {
        return ITEM_REMAP.size();
    }

    private RegistryRemapper() {}

    static int legacyItemIdByRegistryKeyOrFallback(String registryId) {
        int stone = legacyItemIdByString.getOrDefault("minecraft:stone", Item.getId(Items.STONE));
        int mapped = legacyItemIdByString.getOrDefault(registryId, stone);
        if (mapped < 0 || mapped > LegacyLinkConstants.MAX_26_1_ITEM_ID) {
            return stone;
        }
        return mapped;
    }

    public static boolean hasLegacyItemRegistryKey(String registryId) {
        return legacyItemIdByString.containsKey(registryId);
    }

    private static Map<String, Integer> loadLegacyStateIdMap() {
        Path path = resolveExistingPath(LEGACY_STATE_MAP_OVERRIDE, List.of(
                "reaper-ac/mapping-artifacts/26.1.2-stable/state-to-id-26.1.2-stable.json",
                "../reaper-ac/mapping-artifacts/26.1.2-stable/state-to-id-26.1.2-stable.json",
                "../../reaper-ac/mapping-artifacts/26.1.2-stable/state-to-id-26.1.2-stable.json"
        ));
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Type type = new TypeToken<LinkedHashMap<String, Integer>>() {}.getType();
            Map<String, Integer> map = GSON.fromJson(json, type);
            if (map == null || map.isEmpty()) {
                throw new IllegalStateException("[LegacyLink] Legacy state map is empty: " + path);
            }
            return map;
        } catch (IOException e) {
            throw new IllegalStateException("[LegacyLink] Failed reading legacy state map from " + path, e);
        }
    }

    private static Map<String, String> loadSnapshotStateByIdMap() {
        Path path = resolveExistingPath(SNAPSHOT_STATE_MAP_OVERRIDE, List.of(
                "reaper-ac/mapping-artifacts/26.2-snapshot-3/id-to-state-26.2-snapshot-3.json",
                "../reaper-ac/mapping-artifacts/26.2-snapshot-3/id-to-state-26.2-snapshot-3.json",
                "../../reaper-ac/mapping-artifacts/26.2-snapshot-3/id-to-state-26.2-snapshot-3.json"
        ));
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Type type = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
            Map<String, String> map = GSON.fromJson(json, type);
            if (map == null || map.isEmpty()) {
                throw new IllegalStateException("[LegacyLink] Snapshot id->state map is empty: " + path);
            }
            return map;
        } catch (IOException e) {
            throw new IllegalStateException("[LegacyLink] Failed reading snapshot id->state map from " + path, e);
        }
    }

    private static Map<String, Integer> loadLegacyItemIdMap() {
        Path path = resolveExistingPath(LEGACY_ITEM_MAP_OVERRIDE, List.of(
                "reaper-ac/mapping-artifacts/26.1.2-stable/item-protocol-map-26.1.2-stable.csv",
                "../reaper-ac/mapping-artifacts/26.1.2-stable/item-protocol-map-26.1.2-stable.csv",
                "../../reaper-ac/mapping-artifacts/26.1.2-stable/item-protocol-map-26.1.2-stable.csv"
        ));
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            Map<String, Integer> out = new LinkedHashMap<>();
            for (String line : lines) {
                if (line == null || line.isBlank() || line.startsWith("item,")) {
                    continue;
                }
                int comma = line.lastIndexOf(',');
                if (comma <= 0 || comma >= line.length() - 1) {
                    continue;
                }
                String key = line.substring(0, comma).trim();
                String idRaw = line.substring(comma + 1).trim();
                if (!idRaw.chars().allMatch(Character::isDigit)) {
                    continue;
                }
                out.put(key, Integer.parseInt(idRaw));
            }
            if (out.isEmpty()) {
                throw new IllegalStateException("[LegacyLink] Legacy item map is empty: " + path);
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("[LegacyLink] Failed reading legacy item map from " + path, e);
        }
    }

    private static Path resolveExistingPath(String override, List<String> candidates) {
        if (override != null && !override.isBlank()) {
            Path p = Path.of(override);
            if (Files.exists(p)) {
                return p;
            }
            throw new IllegalStateException("[LegacyLink] Mapping override path does not exist: " + override);
        }
        for (String candidate : candidates) {
            Path p = Path.of(candidate);
            if (Files.exists(p)) {
                return p;
            }
        }
        throw new IllegalStateException("[LegacyLink] Could not locate required mapping artifact. Tried: " + candidates);
    }
}
