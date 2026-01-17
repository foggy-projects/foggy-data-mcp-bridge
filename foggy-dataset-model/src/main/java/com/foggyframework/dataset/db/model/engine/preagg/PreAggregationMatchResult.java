package com.foggyframework.dataset.db.model.engine.preagg;

import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import lombok.Getter;

/**
 * 预聚合匹配结果
 * <p>
 * 包含匹配的预聚合信息以及是否需要进行 rollup 聚合。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Getter
public class PreAggregationMatchResult {

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
     * 匹配分数（用于调试和日志）
     */
    private final int score;

    /**
     * 未匹配的原因（如果未匹配）
     */
    private final String reason;

    private PreAggregationMatchResult(boolean matched, PreAggregation preAggregation,
                                       boolean needsRollup, int score, String reason) {
        this.matched = matched;
        this.preAggregation = preAggregation;
        this.needsRollup = needsRollup;
        this.score = score;
        this.reason = reason;
    }

    /**
     * 创建匹配成功的结果
     *
     * @param preAggregation 匹配的预聚合
     * @param needsRollup    是否需要 rollup
     * @param score          匹配分数
     * @return 匹配结果
     */
    public static PreAggregationMatchResult matched(PreAggregation preAggregation,
                                                     boolean needsRollup, int score) {
        return new PreAggregationMatchResult(true, preAggregation, needsRollup, score, null);
    }

    /**
     * 创建未匹配的结果
     *
     * @param reason 未匹配原因
     * @return 匹配结果
     */
    public static PreAggregationMatchResult noMatch(String reason) {
        return new PreAggregationMatchResult(false, null, false, 0, reason);
    }

    /**
     * 创建未匹配的结果（无预聚合配置）
     *
     * @return 匹配结果
     */
    public static PreAggregationMatchResult noMatch() {
        return new PreAggregationMatchResult(false, null, false, 0, "No pre-aggregation available");
    }

    /**
     * 获取预聚合名称（如果匹配成功）
     */
    public String getPreAggName() {
        return preAggregation != null ? preAggregation.getName() : null;
    }

    @Override
    public String toString() {
        if (matched) {
            return "PreAggMatchResult{" +
                    "matched=true" +
                    ", preAgg='" + getPreAggName() + '\'' +
                    ", needsRollup=" + needsRollup +
                    ", score=" + score +
                    '}';
        } else {
            return "PreAggMatchResult{matched=false, reason='" + reason + "'}";
        }
    }
}
