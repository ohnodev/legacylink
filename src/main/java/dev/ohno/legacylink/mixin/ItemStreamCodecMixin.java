package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.connection.LegacyTracker;
import dev.ohno.legacylink.encoding.LegacyInboundDecoding;
import dev.ohno.legacylink.encoding.LegacyItemStackWireCodec;
import dev.ohno.legacylink.encoding.LegacyOutboundEncoding;
import dev.ohno.legacylink.mapping.LegacyItemIdTable;
import net.minecraft.core.Holder;
import net.minecraft.network.Connection;
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
 * Rewrites raw Item holder ids for legacy connections.
 * Needed for recipe display paths that encode plain Item holders (not ItemStackTemplate).
 */
@Mixin(Item.class)
public abstract class ItemStreamCodecMixin {

    @Mutable
    @Shadow
    @Final
    public static StreamCodec<RegistryFriendlyByteBuf, Holder<Item>> STREAM_CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void legacylink$wrapItemStreamCodec(CallbackInfo ci) {
        final StreamCodec<RegistryFriendlyByteBuf, Holder<Item>> vanilla = STREAM_CODEC;
        STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Holder<Item> decode(RegistryFriendlyByteBuf input) {
                Connection inbound = LegacyInboundDecoding.connection();
                if (inbound == null || !LegacyTracker.isLegacy(inbound)) {
                    return vanilla.decode(input);
                }
                int legacyWireId = input.readVarInt();
                return LegacyItemStackWireCodec.holderFromLegacyWireId(legacyWireId);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf output, Holder<Item> value) {
                Connection outbound = LegacyOutboundEncoding.connection();
                if (outbound == null || !LegacyTracker.isLegacy(outbound)) {
                    vanilla.encode(output, value);
                    return;
                }
                int legacyId = LegacyItemIdTable.toLegacyId(Item.getId(value.value()));
                output.writeVarInt(legacyId);
            }
        };
    }
}
