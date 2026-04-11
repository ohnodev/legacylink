package dev.ohno.legacylink;

import dev.ohno.legacylink.debug.LegacyOutboundPacketCapture;
import dev.ohno.legacylink.debug.LegacyPacketMapTrace;
import dev.ohno.legacylink.debug.PositionPacketTrace;
import dev.ohno.legacylink.debug.SpawnPacketTrace;
import dev.ohno.legacylink.mapping.RegistryRemapper;
import dev.ohno.legacylink.runtime.LegacyRuntimeContext;
import dev.ohno.legacylink.telemetry.TranslationStats;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegacyLinkMod implements ModInitializer {

    public static final String MOD_ID = "legacylink";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[LegacyLink] Initializing protocol bridge (26.2 -> 26.1)");

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LegacyRuntimeContext.initialize(
                    server.registryAccess(),
                    server.overworld().palettedContainerFactory(),
                    server.overworld().getSectionsCount()
            );
            LegacyRuntimeContext.bindServer(server);
            RegistryRemapper.buildMappings();
            LOGGER.info("[LegacyLink] Block/item/entity mappings built — {} block states remapped, {} items remapped",
                    RegistryRemapper.blockStateRemapCount(), RegistryRemapper.itemRemapCount());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LegacyRuntimeContext.bindServer(null);
            LegacyRuntimeContext.reset();
            TranslationStats.dump();
        });

        LOGGER.info("[LegacyLink] Ready — 26.1 clients (protocol {}) will be accepted",
                LegacyLinkConstants.PROTOCOL_26_1);

        if (PositionPacketTrace.enabled()) {
            LOGGER.warn("[LegacyLink] Position tracing ON (-Dlegacylink.tracePositions=true). "
                    + "stage=connection_send (26.2) vs stage=post_legacy_rewrite (26.1 after translation); "
                    + "grep [LegacyLink][PosTrace] in latest.log; disable after capture.");
        }
        if (SpawnPacketTrace.enabled()) {
            LOGGER.warn("[LegacyLink] Spawn/join tracing ON (-Dlegacylink.traceSpawn=true). "
                    + "login, respawn, default spawn, add_entity, player_position; grep [LegacyLink][SpawnTrace]; "
                    + "no move_entity spam.");
        }
        if (LegacyOutboundPacketCapture.enabled()) {
            LOGGER.warn("[LegacyLink] Outbound packet capture ON (-Dlegacylink.captureOutbound=true). "
                    + "Every legacy clientbound packet logged to latest.log; grep [LegacyLink][OutboundCapture]. "
                    + "Stages: connection_send (pre-handler) vs post_legacy_rewrite (wire-bound). Very verbose — disable after capture.");
        }
        if (LegacyPacketMapTrace.enabled()) {
            LOGGER.warn("[LegacyLink] Packet map trace ON (-Dlegacylink.tracePacketMap=true). "
                    + "PRE/POST remap lines and entity_data_ctx; grep [LegacyLink][PacketMap]. Very verbose — disable after debugging.");
        }
    }
}
