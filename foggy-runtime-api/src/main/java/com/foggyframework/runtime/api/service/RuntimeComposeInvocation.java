package com.foggyframework.runtime.api.service;

import com.foggyframework.runtime.api.dto.ComposeRequest;
import com.foggyframework.runtime.api.dto.FsscriptRequest;

import java.util.LinkedHashMap;
import java.util.Map;

public record RuntimeComposeInvocation(
        String script,
        Map<String, Object> params,
        Map<String, Object> options,
        String namespace,
        String traceId,
        String headerNamespace,
        String authorization,
        Map<String, String> headers
) {

    public static RuntimeComposeInvocation fromComposeRequest(
            ComposeRequest request,
            String headerNamespace,
            String authorization,
            Map<String, String> headers
    ) {
        return new RuntimeComposeInvocation(
                request != null ? request.script() : null,
                request != null ? request.params() : null,
                request != null ? request.options() : null,
                request != null ? request.namespace() : null,
                request != null ? request.traceId() : null,
                headerNamespace,
                authorization,
                headers
        );
    }

    public static RuntimeComposeInvocation fromFsscriptCteArgs(
            FsscriptRequest request,
            String headerNamespace,
            String authorization,
            Map<String, String> headers,
            Object[] args,
            String phase
    ) {
        CteRequestParts compose = cteRequest(args, phase);
        return new RuntimeComposeInvocation(
                compose.script(),
                compose.params(),
                mergeOptions(request != null ? request.options() : null, compose.options()),
                request != null ? request.namespace() : null,
                request != null ? request.traceId() : null,
                headerNamespace,
                authorization,
                headers
        );
    }

    private static CteRequestParts cteRequest(Object[] args, String phase) {
        if (args == null || args.length == 0 || args[0] == null) {
            return new CteRequestParts(null, Map.of(), Map.of());
        }
        Object arg = args[0];
        if (arg instanceof String script) {
            return new CteRequestParts(script, Map.of(), Map.of());
        }
        if (arg instanceof Map<?, ?> map) {
            return new CteRequestParts(
                    stringValue(map.get("script")),
                    objectMap(map.get("params")),
                    objectMap(map.get("options"))
            );
        }
        throw new RuntimeComposeException("COMPOSE_SCRIPT_INVALID", phase,
                "cte request must be a string script or object containing script",
                null, "Pass foggy.cte.*({ script: '...' }).", true);
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> {
                if (key != null) {
                    result.put(key.toString(), mapValue);
                }
            });
            return result;
        }
        return Map.of();
    }

    private static Map<String, Object> mergeOptions(
            Map<String, Object> outerOptions,
            Map<String, Object> composeOptions) {
        if ((outerOptions == null || outerOptions.isEmpty())
                && (composeOptions == null || composeOptions.isEmpty())) {
            return Map.of();
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        if (outerOptions != null) {
            merged.putAll(outerOptions);
        }
        if (composeOptions != null) {
            merged.putAll(composeOptions);
        }
        return merged;
    }

    private record CteRequestParts(
            String script,
            Map<String, Object> params,
            Map<String, Object> options) {
    }
}
