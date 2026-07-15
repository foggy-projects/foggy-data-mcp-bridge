package com.foggyframework.dataset.mcp.integration;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.mcp.tools.DescriptionModelTool;
import com.foggyframework.dataset.mcp.tools.ListModelsTool;
import com.foggyframework.dataset.mcp.tools.MetadataTool;
import com.foggyframework.dataset.mcp.tools.QueryModelTool;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
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
class McpToolsIT extends McpIntegrationTestSupport {

    @Autowired
    private MetadataTool metadataTool;

    @Autowired
    private QueryModelTool queryModelTool;

    @Autowired
    private DescriptionModelTool descriptionModelTool;

    @Autowired
    private ListModelsTool listModelsTool;

    @Value("${foggy.mcp.semantic.use-all-models:false}")
    private boolean useAllModels;

    @Value("${v934.external.variant:}")
    private String externalVariant;

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
        Map.of(
                "dataset.get_metadata", metadataTool,
                "dataset.list_models", listModelsTool,
                "dataset.query_model", queryModelTool,
                "dataset.describe_model_internal", descriptionModelTool
        ).forEach((name, expectedTool) -> {
            assertSame(expectedTool, getTool(name), name + " 应解析到注入实例");
            assertEquals(1, mcpTools.stream().filter(tool -> name.equals(tool.getName())).count(),
                    name + " 应按唯一名称注册");
        });

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
            ToolExecutionContext context = ToolExecutionContext.of(generateTraceId(), null);
            Object result = metadataTool.execute(Map.of(), context);

