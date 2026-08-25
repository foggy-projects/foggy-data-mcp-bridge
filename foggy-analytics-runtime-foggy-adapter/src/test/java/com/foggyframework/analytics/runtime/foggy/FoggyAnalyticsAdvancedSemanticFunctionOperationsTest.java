package com.foggyframework.analytics.runtime.foggy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.function.contract.AnalyticsComposeFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelFunctionRequest;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.semantic.port.ComposeCaller;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionPort;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionRequest;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionResult;
import com.foggyframework.dataset.model.semantic.port.ComposeOperation;
import com.foggyframework.dataset.model.semantic.port.SemanticQueryExecutionPort;
import com.foggyframework.dataset.model.spi.QueryModel;
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

class FoggyAnalyticsAdvancedSemanticFunctionOperationsTest {

    private static final String REVISION = "sha256:" + "a".repeat(64);
    private static final AnalyticsFunctionAuthority AUTHORITY =
            new AnalyticsFunctionAuthority("tms", "subject:42");
    private static final AnalyticsFunctionRequestContext REQUEST_CONTEXT =
            new AnalyticsFunctionRequestContext("request-1", "trace-1");

    @Test
    void mapsFullDslAndCapsOnlyTheOuterRowLimit() {
        FoggyQueryAuthorityResolver authorityResolver =
                mock(FoggyQueryAuthorityResolver.class);
        SemanticQueryExecutionPort queryPort = mock(SemanticQueryExecutionPort.class);
        FoggyAnalyticsAuthority authority = mock(FoggyAnalyticsAuthority.class);
        @SuppressWarnings("unchecked")
        CatalogResolution<QueryModel> catalog = mock(CatalogResolution.class);
        var semanticContext = com.foggyframework.dataset.model.semantic.domain
                .SemanticRequestContext.empty();
        when(authorityResolver.resolve(any())).thenReturn(authority);
        when(authority.catalogResolution()).thenReturn(catalog);
        when(authority.semanticRequestContext()).thenReturn(semanticContext);
        when(catalog.canonicalName()).thenReturn("FactOrderQueryModel");
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of(Map.of("orderCount", 12)));
        when(queryPort.queryModel(
                eq("FactOrderQueryModel"), any(), eq("validate"), any()))
                .thenReturn(response);
        FoggyAnalyticsAdvancedSemanticFunctionOperations operations = operations(
                authorityResolver,
                request -> new ComposeCaller("user-1", null, List.of(), null, null, null),
                queryPort,
                mock(ComposeExecutionPort.class));
        AnalyticsQueryModelFunctionRequest request =
                new AnalyticsQueryModelFunctionRequest(
                        "default",
                        "FactOrderQueryModel",
                        REVISION,
                        "validate",
                        Map.of(
                                "columns", List.of("orderCount"),
                                "groupBy", List.of(
                                        "customer$ageGroup",
                                        Map.of("field", "customer$caption", "agg", "PK")),
                                "slice", List.of(
                                        Map.of("status", "PAID"),
                                        Map.of(
                                                "field", "org$id",
                                                "op", "descendantsOf",
                                                "value", "root",
                                                "maxDepth", 2),
                                        Map.of("$expr", "payAmount > costAmount")),
                                "orderBy", List.of(
                                        "-orderCount",
                                        Map.of(
                                                "field", "customer$caption",
                                                "dir", "asc",
                                                "nullLast", true)),
                                "calculatedFields", List.of(Map.of(
                                        "name", "ranked",
                                        "expression", "RANK()")),
                                "timeWindow", Map.of("type", "YTD"),
                                "limit", 500),
                        AUTHORITY,
                        REQUEST_CONTEXT);

        var result = operations.runQueryModel(
                request, AnalyticsFunctionContext.normalize(REQUEST_CONTEXT));

