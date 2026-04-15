package dev.ohno.legacylink.mapping;

import dev.ohno.legacylink.LegacyLinkConstants;
import dev.ohno.legacylink.LegacyLinkMod;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side 26.2 → 26.1 numeric remaps used on the wire. Same role as Via’s per-protocol mapping tables:
 * all remaps are dump-table driven with exact legacy ID membership checks (no max-threshold gating).
 */
public final class RegistryRemapper {

    private static final Gson GSON = new Gson();
    private static final String LEGACY_STATE_MAP_RESOURCE = "/legacylink/mappings/legacy-state-to-id-26.1.2.json";
    private static final String SNAPSHOT_STATE_MAP_RESOURCE = "/legacylink/mappings/snapshot-id-to-state-26.2-snapshot-3.json";
    private static final String LEGACY_ITEM_MAP_RESOURCE = "/legacylink/mappings/legacy-item-protocol-map-26.1.2.csv";
    private static final String LEGACY_STATE_MAP_OVERRIDE = System.getProperty("legacylink.legacyStateMap");
    private static final String SNAPSHOT_STATE_MAP_OVERRIDE = System.getProperty("legacylink.snapshotStateMap");
    private static final String LEGACY_ITEM_MAP_OVERRIDE = System.getProperty("legacylink.legacyItemMap");
    private static final Int2IntMap BLOCK_STATE_REMAP = new Int2IntOpenHashMap();
    private static final Int2IntMap ITEM_REMAP = new Int2IntOpenHashMap();
    private static int[] blockStateToLegacy = new int[0];
    private static int[] itemToLegacy = new int[0];
    private static IntSet validLegacyStateIds = IntSets.EMPTY_SET;
    private static IntSet validLegacyItemIds = IntSets.EMPTY_SET;
    private static Map<String, Integer> legacyStateIdByString = Map.of();
    private static Map<String, String> snapshotStateById = Map.of();
    private static Map<String, Integer> legacyItemIdByString = Map.of();

