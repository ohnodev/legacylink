package dev.ohno.legacylink.connection;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;

public final class LegacyTracker {

    private static final AttributeKey<Boolean> LEGACY_KEY =
            AttributeKey.valueOf("legacylink:is_legacy");

    public static void markLegacy(Connection connection) {
        Channel ch = connection.channel;
        ch.attr(LEGACY_KEY).set(Boolean.TRUE);
    }

    public static boolean isLegacy(Connection connection) {
        Channel ch = connection.channel;
        Boolean val = ch.attr(LEGACY_KEY).get();
        return val != null && val;
    }

    public static boolean isLegacy(Channel channel) {
        Boolean val = channel.attr(LEGACY_KEY).get();
        return val != null && val;
    }

    private LegacyTracker() {}
}
