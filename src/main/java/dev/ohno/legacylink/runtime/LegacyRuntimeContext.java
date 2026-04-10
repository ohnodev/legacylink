package dev.ohno.legacylink.runtime;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;

public final class LegacyRuntimeContext {

    private static volatile RegistryAccess registryAccess;
    private static volatile PalettedContainerFactory chunkContainerFactory;
    private static volatile int sectionCount;

    public static void initialize(RegistryAccess access, PalettedContainerFactory factory, int sections) {
        registryAccess = access;
        chunkContainerFactory = factory;
        sectionCount = sections;
    }

    public static RegistryAccess registryAccess() {
        return registryAccess;
    }

    public static PalettedContainerFactory chunkContainerFactory() {
        return chunkContainerFactory;
    }

    public static int sectionCount() {
        return sectionCount;
    }

    public static boolean isReady() {
        return registryAccess != null && chunkContainerFactory != null && sectionCount > 0;
    }

    private LegacyRuntimeContext() {}
}
