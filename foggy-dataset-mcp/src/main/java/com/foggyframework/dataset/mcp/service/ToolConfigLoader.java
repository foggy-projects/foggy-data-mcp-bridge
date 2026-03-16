package com.foggyframework.dataset.mcp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.mcp.config.McpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具配置加载器
 * <p>
 * 从classpath加载工具描述和JSON Schema：
 * - 描述文件 (*.md) -> 完整描述内容
 * - Schema文件 (*.json) -> 输入参数定义
 */
@Slf4j
@Component
public class ToolConfigLoader {
    private final SystemBundlesContext systemBundlesContext;
    private final QueryModelLoader queryModelLoader;

    private final McpProperties mcpProperties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    /**
     * 工具名称 -> 完整描述内容
     */
    private final Map<String, String> descriptionCache = new LinkedHashMap<>();

    /**
     * 工具名称 -> JSON Schema
     */
    private final Map<String, Map<String, Object>> schemaCache = new LinkedHashMap<>();

    public ToolConfigLoader(McpProperties mcpProperties, ResourceLoader resourceLoader, ObjectMapper objectMapper, SystemBundlesContext systemBundlesContext, QueryModelLoader queryModelLoader) {
        this.mcpProperties = mcpProperties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.systemBundlesContext = systemBundlesContext;
        this.queryModelLoader = queryModelLoader;
    }

    @PostConstruct
    public void init() {
        loadAllConfigurations();
    }

    /**
     * 加载所有工具配置
     *
     * <p>合并策略：始终以内置默认工具为基础，再叠加 YAML 中的覆盖配置。
     * 这解决了 Spring Boot 对 list 属性采用"整体替换"导致 profile（如 lite）
     * 只配了一个 disabled 条目就把其余默认工具全部丢失的问题。
     */
    private void loadAllConfigurations() {
        log.info("Loading tool configurations from classpath...");
        mergeWithDefaults();
        // useAllModels 为三态逻辑，null 时在运行时根据 model-list 自动推断
        // 这里仅记录日志，不修改配置值
        if (mcpProperties.getSemantic().getUseAllModels() == null) {
            if (mcpProperties.getSemantic().getModelList() == null || mcpProperties.getSemantic().getModelList().isEmpty()) {
                log.info("No semantic model configured, will use dynamic model discovery at runtime");
            } else {
                log.info("Using configured model-list: {}", mcpProperties.getSemantic().getModelList());
            }
        } else if (Boolean.TRUE.equals(mcpProperties.getSemantic().getUseAllModels())) {
            log.info("Dynamic model discovery explicitly enabled (useAllModels=true)");
        } else {
            log.info("Model discovery explicitly disabled (useAllModels=false)");
        }
        for (McpProperties.ToolConfigItem item : mcpProperties.getTools()) {
            String toolName = item.getName();

            // 加载描述文件
            if (item.getDescriptionFile() != null) {
                try {
                    String description = loadResourceAsString(item.getDescriptionFile());
                    descriptionCache.put(toolName, description);
                    log.debug("Loaded description for tool: {}", toolName);
                } catch (Exception e) {
                    log.warn("Failed to load description file for tool {}: {}", toolName, e.getMessage());
                }
            }

            // 加载Schema文件
            if (item.getSchemaFile() != null) {
                try {
                    Map<String, Object> schema = loadResourceAsJson(item.getSchemaFile());
                    schemaCache.put(toolName, schema);
                    log.debug("Loaded schema for tool: {}", toolName);
                } catch (Exception e) {
                    log.warn("Failed to load schema file for tool {}: {}", toolName, e.getMessage());
                }
            }
        }

        log.info("Loaded configurations for {} tools (descriptions: {}, schemas: {})",
                mcpProperties.getTools().size(), descriptionCache.size(), schemaCache.size());
    }

