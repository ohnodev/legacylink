package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.connection.LegacyTracker;
import dev.ohno.legacylink.handler.LegacyPacketHandler;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ConfigurationFinishMixin {

    @Inject(
            method = "handleConfigurationFinished",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/Connection;setupOutboundProtocol(Lnet/minecraft/network/ProtocolInfo;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void legacylink$reinstallAfterOutboundSwitch(ServerboundFinishConfigurationPacket packet, CallbackInfo ci) {
        var connection = ((ServerCommonConnectionAccessor) this).legacylink$getConnection();
        if (LegacyTracker.isLegacy(connection)) {
            LegacyPacketHandler.install(connection);
        }
    }
}
