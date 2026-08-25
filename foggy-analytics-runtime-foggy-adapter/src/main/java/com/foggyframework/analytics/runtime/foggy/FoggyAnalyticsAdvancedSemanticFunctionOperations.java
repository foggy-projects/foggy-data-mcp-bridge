package com.foggyframework.analytics.runtime.foggy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.function.contract.AnalyticsComposeFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsComposeResult;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelResult;
import com.foggyframework.analytics.runtime.core.function.AnalyticsAdvancedSemanticFunctionOperations;
import com.foggyframework.analytics.runtime.core.function.AnalyticsSemanticFunctionException;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityBinding;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.semantic.port.ComposeCaller;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionException;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionPort;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionRequest;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionResult;
import com.foggyframework.dataset.model.semantic.port.ComposeOperation;
import com.foggyframework.dataset.model.semantic.port.SemanticQueryExecutionPort;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Foggy adapter for the complete single-model DSL and restricted Compose/CTE. */
public final class FoggyAnalyticsAdvancedSemanticFunctionOperations
        implements AnalyticsAdvancedSemanticFunctionOperations {

    private static final TypeReference<LinkedHashMap<String, Object>> JSON_OBJECT =
            new TypeReference<>() { };
    private static final TypeReference<java.util.List<Object>> JSON_LIST =
            new TypeReference<>() { };

    private final FoggyQueryAuthorityResolver queryAuthorityResolver;
    private final FoggyComposeCallerResolver composeCallerResolver;
    private final SemanticQueryExecutionPort queryExecutionPort;
    private final ComposeExecutionPort composeExecutionPort;
    private final ObjectMapper json;
    private final int maxRows;
    private final String composeDialect;
    private final FoggyAnalyticsNamespaceMapper namespaceMapper;

    public FoggyAnalyticsAdvancedSemanticFunctionOperations(
            FoggyQueryAuthorityResolver queryAuthorityResolver,
            FoggyComposeCallerResolver composeCallerResolver,
            SemanticQueryExecutionPort queryExecutionPort,
            ComposeExecutionPort composeExecutionPort,
            ObjectMapper json,
            int maxRows,
            String composeDialect) {
        this(
                queryAuthorityResolver,
                composeCallerResolver,
                queryExecutionPort,
                composeExecutionPort,
                json,
                maxRows,
                composeDialect,
                FoggyAnalyticsNamespaceMapper.defaultConvention());
    }

    public FoggyAnalyticsAdvancedSemanticFunctionOperations(
            FoggyQueryAuthorityResolver queryAuthorityResolver,
            FoggyComposeCallerResolver composeCallerResolver,
            SemanticQueryExecutionPort queryExecutionPort,
            ComposeExecutionPort composeExecutionPort,
            ObjectMapper json,
            int maxRows,
            String composeDialect,
            FoggyAnalyticsNamespaceMapper namespaceMapper) {
        this.queryAuthorityResolver = Objects.requireNonNull(
                queryAuthorityResolver, "queryAuthorityResolver");
        this.composeCallerResolver = Objects.requireNonNull(
                composeCallerResolver, "composeCallerResolver");
        this.queryExecutionPort = Objects.requireNonNull(
                queryExecutionPort, "queryExecutionPort");
        this.composeExecutionPort = Objects.requireNonNull(
                composeExecutionPort, "composeExecutionPort");
        this.json = Objects.requireNonNull(json, "json").copy();
        if (maxRows < 1) {
            throw new IllegalArgumentException("maxRows must be positive");
        }
        this.maxRows = maxRows;
        this.composeDialect = requireText("composeDialect", composeDialect);
        this.namespaceMapper = Objects.requireNonNull(namespaceMapper, "namespaceMapper");
    }

    @Override
    public AnalyticsQueryModelResult runQueryModel(
            AnalyticsQueryModelFunctionRequest request,
            AnalyticsFunctionContext context) {
        FoggyAnalyticsAuthority authority = resolveModel(request, context);
        SemanticQueryRequest semanticRequest;
        try {
            semanticRequest = json.convertValue(request.payload(), SemanticQueryRequest.class);
            Integer requestedLimit = semanticRequest.getLimit();
            if (requestedLimit == null || requestedLimit > maxRows) {
                semanticRequest.setLimit(maxRows);
            }
        } catch (IllegalArgumentException invalid) {
            throw failure(
                    AnalyticsSemanticFunctionException.Code.QUERY_INVALID,
                    "Foggy query-model payload is invalid",
                    invalid);
        }
        try {
            SemanticQueryResponse response = queryExecutionPort.queryModel(
                    authority.catalogResolution().canonicalName(),
                    semanticRequest,
                    request.mode(),
                    authority.semanticRequestContext().withPermissionAction(
                            "validate".equals(request.mode())
                                    ? PermissionAction.VALIDATE
                                    : PermissionAction.EXECUTE));
            if (response == null) {
                throw failure(
                        AnalyticsSemanticFunctionException.Code.RESPONSE_INVALID,
                        "Foggy query-model response is missing");
            }
            return new AnalyticsQueryModelResult(
                    request.namespace(),
                    request.modelName(),
                    request.expectedModelRevision(),
                    request.mode(),
                    json.convertValue(response, JSON_OBJECT));
        } catch (AnalyticsSemanticFunctionException known) {
            throw known;
        } catch (IllegalArgumentException invalid) {
            throw failure(
                    AnalyticsSemanticFunctionException.Code.QUERY_INVALID,
                    "Foggy query-model DSL is invalid",
                    invalid);
        } catch (RuntimeException failed) {
            throw failure(
                    AnalyticsSemanticFunctionException.Code.QUERY_FAILED,
                    "Foggy query-model execution failed",
                    failed);
        }
    }

    @Override
    public AnalyticsComposeResult runCompose(
            AnalyticsComposeFunctionRequest request,
            AnalyticsFunctionContext context) {
        ComposeCaller caller;
        try {
            caller = composeCallerResolver.resolve(new FoggyComposeAuthorityRequest(
                    request.namespace(),
                    new QueryAuthorityBinding(
                            request.authority().provider(),
                            request.authority().reference()),
                    context.requestId(),
                    context.traceId()));
            if (caller == null) {
                throw new IllegalStateException("Compose caller is unavailable");
            }
        } catch (RuntimeException unavailable) {
            throw failure(
                    AnalyticsSemanticFunctionException.Code.COMPOSE_FAILED,
                    "Foggy Compose authority resolution failed",
                    unavailable);
        }
        try {
            ComposeExecutionResult result = composeExecutionPort.execute(
                    new ComposeExecutionRequest(
                            composeOperation(request.mode()),
                            request.script(),
                            engineNamespace(request.namespace()),
                            context.traceId(),
                            request.params(),
                            caller,
                            composeDialect));
            if (result == null) {
                throw failure(
                        AnalyticsSemanticFunctionException.Code.RESPONSE_INVALID,
                        "Foggy Compose response is missing");
            }
            Object value;
            java.util.List<Object> params;
            try {
                value = json.convertValue(result.value(), Object.class);
                params = json.convertValue(result.params(), JSON_LIST);
            } catch (IllegalArgumentException invalid) {
                throw failure(
                        AnalyticsSemanticFunctionException.Code.RESPONSE_INVALID,
                        "Foggy Compose response is not JSON-safe",
                        invalid);
            }
            return new AnalyticsComposeResult(
                    request.namespace(),
                    request.mode(),
                    result.valid(),
                    result.executed(),
                    value,
                    result.sql(),
                    params,
                    result.warnings());
        } catch (AnalyticsSemanticFunctionException known) {
            throw known;
        } catch (ComposeExecutionException failed) {
            AnalyticsSemanticFunctionException.Code code = switch (failed.kind()) {
                case SANDBOX -> AnalyticsSemanticFunctionException.Code.COMPOSE_SANDBOX;
                case SCHEMA, COMPILE -> AnalyticsSemanticFunctionException.Code.COMPOSE_INVALID;
                case AUTHORITY -> AnalyticsSemanticFunctionException.Code.COMPOSE_FAILED;
            };
            throw failure(code, "Foggy Compose execution was rejected", failed);
        } catch (IllegalArgumentException invalid) {
            throw failure(
                    AnalyticsSemanticFunctionException.Code.COMPOSE_INVALID,
                    "Foggy Compose request is invalid",
                    invalid);
        } catch (RuntimeException failed) {
            throw failure(
                    AnalyticsSemanticFunctionException.Code.COMPOSE_FAILED,
                    "Foggy Compose execution failed",
                    failed);
        }
    }

    private FoggyAnalyticsAuthority resolveModel(
            AnalyticsQueryModelFunctionRequest request,
            AnalyticsFunctionContext context) {
        AnalyticsModelDependency dependency = new AnalyticsModelDependency(
                new AnalyticsNamespaceRef(request.namespace()),
                "qm",
                request.modelName(),
                new AnalyticsModelRevision(request.expectedModelRevision()));
        try {
            return queryAuthorityResolver.resolve(new QueryAuthorityRequest(
                    dependency,
                    new QueryAuthorityBinding(
                            request.authority().provider(),
                            request.authority().reference()),
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
            throw failure(code, "Foggy query-model authority resolution failed", failed);
        }
    }

    private static ComposeOperation composeOperation(String mode) {
        return switch (mode) {
            case "validate" -> ComposeOperation.VALIDATE;
            case "preview" -> ComposeOperation.PREVIEW;
            case "execute" -> ComposeOperation.EXECUTE;
            default -> throw new IllegalArgumentException("Unsupported Compose mode");
        };
    }

    private String engineNamespace(String namespace) {
        String mapped = namespaceMapper.toEngineNamespace(
                new AnalyticsNamespaceRef(namespace));
        return Objects.requireNonNull(mapped, "mapped engine namespace");
    }

    private static String requireText(String field, String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
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
