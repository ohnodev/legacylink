package dev.ohno.legacylink;

import java.util.Set;

public final class LegacyLinkConstants {

    // Supported bridge pair: 26.2-snapshot-3 server <-> 26.1.2 client.
    public static final int PROTOCOL_26_1_2 = 775;
    public static final int PROTOCOL_26_2_SNAPSHOT_3 = 1073742133;

    public static final Set<String> SULFUR_BLOCK_IDS = Set.of(
            "minecraft:sulfur",
            "minecraft:potent_sulfur",
            "minecraft:sulfur_slab",
            "minecraft:sulfur_stairs",
            "minecraft:sulfur_wall",
            "minecraft:polished_sulfur",
            "minecraft:polished_sulfur_slab",
            "minecraft:polished_sulfur_stairs",
            "minecraft:polished_sulfur_wall",
            "minecraft:sulfur_bricks",
            "minecraft:sulfur_brick_slab",
            "minecraft:sulfur_brick_stairs",
            "minecraft:sulfur_brick_wall",
            "minecraft:chiseled_sulfur",
            "minecraft:cinnabar",
            "minecraft:cinnabar_slab",
            "minecraft:cinnabar_stairs",
            "minecraft:cinnabar_wall",
            "minecraft:polished_cinnabar",
            "minecraft:polished_cinnabar_slab",
            "minecraft:polished_cinnabar_stairs",
            "minecraft:polished_cinnabar_wall",
            "minecraft:cinnabar_bricks",
            "minecraft:cinnabar_brick_slab",
            "minecraft:cinnabar_brick_stairs",
            "minecraft:cinnabar_brick_wall",
            "minecraft:chiseled_cinnabar",
            "minecraft:sulfur_spike"
    );

    public static final Set<String> SULFUR_ITEM_IDS = Set.of(
            "minecraft:sulfur",
            "minecraft:potent_sulfur",
            "minecraft:sulfur_slab",
            "minecraft:sulfur_stairs",
            "minecraft:sulfur_wall",
            "minecraft:polished_sulfur",
            "minecraft:polished_sulfur_slab",
            "minecraft:polished_sulfur_stairs",
            "minecraft:polished_sulfur_wall",
            "minecraft:sulfur_bricks",
            "minecraft:sulfur_brick_slab",
            "minecraft:sulfur_brick_stairs",
            "minecraft:sulfur_brick_wall",
            "minecraft:chiseled_sulfur",
            "minecraft:cinnabar",
            "minecraft:cinnabar_slab",
            "minecraft:cinnabar_stairs",
            "minecraft:cinnabar_wall",
            "minecraft:polished_cinnabar",
            "minecraft:polished_cinnabar_slab",
            "minecraft:polished_cinnabar_stairs",
            "minecraft:polished_cinnabar_wall",
            "minecraft:cinnabar_bricks",
            "minecraft:cinnabar_brick_slab",
            "minecraft:cinnabar_brick_stairs",
            "minecraft:cinnabar_brick_wall",
            "minecraft:chiseled_cinnabar",
            "minecraft:sulfur_spike",
            "minecraft:sulfur_cube_bucket",
            "minecraft:sulfur_cube_spawn_egg"
    );

    public static final String SULFUR_CUBE_ENTITY_ID = "minecraft:sulfur_cube";
    public static final String SULFUR_CAVES_BIOME_ID = "minecraft:sulfur_caves";
    public static final String SULFUR_CUBE_ARCHETYPE_REGISTRY = "minecraft:sulfur_cube_archetype";
    public static final String POTENT_SULFUR_BLOCK_ENTITY_ID = "minecraft:potent_sulfur";
    /**
     * 26.2-only {@link net.minecraft.world.entity.ai.attributes.Attribute} types. Dropped from
     * {@link net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket} and from the
     * {@code minecraft:attribute} registry sync so network registry indices match 26.1 — otherwise
     * {@code Holder} attribute varints decode to the wrong entry (e.g. {@code minecraft:camera_distance},
     * {@code minecraft:scale}) and the client camera / third-person offset looks wrong.
     */
    public static final Set<String> LEGACY_UNSUPPORTED_ATTRIBUTE_IDS = Set.of(
            "minecraft:bounciness",
            "minecraft:air_drag_modifier",
            "minecraft:friction_modifier"
    );

    /** Built-in items omitted from the 26.1 wire-id sequence (26.2-only); keep {@link #SULFUR_ITEM_IDS} authoritative. */
    public static boolean is26_2OnlyItemId(String registryId) {
        return SULFUR_ITEM_IDS.contains(registryId);
    }

    private LegacyLinkConstants() {}
}
