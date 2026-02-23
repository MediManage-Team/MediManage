package org.example.MediManage.config;

import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.util.UserSession;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureFlagsTest {

    @Test
    void defaultFlagsMatchPhaseZeroBaseline() {
        assertTrue(FeatureFlags.isEnabled(FeatureFlag.AI_ASSISTANT));
        assertTrue(FeatureFlags.isEnabled(FeatureFlag.POSTGRES_MIGRATION));
        assertFalse(FeatureFlags.isEnabled(FeatureFlag.SUBSCRIPTION_RELEASE));
        assertFalse(FeatureFlags.isEnabled(FeatureFlag.SUBSCRIPTION_COMMERCE));
        assertFalse(FeatureFlags.isEnabled(FeatureFlag.SUBSCRIPTION_APPROVALS));
        assertFalse(FeatureFlags.isEnabled(FeatureFlag.SUBSCRIPTION_DISCOUNT_OVERRIDES));
    }

    @Test
    void systemPropertyOverridesDefaults() {
        FeatureFlag flag = FeatureFlag.SUBSCRIPTION_COMMERCE;
        withSystemProperty(FeatureFlags.propertyName(flag), "true", () -> {
            assertTrue(FeatureFlags.isEnabled(flag));
        });
    }

    @Test
    void invalidSystemPropertyFallsBackToDefaults() {
        FeatureFlag flag = FeatureFlag.SUBSCRIPTION_COMMERCE;
        withSystemProperty(FeatureFlags.propertyName(flag), "maybe", () -> {
            assertFalse(FeatureFlags.isEnabled(flag));
        });
    }

    @Test
    void parserAcceptsCommonBooleanVariants() {
        assertEquals(Boolean.TRUE, FeatureFlags.parseBoolean("enabled"));
        assertEquals(Boolean.TRUE, FeatureFlags.parseBoolean("ON"));
        assertEquals(Boolean.FALSE, FeatureFlags.parseBoolean("disabled"));
        assertEquals(Boolean.FALSE, FeatureFlags.parseBoolean("0"));
    }

    @Test
    void subscriptionFlagRemainsEnabledForCurrentUserWhenPilotDisabled() {
        withSystemProperties(Map.of(
                FeatureFlags.propertyName(FeatureFlag.SUBSCRIPTION_RELEASE), "true",
                FeatureFlags.propertyName(FeatureFlag.SUBSCRIPTION_COMMERCE), "true",
                FeatureFlags.propertyName("subscription.pilot.enabled"), "false"), () -> {
                    withLoggedInUser(null, () -> {
                        assertTrue(FeatureFlags.isEnabledForCurrentUser(FeatureFlag.SUBSCRIPTION_COMMERCE));
                    });
                });
    }

    @Test
    void subscriptionPilotAllowlistSupportsUsernamesAndUserIds() {
        withSystemProperties(Map.of(
                FeatureFlags.propertyName(FeatureFlag.SUBSCRIPTION_RELEASE), "true",
                FeatureFlags.propertyName(FeatureFlag.SUBSCRIPTION_COMMERCE), "true",
                FeatureFlags.propertyName("subscription.pilot.enabled"), "true",
                FeatureFlags.propertyName("subscription.pilot.allowed.usernames"), "pilot_user",
                FeatureFlags.propertyName("subscription.pilot.allowed.user_ids"), "77"), () -> {
                    withLoggedInUser(null, () -> {
                        assertFalse(FeatureFlags.isEnabledForCurrentUser(FeatureFlag.SUBSCRIPTION_COMMERCE));
                    });
                    withLoggedInUser(new User(13, "other_user", "x", UserRole.CASHIER), () -> {
                        assertFalse(FeatureFlags.isEnabledForCurrentUser(FeatureFlag.SUBSCRIPTION_COMMERCE));
                    });
                    withLoggedInUser(new User(13, "pilot_user", "x", UserRole.CASHIER), () -> {
                        assertTrue(FeatureFlags.isEnabledForCurrentUser(FeatureFlag.SUBSCRIPTION_COMMERCE));
                    });
                    withLoggedInUser(new User(77, "other_user", "x", UserRole.CASHIER), () -> {
                        assertTrue(FeatureFlags.isEnabledForCurrentUser(FeatureFlag.SUBSCRIPTION_COMMERCE));
                    });
                });
    }

    @Test
    void subscriptionRuntimeFlagBlockedWhenReleaseGateDisabled() {
        withSystemProperties(Map.of(
                FeatureFlags.propertyName(FeatureFlag.SUBSCRIPTION_RELEASE), "false",
                FeatureFlags.propertyName(FeatureFlag.SUBSCRIPTION_COMMERCE), "true",
                FeatureFlags.propertyName("subscription.pilot.enabled"), "false"), () -> {
                    withLoggedInUser(new User(13, "pilot_user", "x", UserRole.CASHIER), () -> {
                        assertFalse(FeatureFlags.isEnabledForCurrentUser(FeatureFlag.SUBSCRIPTION_COMMERCE));
                    });
                });
    }

    private static void withSystemProperty(String key, String value, Runnable assertion) {
        String previous = System.getProperty(key);
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
        try {
            assertion.run();
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    private static void withSystemProperties(Map<String, String> values, Runnable assertion) {
        Map<String, String> previous = new HashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            previous.put(key, System.getProperty(key));
            String value = entry.getValue();
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        }
        try {
            assertion.run();
        } finally {
            for (Map.Entry<String, String> entry : previous.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value == null) {
                    System.clearProperty(key);
                } else {
                    System.setProperty(key, value);
                }
            }
        }
    }

    private static void withLoggedInUser(User user, Runnable assertion) {
        UserSession session = UserSession.getInstance();
        User previous = session.getUser();
        if (user == null) {
            session.logout();
        } else {
            session.login(user);
        }
        try {
            assertion.run();
        } finally {
            if (previous == null) {
                session.logout();
            } else {
                session.login(previous);
            }
        }
    }
}
