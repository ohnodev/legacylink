package dev.ohno.legacylink.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ohno.legacylink.encoding.LegacyInboundDecoding;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketDecoder;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin(PacketDecoder.class)
public abstract class PacketDecoderMixin {

    /**
     * Sponge Mixin on this stack does not accept {@code @At("THROW")}. Wrap the whole decode so we always
     * {@link LegacyInboundDecoding#leaveDecode()} (including on codec exceptions).
     */
    @WrapMethod(method = "decode")
    private void legacylink$wrapDecode(
            ChannelHandlerContext ctx,
            ByteBuf input,
            List<Object> out,
            Operation<Void> original
    ) throws Exception {
        LegacyInboundDecoding.enterDecode(ctx.channel());
        try {
            original.call(ctx, input, out);
        } finally {
            LegacyInboundDecoding.leaveDecode();
        }
    }
}