            printJson(result, "Metadata Response");
            assertMetadataSuccess(result, "FactSalesQueryModel", "FactOrderQueryModel");
        }

        @Test
        @Order(2)
        @DisplayName("获取元数据 - 应包含电商模型")
        void getMetadata_shouldContainEcommerceModels() {
            ToolExecutionContext context = ToolExecutionContext.of(generateTraceId(), null);
            Object result = metadataTool.execute(Map.of(), context);

            log.info("Checking for ecommerce models in metadata...");
            SemanticMetadataResponse metadata = assertMetadataSuccess(
                    result, "FactSalesQueryModel", "FactOrderQueryModel", "salesAmount", "amount");
            assertFalse(metadata.getContent().contains("DocumentSearchQueryModel"),
                    "隔离的 ecommerce 元数据不应泄漏 Vector 模型");

            if ("mysql57-mcp".equals(externalVariant)) {
                assertTrue(useAllModels,
                        "MySQL 5.7 external lane 必须启用完整 catalog，不能由 model-list 裁剪伪绿");
            }
            if (useAllModels) {
                Object catalogResult = listModelsTool.execute(Map.of(), context);
                Map<?, ?> catalogEnvelope = assertInstanceOf(Map.class, catalogResult,
                        "dataset.list_models 应返回 Map envelope");
                assertEquals(200, catalogEnvelope.get("code"), "catalog 应返回 200");
                Map<?, ?> catalogData = assertInstanceOf(Map.class, catalogEnvelope.get("data"),
                        "catalog data 不应为空");
                String catalog = assertInstanceOf(String.class, catalogData.get("content"),
                        "catalog content 应为 markdown");
                long modelCount = catalog.lines().filter(line -> line.startsWith("- **")).count();
                assertEquals(32, modelCount, "curated ecommerce catalog 应精确装载 32 个 QM");
                assertTrue(catalog.contains("FactSalesQueryModel"), "catalog 应包含销售模型");
                assertTrue(catalog.contains("FactOrderQueryModel"), "catalog 应包含订单模型");
                assertFalse(catalog.contains("FactSalesDemoAuthQueryModel"),
                        "catalog 不应装载依赖 demoAuthorizationService 的销售演示模型");
                assertFalse(catalog.contains("FactOrderDemoAuthQueryModel"),
                        "catalog 不应装载依赖 demoAuthorizationService 的订单演示模型");
                assertFalse(catalog.contains("DocumentSearchQueryModel"),
                        "catalog 不应泄漏 Vector 模型");
            }
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
            ToolExecutionContext context = ToolExecutionContext.of(generateTraceId(), null);
            Object result = queryModelTool.execute(arguments, context);

            printJson(result, "FactSales Query Result");
            assertQuerySuccess(result, List.of("product$caption", "salesAmount"), 10);
        }

        @Test
        @Order(2)
        @DisplayName("查询 FactSalesQueryModel - 带条件的查询")
        void queryFactSales_withConditions() {
            // 准备查询参数 - 查询特定商品类别的销售
            Map<String, Object> payload = new HashMap<>();
            payload.put("columns", List.of(
                    "product$caption", "product$subCategoryName", "customer$caption", "salesAmount", "quantity"));
            payload.put("slice", List.of(Map.of(
                    "field", "product$subCategoryName",
                    "op", "in",
                    "value", List.of("手机通讯", "电脑办公")
            )));
            payload.put("limit", 20);

            Map<String, Object> arguments = Map.of(
                    "model", "FactSalesQueryModel",
                    "payload", payload,
                    "mode", "execute"
            );

            // 执行
            ToolExecutionContext context = ToolExecutionContext.of(generateTraceId(), null);
            Object result = queryModelTool.execute(arguments, context);

            printJson(result, "FactSales with Conditions");
            SemanticQueryResponse response = assertQuerySuccess(result, List.of(
                    "product$caption", "product$subCategoryName", "customer$caption", "salesAmount", "quantity"), 20);
            response.getItems().forEach(row -> assertTrue(
                    List.of("手机通讯", "电脑办公").contains(row.get("product$subCategoryName")),
                    "返回行必须满足二级品类过滤: " + row));
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
            ToolExecutionContext context = ToolExecutionContext.of(generateTraceId(), null);
            Object result = queryModelTool.execute(arguments, context);

            printJson(result, "FactSales Aggregation");
            SemanticQueryResponse response = assertQuerySuccess(
                    result, List.of("product$caption", "salesAmount", "quantity"), 10);
            for (int index = 1; index < response.getItems().size(); index++) {
                BigDecimal previous = new BigDecimal(
                        response.getItems().get(index - 1).get("salesAmount").toString());
                BigDecimal current = new BigDecimal(
                        response.getItems().get(index).get("salesAmount").toString());
                assertTrue(previous.compareTo(current) >= 0, "销售额应按 DESC 排序");
            }
        }

        @Test
        @Order(4)
        @DisplayName("查询 FactOrderQueryModel - 订单查询")
        void queryFactOrder() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("columns", List.of("orderDate$caption", "customer$caption", "amount"));
            payload.put("limit", 5);

            Map<String, Object> arguments = Map.of(
                    "model", "FactOrderQueryModel",
                    "payload", payload,
                    "mode", "execute"
            );

            ToolExecutionContext context = ToolExecutionContext.of(generateTraceId(), null);
            Object result = queryModelTool.execute(arguments, context);

            printJson(result, "FactOrder Query Result");
            assertQuerySuccess(result, List.of("orderDate$caption", "customer$caption", "amount"), 5);
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

            ToolExecutionContext context = ToolExecutionContext.of(generateTraceId(), null);
            Object result = queryModelTool.execute(arguments, context);

            printJson(result, "Validate Mode Result");
            RX<?> rx = assertRx(result);
            assertTrue(rx._isSuccess(), "validate 模式应成功, msg=" + rx.getMsg());
            SemanticQueryResponse response = assertInstanceOf(SemanticQueryResponse.class, rx.getData());
            assertNull(response.getItems(), "validate 模式不应执行并返回数据行");
            assertNull(response.getSchema(), "validate 模式不应构造执行结果 schema");
            assertNull(response.getPagination(), "validate 模式不应构造执行分页信息");
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

            ToolExecutionContext context = ToolExecutionContext.of(generateTraceId(), null);
            Object result = queryModelTool.execute(arguments, context);

            printJson(result, "Invalid Model Error");
            RX<?> rx = assertRx(result);
            assertFalse(rx._isSuccess(), "不存在的模型必须 fail closed");
            assertEquals(600, rx.getCode(), "不存在的模型应返回通用业务错误码");
            assertEquals(RX.B_COMMON, rx.getExCode(), "不存在的模型应返回 B600");
            assertNull(rx.getData(), "失败响应不应携带伪查询数据");
            assertTrue(rx.getMsg() != null && rx.getMsg().contains("NonExistentModel"),
                    "失败信息应标识不存在的模型: " + rx.getMsg());
            assertTrue(rx.getMsg().contains("不存在"), "失败信息应明确模型不存在");
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

            ToolExecutionContext context = ToolExecutionContext.of(generateTraceId(), null);
            Object result = descriptionModelTool.execute(arguments, context);

            printJson(result, "FactSalesQueryModel Description");
            assertMetadataSuccess(result, "FactSalesQueryModel", "salesAmount", "product$caption");
        }

        @Test
        @Order(2)
        @DisplayName("描述 FactOrderQueryModel - 应返回字段定义")
        void describeFactOrderModel() {
            Map<String, Object> arguments = Map.of("model", "FactOrderQueryModel");

            ToolExecutionContext context = ToolExecutionContext.of(generateTraceId(), null);
            Object result = descriptionModelTool.execute(arguments, context);

            printJson(result, "FactOrderQueryModel Description");
            assertMetadataSuccess(result, "FactOrderQueryModel", "amount", "orderStatus");
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
            ToolExecutionContext ctx1 = ToolExecutionContext.of(generateTraceId(), null);
            Object metadata = metadataTool.execute(Map.of(), ctx1);
            assertMetadataSuccess(metadata, "FactSalesQueryModel", "FactOrderQueryModel");

            // Step 2: 获取模型描述
            log.info("Step 2: 获取 FactSalesQueryModel 描述...");
            ToolExecutionContext ctx2 = ToolExecutionContext.of(generateTraceId(), null);
            Object description = descriptionModelTool.execute(
                    Map.of("model", "FactSalesQueryModel"),
                    ctx2
            );
            assertMetadataSuccess(description, "FactSalesQueryModel", "salesAmount");

            // Step 3: 执行查询
            log.info("Step 3: 执行销售数据查询...");
            Map<String, Object> payload = new HashMap<>();
            payload.put("columns", List.of("product$caption", "salesAmount", "quantity"));
            payload.put("limit", 10);

            ToolExecutionContext ctx3 = ToolExecutionContext.of(generateTraceId(), null);
            Object queryResult = queryModelTool.execute(
                    Map.of("model", "FactSalesQueryModel", "payload", payload, "mode", "execute"),
                    ctx3
            );
            printJson(queryResult, "端到端查询结果");
            assertQuerySuccess(queryResult, List.of("product$caption", "salesAmount", "quantity"), 10);

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

            ToolExecutionContext context = ToolExecutionContext.of(generateTraceId(), null);
            Object result = queryModelTool.execute(
                    Map.of("model", "FactSalesQueryModel", "payload", payload, "mode", "execute"),
                    context
            );

            printJson(result, "多维度销售分析结果");
            assertQuerySuccess(result, List.of(
                    "product$caption", "store$caption", "salesAmount", "quantity"), 20);

            log.info("=== 场景2 完成 ===");
        }
    }

    @SuppressWarnings("rawtypes")
    private static RX<?> assertRx(Object result) {
        return assertInstanceOf(RX.class, result, "工具结果必须使用 RX 契约");
    }

    private static SemanticMetadataResponse assertMetadataSuccess(Object result, String... requiredTokens) {
        RX<?> rx = assertRx(result);
        assertEquals(RX.SUCCESS, rx.getCode(), "元数据成功响应应返回 200");
        assertTrue(rx._isSuccess(), "元数据请求应成功, code=" + rx.getCode() + ", msg=" + rx.getMsg());
        SemanticMetadataResponse metadata = assertInstanceOf(SemanticMetadataResponse.class, rx.getData());
        assertEquals("markdown", metadata.getFormat(), "集成测试默认元数据格式应为 markdown");
        assertNotNull(metadata.getContent(), "元数据内容不应为空");
        assertFalse(metadata.getContent().isBlank(), "元数据内容不应为空白");
        for (String token : requiredTokens) {
            assertTrue(metadata.getContent().contains(token), "元数据应包含 " + token);
        }
        return metadata;
    }

    private static SemanticQueryResponse assertQuerySuccess(
            Object result,
            List<String> expectedColumns,
            int expectedLimit
    ) {
        RX<?> rx = assertRx(result);
        assertEquals(RX.SUCCESS, rx.getCode(), "查询成功响应应返回 200");
        assertTrue(rx._isSuccess(), "查询应成功, code=" + rx.getCode() + ", msg=" + rx.getMsg());
        SemanticQueryResponse response = assertInstanceOf(SemanticQueryResponse.class, rx.getData());
        assertNotNull(response.getItems(), "成功执行必须返回 items");
        assertFalse(response.getItems().isEmpty(), "真实数据库查询必须命中数据");
        assertTrue(response.getItems().size() <= expectedLimit, "返回行数不得超过 limit");
        assertNotNull(response.getSchema(), "成功执行必须返回 schema");
        assertNotNull(response.getSchema().getColumns(), "成功执行必须返回列定义");
        assertEquals(expectedColumns, response.getSchema().getColumns().stream()
                .map(SemanticQueryResponse.SchemaInfo.ColumnDef::getName)
                .toList(), "返回列必须与请求列一致");
        response.getItems().forEach(row -> assertTrue(row.keySet().containsAll(expectedColumns),
                "每行必须包含全部请求列: " + row));
        assertNotNull(response.getPagination(), "成功执行必须返回分页信息");
        assertEquals(expectedLimit, response.getPagination().getLimit(), "分页 limit 必须回显");
        assertEquals(response.getItems().size(), response.getPagination().getReturned(),
                "分页 returned 必须匹配实际行数");
        return response;
    }
}
