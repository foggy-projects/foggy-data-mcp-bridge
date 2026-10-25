package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.dataset.model.spi.JdbcQueryModel;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Detects aggregate GROUP_CONCAT alias filters that need member-level
 * semantics before SQL generation.
 */
@Component
@Order(9)
public class AggregateMemberFilterRewriteStep implements DataSetResultStep {

    @Override
    public java.util.Optional<String> pivotCacheScope(ModelResultContext context) {
        return java.util.Optional.of("engine-governed-v1");
    }

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        if (ctx == null || ctx.isSkipQuery() || !(ctx.getQueryModel() instanceof JdbcQueryModel jdbcQueryModel)) {
            return CONTINUE;
        }
        AggregateMemberFilterPlanner.plan(ctx, jdbcQueryModel);
        return CONTINUE;
    }
}
