package com.foggyframework.dataset.db.model.odoo;

import com.foggyframework.core.ex.ExRuntimeExceptionImpl;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Odoo 无效字段可恢复错误测试")
class OdooInvalidFieldRecoveryTest extends EcommerceTestSupport {

    @Resource
    private SemanticQueryServiceV3 semanticQueryService;

    @Test
    @DisplayName("move$moveType 应在 SQL 前返回 did-you-mean 错误")
    void testInvalidColumnSuggestsMoveType() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("move$moveType", "name"));
        request.setLimit(10);

        assertInvalidFieldError(request, "move$moveType", "moveType");
    }

    @Test
    @DisplayName("move$state 应在 SQL 前返回 state 推荐，而不是继续落到底层 SQL")
    void testInvalidColumnSuggestsState() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("move$state", "name"));
        request.setLimit(10);

        assertInvalidFieldError(request, "move$state", "state");
    }

    @Test
    @DisplayName("parent$state 误套到 account.move 根模型时应推荐 state")
    void testInvalidColumnSuggestsStateForParentStyleField() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("parent$state", "name"));
        request.setLimit(10);

        assertInvalidFieldError(request, "parent$state", "state");
    }

    @Test
    @DisplayName("groupBy 误用 move$moveType 应保持显式失败，不自动修复执行")
    void testInvalidGroupBySuggestsMoveType() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("name", "move$moveType"));
        request.setGroupBy(List.of(new SemanticQueryRequest.GroupByItem("move$moveType", null)));
        request.setLimit(10);

        assertInvalidFieldError(request, "move$moveType", "moveType");
    }

    @Test
    @DisplayName("slice 误用 move$moveType 应保持显式失败，不自动修复执行")
    void testInvalidSliceSuggestsMoveType() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("name"));

        SemanticQueryRequest.SliceItem sliceItem = new SemanticQueryRequest.SliceItem();
        sliceItem.setField("move$moveType");
        sliceItem.setOp("=");
        sliceItem.setValue("out_invoice");
        request.setSlice(List.of(sliceItem));
        request.setLimit(10);

        assertInvalidFieldError(request, "move$moveType", "moveType");
    }

    @Test
    @DisplayName("嵌套 slice 误用 move$moveType 应保持显式失败，不自动修复执行")
    void testInvalidNestedSliceSuggestsMoveType() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("name"));

        SemanticQueryRequest.SliceItem nestedInvalid = new SemanticQueryRequest.SliceItem();
        nestedInvalid.setField("move$moveType");
        nestedInvalid.setOp("=");
        nestedInvalid.setValue("out_invoice");

        SemanticQueryRequest.SliceItem nestedValid = new SemanticQueryRequest.SliceItem();
        nestedValid.setField("moveType");
        nestedValid.setOp("=");
        nestedValid.setValue("out_invoice");

        SemanticQueryRequest.SliceItem root = new SemanticQueryRequest.SliceItem();
        root.setOr(List.of(nestedInvalid, nestedValid));

        request.setSlice(List.of(root));
        request.setLimit(10);

        assertInvalidFieldError(request, "move$moveType", "moveType");
    }

    @Test
    @DisplayName("orderBy 误用 move$moveType 应保持显式失败，不自动修复执行")
    void testInvalidOrderBySuggestsMoveType() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("name"));

        SemanticQueryRequest.OrderItem orderItem = new SemanticQueryRequest.OrderItem();
        orderItem.setField("move$moveType");
        orderItem.setDir("ASC");
        request.setOrderBy(List.of(orderItem));
        request.setLimit(10);

        assertInvalidFieldError(request, "move$moveType", "moveType");
    }

    @Test
    @DisplayName("calculated field 依赖误用 move$moveType 应保持显式失败，不自动修复执行")
    void testInvalidCalculatedFieldDependencySuggestsMoveType() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("name"));

        CalculatedFieldDef calculatedField = new CalculatedFieldDef();
        calculatedField.setName("badMoveType");
        calculatedField.setExpression("COALESCE(move$moveType, moveType)");
        request.setCalculatedFields(List.of(calculatedField));
        request.setLimit(10);

        assertInvalidFieldError(request, "move$moveType", "moveType");
    }

    @SuppressWarnings("unchecked")
    private void assertInvalidFieldError(SemanticQueryRequest request, String invalidField, String expectedSuggestion) {
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            semanticQueryService.queryModel(
                "OdooAccountMoveQueryModel",
                request,
                "execute",
                SemanticRequestContext.empty()
            )
        );

        String errorMsg = exception.getMessage();
        assertNotNull(errorMsg);
        assertTrue(errorMsg.contains("Field '" + invalidField + "' not found in model 'OdooAccountMoveQueryModel'"));
        assertTrue(errorMsg.contains("Did you mean '" + expectedSuggestion + "'?"));
        assertFalse(errorMsg.toLowerCase().contains("column t.move$movetype does not exist"));
        assertFalse(errorMsg.toLowerCase().contains("column t.move$state does not exist"));
        assertFalse(errorMsg.toLowerCase().contains("column t.parent$state does not exist"));

        assertInstanceOf(ExRuntimeExceptionImpl.class, exception);
        Object item = ((ExRuntimeExceptionImpl) exception).getItem();
        assertInstanceOf(Map.class, item);

        Map<String, Object> errorDetail = (Map<String, Object>) item;
        assertEquals("INVALID_QUERY_FIELD", errorDetail.get("errorCode"));
        assertEquals("OdooAccountMoveQueryModel", errorDetail.get("model"));
        assertEquals(invalidField, errorDetail.get("invalidField"));
        assertInstanceOf(List.class, errorDetail.get("suggestions"));
        List<?> suggestions = (List<?>) errorDetail.get("suggestions");
        assertFalse(suggestions.isEmpty());
        assertEquals(expectedSuggestion, suggestions.get(0));
        assertTrue(suggestions.contains(expectedSuggestion));
    }
}
