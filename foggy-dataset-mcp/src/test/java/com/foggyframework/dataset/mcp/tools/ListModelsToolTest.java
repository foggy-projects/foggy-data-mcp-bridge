package com.foggyframework.dataset.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.spi.DbDimension;
import com.foggyframework.dataset.db.model.spi.DbQueryDimension;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.mcp.config.McpProperties;
import com.foggyframework.dataset.mcp.service.ModelCatalogService;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ListModelsTool 单元测试
 *
 * 使用 Mock 的 SemanticServiceResolver 和 QueryModelLoader
 */
@DisplayName("ListModelsTool 单元测试")
@ExtendWith(MockitoExtension.class)
class ListModelsToolTest {

    @Mock
    private SemanticServiceResolver semanticServiceResolver;

    @Mock
    private QueryModelLoader queryModelLoader;

    private ListModelsTool listModelsTool;

    @BeforeEach
    void setUp() {
        listModelsTool = new ListModelsTool(
                new ModelCatalogService(semanticServiceResolver, queryModelLoader, new ObjectMapper())
        );
    }

    // ==================== 基本属性测试 ====================

    @Nested
    @DisplayName("工具基本属性")
    class BasicPropertiesTest {

        @Test
        @DisplayName("getName 应返回 dataset.list_models")
        void getName_shouldReturnCorrectName() {
            assertEquals("dataset.list_models", listModelsTool.getName());
        }

        @Test
        @DisplayName("getCategories 应返回 METADATA 类别")
        void getCategories_shouldReturnMetadataCategory() {
            assertTrue(listModelsTool.getCategories().contains(ToolCategory.METADATA));
            assertEquals(1, listModelsTool.getCategories().size());
        }
    }

    // ==================== execute 成功场景测试 ====================

    @Nested
    @DisplayName("execute - 成功场景")
    class ExecuteSuccessTest {

        @Test
        @DisplayName("无参调用应返回 code=200 和 markdown catalog")
        void shouldReturnMarkdownWithCode200() {
            when(semanticServiceResolver.getAllModelNames()).thenReturn(List.of());

            Object result = listModelsTool.execute(Map.of(), ToolExecutionContext.of("trace-1", null));

            assertNotNull(result);
            assertInstanceOf(Map.class, result);

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertEquals(200, resultMap.get("code"));

            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) resultMap.get("data");
            assertEquals("markdown", dataMap.get("format"));
            assertNotNull(dataMap.get("content"));
            assertFalse(dataMap.containsKey("data"));
        }

        @Test
        @DisplayName("MCP list_models 应忽略 arguments，程序化参数走 Controller")
        void shouldIgnoreArgumentsBecauseProgrammaticCallersUseController() {
            when(semanticServiceResolver.getAllModelNames()).thenReturn(List.of("FactSalesQueryModel"));
            QueryModel mockQm = mockQueryModel("FS", "销售明细查询", "销售额、销量分析", null, null);
            when(queryModelLoader.getJdbcQueryModel(eq("FactSalesQueryModel"), any()))
                    .thenReturn(mockQm);
            SemanticMetadataResponse metadata = new SemanticMetadataResponse();
            Map<String, Object> fieldInfo = new java.util.LinkedHashMap<>();
            fieldInfo.put("models", Map.of("FactSalesQueryModel", Map.of("description", "订单号")));
            metadata.setData(Map.of(
                    "fields", Map.of("orderId", fieldInfo),
                    "models", Map.of("FactSalesQueryModel", Map.of("name", "销售明细查询"))
            ));
            when(semanticServiceResolver.getMetadata(any(), eq("json"), any())).thenReturn(metadata);

            Object result = listModelsTool.execute(
                    Map.of(
                            "format", "markdown",
                            "modelNames", List.of("FactSalesQueryModel"),
                            "visibleFields", List.of("orderId"),
                            "deniedColumns", List.of(Map.of("table", "fact_sales", "column", "secret_amount")),
                            "fieldLimit", 0
                    ),
                    ToolExecutionContext.of("trace-markdown", null)
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) resultMap.get("data");
            assertEquals("markdown", dataMap.get("format"));
            assertTrue(((String) dataMap.get("content")).contains("FactSalesQueryModel"));
            assertFalse(dataMap.containsKey("data"));
            verify(semanticServiceResolver).getAllModelNames();
        }

