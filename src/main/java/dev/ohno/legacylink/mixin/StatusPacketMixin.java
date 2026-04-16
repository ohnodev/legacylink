package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.connection.LegacyTracker;
import dev.ohno.legacylink.status.LegacyStatusCacheManager;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.server.network.ServerStatusPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerStatusPacketListenerImpl.class)
public abstract class StatusPacketMixin {

    @Shadow @Final private Connection connection;
    @Shadow @Final private ServerStatus status;
    @Shadow private boolean hasRequestedStatus;

    @Inject(method = "handleStatusRequest", at = @At("HEAD"), cancellable = true)
    private void legacylink$rewriteLegacyStatus(ServerboundStatusRequestPacket packet, CallbackInfo ci) {
        if (!LegacyTracker.isLegacy(this.connection)) {
            return;
        }
        if (this.hasRequestedStatus) {
            return;
        }

        this.hasRequestedStatus = true;
        this.connection.send(getOrBuildCachedResponse(this.status));
        ci.cancel();
    }

    private static ClientboundStatusResponsePacket getOrBuildCachedResponse(ServerStatus current) {
        return LegacyStatusCacheManager.getOrBuildForStatusListener(current);
    }
}
