package dev.ohno.legacylink;

import dev.ohno.legacylink.mapping.RegistryRemapper;
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

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            RegistryRemapper.buildMappings();
            LOGGER.info("[LegacyLink] Block/item/entity mappings built — {} block states remapped, {} items remapped",
                    RegistryRemapper.blockStateRemapCount(), RegistryRemapper.itemRemapCount());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TranslationStats.dump();
        });

        LOGGER.info("[LegacyLink] Ready — 26.1 clients (protocol {}) will be accepted",
                LegacyLinkConstants.PROTOCOL_26_1);
    }
}
