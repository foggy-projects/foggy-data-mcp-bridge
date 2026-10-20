package com.foggyframework.dataset.model.engine.query;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;

/** Direction precedes native NULLS FIRST/LAST, but follows emulated null-rank expressions. */
public final class OrderBySqlRenderer {
    private OrderBySqlRenderer() {
    }

    public static String render(String expression, String direction, boolean nullFirst,
                                boolean nullLast, FDialect dialect) {
        String suffix = StringUtils.isEmpty(direction) ? "" : " " + direction;
        if (!nullFirst && !nullLast) {
            return expression + suffix;
        }
        if (dialect.supportsNativeNullsOrdering()) {
            return dialect.buildNullOrderClause(expression + suffix, nullFirst);
        }
        return dialect.buildNullOrderClause(expression, nullFirst) + suffix;
    }
}
