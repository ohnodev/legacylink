package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.handler.rewrite.BlockStatePacketRewriter;
import dev.ohno.legacylink.connection.LegacyTracker;
import dev.ohno.legacylink.debug.CameraPacketTrace;
import dev.ohno.legacylink.debug.LegacyOutboundPacketCapture;
import dev.ohno.legacylink.debug.PositionPacketTrace;
import dev.ohno.legacylink.debug.SpawnPacketTrace;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

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
        boolean legacy = LegacyTracker.isLegacy(connection);
        if (legacy) {
            out = remapSectionBlockUpdates(out);
            LegacyOutboundPacketCapture.logIfLegacy(connection, out, "connection_send");
        }
        if (!legacy) {
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

    @SuppressWarnings("unchecked")
    private static Packet<?> remapSectionBlockUpdates(Packet<?> packet) {
        if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdatePacket) {
            return BlockStatePacketRewriter.remapSectionBlocksUpdate(sectionUpdatePacket);
        }
        if (!(packet instanceof ClientboundBundlePacket bundlePacket)) {
            return packet;
        }
        List<Packet<? super ClientGamePacketListener>> rewrittenSubPackets = new ArrayList<>();
        boolean changed = false;
        for (Packet<? super ClientGamePacketListener> subPacket : bundlePacket.subPackets()) {
            Packet<?> rewritten = remapSectionBlockUpdates((Packet<?>) subPacket);
            rewrittenSubPackets.add((Packet<? super ClientGamePacketListener>) rewritten);
            if (rewritten != subPacket) {
                changed = true;
            }
        }
        return changed ? new ClientboundBundlePacket(rewrittenSubPackets) : packet;
    }
}
