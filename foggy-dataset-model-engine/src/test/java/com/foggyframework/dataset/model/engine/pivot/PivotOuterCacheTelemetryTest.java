package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.model.semantic.permission.AuthorizationSignature;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.TableModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PivotOuterCacheTelemetryTest {

    @Test
    @DisplayName("E1b cache key uses engine authorization signature and excludes raw authorization")
    void testKeyUsesAuthorizationSignature() {
        QueryModel queryModel = queryModel("FactSalesQueryModel", "FS", "FactSalesTableModel");
        SemanticQueryRequest request = request();

        PivotOuterCacheTelemetry.Evaluation userA = evaluate(queryModel, request,
                context("user-a", Set.of("product$categoryName", "salesAmount"),
                        List.of(new DeniedPhysicalColumn(null, "fact_sales", "profit_amount"))),
                publicSignature("permission-a"));
        PivotOuterCacheTelemetry.Evaluation userB = evaluate(queryModel, request,
                context("user-b", Set.of("product$categoryName", "salesAmount"),
                        List.of(new DeniedPhysicalColumn(null, "fact_sales", "profit_amount"))),
                publicSignature("permission-a"));
        PivotOuterCacheTelemetry.Evaluation differentPermission = evaluate(queryModel, request,
                context("user-a", Set.of("product$categoryName", "salesAmount"),
                        List.of(new DeniedPhysicalColumn(null, "fact_sales", "profit_amount"))),
                publicSignature("permission-b"));

        assertEquals(userA.keyHash(), userB.keyHash(),
                "raw authorization must not affect the outer cache key");
        assertNotEquals(userA.keyHash(), differentPermission.keyHash(),
                "different final permission snapshots must not share cache keys");
        assertFalse(userA.refused(), "a complete catalog identity must be cache eligible");
    }

    @Test
    @DisplayName("E1b refuses missing and protected authorization signatures")
    void testRefusesUnsafeAuthorizationSignature() {
        QueryModel queryModel = queryModel("FactSalesQueryModel", "FS", "FactSalesTableModel");
        SemanticQueryRequest request = request();
        SemanticRequestContext context = context(
                "user-a", Set.of("product$categoryName", "salesAmount"), null);
        PivotOuterCacheTelemetry.ModelIdentity identity = modelIdentity(queryModel, "", "");

        PivotOuterCacheTelemetry.Evaluation missing = PivotOuterCacheTelemetry.evaluate(
                "FactSalesQueryModel", queryModel, request, context, false, false,
                PivotOuterCacheTelemetry.CACHE_STAGE, identity, null);
        PivotOuterCacheTelemetry.Evaluation protectedDecision = PivotOuterCacheTelemetry.evaluate(
                "FactSalesQueryModel", queryModel, request, context, false, false,
                PivotOuterCacheTelemetry.CACHE_STAGE, identity,
                new AuthorizationSignature(
                        "AUTH:protected", false, java.time.Instant.now().plusSeconds(60)));

        assertEquals(PivotOuterCacheTelemetry.MISSING_AUTHORIZATION_SIGNATURE_REASON,
                missing.refusalReason());
        assertEquals(PivotOuterCacheTelemetry.PROTECTED_PERMISSION_OUTER_CACHE_UNSUPPORTED_REASON,
                protectedDecision.refusalReason());
        assertTrue(missing.refused());
        assertTrue(protectedDecision.refused());
    }

    @Test
    @DisplayName("E1b cache key changes when QueryModel/TableModel fingerprint changes")
    void testKeyChangesForQueryModelFingerprint() {
        SemanticQueryRequest request = request();
        SemanticRequestContext context = context("user-a", Set.of("product$categoryName", "salesAmount"), null);

        PivotOuterCacheTelemetry.Evaluation first = evaluate(
                queryModel("FactSalesQueryModel", "FS", "FactSalesTableModel"), request, context);
        PivotOuterCacheTelemetry.Evaluation renamedQueryModel = evaluate(
                queryModel("FactSalesQueryModelV2", "FS", "FactSalesTableModel"), request, context);
        PivotOuterCacheTelemetry.Evaluation changedTableModel = evaluate(
                queryModel("FactSalesQueryModel", "FS", "FactSalesTableModelV2"), request, context);

        assertNotEquals(first.keyHash(), renamedQueryModel.keyHash(), "query model identity must affect cache keys");
        assertNotEquals(first.keyHash(), changedTableModel.keyHash(), "table model identity must affect cache keys");
    }

    @Test
    @DisplayName("E1b cache key includes bundle fingerprint and freshness token")
    void testKeyChangesForBundleFingerprintAndFreshnessToken() {
        QueryModel queryModel = queryModel("FactSalesQueryModel", "FS", "FactSalesTableModel");
        SemanticQueryRequest request = request();
        SemanticRequestContext context = context("user-a", Set.of("product$categoryName", "salesAmount"), null);

        PivotOuterCacheTelemetry.Evaluation first = evaluate(queryModel, request, context,
                modelIdentity(queryModel, "bundle-sha:aaa", "freshness:1"));
        PivotOuterCacheTelemetry.Evaluation changedBundle = evaluate(queryModel, request, context,
                modelIdentity(queryModel, "bundle-sha:bbb", "freshness:1"));
        PivotOuterCacheTelemetry.Evaluation changedFreshness = evaluate(queryModel, request, context,
                modelIdentity(queryModel, "bundle-sha:aaa", "freshness:2"));

        assertNotEquals(first.keyHash(), changedBundle.keyHash(),
                "deployment bundle fingerprint must affect cache keys");
        assertNotEquals(first.keyHash(), changedFreshness.keyHash(),
                "model freshness token must affect cache keys");
    }

    @Test
    @DisplayName("E1a request refusal remains primary while incomplete identity stays fail-closed")
    void requestShapeReasonPrecedesLifecycleIdentityReason() {
        QueryModel queryModel = queryModel("FactSalesQueryModel", "FS", "FactSalesTableModel");
        SemanticQueryRequest request = request();
        CatalogResolution<QueryModel> incompleteResolution = new CatalogResolution<>(
                queryModel.getName(),
                queryModel,
                new CatalogIdentity(
                        "",
                        new CatalogGeneration("catalog-generation-a"),
                        new SourceRevision("source-revision-a")),
                Map.of(),
                false);
        PivotOuterCacheTelemetry.ModelIdentity incompleteIdentity =
                PivotOuterCacheTelemetry.ModelIdentity.from(
                        PivotOuterCacheStrongIdentity.assess(incompleteResolution, null),
                        PivotOuterCacheModelIdentity.empty(),
                        "",
                        "");

        PivotOuterCacheTelemetry.Evaluation evaluation = PivotOuterCacheTelemetry.evaluate(
                "FactSalesQueryModel",
                queryModel,
                request,
                context("user-a", Set.of("product$categoryName", "salesAmount"), null),
                false,
                true,
                PivotOuterCacheTelemetry.TELEMETRY_STAGE,
                incompleteIdentity,
                publicSignature("permission-a"));

        assertEquals("cascade_shape", evaluation.refusalReason());
        assertEquals(PivotOuterCacheStrongIdentity.STATUS_INCOMPLETE, evaluation.identityStatus());
        assertEquals("cascade", evaluation.shapeClass());
        assertTrue(evaluation.refused(), "incomplete lifecycle identity must still refuse cache I/O");
    }

    private PivotOuterCacheTelemetry.Evaluation evaluate(QueryModel queryModel,
                                                         SemanticQueryRequest request,
                                                         SemanticRequestContext context) {
        return evaluate(queryModel, request, context, publicSignature("permission-default"));
    }

    private PivotOuterCacheTelemetry.Evaluation evaluate(QueryModel queryModel,
                                                         SemanticQueryRequest request,
                                                         SemanticRequestContext context,
                                                         AuthorizationSignature authorizationSignature) {
        return evaluate(queryModel, request, context, modelIdentity(queryModel, "", ""),
                authorizationSignature);
    }

    private PivotOuterCacheTelemetry.Evaluation evaluate(QueryModel queryModel,
                                                         SemanticQueryRequest request,
                                                         SemanticRequestContext context,
                                                         PivotOuterCacheTelemetry.ModelIdentity modelIdentity) {
        return evaluate(queryModel, request, context, modelIdentity,
                publicSignature("permission-default"));
    }

    private PivotOuterCacheTelemetry.Evaluation evaluate(QueryModel queryModel,
                                                         SemanticQueryRequest request,
                                                         SemanticRequestContext context,
                                                         PivotOuterCacheTelemetry.ModelIdentity modelIdentity,
                                                         AuthorizationSignature authorizationSignature) {
        return PivotOuterCacheTelemetry.evaluate("FactSalesQueryModel", queryModel, request, context,
                false, false, PivotOuterCacheTelemetry.CACHE_STAGE, modelIdentity,
                authorizationSignature);
    }

    private AuthorizationSignature publicSignature(String value) {
        return new AuthorizationSignature("PUBLIC:" + value, true, null);
    }

    private PivotOuterCacheTelemetry.ModelIdentity modelIdentity(QueryModel queryModel,
                                                                 String bundleFingerprint,
                                                                 String freshnessToken) {
        CatalogResolution<QueryModel> resolution = new CatalogResolution<>(
                queryModel.getName(),
                queryModel,
                new CatalogIdentity(
                        "",
                        new CatalogGeneration("catalog-generation-a"),
                        new SourceRevision("source-revision-a")),
                Map.of(),
                true);
        return PivotOuterCacheTelemetry.ModelIdentity.from(
                PivotOuterCacheStrongIdentity.assess(resolution, null),
                new PivotOuterCacheModelIdentity(bundleFingerprint, freshnessToken),
                "",
                "");
    }

    private SemanticRequestContext context(String userId,
                                           Set<String> fieldAccess,
                                           List<DeniedPhysicalColumn> deniedColumns) {
        ModelResultContext.SecurityContext securityContext = ModelResultContext.SecurityContext.builder()
                .authorization("Bearer " + userId)
                .userId(userId)
                .roles(List.of("analyst"))
                .tenantId("tenant-main")
                .attributes(Map.of("scope", "pivot-cache-test"))
                .build();
        return SemanticRequestContext.of(null, securityContext, fieldAccess, deniedColumns);
    }

    private SemanticQueryRequest request() {
        AxisField row = new AxisField();
        row.setField("product$categoryName");
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(row));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);
        return request;
    }

    private QueryModel queryModel(String name, String shortAlias, String tableModelName) {
        TableModel tableModel = tableModel(tableModelName);
        return (QueryModel) Proxy.newProxyInstance(
                QueryModel.class.getClassLoader(),
                new Class<?>[]{QueryModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getShortAlias" -> shortAlias;
                    case "getJdbcModel" -> tableModel;
                    case "getJdbcModelList" -> List.of(tableModel);
                    case "getPredefinedCalculatedFields" -> List.of();
                    case "toString" -> "QueryModelFingerprint(" + name + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private TableModel tableModel(String name) {
        return (TableModel) Proxy.newProxyInstance(
                TableModel.class.getClassLoader(),
                new Class<?>[]{TableModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "toString" -> "TableModelFingerprint(" + name + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
