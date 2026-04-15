package dev.ohno.legacylink.handler.rewrite;

import dev.ohno.legacylink.LegacyLinkMod;
import dev.ohno.legacylink.mapping.RegistryRemapper;
import dev.ohno.legacylink.telemetry.TranslationStats;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AdvancementRewriter {

    public static ClientboundUpdateAdvancementsPacket rewrite(ClientboundUpdateAdvancementsPacket packet) {
        if (packet.getAdded().isEmpty()) {
            return packet;
        }

        List<AdvancementHolder> rewritten = new ArrayList<>(packet.getAdded().size());
        int remappedIcons = 0;
        int strictFallbackIcons = 0;
        int firstLegacyUnsafeItemId = -1;

        for (AdvancementHolder holder : packet.getAdded()) {
            Advancement advancement = holder.value();
            Optional<DisplayInfo> display = advancement.display();
            if (display.isPresent()) {
                int originalItemId = Item.getId(display.get().getIcon().item().value());
                if (!RegistryRemapper.isLegacyItemWireId(originalItemId) && firstLegacyUnsafeItemId == -1) {
                    firstLegacyUnsafeItemId = originalItemId;
                }
                ItemStackTemplate remappedIcon = ensureLegacyWireSafeTemplate(display.get().getIcon());
                if (remappedIcon != display.get().getIcon()) {
                    DisplayInfo remappedDisplay = new DisplayInfo(
                            remappedIcon,
                            display.get().getTitle(),
                            display.get().getDescription(),
                            display.get().getBackground(),
                            display.get().getType(),
                            display.get().shouldShowToast(),
                            display.get().shouldAnnounceChat(),
                            display.get().isHidden()
                    );
                    remappedDisplay.setLocation(display.get().getX(), display.get().getY());
                    display = Optional.of(remappedDisplay);
                    remappedIcons++;
                    ItemStack stack = remappedIcon.create();
                    if (!stack.isEmpty() && stack.getItem() == Items.STONE && originalItemId != Item.getId(Items.STONE)) {
                        strictFallbackIcons++;
                    }
                }
            }
            Advancement rebuilt = new Advancement(
                    advancement.parent(),
                    display,
                    advancement.rewards(),
                    advancement.criteria(),
                    advancement.requirements(),
                    advancement.sendsTelemetryEvent()
            );
            rewritten.add(new AdvancementHolder(holder.id(), rebuilt));
        }

        if (remappedIcons == 0 && strictFallbackIcons == 0) {
            return packet;
        }

        if (remappedIcons > 0) {
            TranslationStats.recordAdvancementsRemapped(remappedIcons);
        }
        LegacyLinkMod.LOGGER.info(
                "[LegacyLink][Trace] update_advancements rewritten_icons={} strict_fallback_icons={} first_bad_item_id={}",
                remappedIcons,
                strictFallbackIcons,
                firstLegacyUnsafeItemId
        );

        return new ClientboundUpdateAdvancementsPacket(
                packet.shouldReset(),
                rewritten,
                packet.getRemoved(),
                packet.getProgress(),
                packet.shouldShowAdvancements()
        );
    }

    private static ItemStackTemplate ensureLegacyWireSafeTemplate(ItemStackTemplate template) {
        ItemStackTemplate remapped = ItemRewriter.remapTemplate(template);
        ItemStack stack = remapped.create();
        if (stack.isEmpty()) {
            return remapped;
        }
        Item registrySafe = ItemRewriter.remapItemForLegacyRegistryEncoding(stack.getItem());
        if (registrySafe != stack.getItem()) {
            remapped = ItemStackTemplate.fromNonEmptyStack(
                    new ItemStack(registrySafe.builtInRegistryHolder(), stack.getCount(), stack.getComponentsPatch())
            );
            stack = remapped.create();
        }
        int serverItemId = Item.getId(stack.getItem());
        int legacyWireId = ItemRewriter.remapItemIdStrict(serverItemId);
        if (RegistryRemapper.isLegacyItemWireId(legacyWireId)) {
            return remapped;
        }
        Item fallback = Items.STONE;
        return ItemStackTemplate.fromNonEmptyStack(new ItemStack(fallback));
    }

    private AdvancementRewriter() {}
}
