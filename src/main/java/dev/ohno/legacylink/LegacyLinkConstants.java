package dev.ohno.legacylink;

import java.util.Set;

public final class LegacyLinkConstants {

    public static final int PROTOCOL_26_1 = 775;
    public static final int PROTOCOL_26_2_SNAPSHOT = 1073742132;
    // Upper bounds for 26.1.x protocol registries; anything above is treated as 26.2-only on the wire.
    // Block state ids must match the legacy client's {@code Block.BLOCK_STATE_REGISTRY} size. 26.1.2 crashes with
    // {@code No value with id 30224} when chunks still carry 26.2-only palette entries, so the last valid id is 30223.
    // Bump when Mojang extends 26.1.x without changing the 26.2 registry layout the same way.
    public static final int MAX_26_1_ITEM_ID = 1505;
    public static final int MAX_26_1_BLOCKSTATE_ID = 30223;

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
            "minecraft:chiseled_sulfur"
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

    private LegacyLinkConstants() {}
}
