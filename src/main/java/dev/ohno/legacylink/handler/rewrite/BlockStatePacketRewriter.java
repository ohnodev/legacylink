package dev.ohno.legacylink.handler.rewrite;

import dev.ohno.legacylink.mapping.RegistryRemapper;
import dev.ohno.legacylink.telemetry.TranslationStats;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;

public final class BlockStatePacketRewriter {

    private static final Field SECTION_STATES_FIELD;

    static {
        try {
            SECTION_STATES_FIELD = ClientboundSectionBlocksUpdatePacket.class.getDeclaredField("states");
            SECTION_STATES_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static ClientboundBlockUpdatePacket remapBlockUpdate(ClientboundBlockUpdatePacket packet) {
        BlockState state = packet.getBlockState();
        int stateId = Block.BLOCK_STATE_REGISTRY.getId(state);
        int remapped = RegistryRemapper.remapBlockState(stateId);
        if (remapped != stateId) {
            BlockState resolved = Block.BLOCK_STATE_REGISTRY.byId(remapped);
            if (resolved == null) {
                throw new IllegalStateException(
                        "[LegacyLink] remapBlockUpdate: no BlockState for id " + remapped + " (from " + stateId + ")");
            }
            TranslationStats.recordBlockRemap();
            return new ClientboundBlockUpdatePacket(packet.getPos(), resolved);
        }
        return packet;
    }

    public static ClientboundSectionBlocksUpdatePacket remapSectionBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet) {
        try {
            BlockState[] original = (BlockState[]) SECTION_STATES_FIELD.get(packet);
            boolean needsRemap = false;
            for (BlockState state : original) {
                int oldId = Block.BLOCK_STATE_REGISTRY.getId(state);
                if (RegistryRemapper.remapBlockState(oldId) != oldId) {
                    needsRemap = true;
                    break;
                }
            }
            if (!needsRemap) {
                return packet;
            }

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundSectionBlocksUpdatePacket.STREAM_CODEC.encode(buf, packet);
                ClientboundSectionBlocksUpdatePacket copy = ClientboundSectionBlocksUpdatePacket.STREAM_CODEC.decode(buf);
                BlockState[] states = (BlockState[]) SECTION_STATES_FIELD.get(copy);
                int remapped = 0;
                for (int i = 0; i < states.length; i++) {
                    BlockState state = states[i];
                    int oldId = Block.BLOCK_STATE_REGISTRY.getId(state);
                    int newId = RegistryRemapper.remapBlockState(oldId);
                    if (newId != oldId) {
                        BlockState resolved = Block.BLOCK_STATE_REGISTRY.byId(newId);
                        if (resolved == null) {
                            throw new IllegalStateException(
                                    "[LegacyLink] remapSectionBlocksUpdate: no BlockState for id " + newId + " (from " + oldId + ")");
                        }
                        states[i] = resolved;
                        remapped++;
                    }
                }
                if (remapped > 0) {
                    TranslationStats.recordSectionBlocksRemap(remapped);
                }
                return copy;
            } finally {
                ReferenceCountUtil.release(buf.unwrap());
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("[LegacyLink] remapSectionBlocksUpdate: states field access failed", e);
        }
    }

    private BlockStatePacketRewriter() {}
}
