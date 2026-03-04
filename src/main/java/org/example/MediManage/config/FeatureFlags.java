package org.example.MediManage.config;

import java.io.InputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/**
 * Simple feature-flag reader. Resolution order: System property → env variable
 * → properties file → enum default.
 */
public final class FeatureFlags {
    private static final String FEATURE_PROPERTIES = "feature-flags.properties";
    private static final String PROPERTY_PREFIX = "medimanage.feature.";
    private static final String ENV_PREFIX = "MEDIMANAGE_FEATURE_";

    private static final Properties FILE_DEFAULTS = loadDefaults();

    private FeatureFlags() {
    }

    public static boolean isEnabled(FeatureFlag flag) {
        if (flag == null)
            return false;

        Boolean fromProperty = parseBoolean(System.getProperty(PROPERTY_PREFIX + flag.key()));
        if (fromProperty != null)
            return fromProperty;

        Boolean fromEnv = parseBoolean(System.getenv(envName(flag)));
        if (fromEnv != null)
            return fromEnv;

        Boolean fromFile = parseBoolean(FILE_DEFAULTS.getProperty(flag.key()));
        if (fromFile != null)
            return fromFile;

        return flag.defaultEnabled();
    }

    public static Map<FeatureFlag, Boolean> snapshot() {
        EnumMap<FeatureFlag, Boolean> state = new EnumMap<>(FeatureFlag.class);
        for (FeatureFlag flag : FeatureFlag.values()) {
            state.put(flag, isEnabled(flag));
        }
        return Collections.unmodifiableMap(state);
    }

    // Visible for testing
    static String propertyName(FeatureFlag flag) {
        return PROPERTY_PREFIX + flag.key();
    }

    static Boolean parseBoolean(String raw) {
        if (raw == null || raw.isBlank())
            return null;
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "1", "true", "yes", "on", "enabled" -> Boolean.TRUE;
            case "0", "false", "no", "off", "disabled" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static String envName(FeatureFlag flag) {
        return ENV_PREFIX + flag.key().toUpperCase().replace('.', '_').replace('-', '_');
    }

    private static Properties loadDefaults() {
        Properties defaults = new Properties();
        try (InputStream input = FeatureFlags.class.getClassLoader().getResourceAsStream(FEATURE_PROPERTIES)) {
            if (input != null) {
                defaults.load(input);
            }
        } catch (Exception e) {
            System.err.println("Failed to load feature flags defaults: " + e.getMessage());
        }
        return defaults;
    }
}
