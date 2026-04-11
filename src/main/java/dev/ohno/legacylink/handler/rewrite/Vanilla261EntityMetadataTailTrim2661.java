package dev.ohno.legacylink.handler.rewrite;

import dev.ohno.legacylink.runtime.LegacyRuntimeContext;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Drops metadata entries whose index is above the vanilla 26.1 client's last slot for that {@link EntityType}.
 * <p>
 * When 26.2 adds new {@link SynchedEntityData} fields in the middle or tail of a mob's hierarchy, the server may send
 * indices the 26.1 client never allocated (e.g. {@code ArrayIndexOutOfBoundsException: Index 20 out of bounds for length 19}).
 * This is intentionally <b>per entity type</b> with explicit documented maxima (not a global index cap).
 * <p>
 * If a future 26.2 change <b>remaps</b> an existing field (like {@link CubeMobEntityData2661} for slime), prefer a
 * dedicated rewriter; this class only removes <b>unknown tail</b> indices.
 */
public final class Vanilla261EntityMetadataTailTrim2661 {

    private Vanilla261EntityMetadataTailTrim2661() {}

    /**
     * @return inclusive max metadata index on vanilla 26.1 for this type, or {@code -1} if unknown / do not trim
     */
    public static int legacyMaxMetadataIndexInclusive(@Nullable EntityType<?> type) {
        if (type == null) {
            return -1;
        }
        if (type == EntityType.SQUID) {
            return 17;
        }
        if (type == EntityType.GLOW_SQUID) {
            return 18;
        }
        if (type == EntityType.CREEPER) {
            return 18;
        }
        if (type == EntityType.ENDERMAN) {
            return 18;
        }
        if (type == EntityType.ZOMBIE || type == EntityType.DROWNED || type == EntityType.HUSK || type == EntityType.ZOMBIFIED_PIGLIN) {
            return 18;
        }
        if (type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.WITHER_SKELETON || type == EntityType.BOGGED) {
            return 16;
        }
        if (type == EntityType.BAT) {
            return 16;
        }
        if (type == EntityType.SALMON || type == EntityType.COD || type == EntityType.PUFFERFISH || type == EntityType.TROPICAL_FISH) {
            return 17;
        }
        if (type == EntityType.HOGLIN) {
            return 18;
        }
        if (type == EntityType.PLAYER) {
            return -1;
        }
        return -1;
    }

    /**
     * @return rewritten list, or {@code null} if unchanged
     */
    public static @org.jspecify.annotations.Nullable List<SynchedEntityData.DataValue<?>> trimIfNeeded(
            int entityId,
            List<SynchedEntityData.DataValue<?>> packedItems,
            @Nullable EntityType<?> clientVisibleTypeHint
    ) {
        EntityType<?> type = clientVisibleTypeHint;
        if (type == null && LegacyRuntimeContext.server() != null) {
            Entity entity = LegacyRuntimeContext.findEntity(entityId);
            if (entity != null) {
                type = entity.getType();
            }
        }
        if (type == null) {
            return null;
        }
        int cap = legacyMaxMetadataIndexInclusive(type);
        if (cap < 0) {
            return null;
        }
        boolean overflow = false;
        for (SynchedEntityData.DataValue<?> v : packedItems) {
            if (v.id() > cap) {
                overflow = true;
                break;
            }
        }
        if (!overflow) {
            return null;
        }
        List<SynchedEntityData.DataValue<?>> out = new ArrayList<>(packedItems.size());
        for (SynchedEntityData.DataValue<?> v : packedItems) {
            if (v.id() <= cap) {
                out.add(v);
            }
        }
        return out;
    }
}
