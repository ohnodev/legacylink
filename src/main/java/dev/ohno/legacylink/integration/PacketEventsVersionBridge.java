package dev.ohno.legacylink.integration;

import dev.ohno.legacylink.LegacyLinkMod;
import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;

import java.lang.reflect.Method;

/**
 * Pins PacketEvents/Reaper to legacy-facing client semantics for LegacyLink sessions.
 * Legacy clients still use protocol 775 on the wire; this only controls PacketEvents version-gated logic.
 */
public final class PacketEventsVersionBridge {

    private static volatile boolean warnedUnavailable;
    private static final AttributeKey<Boolean> NORMALIZED_ATTR = AttributeKey.valueOf("legacylink_pe_normalized");

    private PacketEventsVersionBridge() {}

    public static void force26_2IfPresent(Connection connection) {
        if (Boolean.TRUE.equals(connection.channel.attr(NORMALIZED_ATTR).get())) {
            return;
        }
        try {
            Class<?> packetEventsClass = Class.forName("com.github.retrooper.packetevents.PacketEvents");
            Object api = packetEventsClass.getMethod("getAPI").invoke(null);
            if (api == null) {
                return;
            }

            Method getProtocolManager = api.getClass().getMethod("getProtocolManager");
            Object protocolManager = getProtocolManager.invoke(api);
            if (protocolManager == null) {
                return;
            }

            Method getUser = protocolManager.getClass().getMethod("getUser", Object.class);
            Object user = getUser.invoke(protocolManager, connection.channel);
            if (user == null) {
                return;
            }

            Class<?> clientVersionClass = Class.forName("com.github.retrooper.packetevents.protocol.player.ClientVersion");
            Object normalizedVersion = resolveLegacyClientVersion(clientVersionClass);
            if (normalizedVersion == null) {
                // PacketEvents present, but this build does not expose expected snapshot constants.
                logUnavailableOnce();
                return;
            }

            Method setClientVersion = user.getClass().getMethod("setClientVersion", clientVersionClass);
            setClientVersion.invoke(user, normalizedVersion);
            connection.channel.attr(NORMALIZED_ATTR).set(Boolean.TRUE);
        } catch (ClassNotFoundException e) {
            // PacketEvents is optional from LegacyLink's perspective.
            logUnavailableOnce();
        } catch (ReflectiveOperationException e) {
            LegacyLinkMod.LOGGER.warn(
                    "[LegacyLink] Failed to force PacketEvents user client version to V_26_2 for {}: {}",
                    connection.getRemoteAddress(),
                    e.toString()
            );
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object resolveLegacyClientVersion(Class<?> clientVersionClass) {
        Class<? extends Enum> enumClass = (Class<? extends Enum>) clientVersionClass.asSubclass(Enum.class);
        try {
            /*
             * PacketEvents uses enum ordinal as a tie-breaker when protocol ids are shared.
             * For protocol 775, V_26_1 keeps pre-26.2 movement/attribute gates in Grim.
             */
            return Enum.valueOf(enumClass, "V_26_1");
        } catch (IllegalArgumentException ignored) {
            // Older/newer PE builds may only expose V_26_2.
        }
        try {
            return Enum.valueOf(enumClass, "V_26_2");
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void logUnavailableOnce() {
        if (warnedUnavailable) {
            return;
        }
        warnedUnavailable = true;
        LegacyLinkMod.LOGGER.debug("[LegacyLink] PacketEvents not present; skipping client-version normalization bridge.");
    }
}
