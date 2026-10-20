package com.foggyframework.dataset.model.engine.query;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.impl.query.DbQueryOrderColumnImpl;
import com.foggyframework.dataset.model.spi.DbColumn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Shared binding and first-wins ordering policy; callers supply orders in precedence order. */
public final class OrderByPlanner {
    public enum Mode { DETAIL, DISTINCT, GROUPED }

    private final Mode mode;
    private final OrderByFieldBindings bindings;
    private final Set<DbColumn> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<DbQueryOrderColumnImpl> orders = new ArrayList<>();

    public OrderByPlanner(Mode mode, OrderByFieldBindings bindings) {
        this.mode = mode;
        this.bindings = bindings;
    }

    public void explicit(String field, DbColumn source, String direction,
                         boolean nullLast, boolean nullFirst, boolean resultStage) {
        DbColumn bound = bindings.projection(field, source);
        if (bound == null && (mode == Mode.DETAIL || (mode == Mode.GROUPED && resultStage))) {
            bound = source;
        }
        if (bound == null) {
            String code = mode == Mode.DISTINCT
                    ? "DISTINCT_ORDER_BY_NOT_SELECTED" : "GROUP_BY_ORDER_BY_NOT_SELECTED";
            throw RX.throwAUserTip(code + ": ORDER BY field '" + field
                    + "' must be included in the query projection.");
        }
        add(bound, direction, nullLast, nullFirst);
    }

    public void optional(DbQueryOrderColumnImpl order) {
        DbColumn source = order.getSelectColumn();
        DbColumn bound = bindings.projection(source);
        if (bound == null && mode == Mode.DETAIL) {
            bound = source;
        }
        if (bound != null) {
            add(bound, order.getOrder(), order.isNullLast(), order.isNullFirst());
        }
    }

    private void add(DbColumn column, String direction, boolean nullLast, boolean nullFirst) {
        if (seen.add(OrderByFieldBindings.expressionIdentity(column))) {
            // Never keep or mutate the shared QM order object.
            orders.add(new DbQueryOrderColumnImpl(column, direction, nullLast, nullFirst));
        }
    }

    public List<DbQueryOrderColumnImpl> orders() {
        return List.copyOf(orders);
    }
}