    public static void buildMappings() {
        legacyStateIdByString = loadLegacyStateIdMap();
        snapshotStateById = loadSnapshotStateByIdMap();
        legacyItemIdByString = loadLegacyItemIdMap();

        BLOCK_STATE_REMAP.clear();
        ITEM_REMAP.clear();

        int stateRegistrySize = Block.BLOCK_STATE_REGISTRY.size();
        int[] legacyTable = new int[stateRegistrySize];
        IntSet validStateIds = new IntOpenHashSet(legacyStateIdByString.size());

        int stoneStateId = legacyStateIdByString.getOrDefault(Blocks.STONE.defaultBlockState().toString(), 1);
        for (Integer id : legacyStateIdByString.values()) {
            if (id != null && id >= 0) {
                validStateIds.add(id);
            }
        }
        if (!validStateIds.contains(stoneStateId)) {
            validStateIds.add(stoneStateId);
        }
        validLegacyStateIds = validStateIds;

        BLOCK_STATE_REMAP.clear();
        for (int stateId = 0; stateId < legacyTable.length; stateId++) {
            legacyTable[stateId] = stoneStateId;
        }

        List<String> missingSnapshotStateIds = new ArrayList<>();
        List<String> unmappedSnapshotStates = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                int stateId = Block.BLOCK_STATE_REGISTRY.getId(state);
                String snapshotIdKey = Integer.toString(stateId);
                String stateKey = snapshotStateById.get(snapshotIdKey);
                if (stateKey == null) {
                    stateKey = state.toString();
                    missingSnapshotStateIds.add(snapshotIdKey + "=" + stateKey);
                }
                Integer mappedLegacyId = legacyStateIdByString.get(stateKey);
                if (mappedLegacyId == null) {
                    mappedLegacyId = explicitLegacyStateIdForUnsupportedSnapshotState(state);
                }
                if (mappedLegacyId == null) {
                    unmappedSnapshotStates.add(snapshotIdKey + "=" + stateKey);
                }
                int target = mappedLegacyId == null ? stoneStateId : mappedLegacyId;
                if (target < 0 || !validStateIds.contains(target)) {
                    target = stoneStateId;
                }
                legacyTable[stateId] = target;
                if (target != stateId) {
                    BLOCK_STATE_REMAP.put(stateId, target);
                }
            }
        }

        blockStateToLegacy = legacyTable;
        assertNoMissingMappings("snapshot state ids", missingSnapshotStateIds);
        assertNoMissingMappings("snapshot block-state->legacy remaps", unmappedSnapshotStates);
        assertLegacyWireIdsResolveOnServer(legacyTable);

        IntSet validItemIds = new IntOpenHashSet(legacyItemIdByString.size());
        for (Integer id : legacyItemIdByString.values()) {
            if (id != null && id >= 0) {
                validItemIds.add(id);
            }
        }
        int stoneItemId = legacyItemIdByString.getOrDefault("minecraft:stone", Item.getId(Items.STONE));
        if (!validItemIds.contains(stoneItemId)) {
            validItemIds.add(stoneItemId);
        }
        int itemRegistrySize = BuiltInRegistries.ITEM.size();
        int[] itemTable = new int[itemRegistrySize];
        List<String> unmappedSnapshotItems = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            int serverItemId = Item.getId(item);
            String key = itemId == null ? "" : itemId.toString();
            Integer explicitLegacyItemId = null;
            if (!legacyItemIdByString.containsKey(key)) {
                explicitLegacyItemId = explicitLegacyItemIdForUnsupportedSnapshotItem(key);
            }
            if (!legacyItemIdByString.containsKey(key) && explicitLegacyItemId == null) {
                unmappedSnapshotItems.add(serverItemId + "=" + key);
            }
            int mapped = explicitLegacyItemId != null
                    ? explicitLegacyItemId
                    : legacyItemIdByString.getOrDefault(key, stoneItemId);
            if (mapped < 0 || !validItemIds.contains(mapped)) {
                mapped = stoneItemId;
            }
            itemTable[serverItemId] = mapped;
            if (mapped != serverItemId) {
                ITEM_REMAP.put(serverItemId, mapped);
            }
        }
        itemToLegacy = itemTable;
        validLegacyItemIds = validItemIds;
        assertNoMissingMappings("snapshot item->legacy remaps", unmappedSnapshotItems);

        LegacyItemIdTable.rebuild();
        LegacyAttributeWireTable.rebuild();
        LegacyEntityTypeWireRemapper.rebuild();

        LegacyLinkMod.LOGGER.info("[LegacyLink] Registry mappings built: {} block states, {} items",
                BLOCK_STATE_REMAP.size(), ITEM_REMAP.size());
    }

    public static int remapBlockState(int stateId) {
        int[] table = blockStateToLegacy;
        if (stateId < 0 || stateId >= table.length) {
            throw new IllegalStateException("[LegacyLink] remapBlockState out of range: " + stateId
                    + " (table size " + table.length + ")");
        }
        int mapped = table[stateId];
        if (mapped < 0) {
            throw new IllegalStateException("[LegacyLink] remapBlockState produced negative mapping: " + stateId + " -> " + mapped);
        }
        IntSet valid = validLegacyStateIds;
        if (!valid.contains(mapped)) {
            throw new IllegalStateException("[LegacyLink] remapBlockState produced non-legacy id: "
                    + stateId + " -> " + mapped);
        }
        return mapped;
    }

    /** Map a 26.2 item registry id to the 26.1 wire-id produced by dump-table mapping. */
    public static int remapItem(int itemId) {
        int[] table = itemToLegacy;
        if (itemId < 0 || itemId >= table.length) {
            throw new IllegalStateException("[LegacyLink] remapItem out of range: " + itemId
                    + " (table size " + table.length + ")");
        }
        int mapped = table[itemId];
        if (mapped < 0 || !validLegacyItemIds.contains(mapped)) {
            throw new IllegalStateException("[LegacyLink] remapItem produced non-legacy id: "
                    + itemId + " -> " + mapped);
        }
        return mapped;
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

    /**
     * Every value stored in the remap table is written on the wire as a global block state id. On this server it must
     * be a real {@link BlockState} registry index so outbound rewriting can use {@code Block.BLOCK_STATE_REGISTRY.byId}
     * as the only materialization path.
     */
    private static void assertLegacyWireIdsResolveOnServer(int[] legacyTable) {
        int registrySize = Block.BLOCK_STATE_REGISTRY.size();
        IntOpenHashSet seen = new IntOpenHashSet();
        for (int target : legacyTable) {
            if (!seen.add(target)) {
                continue;
            }
            if (target < 0 || target >= registrySize) {
                throw new IllegalStateException(
                        "[LegacyLink] Legacy block-state wire id " + target
                                + " is out of range for this server's BLOCK_STATE_REGISTRY (size " + registrySize + ")");
            }
            BlockState resolved = Block.BLOCK_STATE_REGISTRY.byId(target);
            if (resolved == null) {
                throw new IllegalStateException(
                        "[LegacyLink] Legacy block-state wire id " + target
                                + " does not resolve via Block.BLOCK_STATE_REGISTRY.byId on this server; fix mapping tables");
            }
        }
    }

    public static int itemRemapCount() {
        return ITEM_REMAP.size();
    }

    private RegistryRemapper() {}

    static int legacyItemIdByRegistryKeyOrFallback(String registryId) {
        int stone = legacyItemIdByString.getOrDefault("minecraft:stone", Item.getId(Items.STONE));
        int mapped = legacyItemIdByString.getOrDefault(registryId, stone);
        if (mapped < 0 || !validLegacyItemIds.contains(mapped)) {
            return stone;
        }
        return mapped;
    }

    public static boolean hasLegacyItemRegistryKey(String registryId) {
        Integer mapped = legacyItemIdByString.get(registryId);
        return mapped != null && mapped >= 0 && validLegacyItemIds.contains(mapped);
    }

    public static boolean isLegacyItemWireId(int itemId) {
        return itemId >= 0 && validLegacyItemIds.contains(itemId);
    }

    private static Map<String, Integer> loadLegacyStateIdMap() {
        Type type = new TypeToken<LinkedHashMap<String, Integer>>() {}.getType();
        Map<String, Integer> map = loadJsonMap(LEGACY_STATE_MAP_OVERRIDE, LEGACY_STATE_MAP_RESOURCE, type, "legacy state map");
        if (map == null || map.isEmpty()) {
            throw new IllegalStateException("[LegacyLink] Legacy state map is empty");
        }
        return map;
    }

    private static Map<String, String> loadSnapshotStateByIdMap() {
        Type type = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
        Map<String, String> map = loadJsonMap(SNAPSHOT_STATE_MAP_OVERRIDE, SNAPSHOT_STATE_MAP_RESOURCE, type, "snapshot id->state map");
        if (map == null || map.isEmpty()) {
            throw new IllegalStateException("[LegacyLink] Snapshot id->state map is empty");
        }
        return map;
    }

    private static Map<String, Integer> loadLegacyItemIdMap() {
        List<String> lines = loadTextLines(LEGACY_ITEM_MAP_OVERRIDE, LEGACY_ITEM_MAP_RESOURCE, "legacy item map");
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
            throw new IllegalStateException("[LegacyLink] Legacy item map is empty");
        }
        return out;
    }

    private static <T> T loadJsonMap(String override, String resourcePath, Type type, String label) {
        String json = loadTextBlob(override, resourcePath, label);
        T map = GSON.fromJson(json, type);
        if (map == null) {
            throw new IllegalStateException("[LegacyLink] Parsed null for " + label);
        }
        return map;
    }

    private static List<String> loadTextLines(String override, String resourcePath, String label) {
        String blob = loadTextBlob(override, resourcePath, label);
        return blob.lines().toList();
    }

    private static String loadTextBlob(String override, String resourcePath, String label) {
        if (override != null && !override.isBlank()) {
            Path p = Path.of(override);
            if (!Files.exists(p)) {
                throw new IllegalStateException("[LegacyLink] Mapping override path does not exist: " + override);
            }
            try {
                return Files.readString(p, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("[LegacyLink] Failed reading override " + label + " from " + p, e);
            }
        }
        try (InputStream in = RegistryRemapper.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("[LegacyLink] Missing bundled " + label + " resource: " + resourcePath);
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (IOException e) {
            throw new IllegalStateException("[LegacyLink] Failed reading bundled " + label + " resource: " + resourcePath, e);
        }
    }

    private static void assertNoMissingMappings(String label, List<String> missingEntries) {
        if (missingEntries.isEmpty()) {
            return;
        }
        throw new IllegalStateException(
                "[LegacyLink] Missing deterministic " + label + " (" + missingEntries.size() + "): "
                        + String.join(", ", missingEntries)
        );
    }

    private static Integer explicitLegacyStateIdForUnsupportedSnapshotState(BlockState snapshotState) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(snapshotState.getBlock());
        if (blockId == null) {
            return null;
        }
        String key = blockId.toString();
        if (!LegacyLinkConstants.SULFUR_BLOCK_IDS.contains(key)) {
            return null;
        }
        Block replacement = replacementBlockForUnsupportedSnapshotBlock(key);
        BlockState projected = projectSharedProperties(snapshotState, replacement.defaultBlockState());
        return legacyStateIdByString.get(projected.toString());
    }

    private static Block replacementBlockForUnsupportedSnapshotBlock(String blockId) {
        if (blockId.endsWith("_slab")) {
            return Blocks.STONE_SLAB;
        }
        if (blockId.endsWith("_stairs")) {
            return Blocks.STONE_STAIRS;
        }
        if (blockId.endsWith("_wall")) {
            return Blocks.COBBLESTONE_WALL;
        }
        if ("minecraft:sulfur_spike".equals(blockId)) {
            return Blocks.POINTED_DRIPSTONE;
        }
        if (blockId.contains("brick")) {
            return Blocks.STONE_BRICKS;
        }
        if (blockId.startsWith("minecraft:chiseled_")) {
            return Blocks.CHISELED_STONE_BRICKS;
        }
        return Blocks.STONE;
    }

    private static BlockState projectSharedProperties(BlockState source, BlockState target) {
        BlockState out = target;
        for (Property<?> sourceProperty : source.getProperties()) {
            out = projectPropertyByName(source, out, sourceProperty);
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState projectPropertyByName(BlockState source, BlockState target, Property<?> sourceProperty) {
        Property targetProperty = null;
        for (Property<?> candidate : target.getProperties()) {
            if (candidate.getName().equals(sourceProperty.getName())) {
                targetProperty = candidate;
                break;
            }
        }
        if (targetProperty == null) {
            return target;
        }
        Property rawSourceProperty = (Property) sourceProperty;
        Comparable sourceValue = source.getValue(rawSourceProperty);
        String serializedValue = rawSourceProperty.getName(sourceValue);
        Optional parsed = targetProperty.getValue(serializedValue);
        if (parsed.isEmpty()) {
            return target;
        }
        return target.setValue(targetProperty, (Comparable) parsed.get());
    }

    private static Integer explicitLegacyItemIdForUnsupportedSnapshotItem(String snapshotItemId) {
        if (!LegacyLinkConstants.SULFUR_ITEM_IDS.contains(snapshotItemId)) {
            return null;
        }
        String replacement = replacementItemRegistryIdForUnsupportedSnapshotItem(snapshotItemId);
        return legacyItemIdByString.get(replacement);
    }

    private static String replacementItemRegistryIdForUnsupportedSnapshotItem(String snapshotItemId) {
        if ("minecraft:sulfur_cube_bucket".equals(snapshotItemId)) {
            return "minecraft:water_bucket";
        }
        if ("minecraft:sulfur_cube_spawn_egg".equals(snapshotItemId)) {
            return "minecraft:slime_spawn_egg";
        }
        if ("minecraft:sulfur_spike".equals(snapshotItemId)) {
            return "minecraft:pointed_dripstone";
        }
        if (snapshotItemId.endsWith("_slab")) {
            return "minecraft:stone_slab";
        }
        if (snapshotItemId.endsWith("_stairs")) {
            return "minecraft:stone_stairs";
        }
        if (snapshotItemId.endsWith("_wall")) {
            return "minecraft:cobblestone_wall";
        }
        if (snapshotItemId.contains("brick")) {
            return "minecraft:stone_bricks";
        }
        if (snapshotItemId.startsWith("minecraft:chiseled_")) {
            return "minecraft:chiseled_stone_bricks";
        }
        return "minecraft:stone";
    }

}
