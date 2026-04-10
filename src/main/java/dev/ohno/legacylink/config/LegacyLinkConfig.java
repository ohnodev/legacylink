package dev.ohno.legacylink.config;

import dev.ohno.legacylink.LegacyLinkMod;

public final class LegacyLinkConfig {

    private static boolean verboseLogging = false;

    public static boolean verboseLogging() {
        return verboseLogging;
    }

    public static void load() {
        String prop = System.getProperty("legacylink.verbose", "false");
        verboseLogging = Boolean.parseBoolean(prop);
        LegacyLinkMod.LOGGER.info("[LegacyLink] Config loaded — verbose={}", verboseLogging);
    }

    private LegacyLinkConfig() {}
}
