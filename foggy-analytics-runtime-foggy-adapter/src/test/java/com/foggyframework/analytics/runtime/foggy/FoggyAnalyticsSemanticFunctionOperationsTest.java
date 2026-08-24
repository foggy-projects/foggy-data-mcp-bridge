package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQuery;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryFunctionRequest;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityRequest;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.semantic.port.SemanticQueryExecutionPort;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoggyAnalyticsSemanticFunctionOperationsTest {

    private static final String REVISION = "sha256:" + "a".repeat(64);

    @Test
    void trimsEngineMetadataAtTheFunctionBoundary() {
        FoggyQueryAuthorityResolver authorityResolver =
                mock(FoggyQueryAuthorityResolver.class);
        SemanticServiceV3 metadata = mock(SemanticServiceV3.class);
        FoggyAnalyticsAuthority authority = mock(FoggyAnalyticsAuthority.class);
        var semanticContext = com.foggyframework.dataset.model.semantic.domain
                .SemanticRequestContext.empty();
        when(authorityResolver.resolve(any())).thenReturn(authority);
        when(authority.modelDependency()).thenReturn(new AnalyticsModelDependency(
                new AnalyticsNamespaceRef("default"),
                "qm",
                "FactOrderQueryModel",
                new AnalyticsModelRevision(REVISION)));
        when(authority.semanticRequestContext()).thenReturn(semanticContext);

        SemanticMetadataResponse response = new SemanticMetadataResponse();
        response.setFormat("markdown");
        response.setContent("# FactOrderQueryModel\n\n- amount\n");
        when(metadata.getMetadata(any(), eq("markdown"), any()))
                .thenReturn(response);

        FoggyAnalyticsSemanticFunctionOperations operations =
                new FoggyAnalyticsSemanticFunctionOperations(
                        authorityResolver,
                        metadata,
                        mock(SemanticQueryExecutionPort.class),
                        100);
        AnalyticsSemanticModelFunctionRequest request =
                new AnalyticsSemanticModelFunctionRequest(
                        "default",
                        "FactOrderQueryModel",
                        REVISION,
                        new AnalyticsFunctionAuthority("tms", "subject:42"),
                        new AnalyticsFunctionRequestContext("request-1", "trace-1"));

        var result = operations.describeModel(
                request,
                AnalyticsFunctionContext.normalize(request.context()));

        assertThat(result.content())
                .isEqualTo("# FactOrderQueryModel\n\n- amount");
        ArgumentCaptor<SemanticMetadataRequest> metadataRequest =
                ArgumentCaptor.forClass(SemanticMetadataRequest.class);
        var context = ArgumentCaptor.forClass(
                com.foggyframework.dataset.model.semantic.domain
                        .SemanticRequestContext.class);
        verify(metadata).getMetadata(
                metadataRequest.capture(), eq("markdown"), context.capture());
        assertThat(metadataRequest.getValue().getQmModels())
                .containsExactly("FactOrderQueryModel");
        assertThat(context.getValue().getPermissionAction())
                .isEqualTo(PermissionAction.DESCRIBE);
    }

    @Test
    void resolvesOpaqueAuthorityAndMapsOnlyTheBoundedSemanticSubset() {
        FoggyQueryAuthorityResolver authorityResolver =
                mock(FoggyQueryAuthorityResolver.class);
        SemanticQueryExecutionPort execution = mock(SemanticQueryExecutionPort.class);
        FoggyAnalyticsAuthority authority = mock(FoggyAnalyticsAuthority.class);
        @SuppressWarnings("unchecked")
        CatalogResolution<com.foggyframework.dataset.model.spi.QueryModel> catalog =
                mock(CatalogResolution.class);
        var semanticContext = com.foggyframework.dataset.model.semantic.domain
                .SemanticRequestContext.empty();
        when(authorityResolver.resolve(any())).thenReturn(authority);
        when(authority.catalogResolution()).thenReturn(catalog);
        when(authority.semanticRequestContext()).thenReturn(semanticContext);
        when(catalog.canonicalName()).thenReturn("FactOrderQueryModel");

        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of(
                Map.of("orderCount", 12, "rawSql", "select private"),
                Map.of("orderCount", 7),
                Map.of("orderCount", 3)));
        response.setTotal(3L);
        response.setHasNext(true);
        response.setWarnings(List.of(
                "private SQL text must not cross the Function boundary",
                "RESULT_TRUNCATED"));
        when(execution.queryModel(
                eq("FactOrderQueryModel"), any(), eq("execute"), any()))
                .thenReturn(response);

        FoggyAnalyticsSemanticFunctionOperations operations =
                new FoggyAnalyticsSemanticFunctionOperations(
                        authorityResolver,
                        mock(SemanticServiceV3.class),
                        execution,
                        2);
        AnalyticsSemanticQueryFunctionRequest request =
                new AnalyticsSemanticQueryFunctionRequest(
                        "default",
                        "FactOrderQueryModel",
                        REVISION,
                        new AnalyticsSemanticQuery(
                                List.of("orderCount"),
                                List.of(new AnalyticsSemanticQuery.Filter(
                                        "status", "=", "SHIPPED")),
                                List.of(),
                                List.of(new AnalyticsSemanticQuery.Order(
                                        "orderCount", "desc")),
                                0,
                                100,
                                true,
                                false),
                        new AnalyticsFunctionAuthority("tms", "subject:42"),
                        new AnalyticsFunctionRequestContext("request-1", "trace-1"));

        var result = operations.executeQuery(
                request,
                AnalyticsFunctionContext.normalize(request.context()));

        ArgumentCaptor<QueryAuthorityRequest> authorityRequest =
                ArgumentCaptor.forClass(QueryAuthorityRequest.class);
        verify(authorityResolver).resolve(authorityRequest.capture());
        assertThat(authorityRequest.getValue().binding().provider()).isEqualTo("tms");
        assertThat(authorityRequest.getValue().binding().reference())
                .isEqualTo("subject:42");
        assertThat(authorityRequest.getValue().modelDependency().modelRevision().value())
                .isEqualTo(REVISION);

        ArgumentCaptor<SemanticQueryRequest> semanticRequest =
                ArgumentCaptor.forClass(SemanticQueryRequest.class);
        var context = ArgumentCaptor.forClass(
                com.foggyframework.dataset.model.semantic.domain
                        .SemanticRequestContext.class);
        verify(execution).queryModel(
                eq("FactOrderQueryModel"),
                semanticRequest.capture(),
                eq("execute"),
                context.capture());
        assertThat(semanticRequest.getValue().getColumns())
                .containsExactly("orderCount");
        assertThat(semanticRequest.getValue().getSlice()).hasSize(1);
        assertThat(semanticRequest.getValue().getLimit()).isEqualTo(2);
        assertThat(semanticRequest.getValue().getSemanticSql()).isNull();
        assertThat(semanticRequest.getValue().getCalculatedFields()).isNull();
        assertThat(semanticRequest.getValue().getHints()).isNull();
        assertThat(semanticRequest.getValue().getExtData()).isNull();
        assertThat(context.getValue().getPermissionAction())
                .isEqualTo(PermissionAction.EXECUTE);
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0)).containsOnlyKeys("orderCount");
        assertThat(result.truncated()).isTrue();
        assertThat(result.warnings()).containsExactly(
                "SEMANTIC_QUERY_WARNING", "RESULT_TRUNCATED");
    }
}
