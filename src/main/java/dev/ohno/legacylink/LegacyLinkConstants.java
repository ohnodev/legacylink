package dev.ohno.legacylink;

import java.util.Set;

public final class LegacyLinkConstants {

    public static final int PROTOCOL_26_1 = 775;
    public static final int PROTOCOL_26_2_SNAPSHOT = 1073742132;

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

    private LegacyLinkConstants() {}
}
