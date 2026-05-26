package com.foggyframework.dataset.db.model.engine.pivot.algo;

import com.foggyframework.dataset.db.model.engine.pivot.rollup.MetricAdditivityAnalyzer;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotMetricItem;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ParentShare 父级占比计算器（Phase 2.8）
 *
 * <p>在 SubtotalInjector / PropertyAttacher 之后、ResultShaper 之前执行。</p>
 *
 * <p>语义：当前子级单元格指标值 / 当前父级坐标下的父级聚合值</p>
 *
 * <p>第一版限制：
 * <ul>
 *   <li>只支持同一轴相邻层级</li>
 *   <li>不支持 hierarchyMode=tree</li>
 *   <li>不支持跨轴父级</li>
 *   <li>除零、父级缺失、当前值 null 时返回 null</li>
 *   <li>parentShare 只作为输出指标，不参与 having/orderBy/limit</li>
 *   <li>不可加度量暂不支持（fail-closed）</li>
 * </ul>
 */
public class ParentShareCalculator {

    private static final Logger logger = LoggerFactory.getLogger(ParentShareCalculator.class);
    private static final String SYS_META_KEY = "_sys_meta";

    /**
     * 对结果集中的每行计算 parentShare 并写入
     *
     * @param resultSet         当前结果集（含 subtotal 行）
     * @param pivot             PivotRequest（含 parentShare metric 定义）
     * @param rowFields         行轴字段名列表
     * @param colFields         列轴字段名列表
     * @return 结果集（parentShare 字段已写入）
     */
    public static List<Map<String, Object>> apply(
            List<Map<String, Object>> resultSet,
            PivotRequest pivot,
            List<String> rowFields,
            List<String> colFields) {
        return apply(resultSet, pivot, rowFields, colFields, Collections.emptyMap());
    }

    /**
     * 对结果集中的每行计算 parentShare，并优先使用外部父级分母索引。
     *
     * <p>外部分母用于 {@code denominatorScope=prePageParent}：可在 rows window
     * 截断前构建父级分母，再对截断后的可见行计算占比。</p>
     */
    public static List<Map<String, Object>> apply(
            List<Map<String, Object>> resultSet,
            PivotRequest pivot,
            List<String> rowFields,
            List<String> colFields,
            Map<String, Map<String, Number>> externalParentAggIndex) {

        List<PivotMetricItem> parentShareMetrics = pivot.getParentShareMetrics();
        if (parentShareMetrics.isEmpty()) {
            return resultSet;
        }

        for (PivotMetricItem psMetric : parentShareMetrics) {
            ResolvedParentShare resolved = resolve(psMetric, rowFields, colFields, pivot);
            resolved.ofMetric = psMetric.getOf();
            logger.debug("[Pivot] Phase 2.8: Computing parentShare '{}' of '{}', " +
                            "axis={}, level={}, parentLevel={}",
                    psMetric.getName(), psMetric.getOf(),
                    resolved.axis, resolved.level, resolved.parentLevel);

            // 构建父级聚合索引：groupKey → sum of 'of' metric
            Map<String, Double> visibleParentAggIndex = buildParentAggIndex(
                    resultSet, resolved, colFields);
            Map<String, Number> externalParentValues = externalParentAggIndex == null
                    ? Collections.emptyMap()
                    : externalParentAggIndex.getOrDefault(psMetric.getName(), Collections.emptyMap());

            // 对每个非 subtotal 行计算 parentShare
            for (Map<String, Object> row : resultSet) {
                if (isSubtotalRow(row)) {
                    // subtotal 行不计算 parentShare
                    row.put(psMetric.getName(), null);
                    continue;
                }

                Object currentVal = row.get(psMetric.getOf());
                if (!(currentVal instanceof Number)) {
                    row.put(psMetric.getName(), null);
                    continue;
                }

                String parentKey = buildParentKey(row, resolved, colFields);
                Number externalParentVal = externalParentValues.get(parentKey);
                Double parentVal = externalParentVal != null
                        ? externalParentVal.doubleValue()
                        : visibleParentAggIndex.get(parentKey);

                if (parentVal == null || parentVal == 0.0) {
                    row.put(psMetric.getName(), null);
                } else {
                    double share = ((Number) currentVal).doubleValue() / parentVal;
                    row.put(psMetric.getName(), share);
                }
            }
        }

        return resultSet;
    }

