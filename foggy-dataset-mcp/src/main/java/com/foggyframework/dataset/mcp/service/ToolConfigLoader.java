package com.foggyframework.dataset.mcp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具配置加载器
 *
 * 从classpath加载工具描述和JSON Schema：
 * - 描述文件 (*.md) -> 完整描述内容
 * - Schema文件 (*.json) -> 输入参数定义
 */
@Slf4j
@Component
public class ToolConfigLoader {

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

    public ToolConfigLoader(McpProperties mcpProperties, ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.mcpProperties = mcpProperties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        loadAllConfigurations();
    }

    /**
     * 加载所有工具配置
     */
    private void loadAllConfigurations() {
        log.info("Loading tool configurations from classpath...");

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
            return objectMapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {});
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
}
