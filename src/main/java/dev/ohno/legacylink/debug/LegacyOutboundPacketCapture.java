package dev.ohno.legacylink.debug;

import dev.ohno.legacylink.LegacyLinkMod;
import dev.ohno.legacylink.connection.LegacyTracker;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.BundleDelimiterPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Logs <b>every</b> clientbound packet for {@link LegacyTracker#isLegacy legacy} connections so {@code logs/latest.log}
 * can be diffed (e.g. 26.1 client vs baseline). Two stages:
 * <ul>
 *   <li>{@code connection_send} — right as {@link net.minecraft.network.Connection#send} runs (pre-Netty tail;
 *       bundles nested).</li>
 *   <li>{@code post_legacy_rewrite} — after {@link dev.ohno.legacylink.handler.LegacyPacketHandler} translation and
 *       bundle flattening (matches what {@link net.minecraft.network.PacketEncoder} encodes per frame).</li>
 * </ul>
 * <p>
 * Enable: {@code -Dlegacylink.captureOutbound=true} or env {@code LEGACYLINK_CAPTURE_OUTBOUND=1} / {@code true} — grep {@code [LegacyLink][OutboundCapture]}.
 * {@link EntityDataRewriteTrace} also turns on with capture outbound; or use {@code -Dlegacylink.traceEntityDataRewrite=true} alone.
 * <p>
 * <b>Warning:</b> Very verbose; disable after capture. Large payloads (chunks) are summarized only.
 */
public final class LegacyOutboundPacketCapture {

    private static final boolean CAPTURE_OUTBOUND_ENABLED = outboundCaptureEnabled();

    private static final AtomicLong SEQ = new AtomicLong();
    private static final AtomicReference<Field> MOVE_ENTITY_ID_REF = new AtomicReference<>();
    private static final AtomicReference<Field> ROTATE_HEAD_ENTITY_ID_REF = new AtomicReference<>();
    private static volatile @Nullable Field cameraEntityIdField;

    /**
     * {@code LEGACYLINK_CAPTURE_OUTBOUND} is checked first; then system property {@code legacylink.captureOutbound}.
     * Truthy values: {@code 1} or {@code true} (case-insensitive for {@code true}).
     */
    private static boolean outboundCaptureEnabled() {
        String env = System.getenv("LEGACYLINK_CAPTURE_OUTBOUND");
        if (env != null) {
            String t = env.trim();
            if ("1".equals(t) || "true".equalsIgnoreCase(t)) {
                return true;
            }
        }
        String v = System.getProperty("legacylink.captureOutbound", "");
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    public static boolean enabled() {
        return CAPTURE_OUTBOUND_ENABLED;
    }

    public static void logIfLegacy(Connection connection, Packet<?> packet, String stage) {
        if (!enabled() || connection == null || packet == null || !LegacyTracker.isLegacy(connection)) {
            return;
        }
        logRecursive(connection, packet, stage, "");
    }

    private static void logRecursive(Connection connection, Packet<?> packet, String stage, String path) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            int count = 0;
            for (Packet<? super ClientGamePacketListener> ignored : bundle.subPackets()) {
                count++;
            }
            emit(connection, stage, path, Identifier.withDefaultNamespace("bundle"), "ClientboundBundlePacket",
                    "subPackets=" + count);
            int i = 0;
            for (Packet<? super ClientGamePacketListener> sub : bundle.subPackets()) {
                logRecursive(connection, sub, stage, path + "bundle[" + i++ + "].");
            }
            return;
        }
        if (packet instanceof BundleDelimiterPacket) {
            emit(connection, stage, path, Identifier.withDefaultNamespace("bundle_delimiter"),
                    "BundleDelimiterPacket", "");
            return;
        }
        Identifier typeId = packet.type().id();
        String cls = packet.getClass().getSimpleName();
        emit(connection, stage, path, typeId, cls, summarize(packet));
    }

    private static void emit(
            Connection connection,
            String stage,
            String path,
            Identifier typeId,
            String classSimple,
            String detail
    ) {
        long seq = SEQ.incrementAndGet();
        LegacyLinkMod.LOGGER.info(
                "[LegacyLink][OutboundCapture] seq={} stage={} remote={} path={} packetType={} class={} detail={}",
                seq,
                stage,
                connection.getRemoteAddress(),
                path.isEmpty() ? "-" : path,
                typeId,
                classSimple,
                detail
        );
    }

    private static String summarize(Packet<?> packet) {
        if (packet instanceof ClientboundPlayerPositionPacket p) {
            Vec3 pos = p.change().position();
            Vec3 d = p.change().deltaMovement();
            return String.format(java.util.Locale.ROOT,
                    "teleportId=%d pos=(%.4f,%.4f,%.4f) delta=(%.4f,%.4f,%.4f) yRot=%.4f xRot=%.4f relatives=%s",
                    p.id(), pos.x, pos.y, pos.z, d.x, d.y, d.z, p.change().yRot(), p.change().xRot(), p.relatives());
        }
        if (packet instanceof ClientboundTeleportEntityPacket p) {
            Vec3 pos = p.change().position();
            Vec3 d = p.change().deltaMovement();
            return String.format(java.util.Locale.ROOT,
                    "entityId=%d pos=(%.4f,%.4f,%.4f) delta=(%.4f,%.4f,%.4f) onGround=%s relatives=%s",
                    p.id(), pos.x, pos.y, pos.z, d.x, d.y, d.z, p.onGround(), p.relatives());
        }
        if (packet instanceof ClientboundMoveEntityPacket p) {
            int eid = moveEntityId(p);
            return String.format(java.util.Locale.ROOT,
                    "entityId=%d dPos=(%s,%s,%s) yRot=%s xRot=%s hasPos=%s hasRot=%s onGround=%s wireType=%s",
                    eid,
                    p.getXa(),
                    p.getYa(),
                    p.getZa(),
                    p.getYRot(),
                    p.getXRot(),
                    p.hasPosition(),
                    p.hasRotation(),
                    p.isOnGround(),
                    p.type().id());
        }
        if (packet instanceof ClientboundRotateHeadPacket p) {
            return "entityId=" + rotateHeadEntityId(p) + " yHeadRot=" + p.getYHeadRot();
        }
        if (packet instanceof ClientboundSetCameraPacket) {
            return "cameraEntityId=" + cameraEntityId((ClientboundSetCameraPacket) packet);
        }
        if (packet instanceof ClientboundSetEntityDataPacket p) {
            return formatSetEntityDataDetail(p);
        }
        if (packet instanceof ClientboundUpdateAttributesPacket p) {
            return "entityId=" + p.getEntityId() + " snapshots=" + p.getValues().size();
        }
        if (packet instanceof ClientboundLevelChunkWithLightPacket) {
            return "chunk_with_light (payload omitted)";
        }
        if (packet instanceof ClientboundDisconnectPacket p) {
            String text;
            try {
                text = p.reason() == null ? "<null>" : p.reason().getString();
            } catch (RuntimeException e) {
                text = "<unreadable:" + e.getClass().getSimpleName() + ">";
            }
            String sanitized = text.replace('\r', ' ').replace('\n', ' ');
            return "reason=\"" + sanitized + "\"";
        }
        return "-";
    }

    private static int moveEntityId(ClientboundMoveEntityPacket p) {
        return PacketReflectionUtil.getIntField(p, ClientboundMoveEntityPacket.class, "entityId", MOVE_ENTITY_ID_REF);
    }

    private static int rotateHeadEntityId(ClientboundRotateHeadPacket p) {
        return PacketReflectionUtil.getIntField(p, ClientboundRotateHeadPacket.class, "entityId", ROTATE_HEAD_ENTITY_ID_REF);
    }

    private static int cameraEntityId(ClientboundSetCameraPacket packet) {
        try {
            Field f = cameraEntityIdField;
            if (f == null) {
                for (String name : new String[] {"cameraId", "id"}) {
                    try {
                        f = ClientboundSetCameraPacket.class.getDeclaredField(name);
                        f.setAccessible(true);
                        cameraEntityIdField = f;
                        break;
                    } catch (NoSuchFieldException ignored) {
                        f = null;
                    }
                }
            }
            if (f == null) {
                return -1;
            }
            return f.getInt(packet);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private static String formatSetEntityDataDetail(ClientboundSetEntityDataPacket p) {
        List<SynchedEntityData.DataValue<?>> items = p.packedItems();
        int max = -1;
        StringBuilder idSb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            SynchedEntityData.DataValue<?> v = items.get(i);
            int id = v.id();
            max = Math.max(max, id);
            if (i > 0) {
                idSb.append(',');
            }
            idSb.append(id);
        }
        return "entityId=" + p.id() + " count=" + items.size() + " maxId=" + max + " ids=[" + idSb + "]";
    }

    private LegacyOutboundPacketCapture() {}
}