    /**
     * 获取内置默认工具列表
     */
    static List<McpProperties.ToolConfigItem> getBuiltinDefaults() {
        List<McpProperties.ToolConfigItem> defaults = new ArrayList<>();
        defaults.add(createToolConfig("dataset_nl.query", "classpath:/schemas/descriptions/dataset_nl_query.md", "classpath:/schemas/dataset_nl_query_schema.json", "NATURAL_LANGUAGE"));
        defaults.add(createToolConfig("dataset.get_metadata", "classpath:/schemas/descriptions/get_metadata.md", "classpath:/schemas/get_metadata_schema.json", "METADATA"));
        defaults.add(createToolConfig("dataset.describe_model_internal", "classpath:/schemas/descriptions/describe_model_internal.md", "classpath:/schemas/describe_model_internal_schema.json", "METADATA"));
        defaults.add(createToolConfig("dataset.query_model", "classpath:/schemas/descriptions/query_model_v3.md", "classpath:/schemas/query_model_v3_schema.json", "QUERY"));
        defaults.add(createToolConfig("chart.generate", "classpath:/schemas/descriptions/generate_chart.md", "classpath:/schemas/generate_chart_schema.json", "VISUALIZATION", false));
        defaults.add(createToolConfig("dataset.export_with_chart", "classpath:/schemas/descriptions/export_with_chart.md", "classpath:/schemas/export_with_chart_schema.json", "EXPORT"));
        defaults.add(createToolConfig("dataset.inspect_table", "classpath:/schemas/descriptions/inspect_table.md", "classpath:/schemas/inspect_table_schema.json", "ADMIN", false));
        defaults.add(createToolConfig("dataset.open_in_viewer", "classpath:/schemas/descriptions/open_in_viewer.md", "classpath:/schemas/open_in_viewer_schema.json", "EXPORT"));
        defaults.add(createToolConfig("dataset.compose_query", "classpath:/schemas/descriptions/compose_query.md", "classpath:/schemas/compose_query_schema.json", "QUERY"));
        return defaults;
    }

    /**
     * 合并策略：以内置默认工具为基础，叠加 YAML 覆盖
     *
     * <p>Spring Boot 对 list 属性采用整体替换策略，导致 profile 中只要配了
     * {@code foggy.mcp.tools} 的任意条目，就会覆盖掉主 application.yml 中的全部工具。
     * 此方法始终先加载 8 个内置默认工具，再将 YAML 中的同名条目合并覆盖上去（仅覆盖已设置的字段）。
     */
    private void mergeWithDefaults() {
        List<McpProperties.ToolConfigItem> yamlOverrides = new ArrayList<>(mcpProperties.getTools());
        Map<String, McpProperties.ToolConfigItem> overrideMap = new LinkedHashMap<>();
        for (McpProperties.ToolConfigItem item : yamlOverrides) {
            if (item.getName() != null) {
                overrideMap.put(item.getName(), item);
            }
        }

        List<McpProperties.ToolConfigItem> merged = new ArrayList<>();
        for (McpProperties.ToolConfigItem defaultTool : getBuiltinDefaults()) {
            McpProperties.ToolConfigItem override = overrideMap.remove(defaultTool.getName());
            if (override != null) {
                // YAML 中有同名覆盖：只覆盖 YAML 中显式设置的字段
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
                log.info("Tool '{}' overridden by YAML config (enabled={})", defaultTool.getName(), defaultTool.isEnabled());
            }
            merged.add(defaultTool);
        }

        // 追加 YAML 中额外定义的非内置工具
        for (McpProperties.ToolConfigItem extra : overrideMap.values()) {
            merged.add(extra);
            log.info("Additional tool from YAML: '{}'", extra.getName());
        }

        mcpProperties.getTools().clear();
        mcpProperties.getTools().addAll(merged);

        if (yamlOverrides.isEmpty()) {
            log.info("No YAML tool overrides, using {} builtin defaults", merged.size());
        } else {
            log.info("Merged {} builtin defaults with {} YAML overrides -> {} tools total",
                    getBuiltinDefaults().size(), yamlOverrides.size(), merged.size());
        }
    }

