package com.foggyframework.dataset.model.engine.pivot.algo;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Typed, length-delimited tuple identity. Presentation strings are never coordinate identity. */
public final class PivotCoordinates {
    private PivotCoordinates() {}

    public static List<Object> tuple(Map<String, Object> row, List<String> fields) {
        List<Object> values = new ArrayList<>();
        for (String field : fields) {
            Object value = row.get(field);
            values.add(
                    value instanceof Number
                            ? new BigDecimal(value.toString()).stripTrailingZeros()
                            : value);
        }
        return Collections.unmodifiableList(values);
    }

    public static String key(Map<String, Object> row, List<String> fields) {
        StringBuilder key = new StringBuilder();
        for (Object value : tuple(row, fields)) {
            if (value == null) key.append('N');
            else {
                String text = value.toString();
                key.append(
                                value instanceof Number
                                        ? 'D'
                                        : value instanceof String
                                                ? 'S'
                                                : value.getClass().getName())
                        .append(text.length())
                        .append(':')
                        .append(text);
            }
            key.append(';');
        }
        return key.toString();
    }

    /** Preserve ordinary legacy labels; reserve ~ for escaped null/empty/special components. */
    public static String display(Object value) {
        if (value == null) return "~n";
        String text = value.toString();
        if (text.isEmpty() || text.contains("|") || text.startsWith("~"))
            return "~s"
                    + Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(text.getBytes(StandardCharsets.UTF_8));
        return text;
    }

    public static void putCell(Map<String, Object> cells, String key, Object value) {
        if (cells.containsKey(key))
            throw new IllegalArgumentException("PIVOT_DUPLICATE_CELL: " + key);
        cells.put(key, value);
    }
}
