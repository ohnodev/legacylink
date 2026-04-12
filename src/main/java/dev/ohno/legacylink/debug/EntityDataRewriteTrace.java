package dev.ohno.legacylink.debug;

import dev.ohno.legacylink.LegacyLinkMod;
import net.minecraft.network.syncher.SynchedEntityData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Logs when {@link dev.ohno.legacylink.handler.LegacyPacketHandler#remapEntityData} changes metadata indices so
 * {@code server/logs/latest.log} can prove rewrites (compare {@code beforeIds} vs {@code afterIds}; villager fix drops 21
 * and moves 20→18).
 * <p>
 * Enable: {@code -Dlegacylink.traceEntityDataRewrite=true} — also on when outbound capture is on
 * ({@code -Dlegacylink.captureOutbound=true} or {@code LEGACYLINK_CAPTURE_OUTBOUND}).
 * Grep: {@code [LegacyLink][EntityDataRewrite]}.
 */
public final class EntityDataRewriteTrace {

    public static boolean enabled() {
        String v = System.getProperty("legacylink.traceEntityDataRewrite", "");
        if ("true".equalsIgnoreCase(v) || "1".equals(v)) {
            return true;
        }
        return LegacyOutboundPacketCapture.enabled();
    }

    public static String formatSortedIds(List<SynchedEntityData.DataValue<?>> list) {
        if (list.isEmpty()) {
            return "";
        }
        List<Integer> ids = new ArrayList<>(list.size());
        for (SynchedEntityData.DataValue<?> v : list) {
            ids.add(v.id());
        }
        Collections.sort(ids);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    public static void logIfChanged(int entityId, String beforeIds, String afterIds) {
        if (!enabled() || beforeIds.equals(afterIds)) {
            return;
        }
        LegacyLinkMod.LOGGER.info(
                "[LegacyLink][EntityDataRewrite] entityId={} beforeIds=[{}] afterIds=[{}]",
                entityId,
                beforeIds,
                afterIds
        );
    }

    private EntityDataRewriteTrace() {}
}
