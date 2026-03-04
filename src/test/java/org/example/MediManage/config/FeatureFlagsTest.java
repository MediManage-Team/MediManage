package org.example.MediManage.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureFlagsTest {

    @Test
    void defaultFlagsMatchBaseline() {
        assertTrue(FeatureFlags.isEnabled(FeatureFlag.AI_ASSISTANT));
        assertTrue(FeatureFlags.isEnabled(FeatureFlag.POSTGRES_MIGRATION));
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
}
