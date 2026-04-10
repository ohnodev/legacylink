package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.LegacyLinkConstants;
import dev.ohno.legacylink.LegacyLinkMod;
import dev.ohno.legacylink.connection.LegacyTracker;
import dev.ohno.legacylink.handler.LegacyPacketHandler;
import net.minecraft.network.Connection;
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

    @Inject(method = "beginLogin", at = @At("HEAD"), cancellable = true)
    private void legacylink$acceptLegacyClient(ClientIntentionPacket packet, boolean transfer, CallbackInfo ci) {
        if (packet.protocolVersion() == LegacyLinkConstants.PROTOCOL_26_1) {
            LegacyLinkMod.LOGGER.info("[LegacyLink] Accepting 26.1 client from {}",
                    this.connection.getRemoteAddress());

            LegacyTracker.markLegacy(this.connection);
            LegacyPacketHandler.install(this.connection);

            this.connection.setupOutboundProtocol(LoginProtocols.CLIENTBOUND);
            this.connection.setupInboundProtocol(LoginProtocols.SERVERBOUND,
                    new ServerLoginPacketListenerImpl(this.server, this.connection, transfer));
            ci.cancel();
        }
    }

    @Inject(method = "handleIntention", at = @At("HEAD"))
    private void legacylink$markLegacyOnStatusPing(ClientIntentionPacket packet, CallbackInfo ci) {
        if (packet.protocolVersion() == LegacyLinkConstants.PROTOCOL_26_1) {
            LegacyTracker.markLegacy(this.connection);
            LegacyPacketHandler.install(this.connection);
        }
    }
}
