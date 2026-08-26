package com.foggyframework.analytics.function.fap;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Command-line exporter for the exact Analytics question Function publication catalog.
 *
 * <p>The exporter is deliberately credential-free and read-only. Release tooling and
 * FAP hosts can consume its JSON output without duplicating the Java catalog.</p>
 */
public final class FapAnalyticsQuestionPublicationExporter {

    private FapAnalyticsQuestionPublicationExporter() {
    }

    public static void main(String[] args) {
        Map<String, Object> envelope = Map.of(
                "contractVersion",
                "foggy.analytics.question-function-publication.v1",
                "functions",
                FapAnalyticsQuestionFunctionCatalog.publicationValues());
        System.out.println(json(envelope));
    }

    private static String json(Object value) {
        StringBuilder output = new StringBuilder();
        appendJson(output, value);
        return output.toString();
    }

    private static void appendJson(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String text) {
            appendString(output, text);
        } else if (value instanceof Boolean || value instanceof Number) {
            output.append(value);
        } else if (value instanceof Map<?, ?> map) {
            output.append('{');
            boolean first = true;
            List<? extends Map.Entry<?, ?>> entries = map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .toList();
            for (Map.Entry<?, ?> entry : entries) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                appendString(output, String.valueOf(entry.getKey()));
                output.append(':');
                appendJson(output, entry.getValue());
            }
            output.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            output.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                appendJson(output, item);
            }
            output.append(']');
        } else {
            throw new IllegalArgumentException(
                    "Unsupported publication JSON value: " + value.getClass().getName());
        }
    }

    private static void appendString(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append(String.format("\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }
}
