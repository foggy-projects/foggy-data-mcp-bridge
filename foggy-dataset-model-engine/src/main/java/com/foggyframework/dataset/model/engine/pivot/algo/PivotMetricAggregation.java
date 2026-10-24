package com.foggyframework.dataset.model.engine.pivot.algo;

import com.foggyframework.dataset.model.spi.DbAggregation;
import java.math.BigDecimal;
import java.util.*;

/** Reaggregation of already aggregated cells; never infer AVG or distinct counts from cell sums. */
public final class PivotMetricAggregation {
    private PivotMetricAggregation() {}

    public static Number aggregate(
            List<Map<String, Object>> rows, String metric, DbAggregation aggregation) {
        BigDecimal result = null;
        for (var row : rows)
            if (row.get(metric) instanceof Number n) {
                BigDecimal value = new BigDecimal(n.toString());
                result =
                        result == null
                                ? value
                                : switch (aggregation) {
                                    case MIN -> result.min(value);
                                    case MAX -> result.max(value);
                                    case SUM, COUNT -> result.add(value);
                                    default ->
                                            throw new IllegalArgumentException(
                                                    "PIVOT_REAGGREGATION_UNSUPPORTED: " + metric);
                                };
            }
        return result;
    }
}
