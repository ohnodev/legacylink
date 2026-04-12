package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.connection.LegacyTracker;
import dev.ohno.legacylink.encoding.LegacyInboundDecoding;
import dev.ohno.legacylink.encoding.LegacyItemStackWireCodec;
import dev.ohno.legacylink.encoding.LegacyOutboundEncoding;
import dev.ohno.legacylink.mapping.LegacyItemIdTable;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rewrites {@link ItemStackTemplate#STREAM_CODEC} item ids for legacy connections.
 * Used by packets like update_advancements display icons that do not go through optional item stack codecs.
 */
@Mixin(ItemStackTemplate.class)
public abstract class ItemStackTemplateCodecMixin {

    @Mutable
    @Shadow
    @Final
    public static StreamCodec<RegistryFriendlyByteBuf, ItemStackTemplate> STREAM_CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void legacylink$wrapTemplateCodec(CallbackInfo ci) {
        final StreamCodec<RegistryFriendlyByteBuf, ItemStackTemplate> vanilla = STREAM_CODEC;
        STREAM_CODEC = new StreamCodec<>() {
            @Override
            public ItemStackTemplate decode(RegistryFriendlyByteBuf input) {
                Connection inbound = LegacyInboundDecoding.connection();
                if (inbound == null || !LegacyTracker.isLegacy(inbound)) {
                    return vanilla.decode(input);
                }
                int legacyWireId = input.readVarInt();
                Holder<Item> holder = LegacyItemStackWireCodec.holderFromLegacyWireId(legacyWireId);
                int count = input.readVarInt();
                DataComponentPatch components = DataComponentPatch.STREAM_CODEC.decode(input);
                return new ItemStackTemplate(holder, count, components);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf output, ItemStackTemplate value) {
                Connection outbound = LegacyOutboundEncoding.connection();
                if (outbound == null || !LegacyTracker.isLegacy(outbound)) {
                    vanilla.encode(output, value);
                    return;
                }
                int legacyId = LegacyItemIdTable.toLegacyId(Item.getId(value.item().value()));
                output.writeVarInt(legacyId);
                output.writeVarInt(value.count());
                DataComponentPatch.STREAM_CODEC.encode(output, value.components());
            }
        };
    }
}
