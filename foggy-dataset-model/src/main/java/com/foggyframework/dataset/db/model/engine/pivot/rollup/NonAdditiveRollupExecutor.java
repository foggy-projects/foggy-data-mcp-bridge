package com.foggyframework.dataset.db.model.engine.pivot.rollup;

import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.pivot.CardinalityBreaker;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 不可加度量辅助聚合查询执行器
 *
 * <p>根据 {@link RollupGrain} 列表和 surviving domain，
 * 执行辅助聚合查询并将结果写入 {@link RollupCache}。</p>
 *
 * <p>S8.3.5: 使用 UNION ALL 批量合并模式 —— 对每个 grain 调用 {@code generateSql()}
 * 获取含治理策略的子查询 SQL，然后拼接为单条 UNION ALL SQL 执行，
 * 将 N 次 DB 往返合并为 1 次。</p>
 *
 * <p>降级策略：如果 {@code generateSql()} 不可用（抛异常），
 * 自动降级为逐 grain 串行执行（S8.3.0 行为）。</p>
 */
public class NonAdditiveRollupExecutor {

    private static final Logger logger = LoggerFactory.getLogger(NonAdditiveRollupExecutor.class);

    /** UNION ALL grain 标识列名 */
    static final String GRAIN_IDX_COLUMN = "_pivot_grain_idx";

    /** IN 列表最大长度（超过则不限制，避免超长 SQL） */
    private static final int MAX_IN_LIST_SIZE = 500;

    /** 单次 UNION ALL 最多合并的 grain 数量，超出部分分批 */
    private static final int MAX_GRAINS_PER_BATCH = 20;

    private final SemanticQueryServiceV3 semanticQueryService;

    public NonAdditiveRollupExecutor(SemanticQueryServiceV3 semanticQueryService) {
        this.semanticQueryService = semanticQueryService;
    }

    /**
     * 执行辅助聚合查询并构建 RollupCache
     *
     * <p>S8.3.5: 优先使用 UNION ALL 批量合并，失败时降级为逐 grain 串行。</p>
     *
     * @param model           模型名称
     * @param originalRequest 原始请求（用于透传 slice/calculatedFields）
     * @param context         请求上下文
     * @param grains          需要执行的辅助 grain 列表
     * @param rollupPlans     metric 的 rollup 计划列表
     * @param rowFields       行轴字段
     * @param colFields       列轴字段
     * @param survivingRowDomain  Having/TopN 后的存活行域（可为 null 表示不限制）
     * @param survivingColDomain  Having/TopN 后的存活列域（可为 null 表示不限制）
     * @return 填充好的 RollupCache
     */
    public RollupCache execute(
            String model,
            SemanticQueryRequest originalRequest,
            SemanticRequestContext context,
            List<RollupGrain> grains,
            List<RollupMetricPlan> rollupPlans,
            List<String> rowFields,
            List<String> colFields,
            Set<List<Object>> survivingRowDomain,
            Set<List<Object>> survivingColDomain) {

        RollupCache cache = new RollupCache();

        // 收集需要辅助查询的度量
        Set<String> auxMetrics = MetricAdditivityAnalyzer.collectAuxQueryMetrics(rollupPlans);
        if (auxMetrics.isEmpty()) {
            return cache;
        }

        logger.debug("[Pivot] NonAdditiveRollupExecutor: {} grains, {} aux metrics: {}",
                grains.size(), auxMetrics.size(), auxMetrics);

        // 尝试 UNION ALL 批量合并
        boolean batchSuccess = tryBatchExecute(
                model, originalRequest, context, grains, auxMetrics,
                rowFields, colFields, survivingRowDomain, survivingColDomain, cache);

        if (!batchSuccess) {
            // 降级: 逐 grain 串行执行
            logger.info("[Pivot] Falling back to per-grain serial execution");
            for (RollupGrain grain : grains) {
                executeGrainSerial(model, originalRequest, context, grain, auxMetrics,
                        rowFields, colFields, survivingRowDomain, survivingColDomain, cache);
            }
        }

        logger.debug("[Pivot] RollupCache built: {}", cache);
        return cache;
    }

    // ========== UNION ALL 批量执行 ==========

