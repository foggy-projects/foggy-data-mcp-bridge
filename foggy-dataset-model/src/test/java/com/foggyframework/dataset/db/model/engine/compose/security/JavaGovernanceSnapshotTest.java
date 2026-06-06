package com.foggyframework.dataset.db.model.engine.compose.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.compose.compilation.ComposeCompileErrorCodes;
import com.foggyframework.dataset.db.model.engine.compose.compilation.ComposeCompileException;
import com.foggyframework.dataset.db.model.engine.compose.compilation.ComposeSqlCompiler;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.FieldAccessPermissionStep;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.PhysicalColumnMapping;
import com.foggyframework.dataset.db.model.spi.PhysicalColumnRef;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Java-side producer for Python P0-5/P0-6 governance neutral snapshot replay.
 *
 * <p>The first governance lane intentionally stays on engine-neutral contracts:
 * {@link ModelBinding} three-state semantics, compile-time fail-closed on
 * missing bindings, and per-base forwarding of fieldAccess / deniedColumns /
 * systemSlice into the v1.3 semantic-service boundary.</p>
 */
@DisplayName("JavaGovernanceSnapshotTest · Python alignment P0-5/P0-6")
class JavaGovernanceSnapshotTest {

    @Test
    @DisplayName("writes java_governance_snapshot_parity.json for Python replay")
    void shouldProduceGovernanceSnapshot() throws Exception {
        List<Map<String, Object>> cases = cases();
        for (Map<String, Object> c : cases) {
            assertJavaContract(c);
        }

        Map<String, Object> snapshot = ordered();
        snapshot.put("schemaVersion", 1);
        snapshot.put("feature", "governance");
        snapshot.put("source", "JavaGovernanceSnapshotTest");
        snapshot.put("cases", cases);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path pythonTarget = Path.of(
                "..",
                "..",
                "foggy-data-mcp-bridge-python",
                "tests",
                "fixtures",
                "java_governance_snapshot_parity.json"
        ).normalize();
        Files.createDirectories(pythonTarget.getParent());
        mapper.writeValue(pythonTarget.toFile(), snapshot);

        Path localCopy = Path.of("target", "parity", "java_governance_snapshot_parity.json");
        Files.createDirectories(localCopy.getParent());
        mapper.writeValue(localCopy.toFile(), snapshot);
        assertTrue(Files.exists(pythonTarget),
                "snapshot was not written: " + pythonTarget.toAbsolutePath());
    }

    private static void assertJavaContract(Map<String, Object> c) {
        String type = (String) c.get("type");
        switch (type) {
            case "binding-semantics" -> assertBindingSemantics(c);
            case "compile-forwarding" -> assertCompileForwarding(c);
            case "compile-error" -> assertCompileError(c);
            case "denied-column-mapping" -> assertDeniedColumnMapping(c);
            case "query-validation" -> assertQueryValidation(c);
            case "metadata-trimming" -> assertMetadataTrimmingContract(c);
            default -> fail("Unknown governance snapshot case type: " + type);
        }
    }

    private static void assertBindingSemantics(Map<String, Object> c) {
        ModelBinding binding = bindingFrom(c.get("binding"));
        @SuppressWarnings("unchecked")
        Map<String, Object> expected = (Map<String, Object>) c.get("expected");

        if (Boolean.TRUE.equals(expected.get("fieldAccessIsNull"))) {
            assertNull(binding.fieldAccess(), "[" + c.get("id") + "] fieldAccess should be null");
        } else {
            assertNotNull(binding.fieldAccess(), "[" + c.get("id") + "] fieldAccess should be present");
            assertEquals(expected.get("fieldAccess"), binding.fieldAccess());
        }
        assertEquals(((Number) expected.get("deniedColumnsSize")).intValue(), binding.deniedColumns().size());
        assertEquals(((Number) expected.get("systemSliceSize")).intValue(), binding.systemSlice().size());
    }

