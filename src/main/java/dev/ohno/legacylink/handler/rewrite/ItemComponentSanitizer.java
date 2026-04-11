package dev.ohno.legacylink.handler.rewrite;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Strips 26.2-only item components and rewrites nested stacks (containers, bundles, crossbows) for legacy clients.
 * Numeric item ids are still handled by {@link ItemRewriter#remapItemToLegacySafe}; this class handles component
 * payloads that 26.1 does not define or that reference mod-only data.
 */
final class ItemComponentSanitizer {

    private ItemComponentSanitizer() {}

    /**
     * @return {@code true} if the stack was modified
     */
    static boolean apply(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        boolean changed = false;
        if (stack.hasNonDefault(DataComponents.SULFUR_CUBE_CONTENT)) {
            stack.remove(DataComponents.SULFUR_CUBE_CONTENT);
            changed = true;
        }
        changed |= rewriteContainer(stack);
        changed |= rewriteBundle(stack);
        changed |= rewriteChargedProjectiles(stack);
        return changed;
    }

    private static boolean rewriteContainer(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null || contents == ItemContainerContents.EMPTY) {
            return false;
        }
        List<ItemStack> slots = contents.allItemsCopyStream().map(ItemRewriter::remapStack).collect(Collectors.toCollection(ArrayList::new));
        ItemContainerContents next = ItemContainerContents.fromItems(slots);
        if (next.equals(contents)) {
            return false;
        }
        stack.set(DataComponents.CONTAINER, next);
        return true;
    }

    private static boolean rewriteBundle(ItemStack stack) {
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle == null || bundle.isEmpty()) {
            return false;
        }
        List<ItemStackTemplate> nextTemplates = new ArrayList<>(bundle.size());
        for (ItemStackTemplate template : bundle.items()) {
            ItemStack inner = template.create();
            if (inner.isEmpty()) {
                nextTemplates.add(template);
                continue;
            }
            ItemStack rewritten = ItemRewriter.remapStack(inner);
            if (rewritten.isEmpty()) {
                nextTemplates.add(template);
            } else {
                nextTemplates.add(ItemStackTemplate.fromNonEmptyStack(rewritten));
            }
        }
        BundleContents next = new BundleContents(nextTemplates);
        if (next.equals(bundle)) {
            return false;
        }
        stack.set(DataComponents.BUNDLE_CONTENTS, next);
        return true;
    }

    private static boolean rewriteChargedProjectiles(ItemStack stack) {
        ChargedProjectiles projectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (projectiles == null || projectiles == ChargedProjectiles.EMPTY) {
            return false;
        }
        List<ItemStackTemplate> next = new ArrayList<>();
        for (ItemStackTemplate template : projectiles.items()) {
            ItemStack inner = template.create();
            if (inner.isEmpty()) {
                next.add(template);
                continue;
            }
            ItemStack rewritten = ItemRewriter.remapStack(inner);
            if (rewritten.isEmpty()) {
                next.add(template);
            } else {
                next.add(ItemStackTemplate.fromNonEmptyStack(rewritten));
            }
        }
        ChargedProjectiles rebuilt = new ChargedProjectiles(next);
        if (rebuilt.equals(projectiles)) {
            return false;
        }
        stack.set(DataComponents.CHARGED_PROJECTILES, rebuilt);
        return true;
    }
}
