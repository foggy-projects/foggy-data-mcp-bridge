package com.foggyframework.dataset.db.model.engine.pivot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotMetricItem;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotOptions;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java-side producer for Python P0-8 real Pivot output replay.
 *
 * <p>The fixture uses isolated SQLite rows keyed by {@code order_status} so
 * Python can replay the same small fact set without importing Odoo or Java's
 * full ecommerce seed.</p>
 */
@DisplayName("JavaPivotOutputSnapshotTest - Python alignment P0-8")
class JavaPivotOutputSnapshotTest extends EcommerceTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final String TEST_MODEL = "FactSalesQueryModel";
    private static final String STATUS = "PY_ALIGN_PIVOT_OUT";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Test
    @DisplayName("writes java_pivot_output_snapshot_parity.json for Python replay")
    void shouldProducePivotOutputSnapshot() throws Exception {
        seedAlignmentRows();
        List<Map<String, Object>> cases = cases();

        Map<String, Object> snapshot = ordered();
        snapshot.put("schemaVersion", 1);
        snapshot.put("feature", "pivotOutput");
        snapshot.put("source", "JavaPivotOutputSnapshotTest");
        snapshot.put("seed", seedContract());
        snapshot.put("cases", cases);

        Path pythonTarget = Path.of(
                "..",
                "..",
                "foggy-data-mcp-bridge-python",
                "tests",
                "fixtures",
                "java_pivot_output_snapshot_parity.json"
        ).normalize();
        Files.createDirectories(pythonTarget.getParent());
        MAPPER.writeValue(pythonTarget.toFile(), snapshot);

        Path localCopy = Path.of("target", "parity", "java_pivot_output_snapshot_parity.json");
        Files.createDirectories(localCopy.getParent());
        MAPPER.writeValue(localCopy.toFile(), snapshot);
        assertTrue(Files.exists(pythonTarget),
                "snapshot was not written: " + pythonTarget.toAbsolutePath());
    }

    private List<Map<String, Object>> cases() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(flatRowsCase());
        out.add(flatRowsColumnsCase());
        out.add(gridRowsColumnsCase());
        out.add(flatRowsGrandTotalCase());
        out.add(flatRowsColumnsGrandTotalCase());
        out.add(gridRowsColumnsGrandTotalCase());
        out.add(flatRowsSubtotalsGrandTotalCase());
        out.add(gridRowsColumnsSubtotalsGrandTotalCase());
        out.add(flatRowsParentShareCase());
        out.add(gridRowsColumnsParentShareCase());
        return out;
    }

    private Map<String, Object> flatRowsCase() {
        PivotRequest pivot = pivot("flat",
                List.of(axis("product$categoryName")),
                null,
                List.of("salesAmount"));
        SemanticQueryResponse response = execute(pivot);
        List<Map<String, Object>> actual = canonicalFlatRows(response.getItems(), false, false);
        List<Map<String, Object>> expected = List.of(
                row("category", "Align-Clothing", "sales", 200),
                row("category", "Align-Electronics", "sales", 200)
        );
        assertEquals(expected, actual);

        Map<String, Object> c = ordered();
        c.put("id", "pivot-flat-rows");
        c.put("type", "flat-output");
        c.put("request", requestContract("flat", List.of("product$categoryName"), List.of(), List.of("salesAmount")));
        c.put("javaCanonical", actual);
        return c;
    }

    private Map<String, Object> flatRowsColumnsCase() {
        PivotRequest pivot = pivot("flat",
                List.of(axis("product$categoryName")),
                List.of(axis("salesDate$year")),
                List.of("salesAmount"));
        SemanticQueryResponse response = execute(pivot);
        List<Map<String, Object>> actual = canonicalFlatRows(response.getItems(), true, false);
        List<Map<String, Object>> expected = List.of(
                row("category", "Align-Clothing", "year", 2098, "sales", 200),
                row("category", "Align-Electronics", "year", 2099, "sales", 200)
        );
        assertEquals(expected, actual);

        Map<String, Object> c = ordered();
        c.put("id", "pivot-flat-rows-columns");
        c.put("type", "flat-output");
        c.put("request", requestContract("flat",
                List.of("product$categoryName"), List.of("salesDate$year"), List.of("salesAmount")));
        c.put("javaCanonical", actual);
        return c;
    }

    private Map<String, Object> gridRowsColumnsCase() {
        PivotRequest pivot = pivot("grid",
                List.of(axis("product$categoryName")),
                List.of(axis("salesDate$year")),
                List.of("salesAmount"));
        SemanticQueryResponse response = execute(pivot);
        List<Map<String, Object>> actual = canonicalGridCells(response.getItems(), false);
        List<Map<String, Object>> expected = List.of(
                row("category", "Align-Clothing", "year", 2098, "metric", "salesAmount", "value", 200),
                row("category", "Align-Clothing", "year", 2099, "metric", "salesAmount", "value", null),
                row("category", "Align-Electronics", "year", 2098, "metric", "salesAmount", "value", null),
                row("category", "Align-Electronics", "year", 2099, "metric", "salesAmount", "value", 200)
        );
        assertEquals(expected, actual);

        Map<String, Object> c = ordered();
        c.put("id", "pivot-grid-rows-columns");
        c.put("type", "grid-output");
        c.put("request", requestContract("grid",
                List.of("product$categoryName"), List.of("salesDate$year"), List.of("salesAmount")));
        c.put("javaCanonical", actual);
        return c;
    }

    private Map<String, Object> flatRowsGrandTotalCase() {
        PivotRequest pivot = pivot("flat",
                List.of(axis("product$categoryName")),
                null,
                List.of("salesAmount"),
                grandTotalOptions());
        SemanticQueryResponse response = execute(pivot);
        List<Map<String, Object>> actual = canonicalFlatRows(response.getItems(), false, false);
        List<Map<String, Object>> expected = List.of(
                row("category", "Align-Clothing", "sales", 200),
                row("category", "Align-Electronics", "sales", 200),
                row("category", "GRAND_TOTAL", "sales", 400)
        );
        assertEquals(expected, actual);

        Map<String, Object> c = ordered();
        c.put("id", "pivot-flat-rows-grand-total");
        c.put("type", "flat-output");
        c.put("request", requestContract("flat",
                List.of("product$categoryName"), List.of(), List.of("salesAmount"),
                row("grandTotal", true)));
        c.put("javaCanonical", actual);
        return c;
    }

    private Map<String, Object> flatRowsColumnsGrandTotalCase() {
        PivotRequest pivot = pivot("flat",
                List.of(axis("product$categoryName")),
                List.of(axis("salesDate$year")),
                List.of("salesAmount"),
                grandTotalOptions());
        SemanticQueryResponse response = execute(pivot);
        List<Map<String, Object>> actual = canonicalFlatRows(response.getItems(), true, false);
        List<Map<String, Object>> expected = List.of(
                row("category", "Align-Clothing", "year", 2098, "sales", 200),
                row("category", "Align-Electronics", "year", 2099, "sales", 200),
                row("category", "GRAND_TOTAL", "year", 2098, "sales", 200),
                row("category", "GRAND_TOTAL", "year", 2099, "sales", 200)
        );
        assertEquals(expected, actual);

        Map<String, Object> c = ordered();
        c.put("id", "pivot-flat-rows-columns-grand-total");
        c.put("type", "flat-output");
        c.put("request", requestContract("flat",
                List.of("product$categoryName"), List.of("salesDate$year"), List.of("salesAmount"),
                row("grandTotal", true)));
        c.put("javaCanonical", actual);
        return c;
    }

    private Map<String, Object> gridRowsColumnsGrandTotalCase() {
        PivotRequest pivot = pivot("grid",
                List.of(axis("product$categoryName")),
                List.of(axis("salesDate$year")),
                List.of("salesAmount"),
                grandTotalOptions());
        SemanticQueryResponse response = execute(pivot);
        List<Map<String, Object>> actual = canonicalGridCells(response.getItems(), false);
        List<Map<String, Object>> expected = List.of(
                row("category", "Align-Clothing", "year", 2098, "metric", "salesAmount", "value", 200),
                row("category", "Align-Clothing", "year", 2099, "metric", "salesAmount", "value", null),
                row("category", "Align-Electronics", "year", 2098, "metric", "salesAmount", "value", null),
                row("category", "Align-Electronics", "year", 2099, "metric", "salesAmount", "value", 200),
                row("category", "GRAND_TOTAL", "year", 2098, "metric", "salesAmount", "value", 200),
                row("category", "GRAND_TOTAL", "year", 2099, "metric", "salesAmount", "value", 200)
        );
        assertEquals(expected, actual);

        Map<String, Object> c = ordered();
        c.put("id", "pivot-grid-rows-columns-grand-total");
        c.put("type", "grid-output");
        c.put("request", requestContract("grid",
                List.of("product$categoryName"), List.of("salesDate$year"), List.of("salesAmount"),
                row("grandTotal", true)));
        c.put("javaCanonical", actual);
        return c;
    }

    private Map<String, Object> flatRowsSubtotalsGrandTotalCase() {
        PivotRequest pivot = pivot("flat",
                List.of(axis("product$categoryName"), axis("product$subCategoryName")),
                null,
                List.of("salesAmount"),
                subtotalsGrandTotalOptions());
        SemanticQueryResponse response = execute(pivot);
        List<Map<String, Object>> actual = canonicalFlatRows(response.getItems(), false, true);
        List<Map<String, Object>> expected = List.of(
                row("category", "Align-Clothing", "subCategory", "ALL", "sales", 200),
                row("category", "Align-Clothing", "subCategory", "Align-Clothing-Sub", "sales", 200),
                row("category", "Align-Electronics", "subCategory", "ALL", "sales", 200),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Alt", "sales", 50),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Sub", "sales", 150),
                row("category", "GRAND_TOTAL", "subCategory", "GRAND_TOTAL", "sales", 400)
        );
        assertEquals(expected, actual);

        Map<String, Object> c = ordered();
        c.put("id", "pivot-flat-rows-subtotals-grand-total");
        c.put("type", "flat-output");
        c.put("request", requestContract("flat",
                List.of("product$categoryName", "product$subCategoryName"), List.of(), List.of("salesAmount"),
                row("rowSubtotals", true, "grandTotal", true)));
        c.put("javaCanonical", actual);
        return c;
    }

    private Map<String, Object> gridRowsColumnsSubtotalsGrandTotalCase() {
        PivotRequest pivot = pivot("grid",
                List.of(axis("product$categoryName"), axis("product$subCategoryName")),
                List.of(axis("salesDate$year")),
                List.of("salesAmount"),
                subtotalsGrandTotalOptions());
        SemanticQueryResponse response = execute(pivot);
        List<Map<String, Object>> actual = canonicalGridCells(response.getItems(), true);
        List<Map<String, Object>> expected = List.of(
                row("category", "Align-Clothing", "subCategory", "ALL",
                        "year", 2098, "metric", "salesAmount", "value", 200),
                row("category", "Align-Clothing", "subCategory", "ALL",
                        "year", 2099, "metric", "salesAmount", "value", null),
                row("category", "Align-Clothing", "subCategory", "Align-Clothing-Sub",
                        "year", 2098, "metric", "salesAmount", "value", 200),
                row("category", "Align-Clothing", "subCategory", "Align-Clothing-Sub",
                        "year", 2099, "metric", "salesAmount", "value", null),
                row("category", "Align-Electronics", "subCategory", "ALL",
                        "year", 2098, "metric", "salesAmount", "value", null),
                row("category", "Align-Electronics", "subCategory", "ALL",
                        "year", 2099, "metric", "salesAmount", "value", 200),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Alt",
                        "year", 2098, "metric", "salesAmount", "value", null),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Alt",
                        "year", 2099, "metric", "salesAmount", "value", 50),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Sub",
                        "year", 2098, "metric", "salesAmount", "value", null),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Sub",
                        "year", 2099, "metric", "salesAmount", "value", 150),
                row("category", "GRAND_TOTAL", "subCategory", "GRAND_TOTAL",
                        "year", 2098, "metric", "salesAmount", "value", 200),
                row("category", "GRAND_TOTAL", "subCategory", "GRAND_TOTAL",
                        "year", 2099, "metric", "salesAmount", "value", 200)
        );
        assertEquals(expected, actual);

        Map<String, Object> c = ordered();
        c.put("id", "pivot-grid-rows-columns-subtotals-grand-total");
        c.put("type", "grid-output");
        c.put("request", requestContract("grid",
                List.of("product$categoryName", "product$subCategoryName"),
                List.of("salesDate$year"), List.of("salesAmount"),
                row("rowSubtotals", true, "grandTotal", true)));
        c.put("javaCanonical", actual);
        return c;
    }

    private Map<String, Object> flatRowsParentShareCase() {
        PivotRequest pivot = pivotItems("flat",
                List.of(axis("product$categoryName"), axis("product$subCategoryName")),
                null,
                List.of(PivotMetricItem.ofNative("salesAmount"), parentShareMetric("share", "salesAmount")));
        SemanticQueryResponse response = execute(pivot);
        List<Map<String, Object>> actual = canonicalFlatRows(response.getItems(), false, true, true);
        List<Map<String, Object>> expected = List.of(
                row("category", "Align-Clothing", "subCategory", "Align-Clothing-Sub",
                        "sales", 200, "share", 1),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Alt",
                        "sales", 50, "share", 0.25),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Sub",
                        "sales", 150, "share", 0.75)
        );
        assertEquals(expected, actual);

        Map<String, Object> c = ordered();
        c.put("id", "pivot-flat-rows-parent-share");
        c.put("type", "flat-output");
        c.put("request", requestContract("flat",
                List.of("product$categoryName", "product$subCategoryName"), List.of(),
                List.of("salesAmount", parentShareContract("share", "salesAmount"))));
        c.put("javaCanonical", actual);
        return c;
    }

    private Map<String, Object> gridRowsColumnsParentShareCase() {
        PivotRequest pivot = pivotItems("grid",
                List.of(axis("product$categoryName"), axis("product$subCategoryName")),
                List.of(axis("salesDate$year")),
                List.of(PivotMetricItem.ofNative("salesAmount"), parentShareMetric("share", "salesAmount")));
        SemanticQueryResponse response = execute(pivot);
        List<Map<String, Object>> actual = canonicalGridCells(response.getItems(), true);
        List<Map<String, Object>> expected = List.of(
                row("category", "Align-Clothing", "subCategory", "Align-Clothing-Sub",
                        "year", 2098, "metric", "salesAmount", "value", 200),
                row("category", "Align-Clothing", "subCategory", "Align-Clothing-Sub",
                        "year", 2098, "metric", "share", "value", 1),
                row("category", "Align-Clothing", "subCategory", "Align-Clothing-Sub",
                        "year", 2099, "metric", "salesAmount", "value", null),
                row("category", "Align-Clothing", "subCategory", "Align-Clothing-Sub",
                        "year", 2099, "metric", "share", "value", null),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Alt",
                        "year", 2098, "metric", "salesAmount", "value", null),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Alt",
                        "year", 2098, "metric", "share", "value", null),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Alt",
                        "year", 2099, "metric", "salesAmount", "value", 50),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Alt",
                        "year", 2099, "metric", "share", "value", 0.25),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Sub",
                        "year", 2098, "metric", "salesAmount", "value", null),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Sub",
                        "year", 2098, "metric", "share", "value", null),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Sub",
                        "year", 2099, "metric", "salesAmount", "value", 150),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Sub",
                        "year", 2099, "metric", "share", "value", 0.75)
        );
        assertEquals(expected, actual);

        Map<String, Object> c = ordered();
        c.put("id", "pivot-grid-rows-columns-parent-share");
        c.put("type", "grid-output");
        c.put("request", requestContract("grid",
                List.of("product$categoryName", "product$subCategoryName"),
                List.of("salesDate$year"),
                List.of("salesAmount", parentShareContract("share", "salesAmount"))));
        c.put("javaCanonical", actual);
        return c;
    }

    private SemanticQueryResponse execute(PivotRequest pivot) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);
        request.setSlice(List.of(slice("orderStatus", "=", STATUS)));
        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                TEST_MODEL, request, "execute", SemanticRequestContext.empty());
        assertNotNull(response);
        assertNotNull(response.getItems());
        assertFalse(response.getItems().isEmpty());
        return response;
    }

    private List<Map<String, Object>> canonicalFlatRows(
            List<Map<String, Object>> items,
            boolean includeYear,
            boolean includeSubcategory) {
        return canonicalFlatRows(items, includeYear, includeSubcategory, false);
    }

    private List<Map<String, Object>> canonicalFlatRows(
            List<Map<String, Object>> items,
            boolean includeYear,
            boolean includeSubcategory,
            boolean includeShare) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> row = ordered();
            row.put("category", pick(item, "product$categoryName", "一级品类名称"));
            if (includeSubcategory) {
                row.put("subCategory", pick(item, "product$subCategoryName", "二级品类名称"));
            }
            if (includeYear) {
                row.put("year", normalizeNumber(pick(item, "salesDate$year", "年")));
            }
            row.put("sales", normalizeNumber(pick(item, "salesAmount", "销售金额")));
            if (includeShare) {
                row.put("share", normalizeNumber(pick(item, "share")));
            }
            out.add(row);
        }
        out.sort(flatComparator(includeYear, includeSubcategory));
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> canonicalGridCells(List<Map<String, Object>> items, boolean includeSubcategory) {
        assertEquals(1, items.size());
        Map<String, Object> grid = items.get(0);
        assertEquals("grid", grid.get("format"));
        List<Map<String, Object>> rowHeaders = (List<Map<String, Object>>) grid.get("rowHeaders");
        List<Map<String, Object>> columnHeaders = (List<Map<String, Object>>) grid.get("columnHeaders");
        List<List<Object>> cells = (List<List<Object>>) grid.get("cells");

        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < rowHeaders.size(); i++) {
            for (int j = 0; j < columnHeaders.size(); j++) {
                Map<String, Object> row = ordered();
                row.put("category", pick(rowHeaders.get(i), "product$categoryName", "一级品类名称"));
                if (includeSubcategory) {
                    row.put("subCategory", pick(rowHeaders.get(i), "product$subCategoryName", "二级品类名称"));
                }
                row.put("year", normalizeNumber(pick(columnHeaders.get(j), "salesDate$year", "年")));
                row.put("metric", pick(columnHeaders.get(j), "metric"));
                row.put("value", normalizeNumber(cells.get(i).get(j)));
                out.add(row);
            }
        }
        out.sort(Comparator
                .comparing((Map<String, Object> row) -> String.valueOf(row.get("category")))
                .thenComparing(row -> String.valueOf(row.getOrDefault("subCategory", "")))
                .thenComparing(row -> ((Number) row.get("year")).intValue())
                .thenComparing(row -> String.valueOf(row.get("metric"))));
        return out;
    }

    private void seedAlignmentRows() {
        cleanupAlignmentRows();
        insertDate(20990101, "2099-01-01", 2099);
        insertDate(20980101, "2098-01-01", 2098);
        insertProduct(990001, "PY-ALIGN-ELECTRONICS", "Align Phone", "A1", "Align-Electronics",
                "Align-Electronics-Sub");
        insertProduct(990002, "PY-ALIGN-CLOTHING", "Align Coat", "A2", "Align-Clothing",
                "Align-Clothing-Sub");
        insertProduct(990003, "PY-ALIGN-ELECTRONICS-ALT", "Align Laptop", "A1", "Align-Electronics",
                "Align-Electronics-Alt");
        jdbcTemplate.update("""
                INSERT INTO fact_sales
                (order_id, order_line_no, date_key, product_key, customer_key, store_key, channel_key, promotion_key,
                 quantity, unit_price, unit_cost, discount_amount, sales_amount, cost_amount, profit_amount, tax_amount,
                 order_status, payment_method)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "PY_ALIGN_PIVOT_OUT_1", 1, 20990101, 990001, null, null, null, null,
                1, 100d, 60d, 0d, 100d, 60d, 40d, 0d, STATUS, "ALIGN");
        jdbcTemplate.update("""
                INSERT INTO fact_sales
                (order_id, order_line_no, date_key, product_key, customer_key, store_key, channel_key, promotion_key,
                 quantity, unit_price, unit_cost, discount_amount, sales_amount, cost_amount, profit_amount, tax_amount,
                 order_status, payment_method)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "PY_ALIGN_PIVOT_OUT_2", 1, 20990101, 990001, null, null, null, null,
                1, 50d, 30d, 0d, 50d, 30d, 20d, 0d, STATUS, "ALIGN");
        jdbcTemplate.update("""
                INSERT INTO fact_sales
                (order_id, order_line_no, date_key, product_key, customer_key, store_key, channel_key, promotion_key,
                 quantity, unit_price, unit_cost, discount_amount, sales_amount, cost_amount, profit_amount, tax_amount,
                 order_status, payment_method)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "PY_ALIGN_PIVOT_OUT_3", 1, 20980101, 990002, null, null, null, null,
                1, 200d, 120d, 0d, 200d, 120d, 80d, 0d, STATUS, "ALIGN");
        jdbcTemplate.update("""
                INSERT INTO fact_sales
                (order_id, order_line_no, date_key, product_key, customer_key, store_key, channel_key, promotion_key,
                 quantity, unit_price, unit_cost, discount_amount, sales_amount, cost_amount, profit_amount, tax_amount,
                 order_status, payment_method)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "PY_ALIGN_PIVOT_OUT_4", 1, 20990101, 990003, null, null, null, null,
                1, 50d, 30d, 0d, 50d, 30d, 20d, 0d, STATUS, "ALIGN");
    }

    private void cleanupAlignmentRows() {
        jdbcTemplate.update("DELETE FROM fact_sales WHERE order_status = ?", STATUS);
        jdbcTemplate.update("DELETE FROM dim_product WHERE product_key IN (?, ?, ?)", 990001, 990002, 990003);
        jdbcTemplate.update("DELETE FROM dim_date WHERE date_key IN (?, ?)", 20990101, 20980101);
    }

    private void insertDate(int key, String fullDate, int year) {
        jdbcTemplate.update("""
                INSERT INTO dim_date
                (date_key, full_date, year, quarter, month, month_name, week_of_year, day_of_month,
                 day_of_week, day_name, is_weekend, is_holiday, fiscal_year, fiscal_quarter)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, key, fullDate, year, 1, 1, "Jan", 1, 1, 1, "Monday", 0, 0, year, 1);
    }

    private void insertProduct(
            int key,
            String productId,
            String productName,
            String categoryId,
            String categoryName,
            String subCategoryName) {
        jdbcTemplate.update("""
                INSERT INTO dim_product
                (product_key, product_id, product_name, category_id, category_name, sub_category_id,
                 sub_category_name, brand, unit_price, unit_cost, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, key, productId, productName, categoryId, categoryName, categoryId + "-S-" + key,
                subCategoryName, "AlignBrand", 100d, 60d, "ACTIVE");
    }

    private Map<String, Object> seedContract() {
        Map<String, Object> seed = ordered();
        seed.put("slice", row("field", "orderStatus", "op", "=", "value", STATUS));
        seed.put("rows", List.of(
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Sub",
                        "year", 2099, "sales", 100),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Sub",
                        "year", 2099, "sales", 50),
                row("category", "Align-Clothing", "subCategory", "Align-Clothing-Sub",
                        "year", 2098, "sales", 200),
                row("category", "Align-Electronics", "subCategory", "Align-Electronics-Alt",
                        "year", 2099, "sales", 50)
        ));
        return seed;
    }

    private Map<String, Object> requestContract(
            String outputFormat,
            List<String> rows,
            List<String> columns,
            List<?> metrics) {
        return requestContract(outputFormat, rows, columns, metrics, null);
    }

    private Map<String, Object> requestContract(
            String outputFormat,
            List<String> rows,
            List<String> columns,
            List<?> metrics,
            Map<String, Object> options) {
        Map<String, Object> request = ordered();
        request.put("outputFormat", outputFormat);
        request.put("rows", rows);
        request.put("columns", columns);
        request.put("metrics", metrics);
        if (options != null) {
            request.put("options", options);
        }
        request.put("slice", List.of(row("field", "orderStatus", "op", "=", "value", STATUS)));
        return request;
    }

    private PivotRequest pivot(
            String outputFormat,
            List<AxisField> rows,
            List<AxisField> columns,
            List<String> metrics) {
        return pivot(outputFormat, rows, columns, metrics, null);
    }

    private PivotRequest pivot(
            String outputFormat,
            List<AxisField> rows,
            List<AxisField> columns,
            List<String> metrics,
            PivotOptions options) {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(rows);
        pivot.setColumns(columns);
        pivot.setMetrics(metrics);
        pivot.setOutputFormat(outputFormat);
        pivot.setOptions(options);
        return pivot;
    }

    private PivotRequest pivotItems(
            String outputFormat,
            List<AxisField> rows,
            List<AxisField> columns,
            List<PivotMetricItem> metrics) {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(rows);
        pivot.setColumns(columns);
        pivot.setMetricItems(metrics);
        pivot.setOutputFormat(outputFormat);
        return pivot;
    }

    private PivotMetricItem parentShareMetric(String name, String of) {
        PivotMetricItem item = new PivotMetricItem();
        item.setName(name);
        item.setType("parentShare");
        item.setOf(of);
        return item;
    }

    private Map<String, Object> parentShareContract(String name, String of) {
        return row("name", name, "type", "parentShare", "of", of);
    }

    private PivotOptions grandTotalOptions() {
        PivotOptions options = new PivotOptions();
        options.setGrandTotal(true);
        return options;
    }

    private PivotOptions subtotalsGrandTotalOptions() {
        PivotOptions options = grandTotalOptions();
        options.setRowSubtotals(true);
        return options;
    }

    private AxisField axis(String field) {
        AxisField axis = new AxisField();
        axis.setField(field);
        return axis;
    }

    private SemanticQueryRequest.SliceItem slice(String field, String op, Object value) {
        SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
        item.setField(field);
        item.setOp(op);
        item.setValue(value);
        return item;
    }

    private Object pick(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        throw new AssertionError("Missing any key " + List.of(keys) + " in " + row);
    }

    private Object normalizeNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            return value;
        }
        BigDecimal decimal = BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros();
        if (decimal.scale() <= 0) {
            return decimal.intValueExact();
        }
        return decimal.doubleValue();
    }

    private Comparator<Map<String, Object>> flatComparator(boolean includeYear, boolean includeSubcategory) {
        Comparator<Map<String, Object>> comparator =
                Comparator.comparing(row -> String.valueOf(row.get("category")));
        if (includeSubcategory) {
            comparator = comparator.thenComparing(row -> String.valueOf(row.get("subCategory")));
        }
        if (includeYear) {
            comparator = comparator.thenComparing(row -> ((Number) row.get("year")).intValue());
        }
        return comparator;
    }

    private static Map<String, Object> ordered() {
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> out = ordered();
        for (int i = 0; i < values.length; i += 2) {
            out.put(String.valueOf(values[i]), values[i + 1]);
        }
        return out;
    }
}
