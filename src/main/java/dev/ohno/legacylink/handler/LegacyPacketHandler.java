package dev.ohno.legacylink.handler;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import dev.ohno.legacylink.LegacyLinkConstants;
import dev.ohno.legacylink.LegacyLinkMod;
import dev.ohno.legacylink.connection.LegacyTracker;
import dev.ohno.legacylink.encoding.LegacyOutboundEncoding;
import dev.ohno.legacylink.integration.PacketEventsVersionBridge;
import dev.ohno.legacylink.debug.CameraPacketTrace;
import dev.ohno.legacylink.debug.EntityDataRewriteTrace;
import dev.ohno.legacylink.debug.LegacyOutboundPacketCapture;
import dev.ohno.legacylink.debug.LegacyPacketMapTrace;
import dev.ohno.legacylink.debug.PositionPacketTrace;
import dev.ohno.legacylink.debug.SpawnPacketTrace;
import dev.ohno.legacylink.handler.rewrite.AdvancementRewriter;
import dev.ohno.legacylink.handler.rewrite.BlockStatePacketRewriter;
import dev.ohno.legacylink.handler.rewrite.EntityMetadataRewriter2661;
import dev.ohno.legacylink.handler.rewrite.RecipeBookAddRewriter;
import dev.ohno.legacylink.handler.rewrite.ItemRewriter;
import dev.ohno.legacylink.handler.rewrite.SlotDisplayUtils;
import dev.ohno.legacylink.mapping.LegacyAttributeWireTable;
import dev.ohno.legacylink.mapping.RegistryRemapper;
import dev.ohno.legacylink.runtime.LegacyRuntimeContext;
import dev.ohno.legacylink.telemetry.TranslationStats;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.BundleDelimiterPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Constructor;
import java.util.Set;
import java.lang.reflect.Field;

import org.jspecify.annotations.Nullable;

public class LegacyPacketHandler extends ChannelDuplexHandler {

    private static final String HANDLER_NAME = "legacylink";
    private static final EntityType<?> LEGACY_SLIME_TYPE = resolveEntityType("minecraft:slime");

    private static final Constructor<ClientboundUpdateAttributesPacket> UPDATE_ATTRIBUTES_REBUILD_CTOR;
    private static final Field TAGS_NETWORK_PAYLOAD_TAGS_FIELD;
    private static final Constructor<TagNetworkSerialization.NetworkPayload> NETWORK_PAYLOAD_CTOR;
    private static final Field RECIPE_ITEMS_FIELD;
    private static final Constructor<RecipePropertySet> RECIPE_PROP_SET_CTOR;

