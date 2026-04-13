package dev.ohno.legacylink.mapping;

import dev.ohno.legacylink.LegacyLinkConstants;
import dev.ohno.legacylink.LegacyLinkMod;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;

import java.util.Arrays;

/**
 * Server-side 26.2 → 26.1 numeric remaps used on the wire. Same role as Via’s per-protocol mapping tables, but
 * scoped to this version pair: explicit {@link LegacyLinkConstants} entries plus range guards so unknown high ids
 * never reach a 26.1 client.
 */
public final class RegistryRemapper {

    private static final Int2IntMap BLOCK_STATE_REMAP = new Int2IntOpenHashMap();
    private static final Int2IntMap ITEM_REMAP = new Int2IntOpenHashMap();
    private static int[] blockStateToLegacy = new int[0];

    /** Legacy wire-id fallback (mapped stone id) for 26.1 clients. */
    private static int fallbackBlockStateId = 1;
    private static final Item FALLBACK_ITEM = Items.STONE;

    public static void buildMappings() {
        BLOCK_STATE_REMAP.clear();
        ITEM_REMAP.clear();

        // Via-style blockstate wire-id mapping:
        // iterate the full 26.2 blockstate registry and assign sequential legacy IDs while skipping
        // 26.2-only states. This keeps all vanilla shared states (e.g. deepslate) stable instead of
        // flattening by range checks.
        int stateRegistrySize = Block.BLOCK_STATE_REGISTRY.size();
        int[] legacyTable = new int[stateRegistrySize];
        Arrays.fill(legacyTable, -1);
        int nextLegacyId = 0;

        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                int stateId = Block.BLOCK_STATE_REGISTRY.getId(state);
                if (is26_2OnlyBlockState(blockId, state)) {
                    continue;
                }
                legacyTable[stateId] = nextLegacyId++;
            }
        }

        int stoneStateId = Block.BLOCK_STATE_REGISTRY.getId(Blocks.STONE.defaultBlockState());
        int stoneLegacyId = (stoneStateId >= 0 && stoneStateId < legacyTable.length)
                ? legacyTable[stoneStateId] : 1;
        if (stoneLegacyId < 0) {
            stoneLegacyId = 1;
        }
        fallbackBlockStateId = stoneLegacyId;

        BLOCK_STATE_REMAP.clear();
        for (int i = 0; i < legacyTable.length; i++) {
            if (legacyTable[i] < 0) {
                legacyTable[i] = stoneLegacyId;
            }
            if (legacyTable[i] != i) {
                BLOCK_STATE_REMAP.put(i, legacyTable[i]);
            }
        }
        blockStateToLegacy = legacyTable;

        if (nextLegacyId != LegacyLinkConstants.MAX_26_1_BLOCKSTATE_ID + 1) {
            LegacyLinkMod.LOGGER.warn(
                    "[LegacyLink] Legacy blockstate count {} does not match expected {} — check 26.2-only blockstate detection",
                    nextLegacyId, LegacyLinkConstants.MAX_26_1_BLOCKSTATE_ID + 1
            );
        }

        // 26.1.1 cannot decode pointed_dripstone tip_merge states; pin to legacy stone fallback.
        for (BlockState state : Blocks.POINTED_DRIPSTONE.getStateDefinition().getPossibleStates()) {
            if (state.getValue(PointedDripstoneBlock.THICKNESS) == SpeleothemThickness.TIP_MERGE) {
                int fromId = Block.BLOCK_STATE_REGISTRY.getId(state);
                int toId = stoneLegacyId;
                BLOCK_STATE_REMAP.put(fromId, toId);
            }
        }

        int stoneItemId = Item.getId(Items.STONE);
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId != null && LegacyLinkConstants.SULFUR_ITEM_IDS.contains(itemId.toString())) {
                ITEM_REMAP.put(Item.getId(item), stoneItemId);
                LegacyLinkMod.LOGGER.debug("[LegacyLink] Mapped item {} -> stone", itemId);
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
        return mapped >= 0 ? mapped : fallbackBlockStateId;
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

    private static boolean is26_2OnlyBlockState(Identifier blockId, BlockState state) {
        if (blockId == null) {
            return true;
        }
        if (LegacyLinkConstants.SULFUR_BLOCK_IDS.contains(blockId.toString())) {
            return true;
        }
        return state.getBlock() == Blocks.POINTED_DRIPSTONE
                && state.getValue(PointedDripstoneBlock.THICKNESS) == SpeleothemThickness.TIP_MERGE;
    }
}
