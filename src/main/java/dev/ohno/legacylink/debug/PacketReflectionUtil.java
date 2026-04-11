package dev.ohno.legacylink.debug;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared cached reflection for packet int fields (debug capture / tracing).
 */
public final class PacketReflectionUtil {

    private PacketReflectionUtil() {}

    /**
     * Reads an int field from {@code packet}, caching the {@link Field} in {@code cachedField}.
     *
     * @return the field value, or {@code -1} on failure
     */
    public static int getIntField(
            Object packet,
            Class<?> packetClass,
            String fieldName,
            AtomicReference<Field> cachedField
    ) {
        try {
            Field f = cachedField.get();
            if (f == null) {
                synchronized (cachedField) {
                    f = cachedField.get();
                    if (f == null) {
                        f = packetClass.getDeclaredField(fieldName);
                        f.setAccessible(true);
                        cachedField.set(f);
                    }
                }
            }
            return f.getInt(packet);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }
}