    private static void assertCompileForwarding(Map<String, Object> c) {
        CapturingSemanticService svc = new CapturingSemanticService();
        ComposedSql sql = compile(
                planFrom(c.get("plan")),
                Map.of("FactSalesModel", bindingFrom(c.get("binding"))),
                svc);

        @SuppressWarnings("unchecked")
        Map<String, Object> expected = (Map<String, Object>) c.get("expected");
        for (String marker : stringList(expected.get("sqlMarkers"))) {
            assertTrue(sql.getSql().contains(marker),
                    "[" + c.get("id") + "] SQL marker missing: " + marker + "\n" + sql.getSql());
        }
        assertEquals(expected.get("forwardedColumns"), svc.request.getColumns());
        assertEquals(expected.get("forwardedFieldAccess"),
                sorted(svc.context.getFieldAccess()));
        assertEquals(expected.get("forwardedDeniedColumns"),
                deniedColumnsToMaps(svc.context.getDeniedColumns()));
        assertEquals(expected.get("forwardedSystemSlice"),
                sliceToMaps(svc.context.getSystemSlice()));
    }

    private static void assertCompileError(Map<String, Object> c) {
        @SuppressWarnings("unchecked")
        Map<String, Object> expected = (Map<String, Object>) c.get("expected");
        try {
            compile(planFrom(c.get("plan")), Map.of(), new CapturingSemanticService());
            fail("Expected Java governance compile case to fail: " + c.get("id"));
        } catch (RuntimeException ex) {
            ComposeCompileException ce = assertInstanceOf(ComposeCompileException.class, ex);
            assertEquals(expected.get("errorCode"), ce.code());
            assertEquals(expected.get("phase"), ce.phase());
        }
    }

    private static void assertDeniedColumnMapping(Map<String, Object> c) {
        @SuppressWarnings("unchecked")
        Map<String, Object> expected = (Map<String, Object>) c.get("expected");
        Set<String> actual = NEUTRAL_MAPPING.toDeniedQmFields(deniedColumnsFrom(c.get("deniedColumns")));
        assertEquals(stringList(expected.get("deniedQmFields")), sorted(actual),
                "[" + c.get("id") + "] deniedColumns mapping mismatch");
    }

    private static void assertQueryValidation(Map<String, Object> c) {
        @SuppressWarnings("unchecked")
        Map<String, Object> expected = (Map<String, Object>) c.get("expected");
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setColumns(stringList(c.get("columns")));
        request.setOrderBy(orderByFrom(c.get("orderBy")));

        ModelResultContext ctx = new ModelResultContext();
        ctx.setRequest(PagingRequest.buildPagingRequest(request, 100));
        ctx.setQueryModel(neutralQueryModel());
        ctx.setDeniedColumns(deniedColumnsFrom(c.get("deniedColumns")));

        try {
            new FieldAccessPermissionStep().beforeQuery(ctx);
            assertTrue(Boolean.TRUE.equals(expected.get("passes")),
                    "[" + c.get("id") + "] expected denied column validation to fail");
        } catch (RuntimeException ex) {
            assertFalse(Boolean.TRUE.equals(expected.get("passes")),
                    "[" + c.get("id") + "] expected denied column validation to pass: " + ex.getMessage());
            for (String marker : stringList(expected.get("messageMarkers"))) {
                assertTrue(ex.getMessage().contains(marker),
                        "[" + c.get("id") + "] error marker missing: " + marker + " in " + ex.getMessage());
            }
        }
    }

    private static void assertMetadataTrimmingContract(Map<String, Object> c) {
        @SuppressWarnings("unchecked")
        Map<String, Object> expected = (Map<String, Object>) c.get("expected");
        Set<String> effective = new LinkedHashSet<>(NEUTRAL_MAPPING.getAllQmFieldNames());
        effective.removeAll(NEUTRAL_MAPPING.toDeniedQmFields(deniedColumnsFrom(c.get("deniedColumns"))));
        if (c.containsKey("visibleFields")) {
            effective.retainAll(stringList(c.get("visibleFields")));
        }

        for (String field : stringList(expected.get("presentFields"))) {
            assertTrue(effective.contains(field),
                    "[" + c.get("id") + "] expected metadata field to remain: " + field);
        }
        for (String field : stringList(expected.get("absentFields"))) {
            assertFalse(effective.contains(field),
                    "[" + c.get("id") + "] expected metadata field to be trimmed: " + field);
        }
    }

    private static ComposedSql compile(QueryPlan plan,
                                       Map<String, ModelBinding> bindings,
                                       CapturingSemanticService svc) {
        return ComposeSqlCompiler.compilePlanToSql(
                plan,
                context(),
                ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(svc)
                        .bindings(bindings)
                        .dialect("mysql8")
                        .build());
    }

