package dev.ohno.legacylink.handler.rewrite;

import dev.ohno.legacylink.runtime.LegacyRuntimeContext;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.VillagerData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 26.2 inserts two synced metadata slots in the class hierarchy before villager-shaped fields, so {@link VillagerData} is sent
 * at index <b>20</b> and the finalized flag at <b>21</b> (same numeric slots for {@link EntityType#VILLAGER} and
 * {@link EntityType#ZOMBIE_VILLAGER} on 26.2). On 26.1, {@link EntityType#VILLAGER} expects profession data at <b>18</b>, but
 * {@link EntityType#ZOMBIE_VILLAGER} keeps booleans in <b>18</b> (converting) and <b>19</b> on 26.1, so {@link VillagerData}
 * stays at <b>20</b> — only the new 26.2 finalized flag at <b>21</b> must be stripped.
 * <p>
 * {@link EntityType#VILLAGER}: remap 20→18 and strip 21. {@link EntityType#ZOMBIE_VILLAGER}: strip 21 only.
 */
public final class VillagerEntityData2661 {

    private static final int VILLAGER_DATA_26_2 = 20;
    private static final int VILLAGER_FINALIZED_26_2 = 21;
    private static final int VILLAGER_DATA_26_1_VILLAGER = 18;

    private static boolean isVillagerLike(@Nullable EntityType<?> t) {
        return t == EntityType.VILLAGER || t == EntityType.ZOMBIE_VILLAGER;
    }

    private VillagerEntityData2661() {}

    public static @Nullable List<SynchedEntityData.DataValue<?>> rewriteIfNeeded(
            int entityId,
            List<SynchedEntityData.DataValue<?>> packedItems,
            @Nullable EntityType<?> spawnPrefetchType,
            @Nullable EntityType<?> worldEntityType
    ) {
        int booleanSerId = EntityDataSerializers.getSerializedId(EntityDataSerializers.BOOLEAN);
        /*
         * Villager / zombie villager: remap 26.2 slots 20/21 to the 26.1 layout (profession at 18, drop finalized).
         *
         * If spawn prefetch or world type is not villager-like, never emit {@link VillagerData} at 18 — strip 20/21 only.
         * (Prefetch/world disagreeing with wire was a symptom of entity_type id skew before {@code sulfur_cube} strip + wire remap.)
         */
        if (spawnPrefetchType != null && !isVillagerLike(spawnPrefetchType)) {
            return stripVillagerOnlyTailSlots(packedItems, booleanSerId);
        }
        if (worldEntityType != null && !isVillagerLike(worldEntityType)) {
            return stripVillagerOnlyTailSlots(packedItems, booleanSerId);
        }
        EntityType<?> type = worldEntityType != null ? worldEntityType : spawnPrefetchType;
        if (type == null && LegacyRuntimeContext.server() != null) {
            Entity entity = LegacyRuntimeContext.findEntity(entityId);
            if (entity != null) {
                type = entity.getType();
            }
        }
        boolean villagerLikeType = isVillagerLike(type);
        if (!villagerLikeType) {
            return stripVillagerOnlyTailSlots(packedItems, booleanSerId);
        }

        boolean zombieVillager = type == EntityType.ZOMBIE_VILLAGER;

        boolean changed = false;
        List<SynchedEntityData.DataValue<?>> out = new ArrayList<>(packedItems.size());
        for (SynchedEntityData.DataValue<?> v : packedItems) {
            int id = v.id();
            if (id == VILLAGER_FINALIZED_26_2 && isBooleanFinalizedSlot(v, booleanSerId)) {
                changed = true;
                continue;
            }
            if (id == VILLAGER_DATA_26_2 && v.value() instanceof VillagerData data) {
                if (zombieVillager) {
                    /*
                     * 26.1 zombie villager: VillagerData already lives at index 20 (booleans at 18–19). 26.2 only adds slot 21.
                     */
                    out.add(v);
                } else {
                    out.add(new SynchedEntityData.DataValue<>(
                            VILLAGER_DATA_26_1_VILLAGER,
                            EntityDataSerializers.VILLAGER_DATA,
                            data
                    ));
                    changed = true;
                }
                continue;
            }
            out.add(v);
        }
        return changed ? out : null;
    }

    /**
     * Drops 26.2 villager profession / finalized slots without re-indexing (for mobs that never use those indices).
     */
    private static @Nullable List<SynchedEntityData.DataValue<?>> stripVillagerOnlyTailSlots(
            List<SynchedEntityData.DataValue<?>> packedItems,
            int booleanSerializerId
    ) {
        boolean hasVillagerDataSlot = false;
        for (SynchedEntityData.DataValue<?> v : packedItems) {
            if (v.id() == VILLAGER_DATA_26_2 && v.value() instanceof VillagerData) {
                hasVillagerDataSlot = true;
                break;
            }
        }
        boolean changed = false;
        List<SynchedEntityData.DataValue<?>> out = new ArrayList<>(packedItems.size());
        for (SynchedEntityData.DataValue<?> v : packedItems) {
            int id = v.id();
            if (id == VILLAGER_DATA_26_2 && v.value() instanceof VillagerData) {
                changed = true;
                continue;
            }
            if (id == VILLAGER_FINALIZED_26_2 && isBooleanFinalizedSlot(v, booleanSerializerId) && hasVillagerDataSlot) {
                changed = true;
                continue;
            }
            out.add(v);
        }
        return changed ? out : null;
    }

    private static boolean isBooleanFinalizedSlot(SynchedEntityData.DataValue<?> v, int booleanSerializerId) {
        if (v.value() instanceof Boolean) {
            return true;
        }
        if (booleanSerializerId < 0) {
            return false;
        }
        return EntityDataSerializers.getSerializedId(v.serializer()) == booleanSerializerId;
    }
}
