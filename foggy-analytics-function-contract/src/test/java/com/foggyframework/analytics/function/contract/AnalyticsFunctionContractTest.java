package com.foggyframework.analytics.function.contract;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsFunctionContractTest {

    private static final String REVISION = "sha256:" + "a".repeat(64);

    @Test
    void freezesSdkOperationNamesToAnalyticsRuntimeApiNames() {
        assertEquals(Set.of(
                        "analytics.capabilities",
                        "analytics.bundles.list",
                        "analytics.bundles.validate",
                        "analytics.bundles.describe",
                        "analytics.artifacts.describe",
                        "analytics.reports.preview",
                        "analytics.dashboards.preview",
                        "analytics.dashboards.render"),
                AnalyticsFunctionOperations.SDK_V1);
        assertFalse(AnalyticsFunctionOperations.SDK_V1.contains(
                AnalyticsFunctionOperations.BUNDLES_PULL));
        assertFalse(AnalyticsFunctionOperations.SDK_V1.contains(
                AnalyticsFunctionOperations.BUNDLES_SAVE));
    }

    @Test
    void enforcesExactRevisionOpaqueAuthorityAndImmutableParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("region", "east");
        AnalyticsRenderFunctionRequest request = new AnalyticsRenderFunctionRequest(
                "sales",
                "sales-summary",
                REVISION,
                parameters,
                "Asia/Shanghai",
                "zh-CN",
                new AnalyticsFunctionAuthority("tms", "subject:42"),
                new AnalyticsFunctionRequestContext("request-1", "trace-1"));
        parameters.put("region", "west");

        assertEquals("east", request.parameters().get("region"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> request.parameters().put("region", "north"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalyticsBundleFunctionRequest(
                        "sales", "latest", AnalyticsFunctionRequestContext.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalyticsFunctionAuthority("tms", "  "));
        assertEquals("report", new AnalyticsArtifactFunctionRequest(
                "sales",
                "report",
                "sales-summary",
                REVISION,
                AnalyticsFunctionRequestContext.empty()).artifactKind());
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalyticsArtifactFunctionRequest(
                        "sales",
                        "query",
                        "sales-summary",
                        REVISION,
                        AnalyticsFunctionRequestContext.empty()));
    }

    @Test
    void canonicalizesJsonValuesRecursivelyAcrossTransports() {
        List<Object> sourceList = new ArrayList<>();
        sourceList.add(7);
        sourceList.add(null);
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("amount", new BigDecimal("12.50"));
        nested.put("items", sourceList);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("nested", nested);

        AnalyticsRenderFunctionRequest request = new AnalyticsRenderFunctionRequest(
                "sales",
                "sales-summary",
                REVISION,
                parameters,
                "UTC",
                "en",
                new AnalyticsFunctionAuthority("tms", "subject:42"),
                AnalyticsFunctionRequestContext.empty());
        nested.put("amount", BigDecimal.ZERO);
        sourceList.set(0, 99);

        @SuppressWarnings("unchecked")
        Map<String, Object> normalized = (Map<String, Object>)
                request.parameters().get("nested");
        @SuppressWarnings("unchecked")
        List<Object> normalizedItems = (List<Object>) normalized.get("items");
        assertEquals(new BigDecimal("12.50"), normalized.get("amount"));
        assertEquals(BigInteger.valueOf(7), normalizedItems.get(0));
        assertNull(normalizedItems.get(1));
        assertThrows(UnsupportedOperationException.class,
                () -> normalizedItems.add("later"));
    }

    @Test
    void rejectsNonJsonValuesUnsafeRefsAndHeaderUnsafeCorrelation() {
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("self", cyclic);
        Map<String, Object> blankKey = new LinkedHashMap<>();
        blankKey.put(" ", "value");

        assertThrows(IllegalArgumentException.class,
                () -> AnalyticsFunctionJsonValues.normalizeValue(
                        "date", Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
                () -> AnalyticsFunctionJsonValues.normalizeValue(
                        "number", Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> AnalyticsFunctionJsonValues.normalizeObject(
                        "parameters", cyclic));
        assertThrows(IllegalArgumentException.class,
                () -> AnalyticsFunctionJsonValues.normalizeObject(
                        "parameters", blankKey));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsBundleFunctionRequest(
                        "sales/eu", REVISION, AnalyticsFunctionRequestContext.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsBundleFunctionRequest(
                        "..", REVISION, AnalyticsFunctionRequestContext.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsFunctionRequestContext("bad\nid", null));
    }

    @Test
    void outcomeIsVersionedAndCannotMixDataWithErrors() {
        AnalyticsFunctionContext context = AnalyticsFunctionContext.normalize(
                new AnalyticsFunctionRequestContext("request-1", null));
        AnalyticsFunctionEnvelope<String> success = AnalyticsFunctionEnvelope.ok(
                AnalyticsFunctionContract.DEFAULT_RUNTIME_API_VERSION,
                AnalyticsFunctionContract.DEFAULT_SCHEMA_VERSION,
                "ok",
                context);

        assertTrue(success.success());
        assertEquals(AnalyticsFunctionContract.VERSION,
                success.functionContractVersion());
        assertEquals("request-1", success.context().traceId());
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalyticsFunctionEnvelope<>(
                        true,
                        "java",
                        AnalyticsFunctionContract.VERSION,
                        AnalyticsFunctionContract.DEFAULT_RUNTIME_API_VERSION,
                        AnalyticsFunctionContract.DEFAULT_SCHEMA_VERSION,
                        "data",
                        context,
                        new AnalyticsFunctionError(
                                "CODE", "phase", "message", false)));
    }

    @Test
    void publicRequestTypesContainNoProductOwnershipOrFapLifecycleFields() {
        Set<String> fields = Arrays.stream(new Class<?>[]{
                        AnalyticsBundleFunctionRequest.class,
                        AnalyticsArtifactFunctionRequest.class,
                        AnalyticsRenderFunctionRequest.class,
                        AnalyticsFunctionAuthority.class
                })
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(component -> component.getName().toLowerCase())
                .collect(Collectors.toSet());

        assertFalse(fields.contains("owner"));
        assertFalse(fields.contains("tenant"));
        assertFalse(fields.contains("acl"));
        assertFalse(fields.contains("task"));
        assertFalse(fields.contains("conversation"));
    }
}
