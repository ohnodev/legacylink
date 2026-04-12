package dev.ohno.legacylink.integration;

import dev.ohno.legacylink.LegacyLinkMod;
import net.minecraft.network.Connection;

import java.lang.reflect.Method;

/**
 * Keeps PacketEvents/Reaper on a normalized modern client version for LegacyLink sessions.
 * Legacy clients still use the 26.1 wire protocol; this only controls PacketEvents version-gated logic.
 */
public final class PacketEventsVersionBridge {

    private static volatile boolean warnedUnavailable;

    private PacketEventsVersionBridge() {}

    public static void force26_2IfPresent(Connection connection) {
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
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object normalizedVersion = Enum.valueOf((Class<? extends Enum>) clientVersionClass.asSubclass(Enum.class), "V_26_2");

            Method setClientVersion = user.getClass().getMethod("setClientVersion", clientVersionClass);
            setClientVersion.invoke(user, normalizedVersion);
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

    private static void logUnavailableOnce() {
        if (warnedUnavailable) {
            return;
        }
        warnedUnavailable = true;
        LegacyLinkMod.LOGGER.debug("[LegacyLink] PacketEvents not present; skipping client-version normalization bridge.");
    }
}
