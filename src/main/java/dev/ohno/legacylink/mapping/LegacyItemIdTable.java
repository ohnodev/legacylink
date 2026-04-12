package dev.ohno.legacylink.mapping;

import dev.ohno.legacylink.LegacyLinkConstants;
import dev.ohno.legacylink.LegacyLinkMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Static 26.2 → 26.1 item wire-ID table, built once from {@link BuiltInRegistries#ITEM} iteration order.
 * <p>
 * 26.2 inserts sulfur + cinnabar items into the middle of the built-in item registry.
 * The 26.1 client's built-in registry has no knowledge of these items, so every vanilla item
 * that comes <em>after</em> an insertion has a lower numeric ID on the 26.1 client.
 * <p>
 * Via pattern: iterate the full 26.2 registry, skip 26.2-only entries, assign sequential legacy IDs
 * to everything else. Wire-level encoding then emits the legacy ID instead of the server's native one.
 * <p>
 * The inverse map (26.1 wire id → 26.2 server numeric id) is built for inbound decode; collisions from
 * multiple server items sharing one legacy id (e.g. stone substitution) resolve in favor of {@link Items#STONE}.
 */
public final class LegacyItemIdTable {

    private static int[] toLegacy = new int[0];
    /** {@code legacyToServer[legacyWireId]} = server {@link Item#getId(Item)}; length {@link #legacyCount}. */
    private static int[] legacyToServer = new int[0];
    private static int legacyStoneId = 1;
    private static int stoneServerItemId = 1;
    private static int legacyCount = 0;

    private LegacyItemIdTable() {}

    public static void rebuild() {
        int size = BuiltInRegistries.ITEM.size();
        int[] table = new int[size];
        int nextLegacyId = 0;
        stoneServerItemId = Item.getId(Items.STONE);

        for (Item item : BuiltInRegistries.ITEM) {
            int serverId = Item.getId(item);
            if (is26_2Only(item)) {
                table[serverId] = -1;
            } else {
                table[serverId] = nextLegacyId;
                nextLegacyId++;
            }
        }

        int stoneLegacy = (stoneServerItemId >= 0 && stoneServerItemId < table.length)
                ? table[stoneServerItemId] : 1;
        if (stoneLegacy < 0) stoneLegacy = 1;

        for (int i = 0; i < table.length; i++) {
            if (table[i] < 0) {
                table[i] = stoneLegacy;
            }
        }

        toLegacy = table;
        legacyStoneId = stoneLegacy;
        legacyCount = nextLegacyId;

        legacyToServer = buildLegacyToServerInverse(table, nextLegacyId, stoneServerItemId);

        LegacyLinkMod.LOGGER.info(
                "[LegacyLink] Item wire-ID table built: {} server items → {} legacy IDs (expected legacy max {})",
                size, nextLegacyId, LegacyLinkConstants.MAX_26_1_ITEM_ID);

        if (nextLegacyId != LegacyLinkConstants.MAX_26_1_ITEM_ID + 1) {
            LegacyLinkMod.LOGGER.warn(
                    "[LegacyLink] Legacy item count {} does not match expected {} — check 26.2-only item detection",
                    nextLegacyId, LegacyLinkConstants.MAX_26_1_ITEM_ID + 1);
        }
    }

    private static int[] buildLegacyToServerInverse(int[] toLegacyTable, int legacySize, int stoneServerId) {
        @SuppressWarnings("unchecked")
        List<Integer>[] buckets = new List[legacySize];
        for (int serverId = 0; serverId < toLegacyTable.length; serverId++) {
            int leg = toLegacyTable[serverId];
            if (leg < 0 || leg >= legacySize) {
                continue;
            }
            if (buckets[leg] == null) {
                buckets[leg] = new ArrayList<>();
            }
            buckets[leg].add(serverId);
        }
        int[] inverse = new int[legacySize];
        for (int leg = 0; leg < legacySize; leg++) {
            List<Integer> bucket = buckets[leg];
            if (bucket == null || bucket.isEmpty()) {
                inverse[leg] = stoneServerId;
                continue;
            }
            if (bucket.size() == 1) {
                inverse[leg] = bucket.getFirst();
                continue;
            }
            int chosen = bucket.getFirst();
            if (bucket.contains(stoneServerId)) {
                chosen = stoneServerId;
            }
            inverse[leg] = chosen;
        }
        return inverse;
    }

    /**
     * Translate a 26.2 server item wire ID to the corresponding 26.1 client wire ID.
     */
    public static int toLegacyId(int serverItemId) {
        int[] t = toLegacy;
        if (serverItemId < 0 || serverItemId >= t.length) {
            return legacyStoneId;
        }
        return t[serverItemId];
    }

    public static int legacyItemCount() {
        return legacyCount;
    }

    /**
     * Map a 26.1 client item wire id to the 26.2 server's numeric item id for {@link BuiltInRegistries#ITEM}.
     */
    public static int serverItemIdFromLegacyWire(int legacyWireId) {
        int[] inv = legacyToServer;
        if (legacyWireId < 0 || legacyWireId >= inv.length) {
            return stoneServerItemId;
        }
        return inv[legacyWireId];
    }

    private static boolean is26_2Only(Item item) {
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        if (key == null) {
            return true;
        }
        // Legacy clients only know vanilla item registries; any custom/mod namespace must be collapsed.
        if (!"minecraft".equals(key.getNamespace())) {
            return true;
        }
        return LegacyLinkConstants.is26_2OnlyItemId(key.toString());
    }
}
