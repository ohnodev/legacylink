package dev.ohno.legacylink.handler.rewrite;

import dev.ohno.legacylink.runtime.LegacyRuntimeContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
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
        if (isEntityType(type, "minecraft:squid")) {
            return 17;
        }
        if (isEntityType(type, "minecraft:glow_squid")) {
            return 18;
        }
        if (isEntityType(type, "minecraft:creeper")) {
            return 18;
        }
        if (isEntityType(type, "minecraft:enderman")) {
            return 18;
        }
        if (isEntityType(type, "minecraft:zombie")
                || isEntityType(type, "minecraft:drowned")
                || isEntityType(type, "minecraft:husk")
                || isEntityType(type, "minecraft:zombified_piglin")) {
            return 18;
        }
        if (isEntityType(type, "minecraft:skeleton")
                || isEntityType(type, "minecraft:stray")
                || isEntityType(type, "minecraft:wither_skeleton")
                || isEntityType(type, "minecraft:bogged")) {
            return 16;
        }
        if (isEntityType(type, "minecraft:bat")) {
            return 16;
        }
        if (isEntityType(type, "minecraft:salmon")
                || isEntityType(type, "minecraft:cod")
                || isEntityType(type, "minecraft:pufferfish")
                || isEntityType(type, "minecraft:tropical_fish")) {
            return 17;
        }
        if (isEntityType(type, "minecraft:hoglin")) {
            return 18;
        }
        if (isEntityType(type, "minecraft:player")) {
            return -1;
        }
        return -1;
    }

    private static boolean isEntityType(@Nullable EntityType<?> type, String id) {
        if (type == null) {
            return false;
        }
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key != null && id.equals(key.toString());
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
