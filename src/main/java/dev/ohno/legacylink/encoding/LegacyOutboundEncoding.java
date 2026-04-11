package dev.ohno.legacylink.encoding;

import net.minecraft.network.Connection;
import org.jspecify.annotations.Nullable;

/**
 * Holds the {@link Connection} currently encoding a clientbound play packet (Netty thread). Used by mixins that
 * run in {@link net.minecraft.network.PacketEncoder} after {@link dev.ohno.legacylink.handler.LegacyPacketHandler}.
 */
public final class LegacyOutboundEncoding {

    private static final ThreadLocal<Connection> CURRENT = new ThreadLocal<>();

    private LegacyOutboundEncoding() {}

    public static void enter(@Nullable Connection connection) {
        CURRENT.set(connection);
    }

    public static void leave() {
        CURRENT.remove();
    }

    public static @Nullable Connection connection() {
        return CURRENT.get();
    }
}
