package com.foggyframework.dataset.model.proxy;

import jakarta.persistence.criteria.JoinType;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Portable null-safe column joins retain ordinary SQL equality semantics. @since 9.3.0 */
class NullSafeJoinConditionTest {
    private final TableModelProxy left = new TableModelProxy("LeftModel", "l");
    private final TableModelProxy right = new TableModelProxy("RightModel", "r");

    @Test
    void nullSafeColumnsMatchOnlyEqualValuesOrTwoNulls() throws Exception {
        assertEquals(1, matches(null, null, true));
        assertEquals(0, matches(null, 1, true));
        assertEquals(0, matches(1, null, true));
        assertEquals(1, matches(1, 1, true));
        assertEquals(0, matches(1, 2, true));
    }

    @Test
    void ordinaryEqualityStillDoesNotMatchTwoNulls() throws Exception {
        assertEquals(0, matches(null, null, false));
        assertEquals(1, matches(1, 1, false));
        var condition = new JoinCondition(column(left, "join_key"), "=", column(right, "join_key"));
        assertEquals("l.join_key = r.join_key", condition.toSqlFragment());
        assertFalse(condition.isNullSafe());
    }

    @Test
    void nullSafeDisjunctionStaysGroupedInsideCompositeOnClause() throws Exception {
        var builder = new JoinBuilder(left, right, JoinType.LEFT);
        builder.invoke(null, "on", new Object[]{column(left, "tenant_id"), column(right, "tenant_id")});
        assertSame(builder, builder.invoke(null, "andNullSafe",
                new Object[]{column(left, "join_key"), column(right, "join_key")}));
        assertEquals("l.tenant_id = r.tenant_id AND (l.join_key = r.join_key OR "
                + "(l.join_key IS NULL AND r.join_key IS NULL))", builder.buildOnClause());
        assertTrue(builder.getConstantParameters().isEmpty());

        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("select count(*) from (select 1 tenant_id, null join_key) l "
                     + "join (select 2 tenant_id, null join_key) r on " + builder.buildOnClause())) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1), "two nulls must not bypass another join key");
        }
    }

    @Test
    void nullSafeDslAcceptsDimensionReferencesButRejectsConstantsAndInvalidArity() {
        var builder = new JoinBuilder(left, right, JoinType.LEFT);
        builder.invoke(null, "andNullSafe", new Object[]{new DimensionProxy(left, "customer"),
                new DimensionProxy(right, "customer")});
        assertEquals(1, builder.getConditions().size());
        assertTrue(builder.getConditions().get(0).isRightColumnRef());
        assertTrue(builder.getConditions().get(0).isEqualityComparison());
        assertThrows(IllegalArgumentException.class, () -> builder.invoke(null, "andNullSafe",
                new Object[]{column(left, "join_key"), 1}));
        assertThrows(IllegalArgumentException.class, () -> builder.invoke(null, "andNullSafe",
                new Object[]{1, column(right, "join_key")}));
        assertThrows(IllegalArgumentException.class, () -> builder.invoke(null, "andNullSafe",
                new Object[]{column(left, "join_key"), null}));
        assertThrows(IllegalArgumentException.class, () -> builder.invoke(null, "andNullSafe", new Object[]{}));
        assertThrows(IllegalArgumentException.class, () -> builder.invoke(null, "andNullSafe", null));
        assertEquals(1, builder.getConditions().size(), "invalid calls must not append a condition");
    }

    @Test
    void ordinaryConstantBindingRemainsUnchangedAlongsideNullSafeColumns() {
        var builder = new JoinBuilder(left, right, JoinType.LEFT);
        builder.andNullSafe(column(left, "join_key"), column(right, "join_key"));
        builder.invoke(null, "eq", new Object[]{column(right, "status"), "ACTIVE"});
        assertTrue(builder.buildOnClause().endsWith(" AND r.status = ?"));
        assertEquals(List.of("ACTIVE"), builder.getConstantParameters());
        assertThrows(IllegalArgumentException.class,
                () -> JoinCondition.nullSafeEquals(column(left, "join_key"), null));
    }

    private ColumnRef column(TableModelProxy model, String name) {
        return new ColumnRef(model, name);
    }

    private int matches(Integer leftValue, Integer rightValue, boolean nullSafe) throws Exception {
        var condition = nullSafe
                ? JoinCondition.nullSafeEquals(column(left, "join_key"), column(right, "join_key"))
                : new JoinCondition(column(left, "join_key"), "=", column(right, "join_key"));
        String sql = "select count(*) from (select " + leftValue + " join_key) l join (select "
                + rightValue + " join_key) r on " + condition.toSqlFragment();
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             var statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
