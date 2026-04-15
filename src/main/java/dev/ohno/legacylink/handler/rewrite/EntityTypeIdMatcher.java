package dev.ohno.legacylink.handler.rewrite;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

final class EntityTypeIdMatcher {

    private EntityTypeIdMatcher() {}

    static boolean isEntityType(@Nullable EntityType<?> type, String id) {
        if (type == null) {
            return false;
        }
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key != null && id.equals(key.toString());
    }
}
