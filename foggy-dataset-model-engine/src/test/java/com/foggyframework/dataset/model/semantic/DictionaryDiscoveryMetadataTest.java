package com.foggyframework.dataset.model.semantic;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.common.query.DimensionDataQueryForm;
import com.foggyframework.dataset.model.common.result.DbDataItem;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.dictionary.DictionaryCaptionDbColumn;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.model.service.JdbcService;
import com.foggyframework.dataset.model.spi.DbQueryColumn;
import com.foggyframework.dataset.model.spi.QueryModel;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DictionaryDiscoveryMetadataTest extends EcommerceTestSupport {

    private static final String TEST_MODEL = "DictionaryDiscoveryOrderQueryModel";
    private static final String INTEGER_DICT_MODEL = "IntegerDictionaryOrderQueryModel";

    @Resource
    private SemanticServiceV3 semanticServiceV3;

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Resource
    private JdbcService jdbcService;

    @Test
    @DisplayName("JSON metadata 输出 orderStatus 的 dictionaryDiscovery 契约和模型别名")
    void jsonMetadata_includesDictionaryDiscoveryContractAndAliases() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Collections.singletonList(TEST_MODEL));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(
                request, "json", SemanticRequestContext.empty());

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) response.getData().get("fields");
        assertTrue(fields.containsKey("status"), "应暴露 status 字段");
        assertTrue(fields.containsKey("status$caption"), "dictRef 字段应自动暴露 caption");

        @SuppressWarnings("unchecked")
        Map<String, Object> orderStatus = (Map<String, Object>) fields.get("status");
        @SuppressWarnings("unchecked")
        Map<String, Object> discovery = (Map<String, Object>) orderStatus.get("dictionaryDiscovery");

        assertNotNull(discovery, "orderStatus 应包含 dictionaryDiscovery metadata");
        assertEquals(Boolean.TRUE, discovery.get("enabled"));
        assertEquals("group_by", discovery.get("strategy"));
        assertEquals(2, ((Number) discovery.get("maxValues")).intValue());
        assertEquals(3600L, ((Number) discovery.get("refreshTtlSeconds")).longValue());
        assertEquals("runtime_observed", discovery.get("valuesSource"));
        assertEquals("sampled", discovery.get("valuesStatus"));
        assertEquals(Boolean.TRUE, discovery.get("truncated"));
        assertNotNull(discovery.get("sampledAt"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> values = (List<Map<String, Object>>) discovery.get("values");
        assertNotNull(values, "应输出运行时发现值");
        assertEquals(2, values.size(), "应按 maxValues 截断");
        assertEquals("COMPLETED", values.get(0).get("value"), "应优先输出最高频真实状态");
        assertTrue(values.stream().anyMatch(v -> "COMPLETED".equals(v.get("value"))),
                "应包含数据库真实订单状态");
        assertTrue(values.stream().allMatch(v -> v.containsKey("count")),
                "group_by 策略应输出每个字典值的计数");
        assertTrue(((Number) values.get(0).get("count")).longValue()
                        >= ((Number) values.get(1).get("count")).longValue(),
                "group_by 发现值应按出现次数降序输出");

        @SuppressWarnings("unchecked")
        Map<String, Object> aliases = (Map<String, Object>) discovery.get("aliases");
        assertNotNull(aliases, "应输出模型治理别名");

        @SuppressWarnings("unchecked")
        Map<String, Object> openOrder = (Map<String, Object>) aliases.get("open_order");
        assertNotNull(openOrder, "应包含 open_order 别名");
        assertEquals(List.of("PENDING", "CONFIRMED", "PROCESSING"), openOrder.get("values"));

        @SuppressWarnings("unchecked")
        Map<String, Object> caption = (Map<String, Object>) fields.get("status$caption");
        assertEquals("STRING", caption.get("type"));
        assertEquals("dictionary-caption", caption.get("semanticRole"));
        assertEquals(Boolean.FALSE, caption.get("sortable"));
        assertEquals("status", caption.get("sourceField"));
        assertEquals("dictionary_discovery_order_status", caption.get("dictId"));
        assertFalse(caption.containsKey("dictItems"), "caption 自身不应重复生成 code 选择器");
    }

    @Test
    @DisplayName("Markdown metadata 输出运行时字典发现说明")
    void markdownMetadata_includesDictionaryDiscoverySummary() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Collections.singletonList(TEST_MODEL));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(
                request, "markdown", SemanticRequestContext.empty());

        String content = response.getContent();
        assertNotNull(content);
        assertTrue(content.contains("运行时字典发现"));
        assertTrue(content.contains("values:"));
        assertTrue(content.contains("open_order"));
        assertTrue(content.contains("PENDING"));
        assertTrue(content.contains("CONFIRMED"));
        assertTrue(content.contains("PROCESSING"));
        assertTrue(content.contains("status$caption"));
        assertTrue(content.contains("不支持排序"));
    }

    @Test
    @DisplayName("fieldAccess 裁剪后不暴露 orderStatus 的 dictionaryDiscovery")
    void fieldAccess_filtersDictionaryDiscoveryWithField() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Collections.singletonList(TEST_MODEL));

        SemanticRequestContext context = SemanticRequestContext.of(
                null, (ModelResultContext.SecurityContext) null, Set.of("orderId"));
        SemanticMetadataResponse response = semanticServiceV3.getMetadata(request, "json", context);

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) response.getData().get("fields");
        assertTrue(fields.containsKey("orderId"), "白名单字段应可见");
        assertFalse(fields.containsKey("status"), "非白名单字段不应暴露 discovery metadata");
        assertFalse(fields.containsKey("status$caption"), "caption 应继承原字段权限");
    }

    @Test
    @DisplayName("QM 仅引用 dictRef 原字段时自动生成 Java-backed caption")
    void queryModel_autoExpandsDictionaryCaption() {
        QueryModel queryModel = queryModelLoader.getJdbcQueryModel(TEST_MODEL, null);

        DbQueryColumn raw = queryModel.findJdbcQueryColumnByName("status", false);
        DbQueryColumn caption = queryModel.findJdbcQueryColumnByName("status$caption", false);
        assertNotNull(raw);
        assertNotNull(caption);
        assertNotNull(caption.getDecorate(DictionaryCaptionDbColumn.class));
        assertEquals("STRING", caption.getType().name());
    }

    @Test
    @DisplayName("QM 显式列出 caption 时保持兼容且不重复展开")
    void queryModel_explicitCaptionDoesNotDuplicate() {
        QueryModel queryModel = queryModelLoader.getJdbcQueryModel(
                "ExplicitDictionaryCaptionQueryModel", null);

        long captionCount = queryModel.getJdbcQueryColumns().stream()
                .filter(column -> "status$caption".equals(column.getName()))
                .count();
        assertEquals(1, captionCount);
        assertNotNull(queryModel.findJdbcQueryColumnByName("status$caption", false)
                .getDecorate(DictionaryCaptionDbColumn.class));
    }

    @Test
    @DisplayName("查询同时保留原始 code 并在 caption 列返回 label")
    void query_returnsRawCodeAndFormattedCaption() {
        SemanticQueryRequest request = request("orderId", "status", "status$caption");

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                TEST_MODEL, request, "execute", SemanticRequestContext.empty());

        assertNotNull(response.getItems());
        assertFalse(response.getItems().isEmpty());
        Map<String, String> labels = Map.of(
                "PENDING", "待处理",
                "CONFIRMED", "已确认",
                "PROCESSING", "处理中",
                "SHIPPED", "已发货",
                "COMPLETED", "已完成",
                "CANCELLED", "已取消",
                "REFUNDED", "已退款");
        assertTrue(response.getItems().stream().anyMatch(row -> labels.containsKey(String.valueOf(row.get("status")))));
        response.getItems().stream()
                .filter(row -> labels.containsKey(String.valueOf(row.get("status"))))
                .forEach(row -> assertEquals(labels.get(String.valueOf(row.get("status"))),
                        row.get("status$caption")));
    }

    @Test
    @DisplayName("caption label 过滤在 Java 层精确转回数据库 code")
    void query_captionFilterTranslatesLabelToCode() {
        SemanticQueryRequest request = request("orderId", "status", "status$caption");
        request.setSlice(List.of(slice("status$caption", "=", "待处理")));

        SqlGenerationResult sql = semanticQueryServiceV3.generateSql(
                TEST_MODEL, request, SemanticRequestContext.empty());
        assertTrue(sql.getParams().stream().anyMatch("PENDING"::equals));

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                TEST_MODEL, request, "execute", SemanticRequestContext.empty());
        assertFalse(response.getItems().isEmpty());
        response.getItems().forEach(row -> {
            assertEquals("PENDING", row.get("status"));
            assertEquals("待处理", row.get("status$caption"));
        });
    }

    @Test
    @DisplayName("原始字典字段过滤同时兼容既有 code 和注册 label")
    void query_rawDictionaryFieldAcceptsLabel() {
        SemanticQueryRequest byLabel = request("orderId", "status", "status$caption");
        byLabel.setSlice(List.of(slice("status", "=", "待处理")));
        SqlGenerationResult labelSql = semanticQueryServiceV3.generateSql(
                TEST_MODEL, byLabel, SemanticRequestContext.empty());
        assertTrue(labelSql.getParams().stream().anyMatch("PENDING"::equals));

        SemanticQueryRequest byCode = request("orderId", "status");
        byCode.setSlice(List.of(slice("status", "=", "PENDING")));
        SqlGenerationResult codeSql = semanticQueryServiceV3.generateSql(
                TEST_MODEL, byCode, SemanticRequestContext.empty());
        assertTrue(codeSql.getParams().stream().anyMatch("PENDING"::equals));

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                TEST_MODEL, byLabel, "execute", SemanticRequestContext.empty());
        assertFalse(response.getItems().isEmpty());
        response.getItems().forEach(row -> assertEquals("PENDING", row.get("status")));
    }

    @Test
    @DisplayName("INTEGER 字典 caption 转换和 label 过滤保持强类型 code")
    void query_integerDictionaryUsesTypedCodes() {
        SemanticQueryRequest request = request("orderId", "customerCode", "customerCode$caption");
        request.setSlice(List.of(slice("customerCode", "=", "一号客户")));

        SqlGenerationResult sql = semanticQueryServiceV3.generateSql(
                INTEGER_DICT_MODEL, request, SemanticRequestContext.empty());
        assertTrue(sql.getParams().stream().anyMatch(value -> Integer.valueOf(1).equals(value)));

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                INTEGER_DICT_MODEL, request, "execute", SemanticRequestContext.empty());
        assertFalse(response.getItems().isEmpty());
        response.getItems().forEach(row -> {
            assertEquals(1, ((Number) row.get("customerCode")).intValue());
            assertEquals("一号客户", row.get("customerCode$caption"));
        });
    }

    @Test
    @DisplayName("caption 分组仍按 code 分组并在输出阶段翻译 label")
    void query_groupedCaptionFormatsResult() {
        SemanticQueryRequest request = request("status$caption", "amount");
        request.setGroupBy(List.of(new SemanticQueryRequest.GroupByItem("status$caption", null)));

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                TEST_MODEL, request, "execute", SemanticRequestContext.empty());

        assertFalse(response.getItems().isEmpty());
        assertTrue(response.getItems().stream().anyMatch(row -> "已完成".equals(row.get("status$caption"))));
        assertTrue(response.getItems().stream().noneMatch(row -> "COMPLETED".equals(row.get("status$caption"))));
    }

    @Test
    @DisplayName("caption 不支持排序及非等值类过滤")
    void query_rejectsCaptionOrderingAndRangeFilter() {
        SemanticQueryRequest ordered = request("status$caption");
        SemanticQueryRequest.OrderItem order = new SemanticQueryRequest.OrderItem();
        order.setField("status$caption");
        order.setDir("asc");
        ordered.setOrderBy(List.of(order));

        RuntimeException orderError = assertThrows(RuntimeException.class,
                () -> semanticQueryServiceV3.queryModel(
                        TEST_MODEL, ordered, "execute", SemanticRequestContext.empty()));
        assertTrue(deepMessage(orderError).contains("DICT_CAPTION_ORDER_UNSUPPORTED"));

        SemanticQueryRequest ranged = request("status$caption");
        ranged.setSlice(List.of(slice("status$caption", ">", "待处理")));
        RuntimeException filterError = assertThrows(RuntimeException.class,
                () -> semanticQueryServiceV3.queryModel(
                        TEST_MODEL, ranged, "execute", SemanticRequestContext.empty()));
        assertTrue(deepMessage(filterError).contains("DICT_CAPTION_FILTER_UNSUPPORTED"));
    }

    @Test
    @DisplayName("原始字典 code 字段仍可按数据库值排序")
    void query_rawDictionaryFieldRemainsSortable() {
        SemanticQueryRequest request = request("customerCode");
        SemanticQueryRequest.OrderItem order = new SemanticQueryRequest.OrderItem();
        order.setField("customerCode");
        order.setDir("asc");
        request.setOrderBy(List.of(order));

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                INTEGER_DICT_MODEL, request, "execute", SemanticRequestContext.empty());

        assertFalse(response.getItems().isEmpty());
        List<Integer> values = response.getItems().stream()
                .map(row -> ((Number) row.get("customerCode")).intValue())
                .toList();
        for (int index = 1; index < values.size(); index++) {
            assertTrue(values.get(index - 1) <= values.get(index));
        }
    }

    @Test
    @DisplayName("静态字典字段 members 直接返回注册项且支持 caption 入口")
    void members_returnsStaticDictionaryItems() {
        DimensionDataQueryForm form = new DimensionDataQueryForm(TEST_MODEL, "status$caption");

        PagingResultImpl<DbDataItem> result = jdbcService.queryDimensionData(
                PagingRequest.buildPagingRequest(form, 20));

        assertEquals(7, result.getItems().size());
        assertEquals(7, result.getTotal());
        assertEquals("PENDING", result.getItems().get(0).getId());
        assertEquals("待处理", result.getItems().get(0).getCaption());
    }

    private SemanticQueryRequest request(String... columns) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of(columns));
        request.setLimit(20);
        return request;
    }

    private SemanticQueryRequest.SliceItem slice(String field, String op, Object value) {
        SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
        item.setField(field);
        item.setOp(op);
        item.setValue(value);
        return item;
    }

    private String deepMessage(Throwable error) {
        StringBuilder result = new StringBuilder();
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                result.append(current.getMessage()).append('\n');
            }
        }
        return result.toString();
    }
}
