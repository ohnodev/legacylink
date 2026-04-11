package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.connection.LegacyTracker;
import dev.ohno.legacylink.debug.CameraPacketTrace;
import dev.ohno.legacylink.debug.LegacyOutboundPacketCapture;
import dev.ohno.legacylink.debug.PositionPacketTrace;
import dev.ohno.legacylink.debug.SpawnPacketTrace;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Connection.class)
public abstract class ConnectionSendMixin {

    @ModifyVariable(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Packet<?> legacylink$rewriteLegacyClientboundPacket(Packet<?> packet) {
        Connection connection = (Connection) (Object) this;
        Packet<?> out = packet;
        if (LegacyTracker.isLegacy(connection)) {
            LegacyOutboundPacketCapture.logIfLegacy(connection, out, "connection_send");
        }
        // All legacy rewrites run in LegacyPacketHandler (Netty tail) so packets are translated once.
        if (!LegacyTracker.isLegacy(connection)) {
            if (PositionPacketTrace.enabled()) {
                PositionPacketTrace.traceOutbound(connection, out, "connection_send");
            }
            if (SpawnPacketTrace.enabled()) {
                SpawnPacketTrace.traceOutbound(connection, out, "connection_send");
            }
            if (CameraPacketTrace.enabled()) {
                CameraPacketTrace.traceOutbound(connection, out, "connection_send");
            }
        }
        return out;
    }
}