    private static ComposeQueryContext context() {
        return ComposeQueryContext.builder()
                .principal(Principal.builder()
                        .userId("snapshot-user")
                        .tenantId("demo")
                        .roles(List.of("analyst"))
                        .build())
                .namespace("demo")
                .traceId("java-governance-snapshot")
                .authorityResolver(request -> AuthorityResolution.builder()
                        .bindings(Map.of())
                        .build())
                .build();
    }

    private static QueryPlan planFrom(Object raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) raw;
        return BaseModelPlan.builder()
                .model((String) node.get("model"))
                .columns(list(node.get("columns")))
                .slice(list(node.get("slice")))
                .build();
    }

    private static ModelBinding bindingFrom(Object raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) raw;
        ModelBinding.Builder b = ModelBinding.builder();
        if (node.containsKey("fieldAccess")) {
            b.fieldAccess(stringList(node.get("fieldAccess")));
        }
        b.deniedColumns(deniedColumnsFrom(node.get("deniedColumns")));
        b.systemSlice(systemSliceFrom(node.get("systemSlice")));
        return b.build();
    }

    private static List<DeniedPhysicalColumn> deniedColumnsFrom(Object raw) {
        List<DeniedPhysicalColumn> out = new ArrayList<>();
        for (Object item : list(raw)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) item;
            out.add(new DeniedPhysicalColumn(
                    (String) m.get("schema"),
                    (String) m.get("table"),
                    (String) m.get("column")));
        }
        return out;
    }

    private static List<SliceRequestDef> systemSliceFrom(Object raw) {
        List<SliceRequestDef> out = new ArrayList<>();
        for (Object item : list(raw)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) item;
            out.add(new SliceRequestDef(
                    (String) m.get("field"),
                    (String) m.getOrDefault("op", m.get("type")),
                    m.get("value")));
        }
        return out;
    }

    private static List<Map<String, Object>> deniedColumnsToMaps(List<DeniedPhysicalColumn> deniedColumns) {
        if (deniedColumns == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (DeniedPhysicalColumn dc : deniedColumns) {
            Map<String, Object> m = ordered();
            if (dc.getSchema() != null) {
                m.put("schema", dc.getSchema());
            }
            m.put("table", dc.getTable());
            m.put("column", dc.getColumn());
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> sliceToMaps(List<SliceRequestDef> slices) {
        if (slices == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (SliceRequestDef slice : slices) {
            Map<String, Object> m = ordered();
            m.put("field", slice.getField());
            m.put("op", slice.getOp());
            m.put("value", slice.getValue());
            out.add(m);
        }
        return out;
    }

    private static List<String> sorted(Set<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static List<Map<String, Object>> cases() {
        return List.of(
                caseOf(
                        "binding-field-access-null-is-unrestricted",
                        "binding-semantics",
                        binding(null, null, null),
                        null,
                        expectedBinding(true, null, 0, 0)),
                caseOf(
                        "binding-empty-field-access-denies-all",
                        "binding-semantics",
                        binding(List.of(), null, null),
                        null,
                        expectedBinding(false, List.of(), 0, 0)),
                caseOf(
                        "compile-forwards-binding-governance-to-v13-boundary",
                        "compile-forwarding",
                        binding(
                                List.of("orderStatus$caption", "salesAmount"),
                                List.of(deniedColumn(null, "fact_sales", "secret_margin")),
                                List.of(slice("tenant_id", "=", "demo"))),
                        base("FactSalesModel",
                                List.of("orderStatus$caption", "salesAmount"),
                                List.of(slice("orderStatus$caption", "=", "COMPLETED"))),
                        expectedForwarding()),
                caseOf(
                        "missing-visible-model-binding-fails-closed",
                        "compile-error",
                        null,
                        base("HiddenModel", List.of("secretAmount"), null),
                        expectedError(ComposeCompileErrorCodes.MISSING_BINDING,
                                ComposeCompileErrorCodes.PHASE_PLAN_LOWER)),
                deniedMappingCase(
                        "denied-physical-measure-maps-to-qm-field",
                        List.of(deniedColumn("public", "fact_sales", "sales_amount")),
                        List.of("salesAmount")),
                deniedMappingCase(
                        "denied-physical-dimension-property-maps-to-qm-field",
                        List.of(deniedColumn(null, "dim_product", "category_name")),
                        List.of("product$categoryName")),
                deniedMappingCase(
                        "denied-unknown-physical-column-maps-to-empty-set",
                        List.of(deniedColumn(null, "dim_customer", "email")),
                        List.of()),
                queryValidationCase(
                        "query-denied-measure-select-refused",
                        List.of("product$caption", "salesAmount"),
                        null,
                        List.of(deniedColumn(null, "fact_sales", "sales_amount")),
                        false,
                        List.of("salesAmount")),
                queryValidationCase(
                        "query-denied-dimension-property-select-refused",
                        List.of("product$caption", "product$categoryName"),
                        null,
                        List.of(deniedColumn(null, "dim_product", "category_name")),
                        false,
                        List.of("product$categoryName")),
                queryValidationCase(
                        "query-denied-unrelated-column-passes",
                        List.of("product$caption", "salesAmount"),
                        null,
                        List.of(deniedColumn(null, "dim_customer", "email")),
                        true,
                        List.of()),
                queryValidationCase(
                        "query-denied-order-by-refused",
                        List.of("product$caption"),
                        List.of(orderBy("salesAmount", "DESC")),
                        List.of(deniedColumn(null, "fact_sales", "sales_amount")),
                        false,
                        List.of("salesAmount")),
                metadataCase(
                        "metadata-denied-measure-trims-sales-amount",
                        "FactSalesModel",
                        null,
                        List.of(deniedColumn(null, "fact_sales", "sales_amount")),
                        List.of("product$caption", "product$categoryName"),
                        List.of("salesAmount")),
                metadataCase(
                        "metadata-visible-and-denied-intersection",
                        "FactSalesModel",
                        List.of("product$caption", "salesAmount", "costAmount"),
                        List.of(deniedColumn(null, "fact_sales", "sales_amount")),
                        List.of("product$caption", "costAmount"),
                        List.of("salesAmount", "product$categoryName"))
        );
    }

    private static Map<String, Object> caseOf(String id, String type,
                                              Map<String, Object> binding,
                                              Map<String, Object> plan,
                                              Map<String, Object> expected) {
        Map<String, Object> out = ordered();
        out.put("id", id);
        out.put("type", type);
        if (binding != null) {
            out.put("binding", binding);
        }
        if (plan != null) {
            out.put("plan", plan);
        }
        out.put("expected", expected);
        return out;
    }

    private static Map<String, Object> deniedMappingCase(String id,
                                                         List<Map<String, Object>> deniedColumns,
                                                         List<String> deniedQmFields) {
        Map<String, Object> out = ordered();
        out.put("id", id);
        out.put("type", "denied-column-mapping");
        out.put("model", "FactSalesModel");
        out.put("deniedColumns", deniedColumns);
        out.put("expected", expectedDeniedMapping(deniedQmFields));
        return out;
    }

    private static Map<String, Object> queryValidationCase(String id,
                                                           List<String> columns,
                                                           List<Map<String, Object>> orderBy,
                                                           List<Map<String, Object>> deniedColumns,
                                                           boolean passes,
                                                           List<String> messageMarkers) {
        Map<String, Object> out = ordered();
        out.put("id", id);
        out.put("type", "query-validation");
        out.put("model", "FactSalesModel");
        out.put("columns", columns);
        out.put("orderBy", orderBy == null ? List.of() : orderBy);
        out.put("deniedColumns", deniedColumns);
        out.put("expected", expectedQueryValidation(passes, messageMarkers));
        return out;
    }

    private static Map<String, Object> metadataCase(String id,
                                                    String model,
                                                    List<String> visibleFields,
                                                    List<Map<String, Object>> deniedColumns,
                                                    List<String> presentFields,
                                                    List<String> absentFields) {
        Map<String, Object> out = ordered();
        out.put("id", id);
        out.put("type", "metadata-trimming");
        out.put("model", model);
        if (visibleFields != null) {
            out.put("visibleFields", visibleFields);
        }
        out.put("deniedColumns", deniedColumns);
        out.put("expected", expectedMetadata(presentFields, absentFields));
        return out;
    }

    private static Map<String, Object> binding(List<String> fieldAccess,
                                               List<Map<String, Object>> deniedColumns,
                                               List<Map<String, Object>> systemSlice) {
        Map<String, Object> out = ordered();
        if (fieldAccess != null) {
            out.put("fieldAccess", fieldAccess);
        }
        out.put("deniedColumns", deniedColumns == null ? List.of() : deniedColumns);
        out.put("systemSlice", systemSlice == null ? List.of() : systemSlice);
        return out;
    }

    private static Map<String, Object> base(String model,
                                            List<String> columns,
                                            List<Map<String, Object>> slice) {
        Map<String, Object> out = ordered();
        out.put("type", "base");
        out.put("model", model);
        out.put("columns", columns);
        if (slice != null) {
            out.put("slice", slice);
        }
        return out;
    }

    private static Map<String, Object> deniedColumn(String schema, String table, String column) {
        Map<String, Object> out = ordered();
        if (schema != null) {
            out.put("schema", schema);
        }
        out.put("table", table);
        out.put("column", column);
        return out;
    }

    private static Map<String, Object> orderBy(String field, String dir) {
        Map<String, Object> out = ordered();
        out.put("field", field);
        out.put("dir", dir);
        return out;
    }

    private static Map<String, Object> slice(String field, String op, Object value) {
        Map<String, Object> out = ordered();
        out.put("field", field);
        out.put("op", op);
        out.put("value", value);
        return out;
    }

    private static Map<String, Object> expectedBinding(boolean fieldAccessIsNull,
                                                       List<String> fieldAccess,
                                                       int deniedColumnsSize,
                                                       int systemSliceSize) {
        Map<String, Object> out = ordered();
        out.put("fieldAccessIsNull", fieldAccessIsNull);
        if (!fieldAccessIsNull) {
            out.put("fieldAccess", fieldAccess);
        }
        out.put("deniedColumnsSize", deniedColumnsSize);
        out.put("systemSliceSize", systemSliceSize);
        return out;
    }

    private static Map<String, Object> expectedForwarding() {
        Map<String, Object> out = ordered();
        out.put("sqlMarkers", List.of("WITH ", "cte_0 AS", "__governance_stub__"));
        out.put("forwardedColumns", List.of("orderStatus$caption", "salesAmount"));
        out.put("forwardedFieldAccess", List.of("orderStatus$caption", "salesAmount"));
        out.put("forwardedDeniedColumns", List.of(deniedColumn(null, "fact_sales", "secret_margin")));
        out.put("forwardedSystemSlice", List.of(slice("tenant_id", "=", "demo")));
        return out;
    }

    private static Map<String, Object> expectedError(String code, String phase) {
        Map<String, Object> out = ordered();
        out.put("errorCode", code);
        out.put("phase", phase);
        return out;
    }

    private static Map<String, Object> expectedDeniedMapping(List<String> deniedQmFields) {
        Map<String, Object> out = ordered();
        out.put("deniedQmFields", deniedQmFields);
        return out;
    }

    private static Map<String, Object> expectedQueryValidation(boolean passes, List<String> messageMarkers) {
        Map<String, Object> out = ordered();
        out.put("passes", passes);
        out.put("messageMarkers", messageMarkers);
        return out;
    }

    private static Map<String, Object> expectedMetadata(List<String> presentFields, List<String> absentFields) {
        Map<String, Object> out = ordered();
        out.put("presentFields", presentFields);
        out.put("absentFields", absentFields);
        return out;
    }

    private static List<OrderRequestDef> orderByFrom(Object raw) {
        List<OrderRequestDef> out = new ArrayList<>();
        for (Object item : list(raw)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) item;
            OrderRequestDef order = new OrderRequestDef();
            order.setField((String) m.get("field"));
            order.setDir((String) m.getOrDefault("dir", "ASC"));
            out.add(order);
        }
        return out;
    }

    private static QueryModel neutralQueryModel() {
        return (QueryModel) Proxy.newProxyInstance(
                JavaGovernanceSnapshotTest.class.getClassLoader(),
                new Class<?>[]{QueryModel.class},
                (proxy, method, args) -> {
                    if ("getPhysicalColumnMapping".equals(method.getName())) {
                        return NEUTRAL_MAPPING;
                    }
                    if ("toString".equals(method.getName())) {
                        return "NeutralGovernanceQueryModel";
                    }
                    Class<?> returnType = method.getReturnType();
                    if (boolean.class.equals(returnType)) {
                        return false;
                    }
                    if (int.class.equals(returnType)) {
                        return 0;
                    }
                    return null;
                });
    }

    private static List<Object> list(Object raw) {
        if (raw == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Object> out = (List<Object>) raw;
        return out;
    }

    private static List<String> stringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<String> out = (List<String>) raw;
        return out;
    }

    private static Map<String, Object> ordered() {
        return new LinkedHashMap<>();
    }

    private static final PhysicalColumnMapping NEUTRAL_MAPPING = new NeutralPhysicalColumnMapping();

    private static final class NeutralPhysicalColumnMapping implements PhysicalColumnMapping {
        private final Map<String, List<PhysicalColumnRef>> qmToPhysical = new LinkedHashMap<>();
        private final Map<String, List<String>> physicalToQm = new LinkedHashMap<>();

        private NeutralPhysicalColumnMapping() {
            add("orderId", "fact_sales", "order_id");
            add("salesAmount", "fact_sales", "sales_amount");
            add("costAmount", "fact_sales", "cost_amount");
            add("discountAmount", "fact_sales", "discount_amount");
            add("product$id", "fact_sales", "product_key");
            add("product$id", "dim_product", "product_key");
            add("product$caption", "dim_product", "product_name");
            add("product$categoryName", "dim_product", "category_name");
            add("customer$customerType", "dim_customer", "customer_type");
        }

        private void add(String qmField, String table, String column) {
            PhysicalColumnRef ref = new PhysicalColumnRef(table, column);
            qmToPhysical.computeIfAbsent(qmField, ignored -> new ArrayList<>()).add(ref);
            physicalToQm.computeIfAbsent(ref.toKey(), ignored -> new ArrayList<>()).add(qmField);
        }

        @Override
        public List<PhysicalColumnRef> getPhysicalColumns(String qmFieldName) {
            return qmToPhysical.getOrDefault(qmFieldName, List.of());
        }

        @Override
        public List<String> getQmFieldNames(String table, String column) {
            return physicalToQm.getOrDefault(table + "." + column, List.of());
        }

        @Override
        public Set<String> toDeniedQmFields(List<DeniedPhysicalColumn> deniedPhysicalColumns) {
            Set<String> out = new LinkedHashSet<>();
            for (DeniedPhysicalColumn dc : deniedPhysicalColumns) {
                if (dc.getTable() == null || dc.getTable().isBlank()
                        || dc.getColumn() == null || dc.getColumn().isBlank()) {
                    continue;
                }
                out.addAll(getQmFieldNames(dc.getTable(), dc.getColumn()));
            }
            return out;
        }

        @Override
        public Set<String> getAllQmFieldNames() {
            return qmToPhysical.keySet();
        }

        @Override
        public Set<String> getAllPhysicalTables() {
            return physicalToQm.keySet().stream()
                    .map(key -> key.substring(0, key.indexOf('.')))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private static final class CapturingSemanticService implements SemanticQueryServiceV3 {
        private SemanticQueryRequest request;
        private SemanticRequestContext context;

        @Override
        public SqlGenerationResult generateSql(String model, SemanticQueryRequest request,
                                               SemanticRequestContext context) {
            this.request = request;
            this.context = context;
            if (!"FactSalesModel".equals(model)) {
                throw new RuntimeException("Model not found: " + model);
            }
            return new SqlGenerationResult(
                    "SELECT 1 AS __governance_stub__",
                    List.of(),
                    null,
                    List.of());
        }

        @Override
        public List<Map<String, Object>> executeSql(String sql, List<Object> params,
                                                    String routeModel) {
            return List.of();
        }

        @Override
        public SemanticQueryResponse queryModel(String model, SemanticQueryRequest request,
                                                String mode, SemanticRequestContext context) {
            throw new UnsupportedOperationException("queryModel is not used by snapshot compile tests");
        }

        @Override
        public SemanticQueryResponse validateQuery(String model, SemanticQueryRequest request,
                                                   SemanticRequestContext context) {
            throw new UnsupportedOperationException("validateQuery is not used by snapshot compile tests");
        }
    }
}
