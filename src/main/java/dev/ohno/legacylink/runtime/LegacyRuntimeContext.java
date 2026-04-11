package dev.ohno.legacylink.runtime;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import org.jspecify.annotations.Nullable;

public final class LegacyRuntimeContext {

    private static volatile RegistryAccess registryAccess;
    private static volatile PalettedContainerFactory chunkContainerFactory;
    private static volatile int sectionCount;
    private static volatile @Nullable MinecraftServer server;

    public static void initialize(RegistryAccess access, PalettedContainerFactory factory, int sections) {
        registryAccess = access;
        chunkContainerFactory = factory;
        sectionCount = sections;
    }

    public static void bindServer(@Nullable MinecraftServer minecraftServer) {
        server = minecraftServer;
    }

    public static @Nullable MinecraftServer server() {
        return server;
    }

    public static @Nullable Entity findEntity(int entityId) {
        MinecraftServer s = server;
        if (s == null) {
            return null;
        }
        for (ServerLevel level : s.getAllLevels()) {
            Entity e = level.getEntity(entityId);
            if (e != null) {
                return e;
            }
        }
        return null;
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