    /**
     * 尝试使用 UNION ALL 批量合并执行
     *
     * @return true 如果批量执行成功；false 如果需要降级
     */
    private boolean tryBatchExecute(
            String model,
            SemanticQueryRequest originalRequest,
            SemanticRequestContext context,
            List<RollupGrain> grains,
            Set<String> auxMetrics,
            List<String> rowFields,
            List<String> colFields,
            Set<List<Object>> survivingRowDomain,
            Set<List<Object>> survivingColDomain,
            RollupCache cache) {

        if (grains.isEmpty()) return true;

        try {
            // 计算所有可能涉及的列的超集（用于 UNION ALL 列对齐）
            Set<String> allFieldsSet = new LinkedHashSet<>();
            allFieldsSet.addAll(rowFields);
            allFieldsSet.addAll(colFields);
            List<String> allDimFields = new ArrayList<>(allFieldsSet);

            // 分批处理（超过 MAX_GRAINS_PER_BATCH 时分多次 UNION ALL）
            List<List<RollupGrain>> batches = partition(grains, MAX_GRAINS_PER_BATCH);
            long batchStart = System.currentTimeMillis();

            for (List<RollupGrain> batch : batches) {
                executeBatch(model, originalRequest, context, batch, auxMetrics,
                        allDimFields, rowFields, colFields,
                        survivingRowDomain, survivingColDomain, cache);
            }

            long elapsed = System.currentTimeMillis() - batchStart;
            logger.info("[Pivot] UNION ALL batch completed: {} grains in {} batches, {}ms",
                    grains.size(), batches.size(), elapsed);
            return true;

        } catch (Exception e) {
            logger.warn("[Pivot] UNION ALL batch failed, will fallback: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 执行单批 UNION ALL
     */
    private void executeBatch(
            String model,
            SemanticQueryRequest originalRequest,
            SemanticRequestContext context,
            List<RollupGrain> batch,
            Set<String> auxMetrics,
            List<String> allDimFields,
            List<String> rowFields,
            List<String> colFields,
            Set<List<Object>> survivingRowDomain,
            Set<List<Object>> survivingColDomain,
            RollupCache cache) {

        // 1. 为每个 grain 生成 SQL
        List<GrainSqlPart> parts = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            RollupGrain grain = batch.get(i);
            GrainSqlPart part = generateGrainSql(
                    model, originalRequest, context, grain, i,
                    auxMetrics, allDimFields, rowFields, colFields,
                    survivingRowDomain, survivingColDomain);
            if (part != null) {
                parts.add(part);
            }
        }

        if (parts.isEmpty()) return;

        // 2. 拼接 UNION ALL SQL
        StringBuilder unionSql = new StringBuilder();
        List<Object> mergedParams = new ArrayList<>();

        for (int i = 0; i < parts.size(); i++) {
            GrainSqlPart part = parts.get(i);
            if (i > 0) {
                unionSql.append("\nUNION ALL\n");
            }
            // 修复 P0: UNION ALL 必须对齐所有分支的列
            unionSql.append("SELECT ").append(part.grainIndex).append(" AS ").append(GRAIN_IDX_COLUMN);
            
            for (String dim : allDimFields) {
                if (part.grain.getGroupByFields().contains(dim)) {
                    unionSql.append(", _pivot_sub.").append(dim);
                } else {
                    unionSql.append(", NULL AS ").append(dim);
                }
            }
            for (String metric : auxMetrics) {
                unionSql.append(", _pivot_sub.").append(metric);
            }
            
            unionSql.append(" FROM (\n");
            unionSql.append(part.sql);
            unionSql.append("\n) _pivot_sub");

            mergedParams.addAll(part.params);
        }

        String finalSql = unionSql.toString();
        logger.debug("[Pivot] UNION ALL SQL: {} parts, {} params, sql length={}",
                parts.size(), mergedParams.size(), finalSql.length());

        // 3. 执行合并 SQL
        List<Map<String, Object>> rows = semanticQueryService.executeSql(
                finalSql, mergedParams, model);

        // 4. 按 grain_idx 分桶，写入 cache
        Map<Integer, List<Map<String, Object>>> buckets = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object idxObj = row.get(GRAIN_IDX_COLUMN);
            int idx = idxObj instanceof Number ? ((Number) idxObj).intValue() : 0;
            buckets.computeIfAbsent(idx, k -> new ArrayList<>()).add(row);
        }

        for (GrainSqlPart part : parts) {
            List<Map<String, Object>> grainRows = buckets.getOrDefault(part.grainIndex, Collections.emptyList());
            RollupGrain grain = part.grain;

            for (Map<String, Object> row : grainRows) {
                List<String> grainFields = grain.getGroupByFields();
                List<RollupCoordinate> coordinates = buildCoordinates(row, rowFields, colFields, grainFields);
                Map<String, Object> metricValues = new LinkedHashMap<>();
                for (String metric : auxMetrics) {
                    metricValues.put(metric, row.get(metric));
                }
                cache.put(grain.getGrainKey(), coordinates, metricValues);
            }

            logger.debug("[Pivot] UNION ALL grain={}: {} rows", grain.getGrainKey(), grainRows.size());
        }
    }

    /**
     * 为单个 grain 生成 SQL（不执行）
     *
     * @return GrainSqlPart 或 null（如果生成失败）
     */
    private GrainSqlPart generateGrainSql(
            String model,
            SemanticQueryRequest originalRequest,
            SemanticRequestContext context,
            RollupGrain grain,
            int grainIndex,
            Set<String> auxMetrics,
            List<String> allDimFields,
            List<String> rowFields,
            List<String> colFields,
            Set<List<Object>> survivingRowDomain,
            Set<List<Object>> survivingColDomain) {

        SemanticQueryRequest auxRequest = buildGrainRequest(
                grain, auxMetrics, originalRequest, rowFields, colFields,
                survivingRowDomain, survivingColDomain);

        SqlGenerationResult sqlResult = semanticQueryService.generateSql(model, auxRequest, context);
        if (sqlResult == null || sqlResult.getSql() == null || sqlResult.getSql().isBlank()) {
            logger.warn("[Pivot] generateSql returned null for grain={}", grain.getGrainKey());
            return null;
        }

        return new GrainSqlPart(grain, grainIndex, sqlResult.getSql(),
                sqlResult.getParams() != null ? sqlResult.getParams() : Collections.emptyList());
    }

    /**
     * 构建单个 grain 的查询请求
     */
    private SemanticQueryRequest buildGrainRequest(
            RollupGrain grain,
            Set<String> auxMetrics,
            SemanticQueryRequest originalRequest,
            List<String> rowFields,
            List<String> colFields,
            Set<List<Object>> survivingRowDomain,
            Set<List<Object>> survivingColDomain) {

        List<String> grainFields = grain.getGroupByFields();

        SemanticQueryRequest auxRequest = new SemanticQueryRequest();

        // columns = grain fields + aux metrics
        List<String> allColumns = new ArrayList<>(grainFields);
        allColumns.addAll(auxMetrics);
        auxRequest.setColumns(allColumns);

        // groupBy: grain fields 不聚合，metrics 保留原始聚合
        List<SemanticQueryRequest.GroupByItem> groupBy = new ArrayList<>();
        for (String field : grainFields) {
            groupBy.add(new SemanticQueryRequest.GroupByItem(field, null));
        }
        for (String metric : auxMetrics) {
            groupBy.add(new SemanticQueryRequest.GroupByItem(metric, null));
        }
        auxRequest.setGroupBy(groupBy);

        // 透传 slice 和 calculatedFields
        List<SemanticQueryRequest.SliceItem> sliceItems = new ArrayList<>();
        if (originalRequest.getSlice() != null) {
            sliceItems.addAll(originalRequest.getSlice());
        }

        // 构建 surviving domain 过滤条件
        addSurvivingDomainSlice(sliceItems, grainFields, rowFields, colFields,
                survivingRowDomain, survivingColDomain);

        auxRequest.setSlice(sliceItems);
        auxRequest.setCalculatedFields(originalRequest.getCalculatedFields());
        auxRequest.setLimit(CardinalityBreaker.DEFAULT_ROW_LIMIT * CardinalityBreaker.DEFAULT_COL_LIMIT);
        auxRequest.setReturnTotal(false);

        return auxRequest;
    }

    // ========== 串行降级路径 ==========

    /**
     * 逐 grain 串行执行（S8.3.0 降级路径）
     */
    private void executeGrainSerial(
            String model,
            SemanticQueryRequest originalRequest,
            SemanticRequestContext context,
            RollupGrain grain,
            Set<String> auxMetrics,
            List<String> rowFields,
            List<String> colFields,
            Set<List<Object>> survivingRowDomain,
            Set<List<Object>> survivingColDomain,
            RollupCache cache) {

        SemanticQueryRequest auxRequest = buildGrainRequest(
                grain, auxMetrics, originalRequest, rowFields, colFields,
                survivingRowDomain, survivingColDomain);

        logger.debug("[Pivot] Serial aux query: grain={}, fields={}", grain.getGrainKey(), grain.getGroupByFields());

        SemanticQueryResponse response = semanticQueryService.queryModel(
                model, auxRequest, "execute", context);

        List<Map<String, Object>> rows = response.getItems() != null
                ? response.getItems() : Collections.emptyList();

        // 写入 cache
        for (Map<String, Object> row : rows) {
            List<String> grainFields = grain.getGroupByFields();
            List<RollupCoordinate> coordinates = buildCoordinates(row, rowFields, colFields, grainFields);
            Map<String, Object> metricValues = new LinkedHashMap<>();
            for (String metric : auxMetrics) {
                metricValues.put(metric, row.get(metric));
            }
            cache.put(grain.getGrainKey(), coordinates, metricValues);
        }

        logger.debug("[Pivot] Serial aux query result: grain={}, rows={}", grain.getGrainKey(), rows.size());
    }

    // ========== 坐标构建 ==========

    /**
     * 构建结构化坐标
     *
     * <p>对于不在 grainFields 中的轴字段：
     * - 如果该轴的所有字段都不在 grain 中 → grandTotal
     * - 否则 → rolledUp（小计）</p>
     */
    private List<RollupCoordinate> buildCoordinates(
            Map<String, Object> row,
            List<String> rowFields,
            List<String> colFields,
            List<String> grainFields) {

        Set<String> grainFieldSet = new HashSet<>(grainFields);
        List<RollupCoordinate> coords = new ArrayList<>();

        // 预计算：行轴是否有任何字段参与 grain（用于区分 subtotal vs grand total）
        boolean anyRowInGrain = rowFields.stream().anyMatch(grainFieldSet::contains);

        for (String field : rowFields) {
            if (grainFieldSet.contains(field)) {
                coords.add(RollupCoordinate.of(field, row.get(field)));
            } else {
                // 行轴中没有任何字段参与 grain → 整行是 grand total
                // 否则只是当前层级被 rollup 的小计
                coords.add(anyRowInGrain ? RollupCoordinate.rolledUp(field) : RollupCoordinate.grandTotal(field));
            }
        }

        for (String field : colFields) {
            if (grainFieldSet.contains(field)) {
                coords.add(RollupCoordinate.of(field, row.get(field)));
            } else {
                coords.add(RollupCoordinate.rolledUp(field));
            }
        }

        return coords;
    }

    // ========== Surviving domain slice ==========

    /**
     * 将 surviving domain 转换为 slice 过滤条件
     */
    private void addSurvivingDomainSlice(
            List<SemanticQueryRequest.SliceItem> sliceItems,
            List<String> grainFields,
            List<String> rowFields,
            List<String> colFields,
            Set<List<Object>> survivingRowDomain,
            Set<List<Object>> survivingColDomain) {

        addAxisDomainSlice(sliceItems, grainFields, rowFields, survivingRowDomain);
        addAxisDomainSlice(sliceItems, grainFields, colFields, survivingColDomain);
    }

    /**
     * 对单个轴的 surviving domain 生成 IN 过滤
     */
    private void addAxisDomainSlice(
            List<SemanticQueryRequest.SliceItem> sliceItems,
            List<String> grainFields,
            List<String> axisFields,
            Set<List<Object>> domain) {

        if (domain == null || domain.isEmpty()) return;

        for (int i = 0; i < axisFields.size(); i++) {
            String field = axisFields.get(i);
            if (grainFields.contains(field)) {
                final int idx = i;
                Set<Object> values = domain.stream()
                        .map(tuple -> tuple.get(idx))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                if (!values.isEmpty() && values.size() <= MAX_IN_LIST_SIZE) {
                    SemanticQueryRequest.SliceItem slice = new SemanticQueryRequest.SliceItem();
                    slice.setField(field);
                    slice.setOp("in");
                    slice.setValue(new ArrayList<>(values));
                    sliceItems.add(slice);
                }
                // 如果 domain 太大则不限制（避免超长 IN 列表）
            }
        }
    }

    // ========== 辅助类和工具 ==========

    /**
     * 单个 grain 的 SQL 片段
     */
    private record GrainSqlPart(
            RollupGrain grain,
            int grainIndex,
            String sql,
            List<Object> params
    ) {}

    /**
     * 将列表按固定大小分批
     */
    private static <T> List<List<T>> partition(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }
}
