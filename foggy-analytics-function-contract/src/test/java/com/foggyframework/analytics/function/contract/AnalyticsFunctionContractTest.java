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
                        "analytics.model-dependencies.resolve",
                        "analytics.model-dependencies.list",
                        "analytics.semantic-models.describe",
                        "analytics.semantic-queries.execute",
                        "analytics.query-model.run",
                        "analytics.compose.run",
                        "analytics.reports.preview",
                        "analytics.dashboards.preview",
                        "analytics.dashboards.render"),
                AnalyticsFunctionOperations.SDK_V1);
        assertFalse(AnalyticsFunctionOperations.FAP_V1.contains(
                AnalyticsFunctionOperations.MODEL_DEPENDENCIES_RESOLVE));
        assertTrue(AnalyticsFunctionOperations.FAP_V1.contains(
                AnalyticsFunctionOperations.MODEL_DEPENDENCIES_LIST));
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
        AnalyticsModelDependencyResolutionRequest dependencyRequest =
                new AnalyticsModelDependencyResolutionRequest(
                        "tms-ai",
                        "qm",
                        "TenantOrgManagementQuery",
                        AnalyticsFunctionRequestContext.empty());
        assertEquals("qm", dependencyRequest.modelKind());
        assertEquals(REVISION, new AnalyticsModelDependencyDescription(
                "tms-ai",
                "qm",
                "TenantOrgManagementQuery",
                REVISION).dependencyDigest());
        AnalyticsModelDependencyList list = new AnalyticsModelDependencyList(
                "tms-ai",
                "qm",
                List.of(new AnalyticsModelSummary(
                        "tms-ai", "qm", "TenantOrgManagementQuery")));
        assertEquals("TenantOrgManagementQuery", list.models().get(0).modelName());
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalyticsModelDependencyResolutionRequest(
                        "tms-ai",
                        "sql",
                        "TenantOrgManagementQuery",
                        AnalyticsFunctionRequestContext.empty()));
    }

    @Test
    void semanticQuestionContractHasAClosedGovernedQueryShape() {
        AnalyticsSemanticQuery query = new AnalyticsSemanticQuery(
                List.of("orderCount"),
                List.of(new AnalyticsSemanticQuery.Filter(
                        "status", "=", "SHIPPED")),
                List.of(),
                List.of(new AnalyticsSemanticQuery.Order("orderCount", "desc")),
                0,
                100,
                true,
                false);
        AnalyticsSemanticQueryFunctionRequest request =
                new AnalyticsSemanticQueryFunctionRequest(
                        "default",
                        "FactOrderQueryModel",
                        query,
                        new AnalyticsFunctionAuthority("tms", "subject:42"),
                        AnalyticsFunctionRequestContext.empty());

        assertEquals(List.of("orderCount"), request.query().columns());
        assertThrows(IllegalArgumentException.class, () -> new AnalyticsSemanticQuery(
                List.of("orderCount"), List.of(), List.of(), List.of(),
                0, 1_001, false, false));
        assertThrows(IllegalArgumentException.class, () ->
                new AnalyticsSemanticQuery.Filter("status", "script", "x"));
        assertThrows(IllegalArgumentException.class, () ->
                new AnalyticsSemanticQuery.Filter(
                        "status", "=", Map.of("authority", "forged")));
        assertThrows(IllegalArgumentException.class, () ->
                new AnalyticsSemanticQuery.Filter(
                        "status", "in", java.util.Collections.nCopies(257, "x")));

        Set<String> queryFields = Arrays.stream(
                        AnalyticsSemanticQuery.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .collect(Collectors.toSet());
        assertFalse(queryFields.contains("rawsql"));
        assertFalse(queryFields.contains("compose"));
        assertFalse(queryFields.contains("script"));
        assertFalse(queryFields.contains("hints"));
        assertFalse(queryFields.contains("extdata"));
        Set<String> requestFields = Arrays.stream(
                        AnalyticsSemanticQueryFunctionRequest.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .collect(Collectors.toSet());
        assertFalse(requestFields.stream().anyMatch(name -> name.contains("revision")));
    }

    @Test
    void advancedSemanticContractsExposeDslWithoutCallerControlledAuthority() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("columns", List.of("orderId"));
        payload.put("timeWindow", Map.of("type", "YTD"));
        AnalyticsQueryModelFunctionRequest query =
                new AnalyticsQueryModelFunctionRequest(
                        "default",
                        "FactOrderQueryModel",
                        "VALIDATE",
                        payload,
                        new AnalyticsFunctionAuthority("tms", "subject:42"),
                        AnalyticsFunctionRequestContext.empty());
        payload.put("rawSql", "select 1");

        assertEquals("validate", query.mode());
        assertFalse(query.payload().containsKey("rawSql"));
        assertThrows(UnsupportedOperationException.class,
                () -> query.payload().put("limit", 10));

        AnalyticsComposeFunctionRequest compose = new AnalyticsComposeFunctionRequest(
                "default",
                "PREVIEW",
                "return { plans: dsl({ model: 'FactOrderQueryModel' }) };",
                Map.of("status", "SHIPPED"),
                new AnalyticsFunctionAuthority("tms", "subject:42"),
                AnalyticsFunctionRequestContext.empty());
        assertEquals("preview", compose.mode());
        assertThrows(IllegalArgumentException.class, () ->
                new AnalyticsComposeFunctionRequest(
                        "default", "sql", "select 1", Map.of(),
                        compose.authority(), compose.context()));
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
                        AnalyticsModelDependencyResolutionRequest.class,
                        AnalyticsRenderFunctionRequest.class,
                        AnalyticsSemanticModelFunctionRequest.class,
                        AnalyticsSemanticQueryFunctionRequest.class,
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
