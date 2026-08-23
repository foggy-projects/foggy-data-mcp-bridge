package com.foggyframework.analytics.runtime.core.render;

import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsDashboardDefinition;
import com.foggyframework.analytics.definition.api.AnalyticsDashboardWidget;
import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsQueryRef;
import com.foggyframework.analytics.definition.api.AnalyticsQuerySpec;
import com.foggyframework.analytics.definition.api.AnalyticsRenderModel;
import com.foggyframework.analytics.definition.api.AnalyticsRenderState;
import com.foggyframework.analytics.definition.api.AnalyticsReportDefinition;
import com.foggyframework.analytics.definition.api.AnalyticsVisualIntent;
import com.foggyframework.analytics.definition.api.AnalyticsWidgetData;
import com.foggyframework.analytics.definition.core.AnalyticsBundleIndex;
import com.foggyframework.analytics.definition.core.AnalyticsDefinitionResolver;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityRequest;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityResolver;
import com.foggyframework.analytics.runtime.core.query.QueryExecutionContext;
import com.foggyframework.analytics.runtime.core.query.QueryExecutionResult;
import com.foggyframework.analytics.runtime.core.query.QueryExecutor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.foggyframework.analytics.runtime.core.render.AnalyticsRenderException.Code.DASHBOARD_NOT_FOUND;
import static com.foggyframework.analytics.runtime.core.render.AnalyticsRenderException.Code.MODEL_DEPENDENCY_NOT_FOUND;
import static com.foggyframework.analytics.runtime.core.render.AnalyticsRenderException.Code.QUERY_NOT_FOUND;
import static com.foggyframework.analytics.runtime.core.render.AnalyticsRenderException.Code.REPORT_NOT_FOUND;

/**
 * Pure Java orchestration for exact-revision Report previews and Dashboard renders.
 *
 * <p>Authority and query execution remain adapter ports. A Dashboard executes each
 * distinct QuerySpec at most once per render and degrades individual failures to
 * widget-level diagnostics.</p>
 */
public final class AnalyticsRenderService<A> {

    public static final int DEFAULT_MAX_ROWS = 1_000;

    private static final String AUTHORITY_FAILURE = "QUERY_AUTHORITY_RESOLUTION_FAILED";
    private static final String EXECUTION_FAILURE = "QUERY_EXECUTION_FAILED";

    private final AnalyticsDefinitionResolver definitionResolver;
    private final QueryAuthorityResolver<A> authorityResolver;
    private final QueryExecutor<A> queryExecutor;
    private final int maxRows;

    public AnalyticsRenderService(
            AnalyticsDefinitionResolver definitionResolver,
            QueryAuthorityResolver<A> authorityResolver,
            QueryExecutor<A> queryExecutor) {
        this(definitionResolver, authorityResolver, queryExecutor, DEFAULT_MAX_ROWS);
    }

    public AnalyticsRenderService(
            AnalyticsDefinitionResolver definitionResolver,
            QueryAuthorityResolver<A> authorityResolver,
            QueryExecutor<A> queryExecutor,
            int maxRows) {
        this.definitionResolver = Objects.requireNonNull(
                definitionResolver,
                "definitionResolver");
        this.authorityResolver = Objects.requireNonNull(authorityResolver, "authorityResolver");
        this.queryExecutor = Objects.requireNonNull(queryExecutor, "queryExecutor");
        if (maxRows <= 0) {
            throw new IllegalArgumentException("maxRows must be positive");
        }
        this.maxRows = maxRows;
    }

    public AnalyticsRenderModel preview(AnalyticsReportPreviewRequest request) {
        Objects.requireNonNull(request, "request");
        AnalyticsBundleIndex index = definitionResolver.resolve(
                request.bundleRef(),
                request.expectedBundleRevision());
        AnalyticsReportDefinition report = index.report(request.reportRef())
                .orElseThrow(() -> new AnalyticsRenderException(
                        REPORT_NOT_FOUND,
                        "Report definition does not exist: " + request.reportRef().value()));
        AnalyticsQuerySpec query = requireQuery(index, report.queryRef());
        QueryOutcome outcome = execute(index, query, request.context());
        AnalyticsWidgetData widget = outcome.toWidget(
                report.artifactRef().value(),
                report.visualIntent());
        return new AnalyticsRenderModel(
                report.artifactRef(),
                index.bundleRevision(),
                widget.state(),
                List.of(widget),
                aggregateDiagnostics(List.of(widget)));
    }

    public AnalyticsRenderModel render(AnalyticsDashboardRenderRequest request) {
        Objects.requireNonNull(request, "request");
        AnalyticsBundleIndex index = definitionResolver.resolve(
                request.bundleRef(),
                request.expectedBundleRevision());
        AnalyticsDashboardDefinition dashboard = index.dashboard(request.dashboardRef())
                .orElseThrow(() -> new AnalyticsRenderException(
                        DASHBOARD_NOT_FOUND,
                        "Dashboard definition does not exist: "
                                + request.dashboardRef().value()));
        Map<AnalyticsQueryRef, QueryOutcome> outcomes = new LinkedHashMap<>();
        List<AnalyticsWidgetData> widgets = new ArrayList<>();
        for (AnalyticsDashboardWidget widget : dashboard.widgets()) {
            AnalyticsQuerySpec query = resolveWidgetQuery(index, widget);
            QueryOutcome outcome = outcomes.computeIfAbsent(
                    query.queryRef(),
                    ignored -> execute(index, query, request.context()));
            widgets.add(outcome.toWidget(widget.widgetRef(), widget.visualIntent()));
        }
        return new AnalyticsRenderModel(
                dashboard.artifactRef(),
                index.bundleRevision(),
                aggregateState(widgets),
                widgets,
                aggregateDiagnostics(widgets));
    }

