package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Physical carrier names alone cannot prove formula join-key lineage. @since 9.3.0 */
class AggregateJoinKeyLineageTest extends EcommerceTestSupport {
    private static final String MODEL = "JoinKeyLineageQueryModel";
    @Resource private SystemBundlesContext bundles;
    @Resource private SqlFormulaService formulas;

    @Test
    void warehouseFilterDoesNotPushIntoFormulaScopeKey() {
        var engine = query(List.of(slice("tenantId", 1), slice("stockHouseId", 10)));
        String sql = normalize(engine.getSql());
        assertTrue(sql.contains("agg_src.stock_house_id = ?"), sql);
        assertFalse(sql.contains("agg_src.scope_org_id = ?"), sql);
        assertSingleWarehouseFacts(engine);
    }

    @Test
    void formulaScopeFilterDoesNotPushIntoWarehouseKey() {
        var engine = query(List.of(slice("tenantId", 1), slice("scopeOrgId", 110)));
        String sql = normalize(engine.getSql());
        assertTrue(sql.contains("agg_src.scope_org_id = ?"), sql);
        assertFalse(sql.contains("agg_src.stock_house_id = ?"), sql);
        assertSingleWarehouseFacts(engine);
    }

    @Test
    void ordinaryPhysicalAliasesStillPushOnlyToTheirEquivalentKey() {
        var engine = query(List.of(slice("tenantAlias", 1), slice("stockHouseAlias", 10)));
        String sql = normalize(engine.getSql());
        assertTrue(sql.contains("agg_src.stock_house_id = ?"), sql);
        assertFalse(sql.contains("agg_src.scope_org_id = ?"), sql);
        assertEquals(2, occurrences(sql, "agg_src.tenant_id = ?"), sql);
        assertSingleWarehouseFacts(engine);
    }

    @Test
    void tenantFilterStillReachesBothRelations() {
        var engine = query(List.of(slice("tenantId", 1)));
        String sql = normalize(engine.getSql());
        assertEquals(2, occurrences(sql, "agg_src.tenant_id = ?"), sql);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(engine.getSql(), engine.getValues().toArray());
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(row -> ((Number) row.get("houseQuantity")).intValue()
                == ((Number) row.get("scopeQuantity")).intValue()));
    }

    private void assertSingleWarehouseFacts(JdbcModelQueryEngine engine) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(engine.getSql(), engine.getValues().toArray());
        assertEquals(1, rows.size());
        assertEquals(5, ((Number) rows.get(0).get("houseQuantity")).intValue());
        assertEquals(5, ((Number) rows.get(0).get("scopeQuantity")).intValue());
    }

    private JdbcModelQueryEngine query(List<SliceRequestDef> slices) {
        var request = new DbQueryRequestDef();
        request.setQueryModel(MODEL);
        request.setColumns(List.of("id", "houseQuantity", "scopeQuantity"));
        request.setSlice(slices);
        var engine = new JdbcModelQueryEngine(getQueryModel(MODEL), formulas);
        engine.analysisQueryRequest(bundles, request);
        return engine;
    }

    private SliceRequestDef slice(String field, Object value) {
        var slice = new SliceRequestDef();
        slice.setField(field); slice.setOp("="); slice.setValue(value);
        return slice;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