    static {
        try {
            Constructor<ClientboundUpdateAttributesPacket> ctor =
                    ClientboundUpdateAttributesPacket.class.getDeclaredConstructor(int.class, List.class);
            ctor.setAccessible(true);
            UPDATE_ATTRIBUTES_REBUILD_CTOR = ctor;

            TAGS_NETWORK_PAYLOAD_TAGS_FIELD =
                    TagNetworkSerialization.NetworkPayload.class.getDeclaredField("tags");
            TAGS_NETWORK_PAYLOAD_TAGS_FIELD.setAccessible(true);

            Constructor<TagNetworkSerialization.NetworkPayload> payloadCtor =
                    TagNetworkSerialization.NetworkPayload.class.getDeclaredConstructor(Map.class);
            payloadCtor.setAccessible(true);
            NETWORK_PAYLOAD_CTOR = payloadCtor;

            RECIPE_ITEMS_FIELD = RecipePropertySet.class.getDeclaredField("items");
            RECIPE_ITEMS_FIELD.setAccessible(true);
            RECIPE_PROP_SET_CTOR = RecipePropertySet.class.getDeclaredConstructor(Set.class);
            RECIPE_PROP_SET_CTOR.setAccessible(true);
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    private final Set<Integer> remappedLegacyEntityIds = new HashSet<>();
    /**
     * {@link ClientboundSetEntityDataPacket} is often handled on the Netty thread, where {@link net.minecraft.server.level.ServerLevel#getEntity(int)}
     * is unreliable. We record the client-visible {@link EntityType} from {@link #remapEntitySpawn} instead.
     */
    private final Int2ObjectOpenHashMap<EntityType<?>> clientVisibleEntityTypeById = new Int2ObjectOpenHashMap<>();
    /** Set in {@link #handlerAdded} — used to resolve entities in the legacy client's dimension (not arbitrary levels). */
    private @Nullable Connection boundConnection;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        Object h = ctx.pipeline().get(HandlerNames.PACKET_HANDLER);
        this.boundConnection = h instanceof Connection c ? c : null;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        this.boundConnection = null;
    }

    public static void install(Connection connection) {
        install(connection, "handshake");
    }

    /**
     * @param phase short label for logs — vanilla calls {@code setupOutboundProtocol} again after configuration, so
     *              legacy clients typically see two installs per join (login + post-configuration); STATUS pings are a
     *              separate TCP connection and get their own install from {@code handleIntention}.
     */
    public static void install(Connection connection, String phase) {
        var pipeline = connection.channel.pipeline();
        /*
         * Always remove and re-append after packet_handler. setupOutboundProtocol() replaces the encoder and can add
         * handlers (e.g. unbundler); a no-op "already installed" return can leave legacylink in the wrong place so
         * play-phase packets never hit translateOutbound — 26.1 clients then see raw 26.2 entity metadata (index 20).
         */
        if (pipeline.get(HANDLER_NAME) != null) {
            pipeline.remove(HANDLER_NAME);
        }
        if (pipeline.get(HandlerNames.PACKET_HANDLER) == null) {
            throw new IllegalStateException(
                    "[LegacyLink] Pipeline missing " + HandlerNames.PACKET_HANDLER + " for " + connection.getRemoteAddress()
                            + "; refusing addLast fallback (legacy translation would not run correctly).");
        }
        pipeline.addAfter(HandlerNames.PACKET_HANDLER, HANDLER_NAME, new LegacyPacketHandler());
        LegacyLinkMod.LOGGER.info(
                "[LegacyLink] Outbound translator placed after '{}' (phase={}) for {}",
                HandlerNames.PACKET_HANDLER,
                phase,
                connection.getRemoteAddress()
        );
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        Connection encodeConn = ctx.pipeline().get(HandlerNames.PACKET_HANDLER) instanceof Connection c ? c : null;
        if (encodeConn == null || !LegacyTracker.isLegacy(encodeConn)) {
            ctx.write(msg, promise);
            return;
        }
        PacketEventsVersionBridge.normalizeLegacyUserIfPresent(encodeConn);
        try (LegacyOutboundEncoding.Scope ignored = LegacyOutboundEncoding.enterScoped(encodeConn)) {
            writeTranslated(ctx, msg, promise);
        }
    }

    private void writeTranslated(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Packet<?> originalPacket) {
            Connection traceConn = ctx.pipeline().get(HandlerNames.PACKET_HANDLER) instanceof Connection c0 ? c0 : null;
            Object translated;
            if (LegacyPacketMapTrace.enabled()
                    && traceConn != null
                    && LegacyTracker.isLegacy(traceConn)
                    && LegacyPacketMapTrace.isInteresting(originalPacket)) {
                long mapSeq = LegacyPacketMapTrace.nextSeq();
                LegacyPacketMapTrace.enter(mapSeq);
                try {
                    LegacyPacketMapTrace.logPhase(traceConn, mapSeq, "PRE", originalPacket);
                    translated = translateOutbound(originalPacket);
                    if (translated instanceof Packet<?> postPacket) {
                        LegacyPacketMapTrace.logPhase(traceConn, mapSeq, "POST", postPacket);
                    } else {
                        LegacyLinkMod.LOGGER.warn(
                                "[LegacyLink][PacketMap] seq={} phase=POST unexpected non-packet {}",
                                mapSeq,
                                translated
                        );
                    }
                } finally {
                    LegacyPacketMapTrace.leave();
                }
            } else {
                translated = translateOutbound(originalPacket);
            }
            msg = translated;
            /*
             * {@link net.minecraft.network.PacketEncoder} encodes a {@link ClientboundBundlePacket} in one codec pass.
             * Our attribute holder remapper mixin only runs when the packet instance is {@link ClientboundUpdateAttributesPacket},
             * so attributes inside bundles still carried server registry ids and crashed 26.1 (e.g. ArrayIndexOutOfBounds on decode).
             * Flatten to individual writes so each sub-packet is encoded separately.
             */
            if (msg instanceof ClientboundBundlePacket bundle) {
                List<Packet<? super ClientGamePacketListener>> flat = flattenBundleForEncode(bundle);
                if (flat.isEmpty()) {
                    promise.setSuccess();
                    return;
                }
                var h = ctx.pipeline().get(HandlerNames.PACKET_HANDLER);
                Connection connection = h instanceof Connection c ? c : null;
                int total = flat.size();
                AtomicInteger finished = new AtomicInteger(0);
                for (int i = 0; i < total; i++) {
                    Packet<?> p = flat.get(i);
                    ChannelPromise childPromise = ctx.newPromise();
                    childPromise.addListener(f -> {
                        try {
                            if (!f.isSuccess()) {
                                promise.tryFailure(f.cause());
                            }
                        } finally {
                            if (finished.incrementAndGet() == total && !promise.isDone()) {
                                promise.trySuccess();
                            }
                        }
                    });
                    tracePostRewriteIfEnabled(connection, p);
                    super.write(ctx, p, childPromise);
                }
                return;
            }
            if (msg instanceof Packet<?> rewritten) {
                var h = ctx.pipeline().get(HandlerNames.PACKET_HANDLER);
                if (h instanceof Connection connection) {
                    tracePostRewriteIfEnabled(connection, rewritten);
                }
            }
        }
        super.write(ctx, msg, promise);
    }

