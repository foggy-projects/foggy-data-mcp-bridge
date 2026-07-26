package com.foggyframework.dataset.mcp.chart;

import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.CategorySeries;
import org.knowm.xchart.ChartEncoder;
import org.knowm.xchart.PieChart;
import org.knowm.xchart.PieChartBuilder;
import org.knowm.xchart.PieSeries;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.internal.chartpart.IChart;
import org.knowm.xchart.internal.series.AxesChartSeries;
import org.knowm.xchart.internal.series.MarkerSeries;
import org.knowm.xchart.internal.series.Series;
import org.knowm.xchart.style.AxesChartStyler;
import org.knowm.xchart.style.CategoryStyler;
import org.knowm.xchart.style.PieStyler;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.XYStyler;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * In-process XChart renderer.
 *
 * <p>The JSON contract mirrors XChart's builder, styler and series concepts.
 * It is intentionally not a shared semantic chart model and is never converted
 * to or from an ECharts Option.
 */
@Component
public class XChartRenderer implements ChartRenderer {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("png", "jpg");
    private static final Set<String> COMMON_CONFIG_FIELDS = Set.of(
            "chartType", "title", "theme", "styler"
    );
    private static final Set<String> AXES_CONFIG_FIELDS = Set.of(
            "xAxisTitle", "yAxisTitle", "series"
    );
    private static final Set<String> PIE_CONFIG_FIELDS = Set.of(
            "nameField", "valueField", "renderStyle"
    );
    private static final Set<String> COMMON_STYLER_FIELDS = Set.of(
            "legendVisible", "legendPosition", "chartTitleVisible",
            "plotBorderVisible", "antiAlias", "chartPadding", "markerSize",
            "decimalPattern", "chartBackgroundColor", "plotBackgroundColor",
            "chartFontColor", "seriesColors", "fontFamily", "fontSize"
    );
    private static final Set<String> AXES_STYLER_FIELDS = Set.of(
            "axisTitlesVisible", "axisTicksVisible", "plotGridLinesVisible",
            "xAxisLogarithmic", "yAxisLogarithmic", "xAxisLabelRotation",
            "xAxisMin", "xAxisMax", "yAxisMin", "yAxisMax",
            "xAxisDecimalPattern", "yAxisDecimalPattern"
    );
    private static final Set<String> CATEGORY_STYLER_FIELDS = Set.of(
            "labelsVisible", "stacked", "overlapped", "availableSpaceFill",
            "defaultSeriesRenderStyle"
    );
    private static final Set<String> XY_STYLER_FIELDS = Set.of(
            "defaultSeriesRenderStyle"
    );
    private static final Set<String> PIE_STYLER_FIELDS = Set.of(
            "labelsVisible", "circular", "forceAllLabelsVisible",
            "donutThickness", "startAngleInDegrees", "labelType",
            "defaultSeriesRenderStyle"
    );
    private static final Set<String> SERIES_FIELDS = Set.of(
            "name", "xField", "yField", "seriesField", "renderStyle",
            "smooth", "lineColor", "fillColor", "markerColor", "lineWidth",
            "showInLegend", "enabled", "label", "yAxisGroup",
            "yAxisDecimalPattern"
    );

    @Override
    public String getEngine() {
        return "xchart";
    }

    @Override
    public ChartRenderResult render(ChartRenderRequest request) {
        Map<String, Object> config = request.config();
        String chartType = requiredString(config, "chartType");
        String normalizedChartType = normalizeChartType(chartType);
        validateConfig(config, normalizedChartType);
        String title = stringValue(config.get("title"), "数据图表");
        ChartImageSpec image = request.image();

        if (!SUPPORTED_FORMATS.contains(image.format())) {
            throw new IllegalArgumentException(
                    "XChart 渲染器仅支持 png/jpg，当前格式: " + image.format());
        }

        IChart chart = switch (normalizedChartType) {
            case "categorychart", "category" -> buildCategoryChart(config, request.data(), image, title);
            case "xychart", "xy" -> buildXYChart(config, request.data(), image, title);
            case "piechart", "pie" -> buildPieChart(config, request.data(), image, title);
            default -> throw new IllegalArgumentException(
                    "XChart chartType 仅支持 CategoryChart、XYChart、PieChart，当前值: " + chartType);
        };

        try {
            byte[] bytes = ChartEncoder.getBytes(chart, image.format());
            return new ChartRenderResult(
                    bytes, image.format(), image.width(), image.height(), chartType, title);
        } catch (IOException e) {
            throw new IllegalStateException("XChart 图片编码失败: " + e.getMessage(), e);
        }
    }

