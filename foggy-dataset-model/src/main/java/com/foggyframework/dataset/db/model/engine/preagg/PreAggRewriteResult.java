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
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>单表模式：仅使用预聚合表</li>
 *   <li>混合模式：预聚合表 UNION 原始表（需要二次聚合）</li>
 * </ul>
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
     * 是否为混合查询模式
     */
    private boolean hybridQuery;

    /**
     * 数据水位线（混合查询模式使用）
     */
    private Object watermark;

    /**
     * 创建未应用预聚合的结果
     */
    public static PreAggRewriteResult notApplied() {
        PreAggRewriteResult result = new PreAggRewriteResult();
        result.setApplied(false);
        return result;
    }

    /**
     * 创建应用了预聚合的结果（单表模式）
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
        result.setHybridQuery(false);
        return result;
    }

    /**
     * 创建混合查询模式的结果
     * <p>
     * 混合查询 SQL 结构：
     * <pre>
     * SELECT ... FROM (
     *   SELECT ... FROM preagg_table WHERE watermark_col < ?
     *   UNION ALL
     *   SELECT ... FROM source_table WHERE watermark_col >= ?
     * ) AS combined
     * GROUP BY ...
     * </pre>
     * </p>
     *
     * @param preAggregation 使用的预聚合
     * @param sql            重写后的 UNION SQL
     * @param params         SQL 参数
     * @param needsRollup    是否需要 rollup（粒度不完全匹配时需要）
     * @param watermark      物化历史的 exclusive upper bound
     * @return 重写结果
     */
    public static PreAggRewriteResult hybrid(PreAggregation preAggregation, String sql,
                                              List<Object> params, boolean needsRollup,
                                              Object watermark) {
        PreAggRewriteResult result = new PreAggRewriteResult();
        result.setApplied(true);
        result.setPreAggregation(preAggregation);
        result.setPreAggName(preAggregation.getName());
        result.setSql(sql);
        result.setParams(params != null ? params : new ArrayList<>());
        result.setNeedsRollup(needsRollup);
        result.setHybridQuery(true);
        result.setWatermark(watermark);
        return result;
    }

    @Override
    public String toString() {
        if (applied) {
            return "PreAggRewriteResult{" +
                    "applied=true" +
                    ", preAgg='" + preAggName + '\'' +
                    ", needsRollup=" + needsRollup +
                    ", hybridQuery=" + hybridQuery +
                    (watermark != null ? ", watermark=" + watermark : "") +
                    ", sql='" + (sql != null && sql.length() > 100 ? sql.substring(0, 100) + "..." : sql) + '\'' +
                    '}';
        } else {
            return "PreAggRewriteResult{applied=false}";
        }
    }
}
