package com.foggyframework.dataset.db.model.engine.preagg;

import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.db.model.spi.preagg.TimeGranularity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 预聚合匹配器
 * <p>
 * 根据查询需求选择最佳的预聚合表。
 * </p>
 * <p>
 * 选择策略：
 * <ol>
 *   <li>满足所有查询需求（维度、属性、度量、粒度）</li>
 *   <li>优先级（priority）高的优先</li>
 *   <li>维度数最接近查询维度数的优先（避免冗余数据）</li>
 *   <li>粒度最接近查询粒度的优先（减少 rollup 开销）</li>
 * </ol>
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class PreAggregationMatcher {

    /**
     * 从可用的预聚合列表中选择最佳匹配
     *
     * @param requirement     查询需求
     * @param preAggregations 可用的预聚合列表
     * @return 匹配结果
     */
    public PreAggregationMatchResult findBestMatch(PreAggQueryRequirement requirement,
                                                    List<PreAggregation> preAggregations) {
        if (preAggregations == null || preAggregations.isEmpty()) {
            return PreAggregationMatchResult.noMatch("No pre-aggregations configured");
        }

        if (requirement == null) {
            return PreAggregationMatchResult.noMatch("Query requirement is null");
        }

        // 只有有分组的查询才考虑预聚合
        if (!requirement.isHasGroupBy()) {
            return PreAggregationMatchResult.noMatch("Query has no GROUP BY, pre-aggregation not applicable");
        }

        // 过滤满足条件的预聚合并计算分数
        List<Candidate> candidates = new ArrayList<>();
        for (PreAggregation preAgg : preAggregations) {
            // 跳过未启用的预聚合
            if (!preAgg.isEnabled()) {
                if (log.isDebugEnabled()) {
                    log.debug("Skipping disabled pre-aggregation: {}", preAgg.getName());
                }
                continue;
            }

            // 检查是否满足需求
            if (requirement.isSatisfiableBy(preAgg)) {
                int score = calculateScore(preAgg, requirement);
                boolean needsRollup = checkNeedsRollup(preAgg, requirement);
                candidates.add(new Candidate(preAgg, score, needsRollup));

                if (log.isDebugEnabled()) {
                    log.debug("Pre-aggregation '{}' is a candidate: score={}, needsRollup={}",
                            preAgg.getName(), score, needsRollup);
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Pre-aggregation '{}' does not satisfy requirements", preAgg.getName());
                }
            }
        }

        if (candidates.isEmpty()) {
            return PreAggregationMatchResult.noMatch("No pre-aggregation satisfies the query requirements");
        }

        // 按分数排序（降序）
        candidates.sort(Comparator.comparingInt(Candidate::getScore).reversed());

        // 选择最高分的候选
        Candidate best = candidates.get(0);

        if (log.isInfoEnabled()) {
            log.info("Selected pre-aggregation '{}' with score {} (needsRollup={})",
                    best.getPreAggregation().getName(), best.getScore(), best.isNeedsRollup());
        }

        return PreAggregationMatchResult.matched(
                best.getPreAggregation(),
                best.isNeedsRollup(),
                best.getScore()
        );
    }

    /**
     * 计算预聚合的匹配分数
     * <p>
     * 评分规则：
     * <ul>
     *   <li>priority * 100（权重最高）</li>
     *   <li>- (预聚合维度数 - 查询维度数) * 10（维度数接近）</li>
     *   <li>- (预聚合粒度级别 - 查询粒度级别)（粒度接近）</li>
     * </ul>
     * </p>
     *
     * @param preAgg      预聚合
     * @param requirement 查询需求
     * @return 分数
     */
    private int calculateScore(PreAggregation preAgg, PreAggQueryRequirement requirement) {
        int score = preAgg.getPriority() * 100;

        // 维度数惩罚：预聚合维度越多，分数越低
        int dimDiff = preAgg.getDimensionCount() - requirement.getDimensionCount();
        score -= dimDiff * 10;

        // 粒度惩罚：预聚合粒度越细，分数越低（因为需要更多 rollup）
        int granDiff = preAgg.getGranularityLevel() - requirement.getGranularityLevel();
        if (granDiff < 0) {
            // 预聚合粒度比查询粗，这不应该发生（已在 isSatisfiableBy 中检查）
            score -= 1000;
        } else {
            score -= granDiff;
        }

        return score;
    }

    /**
     * 检查是否需要 rollup
     * <p>
     * 当预聚合的粒度比查询粒度更细时，需要进行二次聚合。
     * </p>
     *
     * @param preAgg      预聚合
     * @param requirement 查询需求
     * @return 是否需要 rollup
     */
    private boolean checkNeedsRollup(PreAggregation preAgg, PreAggQueryRequirement requirement) {
        Map<String, TimeGranularity> queryGranularities = requirement.getQueryGranularities();
        Map<String, TimeGranularity> preAggGranularities = preAgg.getGranularities();

        if (queryGranularities.isEmpty() || preAggGranularities.isEmpty()) {
            return false;
        }

        for (Map.Entry<String, TimeGranularity> entry : queryGranularities.entrySet()) {
            String dimName = entry.getKey();
            TimeGranularity queryGran = entry.getValue();
            TimeGranularity preAggGran = preAggGranularities.get(dimName);

            if (preAggGran != null && queryGran != null) {
                // 如果预聚合粒度比查询粒度更细，需要 rollup
                if (preAggGran.isFinerThan(queryGran)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 匹配候选
     */
    @Data
    @AllArgsConstructor
    private static class Candidate {
        private PreAggregation preAggregation;
        private int score;
        private boolean needsRollup;
    }
}
