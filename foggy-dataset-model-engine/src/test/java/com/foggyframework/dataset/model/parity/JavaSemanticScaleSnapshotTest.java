package com.foggyframework.dataset.model.parity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.impl.SemanticScaleSqlSupport;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java-side producer for Python P0-32 semanticScaleFactor snapshot replay.
 *
 * <p>The fixture focuses on cross-runtime contracts that should stay stable:
 * SQL semantic-unit rewriting, formula-backed fields, metadata, helper literal
 * formatting, and fail-closed invalid carrier-column validation.</p>
 */
@DisplayName("JavaSemanticScaleSnapshotTest · Python alignment P0-32")
class JavaSemanticScaleSnapshotTest extends EcommerceTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final String QUERY_MODEL = "FactSalesSemanticScaleQueryModel";
    private static final String FORMULA_QUERY_MODEL = "FactSalesSemanticScaleFormulaQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Resource
    private SemanticServiceV3 semanticServiceV3;

    @Test
    @DisplayName("writes java_semantic_scale_snapshot_parity.json for Python replay")
    void shouldProduceSemanticScaleSnapshot() throws Exception {
        List<Map<String, Object>> cases = List.of(
                helperLiteralCase(),
                dimensionPropertySqlCase(),
                measureHavingSqlCase(),
                calculatedFieldSqlCase(),
                formulaPropertySqlCase(),
                metadataCase(),
                invalidCarrierColumnCase()
        );

        Map<String, Object> snapshot = ordered();
        snapshot.put("schemaVersion", 1);
        snapshot.put("feature", "semanticScaleFactor");
        snapshot.put("source", "JavaSemanticScaleSnapshotTest");
        snapshot.put("cases", cases);

        Path localArtifact = Path.of("target", "parity", "java_semantic_scale_snapshot_parity.json");
        Files.createDirectories(localArtifact.getParent());
        MAPPER.writeValue(localArtifact.toFile(), snapshot);
        assertTrue(Files.exists(localArtifact),
                "snapshot was not written: " + localArtifact.toAbsolutePath());
    }

    private Map<String, Object> helperLiteralCase() {
        assertEquals("((t.amount) / 100.0)",
                SemanticScaleSqlSupport.scaledDeclare("t.amount", new BigDecimal("100")));
        assertEquals("((t.amount) / 2.5)",
                SemanticScaleSqlSupport.scaledDeclare("t.amount", new BigDecimal("2.50")));

        Map<String, Object> c = ordered();
        c.put("id", "helper-literal-contract");
        c.put("type", "helper");
        c.put("expected", ordered(
                "scaled100", "((t.amount) / 100.0)",
                "scaled250", "((t.amount) / 2.5)"
        ));
        return c;
    }

    private Map<String, Object> dimensionPropertySqlCase() {
        SemanticQueryRequest request = request(List.of("orderId", "product$unitPriceYuan"));
        return sqlCase(
                "dimension-property-sql",
                QUERY_MODEL,
                request,
                List.of("unit_price", "/ 100.0"),
                List.of("((dp.unit_price) / 100.0)"),
                List.of(),
                List.of()
        );
    }

    private Map<String, Object> measureHavingSqlCase() {
        SemanticQueryRequest request = request(List.of("orderId", "sum(salesAmountYuan) as totalSalesAmountYuan"));
        request.setGroupBy(List.of(new SemanticQueryRequest.GroupByItem("orderId", null)));
        request.setHaving(List.of(slice("totalSalesAmountYuan", ">", 1000)));
        return sqlCase(
                "measure-having-sql",
                QUERY_MODEL,
                request,
                List.of("SUM", "sales_amount", "/ 100.0", "having"),
                List.of("SUM(((t.sales_amount) / 100.0))", "totalSalesAmountYuan", "HAVING"),
                List.of(1000),
                List.of(1000)
        );
    }

    private Map<String, Object> calculatedFieldSqlCase() {
        SemanticQueryRequest request = request(List.of("orderId", "salesAmountYuan", "salesAmountPlusTen"));
        CalculatedFieldDef calculated = new CalculatedFieldDef();
        calculated.setName("salesAmountPlusTen");
        calculated.setExpression("salesAmountYuan + 10");
        request.setCalculatedFields(List.of(calculated));
        return sqlCase(
                "calculated-field-sql",
                QUERY_MODEL,
                request,
                List.of("sales_amount", "/ 100.0", "+ 10"),
                List.of("((t.sales_amount) / 100.0)", "+ ?"),
                List.of(),
                List.of(10)
        );
    }

    private Map<String, Object> formulaPropertySqlCase() {
        SemanticQueryRequest request = request(List.of("orderId", "salesAmountFormulaLeafYuan"));
        request.setSlice(List.of(slice("salesAmountFormulaLeafYuan", ">", 1000)));
        return sqlCase(
                "formula-property-sql",
                FORMULA_QUERY_MODEL,
                request,
                List.of("sales_amount", "+ 2", "/ 100.0", "where"),
                List.of("((t.sales_amount + 2) / 100.0)", "WHERE"),
                List.of(1000),
                List.of(1000)
        );
    }

    private Map<String, Object> metadataCase() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(List.of(QUERY_MODEL));

        SemanticMetadataResponse response = semanticServiceV3.getMetadata(
                request, "json", SemanticRequestContext.empty());
        assertNotNull(response);
        assertNotNull(response.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) response.getData().get("fields");
        assertNotNull(fields);
        for (String fieldName : List.of("salesAmountYuan", "product$unitPriceYuan")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> field = (Map<String, Object>) fields.get(fieldName);
            assertNotNull(field, "metadata field missing: " + fieldName);
            assertEquals("100", String.valueOf(field.get("semanticScaleFactor")));
            assertEquals("CNY", field.get("semanticUnit"));
            assertEquals("元", field.get("semanticUnitLabel"));
        }

        Map<String, Object> c = ordered();
        c.put("id", "metadata-semantic-unit");
        c.put("type", "metadata");
        c.put("model", QUERY_MODEL);
        c.put("fields", List.of("salesAmountYuan", "product$unitPriceYuan"));
        c.put("expected", ordered(
                "semanticScaleFactor", "100",
                "semanticUnit", "CNY",
                "semanticUnitLabel", "元"
        ));
        return c;
    }

    private Map<String, Object> invalidCarrierColumnCase() {
        RuntimeException ex = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> tableModelLoaderManager.load("FactSalesSemanticScaleSqlExpressionInvalidModel"));
        String message = ex.getMessage().toLowerCase(Locale.ROOT);
        assertTrue(message.contains("column"), "error should mention column: " + ex.getMessage());

        Map<String, Object> c = ordered();
        c.put("id", "invalid-carrier-column-fail-closed");
        c.put("type", "model-load-error");
        c.put("field", "salesAmountYuan");
        c.put("invalidColumn", "sales_amount + 0");
        c.put("expected", ordered("errorMarkers", List.of("column")));
        return c;
    }

    private Map<String, Object> sqlCase(String id,
                                        String model,
                                        SemanticQueryRequest request,
                                        List<String> javaSqlMarkers,
                                        List<String> pythonSqlMarkers,
                                        List<Object> javaExpectedParams,
                                        List<Object> pythonExpectedParams) {
        SqlGenerationResult result = semanticQueryServiceV3.generateSql(
                model, request, SemanticRequestContext.empty());
        assertNotNull(result, "generateSql returned null for " + id);
        assertNotNull(result.getSql(), "SQL is null for " + id);
        for (String marker : javaSqlMarkers) {
            assertTrue(result.getSql().contains(marker),
                    "[" + id + "] Java SQL marker missing: " + marker + "\n" + result.getSql());
        }
        assertEquals(paramStrings(javaExpectedParams),
                paramStrings(result.getParams() != null ? result.getParams() : List.of()));

        Map<String, Object> c = ordered();
        c.put("id", id);
        c.put("type", "sql");
        c.put("model", model);
        c.put("request", requestContract(request));
        c.put("expected", ordered(
                "javaSqlMarkers", javaSqlMarkers,
                "pythonSqlMarkers", pythonSqlMarkers,
                "javaParams", javaExpectedParams,
                "pythonParams", pythonExpectedParams
        ));
        return c;
    }

    private static SemanticQueryRequest request(List<String> columns) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(columns);
        return request;
    }

    private static SemanticQueryRequest.SliceItem slice(String field, String op, Object value) {
        SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
        item.setField(field);
        item.setOp(op);
        item.setValue(value);
        return item;
    }

    private static Map<String, Object> requestContract(SemanticQueryRequest request) {
        Map<String, Object> out = ordered();
        if (request.getColumns() != null) {
            out.put("columns", request.getColumns());
        }
        if (request.getSlice() != null) {
            out.put("slice", sliceContract(request.getSlice()));
        }
        if (request.getHaving() != null) {
            out.put("having", sliceContract(request.getHaving()));
        }
        if (request.getGroupBy() != null) {
            out.put("groupBy", request.getGroupBy().stream()
                    .map(SemanticQueryRequest.GroupByItem::getField)
                    .toList());
        }
        if (request.getCalculatedFields() != null) {
            out.put("calculatedFields", request.getCalculatedFields().stream()
                    .map(field -> ordered("name", field.getName(), "expression", field.getExpression()))
                    .toList());
        }
        return out;
    }

    private static List<Map<String, Object>> sliceContract(List<SemanticQueryRequest.SliceItem> items) {
        return items.stream()
                .map(item -> ordered("field", item.getField(), "op", item.getOp(), "value", item.getValue()))
                .toList();
    }

    private static List<String> paramStrings(List<?> params) {
        return params.stream()
                .map(JavaSemanticScaleSnapshotTest::paramString)
                .toList();
    }

    private static String paramString(Object param) {
        if (param instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (param instanceof Number) {
            try {
                return new BigDecimal(param.toString()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException ignored) {
                return String.valueOf(param);
            }
        }
        return String.valueOf(param);
    }

    private static Map<String, Object> ordered() {
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> out = ordered();
        for (int i = 0; i < kv.length; i += 2) {
            out.put((String) kv[i], kv[i + 1]);
        }
        return out;
    }
}
