package com.foggyframework.dataset.mcp.chart;

import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.style.CategoryStyler;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bounded, JVM-only rendering of already-authorized query rows.
 * It does not query, sort, aggregate, truncate, or inspect runtime settings.
 */
@Component
public final class QueryImageRenderer implements ChartRenderer {
    public static final int MAX_ROWS = 100;
    public static final int MAX_TABLE_ROWS = 50;
    public static final int MAX_COLUMNS = 12;
    public static final long MAX_PIXELS = 8_000_000L;
    private static final int MAX_HEIGHT = 4096;
    private static final int PAD = 40;
    private static final int CELL_PAD = 14;
    private static final int BODY_SIZE = 18;
    private static final int TITLE_SIZE = 28;
    private static final int FOOTER_SIZE = 14;
    private static final Color INK = new Color(0x203047);
    private static final Color MUTED = new Color(0x64748B);
    private static final Color RULE = new Color(0xDDE5EF);
    private static final Color BLUE = new Color(0x2563EB);
    private static final Set<String> CONFIG_FIELDS = Set.of(
            "kind", "title", "xField", "yField", "xLabel", "yLabel", "unit", "columns", "footer");
    private final XChartRenderer xchart = new XChartRenderer();

    @Override
    public String getEngine() {
        return "query-image";
    }

    @Override
    public ChartRenderResult render(ChartRenderRequest request) {
        if (!"png".equals(request.image().format())) {
            throw new IllegalArgumentException("查询结果图片仅支持 PNG");
        }
        if (request.image().width() < 640 || request.image().height() < 360) {
            throw new IllegalArgumentException("查询结果图片宽度至少为 640，高度至少为 360");
        }
        requirePixelBudget(request.image().width(), request.image().height());
        if (request.data().size() > MAX_ROWS) {
            throw new IllegalArgumentException("查询结果图片最多支持 100 行，请缩小查询范围");
        }
        rejectUnknown(request.config(), CONFIG_FIELDS);
        String kind = text(request.config().get("kind"), "table", 10).toLowerCase(Locale.ROOT);
        String title = text(request.config().get("title"), "查询结果", 200);
        String footer = text(request.config().get("footer"), "", 500);
        if (request.data().isEmpty()) {
            throw new IllegalArgumentException("查询结果为空，无法生成图片");
        }
        return switch (kind) {
            case "table" -> renderTable(request, title, footer);
            case "bar", "line" -> renderChart(request, kind, title, footer);
            default -> throw new IllegalArgumentException("图片类型仅支持 table、bar、line");
        };
    }

