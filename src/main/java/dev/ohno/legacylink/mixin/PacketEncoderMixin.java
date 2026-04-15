package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.LegacyLinkMod;
import dev.ohno.legacylink.connection.LegacyTracker;
import dev.ohno.legacylink.protocol.LegacyUpdateAttributesWirePatcher;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(PacketEncoder.class)
public abstract class PacketEncoderMixin<T extends PacketListener> {

    /**
     * Must match {@link dev.ohno.legacylink.handler.rewrite.BlockStatePacketRewriter}: when left always-on, INFO logging
     * here runs on the Netty thread for every legacy section update and can stall the event loop (disconnect.timeout).
     */
    private static final boolean TRACE_SECTION_BLOCKS_UPDATE =
            Boolean.getBoolean("legacylink.traceSectionBlocksUpdate");

    @Shadow
    @Final
    private ProtocolInfo<T> protocolInfo;
    private static final Field SECTION_STATES_FIELD;
    private static final Field SECTION_POS_FIELD;

    static {
        Field statesField;
        Field sectionPosField;
        try {
            statesField = ClientboundSectionBlocksUpdatePacket.class.getDeclaredField("states");
            try {
                statesField.setAccessible(true);
            } catch (RuntimeException e) {
                statesField = null;
            }
        } catch (NoSuchFieldException e) {
            statesField = null;
        }
        try {
            sectionPosField = ClientboundSectionBlocksUpdatePacket.class.getDeclaredField("sectionPos");
            try {
                sectionPosField.setAccessible(true);
            } catch (RuntimeException e) {
                sectionPosField = null;
            }
        } catch (NoSuchFieldException e) {
            sectionPosField = null;
        }
        SECTION_STATES_FIELD = statesField;
        SECTION_POS_FIELD = sectionPosField;
    }

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

    @Inject(method = "encode", at = @At("HEAD"))
    private void legacylink$traceSectionBlocksEncode(
            ChannelHandlerContext ctx,
            Packet<?> packet,
            ByteBuf output,
            CallbackInfo ci
    ) {
        if (!(packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket)) {
            return;
        }
        if (this.protocolInfo.id() != ConnectionProtocol.PLAY) {
            return;
        }
        Object handler = ctx.pipeline().get(HandlerNames.PACKET_HANDLER);
        if (!(handler instanceof Connection connection) || !LegacyTracker.isLegacy(connection)) {
            return;
        }
        if (!TRACE_SECTION_BLOCKS_UPDATE) {
            return;
        }
        int entries = -1;
        int nullStates = -1;
        String section = "unknown";
        try {
            if (SECTION_STATES_FIELD != null) {
                BlockState[] states = (BlockState[]) SECTION_STATES_FIELD.get(sectionPacket);
                entries = states == null ? -1 : states.length;
                if (states != null) {
                    int n = 0;
                    for (BlockState state : states) {
                        if (state == null) {
                            n++;
                        }
                    }
                    nullStates = n;
                }
            }
            if (SECTION_POS_FIELD != null) {
                Object pos = SECTION_POS_FIELD.get(sectionPacket);
                if (pos != null) {
                    section = pos.toString();
                }
            }
        } catch (IllegalAccessException ignored) {
            // best-effort trace only
        }
        LegacyLinkMod.LOGGER.info(
                "[LegacyLink][SectionBlocksEncode] remote={} section={} entries={} null_states={}",
                connection.getRemoteAddress(),
                section,
                entries,
                nullStates
        );
    }
}
