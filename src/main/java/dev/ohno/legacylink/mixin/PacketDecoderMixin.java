package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.encoding.LegacyInboundDecoding;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PacketDecoder.class)
public abstract class PacketDecoderMixin {

    @Inject(method = "decode", at = @At("HEAD"))
    private void legacylink$inboundWireContextEnter(
            ChannelHandlerContext ctx,
            ByteBuf input,
            List<Object> out,
            CallbackInfo ci
    ) {
        LegacyInboundDecoding.enterDecode(ctx.channel());
    }

    @Inject(method = "decode", at = @At("RETURN"))
    private void legacylink$inboundWireContextExitReturn(
            ChannelHandlerContext ctx,
            ByteBuf input,
            List<Object> out,
            CallbackInfo ci
    ) {
        LegacyInboundDecoding.leaveDecode();
    }

    @Inject(method = "decode", at = @At(value = "THROW"))
    private void legacylink$inboundWireContextExitThrow(
            ChannelHandlerContext ctx,
            ByteBuf input,
            List<Object> out,
            CallbackInfo ci
    ) {
        LegacyInboundDecoding.leaveDecode();
    }
}
