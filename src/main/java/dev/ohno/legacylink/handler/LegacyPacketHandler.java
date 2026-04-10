package dev.ohno.legacylink.handler;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import dev.ohno.legacylink.LegacyLinkConstants;
import dev.ohno.legacylink.LegacyLinkMod;
import dev.ohno.legacylink.mapping.RegistryRemapper;
import dev.ohno.legacylink.telemetry.TranslationStats;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.lang.reflect.Field;
import java.util.Set;

public class LegacyPacketHandler extends ChannelDuplexHandler {

    private static final String HANDLER_NAME = "legacylink";
    private static final int LEGACY_SAFE_METADATA_MAX_INDEX = 17;
    private final Set<Integer> remappedLegacyEntityIds = new HashSet<>();

    public static void install(Connection connection) {
        var channel = connection.channel;
        if (channel.pipeline().get(HANDLER_NAME) != null) {
            return;
        }
        String anchor = resolvePipelineAnchor(channel.pipeline().names());
        if (anchor != null && channel.pipeline().get(anchor) != null) {
            channel.pipeline().addBefore(anchor, HANDLER_NAME, new LegacyPacketHandler());
            LegacyLinkMod.LOGGER.debug("[LegacyLink] Installed packet handler before '{}' on {}", anchor, connection.getRemoteAddress());
        } else if (channel.pipeline().get("packet_handler") != null) {
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new LegacyPacketHandler());
            LegacyLinkMod.LOGGER.debug("[LegacyLink] Installed packet handler before 'packet_handler' on {}", connection.getRemoteAddress());
        } else {
            channel.pipeline().addLast(HANDLER_NAME, new LegacyPacketHandler());
            LegacyLinkMod.LOGGER.debug("[LegacyLink] Installed packet handler at pipeline tail on {}", connection.getRemoteAddress());
        }
    }

    private static String resolvePipelineAnchor(List<String> pipelineNames) {
        for (String name : pipelineNames) {
            String lower = name.toLowerCase();
            if (lower.startsWith("pre-pe-encoder-")) {
                return name;
            }
        }
        for (String name : pipelineNames) {
            String lower = name.toLowerCase();
            if (lower.startsWith("pe-encoder-")) {
                return name;
            }
        }
        for (String name : pipelineNames) {
            String lower = name.toLowerCase();
            if (lower.equals("encoder") || lower.endsWith("encoder")) {
                return name;
            }
        }
        return null;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Packet<?>) {
            msg = translateOutbound(msg);
        }
        super.write(ctx, msg, promise);
    }

    private Object translateOutbound(Object msg) {
        try {
            if (msg instanceof ClientboundBundlePacket bundlePacket) {
                return remapBundlePacket(bundlePacket);
            }
            if (msg instanceof ClientboundStatusResponsePacket statusResponse) {
                return remapStatusResponse(statusResponse);
            }
            if (msg instanceof ClientboundUpdateAdvancementsPacket advancements) {
                return remapAdvancements(advancements);
            }
            if (msg instanceof ClientboundSetEntityDataPacket entityData) {
                return remapEntityData(entityData);
            }
            if (msg instanceof ClientboundUpdateAttributesPacket updateAttributes) {
                return remapAttributes(updateAttributes);
            }
            if (msg instanceof ClientboundRemoveEntitiesPacket removeEntities) {
                return trackRemovedEntities(removeEntities);
            }
            if (msg instanceof ClientboundRegistryDataPacket registryData) {
                return filterRegistryData(registryData);
            }
            if (msg instanceof ClientboundLevelChunkWithLightPacket levelChunk) {
                return LegacyChunkTranslator.remapChunkPacket(levelChunk);
            }
            if (msg instanceof ClientboundBlockUpdatePacket blockUpdate) {
                return remapBlockUpdate(blockUpdate);
            }
            if (msg instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
                return remapSectionBlocksUpdate(sectionUpdate);
            }
            if (msg instanceof ClientboundAddEntityPacket addEntity) {
                return remapEntitySpawn(addEntity);
            }
        } catch (Exception e) {
            LegacyLinkMod.LOGGER.warn("[LegacyLink] Failed to translate outbound packet: {}", msg.getClass().getSimpleName(), e);
            TranslationStats.recordError();
        }
        return msg;
    }

    @SuppressWarnings("unchecked")
    private ClientboundBundlePacket remapBundlePacket(ClientboundBundlePacket bundlePacket) {
        List<Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> rewritten =
                new ArrayList<>();
        boolean changed = false;

        for (Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> subPacket : bundlePacket.subPackets()) {
            Object remapped = translateOutbound(subPacket);
            if (remapped != subPacket) {
                changed = true;
            }
            rewritten.add((Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>) remapped);
        }

        if (!changed) {
            return bundlePacket;
        }
        return new ClientboundBundlePacket(rewritten);
    }

    private ClientboundStatusResponsePacket remapStatusResponse(ClientboundStatusResponsePacket packet) {
        ServerStatus status = packet.status();
        ServerStatus.Version forcedLegacyVersion = new ServerStatus.Version("26.1.2", LegacyLinkConstants.PROTOCOL_26_1);
        ServerStatus remapped = new ServerStatus(
                status.description(),
                status.players(),
                Optional.of(forcedLegacyVersion),
                status.favicon(),
                status.enforcesSecureChat()
        );
        return new ClientboundStatusResponsePacket(remapped);
    }

    private ClientboundRegistryDataPacket filterRegistryData(ClientboundRegistryDataPacket packet) {
        ResourceKey<?> registryKey = packet.registry();
        String registryId = registryKey.identifier().toString();

        if (registryId.equals(LegacyLinkConstants.SULFUR_CUBE_ARCHETYPE_REGISTRY)) {
            LegacyLinkMod.LOGGER.debug("[LegacyLink] Skipping entire 26.2-only registry: {}", registryId);
            TranslationStats.recordRegistryFiltered(registryId);
            return new ClientboundRegistryDataPacket(packet.registry(), List.of());
        }

        var entries = packet.entries();
        var filtered = new ArrayList<>(entries);
        boolean changed = false;

        var iterator = filtered.iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            String entryId = entry.id().toString();

            if (LegacyLinkConstants.SULFUR_BLOCK_IDS.contains(entryId)
                    || LegacyLinkConstants.SULFUR_ITEM_IDS.contains(entryId)
                    || entryId.equals(LegacyLinkConstants.SULFUR_CAVES_BIOME_ID)
                    || entryId.equals(LegacyLinkConstants.SULFUR_CUBE_ENTITY_ID)
                    || entryId.contains("sulfur")) {
                iterator.remove();
                changed = true;
                TranslationStats.recordRegistryEntryFiltered(registryId, entryId);
                LegacyLinkMod.LOGGER.debug("[LegacyLink] Filtered registry entry {}/{}", registryId, entryId);
            }
        }

        if (changed) {
            return new ClientboundRegistryDataPacket(packet.registry(), filtered);
        }
        return packet;
    }

    private ClientboundSetEntityDataPacket remapEntityData(ClientboundSetEntityDataPacket packet) {
        int entityId = packet.id();
        List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> packedItems = packet.packedItems();
        if (packedItems.isEmpty()) {
            return packet;
        }

        if (remappedLegacyEntityIds.contains(entityId)) {
            // sulfur_cube -> slime remap: metadata schemas differ; safest is dropping metadata payload.
            return new ClientboundSetEntityDataPacket(entityId, List.of());
        }

        List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> filtered = new ArrayList<>(packedItems.size());
        for (net.minecraft.network.syncher.SynchedEntityData.DataValue<?> item : packedItems) {
            if (item.id() <= LEGACY_SAFE_METADATA_MAX_INDEX) {
                filtered.add(item);
            }
        }

        if (filtered.size() != packedItems.size()) {
            LegacyLinkMod.LOGGER.debug(
                    "[LegacyLink] Stripped {} high-index metadata entries from entity {}",
                    packedItems.size() - filtered.size(), entityId
            );
            return new ClientboundSetEntityDataPacket(entityId, filtered);
        }
        return packet;
    }

    @SuppressWarnings("unchecked")
    private ClientboundUpdateAttributesPacket remapAttributes(ClientboundUpdateAttributesPacket packet) {
        try {
            Field attributesField = ClientboundUpdateAttributesPacket.class.getDeclaredField("attributes");
            attributesField.setAccessible(true);
            List<ClientboundUpdateAttributesPacket.AttributeSnapshot> attributes =
                    (List<ClientboundUpdateAttributesPacket.AttributeSnapshot>) attributesField.get(packet);
            if (attributes == null || attributes.isEmpty()) {
                return packet;
            }

            int before = attributes.size();
            attributes.removeIf(snapshot -> {
                Identifier attrId = BuiltInRegistries.ATTRIBUTE.getKey(snapshot.attribute().value());
                return attrId != null && LegacyLinkConstants.LEGACY_UNSUPPORTED_ATTRIBUTE_IDS.contains(attrId.toString());
            });
            int removed = before - attributes.size();
            if (removed > 0) {
                LegacyLinkMod.LOGGER.debug("[LegacyLink] Filtered {} unsupported legacy attributes for entity {}",
                        removed, packet.getEntityId());
            }
        } catch (Exception e) {
            LegacyLinkMod.LOGGER.debug("[LegacyLink] Unable to sanitize update_attributes packet", e);
            TranslationStats.recordError();
        }
        return packet;
    }

    private ClientboundUpdateAdvancementsPacket remapAdvancements(ClientboundUpdateAdvancementsPacket packet) {
        if (packet.getAdded().isEmpty()) {
            return packet;
        }
        List<AdvancementHolder> rewritten = new ArrayList<>(packet.getAdded().size());
        int remappedIcons = 0;

        for (AdvancementHolder holder : packet.getAdded()) {
            Advancement advancement = holder.value();
            Optional<DisplayInfo> display = advancement.display();
            if (display.isPresent()) {
                RemapResult<DisplayInfo> remap = remapAdvancementDisplay(display.get());
                if (remap.changed) {
                    remappedIcons++;
                    display = Optional.of(remap.value);
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

        if (remappedIcons > 0) {
            TranslationStats.recordAdvancementsRemapped(remappedIcons);
        }
        return new ClientboundUpdateAdvancementsPacket(
                packet.shouldReset(),
                rewritten,
                packet.getRemoved(),
                packet.getProgress(),
                packet.shouldShowAdvancements()
        );
    }

    private RemapResult<DisplayInfo> remapAdvancementDisplay(DisplayInfo display) {
        ItemStackTemplate icon = display.getIcon();
        Item mappedItem = remapItemTemplateToLegacySafe(icon);
        if (mappedItem == icon.item().value()) {
            return RemapResult.unchanged(display);
        }

        ItemStackTemplate mappedIcon = new ItemStackTemplate(mappedItem.builtInRegistryHolder(), icon.count(), icon.components());
        DisplayInfo mappedDisplay = new DisplayInfo(
                mappedIcon,
                display.getTitle(),
                display.getDescription(),
                display.getBackground(),
                display.getType(),
                display.shouldShowToast(),
                display.shouldAnnounceChat(),
                display.isHidden()
        );
        mappedDisplay.setLocation(display.getX(), display.getY());
        return RemapResult.changed(mappedDisplay);
    }

    private Item remapItemTemplateToLegacySafe(ItemStackTemplate template) {
        Item item = template.item().value();
        int oldItemId = Item.getId(item);
        int mappedItemId = RegistryRemapper.remapItem(oldItemId);

        Identifier itemKey = BuiltInRegistries.ITEM.getKey(item);
        boolean namedLegacyIncompatible = itemKey != null
                && (LegacyLinkConstants.SULFUR_ITEM_IDS.contains(itemKey.toString())
                || itemKey.toString().contains("sulfur"));
        if (namedLegacyIncompatible) {
            mappedItemId = Item.getId(Items.STONE);
        }

        Item mappedItem = BuiltInRegistries.ITEM.byId(mappedItemId);
        if (mappedItem == null) {
            mappedItem = Items.STONE;
        }
        return mappedItem;
    }

    private ClientboundBlockUpdatePacket remapBlockUpdate(ClientboundBlockUpdatePacket packet) {
        BlockState state = packet.getBlockState();
        int stateId = Block.BLOCK_STATE_REGISTRY.getId(state);
        int remapped = RegistryRemapper.remapBlockState(stateId);
        if (remapped != stateId) {
            BlockState fallback = Block.BLOCK_STATE_REGISTRY.byId(remapped);
            if (fallback == null) fallback = Blocks.STONE.defaultBlockState();
            TranslationStats.recordBlockRemap();
            return new ClientboundBlockUpdatePacket(packet.getPos(), fallback);
        }
        return packet;
    }

    private ClientboundSectionBlocksUpdatePacket remapSectionBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet) {
        try {
            var statesField = ClientboundSectionBlocksUpdatePacket.class.getDeclaredField("states");
            statesField.setAccessible(true);
            BlockState[] states = (BlockState[]) statesField.get(packet);
            int remapped = 0;
            for (int i = 0; i < states.length; i++) {
                BlockState state = states[i];
                int oldId = Block.BLOCK_STATE_REGISTRY.getId(state);
                int newId = RegistryRemapper.remapBlockState(oldId);
                if (newId != oldId) {
                    BlockState fallback = Block.BLOCK_STATE_REGISTRY.byId(newId);
                    if (fallback == null) {
                        fallback = Blocks.STONE.defaultBlockState();
                    }
                    states[i] = fallback;
                    remapped++;
                }
            }
            if (remapped > 0) {
                TranslationStats.recordSectionBlocksRemap(remapped);
            }
        } catch (Exception e) {
            LegacyLinkMod.LOGGER.debug("[LegacyLink] Section update remap fallback (reflection issue)", e);
            TranslationStats.recordError();
        }
        return packet;
    }

    private ClientboundAddEntityPacket remapEntitySpawn(ClientboundAddEntityPacket packet) {
        EntityType<?> type = packet.getType();
        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (typeId != null && typeId.toString().equals(LegacyLinkConstants.SULFUR_CUBE_ENTITY_ID)) {
            TranslationStats.recordEntityRemap();
            remappedLegacyEntityIds.add(packet.getId());
            LegacyLinkMod.LOGGER.debug("[LegacyLink] Remapped sulfur_cube entity to slime at {},{},{}",
                    packet.getX(), packet.getY(), packet.getZ());
            // Sulfur cube is visually similar to slime; send slime instead
            return new ClientboundAddEntityPacket(
                    packet.getId(), packet.getUUID(), packet.getX(), packet.getY(), packet.getZ(),
                    packet.getXRot(), packet.getYRot(), EntityType.SLIME, 1,
                    packet.getMovement(), packet.getYHeadRot()
            );
        }
        return packet;
    }

    private ClientboundRemoveEntitiesPacket trackRemovedEntities(ClientboundRemoveEntitiesPacket packet) {
        for (int id : packet.getEntityIds()) {
            remappedLegacyEntityIds.remove(id);
        }
        return packet;
    }

    private record RemapResult<T>(T value, boolean changed) {
        static <T> RemapResult<T> unchanged(T value) {
            return new RemapResult<>(value, false);
        }

        static <T> RemapResult<T> changed(T value) {
            return new RemapResult<>(value, true);
        }
    }
}
