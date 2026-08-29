package com.foggyframework.dataset.model.engine.total;

import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.DbColumnType;
import com.foggyframework.dataset.model.spi.support.AggregationDbColumn;

import java.util.List;

/** Creates hidden aggregate-state projections consumed by totalData rendering. */
public final class AggregateStateColumnFactory {

    public void append(List<DbColumn> baseColumns,
                       TotalDataAggregatePlan.AggregateStateSpec state,
                       List<Object> params) {
        String source = state.source().sql();
        DbColumnType type = state.type() == null ? DbColumnType.NUMBER : state.type();
        if (state.aggregation() == DbAggregation.AVG) {
            baseColumns.add(new AggregationDbColumn(
                    null, state.sumAlias(), "SUM(" + source + ")", type, DbAggregation.SUM));
            params.addAll(state.source().values());
            baseColumns.add(new AggregationDbColumn(
                    null, state.countAlias(), "COUNT(" + source + ")",
                    DbColumnType.INTEGER, DbAggregation.COUNT));
            params.addAll(state.source().values());
            return;
        }
        String declare = switch (state.aggregation()) {
            case COUNT -> "*".equals(source) ? "COUNT(*)" : "COUNT(" + source + ")";
            case SUM -> "SUM(" + source + ")";
            case MIN -> "MIN(" + source + ")";
            case MAX, PK -> "MAX(" + source + ")";
            default -> throw new IllegalStateException(
                    "Unsupported totalData state: " + state.aggregation());
        };
        baseColumns.add(new AggregationDbColumn(
                null, state.valueAlias(), declare, type, state.aggregation()));
        params.addAll(state.source().values());
    }
}
