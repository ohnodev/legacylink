package dev.ohno.legacylink.encoding;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import org.jspecify.annotations.Nullable;

/**
 * Thread-local {@link Channel} for the duration of {@link net.minecraft.network.PacketDecoder#decode} so
 * {@link net.minecraft.world.item.ItemStack} (and related) codecs can tell if the inbound packet is from a legacy client.
 * <p>
 * Uses a nesting depth so a nested {@code decode} (same thread) does not clear the channel while the outer packet is
 * still being parsed.
 */
public final class LegacyInboundDecoding {

    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();
    private static final ThreadLocal<Channel> CHANNEL = new ThreadLocal<>();

    private LegacyInboundDecoding() {}

    public static void enterDecode(Channel channel) {
        int d = DEPTH.get() == null ? 0 : DEPTH.get();
        if (d == 0) {
            CHANNEL.set(channel);
        }
        DEPTH.set(d + 1);
    }

    public static void leaveDecode() {
        Integer d0 = DEPTH.get();
        if (d0 == null) {
            return;
        }
        int d = d0 - 1;
        if (d <= 0) {
            DEPTH.remove();
            CHANNEL.remove();
        } else {
            DEPTH.set(d);
        }
    }

    public static @Nullable Connection connection() {
        Channel ch = CHANNEL.get();
        if (ch == null) {
            return null;
        }
        Object h = ch.pipeline().get(HandlerNames.PACKET_HANDLER);
        return h instanceof Connection c ? c : null;
    }
}
