package com.foggyframework.analytics.function.fap;

import com.foggyframework.analytics.function.contract.AnalyticsArtifactFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleList;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionCapabilities;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContract;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionErrorCodes;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionJsonValues;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionOperations;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyListRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;
import com.foggyframework.analytics.function.sdk.AnalyticsFunctionClient;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Synchronous descriptor/request/result/error mapper between FAP and Analytics.
 *
 * <p>The adapter executes exactly once through the supplied SDK client. It has
 * no HTTP endpoint, credential handling, callback retry, lifecycle persistence
 * or product permission implementation.</p>
 */
public final class FapAnalyticsFunctionAdapter {

    private static final Set<String> EMPTY_ARGUMENTS = Set.of();
    private static final Set<String> BUNDLE_ARGUMENTS = Set.of(
            "bundleRef", "expectedBundleRevision");
    private static final Set<String> ARTIFACT_ARGUMENTS = Set.of(
            "bundleRef", "artifactKind", "artifactRef", "expectedBundleRevision");
    private static final Set<String> MODEL_LIST_ARGUMENTS = Set.of("namespace");
    private static final Set<String> RENDER_ARGUMENTS = Set.of(
            "bundleRef",
            "artifactRef",
            "expectedBundleRevision",
            "parameters",
            "timezone",
            "locale");
    private static final AnalyticsFunctionAuthority INPUT_VALIDATION_AUTHORITY =
            new AnalyticsFunctionAuthority("fap-adapter", "input-validation");

    private final AnalyticsFunctionClient client;
    private final FapAnalyticsAuthorityResolver authorityResolver;
    private final FapAnalyticsSemanticRequestMapper semanticRequests;

    public FapAnalyticsFunctionAdapter(
            AnalyticsFunctionClient client,
            FapAnalyticsAuthorityResolver authorityResolver) {
        this.client = Objects.requireNonNull(client, "client");
        this.authorityResolver = Objects.requireNonNull(
                authorityResolver, "authorityResolver");
        this.semanticRequests = new FapAnalyticsSemanticRequestMapper(
                authorityResolver);
    }

