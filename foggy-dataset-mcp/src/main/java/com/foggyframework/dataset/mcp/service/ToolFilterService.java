package com.foggyframework.dataset.mcp.service;

import com.foggyframework.dataset.mcp.enums.ToolCategory;
import com.foggyframework.dataset.mcp.enums.UserRole;
import com.foggyframework.dataset.mcp.tools.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具过滤服务
 *
 * 根据用户角色过滤可用的工具集合
 */
@Slf4j
@Service
public class ToolFilterService {

    /**
     * 用户角色与工具分类的映射关系
     */
    private static final Map<UserRole, Set<ToolCategory>> ROLE_CATEGORY_MAPPING = new EnumMap<>(UserRole.class);

    static {
        // 管理员：所有分类
        ROLE_CATEGORY_MAPPING.put(UserRole.ADMIN, EnumSet.allOf(ToolCategory.class));

        // 业务人员：仅自然语言查询
        ROLE_CATEGORY_MAPPING.put(UserRole.BUSINESS, EnumSet.of(
                ToolCategory.NATURAL_LANGUAGE
        ));

        // 数据分析师：除自然语言外的所有专业工具
        ROLE_CATEGORY_MAPPING.put(UserRole.ANALYST, EnumSet.of(
                ToolCategory.METADATA,
                ToolCategory.QUERY,
                ToolCategory.VISUALIZATION,
                ToolCategory.EXPORT,
                ToolCategory.SYSTEM
        ));
    }

    /**
     * 根据用户角色过滤工具列表
     *
     * @param allTools 所有可用工具
     * @param userRole 用户角色
     * @return 过滤后的工具列表
     */
    public List<McpTool> filterToolsByRole(List<McpTool> allTools, UserRole userRole) {
        if (allTools == null || allTools.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取该角色允许的分类
        Set<ToolCategory> allowedCategories = ROLE_CATEGORY_MAPPING.getOrDefault(
                userRole,
                Collections.emptySet()
        );

        // 过滤工具：工具的分类与角色允许的分类有交集即可
        List<McpTool> filteredTools = allTools.stream()
                .filter(tool -> hasAllowedCategory(tool, allowedCategories))
                .collect(Collectors.toList());

        log.info("Filtered tools for role {}: {} tools out of {} total",
                userRole, filteredTools.size(), allTools.size());

        return filteredTools;
    }

    /**
     * 根据用户角色过滤工具定义
     *
     * @param allToolDefinitions 所有工具定义
     * @param allTools 所有工具对象（用于获取分类信息）
     * @param userRole 用户角色
     * @return 过滤后的工具定义列表
     */
    public List<Map<String, Object>> filterToolDefinitionsByRole(
            List<Map<String, Object>> allToolDefinitions,
            List<McpTool> allTools,
            UserRole userRole
    ) {
        // 先过滤工具对象
        List<McpTool> filteredTools = filterToolsByRole(allTools, userRole);

        // 获取允许的工具名称集合
        Set<String> allowedToolNames = filteredTools.stream()
                .map(McpTool::getName)
                .collect(Collectors.toSet());

        // 过滤工具定义
        return allToolDefinitions.stream()
                .filter(def -> allowedToolNames.contains(def.get("name")))
                .collect(Collectors.toList());
    }

    /**
     * 检查工具是否属于允许的分类
     */
    private boolean hasAllowedCategory(McpTool tool, Set<ToolCategory> allowedCategories) {
        // 首先检查工具是否定义了分类
        Set<ToolCategory> toolCategories = tool.getCategories();
        if (toolCategories == null || toolCategories.isEmpty()) {
            log.warn("Tool {} has no categories defined", tool.getName());
            return false;
        }

        if (allowedCategories.isEmpty()) {
            return false;
        }

        // 如果允许的分类包含所有分类，直接返回 true
        if (allowedCategories.containsAll(EnumSet.allOf(ToolCategory.class))) {
            return true;
        }

        // 检查工具的分类是否与允许的分类有交集
        return toolCategories.stream()
                .anyMatch(allowedCategories::contains);
    }

    /**
     * 获取用户角色允许的分类
     *
     * @param userRole 用户角色
     * @return 允许的分类集合
     */
    public Set<ToolCategory> getAllowedCategories(UserRole userRole) {
        return ROLE_CATEGORY_MAPPING.getOrDefault(
                userRole,
                Collections.emptySet()
        );
    }

    /**
     * 检查用户角色是否可以访问指定工具
     *
     * @param tool 工具对象
     * @param userRole 用户角色
     * @return 是否可以访问
     */
    public boolean canAccessTool(McpTool tool, UserRole userRole) {
        Set<ToolCategory> allowedCategories = getAllowedCategories(userRole);
        return hasAllowedCategory(tool, allowedCategories);
    }
}
