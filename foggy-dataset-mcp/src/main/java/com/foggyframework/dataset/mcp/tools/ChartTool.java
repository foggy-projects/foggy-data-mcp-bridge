package com.foggyframework.dataset.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.config.McpProperties;
import com.foggyframework.dataset.mcp.enums.ToolCategory;
import com.foggyframework.dataset.mcp.service.ProgressEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 图表生成工具 - 对接 chart-render-service
 *
 * 功能：
 * - 生成各种类型的图表（线图、柱图、饼图等）
 * - 支持流式 API 避免 Base64 超长问题
 * - 智能降级策略
 */
@Slf4j
@Component
public class ChartTool implements McpTool {

    private final WebClient chartRenderWebClient;
    private final McpProperties mcpProperties;
    private final ObjectMapper objectMapper;

    public ChartTool(
            @Qualifier("chartRenderWebClient") WebClient chartRenderWebClient,
            McpProperties mcpProperties,
            ObjectMapper objectMapper
    ) {
        this.chartRenderWebClient = chartRenderWebClient;
        this.mcpProperties = mcpProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "chart.generate";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        // 数据可视化工具，适合数据分析师
        return EnumSet.of(ToolCategory.VISUALIZATION);
    }

    // 注意：getDescription() 和 getInputSchema() 从配置文件加载，不再硬编码

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> arguments, String traceId, String authorization) {
        String chartType = (String) arguments.get("type");
        String title = (String) arguments.getOrDefault("title", "数据图表");
        List<Map<String, Object>> data = (List<Map<String, Object>>) arguments.get("data");
        String xField = (String) arguments.get("xField");
        String yField = (String) arguments.get("yField");
        String seriesField = (String) arguments.get("seriesField");
        int width = (int) arguments.getOrDefault("width", 800);
        int height = (int) arguments.getOrDefault("height", 600);
        String format = (String) arguments.getOrDefault("format", "png");

        if (data == null || data.isEmpty()) {
            return errorResponse("数据不能为空");
        }

        log.info("Generating chart: type={}, title={}, dataSize={}, traceId={}",
                chartType, title, data.size(), traceId);

        try {
            // 构建渲染请求
            Map<String, Object> renderRequest = buildRenderRequest(
                    chartType, title, data, xField, yField, seriesField, width, height, format
            );

            // 使用流式 API 生成图表
            byte[] imageBytes = generateChartStream(renderRequest, traceId, authorization);

            if (imageBytes == null || imageBytes.length == 0) {
                return errorResponse("图表生成失败：未返回数据");
            }

            // 保存到临时文件（或上传到 OSS）
            String imageUrl = saveChartImage(imageBytes, format, traceId);

            log.info("Chart generated successfully: url={}, size={}KB, traceId={}",
                    imageUrl, imageBytes.length / 1024, traceId);

            return Map.of(
                    "success", true,
                    "chart", Map.of(
                            "url", imageUrl,
                            "type", chartType.toUpperCase(),
                            "title", title,
                            "format", format.toUpperCase(),
                            "width", width,
                            "height", height,
                            "fileSize", imageBytes.length
                    )
            );

        } catch (Exception e) {
            log.error("Chart generation failed: {}, traceId={}", e.getMessage(), traceId, e);
            return errorResponse("图表生成失败: " + e.getMessage());
        }
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public Flux<ProgressEvent> executeWithProgress(Map<String, Object> arguments, String traceId, String authorization) {
        return Flux.create(sink -> {
            try {
                sink.next(ProgressEvent.progress("preparing", 10));

                Object result = execute(arguments, traceId, authorization);

                sink.next(ProgressEvent.progress("rendering", 50));
                sink.next(ProgressEvent.progress("saving", 80));
                sink.next(ProgressEvent.complete(result));
                sink.complete();

            } catch (Exception e) {
                sink.next(ProgressEvent.error("CHART_ERROR", e.getMessage()));
                sink.complete();
            }
        });
    }

    /**
     * 构建渲染请求
     */
    private Map<String, Object> buildRenderRequest(
            String chartType, String title, List<Map<String, Object>> data,
            String xField, String yField, String seriesField,
            int width, int height, String format
    ) {
        Map<String, Object> request = new LinkedHashMap<>();

        // 统一配置
        Map<String, Object> unified = new LinkedHashMap<>();
        unified.put("type", chartType.toLowerCase());
        unified.put("title", title);

        // 字段映射
        if (xField != null) {
            unified.put("xField", xField);
        }
        if (yField != null) {
            // 饼图使用 valueField
            if ("pie".equalsIgnoreCase(chartType)) {
                unified.put("valueField", yField);
                if (xField != null) {
                    unified.put("nameField", xField);
                }
            } else {
                unified.put("yField", yField);
            }
        }
        if (seriesField != null) {
            unified.put("seriesField", seriesField);
        }

        request.put("unified", unified);
        request.put("data", data);

        // 图片配置
        request.put("image", Map.of(
                "format", format.toLowerCase(),
                "width", width,
                "height", height
        ));

        return request;
    }

    /**
     * 使用流式 API 生成图表
     */
    private byte[] generateChartStream(Map<String, Object> renderRequest, String traceId, String authorization) {
        try {
            // 调用流式端点，直接获取字节数组
            WebClient.RequestHeadersSpec<?> request = chartRenderWebClient.post()
                    .uri("/render/unified/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Request-Id", traceId)
                    .bodyValue(renderRequest);

            // 传递 Authorization 头
            if (authorization != null && !authorization.isBlank()) {
                request = request.header("Authorization", authorization);
            }

            byte[] imageBytes = request
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            return imageBytes != null ? imageBytes : new byte[0];

        } catch (WebClientResponseException e) {
            log.error("Chart render service error: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("图表渲染服务错误: " + e.getMessage());
        }
    }

    /**
     * 保存图表图片
     *
     * TODO: 实际项目中应上传到 OSS/OBS
     */
    private String saveChartImage(byte[] imageBytes, String format, String traceId) {
        try {
            // 创建临时目录
            Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "mcp-charts");
            Files.createDirectories(tempDir);

            // 生成文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("chart_%s_%s.%s", timestamp, traceId.substring(0, 8), format);
            Path filePath = tempDir.resolve(fileName);

            // 保存文件
            Files.write(filePath, imageBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("Chart saved to: {}", filePath);

            // 返回本地路径（实际应返回 OSS URL）
            return "file://" + filePath.toAbsolutePath();

        } catch (Exception e) {
            log.error("Failed to save chart image: {}", e.getMessage(), e);
            // 返回 Base64 作为备选
            return "data:image/" + format + ";base64," +
                   Base64.getEncoder().encodeToString(imageBytes);
        }
    }

    private Map<String, Object> errorResponse(String message) {
        return Map.of(
                "success", false,
                "error", true,
                "message", message
        );
    }
}
