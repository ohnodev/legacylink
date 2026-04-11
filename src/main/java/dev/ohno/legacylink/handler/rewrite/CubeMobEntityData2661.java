package dev.ohno.legacylink.handler.rewrite;

import dev.ohno.legacylink.runtime.LegacyRuntimeContext;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 26.2 moves slime and magma cube under {@link AbstractCubeMob} → {@link net.minecraft.world.entity.AgeableMob},
 * so synced data layout <b>no longer matches</b> 26.1 (where they extended {@link net.minecraft.world.entity.Mob} only).
 * <p>
 * On 26.2: indices 16–17 are ageable flags; index 18 is cube size. On 26.1 slime: size lives at index 16 and there are
 * no ageable slots. Sending 26.2 indices blindly causes {@code Network Protocol Error} / OOB on the legacy client.
 * <p>
 * This is a <b>semantic remap</b> for one version delta — not a generic “strip index &gt; N”.
 */
public final class CubeMobEntityData2661 {

    /** Entity + LivingEntity + Mob slots on both versions (indices 0..15). */
    private static final int PREFIX_END_EXCLUSIVE = 16;
    /** Cube size on 26.2 {@link AbstractCubeMob} slime/magma line. */
    private static final int SIZE_INDEX_26_2 = 18;
    /** Cube size on 26.1 slime/magma (directly under Mob). */
    private static final int SIZE_INDEX_26_1 = 16;

    private CubeMobEntityData2661() {}

    /**
     * @return rewritten list, or {@code null} if this packet should pass through unchanged
     */
    public static @org.jspecify.annotations.Nullable List<SynchedEntityData.DataValue<?>> rewriteIfNeeded(
            int entityId,
            List<SynchedEntityData.DataValue<?>> packedItems,
            @Nullable EntityType<?> clientVisibleTypeHint
    ) {
        EntityType<?> type = clientVisibleTypeHint;
        Entity entity = LegacyRuntimeContext.findEntity(entityId);
        if (type == null && entity != null) {
            type = entity.getType();
        }
        if (type != EntityType.SLIME && type != EntityType.MAGMA_CUBE) {
            return null;
        }
        if (entity != null) {
            if (!(entity instanceof AbstractCubeMob)) {
                return null;
            }
        }

        boolean changed = false;
        List<SynchedEntityData.DataValue<?>> out = new ArrayList<>(packedItems.size());
        for (SynchedEntityData.DataValue<?> v : packedItems) {
            int id = v.id();
            if (id < PREFIX_END_EXCLUSIVE) {
                out.add(v);
            } else if (id == 16 || id == 17) {
                changed = true;
            } else if (id == SIZE_INDEX_26_2) {
                if (v.serializer() != EntityDataSerializers.INT) {
                    changed = true;
                    continue;
                }
                out.add(new SynchedEntityData.DataValue<>(
                        SIZE_INDEX_26_1,
                        EntityDataSerializers.INT,
                        (Integer) v.value()
                ));
                changed = true;
            } else {
                changed = true;
            }
        }
        return changed ? out : null;
    }
}
