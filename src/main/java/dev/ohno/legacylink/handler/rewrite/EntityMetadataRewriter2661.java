package dev.ohno.legacylink.handler.rewrite;

import dev.ohno.legacylink.LegacyLinkMod;
import dev.ohno.legacylink.runtime.LegacyRuntimeContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Via-style metadata guard stage:
 * - apply serializer/value rewrites per metadata entry
 * - cancel unsupported entries for legacy clients
 * - if a rewrite handler throws, log and drop only that entry
 */
public final class EntityMetadataRewriter2661 {

    private static final boolean LOG_ENTITY_DATA_ERRORS = Boolean.getBoolean("legacylink.logEntityDataErrors");
    private static final EntityType<?> SLIME_TYPE = resolveEntityType("minecraft:slime");
    private static final EntityType<?> MAGMA_CUBE_TYPE = resolveEntityType("minecraft:magma_cube");
    private static final int VILLAGER_DATA_26_2 = 20;
    private static final int VILLAGER_FINALIZED_26_2 = 21;
    private static final int VILLAGER_DATA_26_1_VILLAGER = 18;
    private static final int CUBE_PREFIX_END_EXCLUSIVE = 16;
    private static final int CUBE_SIZE_INDEX_26_2 = 18;
    private static final int CUBE_SIZE_INDEX_26_1 = 16;

    private EntityMetadataRewriter2661() {}

    /**
     * @return rewritten metadata list, or {@code null} when no changes were applied
     */
    public static @Nullable List<SynchedEntityData.DataValue<?>> rewriteForLegacy(
            int entityId,
            @Nullable EntityType<?> clientType,
            @Nullable EntityType<?> spawnPrefetchType,
            @Nullable EntityType<?> worldEntityType,
            List<SynchedEntityData.DataValue<?>> items
    ) {
        List<SynchedEntityData.DataValue<?>> pre = items;

        // Consolidated entity rewrite pipeline for 26.2 -> 26.1.2 metadata compatibility.
        var cubeRewritten = rewriteCubeMobDataIfNeeded(entityId, pre, clientType);
        if (cubeRewritten != null) {
            pre = cubeRewritten;
        }
        var villagerRewritten = rewriteVillagerDataIfNeeded(entityId, pre, spawnPrefetchType, worldEntityType);
        if (villagerRewritten != null) {
            pre = villagerRewritten;
        }
        var tailTrimmed = trimLegacyTailIfNeeded(entityId, pre, clientType);
        if (tailTrimmed != null) {
            pre = tailTrimmed;
        }

        List<SynchedEntityData.DataValue<?>> out = null;
        int removed = 0;
        for (int i = 0; i < pre.size(); i++) {
            SynchedEntityData.DataValue<?> input = pre.get(i);
            SynchedEntityData.DataValue<?> rewritten;
            try {
                rewritten = rewriteEntry(input);
            } catch (Exception e) {
                if (LOG_ENTITY_DATA_ERRORS) {
                    logRewriteError(entityId, clientType, input, e);
                }
                rewritten = null;
            }

            if (rewritten == null) {
                if (out == null) {
                    out = copyPrefix(pre, i);
                }
                removed++;
                continue;
            }

            if (out != null) {
                out.add(rewritten);
            } else if (rewritten != input) {
                out = copyPrefix(pre, i);
                out.add(rewritten);
            }
        }

        if (out == null) {
            return pre == items ? null : pre;
        }
        if (removed > 0) {
            LegacyLinkMod.LOGGER.warn(
                    "[LegacyLink][EntityDataGuard] removed {} incompatible metadata entries for legacy client (eid={}, type={})",
                    removed,
                    entityId,
                    clientType == null ? "unknown" : Objects.toString(BuiltInRegistries.ENTITY_TYPE.getKey(clientType))
            );
        }
        return out;
    }

    /**
     * @return rewritten entry, original entry, or {@code null} when entry should be cancelled for legacy
     */
    private static SynchedEntityData.DataValue<?> rewriteEntry(SynchedEntityData.DataValue<?> value) {
        EntityDataSerializer<?> serializer = value.serializer();

        // Particle metadata payloads are the highest-risk desync source across nearby snapshots.
        if (serializer == EntityDataSerializers.PARTICLE || serializer == EntityDataSerializers.PARTICLES) {
            return null;
        }

        if (serializer == EntityDataSerializers.ITEM_STACK && value.value() instanceof ItemStack stack) {
            ItemStack remapped = ItemRewriter.remapStack(stack);
            if (remapped != stack) {
                return new SynchedEntityData.DataValue<>(
                        value.id(),
                        EntityDataSerializers.ITEM_STACK,
                        remapped
                );
            }
        }

        return value;
    }

