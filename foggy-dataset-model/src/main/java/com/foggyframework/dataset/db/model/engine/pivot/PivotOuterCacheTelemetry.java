package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotMetricItem;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Staged outer Pivot cache eligibility and key telemetry for E1a/E1b.
 */
final class PivotOuterCacheTelemetry {

    static final String TELEMETRY_STAGE = "E1a";
    static final String CACHE_STAGE = "E1b";
    static final String TELEMETRY_MISS_REASON = "telemetry_only";
    static final String CACHE_MISS_REASON = "cache_not_found";
    static final String CACHE_EXPIRED_REASON = "ttl_expired";
    static final String CACHE_STORE_SKIPPED_WARNING_REASON = "response_warning";

    private static final List<String> VOLATILE_EXPR_MARKERS = List.of(
            "now(",
            "current_date",
            "current_time",
            "current_timestamp",
            "localtime",
            "localtimestamp",
            "rand(",
            "random(",
            "uuid("
    );

    private PivotOuterCacheTelemetry() {
    }

    static Evaluation evaluate(String model,
                               SemanticQueryRequest request,
                               SemanticRequestContext context,
                               boolean treeMode,
                               boolean cascadeRequest) {
        return evaluate(model, null, request, context, treeMode, cascadeRequest, TELEMETRY_STAGE);
    }

    static Evaluation evaluate(String model,
                               QueryModel queryModel,
                               SemanticQueryRequest request,
                               SemanticRequestContext context,
                               boolean treeMode,
                               boolean cascadeRequest,
                               String eligibilityStage) {
        PivotRequest pivot = request != null ? request.getPivot() : null;
        String shapeClass = shapeClass(pivot, treeMode, cascadeRequest);
        String reason = refusalReason(request, pivot, treeMode, cascadeRequest);
        String keyHash = keyHash(model, queryModel, request, context, shapeClass, eligibilityStage);
        return new Evaluation(keyHash, shapeClass, reason);
    }

    private static String refusalReason(SemanticQueryRequest request,
                                        PivotRequest pivot,
                                        boolean treeMode,
                                        boolean cascadeRequest) {
        if (treeMode) {
            return "tree_mode";
        }
        if (pivot != null && "tree".equals(normalizedFormat(pivot))) {
            return "tree_output_format";
        }
        if (cascadeRequest) {
            return "cascade_shape";
        }
        if (request != null && Boolean.TRUE.equals(request.getStream())) {
            return "streaming_response";
        }
        if (hasVolatileCalculatedField(request)) {
            return "volatile_calculated_field";
        }
        String format = normalizedFormat(pivot);
        if (!"flat".equals(format) && !"grid".equals(format)) {
            return "unsupported_output_format";
        }
        return null;
    }