    public FapAnalyticsFunctionOutcome invoke(
            FapAnalyticsFunctionInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        if (!FapAnalyticsContract.SERVICE_PROVIDER_CONTRACT_VERSION.equals(
                invocation.serviceProviderContractVersion())) {
            return failure(
                    invocation,
                    FapAnalyticsErrorCodes.CONTRACT_UNSUPPORTED,
                    "FAP service-provider contract version is unsupported",
                    false,
                    422);
        }

        String operation = FapAnalyticsFunctionRefs.operation(
                invocation.functionRef());
        if (operation == null) {
            return failure(
                    invocation,
                    FapAnalyticsErrorCodes.FUNCTION_UNKNOWN,
                    "FAP Analytics functionRef is not registered",
                    false,
                    404);
        }

        try {
            return switch (operation) {
                case AnalyticsFunctionOperations.CAPABILITIES -> {
                    requireArguments(invocation.arguments(), EMPTY_ARGUMENTS);
                    yield complete(
                            invocation,
                            operation,
                            client.capabilities(context(invocation)),
                            FapAnalyticsResults::capabilities);
                }
                case AnalyticsFunctionOperations.BUNDLES_LIST -> {
                    requireArguments(invocation.arguments(), EMPTY_ARGUMENTS);
                    yield complete(
                            invocation,
                            operation,
                            client.listBundles(context(invocation)),
                            FapAnalyticsResults::bundleList);
                }
                case AnalyticsFunctionOperations.BUNDLES_VALIDATE -> complete(
                        invocation,
                        operation,
                        client.validateBundle(bundleRequest(invocation)),
                        FapAnalyticsResults::bundleDescription);
                case AnalyticsFunctionOperations.BUNDLES_DESCRIBE -> complete(
                        invocation,
                        operation,
                        client.describeBundle(bundleRequest(invocation)),
                        FapAnalyticsResults::bundleDescription);
                case AnalyticsFunctionOperations.ARTIFACTS_DESCRIBE -> complete(
                        invocation,
                        operation,
                        client.describeArtifact(artifactRequest(invocation)),
                        FapAnalyticsResults::artifactDescription);
                case AnalyticsFunctionOperations.MODEL_DEPENDENCIES_LIST -> complete(
                        invocation,
                        operation,
                        client.listModelDependencies(modelListRequest(invocation)),
                        FapAnalyticsResults::modelDependencyList);
                case AnalyticsFunctionOperations.SEMANTIC_MODELS_DESCRIBE -> complete(
                        invocation,
                        operation,
                        client.describeSemanticModel(semanticRequests.model(
                                invocation, operation)),
                        FapAnalyticsResults::semanticModel);
                case AnalyticsFunctionOperations.SEMANTIC_QUERIES_EXECUTE -> complete(
                        invocation,
                        operation,
                        client.executeSemanticQuery(semanticRequests.query(
                                invocation, operation)),
                        FapAnalyticsResults::semanticQuery);
                case AnalyticsFunctionOperations.REPORTS_PREVIEW -> complete(
                        invocation,
                        operation,
                        client.previewReport(renderRequest(invocation, operation)),
                        FapAnalyticsResults::render);
                case AnalyticsFunctionOperations.DASHBOARDS_PREVIEW -> complete(
                        invocation,
                        operation,
                        client.previewDashboard(renderRequest(invocation, operation)),
                        FapAnalyticsResults::render);
                case AnalyticsFunctionOperations.DASHBOARDS_RENDER -> complete(
                        invocation,
                        operation,
                        client.renderDashboard(renderRequest(invocation, operation)),
                        FapAnalyticsResults::render);
                default -> failure(
                        invocation,
                        FapAnalyticsErrorCodes.FUNCTION_UNKNOWN,
                        "FAP Analytics operation is not registered",
                        false,
                        404);
            };
        } catch (ArgumentsInvalid invalid) {
            return failure(
                    invocation,
                    FapAnalyticsErrorCodes.ARGUMENTS_INVALID,
                    "FAP Analytics function arguments are invalid",
                    false,
                    422);
        } catch (FapAnalyticsSemanticRequestMapper.ArgumentsInvalid invalid) {
            return failure(
                    invocation,
                    FapAnalyticsErrorCodes.ARGUMENTS_INVALID,
                    "FAP Analytics function arguments are invalid",
                    false,
                    422);
        } catch (AuthorityUnavailable unavailable) {
            return failure(
                    invocation,
                    FapAnalyticsErrorCodes.AUTHORITY_UNAVAILABLE,
                    "Analytics data authority is unavailable for the FAP Subject",
                    false,
                    403);
        } catch (FapAnalyticsSemanticRequestMapper.AuthorityUnavailable unavailable) {
            return failure(
                    invocation,
                    FapAnalyticsErrorCodes.AUTHORITY_UNAVAILABLE,
                    "Analytics data authority is unavailable for the FAP Subject",
                    false,
                    403);
        } catch (RuntimeException unexpected) {
            return failure(
                    invocation,
                    FapAnalyticsErrorCodes.ADAPTER_INTERNAL,
                    "FAP Analytics adapter could not complete the invocation",
                    false,
                    500);
        }
    }

    private AnalyticsBundleFunctionRequest bundleRequest(
            FapAnalyticsFunctionInvocation invocation) {
        try {
            requireArguments(invocation.arguments(), BUNDLE_ARGUMENTS);
            return new AnalyticsBundleFunctionRequest(
                    requiredString(invocation.arguments(), "bundleRef"),
                    optionalString(invocation.arguments(), "expectedBundleRevision"),
                    context(invocation));
        } catch (ArgumentsInvalid invalid) {
            throw invalid;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new ArgumentsInvalid();
        }
    }

    private AnalyticsModelDependencyListRequest modelListRequest(
            FapAnalyticsFunctionInvocation invocation) {
        try {
            requireArguments(invocation.arguments(), MODEL_LIST_ARGUMENTS);
            return new AnalyticsModelDependencyListRequest(
                    requiredString(invocation.arguments(), "namespace"),
                    "qm",
                    context(invocation));
        } catch (ArgumentsInvalid invalid) {
            throw invalid;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new ArgumentsInvalid();
        }
    }

