package com.foggyframework.dataset.jdbc.model.semantic;

import com.foggyframework.dataset.jdbc.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.jdbc.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.jdbc.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.jdbc.model.semantic.service.SemanticQueryServiceV3;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import jakarta.annotation.Resource;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 语义查询校验测试
 *
 * <p>测试 SemanticQueryServiceV3 的参数校验和自动补全功能：
 * 1. columns 和 groupBy 的自动对齐补全
 * 2. 错误列名的校验
 * 3. 维度字段后缀的校验
 * </p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("语义查询校验测试 - SemanticQueryServiceV3")
class SemanticQueryValidationTest extends EcommerceTestSupport {

    @Resource
    private SemanticQueryServiceV3 semanticQueryService;

    private static final String TEST_MODEL = "FactOrderQueryModel";

    // ==========================================
    // columns 和 groupBy 自动对齐测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("自动补全 - columns有$caption，groupBy有$id，应自动对齐")
    void testAutoAlign_ColumnsCaptionGroupById() {
        // columns: ["customer$caption", "totalAmount"]
        // groupBy: [{name:"customer$id"}]
        // 期望补全后：
        // columns: ["customer$caption", "totalAmount", "customer$id"]
        // groupBy: [{name:"customer$id"}, {name:"customer$caption"}]

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("customer$caption", "totalAmount"));

        SemanticQueryRequest.GroupByItem groupByItem = new SemanticQueryRequest.GroupByItem();
        groupByItem.setField("customer$id");
        request.setGroupBy(Collections.singletonList(groupByItem));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryService.queryModel(TEST_MODEL, request, "execute");

        assertNotNull(response, "响应不应为空");
        assertNotNull(response.getWarnings(), "应有警告信息");

        // 检查警告中包含自动补全的信息
        String warningsStr = String.join(", ", response.getWarnings());
        log.info("自动补全警告: {}", warningsStr);

