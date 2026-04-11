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

    /**
     * Prefer {@link #enterScoped(Connection)} with try-with-resources so the thread-local is always cleared.
     */
    public static Scope enterScoped(@Nullable Connection connection) {
        CURRENT.set(connection);
        return new Scope();
    }

    private static void clear() {
        CURRENT.remove();
    }

    public static @Nullable Connection connection() {
        return CURRENT.get();
    }

    public static final class Scope implements AutoCloseable {
        private Scope() {}

        @Override
        public void close() {
            LegacyOutboundEncoding.clear();
        }
    }
}
