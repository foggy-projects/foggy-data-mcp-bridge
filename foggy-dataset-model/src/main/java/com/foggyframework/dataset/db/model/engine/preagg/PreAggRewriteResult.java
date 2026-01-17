package com.foggyframework.dataset.db.model.engine.preagg;

import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 预聚合查询重写结果
 * <p>
 * 包含重写后的 SQL 语句和参数。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Data
public class PreAggRewriteResult {

    /**
     * 是否应用了预聚合
     */
    private boolean applied;

    /**
     * 重写后的 SQL
     */
    private String sql;

    /**
     * SQL 参数
     */
    private List<Object> params = new ArrayList<>();

    /**
     * 使用的预聚合名称
     */
    private String preAggName;

    /**
     * 使用的预聚合
     */
    private PreAggregation preAggregation;

    /**
     * 是否需要 rollup
     */
    private boolean needsRollup;

    /**
     * 创建未应用预聚合的结果
     */
    public static PreAggRewriteResult notApplied() {
        PreAggRewriteResult result = new PreAggRewriteResult();
        result.setApplied(false);
        return result;
    }

    /**
     * 创建应用了预聚合的结果
     *
     * @param preAggregation 使用的预聚合
     * @param sql            重写后的 SQL
     * @param params         SQL 参数
     * @param needsRollup    是否需要 rollup
     * @return 重写结果
     */
    public static PreAggRewriteResult applied(PreAggregation preAggregation, String sql,
                                               List<Object> params, boolean needsRollup) {
        PreAggRewriteResult result = new PreAggRewriteResult();
        result.setApplied(true);
        result.setPreAggregation(preAggregation);
        result.setPreAggName(preAggregation.getName());
        result.setSql(sql);
        result.setParams(params != null ? params : new ArrayList<>());
        result.setNeedsRollup(needsRollup);
        return result;
    }

    @Override
    public String toString() {
        if (applied) {
            return "PreAggRewriteResult{" +
                    "applied=true" +
                    ", preAgg='" + preAggName + '\'' +
                    ", needsRollup=" + needsRollup +
                    ", sql='" + (sql != null && sql.length() > 100 ? sql.substring(0, 100) + "..." : sql) + '\'' +
                    '}';
        } else {
            return "PreAggRewriteResult{applied=false}";
        }
    }
}
