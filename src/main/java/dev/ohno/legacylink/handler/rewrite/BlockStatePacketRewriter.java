package dev.ohno.legacylink.handler.rewrite;

import dev.ohno.legacylink.LegacyLinkMod;
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
import java.util.concurrent.atomic.AtomicLong;

public final class BlockStatePacketRewriter {

    private static final Field SECTION_STATES_FIELD;
    private static final Field SECTION_POS_FIELD;
    private static final boolean TRACE_SECTION_BLOCKS_UPDATE =
            Boolean.getBoolean("legacylink.traceSectionBlocksUpdate");
    private static final AtomicLong SECTION_PACKET_SEQ = new AtomicLong();

    static {
        try {
            SECTION_STATES_FIELD = ClientboundSectionBlocksUpdatePacket.class.getDeclaredField("states");
            SECTION_STATES_FIELD.setAccessible(true);
            Field posField;
            try {
                posField = ClientboundSectionBlocksUpdatePacket.class.getDeclaredField("sectionPos");
                posField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                posField = null;
            }
            SECTION_POS_FIELD = posField;
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
            long seq = SECTION_PACKET_SEQ.incrementAndGet();
            BlockState[] original = (BlockState[]) SECTION_STATES_FIELD.get(packet);
            if (original == null) {
                throw new IllegalStateException("[LegacyLink] remapSectionBlocksUpdate: packet states array is null");
            }
            boolean needsRemap = false;
            int nullStatesBefore = 0;
            String preSample = sampleStateIds(original, false);
            for (BlockState state : original) {
                if (state == null) {
                    nullStatesBefore++;
                    needsRemap = true;
                    continue;
                }
                int oldId = Block.BLOCK_STATE_REGISTRY.getId(state);
                if (RegistryRemapper.remapBlockState(oldId) != oldId) {
                    needsRemap = true;
                    break;
                }
            }
            if (TRACE_SECTION_BLOCKS_UPDATE || nullStatesBefore > 0) {
                LegacyLinkMod.LOGGER.info(
                        "[LegacyLink][SectionBlocksTrace] seq={} phase=pre section={} entries={} null_states={} needs_remap={} sample={}",
                        seq,
                        sectionPosString(packet),
                        original.length,
                        nullStatesBefore,
                        needsRemap,
                        preSample
                );
            }
            if (!needsRemap) {
                return packet;
            }

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundSectionBlocksUpdatePacket.STREAM_CODEC.encode(buf, packet);
                ClientboundSectionBlocksUpdatePacket copy = ClientboundSectionBlocksUpdatePacket.STREAM_CODEC.decode(buf);
                BlockState[] states = (BlockState[]) SECTION_STATES_FIELD.get(copy);
                if (states == null) {
                    throw new IllegalStateException("[LegacyLink] remapSectionBlocksUpdate: decoded states array is null");
                }
                int remapped = 0;
                for (int i = 0; i < states.length; i++) {
                    BlockState state = states[i];
                    if (state == null) {
                        throw new IllegalStateException(
                                "[LegacyLink] remapSectionBlocksUpdate: null BlockState after decode at index "
                                        + i + " (section=" + sectionPosString(packet) + "); wire id would be invalid for clients");
                    }
                    int oldId = Block.BLOCK_STATE_REGISTRY.getId(state);
                    int wireId = RegistryRemapper.remapBlockState(oldId);
                    BlockState resolved = Block.BLOCK_STATE_REGISTRY.byId(wireId);
                    if (resolved == null) {
                        throw new IllegalStateException(
                                "[LegacyLink] remapSectionBlocksUpdate: byId(" + wireId + ") is null (from server state id "
                                        + oldId + ", section=" + sectionPosString(packet)
                                        + "); mapping tables must match this server's registry (see RegistryRemapper startup check)");
                    }
                    states[i] = resolved;
                    if (wireId != oldId) {
                        remapped++;
                    }
                }
                if (remapped > 0) {
                    TranslationStats.recordSectionBlocksRemap(remapped);
                }
                if (TRACE_SECTION_BLOCKS_UPDATE) {
                    LegacyLinkMod.LOGGER.info(
                            "[LegacyLink][SectionBlocksTrace] seq={} phase=post section={} entries={} remapped={} sample={}",
                            seq,
                            sectionPosString(packet),
                            states.length,
                            remapped,
                            sampleStateIds(states, true)
                    );
                }
                return copy;
            } finally {
                ReferenceCountUtil.release(buf.unwrap());
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("[LegacyLink] remapSectionBlocksUpdate: states field access failed", e);
        }
    }

    private static String sectionPosString(ClientboundSectionBlocksUpdatePacket packet) {
        if (SECTION_POS_FIELD == null) {
            return "unknown";
        }
        try {
            Object value = SECTION_POS_FIELD.get(packet);
            return value == null ? "null" : value.toString();
        } catch (IllegalAccessException e) {
            return "unreadable";
        }
    }

    private static String sampleStateIds(BlockState[] states, boolean mapped) {
        int limit = Math.min(states.length, 8);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append(',');
            }
            BlockState s = states[i];
            if (s == null) {
                sb.append(i).append(":null");
                continue;
            }
            int oldId = Block.BLOCK_STATE_REGISTRY.getId(s);
            if (!mapped) {
                sb.append(i).append(':').append(oldId);
            } else {
                int newId = RegistryRemapper.remapBlockState(oldId);
                sb.append(i).append(':').append(oldId).append("->").append(newId);
            }
        }
        if (states.length > limit) {
            sb.append(",...");
        }
        return sb.toString();
    }

    private BlockStatePacketRewriter() {}
}
