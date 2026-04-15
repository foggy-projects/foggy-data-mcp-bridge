package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V3版本语义服务测试
 *
 * <p>测试 V3 版本的核心特性：</p>
 * <ul>
 *   <li>维度字段展开为独立的 $id 和 $caption 字段</li>
 *   <li>每个展开字段有独立的描述（格式说明等）</li>
 *   <li>查询时直接使用字段名，无需拼接后缀</li>
 * </ul>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("V3版本语义服务测试")
class SemanticServiceV3Test extends EcommerceTestSupport {

    @Resource
    private SemanticServiceV3 semanticServiceV3;

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    private static final String TEST_MODEL = "FactOrderQueryModel";

    // ==========================================
    // 元数据测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("V3元数据 - JSON格式应包含展开的维度字段")
    void testMetadata_JsonFormat_ShouldContainExpandedDimensionFields() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Collections.singletonList(TEST_MODEL));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(request, "json", SemanticRequestContext.empty());

        assertNotNull(response, "响应不应为空");
        assertEquals("json", response.getFormat(), "格式应为json");
        assertNotNull(response.getData(), "数据不应为空");

        Map<String, Object> data = response.getData();
        assertEquals("v3", data.get("version"), "版本应为v3");

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) data.get("fields");
        assertNotNull(fields, "字段信息不应为空");

        // 检查维度字段是否已展开
        // 应该有 customer$id 和 customer$caption 两个独立字段
        assertTrue(fields.containsKey("customer$id"), "应包含 customer$id 字段");
        assertTrue(fields.containsKey("customer$caption"), "应包含 customer$caption 字段");

        // 检查字段元数据
        @SuppressWarnings("unchecked")
        Map<String, Object> customerIdField = (Map<String, Object>) fields.get("customer$id");
        assertNotNull(customerIdField, "customer$id 字段信息不应为空");
        assertTrue(customerIdField.get("meta").toString().contains("维度ID"), "customer$id 应标记为维度ID");

        @SuppressWarnings("unchecked")
        Map<String, Object> customerCaptionField = (Map<String, Object>) fields.get("customer$caption");
        assertNotNull(customerCaptionField, "customer$caption 字段信息不应为空");
        assertTrue(customerCaptionField.get("meta").toString().contains("维度名称"), "customer$caption 应标记为维度名称");

        log.info("V3元数据字段数量: {}", fields.size());
        log.info("展开的字段: {}", fields.keySet());
    }

    @Test
    @Order(2)
    @DisplayName("V3元数据 - Markdown格式应包含展开的维度字段")
    void testMetadata_MarkdownFormat_ShouldContainExpandedDimensionFields() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Collections.singletonList(TEST_MODEL));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(request, "markdown", SemanticRequestContext.empty());

        assertNotNull(response, "响应不应为空");
        assertEquals("markdown", response.getFormat(), "格式应为markdown");
        assertNotNull(response.getContent(), "内容不应为空");

        String content = response.getContent();

        // 检查是否包含 V3 标识
