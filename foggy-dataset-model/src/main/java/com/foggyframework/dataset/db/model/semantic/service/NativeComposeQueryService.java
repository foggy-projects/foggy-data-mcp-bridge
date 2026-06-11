package com.foggyframework.dataset.db.model.semantic.service;

import com.foggyframework.dataset.db.model.engine.compose.compilation.ComposeCompileException;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeScriptService;
import com.foggyframework.dataset.db.model.engine.compose.sandbox.ComposeSandboxViolationException;
import com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaException;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolutionException;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native REST execution service for Compose Script without MCP ToolExecutionContext.
 */
@Slf4j
@Service
public class NativeComposeQueryService {

    private final SemanticQueryServiceV3 semanticQueryServiceV3;
    private final AuthorityResolver authorityResolver;
    private final String defaultDialect;

    public NativeComposeQueryService(
            SemanticQueryServiceV3 semanticQueryServiceV3,
            ObjectProvider<AuthorityResolver> authorityResolvers,
            @Value("${foggy.compose.dialect:mysql}") String defaultDialect) {
        this.semanticQueryServiceV3 = semanticQueryServiceV3;
        this.authorityResolver = authorityResolvers.orderedStream()
                .findFirst()
                .orElse(NativeComposeQueryService::allowAll);
        this.defaultDialect = defaultDialect != null ? defaultDialect : "mysql";
    }

    public Map<String, Object> execute(Map<String, Object> request, String namespace,
                                       String authorization, Map<String, String> headers) {
        String script = request != null ? stringValue(request.get("script")) : null;
        if (script == null || script.isBlank()) {
            return errorPayload("missing-script", "internal",
                    "parameter 'script' is required and must be non-blank", null);
        }
        try {
            ComposeQueryContext context = buildContext(request, namespace, authorization, headers);
            boolean previewMode = boolValue(request.get("previewMode"));
            ComposeScriptService.ComposeScriptResult result = previewMode
                    ? ComposeScriptService.preview(script, context, semanticQueryServiceV3, defaultDialect)
                    : ComposeScriptService.execute(script, context, semanticQueryServiceV3, defaultDialect);
            Object value = withEmptyResultSemantic(result.value());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("value", value);
            data.put("sql", result.sql() != null ? result.sql() : "");
            data.put("params", result.params() != null ? result.params() : List.of());
            data.put("warnings", result.warnings() != null ? result.warnings() : List.of());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("data", data);
            return response;
        } catch (AuthorityResolutionException e) {
            log.warn("native compose permission-resolve error: {}", e.getMessage());
            return errorPayload(e.code(), "permission-resolve", e.getMessage(), e.modelInvolved());
        } catch (ComposeSchemaException e) {
            return errorPayload(e.code(), "schema-derive", e.getMessage(), e.offendingField());
        } catch (ComposeCompileException e) {
            return errorPayload(e.code(), "compile", e.getMessage(), null);
        } catch (ComposeSandboxViolationException e) {
            log.warn("native compose sandbox violation: {}", e.getMessage());
            return errorPayload(e.code(), "compile", e.getMessage(), null);
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.startsWith("Plan execution failed at execute phase:")) {
                return errorPayload("execute-phase-error", "execute", msg, null);
            }
            if (msg.contains("requires an ambient ComposeRuntimeBundle")
                    || msg.contains("semanticService unbound")) {
                return errorPayload("host-misconfig", "internal", msg, null);
            }
            return errorPayload("internal-error", "internal", msg, null);
        } catch (Exception e) {
            log.error("native compose unexpected error", e);
            return errorPayload("internal-error", "internal",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), null);
        }
    }

    @SuppressWarnings("unchecked")
    private ComposeQueryContext buildContext(Map<String, Object> request, String namespace,
                                             String authorization, Map<String, String> headers) {
        Principal principal = Principal.builder()
                .userId(firstNonBlank(header(headers, "X-User-Id"), stringValue(request.get("userId")), "native-rest"))
                .tenantId(firstNonBlank(header(headers, "X-Tenant-Id"), stringValue(request.get("tenantId"))))
                .roles(parseRoles(firstNonBlank(header(headers, "X-Roles"), stringValue(request.get("roles")))))
                .deptId(firstNonBlank(header(headers, "X-Dept-Id"), stringValue(request.get("deptId"))))
                .authorizationHint(authorization)
                .policySnapshotId(firstNonBlank(header(headers, "X-Policy-Snapshot-Id"),
                        stringValue(request.get("policySnapshotId"))))
                .build();

        Map<String, Object> params = request.get("params") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : null;
        return ComposeQueryContext.builder()
                .principal(principal)
                .namespace(namespace)
                .traceId(firstNonBlank(header(headers, "X-Trace-Id"), stringValue(request.get("traceId"))))
                .params(params)
                .authorityResolver(authorityResolver)
                .build();
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

    @SuppressWarnings("unchecked")
    private static Object withEmptyResultSemantic(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return value;
        }
        Map<String, Object> map = raw instanceof LinkedHashMap<?, ?>
                ? (Map<String, Object>) raw
                : new LinkedHashMap<>((Map<String, Object>) raw);
        if (map.containsKey("semantic")) {
            return map;
        }
        Object plans = map.get("plans");
        boolean emptyPlans = plans instanceof List<?> list && list.isEmpty();
        if (!emptyPlans) {
            return map;
        }
        Map<String, Object> semantic = new LinkedHashMap<>();
        semantic.put("emptyResult", true);
        semantic.put("emptyReason", "NO_MATCHING_ROWS_AFTER_COMPOSE");
        semantic.put("shouldAnswerDirectly", true);
        map.put("semantic", semantic);
        return map;
    }

    private static Map<String, Object> errorPayload(String code, String phase, String message, String model) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("error_code", code);
        data.put("phase", phase);
        data.put("message", message);
        if (model != null) {
            data.put("model", model);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "error");
        response.put("data", data);
        return response;
    }

    private static List<String> parseRoles(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String header(Map<String, String> headers, String name) {
        return headers != null ? headers.get(name) : null;
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

    private static boolean boolValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }
}