    private static boolean hasVolatileCalculatedField(SemanticQueryRequest request) {
        if (request == null) {
            return false;
        }
        if (containsVolatileCalculatedField(request.getCalculatedFields())) {
            return true;
        }
        PivotRequest pivot = request.getPivot();
        if (pivot == null || pivot.getMetricItems() == null) {
            return false;
        }
        for (PivotMetricItem item : pivot.getMetricItems()) {
            if (item != null && containsVolatileExpression(item.getExpr())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsVolatileCalculatedField(List<CalculatedFieldDef> calculatedFields) {
        if (calculatedFields == null || calculatedFields.isEmpty()) {
            return false;
        }
        for (CalculatedFieldDef field : calculatedFields) {
            if (field != null && containsVolatileExpression(field.getExpression())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsVolatileExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        String lower = expression.toLowerCase();
        for (String marker : VOLATILE_EXPR_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String shapeClass(PivotRequest pivot, boolean treeMode, boolean cascadeRequest) {
        if (treeMode || (pivot != null && "tree".equals(normalizedFormat(pivot)))) {
            return "tree";
        }
        if (cascadeRequest) {
            return "cascade";
        }
        return normalizedFormat(pivot);
    }

    private static String normalizedFormat(PivotRequest pivot) {
        if (pivot == null || pivot.getOutputFormat() == null || pivot.getOutputFormat().isBlank()) {
            return "tree";
        }
        return pivot.getOutputFormat();
    }

    private static String keyHash(String model,
                                  QueryModel queryModel,
                                  SemanticQueryRequest request,
                                  SemanticRequestContext context,
                                  String shapeClass,
                                  String eligibilityStage) {
        List<String> parts = new ArrayList<>();
        parts.add("stage=" + safe(eligibilityStage));
        parts.add("model=" + safe(model));
        parts.add("queryModel=" + queryModelValue(queryModel));
        parts.add("shape=" + safe(shapeClass));
        parts.add("namespace=" + safe(context != null ? context.getNamespace() : null));
        parts.add("pivot=" + stableValue(request != null ? request.getPivot() : null));
        parts.add("slice=" + stableValue(request != null ? request.getSlice() : null));
        parts.add("calculatedFields=" + stableValue(request != null ? request.getCalculatedFields() : null));
        parts.add("extData=" + stableValue(request != null ? request.getExtData() : null));
        parts.add("systemSlice=" + stableValue(context != null ? context.getSystemSlice() : null));
        parts.add("security=" + securityValue(context != null ? context.getSecurityContext() : null));
        parts.add("fieldAccess=" + stableValue(context != null ? context.getFieldAccess() : null));
        parts.add("deniedColumns=" + stableValue(context != null ? context.getDeniedColumns() : null));
        return sha256(String.join("|", parts)).substring(0, 16);
    }

    private static String queryModelValue(QueryModel queryModel) {
        if (queryModel == null) {
            return "<none>";
        }
        List<String> parts = new ArrayList<>();
        parts.add("class=" + queryModel.getClass().getName());
        parts.add("name=" + safe(queryModel.getName()));
        parts.add("shortAlias=" + safe(queryModel.getShortAlias()));
        parts.add("jdbcModel=" + tableModelValue(queryModel.getJdbcModel()));
        parts.add("jdbcModelList=" + stableValue(queryModel.getJdbcModelList() == null
                ? null
                : queryModel.getJdbcModelList().stream()
                .map(PivotOuterCacheTelemetry::tableModelValue)
                .toList()));
        parts.add("predefinedCalculatedFields=" + stableValue(queryModel.getPredefinedCalculatedFields()));
        return String.join(",", parts);
    }

    private static String tableModelValue(TableModel tableModel) {
        if (tableModel == null) {
            return "<none>";
        }
        return tableModel.getClass().getName() + ":" + safe(tableModel.getName());
    }

    private static String securityValue(ModelResultContext.SecurityContext securityContext) {
        if (securityContext == null) {
            return "<none>";
        }
        List<String> parts = new ArrayList<>();
        parts.add("authorization=" + safe(securityContext.getAuthorization()));
        parts.add("userId=" + safe(securityContext.getUserId()));
        parts.add("roles=" + stableValue(securityContext.getRoles()));
        parts.add("tenantId=" + safe(securityContext.getTenantId()));
        parts.add("deptId=" + safe(securityContext.getDeptId()));
        parts.add("attributes=" + stableValue(securityContext.getAttributes()));
        return String.join(",", parts);
    }

    private static String stableValue(Object value) {
        if (value == null) {
            return "<null>";
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .map(entry -> safe(entry.getKey()) + "=" + stableValue(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Set<?> set) {
            return set.stream()
                    .map(PivotOuterCacheTelemetry::stableValue)
                    .sorted()
                    .collect(Collectors.joining(",", "[", "]"));
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> items = new ArrayList<>();
            for (Object item : iterable) {
                items.add(stableValue(item));
            }
            return items.stream().collect(Collectors.joining(",", "[", "]"));
        }
        return String.valueOf(value);
    }

    private static String safe(Object value) {
        return value == null ? "<null>" : String.valueOf(value);
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    record Evaluation(String keyHash, String shapeClass, String refusalReason) {
        boolean refused() {
            return refusalReason != null;
        }
    }
}
