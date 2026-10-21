package com.foggyframework.dataset.model.engine.query;

import com.foggyframework.dataset.model.impl.query.DbQueryColumnImpl;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.support.CalculatedDbColumn;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request-local lineage from SELECT sources to the expressions emitted by this stage.
 * Names are lookup labels, never expression identity. Only pure QM/request alias wrappers
 * are transparent; table-instance and expression decorators remain significant.
 */
public final class OrderByFieldBindings {
    private final Map<DbColumn, DbColumn> projections = new IdentityHashMap<>();
    private record Projection(DbColumn source, DbColumn output) {}
    private final Map<String, Projection> names = new HashMap<>();

    public OrderByFieldBindings(List<DbColumn> sources, List<DbColumn> outputs) {
        if (sources.size() != outputs.size()) {
            throw new IllegalArgumentException("ORDER BY projection lineage size mismatch");
        }
        for (int i = 0; i < sources.size(); i++) {
            DbColumn source = sources.get(i);
            DbColumn output = outputs.get(i);
            projections.putIfAbsent(expressionIdentity(source), output);
            projections.putIfAbsent(expressionIdentity(output), output);
            registerName(source.getName(), source, output);
            registerName(source.getAlias(), source, output);
        }
    }

    private void registerName(String name, DbColumn source, DbColumn output) {
        if (name != null) {
            names.putIfAbsent(name, new Projection(source, output));
        }
    }

    public DbColumn source(String field) {
        Projection named = names.get(field);
        return named == null ? null : named.source();
    }

    public DbColumn projection(String field, DbColumn source) {
        // An explicitly selected name identifies its own stage expression, even
        // when more than one projection originated from the same source.
        Projection named = field == null ? null : names.get(field);
        return named != null ? named.output() : projection(source);
    }

    public DbColumn projection(DbColumn source) {
        return source == null ? null : projections.get(expressionIdentity(source));
    }

    public static DbColumn expressionIdentity(DbColumn column) {
        while (true) {
            if (column instanceof DbQueryColumnImpl queryColumn) {
                column = queryColumn.getSelectColumn();
            } else if (column instanceof CalculatedDbColumn calculated
                    && calculated.getResultStageDepth() == 0
                    && !calculated.getSqlFragment().isHasAggregate()
                    && !calculated.getSqlFragment().isHasWindow()
                    && (calculated.getSqlFragment().getAggregationType() == null
                        || "NONE".equals(calculated.getSqlFragment().getAggregationType()))
                    && calculated.getSqlFragment().getDirectColumn() != null
                    && calculated.getSqlFragment().getDirectColumn() != column) {
                column = calculated.getSqlFragment().getDirectColumn();
            } else {
                return column;
            }
        }
    }
}
