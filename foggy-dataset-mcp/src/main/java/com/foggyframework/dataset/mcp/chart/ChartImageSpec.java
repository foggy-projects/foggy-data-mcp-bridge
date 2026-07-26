package com.foggyframework.dataset.mcp.chart;

import java.util.Locale;
import java.util.Map;

/**
 * Renderer-independent image output settings.
 *
 * <p>The chart configuration itself remains renderer-native. Only the output
 * envelope is shared so the tool can persist the rendered bytes consistently.
 */
public record ChartImageSpec(int width, int height, String format) {

    public static final int DEFAULT_WIDTH = 800;
    public static final int DEFAULT_HEIGHT = 600;
    public static final String DEFAULT_FORMAT = "png";
    private static final int MIN_SIZE = 100;
    private static final int MAX_SIZE = 4096;

    public ChartImageSpec {
        if (width < MIN_SIZE || width > MAX_SIZE) {
            throw new IllegalArgumentException("image.width 必须在 " + MIN_SIZE + "-" + MAX_SIZE + " 之间");
        }
        if (height < MIN_SIZE || height > MAX_SIZE) {
            throw new IllegalArgumentException("image.height 必须在 " + MIN_SIZE + "-" + MAX_SIZE + " 之间");
        }
        if (format == null || !format.matches("[a-zA-Z0-9]+")) {
            throw new IllegalArgumentException("image.format 只能包含字母和数字");
        }
        format = normalizeFormat(format);
    }

    public static ChartImageSpec from(Object value) {
        if (value == null) {
            return new ChartImageSpec(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FORMAT);
        }
        if (!(value instanceof Map<?, ?> image)) {
            throw new IllegalArgumentException("image 必须是对象");
        }

        int width = integerValue(image.get("width"), DEFAULT_WIDTH, "image.width");
        int height = integerValue(image.get("height"), DEFAULT_HEIGHT, "image.height");
        Object formatValue = image.get("format");
        String format = formatValue == null ? DEFAULT_FORMAT : formatValue.toString();
        return new ChartImageSpec(width, height, format);
    }

    private static int integerValue(Object value, int defaultValue, String field) {
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(field + " 必须是整数");
        }
        double doubleValue = number.doubleValue();
        int intValue = number.intValue();
        if (doubleValue != intValue) {
            throw new IllegalArgumentException(field + " 必须是整数");
        }
        return intValue;
    }

    private static String normalizeFormat(String format) {
        String normalized = format.toLowerCase(Locale.ROOT);
        return "jpeg".equals(normalized) ? "jpg" : normalized;
    }
}