        assertTrue(warningsStr.contains("自动补充"), "应包含自动补充的警告");
    }

    @Test
    @Order(2)
    @DisplayName("自动补全 - columns有$id，groupBy有$caption，应双向补全")
    void testAutoAlign_ColumnsIdGroupByCaption() {
        // columns: ["customer$id", "totalAmount"]
        // groupBy: [{name:"customer$caption"}]
        //
        // V3 实现逻辑：
        // - columns 有 customer$id，groupBy 有 customer$caption
        // - alignColumnsAndGroupBy 会检测到不对齐
        // - 应该补全 customer$caption 到 columns，customer$id 到 groupBy

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("customer$id", "totalAmount"));

        SemanticQueryRequest.GroupByItem groupByItem = new SemanticQueryRequest.GroupByItem();
        groupByItem.setField("customer$caption");
        request.setGroupBy(Collections.singletonList(groupByItem));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryService.queryModel(TEST_MODEL, request, "execute");

        assertNotNull(response, "响应不应为空");
        // V3 会触发双向补全
        assertNotNull(response.getWarnings(), "应有警告信息");

        String warningsStr = String.join(", ", response.getWarnings());
        log.info("警告信息: {}", warningsStr);

        // 验证双向补全警告
        assertTrue(warningsStr.contains("columns 自动补充字段 customer$caption"),
                "应补充 customer$caption 到 columns");
        assertTrue(warningsStr.contains("groupBy 自动补充字段 customer$id"),
                "应补充 customer$id 到 groupBy");

        // 验证查询成功返回数据
        assertNotNull(response.getItems(), "应返回数据");
        log.info("查询返回 {} 条数据", response.getItems().size());
    }

    @Test
    @Order(3)
    @DisplayName("自动补全 - columns有$caption，groupBy有$id，应触发双向补全")
    void testAutoAlign_RealAlignment() {
        // columns: ["customer$caption", "totalAmount"]
        // groupBy: [{name:"customer$id"}]  (注意：不是 $caption，所以 normalizeGroupBy 不会自动添加)
        //
        // 这个场景会真正触发 alignColumnsAndGroupBy：
        // - columns 有 customer$caption (suffix = "caption")
        // - groupBy 有 customer$id (suffix = "id")
        // 应该补全：
        // - customer$id 到 columns
        // - customer$caption 到 groupBy

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("customer$caption", "totalAmount"));

        SemanticQueryRequest.GroupByItem groupByItem = new SemanticQueryRequest.GroupByItem();
        groupByItem.setField("customer$id");
        request.setGroupBy(Collections.singletonList(groupByItem));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryService.queryModel(TEST_MODEL, request, "execute");

        assertNotNull(response, "响应不应为空");
        assertNotNull(response.getWarnings(), "应有警告信息");

        String warningsStr = String.join(", ", response.getWarnings());
        log.info("自动补全警告: {}", warningsStr);

        // 验证双向补全警告
        assertTrue(warningsStr.contains("columns 自动补充字段 customer$id"),
                "应补充 customer$id 到 columns");
        assertTrue(warningsStr.contains("groupBy 自动补充字段 customer$caption"),
                "应补充 customer$caption 到 groupBy");
    }

    @Test
    @Order(4)
    @DisplayName("自动补全 - 度量字段不需要出现在columns中")
    void testAutoAlign_MeasureFieldNotRequired() {
        // columns: ["customer$caption"]
        // groupBy: [{name:"customer$id"}, {name:"totalAmount", type:"SUM"}]
        // 度量字段 totalAmount 有聚合类型，不需要出现在 columns 中

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("customer$caption"));

        List<SemanticQueryRequest.GroupByItem> groupByItems = new ArrayList<>();

        SemanticQueryRequest.GroupByItem dimensionItem = new SemanticQueryRequest.GroupByItem();
        dimensionItem.setField("customer$id");
        groupByItems.add(dimensionItem);

        SemanticQueryRequest.GroupByItem measureItem = new SemanticQueryRequest.GroupByItem();
        measureItem.setField("totalAmount");
        measureItem.setAgg("SUM");
        groupByItems.add(measureItem);

        request.setGroupBy(groupByItems);
        request.setLimit(10);

        // 不应抛出异常，度量字段不需要在 columns 中
        SemanticQueryResponse response = semanticQueryService.queryModel(TEST_MODEL, request, "execute");

        assertNotNull(response, "响应不应为空");
        log.info("度量字段测试通过，返回 {} 条数据", response.getItems() != null ? response.getItems().size() : 0);
    }

    @Test
    @Order(5)
    @DisplayName("自动补全 - 已对齐的字段不应重复补全")
    void testAutoAlign_AlreadyAligned() {
        // columns: ["customer$caption", "customer$id", "totalAmount"]
        // groupBy: [{name:"customer$id"}, {name:"customer$caption"}]
        // 已经对齐，不应有补全警告

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("customer$caption", "customer$id", "totalAmount"));

        List<SemanticQueryRequest.GroupByItem> groupByItems = new ArrayList<>();

        SemanticQueryRequest.GroupByItem idItem = new SemanticQueryRequest.GroupByItem();
        idItem.setField("customer$id");
        groupByItems.add(idItem);

        SemanticQueryRequest.GroupByItem captionItem = new SemanticQueryRequest.GroupByItem();
        captionItem.setField("customer$caption");
        groupByItems.add(captionItem);

        request.setGroupBy(groupByItems);
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryService.queryModel(TEST_MODEL, request, "execute");

        assertNotNull(response, "响应不应为空");

        // 检查是否没有自动补全的警告
        if (response.getWarnings() != null) {
            String warningsStr = String.join(", ", response.getWarnings());
            log.info("警告信息: {}", warningsStr);
            assertFalse(warningsStr.contains("自动补充字段 customer"), "已对齐的字段不应有补全警告");
        }
    }

    // ==========================================
    // groupBy 字段缺失校验测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("校验失败 - groupBy字段不在columns中")
    void testValidation_GroupByFieldMissingInColumns() {
        // columns: ["totalAmount"]  (没有 product 字段)
        // groupBy: [{name:"product$id"}]
        // 应抛出异常

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("totalAmount"));

        SemanticQueryRequest.GroupByItem groupByItem = new SemanticQueryRequest.GroupByItem();
        groupByItem.setField("product$id");
        request.setGroupBy(Collections.singletonList(groupByItem));
        request.setLimit(10);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            semanticQueryService.queryModel(TEST_MODEL, request, "execute");
        });

        String errorMsg = exception.getMessage();
        log.info("预期的错误信息: {}", errorMsg);

        assertTrue(errorMsg.contains("groupBy") && errorMsg.contains("columns"),
                "错误信息应包含 groupBy 和 columns 关键字");
        assertTrue(errorMsg.contains("product"), "错误信息应包含缺失的字段名");
    }

    // ==========================================
    // 错误列名校验测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("校验失败 - 列名不存在")
    void testValidation_InvalidColumnName() {
        // columns 中包含不存在的列名

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("totalAmount", "nonExistentColumn"));
        request.setLimit(10);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            semanticQueryService.queryModel(TEST_MODEL, request, "execute");
        });

        String errorMsg = exception.getMessage();
        log.info("预期的错误信息: {}", errorMsg);

        assertTrue(errorMsg.contains("nonExistentColumn") || errorMsg.toLowerCase().contains("not found")
                || errorMsg.contains("不存在") || errorMsg.contains("找不到"),
                "错误信息应指明无效的列名");
    }

    @Test
    @Order(21)
    @DisplayName("校验失败 - groupBy列名不存在")
    void testValidation_InvalidGroupByColumnName() {
        // groupBy 中包含不存在的列名

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("totalAmount", "invalidField$id"));

        SemanticQueryRequest.GroupByItem groupByItem = new SemanticQueryRequest.GroupByItem();
        groupByItem.setField("invalidField$id");
        request.setGroupBy(Collections.singletonList(groupByItem));
        request.setLimit(10);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            semanticQueryService.queryModel(TEST_MODEL, request, "execute");
        });

        String errorMsg = exception.getMessage();
        log.info("预期的错误信息: {}", errorMsg);

        assertTrue(errorMsg.contains("invalidField") || errorMsg.toLowerCase().contains("not found")
                || errorMsg.contains("不存在") || errorMsg.contains("找不到"),
                "错误信息应指明无效的字段名");
    }

    @Test
    @Order(22)
    @DisplayName("校验失败 - slice条件列名不存在")
    void testValidation_InvalidSliceColumnName() {
        // slice 中包含不存在的列名

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("totalAmount"));

        SemanticQueryRequest.SliceItem sliceItem = new SemanticQueryRequest.SliceItem();
        sliceItem.setField("nonExistentField");
        sliceItem.setOp("=");
        sliceItem.setValue("test");
        request.setSlice(Collections.singletonList(sliceItem));
        request.setLimit(10);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            semanticQueryService.queryModel(TEST_MODEL, request, "execute");
        });

        String errorMsg = exception.getMessage();
        log.info("预期的错误信息: {}", errorMsg);

        assertTrue(errorMsg.contains("nonExistentField") || errorMsg.toLowerCase().contains("not found")
                || errorMsg.contains("不存在") || errorMsg.contains("找不到"),
                "错误信息应指明无效的过滤条件字段名");
    }

    // ==========================================
    // 维度字段后缀校验测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("维度字段无后缀 - orderBy自动补充$id")
    void testDimensionSuffix_OrderByAutoAddId() {
        // orderBy 中维度字段没有后缀，应自动补充 $id

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("customer$caption", "totalAmount"));

        SemanticQueryRequest.OrderItem orderItem = new SemanticQueryRequest.OrderItem();
        orderItem.setField("customer");  // 没有 $id 或 $caption 后缀
        orderItem.setDir("ASC");
        request.setOrderBy(Collections.singletonList(orderItem));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryService.queryModel(TEST_MODEL, request, "execute");

        assertNotNull(response, "响应不应为空");

        // 检查警告信息中是否包含自动补充的说明
        if (response.getWarnings() != null) {
            String warningsStr = String.join(", ", response.getWarnings());
            log.info("警告信息: {}", warningsStr);
            assertTrue(warningsStr.contains("orderBy") && warningsStr.contains("自动补充"),
                    "应有 orderBy 自动补充的警告");
        }
    }

    @Test
    @Order(31)
    @DisplayName("度量字段无后缀 - orderBy保持原样")
    void testMeasureSuffix_OrderByKeepOriginal() {
        // orderBy 中度量字段没有后缀，应保持原样（度量不需要后缀）

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("customer$caption", "totalAmount"));

        SemanticQueryRequest.OrderItem orderItem = new SemanticQueryRequest.OrderItem();
        orderItem.setField("totalAmount");  // 度量字段，不需要后缀
        orderItem.setDir("DESC");
        request.setOrderBy(Collections.singletonList(orderItem));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryService.queryModel(TEST_MODEL, request, "execute");

        assertNotNull(response, "响应不应为空");

        // 度量字段不应有自动补充的警告
        if (response.getWarnings() != null) {
            String warningsStr = String.join(", ", response.getWarnings());
            log.info("警告信息: {}", warningsStr);
            assertFalse(warningsStr.contains("totalAmount") && warningsStr.contains("自动补充"),
                    "度量字段不应被自动补充后缀");
        }
    }

    // ==========================================
    // 正常查询场景（确保不影响正常功能）
    // ==========================================

    @Test
    @Order(100)
    @DisplayName("正常查询 - 简单明细查询")
    void testNormalQuery_SimpleDetail() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("orderId", "customer$caption", "totalAmount"));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryService.queryModel(TEST_MODEL, request, "execute");

        assertNotNull(response, "响应不应为空");
        assertNotNull(response.getItems(), "数据项不应为空");
        assertTrue(response.getItems().size() > 0, "应返回数据");

        log.info("简单查询返回 {} 条数据", response.getItems().size());
    }

    @Test
    @Order(101)
    @DisplayName("正常查询 - 分组聚合查询")
    void testNormalQuery_GroupByAggregation() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("customer$caption", "customer$id"));

        List<SemanticQueryRequest.GroupByItem> groupByItems = new ArrayList<>();

        SemanticQueryRequest.GroupByItem dimItem = new SemanticQueryRequest.GroupByItem();
        dimItem.setField("customer$id");
        groupByItems.add(dimItem);

        SemanticQueryRequest.GroupByItem dimCaptionItem = new SemanticQueryRequest.GroupByItem();
        dimCaptionItem.setField("customer$caption");
        groupByItems.add(dimCaptionItem);

        SemanticQueryRequest.GroupByItem measureItem = new SemanticQueryRequest.GroupByItem();
        measureItem.setField("totalAmount");
        measureItem.setAgg("SUM");
        groupByItems.add(measureItem);

        request.setGroupBy(groupByItems);
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryService.queryModel(TEST_MODEL, request, "execute");

        assertNotNull(response, "响应不应为空");
        assertNotNull(response.getItems(), "数据项不应为空");

        log.info("分组聚合查询返回 {} 条数据", response.getItems().size());
    }
}
