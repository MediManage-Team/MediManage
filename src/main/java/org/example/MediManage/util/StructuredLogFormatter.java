package org.example.MediManage.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * JSON-line formatter for java.util.logging.
 */
public class StructuredLogFormatter extends Formatter {
    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public String format(LogRecord record) {
        String timestamp = Instant.ofEpochMilli(record.getMillis())
                .atOffset(ZoneOffset.UTC)
                .format(TS_FORMATTER);

        StringBuilder sb = new StringBuilder(256);
        sb.append('{')
                .append("\"timestamp\":\"").append(escape(timestamp)).append("\",")
                .append("\"level\":\"").append(escape(record.getLevel().getName())).append("\",")
                .append("\"logger\":\"").append(escape(nullSafe(record.getLoggerName()))).append("\",")
                .append("\"thread\":\"").append(escape(Thread.currentThread().getName())).append("\",")
                .append("\"correlation_id\":\"").append(escape(LogContext.getCorrelationId())).append("\",")
                .append("\"message\":\"").append(escape(formatMessage(record))).append('"');

        Throwable thrown = record.getThrown();
        if (thrown != null) {
            sb.append(",\"exception\":\"").append(escape(stackTrace(thrown))).append('"');
        }

        sb.append('}').append(System.lineSeparator());
        return sb.toString();
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}

