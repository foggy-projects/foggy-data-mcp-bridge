package com.foggyframework.dataset.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.jdbc.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.mcp.enums.ToolCategory;
import com.foggyframework.dataset.mcp.service.ProgressEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * 导出并生成图表工具
 *
 * 组合查询、图表生成功能（不含 Excel）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExportWithChartTool implements McpTool {

    private final QueryModelTool queryModelTool;
    private final ChartTool chartTool;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "dataset.export_with_chart";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        // 组合工具，包含查询和可视化，也包含导出功能
        return EnumSet.of(ToolCategory.QUERY, ToolCategory.VISUALIZATION, ToolCategory.EXPORT);
    }

    // 注意：getDescription() 和 getInputSchema() 从配置文件加载，不再硬编码

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> arguments, String traceId, String authorization) {
        String model = (String) arguments.get("model");
        Map<String, Object> payload = (Map<String, Object>) arguments.get("payload");
        Map<String, Object> chartConfig = (Map<String, Object>) arguments.getOrDefault("chart", new HashMap<>());

        log.info("Export with chart: model={}, traceId={}", model, traceId);

        try {
            // 1. 执行查询（使用强类型方法）
            RX<SemanticQueryResponse> queryResult = queryModelTool.executeQuery(
                    model, payload, "execute", traceId, authorization);

            if (!queryResult.isOk()) {
                return queryResult; // 返回查询错误
            }

            SemanticQueryResponse queryResponse = queryResult.getData();

            // 2. 提取查询数据
            List<Map<String, Object>> items = queryResponse.getItems();

            if (items == null || items.isEmpty()) {
                return buildResponse(queryResponse, null, "查询结果为空，无法生成图表");
            }

            // 3. 确定图表配置
            String chartType = (String) chartConfig.getOrDefault("type", "auto");
            if ("auto".equals(chartType)) {
                chartType = inferChartType(payload, items);
            }

            String xField = (String) chartConfig.get("xField");
            String yField = (String) chartConfig.get("yField");

            // 自动推断字段
            if (xField == null || yField == null) {
                Map<String, String> inferredFields = inferFields(payload, items);
                if (xField == null) xField = inferredFields.get("xField");
                if (yField == null) yField = inferredFields.get("yField");
            }

            String title = (String) chartConfig.getOrDefault("title",
                    generateChartTitle(model, chartType));
            int width = (int) chartConfig.getOrDefault("width", 800);
            int height = (int) chartConfig.getOrDefault("height", 600);

            // 4. 生成图表
            Map<String, Object> chartArgs = new HashMap<>();
            chartArgs.put("type", chartType);
            chartArgs.put("title", title);
            chartArgs.put("data", items);
            chartArgs.put("xField", xField);
            chartArgs.put("yField", yField);
            chartArgs.put("width", width);
            chartArgs.put("height", height);

            Object chartResult = chartTool.execute(chartArgs, traceId, authorization);

            // 5. 组装返回结果
            return buildResponse(queryResponse, chartResult, "查询和图表生成完成");

        } catch (Exception e) {
            log.error("Export with chart failed: {}, traceId={}", e.getMessage(), traceId, e);
            return RX.failB("导出失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<ProgressEvent> executeWithProgress(Map<String, Object> arguments, String traceId, String authorization) {
        return Flux.create(sink -> {
            try {
                sink.next(ProgressEvent.progress("querying", 20));

                // 执行查询部分
                String model = (String) arguments.get("model");
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) arguments.get("payload");

                RX<SemanticQueryResponse> queryResult = queryModelTool.executeQuery(
                        model, payload, "execute", traceId, authorization);

                if (!queryResult.isOk()) {
                    sink.next(ProgressEvent.error("QUERY_ERROR", queryResult.getMsg()));
                    sink.complete();
                    return;
                }

                sink.next(ProgressEvent.partialResult(Map.of("query", "completed")));
                sink.next(ProgressEvent.progress("rendering_chart", 50));

                // 执行完整逻辑
                Object result = execute(arguments, traceId, authorization);

                sink.next(ProgressEvent.progress("finalizing", 90));
                sink.next(ProgressEvent.complete(result));
                sink.complete();

            } catch (Exception e) {
                sink.next(ProgressEvent.error("EXPORT_ERROR", e.getMessage()));
                sink.complete();
            }
        });
    }

    /**
     * 根据查询参数推断图表类型
     */
    @SuppressWarnings("unchecked")
    private String inferChartType(Map<String, Object> payload, List<Map<String, Object>> items) {
        List<?> groupBy = (List<?>) payload.get("groupBy");

        // 有分组 -> 考虑饼图或柱图
        if (groupBy != null && !groupBy.isEmpty()) {
            if (items.size() <= 8) {
                return "pie"; // 少量分类用饼图
            }
            return "bar"; // 较多分类用柱图
        }

        // 时间序列 -> 线图
        if (hasTimeField(payload)) {
            return "line";
        }

        // 默认柱图
        return "bar";
    }

    /**
     * 检查是否有时间字段
     */
    @SuppressWarnings("unchecked")
    private boolean hasTimeField(Map<String, Object> payload) {
        List<String> columns = (List<String>) payload.get("columns");
        if (columns != null) {
            return columns.stream().anyMatch(c ->
                    c.contains("date") || c.contains("time") || c.contains("Date") || c.contains("Time")
            );
        }
        return false;
    }

    /**
     * 推断 X/Y 字段
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> inferFields(Map<String, Object> payload, List<Map<String, Object>> items) {
        Map<String, String> fields = new HashMap<>();

        List<?> groupBy = (List<?>) payload.get("groupBy");
        List<String> columns = (List<String>) payload.get("columns");

        // X 轴优先使用分组字段（非聚合字段）
        if (groupBy != null && !groupBy.isEmpty()) {
            String xField = findFirstNonAggField(groupBy);
            if (xField != null) {
                fields.put("xField", xField);
            }
        }
        // 如果没有从 groupBy 找到，使用 columns 的第一个
        if (!fields.containsKey("xField") && columns != null && !columns.isEmpty()) {
            fields.put("xField", columns.get(0));
        }

        // Y 轴使用数值字段
        if (items != null && !items.isEmpty()) {
            Map<String, Object> firstItem = items.get(0);
            for (Map.Entry<String, Object> entry : firstItem.entrySet()) {
                if (entry.getValue() instanceof Number &&
                    !entry.getKey().equals(fields.get("xField"))) {
                    fields.put("yField", entry.getKey());
                    break;
                }
            }
        }

        return fields;
    }

    /**
     * 从 groupBy 列表中找到第一个非聚合字段
     * groupBy 可以是:
     * - List<String>: ["field1", "field2"]
     * - List<Map>: [{"field": "salesDate$year"}, {"field": "salesAmount", "agg": "SUM"}]
     */
    @SuppressWarnings("unchecked")
    private String findFirstNonAggField(List<?> groupBy) {
        for (Object item : groupBy) {
            if (item instanceof String) {
                // 简化格式：直接是字段名
                return (String) item;
            } else if (item instanceof Map) {
                // 完整格式：{field: "xxx", agg: "SUM"}
                Map<String, Object> groupByItem = (Map<String, Object>) item;
                String agg = (String) groupByItem.get("agg");
                // 没有 agg 的是维度字段，适合作为 X 轴
                if (agg == null || agg.isBlank()) {
                    return (String) groupByItem.get("field");
                }
            }
        }
        return null;
    }

    /**
     * 生成图表标题
     */
    private String generateChartTitle(String model, String chartType) {
        String typeName = switch (chartType) {
            case "line" -> "趋势图";
            case "bar" -> "柱状图";
            case "pie" -> "占比图";
            case "scatter" -> "散点图";
            case "area" -> "面积图";
            default -> "数据图表";
        };
        return model + " " + typeName;
    }

    /**
     * 构建响应
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildResponse(
            SemanticQueryResponse queryResponse,
            Object chartResult,
            String summary
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "result");

        // 查询结果
        List<Map<String, Object>> items = queryResponse.getItems();
        response.put("items", items != null ? items : List.of());
        response.put("total", queryResponse.getTotal() != null ? queryResponse.getTotal() : 0);
        response.put("summary", summary);

        // 导出信息
        Map<String, Object> exports = new LinkedHashMap<>();

        // 图表信息
        if (chartResult instanceof Map) {
            Map<String, Object> chartMap = (Map<String, Object>) chartResult;
            if (Boolean.TRUE.equals(chartMap.get("success"))) {
                Object chartInfo = chartMap.get("chart");
                exports.put("charts", List.of(chartInfo));
            }
        }

        response.put("exports", exports);

        return response;
    }
}