    private CategoryChart buildCategoryChart(
            Map<String, Object> config,
            List<Map<String, Object>> data,
            ChartImageSpec image,
            String title
    ) {
        CategoryChartBuilder builder = new CategoryChartBuilder()
                .width(image.width())
                .height(image.height())
                .title(title);
        optionalString(config, "xAxisTitle").ifPresent(builder::xAxisTitle);
        optionalString(config, "yAxisTitle").ifPresent(builder::yAxisTitle);
        applyTheme(builder, config);

        CategoryChart chart = builder.build();
        Map<String, Object> stylerConfig = objectMap(config.get("styler"), "styler", false);
        applyCommonStyler(chart.getStyler(), stylerConfig);
        applyAxesStyler(chart.getStyler(), stylerConfig);
        applyCategoryStyler(chart.getStyler(), stylerConfig);

        List<Map<String, Object>> seriesConfigs = objectList(config.get("series"), "series", true);
        List<SeriesBinding> bindings = resolveSeriesBindings(seriesConfigs, data);
        for (SeriesBinding binding : bindings) {
            CategorySeries series = chart.addSeries(binding.name(), binding.xData(), binding.yData());
            optionalString(binding.config(), "renderStyle")
                    .map(value -> enumValue(
                            CategorySeries.CategorySeriesRenderStyle.class, value, "series.renderStyle"))
                    .ifPresent(series::setChartCategorySeriesRenderStyle);
            booleanValue(binding.config(), "smooth").ifPresent(series::setSmooth);
            applyAxesSeriesStyle(series, binding.config());
        }
        return chart;
    }

    private XYChart buildXYChart(
            Map<String, Object> config,
            List<Map<String, Object>> data,
            ChartImageSpec image,
            String title
    ) {
        XYChartBuilder builder = new XYChartBuilder()
                .width(image.width())
                .height(image.height())
                .title(title);
        optionalString(config, "xAxisTitle").ifPresent(builder::xAxisTitle);
        optionalString(config, "yAxisTitle").ifPresent(builder::yAxisTitle);
        applyTheme(builder, config);

        XYChart chart = builder.build();
        Map<String, Object> stylerConfig = objectMap(config.get("styler"), "styler", false);
        applyCommonStyler(chart.getStyler(), stylerConfig);
        applyAxesStyler(chart.getStyler(), stylerConfig);
        applyXYStyler(chart.getStyler(), stylerConfig);

        List<Map<String, Object>> seriesConfigs = objectList(config.get("series"), "series", true);
        List<SeriesBinding> bindings = resolveSeriesBindings(seriesConfigs, data);
        for (SeriesBinding binding : bindings) {
            validateXYValues(binding.xData());
            XYSeries series = chart.addSeries(binding.name(), binding.xData(), binding.yData());
            optionalString(binding.config(), "renderStyle")
                    .map(value -> enumValue(
                            XYSeries.XYSeriesRenderStyle.class, value, "series.renderStyle"))
                    .ifPresent(series::setXYSeriesRenderStyle);
            booleanValue(binding.config(), "smooth").ifPresent(series::setSmooth);
            applyAxesSeriesStyle(series, binding.config());
        }
        return chart;
    }

    private PieChart buildPieChart(
            Map<String, Object> config,
            List<Map<String, Object>> data,
            ChartImageSpec image,
            String title
    ) {
        PieChartBuilder builder = new PieChartBuilder()
                .width(image.width())
                .height(image.height())
                .title(title);
        applyTheme(builder, config);

        PieChart chart = builder.build();
        Map<String, Object> stylerConfig = objectMap(config.get("styler"), "styler", false);
        applyCommonStyler(chart.getStyler(), stylerConfig);
        applyPieStyler(chart.getStyler(), stylerConfig);

        List<PieBinding> bindings = resolvePieBindings(config, data);
        for (PieBinding binding : bindings) {
            PieSeries series = chart.addSeries(binding.name(), binding.value());
            optionalString(binding.config(), "renderStyle")
                    .map(value -> enumValue(
                            PieSeries.PieSeriesRenderStyle.class, value, "series.renderStyle"))
                    .ifPresent(series::setChartPieSeriesRenderStyle);
            applySeriesStyle(series, binding.config());
        }
        return chart;
    }

