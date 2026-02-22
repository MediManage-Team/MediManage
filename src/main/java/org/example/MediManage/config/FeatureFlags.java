package org.example.MediManage.config;

import java.io.InputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

public final class FeatureFlags {
    private static final String FEATURE_PROPERTIES = "feature-flags.properties";
    private static final String PROPERTY_PREFIX = "medimanage.feature.";
    private static final String ENV_PREFIX = "MEDIMANAGE_FEATURE_";

    private static final Properties DEFAULTS = loadDefaults();

    private FeatureFlags() {
    }

    public static boolean isEnabled(FeatureFlag flag) {
        if (flag == null) {
            return false;
        }

        Boolean parsedProperty = parseBoolean(System.getProperty(propertyName(flag)));
        if (parsedProperty != null) {
            return parsedProperty;
        }

        Boolean parsedEnv = parseBoolean(System.getenv(envName(flag)));
        if (parsedEnv != null) {
            return parsedEnv;
        }

        Boolean parsedDefault = parseBoolean(DEFAULTS.getProperty(flag.key()));
        if (parsedDefault != null) {
            return parsedDefault;
        }

        return flag.defaultEnabled();
    }

    public static Map<FeatureFlag, Boolean> snapshot() {
        EnumMap<FeatureFlag, Boolean> state = new EnumMap<>(FeatureFlag.class);
        for (FeatureFlag flag : FeatureFlag.values()) {
            state.put(flag, isEnabled(flag));
        }
        return Collections.unmodifiableMap(state);
    }

    static String propertyName(FeatureFlag flag) {
        return PROPERTY_PREFIX + flag.key();
    }

    static String envName(FeatureFlag flag) {
        return ENV_PREFIX + flag.key().toUpperCase().replace('.', '_').replace('-', '_');
    }

    static Boolean parseBoolean(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "1", "true", "yes", "on", "enabled" -> Boolean.TRUE;
            case "0", "false", "no", "off", "disabled" -> Boolean.FALSE;
            default -> null;
        };
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
