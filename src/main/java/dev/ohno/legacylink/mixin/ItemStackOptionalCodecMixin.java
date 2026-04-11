package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.connection.LegacyTracker;
import dev.ohno.legacylink.encoding.LegacyOutboundEncoding;
import dev.ohno.legacylink.mapping.LegacyItemIdTable;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Wraps {@link ItemStack#OPTIONAL_STREAM_CODEC} to emit 26.1-compatible item wire IDs for legacy clients.
 * <p>
 * 26.2 inserts new items (sulfur, cinnabar) into the built-in item registry, shifting numeric IDs of all
 * subsequent items. The 26.1 client uses its own built-in IDs to decode, so we must translate the varint
 * that {@code Item.STREAM_CODEC} would normally write.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackOptionalCodecMixin {

    @Mutable
    @Shadow
    @Final
    public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_STREAM_CODEC;

    @Mutable
    @Shadow
    @Final
    public static StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> OPTIONAL_LIST_STREAM_CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void legacylink$wrapOptionalItemStackCodec(CallbackInfo ci) {
        final StreamCodec<RegistryFriendlyByteBuf, ItemStack> delegate = OPTIONAL_STREAM_CODEC;
        OPTIONAL_STREAM_CODEC = new StreamCodec<>() {
            @Override
            public ItemStack decode(RegistryFriendlyByteBuf input) {
                return delegate.decode(input);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf output, ItemStack stack) {
                Connection connection = LegacyOutboundEncoding.connection();
                if (connection == null || !LegacyTracker.isLegacy(connection)) {
                    delegate.encode(output, stack);
                    return;
                }

                if (stack.isEmpty()) {
                    output.writeVarInt(0);
                    return;
                }

                output.writeVarInt(stack.getCount());
                int serverItemId = Item.getId(stack.getItem());
                int legacyItemId = LegacyItemIdTable.toLegacyId(serverItemId);
                output.writeVarInt(legacyItemId);
                DataComponentPatch.STREAM_CODEC.encode(output, stack.getComponentsPatch());
            }
        };
        OPTIONAL_LIST_STREAM_CODEC = OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.collection(NonNullList::createWithCapacity));
    }
}
