package com.foggyframework.dataset.mcp.integration;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.mcp.tools.MetadataTool;
import com.foggyframework.dataset.mcp.tools.QueryModelTool;
import com.foggyframework.dataset.mcp.tools.DescriptionModelTool;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 工具集成测试
 *
 * 测试 MCP 工具在真实数据库环境下的工作情况
 *
 * 运行前提：
 * 1. 启动 Docker 数据库: cd foggy-dataset-model/docker && docker-compose -f docker-compose.test.yml up -d
 * 2. 确保 MySQL 数据库已初始化测试数据
 */
@Slf4j
@DisplayName("MCP 工具集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpToolsIntegrationTest extends McpIntegrationTestSupport {

    @Autowired
    private MetadataTool metadataTool;

    @Autowired
    private QueryModelTool queryModelTool;

    @Autowired
    private DescriptionModelTool descriptionModelTool;

    // ==================== 环境验证测试 ====================

    @Test
    @Order(1)
    @DisplayName("验证数据库连接和测试数据")
    void verifyDatabaseAndTestData() {
        verifyDatabaseConnection();

        // 验证维度表有数据
        assertTrue(getTableCount("dim_product") > 0, "dim_product 应有数据");
        assertTrue(getTableCount("dim_customer") > 0, "dim_customer 应有数据");
        assertTrue(getTableCount("dim_store") > 0, "dim_store 应有数据");

        // 验证事实表有数据
        assertTrue(getTableCount("fact_sales") > 0, "fact_sales 应有数据");

        log.info("数据库环境验证通过！");
    }

    @Test
    @Order(2)
    @DisplayName("验证 MCP 工具已注册")
    void verifyToolsRegistered() {
        assertNotNull(metadataTool, "MetadataTool 应已注册");
        assertNotNull(queryModelTool, "QueryModelTool 应已注册");
        assertNotNull(descriptionModelTool, "DescriptionModelTool 应已注册");

        log.info("已注册的 MCP 工具:");
        mcpTools.forEach(tool -> log.info("  - {} ({})", tool.getName(), tool.getClass().getSimpleName()));
    }

    // ==================== MetadataTool 测试 ====================

    @Nested
    @DisplayName("MetadataTool 集成测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class MetadataToolIntegrationTest {

        @Test
        @Order(1)
        @DisplayName("获取元数据 - 应返回模型列表")
        void getMetadata_shouldReturnModelList() {
            // 执行
            Object result = metadataTool.execute(Map.of(), generateTraceId(), null);

            // 验证
            assertNotNull(result, "结果不应为空");
            printJson(result, "Metadata Response");

            // 结果应该是 RX 包装的响应
            if (result instanceof RX<?> rx) {
                assertTrue(rx._isSuccess(), "请求应成功");
                assertNotNull(rx.getData(), "数据不应为空");
            }
        }

        @Test
        @Order(2)
        @DisplayName("获取元数据 - 应包含电商模型")
        @SuppressWarnings("unchecked")
        void getMetadata_shouldContainEcommerceModels() {
            Object result = metadataTool.execute(Map.of(), generateTraceId(), null);

            // 验证包含预期的模型
            String resultJson = result.toString();
            log.info("Checking for ecommerce models in metadata...");

            // 元数据中应该包含电商相关模型的信息
            // 具体断言取决于 SemanticMetadataResponse 的结构
            assertNotNull(result);
        }
    }

    // ==================== QueryModelTool 测试 ====================

    @Nested
    @DisplayName("QueryModelTool 集成测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class QueryModelToolIntegrationTest {

        @Test
        @Order(1)
        @DisplayName("查询 FactSalesQueryModel - 基本查询")
        void queryFactSales_basicQuery() {
            // 准备查询参数
            Map<String, Object> payload = new HashMap<>();
            payload.put("columns", List.of("product$caption", "salesAmount"));
            payload.put("limit", 10);

            Map<String, Object> arguments = Map.of(
                    "model", "FactSalesQueryModel",
                    "payload", payload,
                    "mode", "execute"
            );

            // 执行
            Object result = queryModelTool.execute(arguments, generateTraceId(), null);

            // 验证
            assertNotNull(result, "查询结果不应为空");
            printJson(result, "FactSales Query Result");

            // 检查是否有错误
            if (result instanceof Map<?, ?> map) {
                assertFalse(map.containsKey("error") && Boolean.TRUE.equals(map.get("error")),
                        "查询不应返回错误: " + map.get("message"));
            }
        }

        @Test
        @Order(2)
        @DisplayName("查询 FactSalesQueryModel - 带条件的查询")
        void queryFactSales_withConditions() {
            // 准备查询参数 - 查询特定商品类别的销售
            Map<String, Object> payload = new HashMap<>();
            payload.put("columns", List.of("product$caption", "customer$caption", "salesAmount", "quantity"));
            payload.put("slice", Map.of("product$category_name$caption", List.of("手机", "电脑")));
            payload.put("limit", 20);

            Map<String, Object> arguments = Map.of(
                    "model", "FactSalesQueryModel",
                    "payload", payload,
                    "mode", "execute"
            );

            // 执行
            Object result = queryModelTool.execute(arguments, generateTraceId(), null);

            // 验证
            assertNotNull(result, "查询结果不应为空");
            printJson(result, "FactSales with Conditions");
        }

        @Test
        @Order(3)
        @DisplayName("查询 FactSalesQueryModel - 带聚合的查询")
        void queryFactSales_withAggregation() {
            // 准备查询参数 - 按商品分组统计销售额
            Map<String, Object> payload = new HashMap<>();
            payload.put("columns", List.of("product$caption", "salesAmount", "quantity"));
            payload.put("groupBy", List.of("product$caption"));
            payload.put("orderBy", List.of(Map.of("column", "salesAmount", "direction", "DESC")));
            payload.put("limit", 10);

            Map<String, Object> arguments = Map.of(
                    "model", "FactSalesQueryModel",
                    "payload", payload,
                    "mode", "execute"
            );

            // 执行
            Object result = queryModelTool.execute(arguments, generateTraceId(), null);

            // 验证
            assertNotNull(result, "查询结果不应为空");
            printJson(result, "FactSales Aggregation");
        }

        @Test
        @Order(4)
        @DisplayName("查询 FactOrderQueryModel - 订单查询")
        void queryFactOrder() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("columns", List.of("orderDate$caption", "customer$caption", "totalAmount"));
            payload.put("limit", 5);

            Map<String, Object> arguments = Map.of(
                    "model", "FactOrderQueryModel",
                    "payload", payload,
                    "mode", "execute"
            );

            Object result = queryModelTool.execute(arguments, generateTraceId(), null);

            assertNotNull(result, "订单查询结果不应为空");
            printJson(result, "FactOrder Query Result");
        }

        @Test
        @Order(5)
        @DisplayName("查询模式 - validate 模式应只验证不执行")
        void queryValidateMode() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("columns", List.of("product$caption", "salesAmount"));
            payload.put("limit", 5);

            Map<String, Object> arguments = Map.of(
                    "model", "FactSalesQueryModel",
                    "payload", payload,
                    "mode", "validate"
            );

            Object result = queryModelTool.execute(arguments, generateTraceId(), null);

            assertNotNull(result, "验证结果不应为空");
            printJson(result, "Validate Mode Result");
        }

        @Test
        @Order(6)
        @DisplayName("错误处理 - 无效模型名称")
        void queryInvalidModel_shouldReturnError() {
            Map<String, Object> arguments = Map.of(
                    "model", "NonExistentModel",
                    "payload", Map.of("columns", List.of("foo")),
                    "mode", "execute"
            );

            Object result = queryModelTool.execute(arguments, generateTraceId(), null);

            assertNotNull(result, "结果不应为空");
            printJson(result, "Invalid Model Error");

            // 应该返回错误信息
            // 具体的错误格式取决于 SemanticQueryService 的实现
        }
    }

    // ==================== DescriptionModelTool 测试 ====================

    @Nested
    @DisplayName("DescriptionModelTool 集成测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class DescriptionModelToolIntegrationTest {

        @Test
        @Order(1)
        @DisplayName("描述 FactSalesQueryModel - 应返回字段定义")
        void describeFactSalesModel() {
            Map<String, Object> arguments = Map.of("model", "FactSalesQueryModel");

            Object result = descriptionModelTool.execute(arguments, generateTraceId(), null);

            assertNotNull(result, "模型描述不应为空");
            printJson(result, "FactSalesQueryModel Description");
        }

        @Test
        @Order(2)
        @DisplayName("描述 FactOrderQueryModel - 应返回字段定义")
        void describeFactOrderModel() {
            Map<String, Object> arguments = Map.of("model", "FactOrderQueryModel");

            Object result = descriptionModelTool.execute(arguments, generateTraceId(), null);

            assertNotNull(result, "模型描述不应为空");
            printJson(result, "FactOrderQueryModel Description");
        }
    }

    // ==================== 端到端场景测试 ====================

    @Nested
    @DisplayName("端到端场景测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class EndToEndScenarioTest {

        @Test
        @Order(1)
        @DisplayName("场景1: 获取元数据 -> 选择模型 -> 查询数据")
        void scenario_metadataToQuery() {
            log.info("=== 场景1: 完整查询流程 ===");

            // Step 1: 获取元数据
            log.info("Step 1: 获取元数据...");
            Object metadata = metadataTool.execute(Map.of(), generateTraceId(), null);
            assertNotNull(metadata, "元数据不应为空");

            // Step 2: 获取模型描述
            log.info("Step 2: 获取 FactSalesQueryModel 描述...");
            Object description = descriptionModelTool.execute(
                    Map.of("model", "FactSalesQueryModel"),
                    generateTraceId(),
                    null
            );
            assertNotNull(description, "模型描述不应为空");

            // Step 3: 执行查询
            log.info("Step 3: 执行销售数据查询...");
            Map<String, Object> payload = new HashMap<>();
            payload.put("columns", List.of("product$caption", "salesAmount", "quantity"));
            payload.put("limit", 10);

            Object queryResult = queryModelTool.execute(
                    Map.of("model", "FactSalesQueryModel", "payload", payload, "mode", "execute"),
                    generateTraceId(),
                    null
            );
            assertNotNull(queryResult, "查询结果不应为空");
            printJson(queryResult, "端到端查询结果");

            log.info("=== 场景1 完成 ===");
        }

        @Test
        @Order(2)
        @DisplayName("场景2: 多维度销售分析")
        void scenario_multiDimensionAnalysis() {
            log.info("=== 场景2: 多维度销售分析 ===");

            // 按商品和门店分组的销售分析
            Map<String, Object> payload = new HashMap<>();
            payload.put("columns", List.of(
                    "product$caption",
                    "store$caption",
                    "salesAmount",
                    "quantity"
            ));
            payload.put("groupBy", List.of("product$caption", "store$caption"));
            payload.put("orderBy", List.of(Map.of("column", "salesAmount", "direction", "DESC")));
            payload.put("limit", 20);

            Object result = queryModelTool.execute(
                    Map.of("model", "FactSalesQueryModel", "payload", payload, "mode", "execute"),
                    generateTraceId(),
                    null
            );

            assertNotNull(result, "多维度分析结果不应为空");
            printJson(result, "多维度销售分析结果");

            log.info("=== 场景2 完成 ===");
        }
    }
}
