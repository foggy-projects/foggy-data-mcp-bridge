package com.foggyframework.dataset.mcp.service;

import com.foggyframework.dataset.mcp.config.McpProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolConfigLoader 合并策略单元测试
 *
 * <p>验证内置默认工具与 YAML 覆盖的合并行为，
 * 确保 Spring Boot list 替换行为不会导致工具丢失。
 */
class ToolConfigLoaderTest {

    @Test
    @DisplayName("getBuiltinDefaults 应返回 9 个内置工具")
    void testBuiltinDefaults_ShouldReturn8Tools() {
        List<McpProperties.ToolConfigItem> defaults = ToolConfigLoader.getBuiltinDefaults();

        assertEquals(10, defaults.size());

        // 验证每个工具都有完整配置
        for (McpProperties.ToolConfigItem tool : defaults) {
            assertNotNull(tool.getName(), "Tool name should not be null");
            assertNotNull(tool.getDescriptionFile(), "descriptionFile should not be null for " + tool.getName());
            assertNotNull(tool.getSchemaFile(), "schemaFile should not be null for " + tool.getName());
            assertNotNull(tool.getCategory(), "category should not be null for " + tool.getName());
        }
    }

    @Test
    @DisplayName("getBuiltinDefaults 应包含核心工具名称")
    void testBuiltinDefaults_ShouldContainCoreTools() {
        List<McpProperties.ToolConfigItem> defaults = ToolConfigLoader.getBuiltinDefaults();
        List<String> names = defaults.stream().map(McpProperties.ToolConfigItem::getName).toList();

        assertTrue(names.contains("dataset.get_metadata"), "Should contain get_metadata");
        assertTrue(names.contains("dataset.query_model"), "Should contain query_model");
        assertTrue(names.contains("dataset.describe_model_internal"), "Should contain describe_model_internal");
        assertTrue(names.contains("dataset.export_with_chart"), "Should contain export_with_chart");
        assertTrue(names.contains("dataset.inspect_table"), "Should contain inspect_table");
        assertTrue(names.contains("dataset.open_in_viewer"), "Should contain open_in_viewer");
        assertTrue(names.contains("dataset_nl.query"), "Should contain nl.query");
        assertTrue(names.contains("chart.generate"), "Should contain chart.generate");
        assertTrue(names.contains("dataset.list_models"), "Should contain list_models");
        assertTrue(names.contains("dataset.compose_query"), "Should contain compose_query");
    }

    @Test
    @DisplayName("getBuiltinDefaults chart.generate 和 inspect_table 默认禁用")
    void testBuiltinDefaults_ChartAndInspectDisabledByDefault() {
        List<McpProperties.ToolConfigItem> defaults = ToolConfigLoader.getBuiltinDefaults();

        for (McpProperties.ToolConfigItem tool : defaults) {
            if ("chart.generate".equals(tool.getName()) || "dataset.inspect_table".equals(tool.getName())) {
                assertFalse(tool.isEnabled(), tool.getName() + " should be disabled by default");
            } else {
                assertTrue(tool.isEnabled(), tool.getName() + " should be enabled by default");
            }
        }
    }

    @Test
    @DisplayName("每次调用 getBuiltinDefaults 应返回新实例（不互相影响）")
    void testBuiltinDefaults_ShouldReturnNewInstances() {
        List<McpProperties.ToolConfigItem> first = ToolConfigLoader.getBuiltinDefaults();
        List<McpProperties.ToolConfigItem> second = ToolConfigLoader.getBuiltinDefaults();

        assertNotSame(first, second);
        // 修改第一个不影响第二个
        first.get(0).setEnabled(false);
        assertTrue(second.get(0).isEnabled());
    }

    /**
     * 模拟 application-lite.yml 场景：
     * YAML 只配了 1 个 disabled 条目，验证合并后其他 7 个默认工具不丢失
     *
     * <p>这是之前的 BUG 场景：Spring Boot 用 lite profile 的 tools 列表
     * 整体替换主 application.yml 的 8 个工具，导致只剩 1 个。
     */
    @Test
    @DisplayName("lite profile: YAML 只禁用 open_in_viewer，其他 8 个默认工具应保留")
    void testLiteProfileScenario_ShouldKeepAllDefaultsExceptDisabled() {
        // 模拟 application-lite.yml 的 tools 配置
        McpProperties props = new McpProperties();
        McpProperties.ToolConfigItem disableViewer = new McpProperties.ToolConfigItem();
        disableViewer.setName("dataset.open_in_viewer");
        disableViewer.setEnabled(false);
        props.getTools().add(disableViewer);

        // 执行合并
        simulateMerge(props);

        // 验证结果
        assertEquals(10, props.getTools().size(), "Should still have 10 tools after merge");

        // open_in_viewer 应该被禁用
        McpProperties.ToolConfigItem viewer = findTool(props, "dataset.open_in_viewer");
        assertNotNull(viewer);
        assertFalse(viewer.isEnabled(), "open_in_viewer should be disabled");
        // 但仍然有 description 和 schema（来自默认值）
        assertNotNull(viewer.getDescriptionFile(), "Should inherit default descriptionFile");
        assertNotNull(viewer.getSchemaFile(), "Should inherit default schemaFile");

        // 其他工具应该正常存在且有完整配置
        McpProperties.ToolConfigItem metadata = findTool(props, "dataset.get_metadata");
        assertNotNull(metadata, "get_metadata should exist");
        assertTrue(metadata.isEnabled());
        assertNotNull(metadata.getDescriptionFile());

        McpProperties.ToolConfigItem queryModel = findTool(props, "dataset.query_model");
        assertNotNull(queryModel, "query_model should exist");
        assertTrue(queryModel.isEnabled());
        assertNotNull(queryModel.getDescriptionFile());
    }

