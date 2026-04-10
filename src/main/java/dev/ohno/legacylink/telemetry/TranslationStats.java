package dev.ohno.legacylink.telemetry;

import dev.ohno.legacylink.LegacyLinkMod;

import java.util.concurrent.atomic.AtomicLong;

public final class TranslationStats {

    private static final AtomicLong registriesFiltered = new AtomicLong();
    private static final AtomicLong registryEntriesFiltered = new AtomicLong();
    private static final AtomicLong blockRemaps = new AtomicLong();
    private static final AtomicLong entityRemaps = new AtomicLong();
    private static final AtomicLong advancementsDropped = new AtomicLong();
    private static final AtomicLong errors = new AtomicLong();

    public static void recordRegistryFiltered(String registryId) {
        registriesFiltered.incrementAndGet();
    }

    public static void recordRegistryEntryFiltered(String registryId, String entryId) {
        registryEntriesFiltered.incrementAndGet();
    }

    public static void recordBlockRemap() {
        blockRemaps.incrementAndGet();
    }

    public static void recordEntityRemap() {
        entityRemaps.incrementAndGet();
    }

    public static void recordAdvancementsDropped() {
        advancementsDropped.incrementAndGet();
    }

    public static void recordError() {
        errors.incrementAndGet();
    }

    public static void dump() {
        LegacyLinkMod.LOGGER.info(
                "[LegacyLink] Session stats — registries_filtered={}, registry_entries_filtered={}, " +
                        "block_remaps={}, entity_remaps={}, advancements_dropped={}, errors={}",
                registriesFiltered.get(),
                registryEntriesFiltered.get(),
                blockRemaps.get(),
                entityRemaps.get(),
                advancementsDropped.get(),
                errors.get()
        );
    }

    private TranslationStats() {}
}
