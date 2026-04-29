package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.core.ex.ExRuntimeExceptionImpl;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 8.4.0.beta backlog B-03 strict path · v1.3 引擎拒绝裸 dimension 引用。
 *
 * <p>镜像 Python 端
 * {@code tests/test_dataset_model/test_strict_column_resolution.py}
 * 的 T1-T10 矩阵中可在 Java 端单独验证的子集。本批仅覆盖**裸维度
 * 拒绝**与**$attr 接受**两条主路径；并覆盖 Python T4 ★
 * user-alias 透传等价行为。</p>
 *
 * <p>使用 {@code FactSalesQueryModel} 的 {@code product} 维度
 * 作为测试目标（FK-style dim with {@code dim_product} join，自带
 * {@code $id} / {@code $caption} 属性）。</p>
 *
 * <p>Cross-end parity: error code prefix
 * {@code COLUMN_FIELD_NOT_FOUND} 与 Python 端
 * {@code DbTableModelImpl.resolve_field_strict} 一致（A2-1 contract）。</p>
 */
@DisplayName("v1.3 strict bare-dimension rejection (B-03 Java side)")
class StrictBareDimensionRejectionTest extends EcommerceTestSupport {

    private static final String SALES_MODEL = "FactSalesQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryService;

    @Test
    @DisplayName("T1 · 裸 dimension 引用应抛 COLUMN_FIELD_NOT_FOUND + hint $caption")
    void t1_bareDimensionRejectedWithHint() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("product"));  // bare dim — must fail
        request.setLimit(10);

        ExRuntimeExceptionImpl ex = assertThrows(ExRuntimeExceptionImpl.class, () ->
                semanticQueryService.queryModel(
                        SALES_MODEL,
                        request,
                        "execute",
                        SemanticRequestContext.empty()));

        String msg = ex.getMessage();
        assertNotNull(msg);
        assertTrue(msg.contains("COLUMN_FIELD_NOT_FOUND"),
                "expected COLUMN_FIELD_NOT_FOUND prefix, got: " + msg);
        assertTrue(msg.contains("'product'"),
                "expected dim name in error, got: " + msg);
        assertTrue(msg.contains("did you mean 'product$caption'"),
                "expected hint string, got: " + msg);

        Object item = ex.getItem();
        assertInstanceOf(Map.class, item);
        @SuppressWarnings("unchecked")
        Map<String, Object> errorDetail = (Map<String, Object>) item;
        assertEquals("COLUMN_FIELD_NOT_FOUND", errorDetail.get("errorCode"));
        assertEquals(SALES_MODEL, errorDetail.get("model"));
        assertEquals("product", errorDetail.get("invalidField"));
        assertInstanceOf(List.class, errorDetail.get("suggestions"));
        @SuppressWarnings("unchecked")
        List<String> suggestions = (List<String>) errorDetail.get("suggestions");
        assertTrue(suggestions.contains("product$caption"));
        assertTrue(suggestions.contains("product$id"));
    }

    @Test
    @DisplayName("T3 · product$caption 接受路径（FK-style dim · 走 join caption SQL）")
    void t3_dimCaptionAccepted() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("product$caption"));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryService.queryModel(
                SALES_MODEL,
                request,
                "execute",
                SemanticRequestContext.empty());

        assertNotNull(response);
        // execute 模式下也应当成功 —— validate 不抛错且 SQL 真实执行无错。
        // 不强制断言 items 数量；零数据集的 sqlite 模型也算 pass，目的是
        // 确认 SQL 生成路径未被本次 strict 改造误伤。
    }

    @Test
    @DisplayName("T5 · product$id 接受路径（FK 主键投影）")
    void t5_dimIdAccepted() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("product$id"));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryService.queryModel(
                SALES_MODEL,
                request,
                "execute",
                SemanticRequestContext.empty());

        assertNotNull(response);
    }

    // ------------------------------------------------------------------
    // Deferred follow-ups — placeholders surface the gaps in test reports
    // ------------------------------------------------------------------

    @Test
    @DisplayName("T2 · `product AS p` 应抛 COLUMN_FIELD_NOT_FOUND + hint 保留用户 alias（与 Python parity）")
    void t2_bareDimWithAliasRejectedWithUnifiedErrorCode() {
        // FU-2 closure: InlineExpressionPreprocessStep (order=5) runs first
        // and rejects the plain-alias synthesis when baseField isn't a
        // queryable column. We've enhanced its error to detect when
        // baseField is a DbDimension and emit the dim-aware hint with the
        // user's alias preserved. Exception type stays IllegalArgumentException
        // for consistency with the step's sibling error throws.
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("product AS p"));
        request.setLimit(10);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                semanticQueryService.queryModel(
                        SALES_MODEL,
                        request,
                        "execute",
                        SemanticRequestContext.empty()));

        String msg = ex.getMessage();
        assertNotNull(msg);
        assertTrue(msg.contains("COLUMN_FIELD_NOT_FOUND"),
                "expected COLUMN_FIELD_NOT_FOUND prefix, got: " + msg);
        assertTrue(msg.contains("'product AS p'"),
                "error message should quote original columnDef, got: " + msg);
        assertTrue(msg.contains("did you mean 'product$caption AS p'"),
                "hint should preserve user alias 'p', got: " + msg);
    }

    @Test
    @DisplayName("T4 · product$caption AS userAlias 应输出用户 alias（与 Python parity）")
    void t4_userAliasOverridesTmCaptionOnDimAttr() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("product$caption AS userAlias"));
        request.setLimit(10);

        SemanticQueryResponse response = semanticQueryService.queryModel(
                SALES_MODEL,
                request,
                "execute",
                SemanticRequestContext.empty());

        assertNotNull(response);
        assertNotNull(response.getItems());
        assertFalse(response.getItems().isEmpty(), "user-alias query should return rows");
        Map<String, Object> firstRow = response.getItems().get(0);
        assertTrue(firstRow.containsKey("userAlias"),
                "response row should expose user alias, got keys: " + firstRow.keySet());
        assertFalse(firstRow.containsKey("product$caption"),
                "response row should not expose the source dim attr when user alias is present");
    }
}