    private List<SeriesBinding> resolveSeriesBindings(
            List<Map<String, Object>> seriesConfigs,
            List<Map<String, Object>> data
    ) {
        List<SeriesBinding> bindings = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> seriesConfig : seriesConfigs) {
            index++;
            String seriesField = optionalString(seriesConfig, "seriesField").orElse(null);
            if (seriesField == null) {
                String name = stringValue(seriesConfig.get("name"), "Series " + index);
                bindings.add(createSeriesBinding(name, seriesConfig, data));
                continue;
            }

            if (seriesConfig.containsKey("xData") || seriesConfig.containsKey("yData")) {
                throw new IllegalArgumentException(
                        "series.seriesField 不能与 xData/yData 同时使用");
            }
            if (data.isEmpty()) {
                throw new IllegalArgumentException(
                        "series.seriesField 需要顶层 data，当前 data 为空");
            }
            Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
            for (Map<String, Object> row : data) {
                Object groupValue = requiredField(row, seriesField);
                groups.computeIfAbsent(String.valueOf(groupValue), ignored -> new ArrayList<>()).add(row);
            }
            String namePrefix = optionalString(seriesConfig, "name").orElse(null);
            for (Map.Entry<String, List<Map<String, Object>>> group : groups.entrySet()) {
                String name = namePrefix == null
                        ? group.getKey()
                        : namePrefix + " - " + group.getKey();
                bindings.add(createSeriesBinding(name, seriesConfig, group.getValue()));
            }
        }
        return bindings;
    }

    private SeriesBinding createSeriesBinding(
            String name,
            Map<String, Object> config,
            List<Map<String, Object>> data
    ) {
        String xField = requiredString(config, "xField");
        String yField = requiredString(config, "yField");
        if (data.isEmpty()) {
            throw new IllegalArgumentException(
                    "series.xField/yField 需要顶层 data，当前 data 为空");
        }
        List<Object> xData = new ArrayList<>(data.size());
        List<Number> yData = new ArrayList<>(data.size());
        for (Map<String, Object> row : data) {
            xData.add(requiredField(row, xField));
            Object yValue = fieldValue(row, yField);
            if (yValue == null) {
                yData.add(Double.NaN);
            } else if (yValue instanceof Number number) {
                yData.add(number);
            } else {
                throw new IllegalArgumentException(
                        "字段 " + yField + " 必须是数值或 null");
            }
        }
        return new SeriesBinding(name, xData, yData, config);
    }

    private List<PieBinding> resolvePieBindings(
            Map<String, Object> config,
            List<Map<String, Object>> data
    ) {
        String nameField = requiredString(config, "nameField");
        String valueField = requiredString(config, "valueField");
        if (data.isEmpty()) {
            throw new IllegalArgumentException(
                    "PieChart nameField/valueField 需要顶层 data，当前 data 为空");
        }
        Map<String, Double> totals = new LinkedHashMap<>();
        for (Map<String, Object> row : data) {
            String name = String.valueOf(requiredField(row, nameField));
            Object value = requiredField(row, valueField);
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException(
                        "字段 " + valueField + " 必须是数值");
            }
            totals.merge(name, number.doubleValue(), Double::sum);
        }
        String renderStyle = optionalString(config, "renderStyle").orElse(null);
        Map<String, Object> style = renderStyle == null
                ? Map.of()
                : Map.of("renderStyle", renderStyle);
        return totals.entrySet().stream()
                .map(entry -> new PieBinding(entry.getKey(), entry.getValue(), style))
                .toList();
    }

    private void applyTheme(Object builder, Map<String, Object> config) {
        String themeValue = optionalString(config, "theme").orElse(null);
        if (themeValue == null) {
            return;
        }
        Styler.ChartTheme theme = enumValue(Styler.ChartTheme.class, themeValue, "theme");
        if (builder instanceof CategoryChartBuilder categoryBuilder) {
            categoryBuilder.theme(theme);
        } else if (builder instanceof XYChartBuilder xyBuilder) {
            xyBuilder.theme(theme);
        } else if (builder instanceof PieChartBuilder pieBuilder) {
            pieBuilder.theme(theme);
        }
    }

    private void applyCommonStyler(Styler styler, Map<String, Object> config) {
        booleanValue(config, "legendVisible").ifPresent(styler::setLegendVisible);
        optionalString(config, "legendPosition")
                .map(value -> enumValue(Styler.LegendPosition.class, value, "styler.legendPosition"))
                .ifPresent(styler::setLegendPosition);
        booleanValue(config, "chartTitleVisible").ifPresent(styler::setChartTitleVisible);
        booleanValue(config, "plotBorderVisible").ifPresent(styler::setPlotBorderVisible);
        booleanValue(config, "antiAlias").ifPresent(styler::setAntiAlias);
        integerValue(config, "chartPadding").ifPresent(styler::setChartPadding);
        integerValue(config, "markerSize").ifPresent(styler::setMarkerSize);
        optionalString(config, "decimalPattern").ifPresent(styler::setDecimalPattern);
        colorValue(config, "chartBackgroundColor").ifPresent(styler::setChartBackgroundColor);
        colorValue(config, "plotBackgroundColor").ifPresent(styler::setPlotBackgroundColor);
        colorValue(config, "chartFontColor").ifPresent(styler::setChartFontColor);

        Object colorsValue = config.get("seriesColors");
        if (colorsValue != null) {
            List<?> colors = valueList(colorsValue, "styler.seriesColors", true);
            Color[] parsed = colors.stream()
                    .map(value -> parseColor(String.valueOf(value), "styler.seriesColors"))
                    .toArray(Color[]::new);
            styler.setSeriesColors(parsed);
        }

        String fontFamily = optionalString(config, "fontFamily").orElse(null);
        Integer fontSize = integerValue(config, "fontSize").orElse(null);
        if (fontFamily != null || fontSize != null) {
            styler.setBaseFont(new Font(
                    fontFamily == null ? Font.SANS_SERIF : fontFamily,
                    Font.PLAIN,
                    fontSize == null ? 14 : fontSize
            ));
        }
    }

    private void applyAxesStyler(AxesChartStyler styler, Map<String, Object> config) {
        booleanValue(config, "axisTitlesVisible").ifPresent(styler::setAxisTitlesVisible);
        booleanValue(config, "axisTicksVisible").ifPresent(styler::setAxisTicksVisible);
        booleanValue(config, "plotGridLinesVisible").ifPresent(styler::setPlotGridLinesVisible);
        booleanValue(config, "xAxisLogarithmic").ifPresent(styler::setXAxisLogarithmic);
        booleanValue(config, "yAxisLogarithmic").ifPresent(styler::setYAxisLogarithmic);
        integerValue(config, "xAxisLabelRotation").ifPresent(styler::setXAxisLabelRotation);
        doubleValue(config, "xAxisMin").ifPresent(styler::setXAxisMin);
        doubleValue(config, "xAxisMax").ifPresent(styler::setXAxisMax);
        doubleValue(config, "yAxisMin").ifPresent(styler::setYAxisMin);
        doubleValue(config, "yAxisMax").ifPresent(styler::setYAxisMax);
        optionalString(config, "xAxisDecimalPattern").ifPresent(styler::setXAxisDecimalPattern);
        optionalString(config, "yAxisDecimalPattern").ifPresent(styler::setYAxisDecimalPattern);
    }

    private void applyCategoryStyler(CategoryStyler styler, Map<String, Object> config) {
        booleanValue(config, "labelsVisible").ifPresent(styler::setLabelsVisible);
        booleanValue(config, "stacked").ifPresent(styler::setStacked);
        booleanValue(config, "overlapped").ifPresent(styler::setOverlapped);
        doubleValue(config, "availableSpaceFill").ifPresent(styler::setAvailableSpaceFill);
        optionalString(config, "defaultSeriesRenderStyle")
                .map(value -> enumValue(
                        CategorySeries.CategorySeriesRenderStyle.class,
                        value,
                        "styler.defaultSeriesRenderStyle"))
                .ifPresent(styler::setDefaultSeriesRenderStyle);
    }

    private void applyXYStyler(XYStyler styler, Map<String, Object> config) {
        optionalString(config, "defaultSeriesRenderStyle")
                .map(value -> enumValue(
                        XYSeries.XYSeriesRenderStyle.class,
                        value,
                        "styler.defaultSeriesRenderStyle"))
                .ifPresent(styler::setDefaultSeriesRenderStyle);
    }

    private void applyPieStyler(PieStyler styler, Map<String, Object> config) {
        booleanValue(config, "labelsVisible").ifPresent(styler::setLabelsVisible);
        booleanValue(config, "circular").ifPresent(styler::setCircular);
        booleanValue(config, "forceAllLabelsVisible").ifPresent(styler::setForceAllLabelsVisible);
        doubleValue(config, "donutThickness").ifPresent(styler::setDonutThickness);
        doubleValue(config, "startAngleInDegrees").ifPresent(styler::setStartAngleInDegrees);
        optionalString(config, "labelType")
                .map(value -> enumValue(PieStyler.LabelType.class, value, "styler.labelType"))
                .ifPresent(styler::setLabelType);
        optionalString(config, "defaultSeriesRenderStyle")
                .map(value -> enumValue(
                        PieSeries.PieSeriesRenderStyle.class,
                        value,
                        "styler.defaultSeriesRenderStyle"))
                .ifPresent(styler::setDefaultSeriesRenderStyle);
    }

    private void applyAxesSeriesStyle(AxesChartSeries series, Map<String, Object> config) {
        applySeriesStyle(series, config);
        colorValue(config, "lineColor").ifPresent(series::setLineColor);
        doubleValue(config, "lineWidth").ifPresent(value -> series.setLineWidth(value.floatValue()));
        integerValue(config, "yAxisGroup").ifPresent(series::setYAxisGroup);
        optionalString(config, "yAxisDecimalPattern").ifPresent(series::setYAxisDecimalPattern);
        if (series instanceof MarkerSeries markerSeries) {
            colorValue(config, "markerColor").ifPresent(markerSeries::setMarkerColor);
        }
    }

    private void applySeriesStyle(Series series, Map<String, Object> config) {
        colorValue(config, "fillColor").ifPresent(series::setFillColor);
        booleanValue(config, "showInLegend").ifPresent(series::setShowInLegend);
        booleanValue(config, "enabled").ifPresent(series::setEnabled);
        optionalString(config, "label").ifPresent(series::setLabel);
    }

    private void validateXYValues(List<?> xData) {
        for (Object value : xData) {
            if (!(value instanceof Number) && !(value instanceof Date)) {
                throw new IllegalArgumentException(
                        "XYChart 的 xData/xField 必须是数值或日期；分类字符串请使用 CategoryChart");
            }
        }
    }

    private void validateConfig(Map<String, Object> config, String chartType) {
        Set<String> allowedConfig = new java.util.LinkedHashSet<>(COMMON_CONFIG_FIELDS);
        Set<String> allowedStyler = new java.util.LinkedHashSet<>(COMMON_STYLER_FIELDS);
        switch (chartType) {
            case "categorychart", "category" -> {
                allowedConfig.addAll(AXES_CONFIG_FIELDS);
                allowedStyler.addAll(AXES_STYLER_FIELDS);
                allowedStyler.addAll(CATEGORY_STYLER_FIELDS);
                validateSeries(config);
            }
            case "xychart", "xy" -> {
                allowedConfig.addAll(AXES_CONFIG_FIELDS);
                allowedStyler.addAll(AXES_STYLER_FIELDS);
                allowedStyler.addAll(XY_STYLER_FIELDS);
                validateSeries(config);
            }
            case "piechart", "pie" -> {
                allowedConfig.addAll(PIE_CONFIG_FIELDS);
                allowedStyler.addAll(PIE_STYLER_FIELDS);
            }
            default -> {
                return;
            }
        }
        rejectUnknown(config, allowedConfig, "config");
        Map<String, Object> styler = objectMap(config.get("styler"), "styler", false);
        rejectUnknown(styler, allowedStyler, "styler");
    }

    private void validateSeries(Map<String, Object> config) {
        List<Map<String, Object>> series = objectList(config.get("series"), "series", true);
        for (int i = 0; i < series.size(); i++) {
            Map<String, Object> item = series.get(i);
            rejectUnknown(item, SERIES_FIELDS, "series[" + i + "]");
            requiredString(item, "xField");
            requiredString(item, "yField");
        }
    }

    private static void rejectUnknown(
            Map<String, Object> value,
            Set<String> allowed,
            String field
    ) {
        List<String> unknown = value.keySet().stream()
                .filter(key -> !allowed.contains(key))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    field + " 包含不支持的字段: " + unknown);
        }
    }

    private static Object requiredField(Map<String, Object> row, String field) {
        Object value = fieldValue(row, field);
        if (value == null) {
            throw new IllegalArgumentException("data 字段值不能为空: " + field);
        }
        return value;
    }

    private static Object fieldValue(Map<String, Object> row, String field) {
        if (!row.containsKey(field)) {
            throw new IllegalArgumentException("data 中不存在字段: " + field);
        }
        return row.get(field);
    }

    private static String normalizeChartType(String chartType) {
        return chartType.replace("_", "")
                .replace("-", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String requiredString(Map<String, Object> map, String field) {
        return optionalString(map, field)
                .orElseThrow(() -> new IllegalArgumentException(field + " 不得为空"));
    }

    private static java.util.Optional<String> optionalString(
            Map<String, Object> map,
            String field
    ) {
        Object value = map.get(field);
        if (value == null) {
            return java.util.Optional.empty();
        }
        String text = value.toString().trim();
        return text.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(text);
    }

    private static String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private static Map<String, Object> objectMap(Object value, String field, boolean required) {
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException(field + " 不得为空");
            }
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException(field + " 必须是对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static List<Map<String, Object>> objectList(
            Object value,
            String field,
            boolean required
    ) {
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException(field + " 不得为空");
            }
            return List.of();
        }
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalArgumentException(field + " 必须是数组");
        }
        if (required && rawList.isEmpty()) {
            throw new IllegalArgumentException(field + " 不得为空");
        }
        List<Map<String, Object>> result = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            result.add(objectMap(item, field + "[]", true));
        }
        return result;
    }

    private static List<?> valueList(Object value, String field, boolean required) {
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException(field + " 不得为空");
            }
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(field + " 必须是数组");
        }
        if (required && list.isEmpty()) {
            throw new IllegalArgumentException(field + " 不得为空");
        }
        return list;
    }

    private static java.util.Optional<Boolean> booleanValue(
            Map<String, Object> map,
            String field
    ) {
        Object value = map.get(field);
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException(field + " 必须是布尔值");
        }
        return java.util.Optional.of(bool);
    }

    private static java.util.Optional<Integer> integerValue(
            Map<String, Object> map,
            String field
    ) {
        Object value = map.get(field);
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(field + " 必须是整数");
        }
        double doubleValue = number.doubleValue();
        int intValue = number.intValue();
        if (doubleValue != intValue) {
            throw new IllegalArgumentException(field + " 必须是整数");
        }
        return java.util.Optional.of(intValue);
    }

    private static java.util.Optional<Double> doubleValue(
            Map<String, Object> map,
            String field
    ) {
        Object value = map.get(field);
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(field + " 必须是数值");
        }
        return java.util.Optional.of(number.doubleValue());
    }

    private static java.util.Optional<Color> colorValue(
            Map<String, Object> map,
            String field
    ) {
        Object value = map.get(field);
        if (value == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(parseColor(value.toString(), field));
    }

    private static Color parseColor(String value, String field) {
        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        try {
            if (normalized.length() == 6) {
                return new Color(Integer.parseInt(normalized, 16));
            }
            if (normalized.length() == 8) {
                return new Color((int) Long.parseLong(normalized, 16), true);
            }
        } catch (NumberFormatException ignored) {
            // Fall through to the uniform validation error below.
        }
        throw new IllegalArgumentException(field + " 必须是 #RRGGBB 或 #AARRGGBB");
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> enumType,
            String value,
            String field
    ) {
        for (E constant : enumType.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(
                field + " 的值无效: " + value + "，可选值: "
                        + List.of(enumType.getEnumConstants()));
    }

    private record SeriesBinding(
            String name,
            List<?> xData,
            List<? extends Number> yData,
            Map<String, Object> config
    ) {
        private SeriesBinding {
            Objects.requireNonNull(name);
        }
    }

    private record PieBinding(
            String name,
            Number value,
            Map<String, Object> config
    ) {
    }
}
