package com.foggyframework.dataset.mcp.service;

import com.foggyframework.dataset.mcp.base.BaseMcpTest;
import com.foggyframework.dataset.mcp.base.MockToolFactory;
import com.foggyframework.dataset.mcp.enums.ToolCategory;
import com.foggyframework.dataset.mcp.enums.UserRole;
import com.foggyframework.dataset.mcp.tools.McpTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolFilterService 单元测试
 *
 * 测试工具过滤服务的角色权限控制逻辑
 */
@DisplayName("ToolFilterService 单元测试")
class ToolFilterServiceTest extends BaseMcpTest {

    private ToolFilterService filterService;

    @BeforeEach
    void setUp() {
        filterService = new ToolFilterService();
    }

    // ==================== 角色分类映射测试 ====================

    @Nested
    @DisplayName("getAllowedCategories - 获取角色允许的分类")
    class GetAllowedCategoriesTest {

        @Test
        @DisplayName("Admin 角色应拥有所有分类")
        void admin_shouldHaveAllCategories() {
            Set<ToolCategory> allowed = filterService.getAllowedCategories(UserRole.ADMIN);

            assertNotNull(allowed);
            assertEquals(EnumSet.allOf(ToolCategory.class), allowed);
            assertTrue(allowed.contains(ToolCategory.NATURAL_LANGUAGE));
            assertTrue(allowed.contains(ToolCategory.METADATA));
            assertTrue(allowed.contains(ToolCategory.QUERY));
            assertTrue(allowed.contains(ToolCategory.VISUALIZATION));
            assertTrue(allowed.contains(ToolCategory.EXPORT));
            assertTrue(allowed.contains(ToolCategory.SYSTEM));
        }

        @Test
        @DisplayName("Business 角色仅允许自然语言分类")
        void business_shouldOnlyHaveNaturalLanguage() {
            Set<ToolCategory> allowed = filterService.getAllowedCategories(UserRole.BUSINESS);

            assertNotNull(allowed);
            assertEquals(1, allowed.size());
            assertTrue(allowed.contains(ToolCategory.NATURAL_LANGUAGE));
            assertFalse(allowed.contains(ToolCategory.QUERY));
            assertFalse(allowed.contains(ToolCategory.METADATA));
        }

        @Test
        @DisplayName("Analyst 角色应排除自然语言分类")
        void analyst_shouldExcludeNaturalLanguage() {
            Set<ToolCategory> allowed = filterService.getAllowedCategories(UserRole.ANALYST);

            assertNotNull(allowed);
            assertFalse(allowed.contains(ToolCategory.NATURAL_LANGUAGE));
            assertTrue(allowed.contains(ToolCategory.METADATA));
            assertTrue(allowed.contains(ToolCategory.QUERY));
            assertTrue(allowed.contains(ToolCategory.VISUALIZATION));
            assertTrue(allowed.contains(ToolCategory.EXPORT));
            assertTrue(allowed.contains(ToolCategory.SYSTEM));
        }
    }

    // ==================== filterToolsByRole 测试 ====================

    @Nested
    @DisplayName("filterToolsByRole - 按角色过滤工具")
    class FilterToolsByRoleTest {

