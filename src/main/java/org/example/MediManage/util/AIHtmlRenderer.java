package org.example.MediManage.util;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

public final class AIHtmlRenderer {
    public enum Theme {
        CHAT("#1c2033", "#e7ecff", "#7dd3fc", "#93c5fd"),
        PANEL("#151c31", "#e7ecff", "#4ade80", "#60a5fa"),
        DIALOG("#0f1628", "#ecf2ff", "#8b5cf6", "#22d3ee");

        private final String background;
        private final String foreground;
        private final String accent;
        private final String softAccent;

        Theme(String background, String foreground, String accent, String softAccent) {
            this.background = background;
            this.foreground = foreground;
            this.accent = accent;
            this.softAccent = softAccent;
        }
    }

    private static final Parser MARKDOWN_PARSER;
    private static final HtmlRenderer HTML_RENDERER;

    static {
        MutableDataSet options = new MutableDataSet();
        MARKDOWN_PARSER = Parser.builder(options).build();
        HTML_RENDERER = HtmlRenderer.builder(options).build();
    }

    private AIHtmlRenderer() {
    }

    public static String toHtmlDocument(String text, Theme theme) {
        String markdown = preprocess(text);
        String body = HTML_RENDERER.render(MARKDOWN_PARSER.parse(markdown));

        return """
                <html>
                <head>
                  <meta charset="utf-8"/>
                  <style>
                    :root {
                      --bg: %s;
                      --fg: %s;
                      --accent: %s;
                      --soft: %s;
                      --muted: rgba(231, 236, 255, 0.68);
                      --border: rgba(125, 211, 252, 0.16);
                    }
                    html, body {
                      margin: 0;
                      padding: 0;
                      background: var(--bg);
                      color: var(--fg);
                      font-family: "Segoe UI", "Cascadia Code", sans-serif;
                      font-size: 13px;
                      line-height: 1.55;
                    }
                    body {
                      padding: 16px 18px 18px 18px;
                    }
                    h1, h2, h3, h4 {
                      margin: 0 0 10px 0;
                      color: var(--accent);
                      letter-spacing: 0.2px;
                    }
                    h1 { font-size: 20px; }
                    h2 { font-size: 17px; }
                    h3 { font-size: 15px; }
                    p {
                      margin: 0 0 10px 0;
                      color: var(--fg);
                    }
                    strong {
                      color: white;
                    }
                    em {
                      color: var(--soft);
                    }
                    ul, ol {
                      margin: 0 0 12px 0;
                      padding-left: 20px;
                    }
                    li {
                      margin: 0 0 6px 0;
                    }
                    hr {
                      border: none;
                      border-top: 1px solid var(--border);
                      margin: 14px 0;
                    }
                    blockquote {
                      margin: 12px 0;
                      padding: 10px 14px;
                      background: rgba(255, 255, 255, 0.04);
                      border-left: 3px solid var(--accent);
                      border-radius: 8px;
                      color: var(--muted);
                    }
                    code {
                      background: rgba(0, 0, 0, 0.22);
                      color: #fde68a;
                      padding: 2px 6px;
                      border-radius: 6px;
                    }
                    pre {
                      white-space: pre-wrap;
                      background: rgba(0, 0, 0, 0.24);
                      color: var(--fg);
                      padding: 12px;
                      border-radius: 10px;
                      border: 1px solid var(--border);
                      overflow-wrap: anywhere;
                    }
                    table {
                      width: 100%%;
                      border-collapse: collapse;
                      margin: 8px 0 14px 0;
                    }
                    th, td {
                      text-align: left;
                      padding: 8px 10px;
                      border-bottom: 1px solid var(--border);
                    }
                    th {
                      color: var(--accent);
                      font-size: 12px;
                    }
                    .muted {
                      color: var(--muted);
                    }
                  </style>
                </head>
                <body>%s</body>
                </html>
                """.formatted(theme.background, theme.foreground, theme.accent, theme.softAccent, body);
    }

    private static String preprocess(String rawText) {
        String normalized = rawText == null ? "" : rawText.replace("\r\n", "\n").trim();
        if (normalized.isBlank()) {
            return "_No content available._";
        }

        normalized = normalized
                .replace("\n• ", "\n- ")
                .replace("\n▸ ", "\n- ")
                .replace("\n◦ ", "\n- ");

        String[] lines = normalized.split("\n");
        StringBuilder builder = new StringBuilder();
        boolean headingInserted = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                builder.append("\n\n");
                continue;
            }

            if (!headingInserted && shouldPromoteToHeading(trimmed)) {
                builder.append("## ").append(trimmed).append('\n');
                headingInserted = true;
                continue;
            }

            if (looksLikeSectionHeading(trimmed)) {
                builder.append("\n### ").append(trimmed.replaceFirst(":$", "")).append('\n');
                continue;
            }

            if (looksLikeLabeledLine(trimmed)) {
                int colonIndex = trimmed.indexOf(':');
                String label = trimmed.substring(0, colonIndex).trim();
                String value = trimmed.substring(colonIndex + 1).trim();
                builder.append("**").append(label).append(":** ").append(value).append('\n');
                continue;
            }

            builder.append(trimmed).append('\n');
        }

        return builder.toString().trim();
    }

    private static boolean shouldPromoteToHeading(String line) {
        if (line.startsWith("#") || line.startsWith("-") || line.startsWith("*") || line.startsWith(">")) {
            return false;
        }
        if (line.length() > 72 || line.contains("|")) {
            return false;
        }
        return !looksLikeLabeledLine(line);
    }

    private static boolean looksLikeSectionHeading(String line) {
        return line.endsWith(":")
                && line.length() <= 48
                && !line.startsWith("-")
                && !line.startsWith("*")
                && !line.startsWith(">");
    }

    private static boolean looksLikeLabeledLine(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex <= 1 || colonIndex > 36 || colonIndex >= line.length() - 1) {
            return false;
        }
        String label = line.substring(0, colonIndex).trim();
        return label.matches("[\\p{L}\\p{N}][\\p{L}\\p{N} /&()'%-]{1,35}");
    }
}
