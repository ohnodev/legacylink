package dev.ohno.legacylink.protocol;

import dev.ohno.legacylink.mapping.LegacyAttributeWireTable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Post-encodes {@code update_attributes}: rewrites each attribute holder varint from the server's registry id to the
 * legacy client's id. Any parse or mapping failure throws (no silent skip / buffer restore).
 */
public final class LegacyUpdateAttributesWirePatcher {

    private record ParsedAttr(int holderNetworkId, double base, List<AttributeModifier> modifiers) {}

    public static void rewriteBuffer(ByteBuf buffer) {
        if (!LegacyAttributeWireTable.isReady()) {
            throw new IllegalStateException(
                    "[LegacyLink] LegacyAttributeWireTable not ready; cannot encode update_attributes for legacy client");
        }
        int readerStart = buffer.readerIndex();
        FriendlyByteBuf in = new FriendlyByteBuf(buffer);
        int packetId = in.readVarInt();
        int entityId = in.readVarInt();
        int count = in.readVarInt();
        List<ParsedAttr> parsed = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int serverHolderId = in.readVarInt();
            double base = in.readDouble();
            int modCount = in.readVarInt();
            List<AttributeModifier> mods = new ArrayList<>(modCount);
            for (int m = 0; m < modCount; m++) {
                mods.add(AttributeModifier.STREAM_CODEC.decode(in));
            }
            parsed.add(new ParsedAttr(serverHolderId, base, mods));
        }
        if (in.readableBytes() != 0) {
            throw new IllegalStateException(
                    "[LegacyLink] update_attributes wire buffer has " + in.readableBytes() + " trailing byte(s)");
        }

        List<ParsedAttr> out = new ArrayList<>(parsed.size());
        for (ParsedAttr p : parsed) {
            int legacyId = LegacyAttributeWireTable.toLegacyHolderNetworkId(p.holderNetworkId());
            if (legacyId < 0) {
                throw new IllegalStateException(
                        "[LegacyLink] No legacy holder id for server network id " + p.holderNetworkId());
            }
            out.add(new ParsedAttr(legacyId, p.base(), p.modifiers()));
        }

        ByteBuf tmp = Unpooled.buffer();
        try {
            FriendlyByteBuf fo = new FriendlyByteBuf(tmp);
            fo.writeVarInt(packetId);
            fo.writeVarInt(entityId);
            fo.writeVarInt(out.size());
            for (ParsedAttr p : out) {
                fo.writeVarInt(p.holderNetworkId());
                fo.writeDouble(p.base());
                fo.writeVarInt(p.modifiers().size());
                for (AttributeModifier m : p.modifiers()) {
                    AttributeModifier.STREAM_CODEC.encode(fo, m);
                }
            }

            buffer.readerIndex(readerStart);
            buffer.writerIndex(readerStart);
            buffer.writeBytes(tmp);
        } finally {
            tmp.release();
        }
    }

    private LegacyUpdateAttributesWirePatcher() {}
}
