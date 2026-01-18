package com.foggyframework.dataset.db.model.engine.preagg;

import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import lombok.Getter;

/**
 * 预聚合匹配结果
 * <p>
 * 包含匹配的预聚合信息以及是否需要进行 rollup 聚合。
 * </p>
 * <p>
 * 支持三种查询模式：
 * <ul>
 *   <li>FULL：完全使用预聚合表</li>
 *   <li>HYBRID：混合查询（预聚合表 + 原始表 UNION）</li>
 *   <li>NONE：不使用预聚合</li>
 * </ul>
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Getter
public class PreAggregationMatchResult {

    /**
     * 查询模式
     */
    public enum QueryMode {
        /** 完全使用预聚合表 */
        FULL,
        /** 混合查询：预聚合表 + 原始表 UNION */
        HYBRID,
        /** 不使用预聚合 */
        NONE
    }

    /**
     * 是否匹配成功
     */
    private final boolean matched;

    /**
     * 匹配的预聚合（如果匹配成功）
     */
    private final PreAggregation preAggregation;

    /**
     * 是否需要 rollup（二次聚合）
     * <p>
     * 当查询粒度粗于预聚合粒度时，需要 rollup。
     * 例如：预聚合是日粒度，查询是月粒度，需要按月 rollup。
     * </p>
     */
    private final boolean needsRollup;

    /**
     * 查询模式
     */
    private final QueryMode queryMode;

    /**
     * 数据水位线（用于混合查询）
     * <p>
     * 混合查询时，watermark 表示预聚合表已处理的最大时间戳。
     * 小于等于 watermark 的数据从预聚合表查询，大于 watermark 的数据从原始表查询。
     * </p>
     */
    private final Object watermark;

    /**
     * 匹配分数（用于调试和日志）
     */
    private final int score;

    /**
     * 未匹配的原因（如果未匹配）
     */
    private final String reason;

    private PreAggregationMatchResult(boolean matched, PreAggregation preAggregation,
                                       boolean needsRollup, QueryMode queryMode,
                                       Object watermark, int score, String reason) {
        this.matched = matched;
        this.preAggregation = preAggregation;
        this.needsRollup = needsRollup;
        this.queryMode = queryMode;
        this.watermark = watermark;
        this.score = score;
        this.reason = reason;
    }

    /**
     * 创建匹配成功的结果（完全使用预聚合）
     *
     * @param preAggregation 匹配的预聚合
     * @param needsRollup    是否需要 rollup
     * @param score          匹配分数
     * @return 匹配结果
     */
    public static PreAggregationMatchResult matched(PreAggregation preAggregation,
                                                     boolean needsRollup, int score) {
        return new PreAggregationMatchResult(true, preAggregation, needsRollup,
                QueryMode.FULL, null, score, null);
    }

    /**
     * 创建混合查询模式的结果
     * <p>
     * 当预聚合数据不完整时，需要同时从预聚合表和原始表获取数据。
     * </p>
     *
     * @param preAggregation 匹配的预聚合
     * @param needsRollup    是否需要 rollup
     * @param watermark      数据水位线
     * @param score          匹配分数
     * @return 匹配结果
     */
    public static PreAggregationMatchResult hybrid(PreAggregation preAggregation,
                                                    boolean needsRollup,
                                                    Object watermark,
                                                    int score) {
        return new PreAggregationMatchResult(true, preAggregation, needsRollup,
                QueryMode.HYBRID, watermark, score, null);
    }

    /**
     * 创建未匹配的结果
     *
     * @param reason 未匹配原因
     * @return 匹配结果
     */
    public static PreAggregationMatchResult noMatch(String reason) {
        return new PreAggregationMatchResult(false, null, false,
                QueryMode.NONE, null, 0, reason);
    }

    /**
     * 创建未匹配的结果（无预聚合配置）
     *
     * @return 匹配结果
     */
    public static PreAggregationMatchResult noMatch() {
        return new PreAggregationMatchResult(false, null, false,
                QueryMode.NONE, null, 0, "No pre-aggregation available");
    }

    /**
     * 获取预聚合名称（如果匹配成功）
     */
    public String getPreAggName() {
        return preAggregation != null ? preAggregation.getName() : null;
    }

    /**
     * 判断是否为混合查询模式
     */
    public boolean isHybridQuery() {
        return queryMode == QueryMode.HYBRID;
    }

    /**
     * 判断是否为完全预聚合模式
     */
    public boolean isFullPreAggQuery() {
        return queryMode == QueryMode.FULL;
    }

    @Override
    public String toString() {
        if (matched) {
            return "PreAggMatchResult{" +
                    "matched=true" +
                    ", preAgg='" + getPreAggName() + '\'' +
                    ", needsRollup=" + needsRollup +
                    ", queryMode=" + queryMode +
                    (watermark != null ? ", watermark=" + watermark : "") +
                    ", score=" + score +
                    '}';
        } else {
            return "PreAggMatchResult{matched=false, reason='" + reason + "'}";
        }
    }
}
