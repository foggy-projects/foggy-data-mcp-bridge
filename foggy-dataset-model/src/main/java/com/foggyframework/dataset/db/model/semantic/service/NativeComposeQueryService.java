package com.foggyframework.dataset.db.model.semantic.service;

import com.foggyframework.dataset.db.model.semantic.port.ComposeCaller;
import com.foggyframework.dataset.db.model.semantic.port.ComposeExecutionException;
import com.foggyframework.dataset.db.model.semantic.port.ComposeExecutionPort;
import com.foggyframework.dataset.db.model.semantic.port.ComposeExecutionRequest;
import com.foggyframework.dataset.db.model.semantic.port.ComposeExecutionResult;
import com.foggyframework.dataset.db.model.semantic.port.ComposeOperation;
import lombok.extern.slf4j.Slf4j;
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

    private final ComposeExecutionPort composeExecutionPort;

    public NativeComposeQueryService(ComposeExecutionPort composeExecutionPort) {
        this.composeExecutionPort = composeExecutionPort;
    }

    public Map<String, Object> execute(Map<String, Object> request, String namespace,
                                       String authorization, Map<String, String> headers) {
        String script = request != null ? stringValue(request.get("script")) : null;
        if (script == null || script.isBlank()) {
            return errorPayload("missing-script", "internal",
                    "parameter 'script' is required and must be non-blank", null);
        }
        try {
            boolean previewMode = boolValue(request.get("previewMode"));
            ComposeExecutionResult result = composeExecutionPort.execute(new ComposeExecutionRequest(
                    previewMode ? ComposeOperation.PREVIEW : ComposeOperation.EXECUTE,
                    script,
                    namespace,
                    firstNonBlank(header(headers, "X-Trace-Id"), stringValue(request.get("traceId"))),
                    params(request),
                    caller(request, authorization, headers),
                    stringValue(request.get("dialect"))));
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
        } catch (ComposeExecutionException e) {
            if (e.kind() == ComposeExecutionException.Kind.AUTHORITY) {
                log.warn("native compose permission-resolve error: {}", e.getMessage());
                return errorPayload(e.code(), "permission-resolve", e.getMessage(), e.model());
            }
            if (e.kind() == ComposeExecutionException.Kind.SANDBOX) {
                log.warn("native compose sandbox violation: {}", e.getMessage());
                return errorPayload(e.code(), "compile", e.getMessage(), null);
            }
            if (e.kind() == ComposeExecutionException.Kind.SCHEMA) {
                return errorPayload(e.code(), "schema-derive", e.getMessage(), e.field());
            }
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

    private static ComposeCaller caller(
            Map<String, Object> request,
            String authorization,
            Map<String, String> headers
    ) {
        return new ComposeCaller(
                firstNonBlank(header(headers, "X-User-Id"), stringValue(request.get("userId")), "native-rest"),
                firstNonBlank(header(headers, "X-Tenant-Id"), stringValue(request.get("tenantId"))),
                parseRoles(firstNonBlank(header(headers, "X-Roles"), stringValue(request.get("roles")))),
                firstNonBlank(header(headers, "X-Dept-Id"), stringValue(request.get("deptId"))),
                authorization,
                firstNonBlank(header(headers, "X-Policy-Snapshot-Id"),
                        stringValue(request.get("policySnapshotId"))));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> params(Map<String, Object> request) {
        if (request.get("params") instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
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
