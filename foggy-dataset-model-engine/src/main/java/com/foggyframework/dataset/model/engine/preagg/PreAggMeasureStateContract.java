package com.foggyframework.dataset.model.engine.preagg;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;

import java.util.Map;

/**
 * Internal physical-state naming contract shared by the engine and pre-aggregation addon.
 *
 * <p>AVG uses two independently mergeable columns derived from the configured measure
 * column. Existing AVG materializations must be rebuilt when adopting this contract.</p>
 */
public final class PreAggMeasureStateContract {
    private static final String AVG_SUM_SUFFIX = "__sum";
    private static final String AVG_COUNT_SUFFIX = "__count";

    private PreAggMeasureStateContract() {
    }

    public static MeasureState resolve(PreAggregation preAgg, String measureName) {
        if (preAgg == null || StringUtils.isEmpty(measureName)) {
            throw new IllegalArgumentException(
                    "Pre-aggregation and measure name are required");
        }
        Map<String, DbAggregation> aggregations = preAgg.getMeasureAggregations();
        DbAggregation aggregation = aggregations == null
                ? null : aggregations.get(measureName);
        if (aggregation == null) {
            throw new IllegalArgumentException(
                    "No materialized aggregation is declared for " + measureName);
        }
        Map<String, String> columnNames = preAgg.getMeasureColumnNames();
        String configuredColumn = columnNames == null
                ? null : columnNames.get(measureName);
        if (StringUtils.isEmpty(configuredColumn)) {
            throw new IllegalArgumentException(
                    "No explicit materialized measure column is declared for " + measureName);
        }
        if (aggregation == DbAggregation.AVG) {
            return new MeasureState(
                    aggregation,
                    null,
                    configuredColumn + AVG_SUM_SUFFIX,
                    configuredColumn + AVG_COUNT_SUFFIX);
        }
        return new MeasureState(aggregation, configuredColumn, null, null);
    }

    public record MeasureState(DbAggregation aggregation,
                               String valueColumn,
                               String sumColumn,
                               String countColumn) {
        public boolean isAverage() {
            return aggregation == DbAggregation.AVG;
        }
    }
}
