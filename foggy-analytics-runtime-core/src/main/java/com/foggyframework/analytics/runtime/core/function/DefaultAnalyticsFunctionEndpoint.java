package com.foggyframework.analytics.runtime.core.function;

import com.foggyframework.analytics.definition.api.AnalyticsArtifactKind;
import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.function.contract.AnalyticsArtifactDescription;
import com.foggyframework.analytics.function.contract.AnalyticsArtifactFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsComposeFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsComposeResult;
import com.foggyframework.analytics.function.contract.AnalyticsBundleDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleList;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionCapabilities;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionError;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionErrorCodes;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionOperations;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyDescription;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyList;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyListRequest;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyResolutionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelResult;
import com.foggyframework.analytics.function.contract.AnalyticsRenderFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelDescription;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryResult;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityBinding;
import com.foggyframework.analytics.runtime.core.render.AnalyticsDashboardRenderRequest;
import com.foggyframework.analytics.runtime.core.render.AnalyticsRenderRequestContext;
import com.foggyframework.analytics.runtime.core.render.AnalyticsReportPreviewRequest;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Shared synchronous endpoint used by embedded and HTTP Analytics transports. */
public final class DefaultAnalyticsFunctionEndpoint
        implements AnalyticsFunctionEndpoint {

    private final boolean enabled;
    private final String securityMode;
    private final int maxRows;
    private final AnalyticsBundleFunctionOperations bundleOperations;
    private final Supplier<AnalyticsModelDependencyOperations> modelDependencyOperations;
    private final Supplier<AnalyticsFunctionRenderOperations> renderOperations;
    private final Supplier<AnalyticsSemanticFunctionOperations> semanticOperations;
    private final Supplier<AnalyticsAdvancedSemanticFunctionOperations>
            advancedSemanticOperations;
    private final AnalyticsFunctionResponseFactory responses;
    private final AnalyticsFunctionFailureMapper failures;

    public DefaultAnalyticsFunctionEndpoint(
            boolean enabled,
            String securityMode,
            int maxRows,
            AnalyticsBundleFunctionOperations bundleOperations,
            Supplier<AnalyticsFunctionRenderOperations> renderOperations,
            AnalyticsFunctionResponseFactory responses,
            AnalyticsFunctionFailureMapper failures) {
        this(
                enabled,
                securityMode,
                maxRows,
                bundleOperations,
                () -> null,
                () -> null,
                () -> null,
                renderOperations,
                responses,
                failures);
    }

    public DefaultAnalyticsFunctionEndpoint(
            boolean enabled,
            String securityMode,
            int maxRows,
            AnalyticsBundleFunctionOperations bundleOperations,
            Supplier<AnalyticsModelDependencyOperations> modelDependencyOperations,
            Supplier<AnalyticsFunctionRenderOperations> renderOperations,
            AnalyticsFunctionResponseFactory responses,
            AnalyticsFunctionFailureMapper failures) {
        this(
                enabled,
                securityMode,
                maxRows,
                bundleOperations,
                modelDependencyOperations,
                () -> null,
                () -> null,
                renderOperations,
                responses,
                failures);
    }

    public DefaultAnalyticsFunctionEndpoint(
            boolean enabled,
            String securityMode,
            int maxRows,
            AnalyticsBundleFunctionOperations bundleOperations,
            Supplier<AnalyticsModelDependencyOperations> modelDependencyOperations,
            Supplier<AnalyticsSemanticFunctionOperations> semanticOperations,
            Supplier<AnalyticsFunctionRenderOperations> renderOperations,
            AnalyticsFunctionResponseFactory responses,
            AnalyticsFunctionFailureMapper failures) {
        this(
                enabled,
                securityMode,
                maxRows,
                bundleOperations,
                modelDependencyOperations,
                semanticOperations,
                () -> null,
                renderOperations,
                responses,
                failures);
    }

    public DefaultAnalyticsFunctionEndpoint(
            boolean enabled,
            String securityMode,
            int maxRows,
            AnalyticsBundleFunctionOperations bundleOperations,
            Supplier<AnalyticsModelDependencyOperations> modelDependencyOperations,
            Supplier<AnalyticsSemanticFunctionOperations> semanticOperations,
            Supplier<AnalyticsAdvancedSemanticFunctionOperations> advancedSemanticOperations,
            Supplier<AnalyticsFunctionRenderOperations> renderOperations,
            AnalyticsFunctionResponseFactory responses,
            AnalyticsFunctionFailureMapper failures) {
        this.enabled = enabled;
        this.securityMode = requireValue("securityMode", securityMode);
        if (maxRows <= 0) {
            throw new IllegalArgumentException("maxRows must be positive");
        }
        this.maxRows = maxRows;
        this.bundleOperations = Objects.requireNonNull(
                bundleOperations, "bundleOperations");
        this.modelDependencyOperations = Objects.requireNonNull(
                modelDependencyOperations, "modelDependencyOperations");
        this.semanticOperations = Objects.requireNonNull(
                semanticOperations, "semanticOperations");
        this.advancedSemanticOperations = Objects.requireNonNull(
                advancedSemanticOperations, "advancedSemanticOperations");
        this.renderOperations = Objects.requireNonNull(
                renderOperations, "renderOperations");
        this.responses = Objects.requireNonNull(responses, "responses");
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsFunctionCapabilities> capabilities(
            AnalyticsFunctionRequestContext requestedContext) {
        return execute(requestedContext, ignored -> capabilitiesData());
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsBundleList> listBundles(
            AnalyticsFunctionRequestContext requestedContext) {
        return execute(requestedContext, ignored -> bundleOperations.list());
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsBundleDescription> validateBundle(
            AnalyticsBundleFunctionRequest request) {
        Objects.requireNonNull(request, "request");
        return execute(request.context(), ignored -> bundleOperations.validate(
                request.bundleRef(), request.expectedBundleRevision()));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsBundleDescription> describeBundle(
            AnalyticsBundleFunctionRequest request) {
        Objects.requireNonNull(request, "request");
        return execute(request.context(), ignored -> bundleOperations.describe(
                request.bundleRef(), request.expectedBundleRevision()));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsArtifactDescription> describeArtifact(
            AnalyticsArtifactFunctionRequest request) {
        Objects.requireNonNull(request, "request");
        return execute(request.context(), ignored -> bundleOperations.describeArtifact(
                request.bundleRef(),
                request.artifactKind(),
                request.artifactRef(),
                request.expectedBundleRevision()));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsModelDependencyDescription>
            resolveModelDependency(AnalyticsModelDependencyResolutionRequest request) {
        Objects.requireNonNull(request, "request");
        return execute(request.context(), ignored -> {
            AnalyticsModelDependencyOperations operations =
                    modelDependencyOperations.get();
            if (operations == null) {
                throw new AnalyticsModelDependencyResolutionException(
                        AnalyticsModelDependencyResolutionException.Code.REVISION_UNAVAILABLE,
                        "Model dependency resolution is unavailable");
            }
            return operations.resolve(
                    request.namespace(), request.modelKind(), request.modelName());
        });
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsModelDependencyList> listModelDependencies(
            AnalyticsModelDependencyListRequest request) {
        Objects.requireNonNull(request, "request");
        return execute(request.context(), ignored -> {
            AnalyticsModelDependencyOperations operations =
                    modelDependencyOperations.get();
            if (operations == null) {
                throw new AnalyticsModelDependencyResolutionException(
                        AnalyticsModelDependencyResolutionException.Code.REVISION_UNAVAILABLE,
                        "Model dependency listing is unavailable");
            }
            return operations.list(request.namespace(), request.modelKind());
        });
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsSemanticModelDescription> describeSemanticModel(
            AnalyticsSemanticModelFunctionRequest request) {
        Objects.requireNonNull(request, "request");
        return execute(request.context(), context -> requireSemanticOperations()
                .describeModel(request, context));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsSemanticQueryResult> executeSemanticQuery(
            AnalyticsSemanticQueryFunctionRequest request) {
        Objects.requireNonNull(request, "request");
        return execute(request.context(), context -> requireSemanticOperations()
                .executeQuery(request, context));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsQueryModelResult> runQueryModel(
            AnalyticsQueryModelFunctionRequest request) {
        Objects.requireNonNull(request, "request");
        return execute(request.context(), context -> requireAdvancedSemanticOperations()
                .runQueryModel(request, context));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsComposeResult> runCompose(
            AnalyticsComposeFunctionRequest request) {
        Objects.requireNonNull(request, "request");
        return execute(request.context(), context -> requireAdvancedSemanticOperations()
                .runCompose(request, context));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
            AnalyticsRenderFunctionRequest request) {
        Objects.requireNonNull(request, "request");
        return executeRender(request, (operations, context) ->
                operations.previewReport(new AnalyticsReportPreviewRequest(
                        new AnalyticsBundleRef(request.bundleRef()),
                        new AnalyticsBundleRevision(request.expectedBundleRevision()),
                        new AnalyticsArtifactRef(
                                AnalyticsArtifactKind.REPORT,
                                request.artifactRef()),
                        renderContext(request, context))));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewDashboard(
            AnalyticsRenderFunctionRequest request) {
        Objects.requireNonNull(request, "request");
        return executeRender(request, (operations, context) ->
                operations.previewDashboard(dashboardRequest(request, context)));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsRenderResult> renderDashboard(
            AnalyticsRenderFunctionRequest request) {
        Objects.requireNonNull(request, "request");
        return executeRender(request, (operations, context) ->
                operations.renderDashboard(dashboardRequest(request, context)));
    }

    private AnalyticsFunctionCapabilities capabilitiesData() {
        boolean renderAvailable = renderOperations.get() != null;
        boolean modelDependencyResolutionAvailable =
                modelDependencyOperations.get() != null;
        boolean semanticAvailable = semanticOperations.get() != null;
        boolean advancedSemanticAvailable = advancedSemanticOperations.get() != null;
        Map<String, String> operations = new LinkedHashMap<>();
        operations.put(AnalyticsFunctionOperations.CAPABILITIES, "supported");
        operations.put(AnalyticsFunctionOperations.BUNDLES_LIST, "supported");
        operations.put(AnalyticsFunctionOperations.BUNDLES_VALIDATE, "supported");
        operations.put(AnalyticsFunctionOperations.BUNDLES_DESCRIBE, "supported");
        operations.put(
                AnalyticsFunctionOperations.ARTIFACTS_DESCRIBE,
                status(bundleOperations.artifactInspectionAvailable()));
        operations.put(
                AnalyticsFunctionOperations.MODEL_DEPENDENCIES_RESOLVE,
                status(modelDependencyResolutionAvailable));
        operations.put(
                AnalyticsFunctionOperations.MODEL_DEPENDENCIES_LIST,
                status(modelDependencyResolutionAvailable));
        operations.put(
                AnalyticsFunctionOperations.SEMANTIC_MODELS_DESCRIBE,
                status(semanticAvailable));
        operations.put(
                AnalyticsFunctionOperations.SEMANTIC_QUERIES_EXECUTE,
                status(semanticAvailable));
        operations.put(
                AnalyticsFunctionOperations.QUERY_MODEL_RUN,
                status(advancedSemanticAvailable));
        operations.put(
                AnalyticsFunctionOperations.COMPOSE_RUN,
                status(advancedSemanticAvailable));
        operations.put(AnalyticsFunctionOperations.BUNDLES_PULL, "unsupported");
        operations.put(AnalyticsFunctionOperations.BUNDLES_SAVE, "unsupported");
        operations.put(
                AnalyticsFunctionOperations.REPORTS_PREVIEW,
                status(renderAvailable));
        operations.put(
                AnalyticsFunctionOperations.DASHBOARDS_PREVIEW,
                status(renderAvailable));
        operations.put(
                AnalyticsFunctionOperations.DASHBOARDS_RENDER,
                status(renderAvailable));

        List<String> warnings = new ArrayList<>();
        if (!renderAvailable) {
            warnings.add(
                    "Preview/render requires a host authority resolver composition.");
        }
        if (!modelDependencyResolutionAvailable) {
            warnings.add(
                    "Stable model dependency resolution is unavailable in this host composition.");
        }
        if (!semanticAvailable) {
            warnings.add(
                    "Direct semantic questions require a host authority resolver composition.");
        }
        if (!advancedSemanticAvailable) {
            warnings.add(
                    "Full semantic DSL and Compose require a host authority resolver composition.");
        }
        if (bundleOperations.configuredBundleCount() == 0) {
            warnings.add("No trusted Analytics Bundle registrations are configured.");
        }
        return new AnalyticsFunctionCapabilities(
                "analytics",
                responses.runtimeApiVersion(),
                responses.schemaVersion(),
                enabled,
                securityMode,
                operations,
                new AnalyticsFunctionCapabilities.Limits(
                        maxRows,
                        bundleOperations.configuredBundleCount()),
                warnings);
    }

    private AnalyticsFunctionEnvelope<AnalyticsRenderResult> executeRender(
            AnalyticsRenderFunctionRequest request,
            RenderInvocation invocation) {
        return execute(request.context(), context -> {
            AnalyticsFunctionRenderOperations operations = renderOperations.get();
            if (operations == null) {
                throw new RenderCompositionUnavailableException();
            }
            return AnalyticsRenderResultMapper.from(
                    invocation.invoke(operations, context));
        });
    }

    private <T> AnalyticsFunctionEnvelope<T> execute(
            AnalyticsFunctionRequestContext requestedContext,
            Function<AnalyticsFunctionContext, T> invocation) {
        AnalyticsFunctionContext context = responses.context(
                Objects.requireNonNull(requestedContext, "requestedContext"));
        try {
            return responses.ok(invocation.apply(context), context);
        } catch (RenderCompositionUnavailableException unavailable) {
            return responses.fail(new AnalyticsFunctionError(
                    AnalyticsFunctionErrorCodes.RENDER_UNAVAILABLE,
                    "composition",
                    "Analytics preview/render is unavailable in this host composition.",
                    false), context);
        } catch (SemanticCompositionUnavailableException unavailable) {
            return responses.fail(new AnalyticsFunctionError(
                    AnalyticsFunctionErrorCodes.SEMANTIC_QUERY_UNAVAILABLE,
                    "composition",
                    "Analytics semantic questions are unavailable in this host composition.",
                    false), context);
        } catch (RuntimeException failure) {
            return responses.fail(failures.map(failure), context);
        }
    }

    private static AnalyticsDashboardRenderRequest dashboardRequest(
            AnalyticsRenderFunctionRequest request,
            AnalyticsFunctionContext context) {
        return new AnalyticsDashboardRenderRequest(
                new AnalyticsBundleRef(request.bundleRef()),
                new AnalyticsBundleRevision(request.expectedBundleRevision()),
                new AnalyticsArtifactRef(
                        AnalyticsArtifactKind.DASHBOARD,
                        request.artifactRef()),
                renderContext(request, context));
    }

    private static AnalyticsRenderRequestContext renderContext(
            AnalyticsRenderFunctionRequest request,
            AnalyticsFunctionContext context) {
        try {
            Locale locale = Locale.forLanguageTag(request.locale());
            if (locale.getLanguage().isBlank()) {
                throw new IllegalArgumentException(
                        "locale must be a valid language tag");
            }
            return new AnalyticsRenderRequestContext(
                    request.parameters(),
                    ZoneId.of(request.timezone()),
                    locale,
                    new QueryAuthorityBinding(
                            request.authority().provider(),
                            request.authority().reference()),
                    context.requestId(),
                    context.traceId());
        } catch (DateTimeException invalidTimezone) {
            throw new IllegalArgumentException(
                    "timezone must be a valid ZoneId", invalidTimezone);
        }
    }

    private static String status(boolean available) {
        return available ? "supported" : "unavailable";
    }

    private AnalyticsSemanticFunctionOperations requireSemanticOperations() {
        AnalyticsSemanticFunctionOperations operations = semanticOperations.get();
        if (operations == null) {
            throw new SemanticCompositionUnavailableException();
        }
        return operations;
    }

    private AnalyticsAdvancedSemanticFunctionOperations requireAdvancedSemanticOperations() {
        AnalyticsAdvancedSemanticFunctionOperations operations =
                advancedSemanticOperations.get();
        if (operations == null) {
            throw new SemanticCompositionUnavailableException();
        }
        return operations;
    }

    private static String requireValue(String field, String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }

    @FunctionalInterface
    private interface RenderInvocation {
        com.foggyframework.analytics.definition.api.AnalyticsRenderModel invoke(
                AnalyticsFunctionRenderOperations operations,
                AnalyticsFunctionContext context);
    }

    private static final class RenderCompositionUnavailableException
            extends RuntimeException {
    }

    private static final class SemanticCompositionUnavailableException
            extends RuntimeException {
    }
}