    @Test
    @DisplayName("YAML 为空（无任何 tools 配置），应使用全部默认值")
    void testEmptyYaml_ShouldUseAllDefaults() {
        McpProperties props = new McpProperties();
        // tools 列表为空

        simulateMerge(props);

        assertEquals(10, props.getTools().size());
        for (McpProperties.ToolConfigItem tool : props.getTools()) {
            assertNotNull(tool.getDescriptionFile(), "Should have descriptionFile for " + tool.getName());
            assertNotNull(tool.getSchemaFile(), "Should have schemaFile for " + tool.getName());
        }
    }

    @Test
    @DisplayName("YAML 覆盖 descriptionFile 时应使用 YAML 值，其他字段保持默认")
    void testYamlOverrideDescription_ShouldUseYamlValue() {
        McpProperties props = new McpProperties();
        McpProperties.ToolConfigItem customDesc = new McpProperties.ToolConfigItem();
        customDesc.setName("dataset.get_metadata");
        customDesc.setDescriptionFile("classpath:/custom/my_metadata.md");
        props.getTools().add(customDesc);

        simulateMerge(props);

        McpProperties.ToolConfigItem merged = findTool(props, "dataset.get_metadata");
        assertNotNull(merged);
        assertEquals("classpath:/custom/my_metadata.md", merged.getDescriptionFile());
        // schemaFile 应保持默认
        assertNotNull(merged.getSchemaFile());
        assertTrue(merged.getSchemaFile().contains("get_metadata_schema.json"));
    }

    @Test
    @DisplayName("YAML 中额外定义的非内置工具应追加到列表末尾")
    void testYamlExtraTools_ShouldBeAppended() {
        McpProperties props = new McpProperties();
        McpProperties.ToolConfigItem customTool = new McpProperties.ToolConfigItem();
        customTool.setName("custom.my_tool");
        customTool.setDescriptionFile("classpath:/custom/tool.md");
        customTool.setSchemaFile("classpath:/custom/tool_schema.json");
        customTool.setCategory("CUSTOM");
        props.getTools().add(customTool);

        simulateMerge(props);

        assertEquals(11, props.getTools().size(), "10 defaults + 1 custom");
        McpProperties.ToolConfigItem last = props.getTools().get(10);
        assertEquals("custom.my_tool", last.getName());
        assertEquals("classpath:/custom/tool.md", last.getDescriptionFile());
    }

    // ==================== 辅助方法 ====================

    /**
     * 模拟 mergeWithDefaults 逻辑（不需要 Spring 上下文）
     */
    private void simulateMerge(McpProperties props) {
        List<McpProperties.ToolConfigItem> yamlOverrides = new ArrayList<>(props.getTools());
        java.util.LinkedHashMap<String, McpProperties.ToolConfigItem> overrideMap = new java.util.LinkedHashMap<>();
        for (McpProperties.ToolConfigItem item : yamlOverrides) {
            if (item.getName() != null) {
                overrideMap.put(item.getName(), item);
            }
        }

        List<McpProperties.ToolConfigItem> merged = new ArrayList<>();
        for (McpProperties.ToolConfigItem defaultTool : ToolConfigLoader.getBuiltinDefaults()) {
            McpProperties.ToolConfigItem override = overrideMap.remove(defaultTool.getName());
            if (override != null) {
                defaultTool.setEnabled(override.isEnabled());
                if (override.getDescriptionFile() != null) {
                    defaultTool.setDescriptionFile(override.getDescriptionFile());
                }
                if (override.getSchemaFile() != null) {
                    defaultTool.setSchemaFile(override.getSchemaFile());
                }
                if (override.getCategory() != null) {
                    defaultTool.setCategory(override.getCategory());
                }
            }
            merged.add(defaultTool);
        }
        merged.addAll(overrideMap.values());

        props.getTools().clear();
        props.getTools().addAll(merged);
    }

    private McpProperties.ToolConfigItem findTool(McpProperties props, String name) {
        return props.getTools().stream()
                .filter(t -> name.equals(t.getName()))
                .findFirst()
                .orElse(null);
    }
}
