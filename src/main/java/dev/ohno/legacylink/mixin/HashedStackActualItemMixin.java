package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.connection.LegacyTracker;
import dev.ohno.legacylink.encoding.LegacyInboundDecoding;
import dev.ohno.legacylink.encoding.LegacyItemStackWireCodec;
import dev.ohno.legacylink.encoding.LegacyOutboundEncoding;
import dev.ohno.legacylink.mapping.LegacyItemIdTable;
import net.minecraft.core.Holder;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remaps raw item registry varints in {@link HashedStack.ActualItem} for legacy clients (container click packets).
 */
@Mixin(HashedStack.ActualItem.class)
public abstract class HashedStackActualItemMixin {

    @Mutable
    @Shadow
    @Final
    public static StreamCodec<RegistryFriendlyByteBuf, HashedStack.ActualItem> STREAM_CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void legacylink$wrapActualItemCodec(CallbackInfo ci) {
        final StreamCodec<RegistryFriendlyByteBuf, HashedStack.ActualItem> vanilla = STREAM_CODEC;
        STREAM_CODEC = new StreamCodec<>() {
            @Override
            public HashedStack.ActualItem decode(RegistryFriendlyByteBuf input) {
                Connection inbound = LegacyInboundDecoding.connection();
                if (inbound == null || !LegacyTracker.isLegacy(inbound)) {
                    return vanilla.decode(input);
                }
                int wireItemId = input.readVarInt();
                Holder<Item> holder = LegacyItemStackWireCodec.holderFromLegacyWireId(wireItemId);
                int count = input.readVarInt();
                HashedPatchMap components = HashedPatchMap.STREAM_CODEC.decode(input);
                return new HashedStack.ActualItem(holder, count, components);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf output, HashedStack.ActualItem value) {
                Connection outbound = LegacyOutboundEncoding.connection();
                if (outbound == null || !LegacyTracker.isLegacy(outbound)) {
                    vanilla.encode(output, value);
                    return;
                }
                int legacyId = LegacyItemIdTable.toLegacyId(Item.getId(value.item().value()));
                output.writeVarInt(legacyId);
                output.writeVarInt(value.count());
                HashedPatchMap.STREAM_CODEC.encode(output, value.components());
            }
        };
    }
}
