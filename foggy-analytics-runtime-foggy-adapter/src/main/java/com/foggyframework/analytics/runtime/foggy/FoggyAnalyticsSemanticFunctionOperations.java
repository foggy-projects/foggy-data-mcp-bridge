package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelDescription;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQuery;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryResult;
import com.foggyframework.analytics.runtime.core.function.AnalyticsSemanticFunctionException;
import com.foggyframework.analytics.runtime.core.function.AnalyticsSemanticFunctionOperations;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityBinding;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.semantic.port.SemanticQueryExecutionPort;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Governed Foggy implementation of the product-neutral direct-question Function surface. */
public final class FoggyAnalyticsSemanticFunctionOperations
        implements AnalyticsSemanticFunctionOperations {

    private static final String METADATA_FORMAT = "markdown";
    private static final String EXECUTE_MODE = "execute";
    private static final Set<String> SAFE_WARNING_CODES = Set.of(
            "EMPTY_RESULT", "PARTIAL_RESULT", "RESULT_TRUNCATED");

    private final FoggyQueryAuthorityResolver authorityResolver;
    private final SemanticServiceV3 metadataService;
    private final SemanticQueryExecutionPort queryExecutionPort;
    private final int maxRows;

    public FoggyAnalyticsSemanticFunctionOperations(
            FoggyQueryAuthorityResolver authorityResolver,
            SemanticServiceV3 metadataService,
            SemanticQueryExecutionPort queryExecutionPort,
            int maxRows) {
        this.authorityResolver = Objects.requireNonNull(
                authorityResolver, "authorityResolver");
        this.metadataService = Objects.requireNonNull(metadataService, "metadataService");
        this.queryExecutionPort = Objects.requireNonNull(
                queryExecutionPort, "queryExecutionPort");
        if (maxRows < 1) {
            throw new IllegalArgumentException("maxRows must be positive");
        }
        this.maxRows = maxRows;
    }

    @Override
    public AnalyticsSemanticModelDescription describeModel(
            AnalyticsSemanticModelFunctionRequest request,
            AnalyticsFunctionContext context) {
        FoggyAnalyticsAuthority authority = resolve(
                request.namespace(),
                request.modelName(),
                request.expectedModelRevision(),
                request.authority().provider(),
                request.authority().reference(),
                context);
        SemanticMetadataRequest metadataRequest = new SemanticMetadataRequest();
        metadataRequest.setQmModels(List.of(authority.modelDependency().modelName()));
        metadataRequest.setIncludeExamples(false);
        metadataRequest.setTolerateModelLoadErrors(false);
        try {
            SemanticMetadataResponse response = metadataService.getMetadata(
                    metadataRequest,
                    METADATA_FORMAT,
                    authority.semanticRequestContext()
                            .withPermissionAction(PermissionAction.DESCRIBE));
            if (response == null || response.getContent() == null
                    || response.getContent().isBlank()) {
                throw failure(
                        AnalyticsSemanticFunctionException.Code.RESPONSE_INVALID,
                        "Foggy semantic metadata response is missing");
            }
            return new AnalyticsSemanticModelDescription(
                    request.namespace(),
                    request.modelName(),
                    request.expectedModelRevision(),
                    response.getFormat() == null ? METADATA_FORMAT : response.getFormat(),
                    response.getContent().trim());
        } catch (AnalyticsSemanticFunctionException known) {
            throw known;
        } catch (RuntimeException failed) {
            throw failure(
                    AnalyticsSemanticFunctionException.Code.QUERY_FAILED,
                    "Foggy semantic model description failed",
                    failed);
        }
    }

    @Override
    public AnalyticsSemanticQueryResult executeQuery(
            AnalyticsSemanticQueryFunctionRequest request,
            AnalyticsFunctionContext context) {
        FoggyAnalyticsAuthority authority = resolve(
                request.namespace(),
                request.modelName(),
                request.expectedModelRevision(),
                request.authority().provider(),
                request.authority().reference(),
                context);
        SemanticQueryRequest semanticRequest = map(request.query());
        try {
            SemanticQueryResponse response = queryExecutionPort.queryModel(
                    authority.catalogResolution().canonicalName(),
                    semanticRequest,
                    EXECUTE_MODE,
                    authority.semanticRequestContext()
                            .withPermissionAction(PermissionAction.EXECUTE));
            if (response == null) {
                throw failure(
                        AnalyticsSemanticFunctionException.Code.RESPONSE_INVALID,
                        "Foggy semantic query response is missing");
            }
            return result(request, response);
        } catch (AnalyticsSemanticFunctionException known) {
            throw known;
        } catch (IllegalArgumentException invalid) {
            throw failure(
                    AnalyticsSemanticFunctionException.Code.QUERY_INVALID,
                    "Foggy semantic query is invalid",
                    invalid);
        } catch (RuntimeException failed) {
            throw failure(
                    AnalyticsSemanticFunctionException.Code.QUERY_FAILED,
                    "Foggy semantic query failed",
                    failed);
        }
    }

    private FoggyAnalyticsAuthority resolve(
            String namespace,
            String modelName,
            String modelRevision,
            String authorityProvider,
            String authorityReference,
            AnalyticsFunctionContext context) {
        AnalyticsModelDependency dependency = new AnalyticsModelDependency(
                new AnalyticsNamespaceRef(namespace),
                "qm",
                modelName,
                new AnalyticsModelRevision(modelRevision));
        try {
            return authorityResolver.resolve(new QueryAuthorityRequest(
                    dependency,
                    new QueryAuthorityBinding(authorityProvider, authorityReference),
                    context.requestId(),
                    context.traceId()));
        } catch (FoggyAnalyticsAdapterException failed) {
            AnalyticsSemanticFunctionException.Code code = switch (failed.code()) {
                case MODEL_NOT_FOUND, MODEL_NAME_NOT_CANONICAL ->
                        AnalyticsSemanticFunctionException.Code.MODEL_NOT_FOUND;
                case MODEL_REVISION_MISMATCH, MODEL_REVISION_UNAVAILABLE ->
                        AnalyticsSemanticFunctionException.Code.MODEL_REVISION_CONFLICT;
                default -> AnalyticsSemanticFunctionException.Code.QUERY_FAILED;
            };
            throw failure(code, "Foggy semantic authority resolution failed", failed);
        }
    }

    private SemanticQueryRequest map(AnalyticsSemanticQuery source) {
        SemanticQueryRequest target = new SemanticQueryRequest();
        target.setColumns(source.columns());
        target.setSlice(source.filters().stream().map(filter -> {
            SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
            item.setField(filter.field());
            item.setOp(filter.operator());
            item.setValue(filter.value());
            return item;
        }).toList());
        target.setGroupBy(source.groupBy().stream()
                .map(group -> new SemanticQueryRequest.GroupByItem(
                        group.field(), group.aggregation()))
                .toList());
        target.setOrderBy(source.orderBy().stream().map(order -> {
            SemanticQueryRequest.OrderItem item = new SemanticQueryRequest.OrderItem();
            item.setField(order.field());
            item.setDir(order.direction());
            return item;
        }).toList());
        target.setStart(source.start());
        target.setLimit(Math.min(source.limit(), maxRows));
        target.setReturnTotal(source.returnTotal());
        target.setDistinct(source.distinct());
        return target;
    }

    private AnalyticsSemanticQueryResult result(
            AnalyticsSemanticQueryFunctionRequest request,
            SemanticQueryResponse response) {
        List<Map<String, Object>> sourceRows = response.getItems() == null
                ? List.of()
                : response.getItems();
        int returned = Math.min(sourceRows.size(), maxRows);
        List<Map<String, Object>> rows = new ArrayList<>(returned);
        Set<String> requestedColumns = new LinkedHashSet<>(
                request.query().columns());
        for (int index = 0; index < returned; index++) {
            Map<String, Object> sourceRow = sourceRows.get(index);
            Map<String, Object> projected = new LinkedHashMap<>();
            requestedColumns.forEach(name -> {
                if (sourceRow.containsKey(name)) {
                    projected.put(name, sourceRow.get(name));
                }
            });
            rows.add(projected);
        }
        boolean engineHasMore = Boolean.TRUE.equals(response.getHasNext())
                || response.getPagination() != null
                && Boolean.TRUE.equals(response.getPagination().getHasMore());
        boolean truncated = sourceRows.size() > maxRows || engineHasMore;
        return new AnalyticsSemanticQueryResult(
                request.namespace(),
                request.modelName(),
                request.expectedModelRevision(),
                columns(response, rows, requestedColumns),
                rows,
                response.getTotal(),
                engineHasMore,
                truncated,
                warnings(response.getWarnings(), truncated));
    }

    private static List<AnalyticsSemanticQueryResult.Column> columns(
            SemanticQueryResponse response,
            List<Map<String, Object>> rows,
            Set<String> requestedColumns) {
        if (response.getSchema() != null && response.getSchema().getColumns() != null) {
            return response.getSchema().getColumns().stream()
                    .filter(column -> requestedColumns.contains(column.getName()))
                    .map(column -> new AnalyticsSemanticQueryResult.Column(
                            column.getName(),
                            column.getDataType() == null
                                    ? "UNKNOWN"
                                    : column.getDataType().name(),
                            column.getTitle()))
                    .toList();
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        return rows.get(0).keySet().stream()
                .map(name -> new AnalyticsSemanticQueryResult.Column(
                        name, "UNKNOWN", null))
                .toList();
    }

    private static List<String> warnings(List<String> source, boolean truncated) {
        LinkedHashSet<String> safe = new LinkedHashSet<>();
        if (source != null) {
            source.forEach(value -> safe.add(SAFE_WARNING_CODES.contains(value)
                    ? value
                    : "SEMANTIC_QUERY_WARNING"));
        }
        if (truncated) {
            safe.add("RESULT_TRUNCATED");
        }
        return List.copyOf(safe);
    }

    private static AnalyticsSemanticFunctionException failure(
            AnalyticsSemanticFunctionException.Code code,
            String message) {
        return new AnalyticsSemanticFunctionException(code, message);
    }

    private static AnalyticsSemanticFunctionException failure(
            AnalyticsSemanticFunctionException.Code code,
            String message,
            Throwable cause) {
        return new AnalyticsSemanticFunctionException(code, message, cause);
    }
}