//        assertTrue(content.contains("V3版本"), "应包含V3版本标识");

        // 检查是否包含展开的字段
        assertTrue(content.contains("customer$id"), "应包含 customer$id 字段");
        assertTrue(content.contains("customer$caption"), "应包含 customer$caption 字段");

        // 检查字段说明

        log.info("V3 Markdown 内容长度: {}", content.length());
        log.info("V3 Markdown 前500字符:\n{}", content.substring(0, Math.min(500, content.length())));
    }

    @Test
    @Order(3)
    @DisplayName("V3元数据 - 日期维度应包含格式说明")
    void testMetadata_DateDimension_ShouldContainFormatHint() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Collections.singletonList(TEST_MODEL));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(request, "json", SemanticRequestContext.empty());

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) response.getData().get("fields");

        // 检查日期维度的格式说明
        // 假设有 orderDate 维度
        if (fields.containsKey("orderDate$id")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dateIdField = (Map<String, Object>) fields.get("orderDate$id");
            String meta = dateIdField.get("meta").toString();
            log.info("orderDate$id meta: {}", meta);
            // 日期维度应有格式说明
            assertTrue(meta.contains("日期") || meta.contains("格式"), "日期维度应包含格式说明");
        }
    }

    // ==========================================
    // 查询测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("V3查询 - 直接使用展开的字段名")
    void testQuery_DirectlyUseExpandedFieldNames() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        // V3: 直接使用 $id 和 $caption，无需 AI 判断
        request.setColumns(Arrays.asList("orderId", "customer$caption", "customer$id", "amount"));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(TEST_MODEL, request, "execute", SemanticRequestContext.empty());

        assertNotNull(response, "响应不应为空");
        assertNotNull(response.getItems(), "数据项不应为空");
        assertTrue(response.getItems().size() > 0, "应返回数据");

        // 检查返回的数据包含所有请求的字段
        Map<String, Object> firstRow = response.getItems().get(0);
        assertTrue(firstRow.containsKey("orderId"), "应包含 orderId");
        assertTrue(firstRow.containsKey("customer$caption"), "应包含 customer$caption");
        assertTrue(firstRow.containsKey("customer$id"), "应包含 customer$id");
        assertTrue(firstRow.containsKey("amount"), "应包含 amount");

        log.info("V3查询返回 {} 条数据", response.getItems().size());
        log.info("第一行数据: {}", firstRow);
    }

    @Test
    @Order(11)
    @DisplayName("V3查询 - 使用展开字段进行过滤")
    void testQuery_FilterWithExpandedFields() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("orderId", "customer$caption", "amount"));

        // 使用 $caption 字段进行模糊查询
        SemanticQueryRequest.SliceItem slice = new SemanticQueryRequest.SliceItem();
        slice.setField("customer$caption");
        slice.setOp("like");
        slice.setValue("客户");  // 假设有包含"客户"的客户名称
        request.setSlice(Collections.singletonList(slice));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(TEST_MODEL, request, "execute", SemanticRequestContext.empty());

        assertNotNull(response, "响应不应为空");
        // 可能没有匹配的数据，但不应报错
        log.info("V3过滤查询返回 {} 条数据", response.getItems() != null ? response.getItems().size() : 0);
    }

    @Test
    @Order(12)
    @DisplayName("V3查询 - 使用展开字段进行分组")
    void testQuery_GroupByWithExpandedFields() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("customer$caption", "customer$id", "amount"));

        List<SemanticQueryRequest.GroupByItem> groupByItems = new ArrayList<>();

        SemanticQueryRequest.GroupByItem idGroup = new SemanticQueryRequest.GroupByItem();
        idGroup.setField("customer$id");
        groupByItems.add(idGroup);

        SemanticQueryRequest.GroupByItem captionGroup = new SemanticQueryRequest.GroupByItem();
        captionGroup.setField("customer$caption");
        groupByItems.add(captionGroup);

        SemanticQueryRequest.GroupByItem sumGroup = new SemanticQueryRequest.GroupByItem();
        sumGroup.setField("amount");
        sumGroup.setAgg("SUM");
        groupByItems.add(sumGroup);

        request.setGroupBy(groupByItems);
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(TEST_MODEL, request, "execute", SemanticRequestContext.empty());

        assertNotNull(response, "响应不应为空");
        assertNotNull(response.getItems(), "数据项不应为空");

        log.info("V3分组查询返回 {} 条数据", response.getItems().size());
        if (!response.getItems().isEmpty()) {
            log.info("第一行分组数据: {}", response.getItems().get(0));
        }
    }

    @Test
    @Order(13)
    @DisplayName("V3查询 - 使用展开字段进行排序")
    void testQuery_OrderByWithExpandedFields() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("orderId", "customer$caption", "amount"));

        // 使用 $id 字段排序
        SemanticQueryRequest.OrderItem orderItem = new SemanticQueryRequest.OrderItem();
        orderItem.setField("customer$id");  // V3: 直接使用展开的字段名
        orderItem.setDir("ASC");
        request.setOrderBy(Collections.singletonList(orderItem));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(TEST_MODEL, request, "execute", SemanticRequestContext.empty());

        assertNotNull(response, "响应不应为空");
        assertNotNull(response.getItems(), "数据项不应为空");

        log.info("V3排序查询返回 {} 条数据", response.getItems().size());
    }

    // ==========================================
    // 验证测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("V3验证 - 检测不存在的字段")
    void testValidate_DetectNonExistentFields() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("amount", "nonExistentField$id"));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryServiceV3.validateQuery(TEST_MODEL, request, SemanticRequestContext.empty());

        assertNotNull(response, "响应不应为空");
        assertNotNull(response.getWarnings(), "应有警告信息");

        String warningsStr = String.join(", ", response.getWarnings());
        log.info("验证警告: {}", warningsStr);

        assertTrue(warningsStr.contains("nonExistentField"), "应警告不存在的字段");
    }

    @Test
    @Order(21)
    @DisplayName("V3验证 - 检测 groupBy 字段未在 columns 中")
    void testValidate_DetectGroupByFieldMissingInColumns() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(Arrays.asList("amount"));  // 缺少 customer$id

        SemanticQueryRequest.GroupByItem groupByItem = new SemanticQueryRequest.GroupByItem();
        groupByItem.setField("customer$id");
        request.setGroupBy(Collections.singletonList(groupByItem));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryServiceV3.validateQuery(TEST_MODEL, request, SemanticRequestContext.empty());

        assertNotNull(response, "响应不应为空");
        assertNotNull(response.getWarnings(), "应有警告信息");

        String warningsStr = String.join(", ", response.getWarnings());
        log.info("验证警告: {}", warningsStr);

        assertTrue(warningsStr.contains("customer$id") && warningsStr.contains("columns"),
                "应警告 groupBy 字段未在 columns 中");
    }

    // ==========================================
    // 维度属性过滤测试
    // ==========================================

    @Test
    @Order(25)
    @DisplayName("BUG修复 - Markdown元数据应只包含QM暴露的维度属性，不应包含TM中未暴露的属性")
    void testMetadata_Markdown_ShouldOnlyContainQmExposedDimensionProperties() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Collections.singletonList(TEST_MODEL));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(request, "markdown", SemanticRequestContext.empty());

        assertNotNull(response, "响应不应为空");
        String content = response.getContent();
        assertNotNull(content, "内容不应为空");

        // QM中暴露的客户维度属性 — 这些应该出现在输出中
        assertTrue(content.contains("customer$customerType"), "应包含QM暴露的 customer$customerType");
        assertTrue(content.contains("customer$gender"), "应包含QM暴露的 customer$gender");
        assertTrue(content.contains("customer$ageGroup"), "应包含QM暴露的 customer$ageGroup");
        assertTrue(content.contains("customer$province"), "应包含QM暴露的 customer$province");
        assertTrue(content.contains("customer$city"), "应包含QM暴露的 customer$city");
        assertTrue(content.contains("customer$memberLevel"), "应包含QM暴露的 customer$memberLevel");

        // QM中未暴露的客户维度属性 — 这些不应该出现在输出中
        // customer$customerId 在TM维度属性中存在，但QM的columnGroups没有引用它
        assertFalse(content.contains("customer$customerId"),
                "不应包含QM未暴露的 customer$customerId，否则用户使用该字段查询会报错: " +
                "Cannot find column customer$customerId in query model");

        log.info("维度属性过滤验证通过");
    }

    @Test
    @Order(26)
    @DisplayName("BUG修复 - JSON元数据应只包含QM暴露的维度属性")
    void testMetadata_Json_ShouldOnlyContainQmExposedDimensionProperties() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Collections.singletonList(TEST_MODEL));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(request, "json", SemanticRequestContext.empty());

        assertNotNull(response, "响应不应为空");
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) response.getData().get("fields");
        assertNotNull(fields, "字段信息不应为空");

        // QM中暴露的 — 应存在
        assertTrue(fields.containsKey("customer$customerType"), "应包含QM暴露的 customer$customerType");
        assertTrue(fields.containsKey("customer$gender"), "应包含QM暴露的 customer$gender");

        // QM中未暴露的 — 不应存在
        assertFalse(fields.containsKey("customer$customerId"),
                "不应包含QM未暴露的 customer$customerId");

        log.info("JSON元数据维度属性过滤验证通过");
    }

    @Test
    @Order(27)
    @DisplayName("BUG修复 - 多模型元数据应只包含QM暴露的维度属性")
    void testMetadata_MultiModel_ShouldOnlyContainQmExposedDimensionProperties() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(Arrays.asList(TEST_MODEL, "FactSalesQueryModel"));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(request, "markdown", SemanticRequestContext.empty());

        assertNotNull(response, "响应不应为空");
        String content = response.getContent();
        assertNotNull(content, "内容不应为空");

        // QM中暴露的应存在
        assertTrue(content.contains("customer$customerType"), "应包含QM暴露的 customer$customerType");

        // QM中未暴露的不应存在
        assertFalse(content.contains("customer$customerId"),
                "不应包含QM未暴露的 customer$customerId");

        log.info("多模型维度属性过滤验证通过");
    }

    // ==========================================
    // 对比 V2 和 V3
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("V3特性 - 无需后缀归一化处理")
    void testV3Feature_NoSuffixNormalizationNeeded() {
        // V3 的核心优势：字段名直接使用，无需判断后缀
        // 这个测试验证 V3 不会对字段名做额外处理

        SemanticQueryRequest request = new SemanticQueryRequest();
        // 直接混用 $id 和 $caption，V3 不会自动补全或转换
        request.setColumns(Arrays.asList("customer$caption", "amount"));
        // 只在 columns 中有 $caption，没有 $id，V3 不会强制对齐
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(TEST_MODEL, request, "execute", SemanticRequestContext.empty());

        assertNotNull(response, "响应不应为空");
        assertNotNull(response.getItems(), "数据项不应为空");

        // V3 应该没有自动补全的警告
        if (response.getWarnings() != null) {
            String warningsStr = String.join(", ", response.getWarnings());
            assertFalse(warningsStr.contains("自动补充"), "V3 不应有自动补充警告");
        }

        log.info("V3 特性验证通过：字段直接使用，无自动补全");
    }

    // ==========================================
    // metadata fieldAccess 列权限裁剪测试
    // ==========================================

    @Nested
    @DisplayName("metadata fieldAccess 裁剪")
    class MetadataFieldAccessTests {

        @Test
        @DisplayName("JSON metadata — fieldAccess 限制后只包含白名单内字段")
        void metadata_json_withFieldAccess_filtersFields() {
            SemanticMetadataRequest request = new SemanticMetadataRequest();
            request.setQmModels(Collections.singletonList(TEST_MODEL));

            // 获取无限制的完整 metadata 作为基线
            SemanticMetadataResponse fullResponse = semanticServiceV3.getMetadata(
                    request, "json", SemanticRequestContext.empty());
            Map<String, Object> fullData = fullResponse.getData();
            @SuppressWarnings("unchecked")
            Map<String, Object> fullFields = (Map<String, Object>) fullData.get("fields");
            assertTrue(fullFields.size() > 2, "基线 metadata 应包含多个字段");

            // 获取 fieldAccess 限制后的 metadata
            // 只允许 orderId 和 salesAmount
            Set<String> allowed = Set.of("orderId", "salesAmount");
            SemanticRequestContext restrictedCtx = SemanticRequestContext.of(null, (ModelResultContext.SecurityContext) null, allowed);
            SemanticMetadataResponse restrictedResponse = semanticServiceV3.getMetadata(
                    request, "json", restrictedCtx);
            Map<String, Object> restrictedData = restrictedResponse.getData();
            @SuppressWarnings("unchecked")
            Map<String, Object> restrictedFields = (Map<String, Object>) restrictedData.get("fields");

            // 受限 metadata 应只包含白名单内的字段
            assertTrue(restrictedFields.size() < fullFields.size(),
                    "受限 metadata 字段数应少于完整 metadata: restricted=" + restrictedFields.size() + ", full=" + fullFields.size());
            for (String fieldName : restrictedFields.keySet()) {
                // 字段名要么在白名单内，要么是白名单字段的维度展开（如 orderId → 不适用，salesAmount → 不适用）
                // 实际上度量/属性字段名直接匹配
                assertTrue(allowed.contains(fieldName) || allowed.stream().anyMatch(a -> fieldName.startsWith(a + "$")),
                        "受限 metadata 不应包含白名单外的字段: " + fieldName);
            }
        }

        @Test
        @DisplayName("JSON metadata — fieldAccess 为 null 时所有字段可见（向后兼容）")
        void metadata_json_noFieldAccess_allFieldsVisible() {
            SemanticMetadataRequest request = new SemanticMetadataRequest();
            request.setQmModels(Collections.singletonList(TEST_MODEL));

            SemanticMetadataResponse response = semanticServiceV3.getMetadata(
                    request, "json", SemanticRequestContext.empty());
            Map<String, Object> data = response.getData();
            @SuppressWarnings("unchecked")
            Map<String, Object> fields = (Map<String, Object>) data.get("fields");

            assertNotNull(fields);
            assertFalse(fields.isEmpty(), "fieldAccess=null 时所有字段应可见");
        }

        @Test
        @DisplayName("Markdown metadata — fieldAccess 限制后输出不含被拒字段")
        void metadata_markdown_withFieldAccess_filtersFields() {
            SemanticMetadataRequest request = new SemanticMetadataRequest();
            request.setQmModels(Collections.singletonList(TEST_MODEL));

            // 获取无限制的完整 markdown
            SemanticMetadataResponse fullResponse = semanticServiceV3.getMetadata(
                    request, "markdown", SemanticRequestContext.empty());
            String fullContent = fullResponse.getContent();
            assertNotNull(fullContent);

            // 获取限制后的 markdown — 只允许 orderId
            Set<String> allowed = Set.of("orderId");
            SemanticRequestContext restrictedCtx = SemanticRequestContext.of(null, (ModelResultContext.SecurityContext) null, allowed);
            SemanticMetadataResponse restrictedResponse = semanticServiceV3.getMetadata(
                    request, "markdown", restrictedCtx);
            String restrictedContent = restrictedResponse.getContent();

            assertTrue(restrictedContent.length() < fullContent.length(),
                    "受限 markdown 应比完整 markdown 短");
        }

        @Test
        @DisplayName("JSON metadata — fieldAccess 空集合时字段区域为空")
        void metadata_json_emptyFieldAccess_noFields() {
            SemanticMetadataRequest request = new SemanticMetadataRequest();
            request.setQmModels(Collections.singletonList(TEST_MODEL));

            Set<String> empty = Set.of();
            SemanticRequestContext ctx = SemanticRequestContext.of(null, (ModelResultContext.SecurityContext) null, empty);
            SemanticMetadataResponse response = semanticServiceV3.getMetadata(request, "json", ctx);
            Map<String, Object> data = response.getData();
            @SuppressWarnings("unchecked")
            Map<String, Object> fields = (Map<String, Object>) data.get("fields");

            assertTrue(fields.isEmpty(), "fieldAccess 为空集合时不应暴露任何字段");
        }
    }
}
