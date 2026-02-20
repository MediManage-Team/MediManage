package org.example.MediManage.util;

import java.util.UUID;

/**
 * Lightweight per-thread logging context.
 * Provides a correlation id that can be emitted by log formatters.
 */
public final class LogContext {
    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();

    private LogContext() {
    }

    /**
     * Set a new correlation id for the current thread if one does not exist.
     */
    public static String ensureCorrelationId() {
        String existing = CORRELATION_ID.get();
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        String created = UUID.randomUUID().toString();
        CORRELATION_ID.set(created);
        return created;
    }

    /**
     * Returns the current thread correlation id, or "-" when absent.
     */
    public static String getCorrelationId() {
        String id = CORRELATION_ID.get();
        return (id == null || id.isBlank()) ? "-" : id;
    }

    /**
     * Explicitly set correlation id for the current thread.
     */
    public static void setCorrelationId(String correlationId) {
        CORRELATION_ID.set(correlationId);
    }

    /**
     * Clear the current thread context.
     */
    public static void clear() {
        CORRELATION_ID.remove();
    }
}

