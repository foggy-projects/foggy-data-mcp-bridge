package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotMetricItem;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Staged outer Pivot cache eligibility and key telemetry for E1a/E1b.
 */
final class PivotOuterCacheTelemetry {

    static final String TELEMETRY_STAGE = "E1a";
    static final String CACHE_STAGE = "E1b";
    static final String TELEMETRY_MISS_REASON = "telemetry_only";
    static final String CACHE_MISS_REASON = "cache_not_found";
    static final String CACHE_EXPIRED_REASON = "ttl_expired";
    static final String CACHE_PROVIDER_UNAVAILABLE_REASON = "provider_unavailable";
    static final String CACHE_STORE_SKIPPED_WARNING_REASON = "response_warning";
    static final String SUPPLEMENTARY_IDENTITY_PROVIDER_FAILED_REASON =
            "supplementary_identity_provider_failed";
    static final String SUPPLEMENTARY_IDENTITY_PROVIDER_FAILED_STATUS =
            "supplementary_provider_failed";

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
        return evaluate(model, queryModel, request, context, treeMode, cascadeRequest, eligibilityStage,
                ModelIdentity.empty());
    }

    static Evaluation evaluate(String model,
                               QueryModel queryModel,
                               SemanticQueryRequest request,
                               SemanticRequestContext context,
                               boolean treeMode,
                               boolean cascadeRequest,
                               String eligibilityStage,
                               ModelIdentity modelIdentity) {
        ModelIdentity safeIdentity = ModelIdentity.normalize(modelIdentity);
        PivotRequest pivot = request != null ? request.getPivot() : null;
        String shapeClass = shapeClass(pivot, treeMode, cascadeRequest);
        String requestRefusal = refusalReason(request, pivot, treeMode, cascadeRequest);
        // Preserve the E1a request-shape diagnostic when the request is already
        // ineligible. Lifecycle identity remains an independent fail-closed
        // gate (and is reported by pivot.cache.identity), so an incomplete
        // identity still prevents lookup/store without masking the earlier
        // compatibility reason.
        String reason = requestRefusal != null
                ? requestRefusal
                : safeIdentity.identityRefusalReason();
        String keyHash = keyHash(model, queryModel, request, context, shapeClass, eligibilityStage,
                safeIdentity);
        return new Evaluation(
                keyHash,
                shapeClass,
                reason,
                safeIdentity.identityHash(),
                safeIdentity.identityStatus(),
                safeIdentity.bindingCount(),
                safeIdentity.manualTokenPresent(),
                safeIdentity.supplementaryProviderFailed(),
                safeIdentity.supplementaryProviderFailureClass());
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
                                  String eligibilityStage,
                                  ModelIdentity modelIdentity) {
        StringBuilder key = new StringBuilder("pivot-outer-cache-key-v2");
        append(key, "stage", normalizeToken(eligibilityStage));
        append(key, "model", normalizeToken(model));
        append(key, "modelIdentity", modelIdentity.stableValue());
        append(key, "queryModel", queryModelValue(queryModel));
        append(key, "shape", normalizeToken(shapeClass));
        append(key, "namespace", context != null ? normalizeToken(context.getNamespace()) : "");
        append(key, "pivot", stableValue(request != null ? request.getPivot() : null));
        append(key, "slice", stableValue(request != null ? request.getSlice() : null));
        append(key, "calculatedFields", stableValue(request != null ? request.getCalculatedFields() : null));
        append(key, "extData", stableValue(request != null ? request.getExtData() : null));
        append(key, "systemSlice", stableValue(context != null ? context.getSystemSlice() : null));
        append(key, "security", securityValue(context != null ? context.getSecurityContext() : null));
        append(key, "fieldAccess", stableValue(context != null ? context.getFieldAccess() : null));
        append(key, "deniedColumns", stableValue(context != null ? context.getDeniedColumns() : null));
        return "v2:" + sha256(key.toString());
    }

    private static String queryModelValue(QueryModel queryModel) {
        if (queryModel == null) {
            return "N";
        }
        StringBuilder value = new StringBuilder("query-model-v2");
        append(value, "class", queryModel.getClass().getName());
        append(value, "name", normalizeToken(queryModel.getName()));
        append(value, "shortAlias", normalizeToken(queryModel.getShortAlias()));
        append(value, "jdbcModel", tableModelValue(queryModel.getJdbcModel()));
        append(value, "jdbcModelList", stableValue(queryModel.getJdbcModelList() == null
                ? null
                : queryModel.getJdbcModelList().stream()
                .map(PivotOuterCacheTelemetry::tableModelValue)
                .toList()));
        append(value, "predefinedCalculatedFields",
                stableValue(queryModel.getPredefinedCalculatedFields()));
        return value.toString();
    }

    private static String tableModelValue(TableModel tableModel) {
        if (tableModel == null) {
            return "N";
        }
        StringBuilder value = new StringBuilder("table-model-v2");
        append(value, "class", tableModel.getClass().getName());
        append(value, "name", normalizeToken(tableModel.getName()));
        return value.toString();
    }

    private static String securityValue(ModelResultContext.SecurityContext securityContext) {
        if (securityContext == null) {
            return "N";
        }
        StringBuilder value = new StringBuilder("security-v2");
        append(value, "authorization", normalizeToken(securityContext.getAuthorization()));
        append(value, "userId", normalizeToken(securityContext.getUserId()));
        append(value, "roles", stableValue(securityContext.getRoles()));
        append(value, "tenantId", normalizeToken(securityContext.getTenantId()));
        append(value, "deptId", normalizeToken(securityContext.getDeptId()));
        append(value, "attributes", stableValue(securityContext.getAttributes()));
        return value.toString();
    }

    private static String stableValue(Object value) {
        if (value == null) {
            return "N";
        }
        if (value instanceof Map<?, ?> map) {
            List<MapValue> values = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                values.add(new MapValue(stableValue(entry.getKey()), stableValue(entry.getValue())));
            }
            values.sort(Comparator.comparing(MapValue::key).thenComparing(MapValue::value));
            StringBuilder encoded = new StringBuilder("M");
            append(encoded, "size", String.valueOf(values.size()));
            for (MapValue entry : values) {
                append(encoded, "key", entry.key());
                append(encoded, "value", entry.value());
            }
            return encoded.toString();
        }
        if (value instanceof Set<?> set) {
            List<String> values = set.stream()
                    .map(PivotOuterCacheTelemetry::stableValue)
                    .sorted()
                    .toList();
            return collectionValue("S", values);
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object item : iterable) {
                values.add(stableValue(item));
            }
            return collectionValue("L", values);
        }
        if (value.getClass().isArray()) {
            List<String> values = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                values.add(stableValue(Array.get(value, i)));
            }
            return collectionValue("A", values);
        }
        StringBuilder encoded = new StringBuilder("V");
        append(encoded, "type", value.getClass().getName());
        append(encoded, "value", String.valueOf(value));
        return encoded.toString();
    }

    private static String collectionValue(String type, List<String> values) {
        StringBuilder encoded = new StringBuilder(type);
        append(encoded, "size", String.valueOf(values.size()));
        for (String value : values) {
            append(encoded, "item", value);
        }
        return encoded.toString();
    }

    private static void append(StringBuilder encoded, String label, String value) {
        appendFramed(encoded, label);
        appendFramed(encoded, value == null ? "" : value);
    }

    private static void appendFramed(StringBuilder encoded, String value) {
        encoded.append(value.getBytes(StandardCharsets.UTF_8).length)
                .append(':')
                .append(value);
    }

    private static String normalizeToken(String value) {
        return value == null ? "" : value.trim();
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

    record Evaluation(String keyHash,
                      String shapeClass,
                      String refusalReason,
                      String identityHash,
                      String identityStatus,
                      int bindingCount,
                      boolean manualTokenPresent,
                      boolean supplementaryProviderFailed,
                      String supplementaryProviderFailureClass) {

        Evaluation(String keyHash,
                   String shapeClass,
                   String refusalReason,
                   String identityHash,
                   String identityStatus,
                   int bindingCount,
                   boolean manualTokenPresent) {
            this(keyHash, shapeClass, refusalReason, identityHash, identityStatus,
                    bindingCount, manualTokenPresent, false, null);
        }

        Evaluation(String keyHash, String shapeClass, String refusalReason) {
            this(keyHash, shapeClass,
                    refusalReason == null
                            ? PivotOuterCacheStrongIdentity.REFUSAL_MISSING
                            : refusalReason,
                    null,
                    PivotOuterCacheStrongIdentity.STATUS_MISSING,
                    0,
                    false,
                    false,
                    null);
        }

        boolean refused() {
            return supplementaryProviderFailed
                    || refusalReason != null
                    || !PivotOuterCacheStrongIdentity.STATUS_COMPLETE.equals(identityStatus)
                    || identityHash == null
                    || !identityHash.matches("[0-9a-f]{64}")
                    || keyHash == null
                    || !keyHash.matches("v2:[0-9a-f]{64}");
        }
    }

    record ModelIdentity(PivotOuterCacheStrongIdentity strongIdentity,
                         int observedBindingCount,
                         String providerBundleFingerprint,
                         String providerModelFreshnessToken,
                         String manualBundleFingerprint,
                         String manualModelFreshnessToken,
                         String identityStatus,
                         String identityRefusalReason,
                         boolean supplementaryProviderFailed,
                         String supplementaryProviderFailureClass) {

        static ModelIdentity empty() {
            return from(PivotOuterCacheStrongIdentity.assess(null, null),
                    PivotOuterCacheModelIdentity.empty(), "", "");
        }

        /** Compatibility factory for direct telemetry callers; tokens never establish lifecycle identity. */
        static ModelIdentity of(String bundleFingerprint, String modelFreshnessToken) {
            return from(PivotOuterCacheStrongIdentity.assess(null, null),
                    new PivotOuterCacheModelIdentity(bundleFingerprint, modelFreshnessToken), "", "");
        }

        static ModelIdentity from(PivotOuterCacheStrongIdentity.Assessment assessment,
                                  PivotOuterCacheModelIdentity providerIdentity,
                                  String manualBundleFingerprint,
                                  String manualModelFreshnessToken) {
            return from(assessment, providerIdentity, manualBundleFingerprint,
                    manualModelFreshnessToken, null);
        }

        static ModelIdentity from(PivotOuterCacheStrongIdentity.Assessment assessment,
                                  PivotOuterCacheModelIdentity providerIdentity,
                                  String manualBundleFingerprint,
                                  String manualModelFreshnessToken,
                                  Class<? extends Throwable> providerFailureType) {
            PivotOuterCacheStrongIdentity.Assessment safeAssessment = assessment == null
                    ? PivotOuterCacheStrongIdentity.assess(null, null)
                    : assessment;
            PivotOuterCacheModelIdentity safeProvider = providerIdentity == null
                    ? PivotOuterCacheModelIdentity.empty()
                    : providerIdentity.normalized();
            boolean providerFailed = providerFailureType != null;
            return new ModelIdentity(
                    safeAssessment.identity(),
                    safeAssessment.bindingCount(),
                    safeProvider.bundleFingerprint(),
                    safeProvider.modelFreshnessToken(),
                    normalizeToken(manualBundleFingerprint),
                    normalizeToken(manualModelFreshnessToken),
                    providerFailed
                            ? SUPPLEMENTARY_IDENTITY_PROVIDER_FAILED_STATUS
                            : safeAssessment.status(),
                    providerFailed
                            ? SUPPLEMENTARY_IDENTITY_PROVIDER_FAILED_REASON
                            : safeAssessment.refusalReason(),
                    providerFailed,
                    providerFailed ? providerFailureType.getName() : null).normalized();
        }

        static ModelIdentity normalize(ModelIdentity modelIdentity) {
            return modelIdentity == null ? empty() : modelIdentity.normalized();
        }

        ModelIdentity normalized() {
            String status = identityStatus == null || identityStatus.isBlank()
                    ? (strongIdentity == null
                    ? PivotOuterCacheStrongIdentity.STATUS_MISSING
                    : PivotOuterCacheStrongIdentity.STATUS_COMPLETE)
                    : identityStatus.trim();
            String refusal = normalizeNullable(identityRefusalReason);
            if (strongIdentity == null && refusal == null) {
                refusal = PivotOuterCacheStrongIdentity.REFUSAL_MISSING;
            }
            boolean providerFailed = supplementaryProviderFailed;
            String providerFailureClass = providerFailed
                    ? normalizeReasonClass(supplementaryProviderFailureClass)
                    : null;
            if (providerFailed) {
                status = SUPPLEMENTARY_IDENTITY_PROVIDER_FAILED_STATUS;
                refusal = SUPPLEMENTARY_IDENTITY_PROVIDER_FAILED_REASON;
            }
            return new ModelIdentity(
                    strongIdentity,
                    Math.max(0, observedBindingCount),
                    normalizeToken(providerBundleFingerprint),
                    normalizeToken(providerModelFreshnessToken),
                    normalizeToken(manualBundleFingerprint),
                    normalizeToken(manualModelFreshnessToken),
                    status,
                    refusal,
                    providerFailed,
                    providerFailureClass);
        }

        String stableValue() {
            StringBuilder value = new StringBuilder("model-identity-v2");
            append(value, "strongIdentity",
                    strongIdentity == null ? "" : strongIdentity.canonicalValue());
            append(value, "observedBindingCount", String.valueOf(observedBindingCount));
            append(value, "providerBundleFingerprint", providerBundleFingerprint);
            append(value, "providerModelFreshnessToken", providerModelFreshnessToken);
            append(value, "manualBundleFingerprint", manualBundleFingerprint);
            append(value, "manualModelFreshnessToken", manualModelFreshnessToken);
            append(value, "identityStatus", identityStatus);
            append(value, "supplementaryProviderFailed",
                    String.valueOf(supplementaryProviderFailed));
            append(value, "supplementaryProviderFailureClass",
                    supplementaryProviderFailureClass == null ? "" : supplementaryProviderFailureClass);
            return value.toString();
        }

        String identityHash() {
            return strongIdentity == null ? null : strongIdentity.identityHash();
        }

        int bindingCount() {
            return observedBindingCount;
        }

        boolean manualTokenPresent() {
            return !manualBundleFingerprint.isBlank() || !manualModelFreshnessToken.isBlank();
        }

        @Override
        public String toString() {
            return "ModelIdentity[identityHash=" + identityHash()
                    + ", identityStatus=" + identityStatus
                    + ", bindingCount=" + bindingCount()
                    + ", manualTokenPresent=" + manualTokenPresent()
                    + ", supplementaryProviderFailed=" + supplementaryProviderFailed
                    + ", supplementaryProviderFailureClass="
                    + supplementaryProviderFailureClass + "]";
        }

        private static String normalizeNullable(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        }

        private static String normalizeReasonClass(String value) {
            String normalized = normalizeNullable(value);
            if (normalized == null || !normalized.matches("[A-Za-z_$][A-Za-z0-9_.$]*")) {
                return "unknown";
            }
            return normalized;
        }
    }

    private record MapValue(String key, String value) {
    }
}