        @Test
        @DisplayName("空列表应返回空")
        void emptyList_shouldReturnEmpty() {
            List<McpTool> result = filterService.filterToolsByRole(Collections.emptyList(), UserRole.ADMIN);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("null 列表应返回空")
        void nullList_shouldReturnEmpty() {
            List<McpTool> result = filterService.filterToolsByRole(null, UserRole.ADMIN);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Admin 应能看到所有工具")
        void admin_shouldSeeAllTools() {
            List<McpTool> allTools = MockToolFactory.createAllStandardTools();

            List<McpTool> filtered = filterService.filterToolsByRole(allTools, UserRole.ADMIN);

            assertEquals(allTools.size(), filtered.size());
        }

        @Test
        @DisplayName("Business 只能看到 NL 工具")
        void business_shouldOnlySeeNLTools() {
            List<McpTool> allTools = MockToolFactory.createAllStandardTools();

            List<McpTool> filtered = filterService.filterToolsByRole(allTools, UserRole.BUSINESS);

            // 只有 NL 工具和包含 NL 分类的多分类工具
            assertFalse(filtered.isEmpty());
            for (McpTool tool : filtered) {
                assertTrue(tool.getCategories().contains(ToolCategory.NATURAL_LANGUAGE),
                        "Tool " + tool.getName() + " should have NATURAL_LANGUAGE category");
            }
        }

        @Test
        @DisplayName("Analyst 看不到纯 NL 工具")
        void analyst_shouldNotSeePureNLTools() {
            List<McpTool> allTools = MockToolFactory.createAllStandardTools();

            List<McpTool> filtered = filterService.filterToolsByRole(allTools, UserRole.ANALYST);

            // Analyst 不能看到只有 NL 分类的工具
            for (McpTool tool : filtered) {
                Set<ToolCategory> categories = tool.getCategories();
                if (categories.contains(ToolCategory.NATURAL_LANGUAGE)) {
                    // 如果包含 NL，必须还有其他分类
                    assertTrue(categories.size() > 1,
                            "Analyst should not see pure NL tool: " + tool.getName());
                }
            }
        }

        @Test
        @DisplayName("多分类工具：有交集即可通过过滤")
        void multiCategoryTool_shouldPassWithIntersection() {
            // 创建只有 export_with_chart 工具的列表
            McpTool exportTool = MockToolFactory.createExportWithChartTool();
            List<McpTool> tools = List.of(exportTool);

            // Analyst 可以访问（因为有 QUERY, VISUALIZATION, EXPORT 分类）
            List<McpTool> analystFiltered = filterService.filterToolsByRole(tools, UserRole.ANALYST);
            assertEquals(1, analystFiltered.size());

            // Business 不能访问（没有 NATURAL_LANGUAGE 分类）
            List<McpTool> businessFiltered = filterService.filterToolsByRole(tools, UserRole.BUSINESS);
            assertTrue(businessFiltered.isEmpty());
        }
    }

    // ==================== canAccessTool 测试 ====================

    @Nested
    @DisplayName("canAccessTool - 检查工具访问权限")
    class CanAccessToolTest {

        @Test
        @DisplayName("Admin 可访问任意工具")
        void admin_shouldAccessAnyTool() {
            McpTool nlTool = MockToolFactory.createNLQueryTool();
            McpTool queryTool = MockToolFactory.createQueryModelTool();
            McpTool metadataTool = MockToolFactory.createMetadataTool();

            assertTrue(filterService.canAccessTool(nlTool, UserRole.ADMIN));
            assertTrue(filterService.canAccessTool(queryTool, UserRole.ADMIN));
            assertTrue(filterService.canAccessTool(metadataTool, UserRole.ADMIN));
        }

        @Test
        @DisplayName("Business 可以访问 NL 工具")
        void business_shouldAccessNLTool() {
            McpTool nlTool = MockToolFactory.createNLQueryTool();

            assertTrue(filterService.canAccessTool(nlTool, UserRole.BUSINESS));
        }

        @Test
        @DisplayName("Business 不能访问 Query 工具")
        void business_shouldNotAccessQueryTool() {
            McpTool queryTool = MockToolFactory.createQueryModelTool();

            assertFalse(filterService.canAccessTool(queryTool, UserRole.BUSINESS));
        }

        @Test
        @DisplayName("Business 不能访问 Metadata 工具")
        void business_shouldNotAccessMetadataTool() {
            McpTool metadataTool = MockToolFactory.createMetadataTool();

            assertFalse(filterService.canAccessTool(metadataTool, UserRole.BUSINESS));
        }

        @Test
        @DisplayName("Analyst 可以访问 Query 工具")
        void analyst_shouldAccessQueryTool() {
            McpTool queryTool = MockToolFactory.createQueryModelTool();

            assertTrue(filterService.canAccessTool(queryTool, UserRole.ANALYST));
        }

        @Test
        @DisplayName("Analyst 不能访问纯 NL 工具")
        void analyst_shouldNotAccessPureNLTool() {
            McpTool nlTool = MockToolFactory.createNLQueryTool();

            assertFalse(filterService.canAccessTool(nlTool, UserRole.ANALYST));
        }

        @Test
        @DisplayName("Analyst 可以访问多分类工具（有交集）")
        void analyst_shouldAccessMultiCategoryTool() {
            McpTool exportTool = MockToolFactory.createExportWithChartTool();

            assertTrue(filterService.canAccessTool(exportTool, UserRole.ANALYST));
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTest {

        @Test
        @DisplayName("工具没有分类时应被拒绝")
        void tool_withNoCategories_shouldBeRejected() {
            McpTool tool = MockToolFactory.createToolWithNoCategories("empty.tool");

            assertFalse(filterService.canAccessTool(tool, UserRole.ADMIN));
            assertFalse(filterService.canAccessTool(tool, UserRole.BUSINESS));
            assertFalse(filterService.canAccessTool(tool, UserRole.ANALYST));
        }

        @Test
        @DisplayName("工具分类为 null 时应被拒绝")
        void tool_withNullCategories_shouldBeRejected() {
            McpTool tool = MockToolFactory.createToolWithNullCategories("null.tool");

            assertFalse(filterService.canAccessTool(tool, UserRole.ADMIN));
            assertFalse(filterService.canAccessTool(tool, UserRole.BUSINESS));
            assertFalse(filterService.canAccessTool(tool, UserRole.ANALYST));
        }
    }

    // ==================== filterToolDefinitionsByRole 测试 ====================

    @Nested
    @DisplayName("filterToolDefinitionsByRole - 过滤工具定义")
    class FilterToolDefinitionsByRoleTest {

        @Test
        @DisplayName("应正确过滤工具定义列表")
        void shouldFilterDefinitionsCorrectly() {
            List<McpTool> allTools = MockToolFactory.createAllStandardTools();

            // 创建对应的工具定义
            List<Map<String, Object>> allDefinitions = new ArrayList<>();
            for (McpTool tool : allTools) {
                allDefinitions.add(Map.of(
                        "name", tool.getName(),
                        "description", tool.getDescription()
                ));
            }

            // Business 用户过滤
            List<Map<String, Object>> businessDefs = filterService.filterToolDefinitionsByRole(
                    allDefinitions, allTools, UserRole.BUSINESS);

            // 验证只有 NL 工具
            for (Map<String, Object> def : businessDefs) {
                String toolName = (String) def.get("name");
                assertTrue(toolName.contains("nl") || toolName.contains("natural"),
                        "Business should only see NL tools, but got: " + toolName);
            }
        }

        @Test
        @DisplayName("Admin 应看到所有工具定义")
        void admin_shouldSeeAllDefinitions() {
            List<McpTool> allTools = MockToolFactory.createAllStandardTools();
            List<Map<String, Object>> allDefinitions = new ArrayList<>();
            for (McpTool tool : allTools) {
                allDefinitions.add(Map.of("name", tool.getName()));
            }

            List<Map<String, Object>> adminDefs = filterService.filterToolDefinitionsByRole(
                    allDefinitions, allTools, UserRole.ADMIN);

            assertEquals(allDefinitions.size(), adminDefs.size());
        }
    }
}
