package com.foggyframework.analytics.runtime.foggy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.function.contract.AnalyticsComposeFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelFunctionRequest;
import com.foggyframework.analytics.runtime.core.function.AnalyticsSemanticFunctionException;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionException;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.semantic.port.ComposeCaller;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionPort;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionRequest;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionResult;
import com.foggyframework.dataset.model.semantic.port.ComposeOperation;
import com.foggyframework.dataset.model.semantic.port.SemanticQueryExecutionPort;
import com.foggyframework.dataset.model.semantic.support.UnknownQueryPropertyPolicy;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FoggyAnalyticsAdvancedSemanticFunctionOperationsTest {

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
        when(authorityResolver.resolveCurrent(any())).thenReturn(authority);
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
    void propagatesStructuredUnknownPropertyWarnings() {
        FoggyQueryAuthorityResolver authorityResolver = mock(FoggyQueryAuthorityResolver.class);
        SemanticQueryExecutionPort queryPort = mock(SemanticQueryExecutionPort.class);
        FoggyAnalyticsAuthority authority = mock(FoggyAnalyticsAuthority.class);
        @SuppressWarnings("unchecked")
        CatalogResolution<QueryModel> catalog = mock(CatalogResolution.class);
        when(authorityResolver.resolveCurrent(any())).thenReturn(authority);
        when(authority.catalogResolution()).thenReturn(catalog);
        when(authority.semanticRequestContext()).thenReturn(
                com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext.empty());
        when(catalog.canonicalName()).thenReturn("FactOrderQueryModel");
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of());
        when(queryPort.queryModel(eq("FactOrderQueryModel"), any(), eq("execute"), any()))
                .thenReturn(response);
        FoggyAnalyticsAdvancedSemanticFunctionOperations operations = operations(
                authorityResolver,
                request -> new ComposeCaller("user-1", null, List.of(), null, null, null),
                queryPort,
                mock(ComposeExecutionPort.class));

        var result = operations.runQueryModel(
                new AnalyticsQueryModelFunctionRequest(
                        "default",
                        "FactOrderQueryModel",
                        "execute",
                        Map.of("groupBy", List.of(Map.of(
                                "field", "orderDate$month",
                                "grain", "month"))),
                        AUTHORITY,
                        REQUEST_CONTEXT),
                AnalyticsFunctionContext.normalize(REQUEST_CONTEXT));

        assertThat(result.response().get("queryInputWarnings")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> warnings =
                (List<Map<String, Object>>) result.response().get("queryInputWarnings");
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .containsEntry("code", "UNKNOWN_QUERY_PROPERTY_IGNORED")
                .containsEntry("path", "$.groupBy[0].grain");
    }

    @Test
    void strictModeRejectsUnknownPropertyBeforeExecution() {
        FoggyQueryAuthorityResolver authorityResolver = mock(FoggyQueryAuthorityResolver.class);
        SemanticQueryExecutionPort queryPort = mock(SemanticQueryExecutionPort.class);
        FoggyAnalyticsAuthority authority = mock(FoggyAnalyticsAuthority.class);
        @SuppressWarnings("unchecked")
        CatalogResolution<QueryModel> catalog = mock(CatalogResolution.class);
        when(authorityResolver.resolveCurrent(any())).thenReturn(authority);
        when(authority.catalogResolution()).thenReturn(catalog);
        when(authority.semanticRequestContext()).thenReturn(
                com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext.empty());
        when(catalog.canonicalName()).thenReturn("FactOrderQueryModel");
        FoggyAnalyticsAdvancedSemanticFunctionOperations operations =
                new FoggyAnalyticsAdvancedSemanticFunctionOperations(
                        authorityResolver,
                        request -> new ComposeCaller("user-1", null, List.of(), null, null, null),
                        queryPort,
                        mock(ComposeExecutionPort.class),
                        new ObjectMapper(),
                        100,
                        "sqlite",
                        FoggyAnalyticsNamespaceMapper.defaultConvention(),
                        UnknownQueryPropertyPolicy.STRICT);

        AnalyticsSemanticFunctionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                AnalyticsSemanticFunctionException.class,
                () -> operations.runQueryModel(
                        new AnalyticsQueryModelFunctionRequest(
                                "default",
                                "FactOrderQueryModel",
                                "execute",
                                Map.of("groupBy", List.of(Map.of(
                                        "field", "orderDate", "grain", "month"))),
                                AUTHORITY,
                                REQUEST_CONTEXT),
                        AnalyticsFunctionContext.normalize(REQUEST_CONTEXT)));

        assertThat(failure.code())
                .isEqualTo(AnalyticsSemanticFunctionException.Code.QUERY_INVALID);
        assertThat(failure.validationCode()).isEqualTo("UNKNOWN_QUERY_PROPERTY");
        verifyNoInteractions(queryPort);
    }

    @Test
    void mapsStableDslCteValidationFailureToRepairableQueryInvalid() {
        AnalyticsSemanticFunctionException failure = queryFailure(
                "validate",
                RX.throwB("DSL_CTE_STAGE_REFERENCE_INVALID: stage input 'source' "
                        + "must reference a prior stage."));

        assertThat(failure.code())
                .isEqualTo(AnalyticsSemanticFunctionException.Code.QUERY_INVALID);
        assertThat(failure.validationCode())
                .isEqualTo("DSL_CTE_STAGE_REFERENCE_INVALID");
    }

    @Test
    void mapsCommonValidateFailuresToValueFreeRepairKeys() {
        AnalyticsSemanticFunctionException groupBy = queryFailure(
                "validate",
                RX.throwB("groupBy 字段 customer$ageGroup 必须出现在 columns 中"));
        AnalyticsSemanticFunctionException orderBy = queryFailure(
                "validate",
                RX.throwA("GroupBy 模式下 orderBy 字段 payAmount 必须在 columns 存在"));
        AnalyticsSemanticFunctionException field = queryFailure(
                "validate",
                RX.throwB("字段不存在: inventedField"));
        AnalyticsSemanticFunctionException filter = queryFailure(
                "validate",
                RX.throwB("slice 中的 name 字段不能为空"));

        assertThat(List.of(groupBy, orderBy, field, filter))
                .allSatisfy(failure -> assertThat(failure.code())
                        .isEqualTo(AnalyticsSemanticFunctionException.Code.QUERY_INVALID));
        assertThat(groupBy.validationCode())
                .isEqualTo("QUERY_MODEL_GROUP_BY_INVALID");
        assertThat(orderBy.validationCode())
                .isEqualTo("QUERY_MODEL_ORDER_BY_INVALID");
        assertThat(field.validationCode())
                .isEqualTo("QUERY_MODEL_FIELD_INVALID");
        assertThat(filter.validationCode())
                .isEqualTo("QUERY_MODEL_FILTER_INVALID");
    }

    @Test
    void keepsAuthorityAndUnexpectedFailuresOutOfTheRepairChannel() {
        AnalyticsSemanticFunctionException denied = queryFailure(
                "validate", ModelPermissionException.denied());
        AnalyticsSemanticFunctionException unavailable = queryFailure(
                "validate", new IllegalStateException("database unavailable"));

        assertThat(denied.code())
                .isEqualTo(AnalyticsSemanticFunctionException.Code.QUERY_FAILED);
        assertThat(denied.validationCode()).isNull();
        assertThat(unavailable.code())
                .isEqualTo(AnalyticsSemanticFunctionException.Code.QUERY_FAILED);
        assertThat(unavailable.validationCode()).isNull();
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

    private static AnalyticsSemanticFunctionException queryFailure(
            String mode,
            RuntimeException portFailure) {
        FoggyQueryAuthorityResolver authorityResolver =
                mock(FoggyQueryAuthorityResolver.class);
        SemanticQueryExecutionPort queryPort = mock(SemanticQueryExecutionPort.class);
        FoggyAnalyticsAuthority authority = mock(FoggyAnalyticsAuthority.class);
        @SuppressWarnings("unchecked")
        CatalogResolution<QueryModel> catalog = mock(CatalogResolution.class);
        when(authorityResolver.resolveCurrent(any())).thenReturn(authority);
        when(authority.catalogResolution()).thenReturn(catalog);
        when(authority.semanticRequestContext()).thenReturn(
                com.foggyframework.dataset.model.semantic.domain
                        .SemanticRequestContext.empty());
        when(catalog.canonicalName()).thenReturn("FactOrderQueryModel");
        when(queryPort.queryModel(
                eq("FactOrderQueryModel"), any(), eq(mode), any()))
                .thenThrow(portFailure);
        FoggyAnalyticsAdvancedSemanticFunctionOperations operations = operations(
                authorityResolver,
                request -> new ComposeCaller("user-1", null, List.of(), null, null, null),
                queryPort,
                mock(ComposeExecutionPort.class));
        AnalyticsQueryModelFunctionRequest request =
                new AnalyticsQueryModelFunctionRequest(
                        "default",
                        "FactOrderQueryModel",
                        mode,
                        Map.of("columns", List.of("amount")),
                        AUTHORITY,
                        REQUEST_CONTEXT);
        try {
            operations.runQueryModel(
                    request, AnalyticsFunctionContext.normalize(REQUEST_CONTEXT));
        } catch (AnalyticsSemanticFunctionException failure) {
            return failure;
        }
        throw new AssertionError("query-model failure was not propagated");
    }
}