    private ChartRenderResult renderTable(ChartRenderRequest request, String title, String footer) {
        if (request.data().size() > MAX_TABLE_ROWS) {
            throw new IllegalArgumentException("表格图片最多支持 50 行，请缩小查询范围");
        }
        List<Column> columns = columns(request.config().get("columns"), request.data());
        int width = request.image().width();
        Graphics2D measuring = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        configure(measuring);
        TableLayout layout;
        try {
            layout = layout(measuring, request.data(), columns, width, title, footer);
        } finally {
            measuring.dispose();
        }
        requirePixelBudget(width, layout.height());
        BufferedImage image = new BufferedImage(width, layout.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            configure(graphics);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, image.getHeight());
            int y = PAD;
            graphics.setFont(BundledChartFont.bold(TITLE_SIZE));
            graphics.setColor(INK);
            y = drawLines(graphics, layout.title(), PAD, y, 38, false, width - 2 * PAD);
            y += 10;
            graphics.setFont(BundledChartFont.regular(FOOTER_SIZE));
            graphics.setColor(MUTED);
            graphics.drawString(request.data().size() + " 行 · " + columns.size() + " 列", PAD, y + 18);
            y += 38;
            graphics.setColor(new Color(0xEFF5FF));
            graphics.fillRect(PAD, y, width - 2 * PAD, layout.headerHeight());
            graphics.setFont(BundledChartFont.bold(BODY_SIZE));
            graphics.setColor(INK);
            int x = PAD;
            for (int index = 0; index < columns.size(); index++) {
                drawLines(graphics, layout.headers().get(index), x + CELL_PAD, y + CELL_PAD,
                        27, layout.numericColumns()[index], layout.widths()[index] - 2 * CELL_PAD);
                x += layout.widths()[index];
            }
            y += layout.headerHeight();
            graphics.setFont(BundledChartFont.regular(BODY_SIZE));
            for (int rowIndex = 0; rowIndex < request.data().size(); rowIndex++) {
                int rowHeight = layout.rowHeights()[rowIndex];
                if (rowIndex % 2 == 1) {
                    graphics.setColor(new Color(0xF8FAFD));
                    graphics.fillRect(PAD, y, width - 2 * PAD, rowHeight);
                }
                graphics.setColor(RULE);
                graphics.drawLine(PAD, y, width - PAD, y);
                x = PAD;
                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                    Object value = request.data().get(rowIndex).get(columns.get(columnIndex).field());
                    graphics.setColor(value == null ? MUTED : INK);
                    drawLines(graphics, layout.cells().get(rowIndex).get(columnIndex),
                            x + CELL_PAD, y + CELL_PAD, 27, layout.numericColumns()[columnIndex],
                            layout.widths()[columnIndex] - 2 * CELL_PAD);
                    x += layout.widths()[columnIndex];
                }
                y += rowHeight;
            }
            graphics.setColor(RULE);
            graphics.drawRect(PAD, layout.tableTop(), width - 2 * PAD, y - layout.tableTop());
            if (!layout.footer().isEmpty()) {
                graphics.setFont(BundledChartFont.regular(FOOTER_SIZE));
                graphics.setColor(MUTED);
                drawLines(graphics, layout.footer(), PAD, y + 22, 22, false, width - 2 * PAD);
            }
        } finally {
            graphics.dispose();
        }
        return encode(image, "table", title);
    }

    private TableLayout layout(Graphics2D graphics, List<Map<String, Object>> data,
                               List<Column> columns, int width, String title, String footer) {
        int available = width - 2 * PAD;
        if (available / columns.size() < 96) {
            throw new IllegalArgumentException("表格列数过多，请减少字段或增加图片宽度");
        }
        graphics.setFont(BundledChartFont.regular(BODY_SIZE));
        FontMetrics body = graphics.getFontMetrics();
        int[] widths = new int[columns.size()];
        double totalWeight = 0;
        double[] weights = new double[columns.size()];
        boolean[] numericColumns = new boolean[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            Column column = columns.get(index);
            int measured = body.stringWidth(column.caption()) + 2 * CELL_PAD;
            boolean numeric = true;
            boolean hasNumber = false;
            for (Map<String, Object> row : data) {
                requireField(row, column.field());
                measured = Math.max(measured, body.stringWidth(cell(row.get(column.field()))) + 2 * CELL_PAD);
                Object value = row.get(column.field());
                numeric &= value == null || value instanceof Number;
                hasNumber |= value instanceof Number;
            }
            numericColumns[index] = numeric && hasNumber;
            weights[index] = Math.min(360, Math.max(120, measured));
            totalWeight += weights[index];
        }
        int remaining = available - 96 * columns.size();
        int assigned = 0;
        for (int index = 0; index < columns.size(); index++) {
            widths[index] = 96 + (int) (remaining * weights[index] / totalWeight);
            assigned += widths[index];
        }
        widths[widths.length - 1] += available - assigned;
        graphics.setFont(BundledChartFont.bold(BODY_SIZE));
        List<List<String>> headers = new ArrayList<>();
        int headerHeight = 0;
        for (int index = 0; index < columns.size(); index++) {
            List<String> wrapped = wrap(graphics.getFontMetrics(), columns.get(index).caption(),
                    widths[index] - 2 * CELL_PAD, 4);
            headers.add(wrapped);
            headerHeight = Math.max(headerHeight, wrapped.size() * 27 + 2 * CELL_PAD);
        }
        graphics.setFont(BundledChartFont.regular(BODY_SIZE));
        List<List<List<String>>> cells = new ArrayList<>();
        int[] rowHeights = new int[data.size()];
        int bodyHeight = 0;
        for (int rowIndex = 0; rowIndex < data.size(); rowIndex++) {
            List<List<String>> rowCells = new ArrayList<>();
            int rowHeight = 55;
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                List<String> wrapped = wrap(graphics.getFontMetrics(),
                        cell(data.get(rowIndex).get(columns.get(columnIndex).field())),
                        widths[columnIndex] - 2 * CELL_PAD, 6);
                rowCells.add(wrapped);
                rowHeight = Math.max(rowHeight, wrapped.size() * 27 + 2 * CELL_PAD);
            }
            cells.add(rowCells);
            rowHeights[rowIndex] = rowHeight;
            bodyHeight += rowHeight;
        }
        graphics.setFont(BundledChartFont.bold(TITLE_SIZE));
        List<String> titleLines = wrap(graphics.getFontMetrics(), title, available, 2);
        graphics.setFont(BundledChartFont.regular(FOOTER_SIZE));
        List<String> footerLines = footer.isEmpty() ? List.of()
                : wrap(graphics.getFontMetrics(), footer, available, 3);
        int tableTop = PAD + titleLines.size() * 38 + 10 + 38;
        int height = tableTop + headerHeight + bodyHeight + PAD
                + (footerLines.isEmpty() ? 0 : 22 + footerLines.size() * 22);
        if (height > MAX_HEIGHT) {
            throw new IllegalArgumentException("表格内容超过图片高度上限，请减少行数或缩短文字");
        }
        return new TableLayout(Math.max(360, height), widths, numericColumns, headers, headerHeight,
                cells, rowHeights, titleLines, footerLines, tableTop);
    }

    private ChartRenderResult renderChart(ChartRenderRequest request, String kind, String title, String footer) {
        int cap = "bar".equals(kind) ? 40 : 100;
        if (request.data().size() > cap) {
            throw new IllegalArgumentException("图表数据点过多，请缩小查询范围");
        }
        String xField = requiredText(request.config().get("xField"));
        String yField = requiredText(request.config().get("yField"));
        if (xField.startsWith("_sys") || yField.startsWith("_sys")) {
            throw new IllegalArgumentException("图表不能选择内部字段");
        }
        String xLabel = text(request.config().get("xLabel"), xField, 80);
        String yLabel = text(request.config().get("yLabel"), yField, 80);
        String unit = text(request.config().get("unit"), "", 32);
        yLabel = captionWithUnit(yLabel, unit);
        boolean hasNumber = false;
        boolean hasNegative = false;
        boolean hasPositive = false;
        for (Map<String, Object> row : request.data()) {
            requireField(row, xField);
            requireField(row, yField);
            Object x = row.get(xField);
            if (x == null || x instanceof Map<?, ?> || x instanceof Iterable<?>) {
                throw new IllegalArgumentException("图表 X 字段必须是非空的标量值");
            }
            text(x, "", 80);
            Object y = row.get(yField);
            if (y != null) {
                if (!(y instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                    throw new IllegalArgumentException("图表 Y 字段必须是有限数值或 null");
                }
                hasNumber = true;
                hasNegative |= number.doubleValue() < 0;
                hasPositive |= number.doubleValue() > 0;
            }
        }
        if (!hasNumber) {
            throw new IllegalArgumentException("图表 Y 字段没有可绘制的数值");
        }
        int width = request.image().width();
        int height = request.image().height();
        Graphics2D measuring = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        List<String> titleLines;
        List<String> footerLines;
        try {
            configure(measuring);
            measuring.setFont(BundledChartFont.bold(TITLE_SIZE));
            titleLines = wrap(measuring.getFontMetrics(), title, width - 2 * PAD, 2);
            measuring.setFont(BundledChartFont.regular(FOOTER_SIZE));
            footerLines = footer.isEmpty() ? List.of()
                    : wrap(measuring.getFontMetrics(), footer, width - 2 * PAD, 3);
            measuring.setFont(BundledChartFont.regular(16));
            int labelWidthLimit = request.data().size() > 12 ? 180
                    : Math.max(80, (width - 200) / request.data().size());
            for (Map<String, Object> row : request.data()) {
                String label = text(row.get(xField), "", 80);
                if (label.contains("\n") || measuring.getFontMetrics().stringWidth(label) > labelWidthLimit) {
                    throw new IllegalArgumentException("图表分类标签过长，请缩短标签、减少数据点或增加图片宽度");
                }
            }
            measuring.setFont(BundledChartFont.regular(BODY_SIZE));
            if (xLabel.contains("\n") || yLabel.contains("\n")
                    || measuring.getFontMetrics().stringWidth(xLabel) > width - 200
                    || measuring.getFontMetrics().stringWidth(yLabel) > height - 250) {
                throw new IllegalArgumentException("图表坐标轴名称过长，请缩短名称或增加图片尺寸");
            }
        } finally {
            measuring.dispose();
        }
        int headerHeight = PAD + titleLines.size() * 38 + 8;
        int footerHeight = PAD + footerLines.size() * 22;
        int chartHeight = height - headerHeight - footerHeight;
        if (chartHeight < 280) {
            throw new IllegalArgumentException("图表可绘制高度不足，请增加图片高度或缩短标题");
        }
        Map<String, Object> nativeConfig = new LinkedHashMap<>();
        nativeConfig.put("chartType", "CategoryChart");
        nativeConfig.put("title", title);
        nativeConfig.put("xAxisTitle", xLabel);
        nativeConfig.put("yAxisTitle", yLabel);
        nativeConfig.put("series", List.of(Map.of("name", yLabel, "xField", xField,
                "yField", yField, "renderStyle", "bar".equals(kind) ? "Bar" : "Line",
                "lineWidth", 3.0, "lineColor", "#2563EB", "fillColor", "#2563EB")));
        nativeConfig.put("styler", Map.of("legendVisible", false, "chartTitleVisible", false));
        final boolean negative = hasNegative;
        final boolean positive = hasPositive;
        ChartRenderResult rendered = xchart.renderWithCustomizer(new ChartRenderRequest(
                nativeConfig, request.data(), new ChartImageSpec(width, chartHeight, "png"), request.traceId()), chart -> {
            CategoryStyler styler = ((CategoryChart) chart).getStyler();
            styleChart(styler, kind, request.data().size());
            if ("bar".equals(kind) && !negative) {
                styler.setYAxisMin(0.0);
            } else if ("bar".equals(kind) && !positive) {
                styler.setYAxisMax(0.0);
            }
        });
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            configure(graphics);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setFont(BundledChartFont.bold(TITLE_SIZE));
            graphics.setColor(INK);
            drawLines(graphics, titleLines, PAD, PAD, 38, false, width - 2 * PAD);
            BufferedImage chartImage = ImageIO.read(new ByteArrayInputStream(rendered.bytes()));
            graphics.drawImage(chartImage, 0, headerHeight, null);
            if (!footerLines.isEmpty()) {
                graphics.setFont(BundledChartFont.regular(FOOTER_SIZE));
                graphics.setColor(MUTED);
                drawLines(graphics, footerLines, PAD, height - footerHeight, 22, false, width - 2 * PAD);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("查询结果图片编码失败", exception);
        } finally {
            graphics.dispose();
        }
        return encode(image, kind, title);
    }

    private static void styleChart(CategoryStyler styler, String kind, int rowCount) {
        styler.setBaseFont(BundledChartFont.regular(BODY_SIZE));
        styler.setAxisTitleFont(BundledChartFont.regular(BODY_SIZE));
        styler.setAxisTickLabelsFont(BundledChartFont.regular(16));
        styler.setLegendFont(BundledChartFont.regular(16));
        styler.setChartFontColor(INK);
        styler.setAxisTickLabelsColor(MUTED);
        styler.setChartBackgroundColor(Color.WHITE);
        styler.setPlotBackgroundColor(Color.WHITE);
        styler.setPlotBorderVisible(false);
        styler.setPlotGridHorizontalLinesVisible(true);
        styler.setPlotGridVerticalLinesVisible(false);
        styler.setPlotGridLinesColor(RULE);
        styler.setPlotGridLinesStroke(new BasicStroke(1));
        styler.setAxisTicksLineVisible(false);
        styler.setAxisTicksMarksVisible(false);
        styler.setChartPadding(PAD);
        styler.setAxisTitlePadding(18);
        styler.setYAxisTickMarkSpacingHint(65);
        styler.setPlotContentSize(0.90);
        styler.setXAxisMaxLabelCount(Math.min(12, rowCount));
        styler.setXAxisLabelRotation(rowCount > 12 ? 35 : 0);
        styler.setAvailableSpaceFill(0.56);
        styler.setMarkerSize("line".equals(kind) && rowCount <= 30 ? 7 : 0);
        styler.setAntiAlias(true);
        styler.setTextAntiAlias(true);
        styler.setDecimalPattern("#,##0.########");
        styler.setSeriesColors(new Color[]{BLUE});
    }

    private static List<Column> columns(Object configured, List<Map<String, Object>> data) {
        List<Column> result = new ArrayList<>();
        if (configured == null) {
            for (String field : data.get(0).keySet()) {
                if (!field.startsWith("_sys")) {
                    result.add(new Column(field, text(field, "", 160)));
                }
            }
        } else if (configured instanceof List<?> list) {
            Set<String> seen = new LinkedHashSet<>();
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> column)) {
                    throw new IllegalArgumentException("表格 columns 必须是字段配置数组");
                }
                rejectUnknown(column, Set.of("field", "caption", "unit"));
                String field = requiredText(column.get("field"));
                if (!seen.add(field) || field.startsWith("_sys")) {
                    throw new IllegalArgumentException("表格字段不能重复或包含内部字段");
                }
                String caption = text(column.get("caption"), field, 160);
                String unit = text(column.get("unit"), "", 32);
                result.add(new Column(field, captionWithUnit(caption, unit)));
            }
        } else {
            throw new IllegalArgumentException("表格 columns 必须是字段配置数组");
        }
        if (result.isEmpty() || result.size() > MAX_COLUMNS) {
            throw new IllegalArgumentException("表格需要 1 至 12 个可展示字段");
        }
        return result;
    }

    private static String cell(Object value) {
        if (value == null) {
            return "—";
        }
        if (value instanceof Map<?, ?> || value instanceof Iterable<?> || value.getClass().isArray()) {
            throw new IllegalArgumentException("表格仅支持标量字段，请选择可展示的字段");
        }
        if (value instanceof Number number && !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("表格数值字段必须是有限数值");
        }
        String formatted = value instanceof BigDecimal decimal ? decimal.toPlainString()
                : value instanceof BigInteger integer ? integer.toString() : value.toString();
        return text(formatted, "", 400);
    }

    /** Metadata captions may already carry the explicitly selected unit. */
    static String captionWithUnit(String caption, String unit) {
        String normalizedCaption = caption.stripTrailing();
        if (unit.isEmpty() || normalizedCaption.endsWith("（" + unit + "）")
                || normalizedCaption.endsWith("(" + unit + ")")) {
            return caption;
        }
        return caption + "（" + unit + "）";
    }

    private static String requiredText(Object value) {
        String result = text(value, "", 160);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("图片字段名称不得为空");
        }
        return result;
    }

    private static String text(Object value, String fallback, int maxLength) {
        if (value != null && !(value instanceof CharSequence) && !(value instanceof Number)
                && !(value instanceof Boolean) && !(value instanceof java.util.Date)
                && !(value instanceof java.time.temporal.TemporalAccessor)) {
            throw new IllegalArgumentException("图片文字必须是标量值");
        }
        String result = value == null ? fallback : value.toString();
        result = result.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
        if (result.length() > maxLength || result.codePoints().anyMatch(code -> code < 32 && code != '\n')) {
            throw new IllegalArgumentException("图片文字过长或包含不支持的控制字符");
        }
        BundledChartFont.validateGlyphs(result);
        return result;
    }

    private static List<String> wrap(FontMetrics metrics, String text, int maxWidth, int maxLines) {
        List<String> result = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder current = new StringBuilder();
            for (int code : paragraph.codePoints().toArray()) {
                String next = new String(Character.toChars(code));
                if (metrics.stringWidth(current + next) > maxWidth && !current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                if (metrics.stringWidth(next) > maxWidth) {
                    throw new IllegalArgumentException("图片文字无法在当前宽度内排版，请增加图片宽度");
                }
                current.append(next);
                if (result.size() >= maxLines) {
                    throw new IllegalArgumentException("图片文字换行过多，请缩短文字或增加图片宽度");
                }
            }
            result.add(current.toString());
            if (result.size() > maxLines) {
                throw new IllegalArgumentException("图片文字换行过多，请缩短文字或增加图片宽度");
            }
        }
        return result;
    }

    private static int drawLines(Graphics2D graphics, List<String> lines, int x, int y,
                                 int lineHeight, boolean right, int width) {
        FontMetrics metrics = graphics.getFontMetrics();
        for (String line : lines) {
            graphics.drawString(line, right ? x + width - metrics.stringWidth(line) : x,
                    y + metrics.getAscent());
            y += lineHeight;
        }
        return y;
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    private static ChartRenderResult encode(BufferedImage image, String kind, String title) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("当前 Java 环境缺少 PNG 编码器");
            }
            return new ChartRenderResult(output.toByteArray(), "png", image.getWidth(), image.getHeight(), kind, title);
        } catch (IOException exception) {
            throw new IllegalStateException("查询结果图片编码失败", exception);
        }
    }

    private static void requireField(Map<String, Object> row, String field) {
        if (!row.containsKey(field)) {
            throw new IllegalArgumentException("图片字段不在实际查询结果中，请检查字段选择");
        }
    }

    private static void requirePixelBudget(int width, int height) {
        if ((long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException("图片像素总量不得超过 800 万");
        }
    }

    private static void rejectUnknown(Map<?, ?> config, Set<String> allowed) {
        if (config.keySet().stream().anyMatch(key -> !(key instanceof String) || !allowed.contains(key))) {
            throw new IllegalArgumentException("图片配置包含不支持的选项");
        }
    }

    private record Column(String field, String caption) { }
    private record TableLayout(int height, int[] widths, boolean[] numericColumns,
                               List<List<String>> headers, int headerHeight,
                               List<List<List<String>>> cells, int[] rowHeights, List<String> title,
                               List<String> footer, int tableTop) { }
}
