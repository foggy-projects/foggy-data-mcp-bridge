package com.foggyframework.runtime.api.controller;

import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.config.DatasetRequestNamespaceResolver;
import com.foggyframework.dataset.db.model.engine.compose.compilation.ComposeCompileException;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeScriptService;
import com.foggyframework.dataset.db.model.engine.compose.sandbox.ComposeSandboxViolationException;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaException;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.ComposeRequest;
import com.foggyframework.runtime.api.dto.ComposeResponse;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.dto.RuntimeError;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/compose")
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeComposeController {

    private static final String ENGINE = "java";

    private final FoggyRuntimeApiProperties runtimeApiProperties;
    private final SemanticQueryServiceV3 semanticQueryServiceV3;
    private final AuthorityResolver authorityResolver;
    private final DatasetProperties datasetProperties;
    private final String defaultDialect;

    public RuntimeComposeController(
            FoggyRuntimeApiProperties runtimeApiProperties,
            SemanticQueryServiceV3 semanticQueryServiceV3,
            ObjectProvider<AuthorityResolver> authorityResolvers,
            ObjectProvider<DatasetProperties> datasetPropertiesProvider,
            @Value("${foggy.compose.dialect:mysql}") String defaultDialect
    ) {
        this.runtimeApiProperties = runtimeApiProperties;
        this.semanticQueryServiceV3 = semanticQueryServiceV3;
        this.authorityResolver = authorityResolvers.orderedStream()
                .findFirst()
                .orElse(RuntimeComposeController::allowAll);
        this.datasetProperties = datasetPropertiesProvider.getIfAvailable();
        this.defaultDialect = defaultDialect != null ? defaultDialect : "mysql";
    }

    @PostMapping("/validate")
    public RuntimeEnvelope<ComposeResponse> validate(
            @RequestBody(required = false) ComposeRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestHeader Map<String, String> headers
    ) {
        return run(ComposeScriptService.Mode.VALIDATE, "compose.validate",
                request, authorization, namespace, headers);
    }

    @PostMapping("/preview")
    public RuntimeEnvelope<ComposeResponse> preview(
            @RequestBody(required = false) ComposeRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestHeader Map<String, String> headers
    ) {
        return run(ComposeScriptService.Mode.PREVIEW, "compose.preview",
                request, authorization, namespace, headers);
    }

    @PostMapping("/execute")
    public RuntimeEnvelope<ComposeResponse> execute(
            @RequestBody(required = false) ComposeRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestHeader Map<String, String> headers
    ) {
        return run(ComposeScriptService.Mode.EXECUTE, "compose.execute",
                request, authorization, namespace, headers);
    }

    private RuntimeEnvelope<ComposeResponse> run(
            ComposeScriptService.Mode mode,
            String phase,
            ComposeRequest request,
            String authorization,
            String namespace,
            Map<String, String> headers
    ) {
        if (request == null || request.script() == null || request.script().isBlank()) {
            return fail("COMPOSE_SCRIPT_INVALID", phase,
                    "parameter 'script' is required and must be non-blank",
                    null, "Provide an inline compose script.", true);
        }

        try {
            ComposeScriptService.ComposeScriptResult result = ComposeScriptService.run(
                    ComposeScriptService.ComposeScriptRequest.builder()
                            .mode(mode)
                            .script(request.script())
                            .ctx(buildContext(request, namespace, authorization, headers))
                            .semanticService(semanticQueryServiceV3)
                            .dialect(defaultDialect)
                            .build());
            return RuntimeEnvelope.ok(ENGINE, runtimeApiProperties.getRuntimeApiVersion(), toResponse(result));
        } catch (ComposeSandboxViolationException e) {
            return fail("COMPOSE_SANDBOX_VIOLATION", phase, e.getMessage(),
                    null, "Remove forbidden script host access and retry.", false);
        } catch (ComposeSchemaException e) {
            return fail(mapScriptErrorCode(phase), phase, e.getMessage(),
                    e.offendingField(), "Inspect compose fields/schema and retry.", true);
        } catch (ComposeCompileException e) {
            return fail(mapScriptErrorCode(phase), phase, e.getMessage(),
                    null, "Fix compose script or model metadata and retry.", true);
        } catch (RuntimeException e) {
            return fail(mapRuntimeErrorCode(phase), phase, e.getMessage(),
                    null, "Inspect diagnostics and runtime logs, then retry.", false);
        }
    }

    private ComposeResponse toResponse(ComposeScriptService.ComposeScriptResult result) {
        return new ComposeResponse(
                result.valid(),
                "compose",
                result.mode().name().toLowerCase(),
                result.value(),
                result.sql(),
                result.params() != null ? result.params() : List.of(),
                result.warnings() != null ? result.warnings() : List.of()
        );
    }

    private ComposeQueryContext buildContext(
            ComposeRequest request,
            String headerNamespace,
            String authorization,
            Map<String, String> headers
    ) {
        Map<String, String> safeHeaders = headers != null ? headers : Map.of();
        Principal principal = Principal.builder()
                .userId(firstNonBlank(header(safeHeaders, "X-User-Id"), "runtime-api"))
                .tenantId(header(safeHeaders, "X-Tenant-Id"))
                .roles(parseRoles(header(safeHeaders, "X-Roles")))
                .deptId(header(safeHeaders, "X-Dept-Id"))
                .authorizationHint(authorization)
                .policySnapshotId(header(safeHeaders, "X-Policy-Snapshot-Id"))
                .build();
        return ComposeQueryContext.builder()
                .principal(principal)
                .namespace(DatasetRequestNamespaceResolver.resolve(
                        datasetProperties, headerNamespace, request.namespace()))
                .traceId(firstNonBlank(header(safeHeaders, "X-Trace-Id"), request.traceId()))
                .params(request.params())
                .authorityResolver(authorityResolver)
                .build();
    }

    private RuntimeEnvelope<ComposeResponse> fail(
            String code,
            String phase,
            String message,
            String field,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        RuntimeError error = new RuntimeError(
                code,
                phase,
                message,
                null,
                field,
                null,
                suggestedNextAction,
                safeToAutoRepair
        );
        return RuntimeEnvelope.fail(ENGINE, runtimeApiProperties.getRuntimeApiVersion(), error, RuntimeDiagnostics.empty());
    }

    private static String mapScriptErrorCode(String phase) {
        if ("compose.validate".equals(phase)) {
            return "COMPOSE_SCRIPT_INVALID";
        }
        if ("compose.preview".equals(phase)) {
            return "COMPOSE_COMPILE_FAILED";
        }
        return "COMPOSE_EXECUTE_FAILED";
    }

    private static String mapRuntimeErrorCode(String phase) {
        if ("compose.execute".equals(phase)) {
            return "COMPOSE_EXECUTE_FAILED";
        }
        if ("compose.preview".equals(phase)) {
            return "COMPOSE_COMPILE_FAILED";
        }
        return "COMPOSE_SCRIPT_INVALID";
    }

    private static AuthorityResolution allowAll(com.foggyframework.dataset.db.model.engine.compose.security.AuthorityRequest request) {
        Map<String, ModelBinding> bindings = new LinkedHashMap<>();
        for (String model : request.modelNames()) {
            bindings.put(model, ModelBinding.builder()
                    .deniedColumns(List.of())
                    .systemSlice(List.of())
                    .build());
        }
        return AuthorityResolution.builder().bindings(bindings).build();
    }

    private static List<String> parseRoles(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers.containsKey(name)) {
            return headers.get(name);
        }
        return headers.get(name.toLowerCase());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
