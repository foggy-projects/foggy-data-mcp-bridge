package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PivotOuterCacheTelemetryTest {

    @Test
    @DisplayName("E1b cache key includes security, fieldAccess, and deniedColumns")
    void testKeyChangesForPermissionContext() {
        QueryModel queryModel = queryModel("FactSalesQueryModel", "FS", "FactSalesTableModel");
        SemanticQueryRequest request = request();

        PivotOuterCacheTelemetry.Evaluation userA = evaluate(queryModel, request,
                context("user-a", Set.of("product$categoryName", "salesAmount"),
                        List.of(new DeniedPhysicalColumn(null, "fact_sales", "profit_amount"))));
        PivotOuterCacheTelemetry.Evaluation userB = evaluate(queryModel, request,
                context("user-b", Set.of("product$categoryName", "salesAmount"),
                        List.of(new DeniedPhysicalColumn(null, "fact_sales", "profit_amount"))));
        PivotOuterCacheTelemetry.Evaluation narrowerFieldAccess = evaluate(queryModel, request,
                context("user-a", Set.of("product$categoryName"),
                        List.of(new DeniedPhysicalColumn(null, "fact_sales", "profit_amount"))));
        PivotOuterCacheTelemetry.Evaluation differentDeniedColumn = evaluate(queryModel, request,
                context("user-a", Set.of("product$categoryName", "salesAmount"),
                        List.of(new DeniedPhysicalColumn(null, "fact_sales", "sales_amount"))));

        assertNotEquals(userA.keyHash(), userB.keyHash(), "different users must not share cache keys");
        assertNotEquals(userA.keyHash(), narrowerFieldAccess.keyHash(), "fieldAccess must affect cache keys");
        assertNotEquals(userA.keyHash(), differentDeniedColumn.keyHash(), "denied physical columns must affect cache keys");
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

    private PivotOuterCacheTelemetry.Evaluation evaluate(QueryModel queryModel,
                                                         SemanticQueryRequest request,
                                                         SemanticRequestContext context) {
        return PivotOuterCacheTelemetry.evaluate("FactSalesQueryModel", queryModel, request, context,
                false, false, PivotOuterCacheTelemetry.CACHE_STAGE);
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
