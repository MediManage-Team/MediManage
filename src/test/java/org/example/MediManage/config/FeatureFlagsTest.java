package org.example.MediManage.config;

import org.junit.jupiter.api.Test;



import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureFlagsTest {

    @Test
    void defaultFlagsMatchBaseline() {
        assertTrue(FeatureFlags.isEnabled(FeatureFlag.AI_ASSISTANT));
    }

    @Test
    void systemPropertyOverridesDefaults() {
        FeatureFlag flag = FeatureFlag.AI_ASSISTANT;
        withSystemProperty(FeatureFlags.propertyName(flag), "false", () -> {
            assertFalse(FeatureFlags.isEnabled(flag));
        });
    }

    @Test
    void invalidSystemPropertyFallsBackToDefaults() {
        FeatureFlag flag = FeatureFlag.AI_ASSISTANT;
        withSystemProperty(FeatureFlags.propertyName(flag), "maybe", () -> {
            // "maybe" is not a valid boolean, should fall back to default (true for
            // AI_ASSISTANT)
            assertTrue(FeatureFlags.isEnabled(flag));
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
