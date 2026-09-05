package com.foggyframework.analytics.runtime.foggy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.function.contract.AnalyticsComposeFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsComposeResult;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelResult;
import com.foggyframework.analytics.runtime.core.function.AnalyticsAdvancedSemanticFunctionOperations;
import com.foggyframework.analytics.runtime.core.function.AnalyticsSemanticFunctionException;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityBinding;
import com.foggyframework.core.ex.ExRuntimeException;
import com.foggyframework.dataset.model.semantic.domain.QueryInputWarning;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionException;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.semantic.port.ComposeCaller;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionException;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionPort;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionRequest;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionResult;
import com.foggyframework.dataset.model.semantic.port.ComposeOperation;
import com.foggyframework.dataset.model.semantic.port.SemanticQueryExecutionPort;
import com.foggyframework.dataset.model.semantic.support.QueryInputValidationException;
import com.foggyframework.dataset.model.semantic.support.QueryInputWarnings;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import com.foggyframework.dataset.model.semantic.support.UnknownQueryPropertyPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Foggy adapter for the complete single-model DSL and restricted Compose/CTE. */
public final class FoggyAnalyticsAdvancedSemanticFunctionOperations
        implements AnalyticsAdvancedSemanticFunctionOperations {

    private static final TypeReference<LinkedHashMap<String, Object>> JSON_OBJECT =
            new TypeReference<>() { };
    private static final TypeReference<java.util.List<Object>> JSON_LIST =
            new TypeReference<>() { };
    private static final Pattern STABLE_VALIDATION_CODE =
            Pattern.compile("^([A-Z][A-Z0-9_]{2,63})(?::|$)");
    private static final Set<String> EXACT_VALIDATION_CODES = Set.of(
            "ILLEGAL_DOUBLE_AGGREGATION");
    private static final java.util.List<String> VALIDATION_CODE_PREFIXES = java.util.List.of(
            "CALCULATE_",
            "DSL_CTE_",
            "DUPLICATE_QUERY_",
            "OUTPUT_FORMATTING_",
            "QUERY_MODEL_",
            "UNKNOWN_QUERY_",
            "PROTECTED_QUERY_",
            "SEMANTIC_SQL_",
            "TERMINAL_PLAN_",
            "TIME_WINDOW_");

    private final FoggyQueryAuthorityResolver queryAuthorityResolver;
    private final FoggyComposeCallerResolver composeCallerResolver;
    private final SemanticQueryExecutionPort queryExecutionPort;
    private final ComposeExecutionPort composeExecutionPort;
    private final ObjectMapper json;
    private final SemanticQueryPayloadMapper queryPayloadMapper;
    private final UnknownQueryPropertyPolicy unknownQueryPropertyPolicy;
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
        this(queryAuthorityResolver, composeCallerResolver, queryExecutionPort,
                composeExecutionPort, json, maxRows, composeDialect, namespaceMapper,
                UnknownQueryPropertyPolicy.WARN);
    }

    public FoggyAnalyticsAdvancedSemanticFunctionOperations(
            FoggyQueryAuthorityResolver queryAuthorityResolver,
            FoggyComposeCallerResolver composeCallerResolver,
            SemanticQueryExecutionPort queryExecutionPort,
            ComposeExecutionPort composeExecutionPort,
            ObjectMapper json,
            int maxRows,
            String composeDialect,
            FoggyAnalyticsNamespaceMapper namespaceMapper,
            UnknownQueryPropertyPolicy unknownQueryPropertyPolicy) {
        this.queryAuthorityResolver = Objects.requireNonNull(
                queryAuthorityResolver, "queryAuthorityResolver");
        this.composeCallerResolver = Objects.requireNonNull(
                composeCallerResolver, "composeCallerResolver");
        this.queryExecutionPort = Objects.requireNonNull(
                queryExecutionPort, "queryExecutionPort");
        this.composeExecutionPort = Objects.requireNonNull(
                composeExecutionPort, "composeExecutionPort");
        this.json = Objects.requireNonNull(json, "json").copy();
        this.queryPayloadMapper = new SemanticQueryPayloadMapper(this.json);
        this.unknownQueryPropertyPolicy = UnknownQueryPropertyPolicy.orDefault(unknownQueryPropertyPolicy);
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
            semanticRequest = queryPayloadMapper.toQueryRequest(
                    request.payload(), unknownQueryPropertyPolicy);
            Integer requestedLimit = semanticRequest.getLimit();
            if (requestedLimit == null || requestedLimit > maxRows) {
                semanticRequest.setLimit(maxRows);
            }
        } catch (IllegalArgumentException invalid) {
            throw queryInvalid("Foggy query-model payload is invalid", invalid);
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
            QueryInputWarnings.attach(response, semanticRequest);
            if (response == null) {
                throw failure(
                        AnalyticsSemanticFunctionException.Code.RESPONSE_INVALID,
                        "Foggy query-model response is missing");
            }
            return new AnalyticsQueryModelResult(
                    request.namespace(),
                    request.modelName(),
                    request.mode(),
                    json.convertValue(response, JSON_OBJECT));
        } catch (AnalyticsSemanticFunctionException known) {
            throw known;
        } catch (IllegalArgumentException invalid) {
            throw queryInvalid("Foggy query-model DSL is invalid", invalid);
        } catch (RuntimeException failed) {
            String validationCode = queryValidationCode(request.mode(), failed);
            if (validationCode != null) {
                throw failure(
                        AnalyticsSemanticFunctionException.Code.QUERY_INVALID,
                        "Foggy query-model DSL is invalid",
                        validationCode,
                        failed);
            }
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
        try {
            return queryAuthorityResolver.resolveCurrent(
                    new FoggyCurrentQueryAuthorityRequest(
                            new AnalyticsNamespaceRef(request.namespace()),
                            request.modelName(),
                            new QueryAuthorityBinding(
                                    request.authority().provider(),
                                    request.authority().reference()),
                            context.requestId(),
                            context.traceId()));
        } catch (FoggyAnalyticsAdapterException failed) {
            AnalyticsSemanticFunctionException.Code code = switch (failed.code()) {
                case MODEL_NOT_FOUND, MODEL_NAME_NOT_CANONICAL ->
                        AnalyticsSemanticFunctionException.Code.MODEL_NOT_FOUND;
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

    private static AnalyticsSemanticFunctionException failure(
            AnalyticsSemanticFunctionException.Code code,
            String message,
            String validationCode,
            Throwable cause) {
        return new AnalyticsSemanticFunctionException(
                code, message, validationCode, queryInputViolations(cause), cause);
    }

    private static List<Map<String, Object>> queryInputViolations(Throwable failure) {
        for (Throwable current = failure; current != null; current = nextCause(current)) {
            if (current instanceof QueryInputValidationException queryInputFailure) {
                return queryInputFailure.getViolations().stream()
                        .map(FoggyAnalyticsAdvancedSemanticFunctionOperations::queryInputViolation)
                        .toList();
            }
        }
        return List.of();
    }

    private static Map<String, Object> queryInputViolation(QueryInputWarning warning) {
        Map<String, Object> violation = new LinkedHashMap<>();
        violation.put("code", warning.code());
        violation.put("path", warning.path());
        violation.put("message", warning.message());
        violation.put("suggestedNextAction", warning.suggestedNextAction());
        violation.put("safeToAutoRepair", warning.safeToAutoRepair());
        violation.put("normalizedFragment", warning.normalizedFragment());
        violation.put("docsRef", warning.docsRef());
        violation.put("details", warning.details());
        return violation;
    }

    private static AnalyticsSemanticFunctionException queryInvalid(
            String message,
            Throwable cause) {
        String validationCode = stableValidationCode(cause);
        if (validationCode == null) {
            return failure(
                    AnalyticsSemanticFunctionException.Code.QUERY_INVALID,
                    message,
                    cause);
        }
        return failure(
                AnalyticsSemanticFunctionException.Code.QUERY_INVALID,
                message,
                validationCode,
                cause);
    }

    private static String queryValidationCode(String mode, RuntimeException failure) {
        if (!"validate".equals(mode) || containsAuthorityFailure(failure)) {
            return null;
        }
        String stableCode = stableValidationCode(failure);
        if (stableCode != null) {
            return stableCode;
        }
        for (Throwable current = failure; current != null; current = nextCause(current)) {
            if (current instanceof ExRuntimeException && current.getCause() == null) {
                return inferredValidationCode(failure);
            }
        }
        return null;
    }

    private static String inferredValidationCode(Throwable failure) {
        for (Throwable current = failure; current != null; current = nextCause(current)) {
            String message = current.getMessage();
            if (message == null) {
                continue;
            }
            String normalized = message.toLowerCase(java.util.Locale.ROOT);
            if (normalized.contains("orderby")) {
                return "QUERY_MODEL_ORDER_BY_INVALID";
            }
            if (normalized.contains("groupby")) {
                return "QUERY_MODEL_GROUP_BY_INVALID";
            }
            if (normalized.contains("slice")
                    || normalized.contains("having")
                    || normalized.contains("过滤")) {
                return "QUERY_MODEL_FILTER_INVALID";
            }
            if (normalized.contains("查询字段")
                    || normalized.contains("columns")) {
                return "QUERY_MODEL_COLUMNS_INVALID";
            }
            if (normalized.contains("字段")
                    || normalized.contains("field")) {
                return "QUERY_MODEL_FIELD_INVALID";
            }
        }
        return "SEMANTIC_QUERY_INVALID";
    }

    private static String stableValidationCode(Throwable failure) {
        for (Throwable current = failure; current != null; current = nextCause(current)) {
            String message = current.getMessage();
            if (message == null) {
                continue;
            }
            Matcher matcher = STABLE_VALIDATION_CODE.matcher(message.stripLeading());
            if (matcher.find() && isValidationCode(matcher.group(1))) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static boolean isValidationCode(String code) {
        if (EXACT_VALIDATION_CODES.contains(code)) {
            return true;
        }
        return VALIDATION_CODE_PREFIXES.stream().anyMatch(code::startsWith);
    }

    private static boolean containsAuthorityFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = nextCause(current)) {
            if (current instanceof ModelPermissionException
                    || current instanceof SecurityException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null
                    && (message.contains("访问被拒绝")
                    || message.contains("权限错误"))) {
                return true;
            }
        }
        return false;
    }

    private static Throwable nextCause(Throwable failure) {
        Throwable cause = failure.getCause();
        return cause == failure ? null : cause;
    }
}
