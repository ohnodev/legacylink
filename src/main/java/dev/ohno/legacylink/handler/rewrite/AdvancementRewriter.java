package dev.ohno.legacylink.handler.rewrite;

import dev.ohno.legacylink.LegacyLinkConstants;
import dev.ohno.legacylink.LegacyLinkMod;
import dev.ohno.legacylink.telemetry.TranslationStats;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;

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
        int firstLegacyUnsafeItemId = -1;

        for (AdvancementHolder holder : packet.getAdded()) {
            Advancement advancement = holder.value();
            Optional<DisplayInfo> display = advancement.display();
            if (display.isPresent()) {
                int originalItemId = Item.getId(display.get().getIcon().item().value());
                if (originalItemId > LegacyLinkConstants.MAX_26_1_ITEM_ID && firstLegacyUnsafeItemId == -1) {
                    firstLegacyUnsafeItemId = originalItemId;
                }
                ItemStackTemplate remappedIcon = ItemRewriter.remapTemplate(display.get().getIcon());
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

        if (remappedIcons == 0) {
            if (firstLegacyUnsafeItemId != -1) {
                LegacyLinkMod.LOGGER.info(
                        "[LegacyLink][Trace] update_advancements rewritten icons={} first_bad_item_id={}",
                        remappedIcons,
                        firstLegacyUnsafeItemId
                );
            }
            return packet;
        }

        TranslationStats.recordAdvancementsRemapped(remappedIcons);
        LegacyLinkMod.LOGGER.info(
                "[LegacyLink][Trace] update_advancements rewritten icons={} first_bad_item_id={}",
                remappedIcons,
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

    private AdvancementRewriter() {}
}
