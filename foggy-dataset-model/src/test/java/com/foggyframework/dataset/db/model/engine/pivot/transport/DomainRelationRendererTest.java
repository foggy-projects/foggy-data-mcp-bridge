package com.foggyframework.dataset.db.model.engine.pivot.transport;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.dialect.MysqlDialect;
import com.foggyframework.dataset.db.dialect.PostgresDialect;
import com.foggyframework.dataset.db.dialect.SqliteDialect;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class DomainRelationRendererTest {

    private DomainTransportPlan createSingleFieldPlan() {
        return DomainTransportPlan.builder()
                .fields(Collections.singletonList(new DomainTransportField("category")))
                .tuples(Arrays.asList(
                        new DomainTransportTuple(Collections.singletonList("A")),
                        new DomainTransportTuple(Collections.singletonList("B")),
                        new DomainTransportTuple(Collections.singletonList(null))
                ))
                .build();
    }

    private DomainTransportPlan createTupleFieldPlan() {
        return DomainTransportPlan.builder()
                .fields(Arrays.asList(new DomainTransportField("category"), new DomainTransportField("product")))
                .tuples(Arrays.asList(
                        new DomainTransportTuple(Arrays.asList("A", "p1")),
                        new DomainTransportTuple(Arrays.asList("A", null)),
                        new DomainTransportTuple(Arrays.asList("B", "p2"))
                ))
                .build();
    }

    private DomainTransportPlan createLargePlan(int tupleCount) {
        DomainTransportField field = new DomainTransportField("id");
        DomainTransportTuple tuple = new DomainTransportTuple(Collections.singletonList(1));
        return DomainTransportPlan.builder()
                .fields(Collections.singletonList(field))
                .tuples(Collections.nCopies(tupleCount, tuple))
                .build();
    }

    @Test
    void testPostgresCteRenderer_SingleField() {
        FDialect dialect = new PostgresDialect();
        DomainRelationRenderer renderer = new PostgresCteDomainRenderer();
        DomainRelationRenderResult result = renderer.render(dialect, null, createSingleFieldPlan());

        assertEquals(DomainTransportPlacement.CTE, result.getPlacement());
        assertTrue(result.getSqlFragment().contains("_pivot_domain_transport(\"category\") AS (\n  VALUES (?),\n         (?),\n         (?)"));
        assertTrue(result.getJoinPredicate().contains("_base.\"category\" IS NOT DISTINCT FROM _d.\"category\""));
        assertEquals(3, result.getParams().size());
        assertNull(result.getParams().get(2));
    }

    @Test
    void testSqliteCteRenderer_TupleField() {
        FDialect dialect = new SqliteDialect();
        DomainRelationRenderer renderer = new SqliteCteDomainRenderer();
        DomainRelationRenderResult result = renderer.render(dialect, null, createTupleFieldPlan());

        assertEquals(DomainTransportPlacement.CTE, result.getPlacement());
        assertTrue(result.getSqlFragment().contains("VALUES (?, ?),\n         (?, ?),\n         (?, ?)"));
        assertTrue(result.getJoinPredicate().contains("_base.\"category\" IS _d.\"category\""));
        assertTrue(result.getJoinPredicate().contains("_base.\"product\" IS _d.\"product\""));
        assertEquals(6, result.getParams().size());
    }

    @Test
    void testMysql8ValuesRenderer_VersionGate() {
        FDialect dialect = new MysqlDialect();
        DomainRelationRenderer renderer = new Mysql8ValuesDomainRenderer();

        assertThrows(DomainTransportRefusalException.class, () -> renderer.render(dialect, "8.0.18", createSingleFieldPlan()));
        assertThrows(DomainTransportRefusalException.class, () -> renderer.render(dialect, "8.0.9-10", createSingleFieldPlan()));

        DomainRelationRenderResult result = renderer.render(dialect, "8.0.19-commercial", createSingleFieldPlan());
        assertEquals(DomainTransportPlacement.CTE, result.getPlacement());
        assertTrue(result.getSqlFragment().contains("VALUES ROW(?),\n         ROW(?),\n         ROW(?)"));
        assertTrue(result.getJoinPredicate().contains("_base.`category` <=> _d.`category`"));
    }

    @Test
    void testMysql57DerivedTableRenderer() {
        FDialect dialect = new MysqlDialect();
        DomainRelationRenderer renderer = new Mysql57DerivedTableDomainRenderer();
        DomainRelationRenderResult result = renderer.render(dialect, null, createSingleFieldPlan());

        assertEquals(DomainTransportPlacement.DERIVED_TABLE, result.getPlacement());
        assertTrue(result.getSqlFragment().contains("SELECT ? AS `category`"));
        assertTrue(result.getSqlFragment().contains("UNION ALL\n  SELECT ?"));
        assertTrue(result.getJoinPredicate().contains("_base.`category` <=> _d.`category`"));
        assertEquals(3, result.getParams().size());
    }

    @Test
    void testMysql57DerivedTableRenderer_ThresholdRefusal() {
        FDialect dialect = new MysqlDialect();
        DomainRelationRenderer renderer = new Mysql57DerivedTableDomainRenderer();

        DomainTransportPlan largePlan = createLargePlan(2001); // Exceeds MAX_TUPLES = 2000
        DomainTransportRefusalException exception = assertThrows(DomainTransportRefusalException.class, () -> renderer.render(dialect, null, largePlan));
        assertTrue(exception.getMessage().contains("tuple limit exceeded"));
    }

    @Test
    void testUnsupportedRenderer() {
        DomainRelationRenderer renderer = new UnsupportedDomainRenderer();
        assertThrows(DomainTransportRefusalException.class, () -> renderer.render(new MysqlDialect(), null, createSingleFieldPlan()));
    }

    @Test
    void testTupleArityMismatchRefuses() {
        DomainTransportPlan invalidPlan = DomainTransportPlan.builder()
                .fields(Arrays.asList(new DomainTransportField("category"), new DomainTransportField("product")))
                .tuples(Collections.singletonList(new DomainTransportTuple(Collections.singletonList("A"))))
                .build();

        DomainRelationRenderer renderer = new PostgresCteDomainRenderer();
        DomainTransportRefusalException exception = assertThrows(
                DomainTransportRefusalException.class,
                () -> renderer.render(new PostgresDialect(), null, invalidPlan));
        assertTrue(exception.getMessage().contains("arity mismatch"));
    }
}
