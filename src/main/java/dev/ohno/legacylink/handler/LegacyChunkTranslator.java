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
import java.util.List;

public final class LegacyChunkTranslator {

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
            int sectionCount = LegacyRuntimeContext.sectionCount();

            List<LevelChunkSection> sections = new ArrayList<>(sectionCount);
            int remappedStates = 0;

            for (int i = 0; i < sectionCount; i++) {
                LevelChunkSection section = new LevelChunkSection(LegacyRuntimeContext.chunkContainerFactory());
                section.read(in);
                SectionRewriteResult rewritten = rewriteSectionStates(section);
                remappedStates += rewritten.remappedStates();
                sections.add(rewritten.section());
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

    private static SectionRewriteResult rewriteSectionStates(LevelChunkSection sourceSection) throws IllegalAccessException {
        LevelChunkSection rewritten = new LevelChunkSection(LegacyRuntimeContext.chunkContainerFactory());
        SECTION_BIOMES_FIELD.set(rewritten, sourceSection.getBiomes().copy());

        int remapped = 0;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    BlockState state = sourceSection.getBlockState(x, y, z);
                    int oldStateId = Block.BLOCK_STATE_REGISTRY.getId(state);
                    int newStateId = RegistryRemapper.remapBlockState(oldStateId);
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
