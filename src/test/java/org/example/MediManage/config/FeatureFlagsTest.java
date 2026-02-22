package org.example.MediManage.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureFlagsTest {

    @Test
    void defaultFlagsMatchPhaseZeroBaseline() {
        assertTrue(FeatureFlags.isEnabled(FeatureFlag.AI_ASSISTANT));
        assertTrue(FeatureFlags.isEnabled(FeatureFlag.POSTGRES_MIGRATION));
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
}
