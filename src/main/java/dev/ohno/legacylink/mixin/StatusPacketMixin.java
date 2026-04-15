package dev.ohno.legacylink.mixin;

import dev.ohno.legacylink.LegacyLinkConstants;
import dev.ohno.legacylink.connection.LegacyTracker;
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

import java.util.Optional;

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
        ServerStatus.Version forcedLegacyVersion = new ServerStatus.Version(
                "26.1.2",
                LegacyLinkConstants.PROTOCOL_26_1_2
        );
        ServerStatus remapped = new ServerStatus(
                this.status.description(),
                this.status.players(),
                Optional.of(forcedLegacyVersion),
                this.status.favicon(),
                this.status.enforcesSecureChat()
        );
        this.connection.send(new ClientboundStatusResponsePacket(remapped));
        ci.cancel();
    }
}
