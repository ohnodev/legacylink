package dev.ohno.legacylink.status;

import dev.ohno.legacylink.LegacyLinkConstants;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;

import java.util.Optional;

/**
 * Centralized status remap cache so all status pathways share identical TTL and keying rules.
 */
public final class LegacyStatusCacheManager {
    private static final long STATUS_CACHE_TTL_NS = 1_000_000_000L; // 1 second

    private static volatile CacheEntry listenerCache;
    private static volatile CacheEntry outboundHandlerCache;

    private LegacyStatusCacheManager() {}

    public static ClientboundStatusResponsePacket getOrBuildForStatusListener(ServerStatus current) {
        return getOrBuild(current, CacheScope.LISTENER);
    }

    public static ClientboundStatusResponsePacket getOrBuildForOutboundHandler(ServerStatus current) {
        return getOrBuild(current, CacheScope.OUTBOUND_HANDLER);
    }

    private static ClientboundStatusResponsePacket getOrBuild(ServerStatus current, CacheScope scope) {
        long now = System.nanoTime();
        CacheEntry cached = read(scope);
        if (cached != null && cached.sourceStatus == current && (now - cached.builtAtNanos) < STATUS_CACHE_TTL_NS) {
            return cached.response;
        }

        ClientboundStatusResponsePacket built = buildLegacyStatusResponse(current);
        write(scope, new CacheEntry(built, current, now));
        return built;
    }

    private static CacheEntry read(CacheScope scope) {
        return scope == CacheScope.LISTENER ? listenerCache : outboundHandlerCache;
    }

    private static void write(CacheScope scope, CacheEntry entry) {
        if (scope == CacheScope.LISTENER) {
            listenerCache = entry;
        } else {
            outboundHandlerCache = entry;
        }
    }

    private static ClientboundStatusResponsePacket buildLegacyStatusResponse(ServerStatus current) {
        ServerStatus.Version forcedLegacyVersion = new ServerStatus.Version(
                "26.1.2",
                LegacyLinkConstants.PROTOCOL_26_1_2
        );
        ServerStatus remapped = new ServerStatus(
                current.description(),
                current.players(),
                Optional.of(forcedLegacyVersion),
                current.favicon(),
                current.enforcesSecureChat()
        );
        return new ClientboundStatusResponsePacket(remapped);
    }

    private enum CacheScope {
        LISTENER,
        OUTBOUND_HANDLER
    }

    private static final class CacheEntry {
        private final ClientboundStatusResponsePacket response;
        private final ServerStatus sourceStatus;
        private final long builtAtNanos;

        private CacheEntry(ClientboundStatusResponsePacket response, ServerStatus sourceStatus, long builtAtNanos) {
            this.response = response;
            this.sourceStatus = sourceStatus;
            this.builtAtNanos = builtAtNanos;
        }
    }
}
