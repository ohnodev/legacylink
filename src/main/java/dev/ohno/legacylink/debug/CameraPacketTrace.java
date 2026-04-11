package dev.ohno.legacylink.debug;

import dev.ohno.legacylink.LegacyLinkMod;
import dev.ohno.legacylink.connection.LegacyTracker;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.Packet;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logs {@link ClientboundSetCameraPacket} (spectator / forced camera entity id).
 * <p>
 * Server JVM: {@code -Dlegacylink.traceCamera=true} — grep {@code [LegacyLink][CameraTrace]}.
 */
public final class CameraPacketTrace {

    private static final AtomicLong SEQ = new AtomicLong();

    public static boolean enabled() {
        String v = System.getProperty("legacylink.traceCamera", "");
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    public static void traceOutbound(Connection connection, Packet<?> packet, String stage) {
        if (!enabled() || !(packet instanceof ClientboundSetCameraPacket)) {
            return;
        }
        int cameraEid = readCameraEntityId((ClientboundSetCameraPacket) packet);
        boolean legacy = LegacyTracker.isLegacy(connection);
        LegacyLinkMod.LOGGER.warn(
                "[LegacyLink][CameraTrace] seq={} stage={} legacy={} remote={} cameraEntityId={}",
                SEQ.incrementAndGet(),
                stage,
                legacy,
                connection.getRemoteAddress(),
                cameraEid
        );
    }

    private static int readCameraEntityId(ClientboundSetCameraPacket packet) {
        for (String name : new String[] {"cameraId", "id"}) {
            try {
                Field f = ClientboundSetCameraPacket.class.getDeclaredField(name);
                f.setAccessible(true);
                return f.getInt(packet);
            } catch (ReflectiveOperationException ignored) {
                // try next field name (mappings differ)
            }
        }
        return -1;
    }

    private CameraPacketTrace() {}
}