    private AnalyticsRenderFunctionRequest renderRequest(
            FapAnalyticsFunctionInvocation invocation,
            String operation) {
        String bundleRef;
        String artifactRef;
        String expectedBundleRevision;
        Map<String, Object> parameters;
        String timezone;
        String locale;
        AnalyticsRenderFunctionRequest validated;
        try {
            requireArguments(invocation.arguments(), RENDER_ARGUMENTS);
            bundleRef = requiredString(invocation.arguments(), "bundleRef");
            artifactRef = requiredString(invocation.arguments(), "artifactRef");
            expectedBundleRevision = requiredString(
                    invocation.arguments(), "expectedBundleRevision");
            parameters = parameters(invocation.arguments());
            timezone = requiredString(invocation.arguments(), "timezone");
            locale = requiredString(invocation.arguments(), "locale");
            validated = new AnalyticsRenderFunctionRequest(
                    bundleRef,
                    artifactRef,
                    expectedBundleRevision,
                    parameters,
                    timezone,
                    locale,
                    INPUT_VALIDATION_AUTHORITY,
                    context(invocation));
        } catch (ArgumentsInvalid invalid) {
            throw invalid;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new ArgumentsInvalid();
        }

        AnalyticsFunctionAuthority authority;
        try {
            authority = authorityResolver.resolve(invocation.caller(), operation);
            if (authority == null) {
                throw new AuthorityUnavailable();
            }
        } catch (AuthorityUnavailable unavailable) {
            throw unavailable;
        } catch (RuntimeException unavailable) {
            throw new AuthorityUnavailable();
        }

        return new AnalyticsRenderFunctionRequest(
                validated.bundleRef(),
                validated.artifactRef(),
                validated.expectedBundleRevision(),
                validated.parameters(),
                validated.timezone(),
                validated.locale(),
                authority,
                validated.context());
    }

    private AnalyticsArtifactFunctionRequest artifactRequest(
            FapAnalyticsFunctionInvocation invocation) {
        try {
            requireArguments(invocation.arguments(), ARTIFACT_ARGUMENTS);
            return new AnalyticsArtifactFunctionRequest(
                    requiredString(invocation.arguments(), "bundleRef"),
                    requiredString(invocation.arguments(), "artifactKind"),
                    requiredString(invocation.arguments(), "artifactRef"),
                    requiredString(invocation.arguments(), "expectedBundleRevision"),
                    context(invocation));
        } catch (ArgumentsInvalid invalid) {
            throw invalid;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new ArgumentsInvalid();
        }
    }

    private <T> FapAnalyticsFunctionOutcome complete(
            FapAnalyticsFunctionInvocation invocation,
            String operation,
            AnalyticsFunctionEnvelope<T> envelope,
            Function<T, Map<String, Object>> dataMapper) {
        if (envelope == null
                || !AnalyticsFunctionContract.ENGINE.equals(envelope.engine())
                || !AnalyticsFunctionContract.VERSION.equals(
                        envelope.functionContractVersion())
                || !invocation.requestId().equals(envelope.context().requestId())
                || !invocation.functionInvocationId().equals(
                        envelope.context().traceId())) {
            return failure(
                    invocation,
                    FapAnalyticsErrorCodes.PROTOCOL_ERROR,
                    "Analytics Function response correlation or version is invalid",
                    false,
                    502);
        }
        if (!envelope.success()) {
            return analyticsFailure(invocation, envelope);
        }
        try {
            Map<String, Object> data = dataMapper.apply(envelope.data());
            Map<String, Object> result = FapAnalyticsResults.result(
                    operation, envelope, data);
            return FapAnalyticsFunctionOutcome.Success.create(
                    invocation.requestId(),
                    invocation.functionInvocationId(),
                    result);
        } catch (RuntimeException invalid) {
            return failure(
                    invocation,
                    FapAnalyticsErrorCodes.PROTOCOL_ERROR,
                    "Analytics Function result cannot be projected to FAP",
                    false,
                    502);
        }
    }

    private FapAnalyticsFunctionOutcome analyticsFailure(
            FapAnalyticsFunctionInvocation invocation,
            AnalyticsFunctionEnvelope<?> envelope) {
        String code = envelope.error().code();
        try {
            FapAnalyticsValues.safeErrorCode(code);
        } catch (IllegalArgumentException unsafe) {
            return failure(
                    invocation,
                    FapAnalyticsErrorCodes.PROTOCOL_ERROR,
                    "Analytics Function returned an unsafe error code",
                    false,
                    502);
        }
        return failure(
                invocation,
                code,
                safeMessage(envelope.error().message()),
                envelope.error().retryable(),
                analyticsStatus(code));
    }