    private static List<SynchedEntityData.DataValue<?>> copyPrefix(List<SynchedEntityData.DataValue<?>> items, int endExclusive) {
        List<SynchedEntityData.DataValue<?>> out = new ArrayList<>(items.size());
        for (int j = 0; j < endExclusive; j++) {
            out.add(items.get(j));
        }
        return out;
    }

    private static void logRewriteError(
            int entityId,
            @Nullable EntityType<?> clientType,
            SynchedEntityData.DataValue<?> value,
            Exception error
    ) {
        int serializerId = EntityDataSerializers.getSerializedId(value.serializer());
        LegacyLinkMod.LOGGER.warn(
                "[LegacyLink][EntityDataGuard] metadata rewrite failed; dropping entry eid={} type={} index={} serializerId={} valueType={}",
                entityId,
                clientType == null ? "unknown" : Objects.toString(BuiltInRegistries.ENTITY_TYPE.getKey(clientType)),
                value.id(),
                serializerId,
                value.value() == null ? "null" : value.value().getClass().getName(),
                error
        );
    }

    private static EntityType<?> resolveEntityType(String id) {
        return BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse(id))
                .map(holder -> holder.value())
                .orElseThrow(() -> new IllegalStateException("[LegacyLink] Missing required entity type: " + id));
    }

    private static boolean isEntityType(@Nullable EntityType<?> type, String id) {
        if (type == null) {
            return false;
        }
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key != null && id.contentEquals(key.toString());
    }

    private static @Nullable List<SynchedEntityData.DataValue<?>> rewriteCubeMobDataIfNeeded(
            int entityId,
            List<SynchedEntityData.DataValue<?>> packedItems,
            @Nullable EntityType<?> clientVisibleTypeHint
    ) {
        EntityType<?> type = clientVisibleTypeHint;
        Entity entity = LegacyRuntimeContext.findEntity(entityId);
        if (type == null && entity != null) {
            type = entity.getType();
        }
        if (type != SLIME_TYPE && type != MAGMA_CUBE_TYPE) {
            return null;
        }
        if (entity != null && !(entity instanceof AbstractCubeMob)) {
            return null;
        }

        boolean changed = false;
        List<SynchedEntityData.DataValue<?>> out = new ArrayList<>(packedItems.size());
        for (SynchedEntityData.DataValue<?> v : packedItems) {
            int id = v.id();
            if (id < CUBE_PREFIX_END_EXCLUSIVE) {
                out.add(v);
            } else if (id == 16 || id == 17) {
                changed = true;
            } else if (id == CUBE_SIZE_INDEX_26_2) {
                if (v.serializer() != EntityDataSerializers.INT) {
                    changed = true;
                    continue;
                }
                out.add(new SynchedEntityData.DataValue<>(
                        CUBE_SIZE_INDEX_26_1,
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

    private static @Nullable List<SynchedEntityData.DataValue<?>> rewriteVillagerDataIfNeeded(
            int entityId,
            List<SynchedEntityData.DataValue<?>> packedItems,
            @Nullable EntityType<?> spawnPrefetchType,
            @Nullable EntityType<?> worldEntityType
    ) {
        int booleanSerId = EntityDataSerializers.getSerializedId(EntityDataSerializers.BOOLEAN);
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
        if (!isVillagerLike(type)) {
            return stripVillagerOnlyTailSlots(packedItems, booleanSerId);
        }
        boolean zombieVillager = isEntityType(type, "minecraft:zombie_villager");

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

    private static @Nullable List<SynchedEntityData.DataValue<?>> trimLegacyTailIfNeeded(
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

    private static int legacyMaxMetadataIndexInclusive(@Nullable EntityType<?> type) {
        if (type == null) {
            return -1;
        }
        if (isEntityType(type, "minecraft:squid")) return 17;
        if (isEntityType(type, "minecraft:glow_squid")) return 18;
        if (isEntityType(type, "minecraft:creeper")) return 18;
        if (isEntityType(type, "minecraft:enderman")) return 18;
        if (isEntityType(type, "minecraft:zombie")
                || isEntityType(type, "minecraft:drowned")
                || isEntityType(type, "minecraft:husk")
                || isEntityType(type, "minecraft:zombified_piglin")) return 18;
        if (isEntityType(type, "minecraft:skeleton")
                || isEntityType(type, "minecraft:stray")
                || isEntityType(type, "minecraft:wither_skeleton")
                || isEntityType(type, "minecraft:bogged")) return 16;
        if (isEntityType(type, "minecraft:bat")) return 16;
        if (isEntityType(type, "minecraft:salmon")
                || isEntityType(type, "minecraft:cod")
                || isEntityType(type, "minecraft:pufferfish")
                || isEntityType(type, "minecraft:tropical_fish")) return 17;
        if (isEntityType(type, "minecraft:hoglin")) return 18;
        return -1;
    }

    private static boolean isVillagerLike(@Nullable EntityType<?> t) {
        return isEntityType(t, "minecraft:villager") || isEntityType(t, "minecraft:zombie_villager");
    }

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
