package dev.ohno.legacylink.encoding;

import dev.ohno.legacylink.connection.LegacyTracker;
import dev.ohno.legacylink.mapping.LegacyItemIdTable;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Shared optional item-stack wire format (count, raw item id varint, component patch) for legacy clients.
 */
public final class LegacyItemStackWireCodec {

    private LegacyItemStackWireCodec() {}

    /**
     * Resolve a 26.1 client item wire id to a server {@link Item} holder, using the same remap and
     * {@link Items#STONE} fallback as optional-stack decode.
     */
    public static Holder<Item> holderFromLegacyWireId(int legacyWireId) {
        int serverId = LegacyItemIdTable.serverItemIdFromLegacyWire(legacyWireId);
        Item item = BuiltInRegistries.ITEM.byId(serverId);
        if (item == null) {
            item = Items.STONE;
        }
        return item.builtInRegistryHolder();
    }

    public static ItemStack decodeOptional(
            RegistryFriendlyByteBuf input,
            StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> patchCodec,
            StreamCodec<RegistryFriendlyByteBuf, ItemStack> vanillaDelegate
    ) {
        Connection conn = LegacyInboundDecoding.connection();
        if (conn == null || !LegacyTracker.isLegacy(conn)) {
            return vanillaDelegate.decode(input);
        }
        int count = input.readVarInt();
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        int legacyWireId = input.readVarInt();
        Holder<Item> holder = holderFromLegacyWireId(legacyWireId);
        DataComponentPatch patch = patchCodec.decode(input);
        return new ItemStack(holder, count, patch);
    }

    public static void encodeOptional(
            RegistryFriendlyByteBuf output,
            ItemStack stack,
            StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> patchCodec,
            StreamCodec<RegistryFriendlyByteBuf, ItemStack> vanillaDelegate
    ) {
        Connection conn = LegacyOutboundEncoding.connection();
        if (conn == null || !LegacyTracker.isLegacy(conn)) {
            vanillaDelegate.encode(output, stack);
            return;
        }
        if (stack.isEmpty()) {
            output.writeVarInt(0);
            return;
        }
        output.writeVarInt(stack.getCount());
        int serverItemId = Item.getId(stack.getItem());
        output.writeVarInt(LegacyItemIdTable.toLegacyId(serverItemId));
        patchCodec.encode(output, stack.getComponentsPatch());
    }
}