    /**
     * 为 {@code denominatorScope=prePageParent} 构建外部父级分母索引。
     */
    public static Map<String, Map<String, Number>> buildExternalParentAggIndex(
            List<Map<String, Object>> resultSet,
            PivotRequest pivot,
            List<String> rowFields,
            List<String> colFields) {

        List<PivotMetricItem> parentShareMetrics = pivot.getParentShareMetrics();
        if (parentShareMetrics.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Map<String, Number>> result = new LinkedHashMap<>();
        for (PivotMetricItem psMetric : parentShareMetrics) {
            if (!"prePageParent".equals(psMetric.getDenominatorScope())) {
                continue;
            }
            ResolvedParentShare resolved = resolve(psMetric, rowFields, colFields, pivot);
            resolved.ofMetric = psMetric.getOf();
            Map<String, Double> index = buildParentAggIndex(resultSet, resolved, colFields);
            result.put(psMetric.getName(), new LinkedHashMap<>(index));
        }
        return result;
    }

    /**
     * 推断或校验 parentShare 的轴/层级
     */
    static ResolvedParentShare resolve(
            PivotMetricItem psMetric,
            List<String> rowFields,
            List<String> colFields,
            PivotRequest pivot) {

        String axis = psMetric.getAxis();
        String level = psMetric.getLevel();
        String parentLevel = psMetric.getParentLevel();

        // 显式指定 → 校验
        if (level != null && parentLevel != null) {
            if (axis == null) {
                // 推断 axis
                if (rowFields.contains(level) && rowFields.contains(parentLevel)) {
                    axis = "rows";
                } else if (colFields.contains(level) && colFields.contains(parentLevel)) {
                    throw new IllegalArgumentException(
                            "parentShare 第一版仅支持 rows 轴。当前指定 level='" + level +
                            "' 位于 columns 轴，暂不支持");
                } else {
                    throw new IllegalArgumentException(
                            "parentShare '" + psMetric.getName() + "' 的 level='" + level +
                            "' 和 parentLevel='" + parentLevel + "' 不在同一轴上");
                }
            }

            List<String> axisFields = "rows".equals(axis) ? rowFields : colFields;
            int levelIdx = axisFields.indexOf(level);
            int parentIdx = axisFields.indexOf(parentLevel);

            if (levelIdx < 0 || parentIdx < 0) {
                throw new IllegalArgumentException(
                        "parentShare '" + psMetric.getName() + "' 的 level/parentLevel 不在 " +
                        axis + " 轴中");
            }
            if (levelIdx != parentIdx + 1) {
                throw new IllegalArgumentException(
                        "parentShare '" + psMetric.getName() + "' 的 level 和 parentLevel 必须是相邻层级。" +
                        "当前 parentLevel 在 index=" + parentIdx + "，level 在 index=" + levelIdx);
            }

            return new ResolvedParentShare(axis, level, parentLevel, axisFields);
        }

        // 隐式推断
        if (axis == null) {
            // 第一版只从 rows 推断
            if (rowFields.size() >= 2) {
                axis = "rows";
            } else {
                throw new IllegalArgumentException(
                        "parentShare '" + psMetric.getName() + "' 无法推断父子层级：" +
                        "rows 不足 2 个层级。请显式指定 axis/level/parentLevel");
            }
        }

        List<String> axisFields = rowFields; // 第一版只支持 rows
        if (axisFields.size() < 2) {
            throw new IllegalArgumentException(
                    "parentShare '" + psMetric.getName() + "' 的 axis='" + axis +
                    "' 不足 2 个层级，无法推断父子关系");
        }

        // 取最后两个相邻层级
        parentLevel = axisFields.get(axisFields.size() - 2);
        level = axisFields.get(axisFields.size() - 1);

        return new ResolvedParentShare(axis, level, parentLevel, axisFields);
    }

    /**
     * 构建父级聚合索引
     * <p>key = parentLevel值 + colField值, value = SUM(of metric)</p>
     */
    private static Map<String, Double> buildParentAggIndex(
            List<Map<String, Object>> resultSet,
            ResolvedParentShare resolved,
            List<String> colFields) {

        // 分组键 = parentLevel 及其前面的所有轴字段 + 交叉轴字段
        List<String> groupKeys = new ArrayList<>();
        List<String> axisFields = resolved.axisFields;
        int parentIdx = axisFields.indexOf(resolved.parentLevel);
        for (int i = 0; i <= parentIdx; i++) {
            groupKeys.add(axisFields.get(i));
        }
        // 加上交叉轴字段
        List<String> crossFields = "rows".equals(resolved.axis) ? colFields :
                axisFields.subList(0, axisFields.size()); // 如果是 columns 轴，rows 是交叉轴
        if ("rows".equals(resolved.axis)) {
            groupKeys.addAll(colFields);
        }
        // 注意：如果 axis=columns，交叉轴是 rows — 但第一版只支持 rows 轴的 parentShare

        Map<String, Double> index = new LinkedHashMap<>();

        for (Map<String, Object> row : resultSet) {
            if (isSubtotalRow(row)) continue;

            String key = groupKeys.stream()
                    .map(k -> String.valueOf(row.getOrDefault(k, "__null__")))
                    .collect(Collectors.joining("\u001F"));

            Object val = row.get(resolved.ofMetric);
            if (val instanceof Number) {
                index.merge(key, ((Number) val).doubleValue(), Double::sum);
            }
        }

        return index;
    }

    /**
     * 为某一行构建父级索引 key
     */
    private static String buildParentKey(
            Map<String, Object> row,
            ResolvedParentShare resolved,
            List<String> colFields) {

        List<String> groupKeys = new ArrayList<>();
        List<String> axisFields = resolved.axisFields;
        int parentIdx = axisFields.indexOf(resolved.parentLevel);
        for (int i = 0; i <= parentIdx; i++) {
            groupKeys.add(axisFields.get(i));
        }
        if ("rows".equals(resolved.axis)) {
            groupKeys.addAll(colFields);
        }

        return groupKeys.stream()
                .map(k -> String.valueOf(row.getOrDefault(k, "__null__")))
                .collect(Collectors.joining("\u001F"));
    }

    @SuppressWarnings("unchecked")
    private static boolean isSubtotalRow(Map<String, Object> row) {
        Object meta = row.get(SYS_META_KEY);
        if (meta instanceof Map) {
            Map<String, Object> metaMap = (Map<String, Object>) meta;
            return Boolean.TRUE.equals(metaMap.get("isRowSubtotal"))
                    || Boolean.TRUE.equals(metaMap.get("isColSubtotal"))
                    || Boolean.TRUE.equals(metaMap.get("isGrandTotal"));
        }
        return false;
    }

    /**
     * 推断结果封装
     */
    static class ResolvedParentShare {
        final String axis;
        final String level;
        final String parentLevel;
        final List<String> axisFields;
        String ofMetric; // 会在 apply() 中设置

        ResolvedParentShare(String axis, String level, String parentLevel, List<String> axisFields) {
            this.axis = axis;
            this.level = level;
            this.parentLevel = parentLevel;
            this.axisFields = axisFields;
        }
    }

    /**
     * 前置校验 parentShare 定义
     *
     * @throws IllegalArgumentException 如果不满足第一版限制
     */
    public static void validateParentShareMetrics(PivotRequest pivot,
                                                   List<String> rowFields,
                                                   List<String> colFields) {
        List<PivotMetricItem> psMetrics = pivot.getParentShareMetrics();
        if (psMetrics.isEmpty()) return;

        // tree + parentShare → fail-closed
        if (pivot.hasHierarchyField()) {
            throw new IllegalArgumentException(
                    "parentShare 不支持与 hierarchyMode=tree 同时使用。" +
                    "请移除 parentShare 或 hierarchyMode");
        }

        // 校验 of 引用的度量存在于原生度量中
        List<String> nativeMetrics = pivot.getNativeMetricNames();
        for (PivotMetricItem ps : psMetrics) {
            if (!nativeMetrics.contains(ps.getOf())) {
                throw new IllegalArgumentException(
                        "parentShare '" + ps.getName() + "' 的 of='" + ps.getOf() +
                        "' 未在 pivot.metrics 的原生度量中找到");
            }

            // 预先 resolve 以校验层级推断
            ResolvedParentShare resolved = resolve(ps, rowFields, colFields, pivot);
            resolved.ofMetric = ps.getOf();
        }
    }

    /**
     * 校验 parentShare.of 引用的度量是否为可加度量（需要 QueryModel）
     *
     * <p>只允许 SUM、COUNT、MIN、MAX。
     * AVG、COUNT_DISTINCT 等非可加度量会导致内存 SUM 产生错误结果，必须 fail-closed。</p>
     *
     * @throws IllegalArgumentException 如果 of 引用了不可加度量
     */
    public static void validateAdditivity(PivotRequest pivot, QueryModel queryModel) {
        List<PivotMetricItem> psMetrics = pivot.getParentShareMetrics();
        if (psMetrics.isEmpty() || queryModel == null) return;

        Set<DbAggregation> ADDITIVE = Set.of(
                DbAggregation.SUM, DbAggregation.COUNT,
                DbAggregation.MIN, DbAggregation.MAX);

        for (PivotMetricItem ps : psMetrics) {
            DbAggregation agg = MetricAdditivityAnalyzer.resolveAggregation(
                    ps.getOf(), queryModel);
            if (agg == null) {
                // calculatedField 或元数据缺失，默认允许（向后兼容）
                logger.debug("[Pivot] parentShare '{}' of='{}' aggregation unknown, allowing",
                        ps.getName(), ps.getOf());
                continue;
            }

            if (!ADDITIVE.contains(agg)) {
                throw new IllegalArgumentException(
                        "parentShare '" + ps.getName() + "' 的 of='" + ps.getOf() +
                        "' 使用了不可加聚合类型 " + agg +
                        "。parentShare 仅支持可加度量（SUM/COUNT/MIN/MAX）。" +
                        "请移除该 parentShare 指标或换用可加度量");
            }
        }
    }
}
