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
import net.minecraft.world.level.block.state.BlockState;

public final class RegistryRemapper {

    private static final Int2IntMap BLOCK_STATE_REMAP = new Int2IntOpenHashMap();
    private static final Int2IntMap ITEM_REMAP = new Int2IntOpenHashMap();

    private static final int FALLBACK_BLOCK_STATE = 1; // stone default state
    private static final Item FALLBACK_ITEM = Items.STONE;

    public static void buildMappings() {
        BLOCK_STATE_REMAP.clear();
        ITEM_REMAP.clear();

        int stoneStateId = Block.BLOCK_STATE_REGISTRY.getId(Blocks.STONE.defaultBlockState());

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

        int stoneItemId = Item.getId(Items.STONE);
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId != null && LegacyLinkConstants.SULFUR_ITEM_IDS.contains(itemId.toString())) {
                ITEM_REMAP.put(Item.getId(item), stoneItemId);
                LegacyLinkMod.LOGGER.debug("[LegacyLink] Mapped item {} -> stone", itemId);
            }
        }

        LegacyLinkMod.LOGGER.info("[LegacyLink] Registry mappings built: {} block states, {} items",
                BLOCK_STATE_REMAP.size(), ITEM_REMAP.size());
    }

    public static int remapBlockState(int stateId) {
        int explicit = BLOCK_STATE_REMAP.getOrDefault(stateId, stateId);
        if (explicit != stateId) {
            return explicit;
        }
        if (stateId > LegacyLinkConstants.MAX_26_1_BLOCKSTATE_ID) {
            return FALLBACK_BLOCK_STATE;
        }
        return stateId;
    }

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
        return BLOCK_STATE_REMAP.containsKey(stateId) || stateId > LegacyLinkConstants.MAX_26_1_BLOCKSTATE_ID;
    }

    public static int blockStateRemapCount() {
        return BLOCK_STATE_REMAP.size();
    }

    public static int itemRemapCount() {
        return ITEM_REMAP.size();
    }

    private RegistryRemapper() {}
}
