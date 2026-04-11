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
    private static volatile @org.jspecify.annotations.Nullable Field cameraEntityIdField;
    private static final Object CAMERA_FIELD_LOCK = new Object();

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

    private static void ensureCameraFieldResolved() {
        if (cameraEntityIdField != null) {
            return;
        }
        synchronized (CAMERA_FIELD_LOCK) {
            if (cameraEntityIdField != null) {
                return;
            }
            Field resolved = null;
            for (String name : new String[] {"cameraId", "id"}) {
                try {
                    Field f = ClientboundSetCameraPacket.class.getDeclaredField(name);
                    f.setAccessible(true);
                    resolved = f;
                    break;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // try next field name (mappings differ) or module access
                }
            }
            cameraEntityIdField = resolved;
        }
    }

    private static int readCameraEntityId(ClientboundSetCameraPacket packet) {
        ensureCameraFieldResolved();
        Field f = cameraEntityIdField;
        if (f == null) {
            return -1;
        }
        try {
            return f.getInt(packet);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
    }

    private CameraPacketTrace() {}
}