    /**
     * 创建工具配置项
     */
    private static McpProperties.ToolConfigItem createToolConfig(String name, String descriptionFile, String schemaFile, String category) {
        return createToolConfig(name, descriptionFile, schemaFile, category, true);
    }

    /**
     * 创建工具配置项
     */
    private static McpProperties.ToolConfigItem createToolConfig(String name, String descriptionFile, String schemaFile, String category, boolean enabled) {
        McpProperties.ToolConfigItem item = new McpProperties.ToolConfigItem();
        item.setName(name);
        item.setDescriptionFile(descriptionFile);
        item.setSchemaFile(schemaFile);
        item.setCategory(category);
        item.setEnabled(enabled);
        return item;
    }

    /**
     * 从classpath加载资源为字符串
     */
    private String loadResourceAsString(String resourcePath) throws IOException {
        Resource resource = resourceLoader.getResource(resourcePath);
        if (!resource.exists()) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
    }

    /**
     * 从classpath加载资源为JSON Map
     */
    private Map<String, Object> loadResourceAsJson(String resourcePath) throws IOException {
        Resource resource = resourceLoader.getResource(resourcePath);
        if (!resource.exists()) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {
            });
        }
    }

    /**
     * 获取工具的完整描述
     *
     * @param toolName 工具名称
     * @return 描述内容，如果未找到返回null
     */
    public String getDescription(String toolName) {
        return descriptionCache.get(toolName);
    }

    /**
     * 获取工具的JSON Schema
     *
     * @param toolName 工具名称
     * @return JSON Schema，如果未找到返回null
     */
    public Map<String, Object> getSchema(String toolName) {
        return schemaCache.get(toolName);
    }

    /**
     * 检查工具是否有配置
     */
    public boolean hasConfig(String toolName) {
        return descriptionCache.containsKey(toolName) || schemaCache.containsKey(toolName);
    }

    /**
     * 检查工具是否启用
     * <p>如果工具在配置中且 enabled=true，或者工具不在配置中（兼容未配置的工具默认启用），则返回 true
     *
     * @param toolName 工具名称
     * @return true 如果工具启用
     */
    public boolean isEnabled(String toolName) {
        for (McpProperties.ToolConfigItem item : mcpProperties.getTools()) {
            if (toolName.equals(item.getName())) {
                return item.isEnabled();
            }
        }
        // 未配置的工具默认启用（向后兼容）
        return true;
    }

    /**
     * 重新加载配置（热重载）
     */
    public void reload() {
        descriptionCache.clear();
        schemaCache.clear();
        loadAllConfigurations();
        log.info("Tool configurations reloaded");
    }

    /**
     * 查找所有 QM 文件
     */
    private List<String> autoAllQmFiles() {
        List<BundleResource> result = new ArrayList<>();

        try {
            // 从所有 bundle 中查找 .qm 文件
            systemBundlesContext.getBundleList().forEach(bundle -> {
                try {
                    BundleResource[] resources = bundle.findBundleResources("**/*.qm");
                    if (resources != null) {
                        result.addAll(java.util.Arrays.asList(resources));
                    }
                } catch (Exception e) {
                    log.warn("从 bundle {} 查找 QM 文件时出错: {}", bundle.getName(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("查找 QM 文件时出错: {}", e.getMessage());
        }
        List<QueryModel> qms = new ArrayList<>();
        for (BundleResource qmFile : result) {
            String path = qmFile.getResource().getDescription();
            try {
                qms.add(queryModelLoader.loadJdbcQueryModel(qmFile));
                log.debug("QM 校验通过: {}", path);
            } catch (Exception e) {
                String errorMsg = String.format("QM [%s]: %s", path, e.getMessage());
                log.error("QM 校验失败: {}", path, e);
                if (log.isDebugEnabled()) {
                    e.printStackTrace();
                }
            }
        }
        return qms.stream().map(QueryModel::getName).toList();
    }
}