    private static void tracePostRewriteIfEnabled(@Nullable Connection connection, Packet<?> packet) {
        if (connection == null) {
            return;
        }
        LegacyOutboundPacketCapture.logIfLegacy(connection, packet, "post_legacy_rewrite");
        if (PositionPacketTrace.enabled()) {
            PositionPacketTrace.traceOutbound(connection, packet, "post_legacy_rewrite");
        }
        if (SpawnPacketTrace.enabled()) {
            SpawnPacketTrace.traceOutbound(connection, packet, "post_legacy_rewrite");
        }
        if (CameraPacketTrace.enabled()) {
            CameraPacketTrace.traceOutbound(connection, packet, "post_legacy_rewrite");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Packet<? super ClientGamePacketListener>> flattenBundleForEncode(ClientboundBundlePacket bundle) {
        List<Packet<? super ClientGamePacketListener>> out = new ArrayList<>();
        for (Packet<? super ClientGamePacketListener> sub : bundle.subPackets()) {
            if (sub instanceof BundleDelimiterPacket) {
                continue;
            }
            if (sub instanceof ClientboundBundlePacket nested) {
                out.addAll(flattenBundleForEncode(nested));
            } else {
                out.add(sub);
            }
        }
        return out;
    }

    private Object translateOutbound(Object msg) {
        if (msg instanceof ClientboundStatusResponsePacket statusResponse) {
            return remapStatusResponse(statusResponse);
        }
        if (msg instanceof ClientboundRegistryDataPacket registryData) {
            return filterRegistryData(registryData);
        }
        return routePlayPacket(msg);
    }

    /**
     * Single dispatch for play-phase clientbound packets. Everything for 26.1 clients runs here (once), after
     * {@link #translateOutbound} handles status/registry.
     */
    private Object routePlayPacket(Object msg) {
        if (msg instanceof ClientboundBundlePacket bundle) {
            return remapBundlePacket(bundle);
        }
        if (msg instanceof ClientboundUpdateAdvancementsPacket advancements) {
            return remapAdvancements(advancements);
        }
        if (msg instanceof ClientboundSetEntityDataPacket entityData) {
            return remapEntityData(entityData);
        }
        if (msg instanceof ClientboundUpdateAttributesPacket updateAttributes) {
            return remapAttributes(updateAttributes);
        }
        if (msg instanceof ClientboundRemoveEntitiesPacket removeEntities) {
            return trackRemovedEntities(removeEntities);
        }
        if (msg instanceof ClientboundLevelChunkWithLightPacket levelChunk) {
            return remapChunkPacket(levelChunk);
        }
        if (msg instanceof ClientboundBlockUpdatePacket blockUpdate) {
            return remapBlockUpdate(blockUpdate);
        }
        if (msg instanceof ClientboundAddEntityPacket addEntity) {
            return remapEntitySpawn(addEntity);
        }
        if (msg instanceof ClientboundContainerSetSlotPacket slotPacket) {
            return remapContainerSetSlot(slotPacket);
        }
        if (msg instanceof ClientboundContainerSetContentPacket contentPacket) {
            return remapContainerSetContent(contentPacket);
        }
        if (msg instanceof ClientboundSetCursorItemPacket cursorPacket) {
            return remapSetCursorItem(cursorPacket);
        }
        if (msg instanceof ClientboundSetPlayerInventoryPacket inventoryPacket) {
            return remapSetPlayerInventory(inventoryPacket);
        }
        if (msg instanceof ClientboundUpdateRecipesPacket recipesPacket) {
            return remapUpdateRecipes(recipesPacket);
        }
        if (msg instanceof ClientboundRecipeBookAddPacket recipeBookAddPacket) {
            return remapRecipeBookAdd(recipeBookAddPacket);
        }
        if (msg instanceof ClientboundUpdateTagsPacket tagsPacket) {
            return remapUpdateTags(tagsPacket);
        }
        return msg;
    }

    private static @Nullable EntityType<?> entityTypeOf(@Nullable Entity entity) {
        return entity == null ? null : entity.getType();
    }

    /**
     * @param fromRecipientWorld result of {@link #entityForLegacyRecipient(int)} for {@code entityId}; pass the same
     * instance for all uses in one handler pass so the world probe and reconciled type do not drift mid-call.
     */
    private EntityType<?> resolveMetadataEntityType(int entityId, @Nullable Entity fromRecipientWorld) {
        /*
         * Prefer an online ServerPlayer match first (wrong non-player hints can corrupt tail trimming for the
         * local player).
         *
         * Then use the entity in the <b>legacy recipient's level</b> as authoritative when present, and reconcile
         * {@link #clientVisibleEntityTypeById} when it disagrees (stale prefetch after id reuse without a processed
         * remove).
         *
         * If the entity is not yet in that level (metadata before add-entity in the same bundle), fall back to the
         * spawn prefetch map only — no blocking global lookup on the Netty thread. Sulfur cubes map to slime for the client.
         */
        MinecraftServer server = LegacyRuntimeContext.server();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.getId() == entityId) {
                    return p.getType();
                }
            }
        }
        EntityType<?> fromSpawn = clientVisibleEntityTypeById.get(entityId);
        if (fromRecipientWorld != null) {
            EntityType<?> worldType = toClientVisibleEntityType(fromRecipientWorld.getType());
            if (fromSpawn != null && fromSpawn != worldType) {
                clientVisibleEntityTypeById.put(entityId, worldType);
            }
            return worldType;
        }
        if (fromSpawn != null) {
            return fromSpawn;
        }
        return null;
    }

    private @Nullable Entity entityForLegacyRecipient(int entityId) {
        Connection c = this.boundConnection;
        if (c == null) {
            return null;
        }
        PacketListener listener = c.getPacketListener();
        if (!(listener instanceof ServerGamePacketListenerImpl game)) {
            return null;
        }
        ServerPlayer recipient = game.player;
        if (recipient == null) {
            return null;
        }
        return recipient.level().getEntity(entityId);
    }

    private static EntityType<?> toClientVisibleEntityType(EntityType<?> actual) {
        Identifier actualKey = BuiltInRegistries.ENTITY_TYPE.getKey(actual);
        if (actualKey != null && LegacyLinkConstants.SULFUR_CUBE_ENTITY_ID.contentEquals(actualKey.toString())) {
            return LEGACY_SLIME_TYPE;
        }
        return actual;
    }

    private static EntityType<?> resolveEntityType(String id) {
        return BuiltInRegistries.ENTITY_TYPE
                .get(Identifier.parse(id))
                .map(Holder.Reference::value)
                .orElseThrow(() -> new IllegalStateException("[LegacyLink] Missing required entity type: " + id));
    }

    private static boolean isEntityType(@Nullable EntityType<?> type, String id) {
        if (type == null) {
            return false;
        }
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key != null && id.contentEquals(key.toString());
    }

    private void prefetchBundleSpawnsRecursively(Packet<?> packet) {
        if (packet instanceof ClientboundBundlePacket nested) {
            for (Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> sub : nested.subPackets()) {
                prefetchBundleSpawnsRecursively(sub);
            }
        } else if (packet instanceof ClientboundAddEntityPacket add) {
            prefetchAddEntityMetadataHints(add);
        }
    }

    @SuppressWarnings("unchecked")
    public ClientboundBundlePacket remapBundlePacket(ClientboundBundlePacket bundlePacket) {
        for (Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> subPacket : bundlePacket.subPackets()) {
            prefetchBundleSpawnsRecursively(subPacket);
        }

        List<Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> rewritten =
                new ArrayList<>();
        boolean changed = false;

        for (Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> subPacket : bundlePacket.subPackets()) {
            Object remapped = translateOutbound(subPacket);
            if (remapped != subPacket) {
                changed = true;
            }
            rewritten.add((Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>) remapped);
        }

        if (!changed) {
            return bundlePacket;
        }
        return new ClientboundBundlePacket(rewritten);
    }

    private ClientboundStatusResponsePacket remapStatusResponse(ClientboundStatusResponsePacket packet) {
        ServerStatus status = packet.status();
        ServerStatus.Version forcedLegacyVersion = new ServerStatus.Version("26.1.2", LegacyLinkConstants.PROTOCOL_26_1_2);
        ServerStatus remapped = new ServerStatus(
                status.description(),
                status.players(),
                Optional.of(forcedLegacyVersion),
                status.favicon(),
                status.enforcesSecureChat()
        );
        return new ClientboundStatusResponsePacket(remapped);
    }

    private ClientboundRegistryDataPacket filterRegistryData(ClientboundRegistryDataPacket packet) {
        ResourceKey<?> registryKey = packet.registry();
        String registryId = registryKey.identifier().toString();

        if (registryId.equals(LegacyLinkConstants.SULFUR_CUBE_ARCHETYPE_REGISTRY)) {
            LegacyLinkMod.LOGGER.debug("[LegacyLink] Skipping entire 26.2-only registry: {}", registryId);
            TranslationStats.recordRegistryFiltered(registryId);
            if (LegacyPacketMapTrace.enabled()
                    && boundConnection != null
                    && LegacyTracker.isLegacy(boundConnection)) {
                List<String> allIds = new ArrayList<>();
                for (var e : packet.entries()) {
                    allIds.add(e.id().toString());
                }
                LegacyPacketMapTrace.logRegistryFiltered(
                        boundConnection,
                        registryId,
                        packet.entries().size(),
                        0,
                        allIds
                );
            }
            return new ClientboundRegistryDataPacket(packet.registry(), List.of());
        }

        var entries = packet.entries();
        var filtered = new ArrayList<>(entries);
        int beforeCount = filtered.size();
        boolean changed = false;
        boolean attributeRegistry = Registries.ATTRIBUTE.equals(registryKey);
        boolean entityTypeRegistry = Registries.ENTITY_TYPE.equals(registryKey);
        int legacyAttributeStrips = 0;
        List<String> removedEntryIds = new ArrayList<>();

        var iterator = filtered.iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            String entryId = entry.id().toString();

            boolean strip26_2OnlyAttribute =
                    attributeRegistry && LegacyLinkConstants.LEGACY_UNSUPPORTED_ATTRIBUTE_IDS.contains(entryId);
            /*
             * {@code minecraft:sulfur_cube} is 26.2-only; drop it from entity_type sync so 26.1 never decodes it.
             * That renumbers following vanilla types on the wire — {@link dev.ohno.legacylink.mixin.ClientboundAddEntityPacketMixin}
             * and {@link dev.ohno.legacylink.mapping.LegacyEntityTypeWireRemapper} must emit the same indices vanilla encoding
             * would use against the filtered registry (see {@link net.minecraft.network.codec.ByteBufCodecs#registry}).
             */
            boolean stripSulfurCubeEntityType =
                    entityTypeRegistry && LegacyLinkConstants.SULFUR_CUBE_ENTITY_ID.contentEquals(entryId);
            boolean stripSulfurOrModEntry = !entityTypeRegistry
                    && (LegacyLinkConstants.SULFUR_BLOCK_IDS.contains(entryId)
                    || LegacyLinkConstants.SULFUR_ITEM_IDS.contains(entryId)
                    || entryId.equals(LegacyLinkConstants.SULFUR_CAVES_BIOME_ID)
                    || entryId.equals(LegacyLinkConstants.SULFUR_CUBE_ENTITY_ID)
                    || entryId.contains("sulfur"));
            if (stripSulfurCubeEntityType
                    || stripSulfurOrModEntry
                    || strip26_2OnlyAttribute) {
                iterator.remove();
                changed = true;
                removedEntryIds.add(entryId);
                if (strip26_2OnlyAttribute) {
                    legacyAttributeStrips++;
                }
                TranslationStats.recordRegistryEntryFiltered(registryId, entryId);
                LegacyLinkMod.LOGGER.debug("[LegacyLink] Filtered registry entry {}/{}", registryId, entryId);
            }
        }

        if (changed
                && LegacyPacketMapTrace.enabled()
                && boundConnection != null
                && LegacyTracker.isLegacy(boundConnection)) {
            LegacyPacketMapTrace.logRegistryFiltered(
                    boundConnection,
                    registryId,
                    beforeCount,
                    filtered.size(),
                    removedEntryIds
            );
        }

        if (legacyAttributeStrips > 0) {
            LegacyLinkMod.LOGGER.info(
                    "[LegacyLink] Removed {} 26.2-only attribute(s) from registry sync for legacy client (keeps attribute network ids aligned with 26.1)",
                    legacyAttributeStrips
            );
        }

        if (changed) {
            return new ClientboundRegistryDataPacket(packet.registry(), filtered);
        }
        return packet;
    }

    public ClientboundSetEntityDataPacket remapEntityData(ClientboundSetEntityDataPacket packet) {
        int entityId = packet.id();
        List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> packedItems = packet.packedItems();
        String entityDataIdsBefore = EntityDataRewriteTrace.formatSortedIds(packedItems);
        if (remappedLegacyEntityIds.contains(entityId)) {
            if (LegacyPacketMapTrace.enabled()
                    && boundConnection != null
                    && LegacyTracker.isLegacy(boundConnection)) {
                Entity recipient = entityForLegacyRecipient(entityId);
                EntityType<?> clientType = resolveMetadataEntityType(entityId, recipient);
                LegacyPacketMapTrace.logEntityDataContext(
                        boundConnection,
                        entityId,
                        clientType,
                        entityTypeOf(recipient),
                        clientVisibleEntityTypeById.get(entityId),
                        true
                );
            }
            EntityDataRewriteTrace.logIfChanged(entityId, entityDataIdsBefore, EntityDataRewriteTrace.formatSortedIds(List.of()));
            return new ClientboundSetEntityDataPacket(entityId, List.of());
        }
        List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> items = packedItems;
        Entity recipient = entityForLegacyRecipient(entityId);
        EntityType<?> clientType = resolveMetadataEntityType(entityId, recipient);
        EntityType<?> prefetchVisibleType = clientVisibleEntityTypeById.get(entityId);
        EntityType<?> recipientEntityType = entityTypeOf(recipient);
        if (LegacyPacketMapTrace.enabled()
                && boundConnection != null
                && LegacyTracker.isLegacy(boundConnection)) {
            LegacyPacketMapTrace.logEntityDataContext(
                    boundConnection,
                    entityId,
                    clientType,
                    recipientEntityType,
                    prefetchVisibleType,
                    false
            );
        }
        if (Boolean.getBoolean("legacylink.tracePlayerEntityData") && isEntityType(clientType, "minecraft:player")) {
            int max = -1;
            StringBuilder sb = new StringBuilder();
            for (var v : items) {
                max = Math.max(max, v.id());
                if (!sb.isEmpty()) {
                    sb.append(',');
                }
                sb.append(v.id());
            }
            LegacyLinkMod.LOGGER.warn("[LegacyLink][EntityDataTrace] player eid={} maxId={} ids=[{}]", entityId, max, sb);
        }
        var entityMetadataRewritten = EntityMetadataRewriter2661.rewriteForLegacy(
                entityId,
                clientType,
                prefetchVisibleType,
                recipientEntityType,
                items
        );
        if (entityMetadataRewritten != null) {
            items = entityMetadataRewritten;
        }
        EntityDataRewriteTrace.logIfChanged(entityId, entityDataIdsBefore, EntityDataRewriteTrace.formatSortedIds(items));
        if (entityMetadataRewritten != null) {
            return new ClientboundSetEntityDataPacket(entityId, items);
        }
        return packet;
    }

    public ClientboundUpdateAttributesPacket remapAttributes(ClientboundUpdateAttributesPacket packet) {
        List<ClientboundUpdateAttributesPacket.AttributeSnapshot> attrs = new ArrayList<>(packet.getValues());
        if (attrs.isEmpty()) {
            return packet;
        }
        boolean changed = false;
        int before = attrs.size();
        attrs.removeIf(snapshot -> {
            Identifier attrId = BuiltInRegistries.ATTRIBUTE.getKey(snapshot.attribute().value());
            if (attrId == null) {
                return true;
            }
            if (LegacyAttributeWireTable.isReady()) {
                return !LegacyAttributeWireTable.isSyncedToLegacy(attrId);
            }
            String s = attrId.toString();
            return LegacyLinkConstants.LEGACY_UNSUPPORTED_ATTRIBUTE_IDS.contains(s) || s.contains("sulfur");
        });
        if (attrs.size() != before) {
            changed = true;
            LegacyLinkMod.LOGGER.debug("[LegacyLink] Filtered {} attributes not synced to legacy for entity {}",
                    before - attrs.size(), packet.getEntityId());
        }
        if (!changed) {
            return packet;
        }
        try {
            return UPDATE_ATTRIBUTES_REBUILD_CTOR.newInstance(packet.getEntityId(), attrs);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "[LegacyLink] Failed to rebuild ClientboundUpdateAttributesPacket for entity " + packet.getEntityId(), e);
        }
    }

    public ClientboundUpdateAdvancementsPacket remapAdvancements(ClientboundUpdateAdvancementsPacket packet) {
        return AdvancementRewriter.rewrite(packet);
    }

    public ClientboundContainerSetSlotPacket remapContainerSetSlot(ClientboundContainerSetSlotPacket packet) {
        ItemStack remapped = ItemRewriter.remapStack(packet.getItem());
        if (remapped == packet.getItem()) {
            return packet;
        }
        return new ClientboundContainerSetSlotPacket(packet.getContainerId(), packet.getStateId(), packet.getSlot(), remapped);
    }

    public ClientboundContainerSetContentPacket remapContainerSetContent(ClientboundContainerSetContentPacket packet) {
        List<ItemStack> remappedItems = ItemRewriter.remapStackList(packet.items());
        ItemStack remappedCarried = ItemRewriter.remapStack(packet.carriedItem());
        if (remappedItems.equals(packet.items()) && remappedCarried == packet.carriedItem()) {
            return packet;
        }
        return new ClientboundContainerSetContentPacket(packet.containerId(), packet.stateId(), remappedItems, remappedCarried);
    }

    public ClientboundSetCursorItemPacket remapSetCursorItem(ClientboundSetCursorItemPacket packet) {
        ItemStack remapped = ItemRewriter.remapStack(packet.contents());
        if (remapped == packet.contents()) {
            return packet;
        }
        return new ClientboundSetCursorItemPacket(remapped);
    }

    public ClientboundSetPlayerInventoryPacket remapSetPlayerInventory(ClientboundSetPlayerInventoryPacket packet) {
        ItemStack remapped = ItemRewriter.remapStack(packet.contents());
        if (remapped == packet.contents()) {
            return packet;
        }
        return new ClientboundSetPlayerInventoryPacket(packet.slot(), remapped);
    }

    @SuppressWarnings("unchecked")
    public ClientboundUpdateRecipesPacket remapUpdateRecipes(ClientboundUpdateRecipesPacket packet) {
        try {
            Map<ResourceKey<RecipePropertySet>, RecipePropertySet> itemSets = packet.itemSets();
            Map<ResourceKey<RecipePropertySet>, RecipePropertySet> remappedSets = new HashMap<>(itemSets.size());
            boolean changed = false;

            for (Map.Entry<ResourceKey<RecipePropertySet>, RecipePropertySet> entry : itemSets.entrySet()) {
                RecipePropertySet set = entry.getValue();
                Set<net.minecraft.core.Holder<Item>> items =
                        (Set<net.minecraft.core.Holder<Item>>) RECIPE_ITEMS_FIELD.get(set);
                Set<net.minecraft.core.Holder<Item>> remappedItems = new HashSet<>(items.size());
                boolean setChanged = false;
                for (net.minecraft.core.Holder<Item> holder : items) {
                    Item remapped = ItemRewriter.remapItemForLegacyRegistryEncoding(holder.value());
                    remappedItems.add(remapped.builtInRegistryHolder());
                    setChanged |= remapped != holder.value();
                }
                if (setChanged) {
                    changed = true;
                    RecipePropertySet rebuilt = RECIPE_PROP_SET_CTOR.newInstance(remappedItems);
                    remappedSets.put(entry.getKey(), rebuilt);
                } else {
                    remappedSets.put(entry.getKey(), set);
                }
            }
            SelectableRecipe.SingleInputSet<StonecutterRecipe> stonecutter = packet.stonecutterRecipes();
            SelectableRecipe.SingleInputSet<StonecutterRecipe> remappedStonecutter = stonecutter;
            if (!stonecutter.entries().isEmpty()) {
                List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> rewrittenEntries =
                        new ArrayList<>(stonecutter.entries().size());
                boolean stonecutterChanged = false;
                for (SelectableRecipe.SingleInputEntry<StonecutterRecipe> entry : stonecutter.entries()) {
                    Ingredient remappedInput = RecipeBookAddRewriter.remapIngredient(entry.input());
                    SelectableRecipe<StonecutterRecipe> selectable = entry.recipe();
                    SlotDisplay originalDisplay = selectable.optionDisplay();
                    SlotDisplay remappedDisplay = RecipeBookAddRewriter.remapSlotDisplay(originalDisplay);
                    SelectableRecipe<StonecutterRecipe> remappedSelectable = selectable;
                    if (remappedDisplay != originalDisplay) {
                        remappedSelectable = new SelectableRecipe<>(remappedDisplay, selectable.recipe());
                        stonecutterChanged = true;
                    }
                    if (remappedInput != entry.input()) {
                        stonecutterChanged = true;
                    }
                    rewrittenEntries.add(new SelectableRecipe.SingleInputEntry<>(remappedInput, remappedSelectable));
                }
                if (stonecutterChanged) {
                    remappedStonecutter = new SelectableRecipe.SingleInputSet<>(rewrittenEntries);
                    changed = true;
                }
            }
            ClientboundUpdateRecipesPacket rewrittenPacket = changed
                    ? new ClientboundUpdateRecipesPacket(remappedSets, remappedStonecutter)
                    : packet;
            if (containsUnsafeRecipeIds(rewrittenPacket)) {
                LegacyLinkMod.LOGGER.warn(
                        "[LegacyLink] update_recipes still contains item ids outside legacy membership set; sending empty recipe sync to prevent client decode crash"
                );
                return new ClientboundUpdateRecipesPacket(Map.of(), SelectableRecipe.SingleInputSet.empty());
            }
            if (changed) {
                LegacyLinkMod.LOGGER.debug("[LegacyLink] update_recipes payload contained legacy-incompatible items; strict guard applied");
            }
            return rewrittenPacket;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("[LegacyLink] remapUpdateRecipes reflection failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean containsUnsafeRecipeIds(ClientboundUpdateRecipesPacket packet) throws ReflectiveOperationException {
        for (RecipePropertySet set : packet.itemSets().values()) {
            Set<net.minecraft.core.Holder<Item>> items =
                    (Set<net.minecraft.core.Holder<Item>>) RECIPE_ITEMS_FIELD.get(set);
            for (net.minecraft.core.Holder<Item> holder : items) {
                if (!RegistryRemapper.isLegacyItemWireId(Item.getId(holder.value()))) {
                    return true;
                }
            }
        }
        for (SelectableRecipe.SingleInputEntry<StonecutterRecipe> entry : packet.stonecutterRecipes().entries()) {
            for (var holder : entry.input().items().toList()) {
                if (!RegistryRemapper.isLegacyItemWireId(Item.getId(holder.value()))) {
                    return true;
                }
            }
            if (containsUnsafeSlotDisplay(entry.recipe().optionDisplay())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsUnsafeSlotDisplay(SlotDisplay display) {
        return SlotDisplayUtils.anyMatch(display, slotDisplay -> {
            if (slotDisplay instanceof SlotDisplay.ItemSlotDisplay itemDisplay) {
                return !RegistryRemapper.isLegacyItemWireId(Item.getId(itemDisplay.item().value()));
            }
            if (slotDisplay instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay) {
                return !RegistryRemapper.isLegacyItemWireId(Item.getId(stackDisplay.stack().create().getItem()));
            }
            return false;
        });
    }

    public ClientboundRecipeBookAddPacket remapRecipeBookAdd(ClientboundRecipeBookAddPacket packet) {
        return RecipeBookAddRewriter.rewrite(packet);
    }

    @SuppressWarnings("unchecked")
    public ClientboundUpdateTagsPacket remapUpdateTags(ClientboundUpdateTagsPacket packet) {
        try {
            var tags = packet.getTags();
            var itemRegistryKey = Registries.ITEM;
            TagNetworkSerialization.NetworkPayload payload = tags.get(itemRegistryKey);
            if (payload == null) {
                return packet;
            }

            Map<Identifier, IntList> tagMap =
                    (Map<Identifier, IntList>) TAGS_NETWORK_PAYLOAD_TAGS_FIELD.get(payload);
            boolean anyChanged = false;
            for (Map.Entry<Identifier, IntList> entry : tagMap.entrySet()) {
                IntList ids = entry.getValue();
                for (int i = 0; i < ids.size(); i++) {
                    int oldId = ids.getInt(i);
                    if (ItemRewriter.remapItemIdStrict(oldId) != oldId) {
                        anyChanged = true;
                        break;
                    }
                }
                if (anyChanged) {
                    break;
                }
            }
            if (!anyChanged) {
                return packet;
            }
            Map<Identifier, IntList> newItemTagMap = new HashMap<>(tagMap.size());
            for (Map.Entry<Identifier, IntList> entry : tagMap.entrySet()) {
                IntList ids = entry.getValue();
                IntArrayList rewritten = new IntArrayList(ids.size());
                boolean changedForEntry = false;
                for (int i = 0; i < ids.size(); i++) {
                    int oldId = ids.getInt(i);
                    int mappedId = ItemRewriter.remapItemIdStrict(oldId);
                    rewritten.add(mappedId);
                    if (mappedId != oldId) {
                        changedForEntry = true;
                    }
                }
                newItemTagMap.put(entry.getKey(), changedForEntry ? rewritten : ids);
            }
            TagNetworkSerialization.NetworkPayload newPayload = NETWORK_PAYLOAD_CTOR.newInstance(newItemTagMap);
            var newTags = new HashMap<>(tags);
            newTags.put(itemRegistryKey, newPayload);
            LegacyLinkMod.LOGGER.debug("[LegacyLink] Remapped item tag payload IDs for legacy client (new packet)");
            return new ClientboundUpdateTagsPacket(newTags);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("[LegacyLink] remapUpdateTags reflection failed", e);
        }
    }

    public ClientboundLevelChunkWithLightPacket remapChunkPacket(ClientboundLevelChunkWithLightPacket packet) {
        return LegacyChunkTranslator.remapChunkPacket(packet);
    }

    public ClientboundBlockUpdatePacket remapBlockUpdate(ClientboundBlockUpdatePacket packet) {
        return BlockStatePacketRewriter.remapBlockUpdate(packet);
    }

    public ClientboundSectionBlocksUpdatePacket remapSectionBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet) {
        if (Boolean.getBoolean("legacylink.traceSectionBlocksUpdate")) {
            LegacyLinkMod.LOGGER.info("[LegacyLink][SectionBlocksTrace] phase=dispatch packet={}", packet.type().id());
        }
        return BlockStatePacketRewriter.remapSectionBlocksUpdate(packet);
    }

    /**
     * Registers {@link #clientVisibleEntityTypeById} (and sulfur remapped-entity tracking) before other packets
     * in the same {@link ClientboundBundlePacket} are translated — needed when {@link ClientboundSetEntityDataPacket}
     * is ordered before {@link ClientboundAddEntityPacket}.
     */
    private void prefetchAddEntityMetadataHints(ClientboundAddEntityPacket packet) {
        int id = packet.getId();
        EntityType<?> type = packet.getType();
        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (typeId != null && typeId.toString().equals(LegacyLinkConstants.SULFUR_CUBE_ENTITY_ID)) {
            clientVisibleEntityTypeById.put(id, LEGACY_SLIME_TYPE);
            remappedLegacyEntityIds.add(id);
            return;
        }
        clientVisibleEntityTypeById.put(id, type);
        remappedLegacyEntityIds.remove(id);
    }

    public ClientboundAddEntityPacket remapEntitySpawn(ClientboundAddEntityPacket packet) {
        prefetchAddEntityMetadataHints(packet);
        EntityType<?> type = packet.getType();
        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (typeId != null && typeId.toString().equals(LegacyLinkConstants.SULFUR_CUBE_ENTITY_ID)) {
            TranslationStats.recordEntityRemap();
            LegacyLinkMod.LOGGER.debug("[LegacyLink] Remapped sulfur_cube entity to slime at {},{},{}",
                    packet.getX(), packet.getY(), packet.getZ());
            // Sulfur cube is visually similar to slime; send slime instead
            return new ClientboundAddEntityPacket(
                    packet.getId(), packet.getUUID(), packet.getX(), packet.getY(), packet.getZ(),
                    packet.getXRot(), packet.getYRot(), LEGACY_SLIME_TYPE, 1,
                    packet.getMovement(), packet.getYHeadRot()
            );
        }
        return packet;
    }

    public ClientboundRemoveEntitiesPacket trackRemovedEntities(ClientboundRemoveEntitiesPacket packet) {
        for (int id : packet.getEntityIds()) {
            remappedLegacyEntityIds.remove(id);
            clientVisibleEntityTypeById.remove(id);
        }
        return packet;
    }

}
