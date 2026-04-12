package dev.ohno.legacylink.handler.rewrite;

import dev.ohno.legacylink.mapping.LegacyItemIdTable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet-facing item rewrites for legacy clients. Numeric ids are canonicalized through
 * {@link LegacyItemIdTable} round-trip translation; stacks then pass through
 * {@link ItemComponentSanitizer} to drop 26.2-only components (e.g. sulfur cube content) and rewrite nested stacks
 * in containers, bundles, and charged projectiles.
 */
public final class ItemRewriter {

    public static Item remapItemToLegacySafe(Item item) {
        /*
         * Canonicalize through the same legacy wire-id table used by optional-stack codecs.
         * This keeps packet-level rewrites and codec-level rewrites consistent so inventory
         * updates do not oscillate between two item interpretations for the same slot.
         */
        int serverId = Item.getId(item);
        int legacyWireId = LegacyItemIdTable.toLegacyId(serverId);
        int canonicalServerId = LegacyItemIdTable.serverItemIdFromLegacyWire(legacyWireId);
        Item mapped = BuiltInRegistries.ITEM.byId(canonicalServerId);
        return mapped != null ? mapped : Items.STONE;
    }

    public static ItemStackTemplate remapTemplate(ItemStackTemplate template) {
        ItemStack created = template.create();
        if (created.isEmpty()) {
            return template;
        }
        ItemStack out = remapStack(created);
        if (out == created) {
            return template;
        }
        return ItemStackTemplate.fromNonEmptyStack(out);
    }

    public static ItemStack remapStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return stack;
        }
        ItemStack copy = stack.copy();
        Item mappedItem = remapItemToLegacySafe(copy.getItem());
        boolean itemChanged = mappedItem != copy.getItem();
        if (itemChanged) {
            copy = new ItemStack(mappedItem.builtInRegistryHolder(), copy.getCount(), copy.getComponentsPatch());
        }
        boolean componentsChanged = ItemComponentSanitizer.apply(copy);
        if (!itemChanged && !componentsChanged) {
            return stack;
        }
        return copy;
    }

    public static List<ItemStack> remapStackList(List<ItemStack> items) {
        List<ItemStack> rewritten = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            rewritten.add(remapStack(stack));
        }
        return rewritten;
    }

    public static int remapItemIdStrict(int itemId) {
        int legacyWireId = LegacyItemIdTable.toLegacyId(itemId);
        return LegacyItemIdTable.serverItemIdFromLegacyWire(legacyWireId);
    }

    private ItemRewriter() {}
}
