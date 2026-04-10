package dev.ohno.legacylink.handler;

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
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LegacyPacketHandler extends ChannelDuplexHandler {

    private static final String HANDLER_NAME = "legacylink";

    public static void install(Connection connection) {
        var channel = connection.channel;
        if (channel.pipeline().get(HANDLER_NAME) != null) {
            return;
        }
        channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new LegacyPacketHandler());
        LegacyLinkMod.LOGGER.debug("[LegacyLink] Installed packet handler on {}", connection.getRemoteAddress());
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Packet<?>) {
            msg = translateOutbound(msg);
            if (msg == null) {
                promise.setSuccess();
                return;
            }
        }
        super.write(ctx, msg, promise);
    }

    private Object translateOutbound(Object msg) {
        try {
            if (msg instanceof ClientboundStatusResponsePacket statusResponse) {
                return remapStatusResponse(statusResponse);
            }
            if (msg instanceof ClientboundUpdateAdvancementsPacket) {
                TranslationStats.recordAdvancementsDropped();
                return null;
            }
            if (msg instanceof ClientboundRegistryDataPacket registryData) {
                return filterRegistryData(registryData);
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

    @SuppressWarnings("unchecked")
    private ClientboundSectionBlocksUpdatePacket remapSectionBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet) {
        // SectionBlocksUpdatePacket is complex — for v1 we pass through
        // and rely on the client handling unknown states as missing blocks.
        // Full remapping requires rebuilding the packed short/state arrays.
        return packet;
    }

    private ClientboundAddEntityPacket remapEntitySpawn(ClientboundAddEntityPacket packet) {
        EntityType<?> type = packet.getType();
        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (typeId != null && typeId.toString().equals(LegacyLinkConstants.SULFUR_CUBE_ENTITY_ID)) {
            TranslationStats.recordEntityRemap();
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
}