        @Test
        @DisplayName("应返回只包含模型名、简称和说明的 Markdown")
        void shouldContainModelInfoInMarkdown() {
            when(semanticServiceResolver.getAllModelNames()).thenReturn(List.of("FactSalesQueryModel"));

            QueryModel mockQm = mockQueryModel("FS", "销售明细查询", "销售额、销量分析", null, null);
            when(queryModelLoader.getJdbcQueryModel(eq("FactSalesQueryModel"), any()))
                    .thenReturn(mockQm);

            Object result = listModelsTool.execute(Map.of(), ToolExecutionContext.of("trace-2", null));

            String content = extractContent(result);
            assertTrue(content.contains("FactSalesQueryModel"), "应包含模型名称");
            assertTrue(content.contains("FS"), "应包含简称");
            assertTrue(content.contains("销售明细查询"), "应包含说明");
            assertFalse(content.contains("dataset.describe_model_internal"), "不应包含推荐下一步");
        }

        @Test
        @DisplayName("JSON catalog 应返回兼容 models 数组和 rich items")
        void shouldReturnModelsAndRichItems() {
            when(semanticServiceResolver.getAllModelNames()).thenReturn(List.of("FactSalesQueryModel", "HiddenModel"));
            QueryModel mockQm = mockQueryModel("FS", "销售明细查询", "销售额、销量分析", null, null);
            when(queryModelLoader.getJdbcQueryModel(eq("FactSalesQueryModel"), any()))
                    .thenReturn(mockQm);

            SemanticMetadataResponse metadata = new SemanticMetadataResponse();
            Map<String, Object> fieldInfo = new java.util.LinkedHashMap<>();
            fieldInfo.put("models", Map.of("FactSalesQueryModel", Map.of("description", "订单号")));
            metadata.setData(Map.of(
                    "fields", Map.of("orderId", fieldInfo),
                    "models", Map.of("FactSalesQueryModel", Map.of("name", "销售明细查询"))
            ));
            when(semanticServiceResolver.getMetadata(any(), eq("json"), any())).thenReturn(metadata);

            ModelCatalogService service = new ModelCatalogService(
                    semanticServiceResolver,
                    queryModelLoader,
                    new ObjectMapper()
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> catalog = (Map<String, Object>) service
                    .buildCatalogResponse(Map.of("format", "json"), null, null)
                    .get("data");

            assertEquals(List.of("FactSalesQueryModel"), catalog.get("models"));
            assertEquals(1, catalog.get("count"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) catalog.get("items");
            assertEquals("FactSalesQueryModel", items.get(0).get("model"));
            assertEquals(List.of("orderId"), items.get(0).get("fieldPreview"));
            assertEquals(1, items.get(0).get("fieldCount"));
            assertFalse(items.get(0).containsKey("recommendedNext"));
        }

        @Test
        @DisplayName("默认 catalog 应遵守配置的 model-list")
        void defaultCatalog_shouldRespectConfiguredModelList() {
            McpProperties properties = new McpProperties();
            properties.getSemantic().setModelList(List.of("FactOrderQueryModel"));

            QueryModel mockQm = mockQueryModel("FO", "订单查询", "订单事实查询", null, null);
            when(queryModelLoader.getJdbcQueryModel(eq("FactOrderQueryModel"), any()))
                    .thenReturn(mockQm);

            SemanticMetadataResponse metadata = new SemanticMetadataResponse();
            metadata.setData(Map.of(
                    "fields", Map.of(),
                    "models", Map.of("FactOrderQueryModel", Map.of("name", "订单查询"))
            ));
            when(semanticServiceResolver.getMetadata(any(), eq("json"), any())).thenReturn(metadata);

            ModelCatalogService service = new ModelCatalogService(
                    semanticServiceResolver,
                    queryModelLoader,
                    new ObjectMapper(),
                    properties
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> catalog = (Map<String, Object>) service
                    .buildCatalogResponse(Map.of("format", "json"), null, null)
                    .get("data");

            assertEquals(List.of("FactOrderQueryModel"), catalog.get("models"));
            verify(semanticServiceResolver, never()).getAllModelNames();
        }

        @Test
        @DisplayName("重复模型名应保序去重且 Markdown 不返回结构化 catalog")
        void duplicateModels_shouldBeDedupedAndRecommendNextOnce() {
            when(semanticServiceResolver.getAllModelNames())
                    .thenReturn(List.of("FactSalesQueryModel", "FactSalesQueryModel"));
            QueryModel mockQm = mockQueryModel("FS", "销售明细查询", "销售额、销量分析", null, null);
            when(queryModelLoader.getJdbcQueryModel(eq("FactSalesQueryModel"), any()))
                    .thenReturn(mockQm);

            Object result = listModelsTool.execute(Map.of(), ToolExecutionContext.of("trace-dedupe", null));

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) resultMap.get("data");
            assertFalse(dataMap.containsKey("data"));

            String content = (String) dataMap.get("content");
            assertEquals(1, occurrences(content, "FactSalesQueryModel"));
            assertEquals(0, occurrences(content, "dataset.describe_model_internal"));
        }

        @Test
        @DisplayName("不应包含 [field: 字段索引")
        void shouldNotContainFieldIndex() {
            when(semanticServiceResolver.getAllModelNames()).thenReturn(List.of("FactSalesQueryModel"));

            QueryModel mockQm = mockQueryModel("FS", "销售明细查询", "销售额分析", null, null);
            when(queryModelLoader.getJdbcQueryModel(eq("FactSalesQueryModel"), any()))
                    .thenReturn(mockQm);

            Object result = listModelsTool.execute(Map.of(), ToolExecutionContext.of("trace-3", null));

            String content = extractContent(result);
            assertFalse(content.contains("[field:"), "不应包含字段索引");
        }

        @Test
        @DisplayName("空模型列表应返回仅含表头的 Markdown")
        void emptyModelList_shouldReturnHeaderOnly() {
            when(semanticServiceResolver.getAllModelNames()).thenReturn(List.of());

            Object result = listModelsTool.execute(Map.of(), ToolExecutionContext.of("trace-4", null));

            String content = extractContent(result);
            assertTrue(content.contains("# Model Catalog"), "应包含标题");
            assertTrue(content.contains("No data models available."), "应提示无可用模型");
            assertFalse(content.contains("FactSalesQueryModel"), "不应包含模型数据行");
        }

        @Test
        @DisplayName("异常模型应被跳过而不影响其他模型")
        void exceptionModel_shouldBeSkipped() {
            when(semanticServiceResolver.getAllModelNames())
                    .thenReturn(List.of("GoodModel", "BadModel"));

            QueryModel goodQm = mockQueryModel("GM", "正常模型", "正常", null, null);
            when(queryModelLoader.getJdbcQueryModel(eq("GoodModel"), any()))
                    .thenReturn(goodQm);
            when(queryModelLoader.getJdbcQueryModel(eq("BadModel"), any()))
                    .thenThrow(new RuntimeException("Load failed"));

            Object result = listModelsTool.execute(Map.of(), ToolExecutionContext.of("trace-5", null));

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertEquals(200, resultMap.get("code"));

            String content = extractContent(result);
            assertTrue(content.contains("GoodModel"), "应包含正常模型");
            assertFalse(content.contains("BadModel"), "不应包含异常模型");
        }

        @Test
        @DisplayName("timeRole=business_date 应优先于其他 date role")
        void businessDateTimeRole_shouldTakePriority() {
            when(semanticServiceResolver.getAllModelNames()).thenReturn(List.of("TestModel"));

            // 创建两个维度：一个 event_date，一个 business_date
            DbDimension eventDim = mock(DbDimension.class);
            when(eventDim.getTimeRole()).thenReturn("event_date");
            lenient().when(eventDim.getEffectiveName()).thenReturn("eventDate");

            DbDimension businessDim = mock(DbDimension.class);
            when(businessDim.getTimeRole()).thenReturn("business_date");
            when(businessDim.getEffectiveName()).thenReturn("salesDate");

            DbQueryDimension eventQd = mock(DbQueryDimension.class);
            when(eventQd.getDimension()).thenReturn(eventDim);

            DbQueryDimension businessQd = mock(DbQueryDimension.class);
            when(businessQd.getDimension()).thenReturn(businessDim);

            QueryModel mockQm = mockQueryModel("TM", "测试模型", "测试", null, null);
            // event_date 排在前面，但 business_date 应优先
            when(mockQm.getQueryDimensions()).thenReturn(List.of(eventQd, businessQd));
            when(queryModelLoader.getJdbcQueryModel(eq("TestModel"), any()))
                    .thenReturn(mockQm);

            ModelCatalogService service = new ModelCatalogService(
                    semanticServiceResolver,
                    queryModelLoader,
                    new ObjectMapper()
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> catalog = (Map<String, Object>) service
                    .buildCatalogResponse(Map.of("format", "json", "fieldLimit", 10), null, null)
                    .get("data");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) catalog.get("items");

            assertEquals("salesDate$id", items.get(0).get("primaryTimeField"), "应使用 business_date 维度");
        }

        @Test
        @DisplayName("无 business_date 时应退化到包含 date 的 timeRole")
        void fallbackDateTimeRole_whenNoBusinessDate() {
            when(semanticServiceResolver.getAllModelNames()).thenReturn(List.of("TestModel"));

            DbDimension eventDim = mock(DbDimension.class);
            when(eventDim.getTimeRole()).thenReturn("event_date");
            when(eventDim.getEffectiveName()).thenReturn("eventDate");

            DbQueryDimension eventQd = mock(DbQueryDimension.class);
            when(eventQd.getDimension()).thenReturn(eventDim);

            QueryModel mockQm = mockQueryModel("TM", "测试模型", "测试", null, null);
            when(mockQm.getQueryDimensions()).thenReturn(List.of(eventQd));
            when(queryModelLoader.getJdbcQueryModel(eq("TestModel"), any()))
                    .thenReturn(mockQm);

            ModelCatalogService service = new ModelCatalogService(
                    semanticServiceResolver,
                    queryModelLoader,
                    new ObjectMapper()
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> catalog = (Map<String, Object>) service
                    .buildCatalogResponse(Map.of("format", "json", "fieldLimit", 10), null, null)
                    .get("data");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) catalog.get("items");

            assertEquals("eventDate$id", items.get(0).get("primaryTimeField"), "应退化到 event_date 维度");
        }

        @Test
        @DisplayName("ai.prompt 存在时应优先用于适用问题列")
        void aiPrompt_shouldOverrideDescription() {
            when(semanticServiceResolver.getAllModelNames()).thenReturn(List.of("TestModel"));

            AiObject aiObj = new AiObject();
            aiObj.setPrompt("销售额、订单量趋势分析");

            QueryModel mockQm = mockQueryModel("TM", "测试模型", "普通描述", aiObj, null);
            when(queryModelLoader.getJdbcQueryModel(eq("TestModel"), any()))
                    .thenReturn(mockQm);

            Object result = listModelsTool.execute(Map.of(), ToolExecutionContext.of("trace-8", null));

            String content = extractContent(result);
            assertTrue(content.contains("销售额、订单量趋势分析"), "应使用 AI prompt");
            assertFalse(content.contains("普通描述"), "不应使用 description");
        }

        @Test
        @DisplayName("Markdown 中换行和管道符应被清理")
        void markdownSpecialChars_shouldBeSanitized() {
            when(semanticServiceResolver.getAllModelNames()).thenReturn(List.of("TestModel"));

            QueryModel mockQm = mockQueryModel("TM", "说明\n含换行", "描述|含管道", null, null);
            when(queryModelLoader.getJdbcQueryModel(eq("TestModel"), any()))
                    .thenReturn(mockQm);

            Object result = listModelsTool.execute(Map.of(), ToolExecutionContext.of("trace-9", null));

            String content = extractContent(result);
            // 表格区域内不应有原始换行（已被替换为空格）
            // 也不应有原始管道符（已被替换为全角）
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.startsWith("| TestModel")) {
                    assertFalse(line.contains("描述|含"), "管道符应被替换");
                    assertTrue(line.contains("描述｜含"), "管道符应被替换为全角");
                }
            }
        }
    }

    // ==================== 辅助方法 ====================

    private QueryModel mockQueryModel(String shortAlias, String caption, String description,
                                        AiObject ai, List<DbQueryDimension> dims) {
        QueryModel qm = mock(QueryModel.class);
        when(qm.getShortAlias()).thenReturn(shortAlias);
        when(qm.getCaption()).thenReturn(caption);
        lenient().when(qm.getDescription()).thenReturn(description);
        when(qm.getAi()).thenReturn(ai);
        when(qm.getQueryDimensions()).thenReturn(dims);
        return qm;
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Object result) {
        Map<String, Object> resultMap = (Map<String, Object>) result;
        Map<String, Object> dataMap = (Map<String, Object>) resultMap.get("data");
        return (String) dataMap.get("content");
    }

    private static int occurrences(String text, String target) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }
}