        ArgumentCaptor<SemanticQueryRequest> query =
                ArgumentCaptor.forClass(SemanticQueryRequest.class);
        var context = ArgumentCaptor.forClass(
                com.foggyframework.dataset.model.semantic.domain
                        .SemanticRequestContext.class);
        verify(queryPort).queryModel(
                eq("FactOrderQueryModel"), query.capture(), eq("validate"),
                context.capture());
        assertThat(query.getValue().getCalculatedFields()).hasSize(1);
        assertThat(query.getValue().getGroupBy())
                .extracting(SemanticQueryRequest.GroupByItem::getField)
                .containsExactly("customer$ageGroup", "customer$caption");
        assertThat(query.getValue().getGroupBy().get(1).getAgg()).isEqualTo("PK");
        assertThat(query.getValue().getSlice().get(0).getField()).isEqualTo("status");
        assertThat(query.getValue().getSlice().get(0).getOp()).isEqualTo("=");
        assertThat(query.getValue().getSlice().get(1).getMaxDepth()).isEqualTo(2);
        assertThat(query.getValue().getSlice().get(2).getExpr())
                .isEqualTo("payAmount > costAmount");
        assertThat(query.getValue().getOrderBy().get(0).getDir()).isEqualTo("desc");
        assertThat(query.getValue().getOrderBy().get(1).getNullLast()).isTrue();
        assertThat(query.getValue().getTimeWindow()).containsEntry("type", "YTD");
        assertThat(query.getValue().getLimit()).isEqualTo(100);
        assertThat(context.getValue().getPermissionAction())
                .isEqualTo(PermissionAction.VALIDATE);
        assertThat(result.response()).containsKey("items");
    }

    @Test
    void mapsRestrictedComposeToTheEnginePortWithTrustedCaller() {
        FoggyQueryAuthorityResolver authorityResolver =
                mock(FoggyQueryAuthorityResolver.class);
        ComposeExecutionPort composePort = mock(ComposeExecutionPort.class);
        ComposeCaller caller = new ComposeCaller(
                "user-42", "tenant-1", List.of("ANALYST"), null, null, null);
        when(composePort.execute(any())).thenReturn(new ComposeExecutionResult(
                ComposeOperation.PREVIEW,
                true,
                false,
                Map.of("plans", List.of()),
                "select 1",
                List.of(),
                List.of()));
        FoggyAnalyticsAdvancedSemanticFunctionOperations operations = operations(
                authorityResolver,
                request -> caller,
                mock(SemanticQueryExecutionPort.class),
                composePort);
        AnalyticsComposeFunctionRequest request = new AnalyticsComposeFunctionRequest(
                "default",
                "preview",
                "return { plans: dsl({ model: 'FactOrderQueryModel' }) };",
                Map.of("status", "SHIPPED"),
                AUTHORITY,
                REQUEST_CONTEXT);

        var result = operations.runCompose(
                request, AnalyticsFunctionContext.normalize(REQUEST_CONTEXT));

        ArgumentCaptor<ComposeExecutionRequest> execution =
                ArgumentCaptor.forClass(ComposeExecutionRequest.class);
        verify(composePort).execute(execution.capture());
        assertThat(execution.getValue().operation()).isEqualTo(ComposeOperation.PREVIEW);
        assertThat(execution.getValue().namespace()).isEmpty();
        assertThat(execution.getValue().caller()).isEqualTo(caller);
        assertThat(execution.getValue().dialect()).isEqualTo("sqlite");
        assertThat(result.valid()).isTrue();
        assertThat(result.executed()).isFalse();
        assertThat(result.sql()).isEqualTo("select 1");
    }

    private static FoggyAnalyticsAdvancedSemanticFunctionOperations operations(
            FoggyQueryAuthorityResolver authorityResolver,
            FoggyComposeCallerResolver composeCallerResolver,
            SemanticQueryExecutionPort queryPort,
            ComposeExecutionPort composePort) {
        return new FoggyAnalyticsAdvancedSemanticFunctionOperations(
                authorityResolver,
                composeCallerResolver,
                queryPort,
                composePort,
                new ObjectMapper(),
                100,
                "sqlite");
    }
}
