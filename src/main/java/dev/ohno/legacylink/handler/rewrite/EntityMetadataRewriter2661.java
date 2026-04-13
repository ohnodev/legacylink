package dev.ohno.legacylink.handler.rewrite;

import dev.ohno.legacylink.LegacyLinkMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
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

    private EntityMetadataRewriter2661() {}

    /**
     * @return rewritten metadata list, or {@code null} when no changes were applied
     */
    public static @Nullable List<SynchedEntityData.DataValue<?>> rewriteForLegacy(
            int entityId,
            @Nullable EntityType<?> clientType,
            List<SynchedEntityData.DataValue<?>> items
    ) {
        List<SynchedEntityData.DataValue<?>> out = null;
        int removed = 0;
        for (int i = 0; i < items.size(); i++) {
            SynchedEntityData.DataValue<?> input = items.get(i);
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
                    out = copyPrefix(items, i);
                }
                removed++;
                continue;
            }

            if (out != null) {
                out.add(rewritten);
            } else if (rewritten != input) {
                out = copyPrefix(items, i);
                out.add(rewritten);
            }
        }

        if (out == null) {
            return null;
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
}
