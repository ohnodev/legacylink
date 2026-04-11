package dev.ohno.legacylink.runtime;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

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

    /**
     * Clears server reference and overworld-derived chunk context (call on server stop after {@link #bindServer}{@code (null)} if desired).
     */
    public static void reset() {
        registryAccess = null;
        chunkContainerFactory = null;
        sectionCount = 0;
    }

    public static @Nullable MinecraftServer server() {
        return server;
    }

    /**
     * Looks up an entity on the server levels. When not called from the server thread, schedules the lookup on the
     * server thread and waits for the result (same semantics as other cross-thread world access guards).
     */
    public static @Nullable Entity findEntity(int entityId) {
        MinecraftServer s = server;
        if (s == null) {
            return null;
        }
        if (s.isSameThread()) {
            return findEntityOnLevels(entityId);
        }
        CompletableFuture<Entity> future = new CompletableFuture<>();
        s.execute(() -> {
            try {
                future.complete(findEntityOnLevels(entityId));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.join();
        } catch (Exception e) {
            return null;
        }
    }

    private static @Nullable Entity findEntityOnLevels(int entityId) {
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
        RegistryAccess ra = registryAccess;
        if (ra == null) {
            throw new IllegalStateException("LegacyRuntimeContext not initialized — call initialize() after SERVER_STARTED");
        }
        return ra;
    }

    public static PalettedContainerFactory chunkContainerFactory() {
        PalettedContainerFactory f = chunkContainerFactory;
        if (f == null) {
            throw new IllegalStateException("LegacyRuntimeContext not initialized — call initialize() after SERVER_STARTED");
        }
        return f;
    }

    public static int sectionCount() {
        return sectionCount;
    }

    public static boolean isReady() {
        return registryAccess != null && chunkContainerFactory != null && sectionCount > 0;
    }

    private LegacyRuntimeContext() {}
}
