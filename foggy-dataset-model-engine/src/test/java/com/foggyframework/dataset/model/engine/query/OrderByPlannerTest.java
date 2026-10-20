package com.foggyframework.dataset.model.engine.query;

import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.model.impl.query.DbQueryColumnImpl;
import com.foggyframework.dataset.model.impl.query.DbQueryOrderColumnImpl;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.DbColumnType;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.support.AggregationDbColumn;
import com.foggyframework.dataset.model.spi.support.SimpleQueryObject;
import com.foggyframework.dataset.model.spi.support.SimpleSqlJdbcColumn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.Types;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderByPlannerTest {
    private DbColumn column(String tableAlias, String name) {
        return new SimpleSqlJdbcColumn(SimpleQueryObject.of("orders", tableAlias, null),
                new SqlColumn(name, name, Types.INTEGER), name, name, name);
    }

    private DbColumn alias(DbColumn source, String name) {
        return new DbQueryColumnImpl(source, name, name, name);
    }

    @ParameterizedTest @EnumSource(OrderByPlanner.Mode.class)
    void explicitOrderOverridesDefaultWithoutMutatingIt(OrderByPlanner.Mode mode) {
        DbColumn source = column("a", "id"), selected = alias(source, "publicId");
        var planner = new OrderByPlanner(mode, new OrderByFieldBindings(List.of(selected), List.of(selected)));
        var defaultOrder = new DbQueryOrderColumnImpl(source, "desc", false, true);
        planner.explicit("publicId", selected, "asc", true, false, false);
        planner.explicit("otherAlias", alias(source, "otherAlias"), "desc", false, true, false);
        planner.optional(defaultOrder);
        assertEquals(1, planner.orders().size());
        assertEquals("asc", planner.orders().get(0).getOrder());
        assertTrue(planner.orders().get(0).isNullLast());
        assertFalse(planner.orders().get(0).isNullFirst());
        assertEquals("desc", defaultOrder.getOrder());
        assertSame(source, defaultOrder.getSelectColumn());
    }

    @Test void inheritedScriptOrderKeepsExistingPrecedenceAndIsCopied() {
        var source = column("a", "id");
        var planner = new OrderByPlanner(OrderByPlanner.Mode.DETAIL,
                new OrderByFieldBindings(List.of(source), List.of(source)));
        var inherited = new DbQueryOrderColumnImpl(source, "desc");
        planner.optional(inherited);
        planner.explicit("id", source, "asc", false, false, false);
        assertEquals(1, planner.orders().size());
        assertEquals("desc", planner.orders().get(0).getOrder());
        assertNotSame(inherited, planner.orders().get(0));
    }

    @Test void detailRetainsUnprojectedOrdersAndDistinctRejectsThem() {
        var source = column("a", "id");
        var bindings = new OrderByFieldBindings(List.of(), List.of());
        var detail = new OrderByPlanner(OrderByPlanner.Mode.DETAIL, bindings);
        detail.explicit("id", source, "asc", false, false, false);
        assertSame(source, detail.orders().get(0).getSelectColumn());
        for (var mode : List.of(OrderByPlanner.Mode.DISTINCT, OrderByPlanner.Mode.GROUPED)) {
            var planner = new OrderByPlanner(mode, bindings);
            planner.optional(new DbQueryOrderColumnImpl(source, "asc"));
            assertTrue(planner.orders().isEmpty());
            assertThrows(RuntimeException.class,
                    () -> planner.explicit("id", source, "asc", false, false, false));
        }
    }

    @Test void sameNamesAcrossTableInstancesAreNotDeduplicated() {
        var left = column("left", "id");
        var right = column("right", "id");
        var planner = new OrderByPlanner(OrderByPlanner.Mode.DETAIL,
                new OrderByFieldBindings(List.of(), List.of()));
        planner.explicit("left.id", left, "asc", false, false, false);
        planner.explicit("right.id", right, "desc", false, false, false);
        assertEquals(2, planner.orders().size());
    }

    @Test void differentAggregateExpressionsKeepDistinctBindings() {
        var source = column("a", "amount");
        var sum = new AggregationDbColumn(source.getQueryObject(), "sumAmount", "SUM(a.amount)",
                DbColumnType.NUMBER, DbAggregation.SUM);
        var avg = new AggregationDbColumn(source.getQueryObject(), "avgAmount", "AVG(a.amount)",
                DbColumnType.NUMBER, DbAggregation.AVG);
        var bindings = new OrderByFieldBindings(List.of(alias(source, "sumAmount"), alias(source, "avgAmount")),
                List.of(sum, avg));
        var planner = new OrderByPlanner(OrderByPlanner.Mode.GROUPED, bindings);
        planner.explicit("sumAmount", source, "asc", false, false, false);
        planner.explicit("avgAmount", source, "desc", false, false, false);
        assertEquals(List.of(sum, avg), planner.orders().stream().map(DbQueryOrderColumnImpl::getSelectColumn).toList());
    }
}