    private QueryOutcome execute(
            AnalyticsBundleIndex index,
            AnalyticsQuerySpec query,
            AnalyticsRenderRequestContext requestContext) {
        AnalyticsModelDependency dependency = index.modelDependency(query)
                .orElseThrow(() -> new AnalyticsRenderException(
                        MODEL_DEPENDENCY_NOT_FOUND,
                        "Query has no pinned QM model dependency: "
                                + query.queryRef().value()));
        A authority;
        try {
            authority = authorityResolver.resolve(new QueryAuthorityRequest(
                    dependency,
                    requestContext.authorityBinding(),
                    requestContext.requestId(),
                    requestContext.traceId()));
            if (authority == null) {
                return QueryOutcome.failure(AUTHORITY_FAILURE);
            }
        } catch (RuntimeException failure) {
            return QueryOutcome.failure(AUTHORITY_FAILURE);
        }

        try {
            QueryExecutionResult result = queryExecutor.execute(new QueryExecutionContext<>(
                    query,
                    dependency,
                    requestContext.parameters(),
                    maxRows,
                    requestContext.timezone(),
                    requestContext.locale(),
                    requestContext.requestId(),
                    requestContext.traceId(),
                    authority));
            if (result == null) {
                return QueryOutcome.failure(EXECUTION_FAILURE);
            }
            return QueryOutcome.success(bound(result));
        } catch (RuntimeException failure) {
            return QueryOutcome.failure(EXECUTION_FAILURE);
        }
    }

    private QueryExecutionResult bound(QueryExecutionResult result) {
        int boundedSize = Math.min(result.rows().size(), maxRows);
        boolean truncated = result.truncated() || result.rows().size() > maxRows;
        return new QueryExecutionResult(
                result.columns(),
                result.rows().subList(0, boundedSize),
                truncated,
                result.diagnostics());
    }

    private static AnalyticsQuerySpec resolveWidgetQuery(
            AnalyticsBundleIndex index,
            AnalyticsDashboardWidget widget) {
        if (widget.queryRef() != null) {
            return requireQuery(index, widget.queryRef());
        }
        AnalyticsReportDefinition report = index.report(widget.reportRef())
                .orElseThrow(() -> new AnalyticsRenderException(
                        REPORT_NOT_FOUND,
                        "Dashboard report definition does not exist: "
                                + widget.reportRef().value()));
        return requireQuery(index, report.queryRef());
    }

    private static AnalyticsQuerySpec requireQuery(
            AnalyticsBundleIndex index,
            AnalyticsQueryRef queryRef) {
        return index.query(queryRef)
                .orElseThrow(() -> new AnalyticsRenderException(
                        QUERY_NOT_FOUND,
                        "Query definition does not exist: " + queryRef.value()));
    }

    private static AnalyticsRenderState aggregateState(List<AnalyticsWidgetData> widgets) {
        if (widgets.isEmpty()) {
            return AnalyticsRenderState.EMPTY;
        }
        long errors = widgets.stream()
                .filter(widget -> widget.state() == AnalyticsRenderState.ERROR)
                .count();
        long unsupported = widgets.stream()
                .filter(widget -> widget.state() == AnalyticsRenderState.UNSUPPORTED)
                .count();
        if (errors == widgets.size()) {
            return AnalyticsRenderState.ERROR;
        }
        if (unsupported == widgets.size()) {
            return AnalyticsRenderState.UNSUPPORTED;
        }
        if (errors > 0 || unsupported > 0) {
            return AnalyticsRenderState.PARTIAL;
        }
        return widgets.stream().anyMatch(widget -> widget.state() == AnalyticsRenderState.READY)
                ? AnalyticsRenderState.READY
                : AnalyticsRenderState.EMPTY;
    }

    private static List<String> aggregateDiagnostics(List<AnalyticsWidgetData> widgets) {
        List<String> diagnostics = new ArrayList<>();
        for (AnalyticsWidgetData widget : widgets) {
            for (String diagnostic : widget.diagnostics()) {
                diagnostics.add(widget.widgetRef() + ":" + diagnostic);
            }
        }
        return List.copyOf(diagnostics);
    }

    private record QueryOutcome(
            QueryExecutionResult result,
            String failureDiagnostic) {

        static QueryOutcome success(QueryExecutionResult result) {
            return new QueryOutcome(Objects.requireNonNull(result, "result"), null);
        }

        static QueryOutcome failure(String diagnostic) {
            return new QueryOutcome(null, Objects.requireNonNull(diagnostic, "diagnostic"));
        }

        AnalyticsWidgetData toWidget(String widgetRef, AnalyticsVisualIntent visualIntent) {
            if (result == null) {
                return new AnalyticsWidgetData(
                        widgetRef,
                        visualIntent,
                        AnalyticsRenderState.ERROR,
                        List.of(),
                        List.of(),
                        false,
                        List.of(failureDiagnostic));
            }
            AnalyticsRenderState state = result.rows().isEmpty()
                    ? AnalyticsRenderState.EMPTY
                    : AnalyticsRenderState.READY;
            return new AnalyticsWidgetData(
                    widgetRef,
                    visualIntent,
                    state,
                    result.columns(),
                    result.rows(),
                    result.truncated(),
                    result.diagnostics());
        }
    }
}
