package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.runtime.core.query.QueryExecutionContext;
import com.foggyframework.analytics.runtime.core.query.QueryExecutionResult;
import com.foggyframework.analytics.runtime.core.query.QueryExecutor;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.port.SemanticQueryExecutionPort;

import java.util.List;
import java.util.Objects;

import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.AUTHORITY_MISMATCH;
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.QUERY_PARAMETERS_UNSUPPORTED;
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.QUERY_RESPONSE_MISSING;

/** Executes the Analytics QuerySpec subset through Foggy's governed semantic-query port. */
public final class FoggyAnalyticsQueryExecutor
        implements QueryExecutor<FoggyAnalyticsAuthority> {

    private static final String RESPONSE_MODE = "json";

    private final SemanticQueryExecutionPort queryExecutionPort;
    private final FoggyAnalyticsQueryResultMapper resultMapper;

    public FoggyAnalyticsQueryExecutor(SemanticQueryExecutionPort queryExecutionPort) {
        this.queryExecutionPort = Objects.requireNonNull(
                queryExecutionPort,
                "queryExecutionPort");
        this.resultMapper = new FoggyAnalyticsQueryResultMapper();
    }

    @Override
    public QueryExecutionResult execute(QueryExecutionContext<FoggyAnalyticsAuthority> context) {
        Objects.requireNonNull(context, "context");
        FoggyAnalyticsAuthority authority = context.authority();
        boolean pinnedDependencyMismatch = authority.pinnedModelDependency()
                .map(dependency -> !dependency.equals(context.modelDependency()))
                .orElse(false);
        if (pinnedDependencyMismatch
                || !context.querySpec().namespaceRef().equals(authority.namespace())
                || !context.querySpec().modelName().equals(authority.modelName())
                || !authority.modelName().equals(
                authority.catalogResolution().canonicalName())) {
            throw failure(
                    AUTHORITY_MISMATCH,
                    "Resolved Foggy authority does not match the query dependency");
        }
        if (!context.parameters().isEmpty()) {
            throw failure(
                    QUERY_PARAMETERS_UNSUPPORTED,
                    "Analytics QuerySpec v1 has no declared parameter binding contract");
        }

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.copyOf(context.querySpec().columns()));
        request.setGroupBy(context.querySpec().groupBy().stream()
                .map(field -> new SemanticQueryRequest.GroupByItem(field, null))
                .toList());
        request.setStart(0);
        request.setLimit(context.rowLimit());

        SemanticQueryResponse response = queryExecutionPort.queryModel(
                authority.catalogResolution().canonicalName(),
                request,
                RESPONSE_MODE,
                authority.semanticRequestContext());
        if (response == null) {
            throw failure(QUERY_RESPONSE_MISSING, "Foggy query returned no response");
        }
        return resultMapper.map(context.querySpec(), response, context.rowLimit());
    }

    private static FoggyAnalyticsAdapterException failure(
            FoggyAnalyticsAdapterException.Code code,
            String message) {
        return new FoggyAnalyticsAdapterException(code, message);
    }
}
