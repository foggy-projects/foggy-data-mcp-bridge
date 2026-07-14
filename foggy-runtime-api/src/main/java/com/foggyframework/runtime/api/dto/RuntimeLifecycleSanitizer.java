package com.foggyframework.runtime.api.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Shared defensive normalization for the public Runtime lifecycle envelope. */
public final class RuntimeLifecycleSanitizer {

    public static final int MAX_FAILED_TARGETS = 100;
    public static final int MAX_DIAGNOSTICS = 50;
    public static final int MAX_MESSAGE_LENGTH = 512;

    private static final int MAX_COMPOSITE_DEPTH = 5;
    private static final int MAX_COMPOSITE_WIDTH = 100;

    private static final Pattern JDBC = Pattern.compile(
            "(?i)jdbc:[^\\s,;]+(?:[;,][^\\s]+)*");
    private static final Pattern URI = Pattern.compile(
            "(?i)\\b[a-z][a-z0-9+.-]*://[^\\s,;]+(?:[;,][^\\s]+)*");
    private static final Pattern WINDOWS_PATH = Pattern.compile(
            "(?i)(?:[a-z]:\\\\|\\\\\\\\)[^\\r\\n,;]+(?:[\\\\/][^\\r\\n,;]+)*");
    private static final Pattern UNIX_PATH = Pattern.compile(
            "(?<![A-Za-z0-9_.-])/(?:[^\\s,;]+/)*[^\\s,;]*");
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|username|user|credential|secret|token|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+");
    private static final Pattern STACK_FRAME = Pattern.compile(
            "(?m)\\s+at\\s+[A-Za-z_$][^\\r\\n]+");

    private static final Comparator<DatasourceBindingGenerationSummary> BINDING_ORDER =
            Comparator.comparing(DatasourceBindingGenerationSummary::bindingKey)
                    .thenComparing(DatasourceBindingGenerationSummary::backendId)
                    .thenComparing(DatasourceBindingGenerationSummary::generation);

    private RuntimeLifecycleSanitizer() {
    }

    public static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public static String sanitizeMessage(String value) {
        String text = value == null || value.isBlank()
                ? "Lifecycle operation failed."
                : value.trim();
        if (text.contains("\n\tat ")
                || text.matches("(?s).*\\n\\s*at\\s+.*")) {
            return "Lifecycle operation failed.";
        }
        text = JDBC.matcher(text).replaceAll("<redacted-datasource>");
        text = URI.matcher(text).replaceAll("<redacted-datasource>");
        text = WINDOWS_PATH.matcher(text).replaceAll("<redacted-path>");
        text = UNIX_PATH.matcher(text).replaceAll("<redacted-path>");
        text = CREDENTIAL.matcher(text).replaceAll("$1=<redacted>");
        text = STACK_FRAME.matcher(text).replaceAll("");
        text = text.replace('\r', ' ').replace('\n', ' ')
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (text.length() > MAX_MESSAGE_LENGTH) {
            text = text.substring(0, MAX_MESSAGE_LENGTH);
        }
        return text.isBlank() ? "Lifecycle operation failed." : text;
    }

    public static RuntimeDiagnostics sanitizeDiagnostics(
            RuntimeDiagnostics diagnostics
    ) {
        if (diagnostics == null) {
            return RuntimeDiagnostics.empty();
        }
        List<String> warnings = diagnostics.warnings() == null
                ? List.of()
                : diagnostics.warnings().stream()
                .filter(java.util.Objects::nonNull)
                .limit(MAX_DIAGNOSTICS)
                .map(RuntimeLifecycleSanitizer::sanitizeMessage)
                .toList();
        Map<String, Object> attributes = sanitizeMap(
                diagnostics.attributes(), 0);
        // SQL and physical plans are not lifecycle failure evidence and may
        // contain reversible datasource or credential material.
        if (diagnostics.sql() == null
                && diagnostics.plan() == null
                && java.util.Objects.equals(diagnostics.warnings(), warnings)
                && java.util.Objects.equals(diagnostics.attributes(), attributes)) {
            return diagnostics;
        }
        return new RuntimeDiagnostics(null, null, warnings, attributes);
    }

    private static Map<String, Object> sanitizeMap(
            Map<?, ?> source,
            int depth
    ) {
        if (source == null || source.isEmpty()
                || depth >= MAX_COMPOSITE_DEPTH) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        List<? extends Map.Entry<?, ?>> entries = source.entrySet().stream()
                .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                .limit(MAX_COMPOSITE_WIDTH)
                .toList();
        for (Map.Entry<?, ?> entry : entries) {
            String baseKey = sanitizeMessage(String.valueOf(entry.getKey()));
            String key = baseKey;
            int collision = 2;
            while (result.containsKey(key)) {
                key = baseKey + "#" + collision++;
            }
            result.put(key, sanitizeValue(entry.getValue(), depth + 1));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Object sanitizeValue(Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof CharSequence text) {
            return sanitizeMessage(text.toString());
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeMap(map, depth);
        }
        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty() || depth >= MAX_COMPOSITE_DEPTH) {
                return List.of();
            }
            return collection.stream()
                    .limit(MAX_COMPOSITE_WIDTH)
                    .map(item -> sanitizeValue(item, depth + 1))
                    .toList();
        }
        return sanitizeMessage(value.toString());
    }

    public static String sanitizeTarget(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String target = value.trim();
        String lower = target.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:") || lower.contains("://")) {
            return "<redacted-datasource>";
        }
        if (target.startsWith("/")
                || target.startsWith("\\\\")
                || target.matches("(?i)^[a-z]:[\\\\/].*")) {
            return "<redacted-path>";
        }
        return target.length() > MAX_MESSAGE_LENGTH
                ? target.substring(0, MAX_MESSAGE_LENGTH)
                : target;
    }

    /**
     * Keeps safe logical identities byte-for-byte stable while removing any
     * physical datasource, path or credential material supplied by a custom
     * binding resolver.
     */
    public static String sanitizeOpaqueIdentity(String value, String field) {
        return sanitizeMessage(requireNonBlank(value, field));
    }

    public static List<DatasourceBindingGenerationSummary> sortedBindings(
            List<DatasourceBindingGenerationSummary> bindings
    ) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream()
                .map(binding -> {
                    if (binding == null) {
                        throw new IllegalArgumentException(
                                "affected binding must not be null");
                    }
                    return binding;
                })
                .distinct()
                .sorted(BINDING_ORDER)
                .toList();
    }

    public static List<String> failedTargets(List<String> targets) {
        TreeSet<String> sorted = new TreeSet<>();
        if (targets != null) {
            for (String target : targets) {
                String sanitized = sanitizeTarget(target);
                if (sanitized != null) {
                    sorted.add(sanitized);
                }
                if (sorted.size() > MAX_FAILED_TARGETS) {
                    sorted.pollLast();
                }
            }
        }
        return List.copyOf(sorted);
    }

    public static List<RuntimeLifecycleFailureDiagnostic> diagnostics(
            List<RuntimeLifecycleFailureDiagnostic> diagnostics
    ) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return List.of();
        }
        List<RuntimeLifecycleFailureDiagnostic> result = new ArrayList<>();
        Set<RuntimeLifecycleFailureDiagnostic> seen = new LinkedHashSet<>();
        for (RuntimeLifecycleFailureDiagnostic diagnostic : diagnostics) {
            if (diagnostic != null && seen.add(diagnostic)) {
                result.add(diagnostic);
                if (result.size() == MAX_DIAGNOSTICS) {
                    break;
                }
            }
        }
        return List.copyOf(result);
    }
}
