package dev.ohno.legacylink.debug;

import dev.ohno.legacylink.LegacyLinkMod;
import dev.ohno.legacylink.connection.LegacyTracker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.BundleDelimiterPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-detail PRE/POST logging for legacy outbound translation: exact packet payloads the handler sees
 * before {@link dev.ohno.legacylink.handler.LegacyPacketHandler#translateOutbound} and the rewritten
 * packet after (same Netty {@code write} frame). Correlates nested work (registry strips, entity metadata)
 * with {@code seq} via a short-lived {@link ThreadLocal}.
 * <p>
 * Enable: {@code -Dlegacylink.tracePacketMap=true} (or {@code LEGACYLINK_TRACE_PACKET_MAP=1} with
 * {@code minecraft-cabal/scripts/start.sh}). Grep {@code [LegacyLink][PacketMap]} in server {@code logs/latest.log}.
 * <p>
 * <b>Noisy</b> — intended for short e2e / debugging sessions.
 */
public final class LegacyPacketMapTrace {

    private static final boolean ENABLED = packetMapTraceEnabled();

    private static final AtomicLong SEQ = new AtomicLong();
    private static final ThreadLocal<Long> ACTIVE_SEQ = new ThreadLocal<>();

    private static boolean packetMapTraceEnabled() {
        String v = System.getProperty("legacylink.tracePacketMap", "");
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    private LegacyPacketMapTrace() {}

    public static boolean enabled() {
        return ENABLED;
    }

    public static long nextSeq() {
        return SEQ.incrementAndGet();
    }

    public static void enter(long seq) {
        ACTIVE_SEQ.set(seq);
    }

    public static void leave() {
        ACTIVE_SEQ.remove();
    }

    private static long seqForAuxLog() {
        Long s = ACTIVE_SEQ.get();
        return s == null ? -1L : s;
    }

    public static boolean isInteresting(Packet<?> p) {
        if (p instanceof ClientboundRegistryDataPacket
                || p instanceof ClientboundAddEntityPacket
                || p instanceof ClientboundSetEntityDataPacket
                || p instanceof ClientboundUpdateAttributesPacket) {
            return true;
        }
        if (p instanceof ClientboundBundlePacket bundle) {
            for (Packet<? super ClientGamePacketListener> sub : bundle.subPackets()) {
                if (sub instanceof BundleDelimiterPacket) {
                    continue;
                }
                if (isInteresting(sub)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void logPhase(Connection connection, long seq, String phase, Packet<?> packet) {
        if (!enabled() || connection == null || !LegacyTracker.isLegacy(connection)) {
            return;
        }
        LegacyLinkMod.LOGGER.info(
                "[LegacyLink][PacketMap] seq={} phase={} remote={} detail={}",
                seq,
                phase,
                connection.getRemoteAddress(),
                formatPacketTree(packet, "")
        );
    }

    public static void logRegistryFiltered(
            Connection connection,
            String registryId,
            int beforeCount,
            int afterCount,
            List<String> removedEntryIds
    ) {
        if (!enabled() || connection == null || !LegacyTracker.isLegacy(connection)) {
            return;
        }
        LegacyLinkMod.LOGGER.info(
                "[LegacyLink][PacketMap] seq={} kind=registry_filter registry={} beforeCount={} afterCount={} removed={}",
                seqForAuxLog(),
                registryId,
                beforeCount,
                afterCount,
                removedEntryIds
        );
    }

    public static void logEntityDataContext(
            Connection connection,
            int entityId,
            @Nullable EntityType<?> resolvedForRewriters,
            @Nullable EntityType<?> serverWorldEntityType,
            @Nullable EntityType<?> spawnPrefetchType,
            boolean sulfurCubeRemappedToSlimeTailStrip
    ) {
        if (!enabled() || connection == null || !LegacyTracker.isLegacy(connection)) {
            return;
        }
        LegacyLinkMod.LOGGER.info(
                "[LegacyLink][PacketMap] seq={} kind=entity_data_ctx eid={} resolvedForRewriters={} serverWorldType={} spawnPrefetch={} sulfurRemappedSlimeStrip={}",
                seqForAuxLog(),
                entityId,
                typeId(resolvedForRewriters),
                typeId(serverWorldEntityType),
                typeId(spawnPrefetchType),
                sulfurCubeRemappedToSlimeTailStrip
        );
    }

    private static String typeId(@Nullable EntityType<?> t) {
        if (t == null) {
            return "null";
        }
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(t);
        return id != null ? id.toString() : t.toString();
    }

    private static String formatPacketTree(Packet<?> packet, String path) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            StringBuilder sb = new StringBuilder();
            sb.append("Bundle path=").append(path.isEmpty() ? "/" : path);
            int subCount = 0;
            int i = 0;
            StringBuilder nestedDetail = new StringBuilder();
            for (Packet<? super ClientGamePacketListener> sub : bundle.subPackets()) {
                subCount++;
                if (sub instanceof BundleDelimiterPacket) {
                    i++;
                    continue;
                }
                if (sub instanceof ClientboundBundlePacket nested) {
                    nestedDetail.append(" {").append(formatPacketTree(nested, path + "/" + i)).append("}");
                } else if (isInteresting(sub)) {
                    nestedDetail.append(" {").append(formatPacketLeaf(sub)).append("}");
                }
                i++;
            }
            sb.append(" subCount=").append(subCount).append(nestedDetail);
            return sb.toString();
        }
        return formatPacketLeaf(packet);
    }

    private static String formatPacketLeaf(Packet<?> packet) {
        if (packet instanceof ClientboundRegistryDataPacket p) {
            return formatRegistryData(p);
        }
        if (packet instanceof ClientboundAddEntityPacket p) {
            return formatAddEntity(p);
        }
        if (packet instanceof ClientboundSetEntityDataPacket p) {
            return formatSetEntityData(p);
        }
        if (packet instanceof ClientboundUpdateAttributesPacket p) {
            return formatUpdateAttributes(p);
        }
        return packet.getClass().getSimpleName();
    }

    private static String formatRegistryData(ClientboundRegistryDataPacket p) {
        var key = p.registry();
        var entries = p.entries();
        StringBuilder sb = new StringBuilder();
        sb.append("RegistryData registry=").append(key.identifier()).append(" entryCount=").append(entries.size());
        if (Registries.ENTITY_TYPE.equals(key)) {
            sb.append(" entityTypeWireOrder=[");
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(i).append(':').append(entries.get(i).id());
            }
            sb.append(']');
        }
        return sb.toString();
    }

    private static String formatAddEntity(ClientboundAddEntityPacket p) {
        var typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(p.getType());
        String typeStr = typeKey != null ? typeKey.toString() : String.valueOf(p.getType());
        return String.format(
                Locale.ROOT,
                "AddEntity eid=%d type=%s uuid=%s pos=(%.4f,%.4f,%.4f) data=%d yRot=%.4f xRot=%.4f",
                p.getId(),
                typeStr,
                p.getUUID(),
                p.getX(),
                p.getY(),
                p.getZ(),
                p.getData(),
                p.getYRot(),
                p.getXRot()
        );
    }

    private static String formatSetEntityData(ClientboundSetEntityDataPacket p) {
        StringBuilder sb = new StringBuilder();
        sb.append("SetEntityData eid=").append(p.id()).append(" itemCount=").append(p.packedItems().size()).append('[');
        List<SynchedEntityData.DataValue<?>> items = p.packedItems();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            var v = items.get(i);
            sb.append("id=").append(v.id()).append(':').append(truncate(String.valueOf(v.value()), 500));
        }
        sb.append(']');
        return sb.toString();
    }

    private static String formatUpdateAttributes(ClientboundUpdateAttributesPacket p) {
        StringBuilder sb = new StringBuilder();
        sb.append("UpdateAttributes eid=").append(p.getEntityId()).append(" [");
        for (var snap : p.getValues()) {
            Identifier aid = BuiltInRegistries.ATTRIBUTE.getKey(snap.attribute().value());
            sb.append(aid != null ? aid.toString() : "?").append(',');
        }
        if (!p.getValues().isEmpty()) {
            sb.setLength(sb.length() - 1);
        }
        sb.append(']');
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...(trunc)";
    }
}
