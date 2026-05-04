package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;

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
}
