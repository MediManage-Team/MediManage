package org.example.MediManage.config;

import org.example.MediManage.model.User;
import org.example.MediManage.util.UserSession;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public final class FeatureFlags {
    private static final String FEATURE_PROPERTIES = "feature-flags.properties";
    private static final String PROPERTY_PREFIX = "medimanage.feature.";
    private static final String ENV_PREFIX = "MEDIMANAGE_FEATURE_";
    private static final String SUBSCRIPTION_PILOT_ENABLED_KEY = "subscription.pilot.enabled";
    private static final String SUBSCRIPTION_PILOT_ALLOWED_USERNAMES_KEY = "subscription.pilot.allowed.usernames";
    private static final String SUBSCRIPTION_PILOT_ALLOWED_USER_IDS_KEY = "subscription.pilot.allowed.user_ids";

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

    public static boolean isEnabledForCurrentUser(FeatureFlag flag) {
        if (!isEnabled(flag)) {
            return false;
        }
        if (!isSubscriptionRuntimeFlag(flag)) {
            return true;
        }
        if (!isEnabled(FeatureFlag.SUBSCRIPTION_RELEASE)) {
            return false;
        }
        if (!isSubscriptionPilotEnabled()) {
            return true;
        }
        User currentUser = UserSession.getInstance().getUser();
        if (currentUser == null) {
            return false;
        }

        Set<String> allowedUsernames = readCsvValues(SUBSCRIPTION_PILOT_ALLOWED_USERNAMES_KEY).stream()
                .map(v -> v.toLowerCase())
                .collect(Collectors.toSet());
        Set<String> allowedUserIds = readCsvValues(SUBSCRIPTION_PILOT_ALLOWED_USER_IDS_KEY);

        String username = currentUser.getUsername() == null ? "" : currentUser.getUsername().trim().toLowerCase();
        String userId = String.valueOf(currentUser.getId());
        return allowedUsernames.contains(username) || allowedUserIds.contains(userId);
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

    static String propertyName(String key) {
        return PROPERTY_PREFIX + key;
    }

    static String envName(FeatureFlag flag) {
        return ENV_PREFIX + flag.key().toUpperCase().replace('.', '_').replace('-', '_');
    }

    static String envName(String key) {
        return ENV_PREFIX + key.toUpperCase().replace('.', '_').replace('-', '_');
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

    private static boolean isSubscriptionPilotEnabled() {
        Boolean parsedProperty = parseBoolean(System.getProperty(propertyName(SUBSCRIPTION_PILOT_ENABLED_KEY)));
        if (parsedProperty != null) {
            return parsedProperty;
        }
        Boolean parsedEnv = parseBoolean(System.getenv(envName(SUBSCRIPTION_PILOT_ENABLED_KEY)));
        if (parsedEnv != null) {
            return parsedEnv;
        }
        Boolean parsedDefault = parseBoolean(DEFAULTS.getProperty(SUBSCRIPTION_PILOT_ENABLED_KEY));
        return parsedDefault != null && parsedDefault;
    }

    private static Set<String> readCsvValues(String key) {
        String raw = firstNonBlank(
                System.getProperty(propertyName(key)),
                System.getenv(envName(key)),
                DEFAULTS.getProperty(key));
        if (raw == null || raw.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static String firstNonBlank(String first, String second, String third) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        if (third != null && !third.isBlank()) {
            return third;
        }
        return null;
    }

    private static boolean isSubscriptionRuntimeFlag(FeatureFlag flag) {
        return flag == FeatureFlag.SUBSCRIPTION_COMMERCE
                || flag == FeatureFlag.SUBSCRIPTION_APPROVALS
                || flag == FeatureFlag.SUBSCRIPTION_DISCOUNT_OVERRIDES;
    }
}
