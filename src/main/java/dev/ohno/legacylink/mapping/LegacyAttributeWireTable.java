package dev.ohno.legacylink.mapping;

import dev.ohno.legacylink.LegacyLinkConstants;
import dev.ohno.legacylink.LegacyLinkMod;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Via-style attribute registry alignment: the play encoder uses {@code server.registryAccess()} (full 26.2
 * {@link BuiltInRegistries#ATTRIBUTE} holder ids). The 26.1 client only has entries we send in
 * {@link dev.ohno.legacylink.handler.LegacyPacketHandler#filterRegistryData}, so network ids must be renumbered the same
 * way as {@link com.viaversion.viaversion.rewriter.AttributeRewriter} remaps varints.
 */
public final class LegacyAttributeWireTable {

    private static volatile boolean ready;
    private static volatile Int2IntOpenHashMap serverHolderIdToLegacyHolderId = newIntMap();
    private static volatile Set<Identifier> LEGACY_SYNCED_IDS = Set.of();

    private static Int2IntOpenHashMap newIntMap() {
        Int2IntOpenHashMap m = new Int2IntOpenHashMap();
        m.defaultReturnValue(-1);
        return m;
    }

    public static boolean isReady() {
        return ready;
    }

    public static void rebuild() {
        ready = false;

        IdMap<Holder<Attribute>> idMap = BuiltInRegistries.ATTRIBUTE.asHolderIdMap();
        List<Identifier> serverSlotId = new ArrayList<>(idMap.size());
        for (int i = 0; i < idMap.size(); i++) {
            Holder<Attribute> holder = idMap.byId(i);
            Identifier id = null;
            if (holder != null && holder.isBound()) {
                id = BuiltInRegistries.ATTRIBUTE.getKey(holder.value());
            }
            serverSlotId.add(id);
        }

        List<Identifier> legacyOrder = new ArrayList<>();
        Set<Identifier> seenLegacy = new ObjectOpenHashSet<>();
        for (Identifier id : serverSlotId) {
            if (id == null || stripFromLegacyAttributeSync(id) || seenLegacy.contains(id)) {
                continue;
            }
            seenLegacy.add(id);
            legacyOrder.add(id);
        }

        Object2IntOpenHashMap<Identifier> legacyIndex = new Object2IntOpenHashMap<>();
        legacyIndex.defaultReturnValue(-1);
        for (int j = 0; j < legacyOrder.size(); j++) {
            legacyIndex.put(legacyOrder.get(j), j);
        }

        Int2IntOpenHashMap localServerMap = newIntMap();
        for (int serverId = 0; serverId < serverSlotId.size(); serverId++) {
            Identifier id = serverSlotId.get(serverId);
            if (id == null || stripFromLegacyAttributeSync(id)) {
                continue;
            }
            int legacyId = legacyIndex.getInt(id);
            if (legacyId >= 0) {
                localServerMap.put(serverId, legacyId);
            }
        }

        Set<Identifier> syncedSnapshot = Set.copyOf(legacyOrder);
        serverHolderIdToLegacyHolderId = localServerMap;
        LEGACY_SYNCED_IDS = syncedSnapshot;
        ready = true;

        LegacyLinkMod.LOGGER.info(
                "[LegacyLink] Attribute wire table: {} server slots, {} legacy-synced (holder ids remapped for 26.1)",
                serverSlotId.size(),
                legacyOrder.size()
        );
    }

    private static boolean stripFromLegacyAttributeSync(Identifier id) {
        String s = id.toString();
        if (LegacyLinkConstants.LEGACY_UNSUPPORTED_ATTRIBUTE_IDS.contains(s)) {
            return true;
        }
        return s.contains("sulfur");
    }

    public static boolean isSyncedToLegacy(Identifier attributeId) {
        return attributeId != null && LEGACY_SYNCED_IDS.contains(attributeId);
    }

    /**
     * @return legacy holder network id, or {@code -1} if this server slot is not present on the 26.1 client
     */
    public static int toLegacyHolderNetworkId(int serverHolderNetworkId) {
        return serverHolderIdToLegacyHolderId.get(serverHolderNetworkId);
    }

    private LegacyAttributeWireTable() {}
}
