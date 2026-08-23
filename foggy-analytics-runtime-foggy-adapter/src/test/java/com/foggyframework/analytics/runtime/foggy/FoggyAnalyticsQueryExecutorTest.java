package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;
import com.foggyframework.analytics.definition.api.AnalyticsQueryRef;
import com.foggyframework.analytics.definition.api.AnalyticsQuerySpec;
import com.foggyframework.analytics.runtime.core.query.QueryExecutionContext;
import com.foggyframework.analytics.runtime.core.query.QueryExecutionResult;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.spi.DbColumnType;
import com.foggyframework.dataset.model.spi.QueryModel;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.foggyframework.analytics.runtime.foggy.FoggyAdapterTestFixtures.MODEL;
import static com.foggyframework.analytics.runtime.foggy.FoggyAdapterTestFixtures.ENGINE_NAMESPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoggyAnalyticsQueryExecutorTest {

    @Test
    void mapsGovernedQueryAndReturnsOnlyDeclaredBoundedColumns() {
        AtomicReference<SemanticQueryRequest> engineRequest = new AtomicReference<>();
        AtomicReference<SemanticRequestContext> engineContext = new AtomicReference<>();
        FoggyAnalyticsAuthority authority = authority(
                FoggyAdapterTestFixtures.queryDependency());
        FoggyAnalyticsQueryExecutor executor = new FoggyAnalyticsQueryExecutor(
                (model, request, mode, context) -> {
                    assertEquals(MODEL, model);
                    assertEquals("json", mode);
                    engineRequest.set(request);
                    engineContext.set(context);
                    return response();
                });

        QueryExecutionResult result = executor.execute(context(
                authority.modelDependency(),
                authority,
                Map.of(),
                2));

        assertEquals(List.of("region", "amount"), engineRequest.get().getColumns());
        assertEquals(1, engineRequest.get().getGroupBy().size());
        assertEquals("region", engineRequest.get().getGroupBy().get(0).getField());
        assertNull(engineRequest.get().getGroupBy().get(0).getAgg());
        assertEquals(0, engineRequest.get().getStart());
        assertEquals(2, engineRequest.get().getLimit());
        assertNull(engineRequest.get().getSlice());
        assertNull(engineRequest.get().getExtData());
        assertSame(authority.semanticRequestContext(), engineContext.get());

        assertEquals(2, result.rows().size());
        assertEquals(List.of("region", "amount"), result.columns().stream()
                .map(column -> column.name())
                .toList());
        assertEquals(List.of("string", "decimal"), result.columns().stream()
                .map(column -> column.type())
                .toList());
        assertTrue(result.columns().stream().allMatch(column -> column.nullable()));
        assertFalse(result.rows().get(0).containsKey("secretColumn"));
        assertTrue(result.truncated());
        assertEquals(List.of("FOGGY_QUERY_WARNING"), result.diagnostics());
        assertFalse(result.diagnostics().toString().contains("credential"));
    }

    @Test
    void rejectsUndeclaredRuntimeParametersWithoutCallingEngine() {
        AtomicInteger calls = new AtomicInteger();
        FoggyAnalyticsAuthority authority = authority(
                FoggyAdapterTestFixtures.queryDependency());
        FoggyAnalyticsQueryExecutor executor = new FoggyAnalyticsQueryExecutor(
                (model, request, mode, semanticContext) -> {
                    calls.incrementAndGet();
                    return response();
                });

        FoggyAnalyticsAdapterException failure = assertThrows(
                FoggyAnalyticsAdapterException.class,
                () -> executor.execute(context(
                        authority.modelDependency(),
                        authority,
                        Map.of("region", "east"),
                        100)));

        assertEquals(
                FoggyAnalyticsAdapterException.Code.QUERY_PARAMETERS_UNSUPPORTED,
                failure.code());
        assertEquals(0, calls.get());
    }

    @Test
    void rejectsAuthorityResolvedForAnotherStableRevision() {
        AnalyticsModelDependency authorityDependency =
                FoggyAdapterTestFixtures.queryDependency();
        FoggyAnalyticsAuthority authority = authority(authorityDependency);
        AnalyticsModelDependency changedDependency = FoggyAdapterTestFixtures.dependency(
                "qm",
                MODEL,
                AnalyticsModelRevision.fromSha256Hex("f".repeat(64)));
        FoggyAnalyticsQueryExecutor executor = new FoggyAnalyticsQueryExecutor(
                (model, request, mode, semanticContext) -> response());

        FoggyAnalyticsAdapterException failure = assertThrows(
                FoggyAnalyticsAdapterException.class,
                () -> executor.execute(context(
                        changedDependency,
                        authority,
                        Map.of(),
                        100)));

        assertEquals(FoggyAnalyticsAdapterException.Code.AUTHORITY_MISMATCH, failure.code());
    }

    @Test
    void rejectsEngineClarifyOrRejectTerminalInsteadOfRenderingEmptyData() {
        FoggyAnalyticsAuthority authority = authority(
                FoggyAdapterTestFixtures.queryDependency());
        SemanticQueryResponse rejected = new SemanticQueryResponse();
        rejected.setItems(List.of());
        SemanticQueryResponse.ExecutionInfo execution =
                new SemanticQueryResponse.ExecutionInfo();
        execution.setStatus("REJECT");
        execution.setWhy(List.of("internal governance detail"));
        rejected.setExecution(execution);
        FoggyAnalyticsQueryExecutor executor = new FoggyAnalyticsQueryExecutor(
                (model, request, mode, semanticContext) -> rejected);

        FoggyAnalyticsAdapterException failure = assertThrows(
                FoggyAnalyticsAdapterException.class,
                () -> executor.execute(context(
                        authority.modelDependency(),
                        authority,
                        Map.of(),
                        100)));

        assertEquals(FoggyAnalyticsAdapterException.Code.QUERY_NOT_EXECUTED, failure.code());
        assertFalse(failure.getMessage().contains("governance detail"));
    }

    private static FoggyAnalyticsAuthority authority(AnalyticsModelDependency dependency) {
        CatalogResolution<QueryModel> resolution = FoggyAdapterTestFixtures.resolution();
        SemanticRequestContext context = SemanticRequestContext.ofNamespace(ENGINE_NAMESPACE)
                .withCatalogResolution(resolution);
        return new FoggyAnalyticsAuthority(
                dependency,
                ENGINE_NAMESPACE,
                resolution,
                context);
    }

    private static QueryExecutionContext<FoggyAnalyticsAuthority> context(
            AnalyticsModelDependency dependency,
            FoggyAnalyticsAuthority authority,
            Map<String, Object> parameters,
            int rowLimit) {
        AnalyticsQuerySpec querySpec = new AnalyticsQuerySpec(
                new AnalyticsQueryRef("sales"),
                dependency.namespace(),
                MODEL,
                List.of("region", "amount"),
                List.of("region"));
        return new QueryExecutionContext<>(
                querySpec,
                dependency,
                parameters,
                rowLimit,
                ZoneId.of("Asia/Shanghai"),
                Locale.SIMPLIFIED_CHINESE,
                "request-1",
                "trace-1",
                authority);
    }

    private static SemanticQueryResponse response() {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of(
                row("east", 12),
                row("west", 8),
                row("north", 5)));
        SemanticQueryResponse.SchemaInfo schema = new SemanticQueryResponse.SchemaInfo();
        schema.setColumns(List.of(
                column("region", DbColumnType.STRING),
                column("amount", DbColumnType.NUMBER),
                column("secretColumn", DbColumnType.TEXT)));
        response.setSchema(schema);
        SemanticQueryResponse.PaginationInfo pagination =
                new SemanticQueryResponse.PaginationInfo();
        pagination.setHasMore(true);
        response.setPagination(pagination);
        response.setWarnings(List.of("credential=must-not-escape"));
        return response;
    }

    private static SemanticQueryResponse.SchemaInfo.ColumnDef column(
            String name,
            DbColumnType type) {
        SemanticQueryResponse.SchemaInfo.ColumnDef column =
                new SemanticQueryResponse.SchemaInfo.ColumnDef();
        column.setName(name);
        column.setDataType(type);
        return column;
    }

    private static Map<String, Object> row(String region, int amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("region", region);
        row.put("amount", amount);
        row.put("secretColumn", "sensitive");
        return row;
    }
}