    private static int analyticsStatus(String code) {
        return switch (code) {
            case AnalyticsFunctionErrorCodes.INVALID_REQUEST -> 400;
            case AnalyticsFunctionErrorCodes.BUNDLE_NOT_REGISTERED,
                    AnalyticsFunctionErrorCodes.REPORT_NOT_FOUND,
                    AnalyticsFunctionErrorCodes.DASHBOARD_NOT_FOUND,
                    AnalyticsFunctionErrorCodes.QUERY_NOT_FOUND,
                    AnalyticsFunctionErrorCodes.MODEL_DEPENDENCY_NOT_FOUND -> 404;
            case AnalyticsFunctionErrorCodes.BUNDLE_REVISION_CONFLICT,
                    AnalyticsFunctionErrorCodes.BUNDLE_DEPENDENCY_STALE,
                    AnalyticsFunctionErrorCodes.MODEL_REVISION_CONFLICT -> 409;
            case AnalyticsFunctionErrorCodes.BUNDLE_IMMUTABLE -> 403;
            case AnalyticsFunctionErrorCodes.BUNDLE_INVALID,
                    AnalyticsFunctionErrorCodes.BUNDLE_IDENTITY_MISMATCH,
                    AnalyticsFunctionErrorCodes.BUNDLE_DIGEST_MISMATCH,
                    AnalyticsFunctionErrorCodes.BUNDLE_UNSAFE_PATH,
                    AnalyticsFunctionErrorCodes.BUNDLE_UNSUPPORTED_RESOURCE_PATH,
                    AnalyticsFunctionErrorCodes.SEMANTIC_QUERY_INVALID -> 422;
            case AnalyticsFunctionErrorCodes.BUNDLE_UNAVAILABLE,
                    AnalyticsFunctionErrorCodes.BUNDLE_RECOVERY_FAILED,
                    AnalyticsFunctionErrorCodes.RENDER_UNAVAILABLE,
                    AnalyticsFunctionErrorCodes.SEMANTIC_QUERY_UNAVAILABLE,
                    AnalyticsFunctionErrorCodes.CLIENT_TRANSPORT_ERROR -> 503;
            case AnalyticsFunctionErrorCodes.CLIENT_PROTOCOL_ERROR -> 502;
            default -> 500;
        };
    }

    private static String safeMessage(String value) {
        String message = value == null ? "Analytics Function failed" : value.strip();
        if (message.isEmpty()) {
            return "Analytics Function failed";
        }
        if (message.length() <= 2_000) {
            return message;
        }
        String bounded = message.substring(0, 2_000).stripTrailing();
        return bounded.isEmpty() ? "Analytics Function failed" : bounded;
    }

    private static FapAnalyticsFunctionOutcome.Failure failure(
            FapAnalyticsFunctionInvocation invocation,
            String code,
            String message,
            boolean retryable,
            int status) {
        return new FapAnalyticsFunctionOutcome.Failure(
                invocation.requestId(),
                invocation.functionInvocationId(),
                code,
                message,
                retryable,
                status);
    }

    private static AnalyticsFunctionRequestContext context(
            FapAnalyticsFunctionInvocation invocation) {
        return new AnalyticsFunctionRequestContext(
                invocation.requestId(), invocation.functionInvocationId());
    }

    private static void requireArguments(
            Map<String, Object> arguments,
            Set<String> allowed) {
        if (!allowed.containsAll(arguments.keySet())) {
            throw new ArgumentsInvalid();
        }
    }

    private static String requiredString(
            Map<String, Object> arguments,
            String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String text)) {
            throw new ArgumentsInvalid();
        }
        return text;
    }

    private static String optionalString(
            Map<String, Object> arguments,
            String name) {
        Object value = arguments.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new ArgumentsInvalid();
        }
        return text;
    }

    private static Map<String, Object> parameters(Map<String, Object> arguments) {
        Object value = arguments.get("parameters");
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new ArgumentsInvalid();
        }
        try {
            return AnalyticsFunctionJsonValues.normalizeObject("parameters", map);
        } catch (IllegalArgumentException invalid) {
            throw new ArgumentsInvalid();
        }
    }

    private static final class ArgumentsInvalid extends RuntimeException {
    }

    private static final class AuthorityUnavailable extends RuntimeException {
    }
}
