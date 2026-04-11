package dev.ohno.legacylink.debug;

import dev.ohno.legacylink.LegacyLinkMod;
import dev.ohno.legacylink.connection.LegacyTracker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Logs join/spawn-related clientbound packets only (no per-tick movement spam).
 * <p>
 * Server JVM: {@code -Dlegacylink.traceSpawn=true}
 * <p>
 * Grep {@code [LegacyLink][SpawnTrace]} in {@code logs/latest.log}. Stages match
 * {@link PositionPacketTrace}: {@code connection_send} (26.2) vs {@code post_legacy_rewrite} (26.1).
 */
public final class SpawnPacketTrace {

    private static final AtomicLong SEQ = new AtomicLong();

    public static boolean enabled() {
        String v = System.getProperty("legacylink.traceSpawn", "");
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
        String base = "[LegacyLink][SpawnTrace] seq=" + seq + " stage=" + stage + " legacy=" + legacy
                + " remote=" + remote + " path=" + path;

        if (packet instanceof ClientboundLoginPacket p) {
            CommonPlayerSpawnInfo s = p.commonPlayerSpawnInfo();
            LegacyLinkMod.LOGGER.info(
                    "{} type=login playerId={} chunkRadius={} simDistance={} hardcore={} reducedDebug={} "
                            + "spawnInfo={}",
                    base, p.playerId(), p.chunkRadius(), p.simulationDistance(), p.hardcore(),
                    p.reducedDebugInfo(), formatSpawnInfo(s)
            );
            return;
        }
        if (packet instanceof ClientboundRespawnPacket p) {
            LegacyLinkMod.LOGGER.info(
                    "{} type=respawn dataToKeep={} spawnInfo={}",
                    base, p.dataToKeep(), formatSpawnInfo(p.commonPlayerSpawnInfo())
            );
            return;
        }
        if (packet instanceof ClientboundSetDefaultSpawnPositionPacket p) {
            var r = p.respawnData();
            LegacyLinkMod.LOGGER.info(
                    "{} type=set_default_spawn dimension={} blockPos=({},{},{}) yaw={} pitch={}",
                    base, r.dimension(), r.pos().getX(), r.pos().getY(), r.pos().getZ(), r.yaw(), r.pitch()
            );
            return;
        }
        if (packet instanceof ClientboundAddEntityPacket p) {
            Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(p.getType());
            String typeStr = typeId != null ? typeId.toString() : String.valueOf(p.getType());
            Vec3 m = p.getMovement();
            LegacyLinkMod.LOGGER.info(
                    "{} type=add_entity id={} uuid={} entityType={} pos=({},{},{}) movement=({},{},{}) "
                            + "yRot={} xRot={} yHeadRot={} data={}",
                    base, p.getId(), p.getUUID(), typeStr,
                    fmt(p.getX()), fmt(p.getY()), fmt(p.getZ()),
                    fmt(m.x), fmt(m.y), fmt(m.z),
                    fmt(p.getYRot()), fmt(p.getXRot()), fmt(p.getYHeadRot()), p.getData()
            );
            return;
        }
        if (packet instanceof ClientboundPlayerPositionPacket p) {
            Vec3 pos = p.change().position();
            Vec3 d = p.change().deltaMovement();
            LegacyLinkMod.LOGGER.info(
                    "{} type=player_position teleportId={} pos=({},{},{}) delta=({},{},{}) yRot={} xRot={} relatives={}",
                    base, p.id(), fmt(pos.x), fmt(pos.y), fmt(pos.z), fmt(d.x), fmt(d.y), fmt(d.z),
                    p.change().yRot(), p.change().xRot(), p.relatives()
            );
        }
    }

    private static String formatSpawnInfo(CommonPlayerSpawnInfo s) {
        return "dim=" + s.dimension()
                + " gameType=" + s.gameType()
                + " prevGameType=" + s.previousGameType()
                + " seaLevel=" + s.seaLevel()
                + " debug=" + s.isDebug()
                + " flat=" + s.isFlat()
                + " portalCooldown=" + s.portalCooldown()
                + " lastDeath=" + s.lastDeathLocation();
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.6f", v);
    }

    private SpawnPacketTrace() {}
}
