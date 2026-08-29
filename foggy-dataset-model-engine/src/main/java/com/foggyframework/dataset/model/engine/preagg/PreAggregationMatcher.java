package com.foggyframework.dataset.model.engine.preagg;

import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.model.spi.preagg.TimeGranularity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
 * <p>
 * 混合查询支持：
 * 当预聚合数据不完整时（watermark 不是最新），自动启用混合查询模式，
 * 将预聚合表和原始表的数据合并查询。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class PreAggregationMatcher {

    /**
     * 是否启用混合查询
     */
    private boolean hybridQueryEnabled = true;

    /**
     * 设置是否启用混合查询
     */
    public void setHybridQueryEnabled(boolean enabled) {
        this.hybridQueryEnabled = enabled;
    }

    /**
     * 从可用的预聚合列表中选择最佳匹配
     *
     * @param requirement     查询需求
     * @param preAggregations 可用的预聚合列表
     * @return 匹配结果
     */
    public PreAggregationMatchResult findBestMatch(PreAggQueryRequirement requirement,
                                                    List<PreAggregation> preAggregations) {
        return findBestMatch(requirement, preAggregations, null);
    }

    /**
     * Finds the best match and optionally emits value-free candidate decisions.
     *
     * <p>The observer is intended for request-scoped explain collection. The
     * ordinary query path calls the two-argument overload and performs no
     * candidate evidence allocation.</p>
     */
    public PreAggregationMatchResult findBestMatch(
            PreAggQueryRequirement requirement,
            List<PreAggregation> preAggregations,
            Consumer<CandidateDecision> decisionObserver
    ) {
        if (preAggregations == null || preAggregations.isEmpty()) {
            return PreAggregationMatchResult.noMatch(
                    "PREAGG_NOT_CONFIGURED", "No pre-aggregations configured");
        }

        if (requirement == null) {
            return PreAggregationMatchResult.noMatch(
                    "PREAGG_REQUIREMENT_UNAVAILABLE", "Query requirement is null");
        }

        // 只有有分组的查询才考虑预聚合
        if (!requirement.isHasGroupBy()) {
            return PreAggregationMatchResult.noMatch(
                    "PREAGG_GROUP_BY_REQUIRED",
                    "Query has no GROUP BY, pre-aggregation not applicable");
        }

        // 有自定义 SQL 条件（query.andSql() 等）时不使用预聚合，因为无法解析
        if (requirement.isHasCustomSqlConditions()) {
            return PreAggregationMatchResult.noMatch(
                    "PREAGG_CUSTOM_SQL_UNSUPPORTED",
                    "Query has custom SQL conditions, pre-aggregation not supported");
        }

        // 注意：有 slice 条件时可以继续匹配，isSatisfiableBy() 会检查 slice 列是否在预聚合中

        // 过滤满足条件的预聚合并计算分数
        List<Candidate> candidates = new ArrayList<>();
        RejectionSummary rejections = new RejectionSummary();
        for (PreAggregation preAgg : preAggregations) {
            // 跳过未启用的预聚合
            if (!preAgg.isEnabled()) {
                if (log.isDebugEnabled()) {
                    log.debug("Skipping disabled pre-aggregation: {}", preAgg.getName());
                }
                reject(decisionObserver, rejections, preAgg,
                        "PREAGG_CANDIDATE_DISABLED",
                        "Pre-aggregation candidate is disabled");
                continue;
            }

            String incompatibilityReasonCode = requirement.incompatibilityReasonCode(preAgg);
            if (incompatibilityReasonCode != null) {
                log.debug("Skipping pre-aggregation '{}': {}",
                        preAgg.getName(), incompatibilityReasonCode);
                reject(decisionObserver, rejections, preAgg,
                        incompatibilityReasonCode, reasonFor(incompatibilityReasonCode));
                continue;
            }

            // An incremental materialization must carry one valid DATE
            // exclusive boundary. Null, future, and foreign-domain values
            // cannot prove a safe materialized/source split.
            Object dataWatermark = preAgg.getDataWatermark();
            if (hybridQueryEnabled && preAgg.supportsHybridQuery()) {
                String invalidWatermarkCode = invalidHybridWatermarkReasonCode(dataWatermark);
                if (invalidWatermarkCode != null) {
                    String invalidWatermarkReason = reasonFor(invalidWatermarkCode);
                    log.debug("Skipping hybrid pre-aggregation '{}': {}",
                            preAgg.getName(), invalidWatermarkReason);
                    reject(decisionObserver, rejections, preAgg,
                            invalidWatermarkCode, invalidWatermarkReason);
                    continue;
                }
            }

            // 检查是否满足需求
            int score = calculateScore(preAgg, requirement);
            boolean needsRollup = checkNeedsRollup(preAgg, requirement);
            boolean needsHybrid = requiresHybridQuery(preAgg, dataWatermark);
            if (needsRollup && !supportsRollupMeasures(preAgg, requirement)) {
                log.debug("Skipping pre-aggregation '{}': one or more measures cannot be rolled up safely",
                        preAgg.getName());
                reject(decisionObserver, rejections, preAgg,
                        "PREAGG_ROLLUP_MEASURE_UNSUPPORTED",
                        "One or more measures cannot be rolled up safely");
                continue;
            }
            if (needsHybrid && !supportsHybridMeasures(preAgg, requirement)) {
                log.debug("Skipping pre-aggregation '{}': one or more measures cannot be merged safely "
                                + "with source rows in hybrid mode",
                        preAgg.getName());
                reject(decisionObserver, rejections, preAgg,
                        "PREAGG_HYBRID_MEASURE_UNSUPPORTED",
                        "One or more measures cannot be merged safely in hybrid mode");
                continue;
            }
            if (needsHybrid && !hasMaterializedWatermarkContract(preAgg)) {
                log.debug("Skipping pre-aggregation '{}': hybrid watermark column '{}' "
                                + "has no materialized column contract",
                        preAgg.getName(), preAgg.getWatermarkColumn());
                reject(decisionObserver, rejections, preAgg,
                        "PREAGG_HYBRID_WATERMARK_COLUMN_UNDECLARED",
                        "Hybrid watermark has no materialized column contract");
                continue;
            }
            Object watermark = needsHybrid ? dataWatermark : null;
            candidates.add(new Candidate(preAgg, score, needsRollup, needsHybrid, watermark));

            if (log.isDebugEnabled()) {
                log.debug("Pre-aggregation '{}' is a candidate: score={}, needsRollup={}, needsHybrid={}",
                        preAgg.getName(), score, needsRollup, needsHybrid);
            }
        }

        if (candidates.isEmpty()) {
            return PreAggregationMatchResult.noMatch(
                    rejections.reasonCode(), rejections.reason());
        }

        // 按分数排序（降序）
        candidates.sort(Comparator.comparingInt(Candidate::getScore).reversed());

        // 选择最高分的候选
        Candidate best = candidates.get(0);
        if (decisionObserver != null) {
            for (Candidate candidate : candidates) {
                boolean selected = candidate == best;
                decisionObserver.accept(new CandidateDecision(
                        candidate.getPreAggregation().getName(),
                        selected ? "SELECTED" : "NOT_SELECTED",
                        selected ? candidateRoute(candidate) : null,
                        selected ? candidateRoute(candidate) : "PREAGG_NOT_SELECTED_BY_SCORE",
                        candidate.getScore()));
            }
        }

        if (log.isInfoEnabled()) {
            log.info("Selected pre-aggregation '{}' with score {} (needsRollup={}, hybridQuery={})",
                    best.getPreAggregation().getName(), best.getScore(),
                    best.isNeedsRollup(), best.isNeedsHybrid());
        }

        // 根据是否需要混合查询返回不同的结果
        if (best.isNeedsHybrid()) {
            return PreAggregationMatchResult.hybrid(
                    best.getPreAggregation(),
                    best.isNeedsRollup(),
                    best.getWatermark(),
                    best.getScore()
            );
        } else {
            return PreAggregationMatchResult.matched(
                    best.getPreAggregation(),
                    best.isNeedsRollup(),
                    best.getScore()
            );
        }
    }

    /**
     * 检查是否需要混合查询
     * <p>
     * 混合查询条件：
     * <ol>
     *   <li>混合查询功能已启用</li>
     *   <li>预聚合支持混合查询（配置了 watermark 列）</li>
     *   <li>存在已发布的 exclusive watermark；其后的源表 tail 必须合并</li>
     * </ol>
     * </p>
     *
     * @param preAgg 预聚合
     * @return true 如果需要混合查询
     */
    boolean requiresHybridQuery(PreAggregation preAgg) {
        return requiresHybridQuery(preAgg, preAgg.getDataWatermark());
    }

    private boolean requiresHybridQuery(PreAggregation preAgg, Object watermark) {
        // 混合查询未启用
        if (!hybridQueryEnabled) {
            return false;
        }

        // 预聚合不支持混合查询
        if (!preAgg.supportsHybridQuery()) {
            return false;
        }

        return watermark != null;
    }

    private String invalidHybridWatermarkReasonCode(Object watermark) {
        if (watermark == null) {
            return "PREAGG_HYBRID_WATERMARK_MISSING";
        }
        if (!(watermark instanceof LocalDate boundary)) {
            return "PREAGG_HYBRID_WATERMARK_TYPE_UNSUPPORTED";
        }
        if (boundary.isAfter(LocalDate.now())) {
            return "PREAGG_HYBRID_WATERMARK_FUTURE";
        }
        return null;
    }

    private void reject(
            Consumer<CandidateDecision> observer,
            RejectionSummary rejections,
            PreAggregation preAggregation,
            String reasonCode,
            String reason
    ) {
        rejections.add(reasonCode, reason);
        if (observer != null) {
            observer.accept(new CandidateDecision(
                    preAggregation == null ? null : preAggregation.getName(),
                    "REJECTED",
                    null,
                    reasonCode,
                    null));
        }
    }

    private String candidateRoute(Candidate candidate) {
        if (candidate.isNeedsHybrid()) {
            return "PREAGG_HYBRID";
        }
        return candidate.isNeedsRollup() ? "PREAGG_ROLLUP" : "PREAGG_DIRECT";
    }

    private String reasonFor(String reasonCode) {
        return switch (reasonCode) {
            case "PREAGG_CANDIDATE_UNAVAILABLE" -> "Pre-aggregation candidate is unavailable";
            case "PREAGG_FILTER_IMPLICATION_UNPROVEN" ->
                    "Candidate filter implication cannot be proven";
            case "PREAGG_DIMENSION_MISSING" -> "Candidate does not materialize a required dimension";
            case "PREAGG_DIMENSION_PROPERTY_NOT_MATERIALIZED" ->
                    "Candidate does not explicitly materialize a required dimension property";
            case "PREAGG_TIME_GRAIN_UNDECLARED" ->
                    "Candidate does not declare a required time grain";
            case "PREAGG_TIME_GRAIN_INCOMPATIBLE" ->
                    "Candidate time grain cannot roll up to the requested grain";
            case "PREAGG_MEASURE_MISSING" -> "Candidate does not materialize a required measure";
            case "PREAGG_AGGREGATION_INCOMPATIBLE" ->
                    "Candidate measure aggregation is incompatible with the request";
            case "PREAGG_SLICE_DIMENSION_MISSING" ->
                    "Candidate does not materialize a dimension required by a predicate";
            case "PREAGG_SLICE_PROPERTY_NOT_MATERIALIZED" ->
                    "Candidate does not explicitly materialize a property required by a predicate";
            case "PREAGG_HYBRID_WATERMARK_MISSING" -> "Hybrid watermark is missing";
            case "PREAGG_HYBRID_WATERMARK_TYPE_UNSUPPORTED" ->
                    "Hybrid watermark is not a LocalDate exclusive boundary";
            case "PREAGG_HYBRID_WATERMARK_FUTURE" -> "Hybrid watermark is in the future";
            default -> reasonCode != null && reasonCode.startsWith("PREAGG_SECURITY_")
                    ? "Candidate cannot reproduce the governed security constraint"
                    : "Candidate does not satisfy the query requirements";
        };
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
        Set<String> preAggDimensions = preAgg.getDimensionNames();
        Set<String> queryDimensions = requirement.getDimensionNames();

        // A pre-aggregation row is grouped by every configured dimension. If
        // the query omits any of them, multiple physical rows can contribute
        // to one result row and a second aggregation is mandatory.
        if (preAggDimensions != null && queryDimensions != null
                && !queryDimensions.containsAll(preAggDimensions)) {
            return true;
        }

        // Grouping by a dimension property (for example product category)
        // is coarser than grouping by the dimension key stored at the
        // pre-aggregation grain. The property can repeat across dimension
        // members, so direct projection would duplicate semantic rows.
        if (requirement.getDimensionProperties().values().stream()
                .anyMatch(properties -> properties != null && !properties.isEmpty())) {
            return true;
        }

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

    private boolean supportsRollupMeasures(PreAggregation preAgg,
                                           PreAggQueryRequirement requirement) {
        for (String measureName : requirement.getMeasureAggregations().keySet()) {
            if (!isRollupSafe(preAgg.getMeasureAggregations().get(measureName))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasMaterializedWatermarkContract(PreAggregation preAgg) {
        String watermarkColumn = preAgg.getWatermarkColumn();
        if (watermarkColumn == null || watermarkColumn.isEmpty()) {
            return false;
        }
        int dollarIndex = watermarkColumn.indexOf('$');
        if (dollarIndex <= 0) {
            // A bare refresh watermark is already an explicit physical column.
            return true;
        }
        String dimensionName = watermarkColumn.substring(0, dollarIndex);
        String propertyName = watermarkColumn.substring(dollarIndex + 1);
        return preAgg.hasMaterializedDimensionProperty(dimensionName, propertyName);
    }

    private boolean supportsHybridMeasures(PreAggregation preAgg,
                                           PreAggQueryRequirement requirement) {
        if (requirement.getMeasureAggregations().isEmpty()) {
            return false;
        }
        for (String measureName : requirement.getMeasureAggregations().keySet()) {
            DbAggregation aggregation = preAgg.getMeasureAggregations().get(measureName);
            if (aggregation != DbAggregation.SUM
                    && aggregation != DbAggregation.MIN
                    && aggregation != DbAggregation.MAX) {
                return false;
            }
        }
        return true;
    }

    private boolean isRollupSafe(DbAggregation aggregation) {
        return aggregation == DbAggregation.SUM
                || aggregation == DbAggregation.COUNT
                || aggregation == DbAggregation.MIN
                || aggregation == DbAggregation.MAX
                || aggregation == DbAggregation.AVG;
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
        private boolean needsHybrid;
        private Object watermark;
    }

    /** Value-free matcher evidence emitted only when an observer is supplied. */
    public record CandidateDecision(
            String preAggregation,
            String decision,
            String route,
            String reasonCode,
            Integer score
    ) {
    }

    private static final class RejectionSummary {
        private String reasonCode;
        private String reason;
        private boolean mixed;

        void add(String candidateReasonCode, String candidateReason) {
            if (reasonCode == null) {
                reasonCode = candidateReasonCode;
                reason = candidateReason;
            } else if (!reasonCode.equals(candidateReasonCode)) {
                mixed = true;
            }
        }

        String reasonCode() {
            return reasonCode == null || mixed
                    ? "PREAGG_NO_COMPATIBLE_CANDIDATE"
                    : reasonCode;
        }

        String reason() {
            return reasonCode == null || mixed
                    ? "No pre-aggregation satisfies the query requirements"
                    : reason;
        }
    }
}
