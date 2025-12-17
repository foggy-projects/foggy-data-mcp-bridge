package com.foggyframework.dataset.mcp.base;

import com.foggyframework.dataset.mcp.enums.ToolCategory;
import com.foggyframework.dataset.mcp.tools.McpTool;

import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Mock 工具工厂
 *
 * 提供各种预配置的 Mock 工具对象
 */
public class MockToolFactory {

    /**
     * 创建元数据工具 Mock
     */
    public static McpTool createMetadataTool() {
        McpTool tool = mock(McpTool.class);
        when(tool.getName()).thenReturn("dataset.get_metadata");
        when(tool.getDescription()).thenReturn("获取用户级元数据包");
        when(tool.getCategories()).thenReturn(EnumSet.of(ToolCategory.METADATA));
        when(tool.getInputSchema()).thenReturn(Map.of(
                "type", "object",
                "properties", Map.of()
        ));
        when(tool.execute(any(), any(), any())).thenReturn(Map.of(
                "models", List.of(
                        Map.of("name", "FactSalesModel", "caption", "销售事实表")
                )
        ));
        return tool;
    }

    /**
     * 创建模型描述工具 Mock
     */
    public static McpTool createDescribeModelTool() {
        McpTool tool = mock(McpTool.class);
        when(tool.getName()).thenReturn("dataset.description_model_internal");
        when(tool.getDescription()).thenReturn("获取模型详细字段信息");
        when(tool.getCategories()).thenReturn(EnumSet.of(ToolCategory.METADATA));
        when(tool.getInputSchema()).thenReturn(Map.of(
                "type", "object",
                "properties", Map.of(
                        "model", Map.of("type", "string", "description", "模型名称")
                ),
                "required", List.of("model")
        ));
        return tool;
    }

    /**
     * 创建查询工具 Mock
     */
    public static McpTool createQueryModelTool() {
        McpTool tool = mock(McpTool.class);
        when(tool.getName()).thenReturn("dataset.query_model_v2");
        when(tool.getDescription()).thenReturn("执行数据查询");
        when(tool.getCategories()).thenReturn(EnumSet.of(ToolCategory.QUERY));
        when(tool.getInputSchema()).thenReturn(Map.of(
                "type", "object",
                "properties", Map.of(
                        "model", Map.of("type", "string"),
                        "payload", Map.of("type", "object")
                ),
                "required", List.of("model", "payload")
        ));
        return tool;
    }

    /**
     * 创建自然语言查询工具 Mock
     */
    public static McpTool createNLQueryTool() {
        McpTool tool = mock(McpTool.class);
        when(tool.getName()).thenReturn("dataset_nl.query");
        when(tool.getDescription()).thenReturn("智能自然语言查询");
        when(tool.getCategories()).thenReturn(EnumSet.of(ToolCategory.NATURAL_LANGUAGE));
        when(tool.supportsStreaming()).thenReturn(true);
        when(tool.getInputSchema()).thenReturn(Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "自然语言问题")
                ),
                "required", List.of("query")
        ));
        return tool;
    }

    /**
     * 创建图表工具 Mock
     */
    public static McpTool createChartTool() {
        McpTool tool = mock(McpTool.class);
        when(tool.getName()).thenReturn("chart.generate");
        when(tool.getDescription()).thenReturn("生成数据可视化图表");
        when(tool.getCategories()).thenReturn(EnumSet.of(ToolCategory.VISUALIZATION));
        when(tool.supportsStreaming()).thenReturn(true);
        when(tool.getInputSchema()).thenReturn(Map.of(
                "type", "object",
                "properties", Map.of(
                        "type", Map.of("type", "string"),
                        "data", Map.of("type", "array")
                ),
                "required", List.of("type", "data")
        ));
        return tool;
    }

    /**
     * 创建导出工具 Mock (多分类)
     */
    public static McpTool createExportWithChartTool() {
        McpTool tool = mock(McpTool.class);
        when(tool.getName()).thenReturn("dataset.export_with_chart");
        when(tool.getDescription()).thenReturn("查询并生成图表导出");
        when(tool.getCategories()).thenReturn(EnumSet.of(
                ToolCategory.QUERY,
                ToolCategory.VISUALIZATION,
                ToolCategory.EXPORT
        ));
        when(tool.supportsStreaming()).thenReturn(true);
        return tool;
    }

    /**
     * 创建所有标准工具的列表
     */
    public static List<McpTool> createAllStandardTools() {
        return List.of(
                createMetadataTool(),
                createDescribeModelTool(),
                createQueryModelTool(),
                createNLQueryTool(),
                createChartTool(),
                createExportWithChartTool()
        );
    }

    /**
     * 创建仅 METADATA 分类的工具列表
     */
    public static List<McpTool> createMetadataTools() {
        return List.of(
                createMetadataTool(),
                createDescribeModelTool()
        );
    }

    /**
     * 创建仅 QUERY 分类的工具列表
     */
    public static List<McpTool> createQueryTools() {
        return List.of(
                createQueryModelTool()
        );
    }

    /**
     * 创建仅 NATURAL_LANGUAGE 分类的工具列表
     */
    public static List<McpTool> createNLTools() {
        return List.of(
                createNLQueryTool()
        );
    }

    /**
     * 创建会抛出异常的工具 Mock
     */
    public static McpTool createFailingTool(String name, RuntimeException exception) {
        McpTool tool = mock(McpTool.class);
        when(tool.getName()).thenReturn(name);
        when(tool.getDescription()).thenReturn("Failing tool: " + name);
        when(tool.getCategories()).thenReturn(EnumSet.of(ToolCategory.QUERY));
        when(tool.execute(any(), any(), any())).thenThrow(exception);
        return tool;
    }

    /**
     * 创建返回指定结果的工具 Mock
     */
    public static McpTool createToolWithResult(String name, ToolCategory category, Object result) {
        McpTool tool = mock(McpTool.class);
        when(tool.getName()).thenReturn(name);
        when(tool.getDescription()).thenReturn("Tool: " + name);
        when(tool.getCategories()).thenReturn(EnumSet.of(category));
        when(tool.getInputSchema()).thenReturn(Map.of("type", "object"));
        when(tool.execute(any(), any(), any())).thenReturn(result);
        return tool;
    }

    /**
     * 创建无分类的工具 Mock（边界测试用）
     */
    public static McpTool createToolWithNoCategories(String name) {
        McpTool tool = mock(McpTool.class);
        when(tool.getName()).thenReturn(name);
        when(tool.getDescription()).thenReturn("Tool with no categories");
        when(tool.getCategories()).thenReturn(Collections.emptySet());
        return tool;
    }

    /**
     * 创建返回 null 分类的工具 Mock（边界测试用）
     */
    public static McpTool createToolWithNullCategories(String name) {
        McpTool tool = mock(McpTool.class);
        when(tool.getName()).thenReturn(name);
        when(tool.getDescription()).thenReturn("Tool with null categories");
        when(tool.getCategories()).thenReturn(null);
        return tool;
    }
}
