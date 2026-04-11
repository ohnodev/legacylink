package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.connection.LegacyTracker;
import dev.ohno.legacylink.encoding.LegacyOutboundEncoding;
import dev.ohno.legacylink.mapping.LegacyEntityTypeWireRemapper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientboundAddEntityPacket.class)
public abstract class ClientboundAddEntityPacketMixin {

    @Redirect(
            method = "write(Lnet/minecraft/network/RegistryFriendlyByteBuf;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/codec/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V",
                    ordinal = 0
            )
    )
    private static void legacylink$encodeEntityTypeWithLegacyRegistryIndices(
            StreamCodec<?, ?> codec,
            Object output,
            Object value
    ) {
        if (output instanceof RegistryFriendlyByteBuf buf && value instanceof EntityType<?> type) {
            Connection connection = LegacyOutboundEncoding.connection();
            if (connection != null && LegacyTracker.isLegacy(connection)) {
                buf.writeVarInt(LegacyEntityTypeWireRemapper.legacyNetworkId(type));
                return;
            }
        }
        @SuppressWarnings("unchecked")
        StreamCodec<RegistryFriendlyByteBuf, EntityType<?>> typed =
                (StreamCodec<RegistryFriendlyByteBuf, EntityType<?>>) (Object) codec;
        typed.encode((RegistryFriendlyByteBuf) output, (EntityType<?>) value);
    }
}
