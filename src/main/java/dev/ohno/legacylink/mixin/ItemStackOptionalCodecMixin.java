package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.encoding.LegacyItemStackWireCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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
 * Wraps {@link ItemStack#OPTIONAL_STREAM_CODEC} and {@link ItemStack#OPTIONAL_UNTRUSTED_STREAM_CODEC} so legacy
 * connections use 26.1 item wire ids on the wire (encode) and map them back to 26.2 registry ids on decode.
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
    public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_UNTRUSTED_STREAM_CODEC;

    @Mutable
    @Shadow
    @Final
    public static StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> OPTIONAL_LIST_STREAM_CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void legacylink$wrapOptionalItemStackCodecs(CallbackInfo ci) {
        final StreamCodec<RegistryFriendlyByteBuf, ItemStack> trustedVanilla = OPTIONAL_STREAM_CODEC;
        final StreamCodec<RegistryFriendlyByteBuf, ItemStack> untrustedVanilla = OPTIONAL_UNTRUSTED_STREAM_CODEC;

        OPTIONAL_STREAM_CODEC = new StreamCodec<>() {
            @Override
            public ItemStack decode(RegistryFriendlyByteBuf input) {
                return LegacyItemStackWireCodec.decodeOptional(
                        input,
                        DataComponentPatch.STREAM_CODEC,
                        trustedVanilla
                );
            }

            @Override
            public void encode(RegistryFriendlyByteBuf output, ItemStack stack) {
                LegacyItemStackWireCodec.encodeOptional(
                        output,
                        stack,
                        DataComponentPatch.STREAM_CODEC,
                        trustedVanilla
                );
            }
        };

        OPTIONAL_UNTRUSTED_STREAM_CODEC = new StreamCodec<>() {
            @Override
            public ItemStack decode(RegistryFriendlyByteBuf input) {
                return LegacyItemStackWireCodec.decodeOptional(
                        input,
                        DataComponentPatch.DELIMITED_STREAM_CODEC,
                        untrustedVanilla
                );
            }

            @Override
            public void encode(RegistryFriendlyByteBuf output, ItemStack stack) {
                LegacyItemStackWireCodec.encodeOptional(
                        output,
                        stack,
                        DataComponentPatch.DELIMITED_STREAM_CODEC,
                        untrustedVanilla
                );
            }
        };

        OPTIONAL_LIST_STREAM_CODEC = OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.collection(NonNullList::createWithCapacity));
    }
}
