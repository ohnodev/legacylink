package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.LegacyLinkConstants;
import dev.ohno.legacylink.LegacyLinkMod;
import dev.ohno.legacylink.connection.LegacyTracker;
import dev.ohno.legacylink.handler.LegacyPacketHandler;
import dev.ohno.legacylink.integration.PacketEventsVersionBridge;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerHandshakePacketListenerImpl.class)
public abstract class HandshakeMixin {

    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private Connection connection;

    private static boolean isSupportedBridgePair(ClientIntentionPacket packet) {
        return SharedConstants.getProtocolVersion() == LegacyLinkConstants.PROTOCOL_26_2_SNAPSHOT_3
                && packet.protocolVersion() == LegacyLinkConstants.PROTOCOL_26_1_2;
    }

    @Inject(method = "beginLogin", at = @At("HEAD"), cancellable = true)
    private void legacylink$acceptLegacyClient(ClientIntentionPacket packet, boolean transfer, CallbackInfo ci) {
        if (isSupportedBridgePair(packet)) {
            LegacyLinkMod.LOGGER.info("[LegacyLink] Accepting 26.1 client from {}",
                    this.connection.getRemoteAddress());

            LegacyTracker.markLegacy(this.connection);
            PacketEventsVersionBridge.normalizeLegacyUserIfPresent(this.connection);
            LegacyPacketHandler.install(this.connection, "login");

            this.connection.setupOutboundProtocol(LoginProtocols.CLIENTBOUND);
            this.connection.setupInboundProtocol(LoginProtocols.SERVERBOUND,
                    new ServerLoginPacketListenerImpl(this.server, this.connection, transfer));
            ci.cancel();
        }
    }

    /**
     * Mark legacy before the handshake switch runs. Install the outbound translator only for {@link ClientIntent#STATUS}:
     * {@code beginLogin} (LOGIN / TRANSFER) also calls {@link LegacyPacketHandler#install}, so doing it here too logged
     * twice and removed/re-added the same handler for no benefit.
     */
    @Inject(method = "handleIntention", at = @At("HEAD"))
    private void legacylink$markLegacyOnHandshake(ClientIntentionPacket packet, CallbackInfo ci) {
        if (!isSupportedBridgePair(packet)) {
            return;
        }
        LegacyTracker.markLegacy(this.connection);
        PacketEventsVersionBridge.normalizeLegacyUserIfPresent(this.connection);
        ClientIntent intent = packet.intention();
        if (intent == ClientIntent.LOGIN || intent == ClientIntent.TRANSFER) {
            return;
        }
        LegacyPacketHandler.install(this.connection, "status");
    }
}
