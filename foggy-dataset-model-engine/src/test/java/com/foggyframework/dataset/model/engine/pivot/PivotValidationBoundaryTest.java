package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.dataset.model.semantic.domain.*;
import com.foggyframework.dataset.model.semantic.domain.pivot.*;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.semantic.port.PivotRollupExecutionPort;
import com.foggyframework.dataset.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PivotValidationBoundaryTest {
    @Test void validationNeverEntersExecutionOrResultCacheEvenWhenCacheIsEnabled() {
        var port = mock(PivotRollupExecutionPort.class);
        var cache = mock(PivotOuterCacheProvider.class);
        var identities = mock(PivotOuterCacheModelIdentityProvider.class);
        when(port.queryModel(anyString(), any(), eq("validate"), any())).thenReturn(new SemanticQueryResponse());
        var pipeline = new PivotPipeline(port, new CardinalityBreaker(), null, null,
                new PivotPipeline.OuterCacheOptions(true, 60000, 10), identities, cache);
        var request = request();
        assertNotNull(pipeline.validate("Sales", request, SemanticRequestContext.ofNamespace("tenant")));
        var flat = ArgumentCaptor.forClass(SemanticQueryRequest.class);
        var context = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(port).queryModel(eq("Sales"), flat.capture(), eq("validate"), context.capture());
        assertNull(flat.getValue().getPivot());
        assertEquals(List.of("category", "amount"), flat.getValue().getColumns());
        assertEquals(PermissionAction.VALIDATE, context.getValue().getPermissionAction());
        assertEquals("tenant", context.getValue().getNamespace());
        verifyNoMoreInteractions(port);
        verifyNoInteractions(cache, identities);
    }

    @Test void bothPublicValidationEntrypointsRouteToPivotBeforeOrdinaryColumnsGuard() {
        var pipeline = mock(PivotPipeline.class);
        var service = new SemanticQueryServiceV3Impl();
        ReflectionTestUtils.setField(service, "pivotPipeline", pipeline);
        var request = request();
        var context = SemanticRequestContext.empty();
        var expected = new SemanticQueryResponse();
        when(pipeline.validate("Sales", request, context)).thenReturn(expected);
        assertSame(expected, service.validateQuery("Sales", request, context));
        assertSame(expected, service.queryModel("Sales", request, "validate", context));
        verify(pipeline, times(2)).validate("Sales", request, context);
        verify(pipeline, never()).execute(any(), any(), any());
    }

    private SemanticQueryRequest request() {
        var axis = new AxisField(); axis.setField("category");
        var pivot = new PivotRequest(); pivot.setRows(List.of(axis)); pivot.setMetrics(List.of("amount"));
        var request = new SemanticQueryRequest(); request.setPivot(pivot); return request;
    }
}
