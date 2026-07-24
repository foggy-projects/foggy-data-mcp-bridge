package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InlineExpressionPreprocessStepTest {

    @Test
    void testPredefinedCalculatedFieldDoubleAggregation() {
        InlineExpressionPreprocessStep step = new InlineExpressionPreprocessStep();

        // Setup predefined calculated fields
        CalculatedFieldDef predefined = new CalculatedFieldDef();
        predefined.setName("arOverdueAmount");
        predefined.setExpression("sum(amountResidual)");
        predefined.setAgg("SUM");
        
        QueryModelSupport qm = mock(QueryModelSupport.class);
        when(qm.getPredefinedCalculatedFields()).thenReturn(Arrays.asList(predefined));

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(Arrays.asList("sum(arOverdueAmount)")); // Double aggregation!
        
        com.foggyframework.dataset.client.domain.PagingRequest reqMock = mock(com.foggyframework.dataset.client.domain.PagingRequest.class);
        when(reqMock.getParam()).thenReturn(request);
        ModelResultContext context = mock(ModelResultContext.class);
        when(context.getRequest()).thenReturn(reqMock);
        when(context.getQueryModel()).thenReturn(qm);

        // Should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            step.beforeQuery(context);
        });

        assertTrue(exception.getMessage().contains("ILLEGAL_DOUBLE_AGGREGATION"));
        assertTrue(exception.getMessage().contains("arOverdueAmount"));
    }

    @Test
    void testPredefinedScalarCalculatedFieldOuterAggregationPasses() {
        InlineExpressionPreprocessStep step = new InlineExpressionPreprocessStep();

        CalculatedFieldDef predefined = new CalculatedFieldDef();
        predefined.setName("availablePieceCount");
        predefined.setExpression("IF(number - plannedPieceCount > 0, number - plannedPieceCount, 0)");

        QueryModelSupport qm = mock(QueryModelSupport.class);
        when(qm.getPredefinedCalculatedFields()).thenReturn(List.of(predefined));

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(List.of("sum(availablePieceCount) as remainingPieceCount"));

        com.foggyframework.dataset.client.domain.PagingRequest reqMock = mock(com.foggyframework.dataset.client.domain.PagingRequest.class);
        when(reqMock.getParam()).thenReturn(request);
        ModelResultContext context = mock(ModelResultContext.class);
        when(context.getRequest()).thenReturn(reqMock);
        when(context.getQueryModel()).thenReturn(qm);

        assertDoesNotThrow(() -> step.beforeQuery(context));

        assertEquals(List.of("remainingPieceCount"), request.getColumns());
        assertTrue(request.getCalculatedFields().stream()
                        .anyMatch(field -> "availablePieceCount".equals(field.getName())),
                "outer aggregate should keep the referenced predefined scalar formula injected");
        assertTrue(request.getCalculatedFields().stream()
                        .anyMatch(field -> "remainingPieceCount".equals(field.getName())
                                && "SUM".equals(field.getAgg())),
                "outer aggregate should be converted to a SUM inline calculated field");
    }

    @Test
    void testNormalExpressionPasses() {
        InlineExpressionPreprocessStep step = new InlineExpressionPreprocessStep();

        CalculatedFieldDef predefined = new CalculatedFieldDef();
        predefined.setName("arOverdueAmount");
        predefined.setExpression("sum(amountResidual)");
        predefined.setAgg("SUM");
        
        QueryModelSupport qm = mock(QueryModelSupport.class);
        when(qm.getPredefinedCalculatedFields()).thenReturn(Arrays.asList(predefined));

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(Arrays.asList("arOverdueAmount", "sum(amountResidual)")); // Valid!
        
        com.foggyframework.dataset.client.domain.PagingRequest reqMock = mock(com.foggyframework.dataset.client.domain.PagingRequest.class);
        when(reqMock.getParam()).thenReturn(request);
        ModelResultContext context = mock(ModelResultContext.class);
        when(context.getRequest()).thenReturn(reqMock);
        when(context.getQueryModel()).thenReturn(qm);

        // Should not throw
        step.beforeQuery(context);
    }

    @Test
    void injectsPredefinedCalculatedFieldReferencedOnlyBySliceWithoutColumns() {
        InlineExpressionPreprocessStep step = new InlineExpressionPreprocessStep();

        CalculatedFieldDef predefined = new CalculatedFieldDef();
        predefined.setName("availablePieceCount");
        predefined.setExpression("number - plannedPieceCount");

        QueryModelSupport qm = mock(QueryModelSupport.class);
        when(qm.getPredefinedCalculatedFields()).thenReturn(List.of(predefined));

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setSlice(List.of(new SliceRequestDef("availablePieceCount", ">", 0)));

        com.foggyframework.dataset.client.domain.PagingRequest reqMock = mock(com.foggyframework.dataset.client.domain.PagingRequest.class);
        when(reqMock.getParam()).thenReturn(request);
        ModelResultContext context = mock(ModelResultContext.class);
        when(context.getRequest()).thenReturn(reqMock);
        when(context.getQueryModel()).thenReturn(qm);

        step.beforeQuery(context);

        assertEquals(1, request.getCalculatedFields().size());
        assertEquals("availablePieceCount", request.getCalculatedFields().get(0).getName());
    }

    @Test
    void injectsPredefinedCalculatedFieldReferencedByFieldReferenceValue() {
        InlineExpressionPreprocessStep step = new InlineExpressionPreprocessStep();

        CalculatedFieldDef predefined = new CalculatedFieldDef();
        predefined.setName("availablePieceCount");
        predefined.setExpression("number - plannedPieceCount");

        QueryModelSupport qm = mock(QueryModelSupport.class);
        when(qm.getPredefinedCalculatedFields()).thenReturn(List.of(predefined));

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setSlice(List.of(new SliceRequestDef(
                "number",
                ">",
                Map.of("$field", "availablePieceCount")
        )));

        com.foggyframework.dataset.client.domain.PagingRequest reqMock = mock(com.foggyframework.dataset.client.domain.PagingRequest.class);
        when(reqMock.getParam()).thenReturn(request);
        ModelResultContext context = mock(ModelResultContext.class);
        when(context.getRequest()).thenReturn(reqMock);
        when(context.getQueryModel()).thenReturn(qm);

        step.beforeQuery(context);

        assertEquals(1, request.getCalculatedFields().size());
        assertEquals("availablePieceCount", request.getCalculatedFields().get(0).getName());
    }
}
