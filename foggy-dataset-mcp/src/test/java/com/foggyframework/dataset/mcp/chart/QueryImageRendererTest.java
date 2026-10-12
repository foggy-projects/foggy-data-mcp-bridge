package com.foggyframework.dataset.mcp.chart;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryImageRendererTest {
    private final QueryImageRenderer renderer = new QueryImageRenderer();

    @Test
    void shouldLoadBundledChineseGlyphsWithoutInstalledFonts() {
        assertEquals("Noto Sans SC", BundledChartFont.regular(18).getFamily(java.util.Locale.ROOT));
        assertEquals(-1, BundledChartFont.regular(18).canDisplayUpTo("中文查询结果：销售额（万元）123.45 —"));
        assertEquals(28, BundledChartFont.bold(28).getSize());
        assertEquals(18, BundledChartFont.regular(18).getSize());
        assertEquals(14, BundledChartFont.regular(14).getSize());
        assertThrows(IllegalArgumentException.class, () -> BundledChartFont.validateGlyphs("\uD83D\uDE00"));
    }

    @Test
    void shouldRenderPngForAllThreeKinds() throws IOException {
        for (String kind : List.of("table", "bar", "line")) {
            ChartRenderResult result = renderer.render(request(kind, monthlyRows()));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.bytes()));
            assertNotNull(image);
            assertEquals(1200, image.getWidth());
            assertEquals(result.height(), image.getHeight());
            assertEquals("png", result.format());
            assertEquals(kind, result.chartType());
            assertTrue(result.bytes().length > 5_000);
            assertEquals(Color.WHITE.getRGB(), image.getRGB(0, 0));
            assertEquals(Color.WHITE.getRGB(), image.getRGB(1199, image.getHeight() - 1));
            assertTrue(countColoredPixels(image) > 1000, "PNG must contain rendered content");
        }
    }

    @Test
    void shouldFitWrappedTableAndKeepOriginalPrecisionAndRows() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("month", "一段需要自动换行的中文说明，用于验证表格单元格的可读性和排版");
        row.put("amount", new BigDecimal("123456789.123456789"));
        row.put("orders", null);
        List<Map<String, Object>> rows = List.of(row);
        ChartRenderResult result = renderer.render(request("table", rows));
        assertTrue(result.height() >= 360);
        assertTrue(result.height() <= 4096);
        assertEquals(new BigDecimal("123456789.123456789"), row.get("amount"));
        assertNull(row.get("orders"));
        assertEquals(1, rows.size());
    }

    @Test
    void shouldNotDuplicateUnitsAlreadyPresentInSchemaCaptions() {
        assertEquals("销售额（万元）", QueryImageRenderer.captionWithUnit("销售额（万元）", "万元"));
        assertEquals("销售额(万元)", QueryImageRenderer.captionWithUnit("销售额(万元)", "万元"));
        for (String kind : List.of("table", "bar", "line")) {
            ChartRenderRequest original = request(kind, monthlyRows());
            Map<String, Object> config = new LinkedHashMap<>(original.config());
            config.put("yLabel", "销售额（万元）");
            config.put("columns", List.of(Map.of("field", "month", "caption", "月份"),
                    Map.of("field", "amount", "caption", "销售额（万元）", "unit", "万元"),
                    Map.of("field", "orders", "caption", "订单数（单）", "unit", "单")));
            assertArrayEquals(renderer.render(original).bytes(), renderer.render(new ChartRenderRequest(
                    config, original.data(), original.image(), original.traceId())).bytes(),
                    "Including a unit in schema captions should produce the same image");
        }
    }

    @Test
    void numericColumnShouldRightAlignNullPlaceholderWithNumbers() throws IOException {
        Map<String, Object> nullRow = new LinkedHashMap<>();
        nullRow.put("month", "二月");
        nullRow.put("amount", null);
        ChartRenderResult result = renderer.render(new ChartRenderRequest(Map.of(
                "kind", "table", "title", "数值对齐",
                "columns", List.of(Map.of("field", "month", "caption", "月份"),
                        Map.of("field", "amount", "caption", "销售额"))),
                List.of(Map.of("month", "一月", "amount", 1000), nullRow), image(), "trace"));
        BufferedImage rendered = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        // Title + summary place the header at y=126, followed by 55px rows.
        int numberRight = rightmostTextPixel(rendered, 181, 236);
        int nullRight = rightmostTextPixel(rendered, 236, 291);
        assertTrue(numberRight > 1100, "Numeric content should sit near the table's right padding");
        assertTrue(nullRight > 1100, "The null placeholder must follow its numeric column alignment");
        assertTrue(Math.abs(numberRight - nullRight) <= 3,
                "Numeric values and null placeholders should share the same right edge");
    }

    @Test
    void shouldRenderNullAsGapInsteadOfZeroOrConnectedLine() throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("month", "一月", "amount", 10, "orders", 1));
        Map<String, Object> missing = new LinkedHashMap<>();
        missing.put("month", "二月");
        missing.put("amount", null);
        missing.put("orders", 2);
        rows.add(missing);
        rows.add(Map.of("month", "三月", "amount", 30, "orders", 3));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(renderer.render(request("line", rows)).bytes()));
        int minX = image.getWidth();
        int maxX = -1;
        for (int y = 100; y < image.getHeight() - 100; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (isSeriesBlue(image.getRGB(x, y))) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                }
            }
        }
        assertTrue(maxX > minX + 200, "Both non-null points should be drawn");
        int middle = (minX + maxX) / 2;
        for (int x = middle - 10; x <= middle + 10; x++) {
            for (int y = 100; y < image.getHeight() - 100; y++) {
                assertFalse(isSeriesBlue(image.getRGB(x, y)), "Missing data point must leave a gap");
            }
        }
    }

    @Test
    void shouldShowNegativeBars() throws IOException {
        List<Map<String, Object>> rows = List.of(
                Map.of("month", "一月", "amount", -25, "orders", 1),
                Map.of("month", "二月", "amount", 40, "orders", 2));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(renderer.render(request("bar", rows)).bytes()));
        int blueOnLeft = 0;
        int blueOnRight = 0;
        for (int y = 100; y < image.getHeight() - 100; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (isSeriesBlue(image.getRGB(x, y))) {
                    if (x < image.getWidth() / 2) {
                        blueOnLeft++;
                    } else {
                        blueOnRight++;
                    }
                }
            }
        }
        assertTrue(blueOnLeft > 100, "Negative actual values must not be clipped at zero");
        assertTrue(blueOnRight > 100);
    }

    @Test
    void shouldRejectUnsafeLayoutAndNotEchoInputsInErrors() {
        List<Map<String, Object>> tooMany = java.util.Collections.nCopies(51, monthlyRows().get(0));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(request("table", tooMany)));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(new ChartRenderRequest(
                Map.of("kind", "table", "title", "密".repeat(201)), monthlyRows(), image(), "trace")));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> renderer.render(new ChartRenderRequest(
                        Map.of("kind", "table", "secret-token-value", "connection-string-value"),
                        monthlyRows(), image(), "trace")));
        assertFalse(unknown.getMessage().contains("secret-token-value"));
        assertFalse(unknown.getMessage().contains("connection-string-value"));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(new ChartRenderRequest(
                Map.of("kind", "table"), monthlyRows(), new ChartImageSpec(4096, 4096, "png"), "trace")));
    }

    @Test
    void shouldRejectUnselectedMissingAndNonNumericChartFields() {
        assertThrows(IllegalArgumentException.class, () -> renderer.render(new ChartRenderRequest(
                Map.of("kind", "bar", "xField", "month", "yField", "missing"), monthlyRows(), image(), "trace")));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(request("bar", List.of(
                Map.of("month", "一月", "amount", "not-a-number", "orders", 1)))));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(request("bar", List.of(
                Map.of("month", "一月", "amount", Double.NaN, "orders", 1)))));
    }

    private ChartRenderRequest request(String kind, List<Map<String, Object>> rows) {
        return new ChartRenderRequest(Map.of(
                "kind", kind, "title", "2026 年上半年销售概览", "xField", "month", "yField", "amount",
                "xLabel", "月份", "yLabel", "销售额", "unit", "万元",
                "columns", List.of(Map.of("field", "month", "caption", "月份"),
                        Map.of("field", "amount", "caption", "销售额", "unit", "万元"),
                        Map.of("field", "orders", "caption", "订单数", "unit", "单")),
                "footer", "来自实际查询结果 · 数值保留原始精度"), rows, image(), "trace");
    }

    private ChartImageSpec image() {
        return new ChartImageSpec(1200, 720, "png");
    }

    private List<Map<String, Object>> monthlyRows() {
        return List.of(
                Map.of("month", "一月", "amount", 120.5, "orders", 820),
                Map.of("month", "二月", "amount", 151.2, "orders", 965),
                Map.of("month", "三月", "amount", 136.8, "orders", 901),
                Map.of("month", "四月", "amount", 184.6, "orders", 1124),
                Map.of("month", "五月", "amount", 201.3, "orders", 1238),
                Map.of("month", "六月", "amount", 228.9, "orders", 1406));
    }

    private static int countColoredPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != Color.WHITE.getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isSeriesBlue(int rgb) {
        Color color = new Color(rgb);
        return color.getBlue() > 170 && color.getRed() < 70 && color.getGreen() < 140;
    }

    private static int rightmostTextPixel(BufferedImage image, int top, int bottom) {
        int rightmost = -1;
        for (int y = top + 8; y < bottom - 8; y++) {
            for (int x = image.getWidth() / 2; x < image.getWidth() - 40; x++) {
                Color color = new Color(image.getRGB(x, y));
                // Include the anti-aliased, muted null glyph but exclude pale table rules.
                if (color.getRed() < 200 && color.getGreen() < 210 && color.getBlue() < 220) {
                    rightmost = Math.max(rightmost, x);
                }
            }
        }
        return rightmost;
    }
}
