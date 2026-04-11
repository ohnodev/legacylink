package dev.ohno.legacylink.mapping;

import dev.ohno.legacylink.LegacyLinkConstants;
import dev.ohno.legacylink.LegacyLinkMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * Network entity-type ids for legacy clients after {@link dev.ohno.legacylink.handler.LegacyPacketHandler#filterRegistryData}
 * drops {@link LegacyLinkConstants#SULFUR_CUBE_ENTITY_ID} from {@code minecraft:entity_type} sync. Vanilla encoding uses
 * {@link net.minecraft.network.RegistryFriendlyByteBuf#registryAccess()} which can still reflect the full 26.2 built-in
 * registry, so {@link dev.ohno.legacylink.mixin.ClientboundAddEntityPacketMixin} writes this index instead.
 */
public final class LegacyEntityTypeWireRemapper {

    private static volatile List<Identifier> legacySyncOrder = List.of();

    private LegacyEntityTypeWireRemapper() {}

    /**
     * Rebuild from {@link BuiltInRegistries#ENTITY_TYPE} iteration order, omitting the sulfur cube entry — must stay in
     * lockstep with {@code filterRegistryData} for {@link net.minecraft.core.registries.Registries#ENTITY_TYPE}.
     */
    public static void rebuild() {
        List<Identifier> next = new ArrayList<>();
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(t);
            if (key == null) {
                continue;
            }
            if (LegacyLinkConstants.SULFUR_CUBE_ENTITY_ID.contentEquals(key.toString())) {
                continue;
            }
            next.add(key);
        }
        legacySyncOrder = List.copyOf(next);
        LegacyLinkMod.LOGGER.debug("[LegacyLink] Legacy entity_type sync order size={} (sulfur_cube omitted)", legacySyncOrder.size());
    }

    public static int legacyNetworkId(EntityType<?> type) {
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (key == null) {
            throw new IllegalStateException(
                    "[LegacyLink] legacyNetworkId: EntityType has no registry key (" + type
                            + "). Filtered or synthetic types must be remapped to a legacy substitute before encoding.");
        }
        List<Identifier> order = legacySyncOrder;
        int idx = order.indexOf(key);
        if (idx < 0) {
            throw new IllegalStateException(
                    "[LegacyLink] legacyNetworkId: EntityType " + key + " (" + type
                            + ") not in legacy entity_type sync order — map to a legacy substitute before calling legacyNetworkId.");
        }
        return idx;
    }

    public static List<Identifier> legacySyncOrderUnmodifiable() {
        return legacySyncOrder;
    }
}
