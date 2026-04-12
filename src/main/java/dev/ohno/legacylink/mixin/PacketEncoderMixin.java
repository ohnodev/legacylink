package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.connection.LegacyTracker;
import dev.ohno.legacylink.encoding.LegacyOutboundEncoding;
import dev.ohno.legacylink.protocol.LegacyUpdateAttributesWirePatcher;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketEncoder.class)
public abstract class PacketEncoderMixin<T extends PacketListener> {

    @Shadow
    @Final
    private ProtocolInfo<T> protocolInfo;

    @Inject(method = "encode", at = @At("TAIL"))
    private void legacylink$remapAttributeHolderIds(
            ChannelHandlerContext ctx,
            Packet<?> packet,
            ByteBuf output,
            CallbackInfo ci
    ) {
        if (!(packet instanceof ClientboundUpdateAttributesPacket)) {
            return;
        }
        if (this.protocolInfo.id() != ConnectionProtocol.PLAY) {
            return;
        }
        Object handler = ctx.pipeline().get(HandlerNames.PACKET_HANDLER);
        if (!(handler instanceof Connection connection)) {
            return;
        }
        if (!LegacyTracker.isLegacy(connection)) {
            return;
        }
        LegacyUpdateAttributesWirePatcher.rewriteBuffer(output);
    }

    /**
     * Ensure codec-level legacy remaps (e.g. ItemStackTemplate in update_advancements) run with an active
     * outbound connection context during the actual PacketEncoder.encode call.
     */
    @WrapMethod(method = "encode")
    private void legacylink$encodeWithLegacyContext(
            ChannelHandlerContext ctx,
            Packet<?> packet,
            ByteBuf output,
            Operation<Void> original
    ) throws Exception {
        Object handler = ctx.pipeline().get(HandlerNames.PACKET_HANDLER);
        Connection encodeConn = handler instanceof Connection c ? c : null;
        if (encodeConn == null || !LegacyTracker.isLegacy(encodeConn)) {
            original.call(ctx, packet, output);
            return;
        }
        try (LegacyOutboundEncoding.Scope ignored = LegacyOutboundEncoding.enterScoped(encodeConn)) {
            original.call(ctx, packet, output);
        }
    }
}
