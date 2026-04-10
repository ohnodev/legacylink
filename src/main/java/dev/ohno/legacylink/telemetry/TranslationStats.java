package dev.ohno.legacylink.telemetry;

import dev.ohno.legacylink.LegacyLinkMod;

import java.util.concurrent.atomic.AtomicLong;

public final class TranslationStats {

    private static final AtomicLong registriesFiltered = new AtomicLong();
    private static final AtomicLong registryEntriesFiltered = new AtomicLong();
    private static final AtomicLong blockRemaps = new AtomicLong();
    private static final AtomicLong sectionBlockRemaps = new AtomicLong();
    private static final AtomicLong chunkBlockStateRemaps = new AtomicLong();
    private static final AtomicLong chunkBlockEntitiesFiltered = new AtomicLong();
    private static final AtomicLong entityRemaps = new AtomicLong();
    private static final AtomicLong advancementsRemapped = new AtomicLong();
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

    public static void recordSectionBlocksRemap(int count) {
        sectionBlockRemaps.addAndGet(count);
    }

    public static void recordChunkRemap(int blockStates, int blockEntities) {
        chunkBlockStateRemaps.addAndGet(blockStates);
        chunkBlockEntitiesFiltered.addAndGet(blockEntities);
    }

    public static void recordEntityRemap() {
        entityRemaps.incrementAndGet();
    }

    public static void recordAdvancementsRemapped(int count) {
        advancementsRemapped.addAndGet(count);
    }

    public static void recordError() {
        errors.incrementAndGet();
    }

    public static void dump() {
        LegacyLinkMod.LOGGER.info(
                "[LegacyLink] Session stats — registries_filtered={}, registry_entries_filtered={}, " +
                        "block_remaps={}, section_block_remaps={}, chunk_blockstate_remaps={}, " +
                        "chunk_block_entities_filtered={}, entity_remaps={}, advancements_remapped={}, errors={}",
                registriesFiltered.get(),
                registryEntriesFiltered.get(),
                blockRemaps.get(),
                sectionBlockRemaps.get(),
                chunkBlockStateRemaps.get(),
                chunkBlockEntitiesFiltered.get(),
                entityRemaps.get(),
                advancementsRemapped.get(),
                errors.get()
        );
    }

    private TranslationStats() {}
}
