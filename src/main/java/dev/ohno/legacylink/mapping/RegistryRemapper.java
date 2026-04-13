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

/**
 * Server-side 26.2 → 26.1 numeric remaps used on the wire. Same role as Via’s per-protocol mapping tables, but
 * scoped to this version pair: explicit {@link LegacyLinkConstants} entries plus range guards so unknown high ids
 * never reach a 26.1 client.
 */
public final class RegistryRemapper {

    private static final Int2IntMap BLOCK_STATE_REMAP = new Int2IntOpenHashMap();
    private static final Int2IntMap ITEM_REMAP = new Int2IntOpenHashMap();

    /** Server registry id for {@link Blocks#STONE} default state — written to legacy clients as the generic fallback. */
    private static int fallbackBlockStateId = 1;
    private static final Item FALLBACK_ITEM = Items.STONE;

    public static void buildMappings() {
        BLOCK_STATE_REMAP.clear();
        ITEM_REMAP.clear();

        int stoneStateId = Block.BLOCK_STATE_REGISTRY.getId(Blocks.STONE.defaultBlockState());
        fallbackBlockStateId = stoneStateId;

        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
            if (blockId != null && LegacyLinkConstants.SULFUR_BLOCK_IDS.contains(blockId.toString())) {
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    int id = Block.BLOCK_STATE_REGISTRY.getId(state);
                    BLOCK_STATE_REMAP.put(id, stoneStateId);
                }
                LegacyLinkMod.LOGGER.debug("[LegacyLink] Mapped block {} -> stone", blockId);
            }
        }

        // 26.1.1 cannot decode pointed_dripstone tip_merge states (observed ids 30205/30207).
        // Map them to stone to guarantee a legacy-safe wire id.
        for (BlockState state : Blocks.POINTED_DRIPSTONE.getStateDefinition().getPossibleStates()) {
            if (state.getValue(PointedDripstoneBlock.THICKNESS) == SpeleothemThickness.TIP_MERGE) {
                int fromId = Block.BLOCK_STATE_REGISTRY.getId(state);
                int toId = stoneStateId;
                BLOCK_STATE_REMAP.put(fromId, toId);
                LegacyLinkMod.LOGGER.debug(
                        "[LegacyLink] Mapped pointed_dripstone {}({}) -> stone({})",
                        state, fromId, toId
                );
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
        int mapped = BLOCK_STATE_REMAP.getOrDefault(stateId, stateId);
        BlockState atId = Block.BLOCK_STATE_REGISTRY.byId(mapped);
        if (atId == null) {
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
        return BLOCK_STATE_REMAP.containsKey(stateId);
    }

    public static int blockStateRemapCount() {
        return BLOCK_STATE_REMAP.size();
    }

    public static int itemRemapCount() {
        return ITEM_REMAP.size();
    }

    private RegistryRemapper() {}
}
