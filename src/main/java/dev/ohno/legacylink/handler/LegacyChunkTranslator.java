package dev.ohno.legacylink.handler;

import dev.ohno.legacylink.LegacyLinkConstants;
import dev.ohno.legacylink.LegacyLinkMod;
import dev.ohno.legacylink.mapping.RegistryRemapper;
import dev.ohno.legacylink.runtime.LegacyRuntimeContext;
import dev.ohno.legacylink.telemetry.TranslationStats;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.lang.ScopedValue;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LegacyChunkTranslator {

    /** Hard cap so a corrupt buffer cannot loop forever (vanilla is on the order of tens of sections). */
    private static final int MAX_CHUNK_SECTIONS = 1024;
    private static final boolean TRACE_CHUNK_STATES = Boolean.getBoolean("legacylink.traceChunkStates");
    private static final int TRACE_CHUNK_STATE_THRESHOLD = Integer.getInteger("legacylink.traceChunkStateThreshold", 30180);
    private static final Set<String> TRACE_SEEN_STATE_MAPPINGS = ConcurrentHashMap.newKeySet();

    private static final Field CHUNK_BUFFER_FIELD;
    private static final Field CHUNK_BLOCK_ENTITIES_FIELD;
    private static final Field SECTION_BIOMES_FIELD;
    private static final Object ANTIXRAY_PACKET_INFO_KEY;
    private static final Object ANTIXRAY_CHUNK_SECTION_INDEX_KEY;

    static {
        try {
            CHUNK_BUFFER_FIELD = ClientboundLevelChunkPacketData.class.getDeclaredField("buffer");
            CHUNK_BUFFER_FIELD.setAccessible(true);

            CHUNK_BLOCK_ENTITIES_FIELD = ClientboundLevelChunkPacketData.class.getDeclaredField("blockEntitiesData");
            CHUNK_BLOCK_ENTITIES_FIELD.setAccessible(true);

            SECTION_BIOMES_FIELD = LevelChunkSection.class.getDeclaredField("biomes");
            SECTION_BIOMES_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }

        Object packetInfoKey = null;
        Object sectionIndexKey = null;
        try {
            Class<?> argumentsClass = Class.forName("me.drex.antixray.common.util.Arguments");
            Field packetInfoField = argumentsClass.getDeclaredField("PACKET_INFO");
            Field sectionIndexField = argumentsClass.getDeclaredField("CHUNK_SECTION_INDEX");
            packetInfoField.setAccessible(true);
            sectionIndexField.setAccessible(true);
            packetInfoKey = packetInfoField.get(null);
            sectionIndexKey = sectionIndexField.get(null);
        } catch (ClassNotFoundException e) {
            // AntiXray mod not present
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
        ANTIXRAY_PACKET_INFO_KEY = packetInfoKey;
        ANTIXRAY_CHUNK_SECTION_INDEX_KEY = sectionIndexKey;
    }

    public static ClientboundLevelChunkWithLightPacket remapChunkPacket(ClientboundLevelChunkWithLightPacket packet) {
        if (!LegacyRuntimeContext.isReady()) {
            throw new IllegalStateException("[LegacyLink] LegacyRuntimeContext not ready; refusing passthrough chunk packet");
        }
        try {
            ClientboundLevelChunkPacketData chunkData = packet.getChunkData();
            FriendlyByteBuf in = chunkData.getReadBuffer();

            /*
             * Section count is per-dimension (e.g. overworld 24 vs nether 8). Using overworld's count for every
             * chunk mis-aligns the buffer: the client then reads garbage palette ids (e.g. "No value with id 30209").
             */
            List<LevelChunkSection> sections = new ArrayList<>();
            int remappedStates = 0;
            Map<String, Integer> tracePairCounts = TRACE_CHUNK_STATES ? new HashMap<>() : null;

            int sectionIndex = 0;
            while (in.readableBytes() > 0) {
                if (sectionIndex >= MAX_CHUNK_SECTIONS) {
                    throw new IllegalStateException(
                            "[LegacyLink] Chunk remap: more than " + MAX_CHUNK_SECTIONS
                                    + " sections in buffer for " + packet.getX() + "," + packet.getZ()
                                    + " (readable=" + in.readableBytes() + ") — corrupt or unknown format?");
                }
                LevelChunkSection section = new LevelChunkSection(LegacyRuntimeContext.chunkContainerFactory());
                section.read(in);
                SectionRewriteResult rewritten = rewriteSectionStates(
                        section,
                        packet.getX(),
                        packet.getZ(),
                        sectionIndex,
                        tracePairCounts
                );
                remappedStates += rewritten.remappedStates();
                sections.add(rewritten.section());
                sectionIndex++;
            }

            FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
            for (int i = 0; i < sections.size(); i++) {
                writeSectionWithContext(sections.get(i), out, i);
            }

            byte[] translatedBuffer = new byte[out.writerIndex()];
            out.getBytes(0, translatedBuffer);
            CHUNK_BUFFER_FIELD.set(chunkData, translatedBuffer);

            int filteredBlockEntities = filterChunkBlockEntities(chunkData);

            if (remappedStates > 0 || filteredBlockEntities > 0) {
                TranslationStats.recordChunkRemap(remappedStates, filteredBlockEntities);
                LegacyLinkMod.LOGGER.debug(
                        "[LegacyLink] Remapped chunk {},{} states={} filtered_block_entities={}",
                        packet.getX(), packet.getZ(), remappedStates, filteredBlockEntities
                );
            }
            if (TRACE_CHUNK_STATES && tracePairCounts != null && !tracePairCounts.isEmpty()) {
                String mappingSummary = tracePairCounts.entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                        .limit(32)
                        .map(e -> e.getKey() + " x" + e.getValue())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                LegacyLinkMod.LOGGER.info(
                        "[LegacyLink][ChunkStateTrace] chunk={},{} sections={} pairs={}",
                        packet.getX(), packet.getZ(), sections.size(), mappingSummary
                );
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "[LegacyLink] Chunk remap failed for " + packet.getX() + "," + packet.getZ(), e);
        }
        return packet;
    }

    @SuppressWarnings("unchecked")
    private static void writeSectionWithContext(LevelChunkSection section, FriendlyByteBuf out, int sectionIndex) {
        if (ANTIXRAY_PACKET_INFO_KEY instanceof ScopedValue<?> packetInfoKey
                && ANTIXRAY_CHUNK_SECTION_INDEX_KEY instanceof ScopedValue<?> sectionIndexKey) {
            ScopedValue.where((ScopedValue<Object>) packetInfoKey, null)
                    .where((ScopedValue<Object>) sectionIndexKey, sectionIndex)
                    .run(() -> section.write(out));
            return;
        }
        section.write(out);
    }

    private static SectionRewriteResult rewriteSectionStates(
            LevelChunkSection sourceSection,
            int chunkX,
            int chunkZ,
            int sectionIndex,
            Map<String, Integer> tracePairCounts
    ) throws IllegalAccessException {
        LevelChunkSection rewritten = new LevelChunkSection(LegacyRuntimeContext.chunkContainerFactory());
        SECTION_BIOMES_FIELD.set(rewritten, sourceSection.getBiomes().copy());

        int remapped = 0;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    BlockState state = sourceSection.getBlockState(x, y, z);
                    int oldStateId = Block.BLOCK_STATE_REGISTRY.getId(state);
                    int newStateId = RegistryRemapper.remapBlockState(oldStateId);
                    if (tracePairCounts != null && (oldStateId != newStateId || oldStateId >= TRACE_CHUNK_STATE_THRESHOLD)) {
                        String key = oldStateId + "->" + newStateId;
                        tracePairCounts.merge(key, 1, Integer::sum);
                        if (TRACE_SEEN_STATE_MAPPINGS.add(key)) {
                            Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                            LegacyLinkMod.LOGGER.info(
                                    "[LegacyLink][ChunkStateTrace] first_pair={} block={} state={} chunk={},{} section={} pos={},{},{}",
                                    key,
                                    blockId,
                                    state,
                                    chunkX,
                                    chunkZ,
                                    sectionIndex,
                                    x,
                                    y,
                                    z
                            );
                        }
                    }
                    BlockState toWrite = state;
                    if (newStateId != oldStateId) {
                        BlockState resolved = Block.BLOCK_STATE_REGISTRY.byId(newStateId);
                        if (resolved == null) {
                            throw new IllegalStateException(
                                    "[LegacyLink] Chunk remap: no BlockState for id " + newStateId + " (from " + oldStateId + ")");
                        }
                        toWrite = resolved;
                        remapped++;
                    }
                    rewritten.setBlockState(x, y, z, toWrite, false);
                }
            }
        }
        return new SectionRewriteResult(rewritten, remapped);
    }

    @SuppressWarnings("unchecked")
    private static int filterChunkBlockEntities(ClientboundLevelChunkPacketData chunkData) throws IllegalAccessException {
        List<Object> blockEntities = (List<Object>) CHUNK_BLOCK_ENTITIES_FIELD.get(chunkData);
        if (blockEntities == null || blockEntities.isEmpty()) {
            return 0;
        }

        int before = blockEntities.size();
        blockEntities.removeIf(entry -> {
            try {
                Field typeField = entry.getClass().getDeclaredField("type");
                typeField.setAccessible(true);
                BlockEntityType<?> type = (BlockEntityType<?>) typeField.get(entry);
                Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
                return id != null && LegacyLinkConstants.POTENT_SULFUR_BLOCK_ENTITY_ID.equals(id.toString());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("[LegacyLink] Chunk block entity filter: cannot read type field", e);
            }
        });
        return before - blockEntities.size();
    }

    private record SectionRewriteResult(LevelChunkSection section, int remappedStates) {}

    private LegacyChunkTranslator() {}
}
