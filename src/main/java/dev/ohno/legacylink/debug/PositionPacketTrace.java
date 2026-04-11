package dev.ohno.legacylink.debug;

import dev.ohno.legacylink.LegacyLinkMod;
import dev.ohno.legacylink.connection.LegacyTracker;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Compare 26.2 vs 26.1 movement by logging clientbound position-related packets.
 * <p>
 * Enable on the server JVM: {@code -Dlegacylink.tracePositions=true}
 * <p>
 * Two stages (grep {@code [LegacyLink][PosTrace]} in {@code logs/latest.log}):
 * <ul>
 *   <li>{@code stage=connection_send legacy=false} — what the server hands off for a <b>26.2</b> client
 *       ({@link net.minecraft.network.Connection#send} mixin, before the Netty pipeline; other mods may still
 *       alter 26.2 afterward).</li>
 *   <li>{@code stage=post_legacy_rewrite legacy=true} — what LegacyLink will put on the wire for a <b>26.1</b>
 *       client (after {@code LegacyPacketHandler} translation). Use this to diff against 26.2 when hunting
 *       snap-back / desync.</li>
 * </ul>
 * Join with 26.2 and 26.1 in turn; {@code seq} order is not comparable across sessions — match by time and type.
 */
public final class PositionPacketTrace {

    private static final AtomicLong SEQ = new AtomicLong();
    private static volatile Field moveEntityIdField;
    private static volatile Field rotateHeadEntityIdField;

    public static boolean enabled() {
        String v = System.getProperty("legacylink.tracePositions", "");
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    public static void traceOutbound(Connection connection, Packet<?> packet, String stage) {
        if (!enabled() || packet == null) {
            return;
        }
        boolean legacy = LegacyTracker.isLegacy(connection);
        String remote = String.valueOf(connection.getRemoteAddress());
        tracePacket(remote, legacy, stage, packet, "");
    }

    private static void tracePacket(String remote, boolean legacy, String stage, Packet<?> packet, String path) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            int i = 0;
            for (Packet<? super ClientGamePacketListener> sub : bundle.subPackets()) {
                tracePacket(remote, legacy, stage, sub, path + "bundle[" + i++ + "].");
            }
            return;
        }

        long seq = SEQ.incrementAndGet();
        String base = "[LegacyLink][PosTrace] seq=" + seq + " stage=" + stage + " legacy=" + legacy
                + " remote=" + remote + " path=" + path;

        if (packet instanceof ClientboundPlayerPositionPacket p) {
            Vec3 pos = p.change().position();
            Vec3 d = p.change().deltaMovement();
            LegacyLinkMod.LOGGER.info(
                    "{} type=player_position teleportId={} pos=({},{},{}) delta=({},{},{}) yRot={} xRot={} relatives={}",
                    base, p.id(), fmt(pos.x), fmt(pos.y), fmt(pos.z), fmt(d.x), fmt(d.y), fmt(d.z),
                    p.change().yRot(), p.change().xRot(), p.relatives()
            );
            return;
        }
        if (packet instanceof ClientboundTeleportEntityPacket p) {
            Vec3 pos = p.change().position();
            Vec3 d = p.change().deltaMovement();
            LegacyLinkMod.LOGGER.info(
                    "{} type=teleport_entity entityId={} pos=({},{},{}) delta=({},{},{}) yRot={} xRot={} onGround={} relatives={}",
                    base, p.id(), fmt(pos.x), fmt(pos.y), fmt(pos.z), fmt(d.x), fmt(d.y), fmt(d.z),
                    p.change().yRot(), p.change().xRot(), p.onGround(), p.relatives()
            );
            return;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket p) {
            Vec3 m = p.movement();
            LegacyLinkMod.LOGGER.info(
                    "{} type=set_entity_motion entityId={} movement=({},{},{})",
                    base, p.id(), fmt(m.x), fmt(m.y), fmt(m.z)
            );
            return;
        }
        if (packet instanceof ClientboundMoveEntityPacket p) {
            int eid = moveEntityId(p);
            LegacyLinkMod.LOGGER.info(
                    "{} type={} entityId={} dPos=({},{},{}) yRot={} xRot={} hasPos={} hasRot={} onGround={}",
                    base, p.type().id(), eid, p.getXa(), p.getYa(), p.getZa(),
                    p.getYRot(), p.getXRot(), p.hasPosition(), p.hasRotation(), p.isOnGround()
            );
            return;
        }
        if (packet instanceof ClientboundRotateHeadPacket p) {
            LegacyLinkMod.LOGGER.info(
                    "{} type=rotate_head entityId={} yHeadRot={}",
                    base, rotateHeadEntityId(p), p.getYHeadRot()
            );
        }
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.6f", v);
    }

    private static int moveEntityId(ClientboundMoveEntityPacket p) {
        try {
            Field f = moveEntityIdField;
            if (f == null) {
                f = ClientboundMoveEntityPacket.class.getDeclaredField("entityId");
                f.setAccessible(true);
                moveEntityIdField = f;
            }
            return f.getInt(p);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    private static int rotateHeadEntityId(ClientboundRotateHeadPacket p) {
        try {
            Field f = rotateHeadEntityIdField;
            if (f == null) {
                f = ClientboundRotateHeadPacket.class.getDeclaredField("entityId");
                f.setAccessible(true);
                rotateHeadEntityIdField = f;
            }
            return f.getInt(p);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    private PositionPacketTrace() {}
}
