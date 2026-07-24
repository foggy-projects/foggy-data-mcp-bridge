package com.foggyframework.dataset.model.engine.pivot.rollup;

import com.foggyframework.dataset.model.engine.pivot.CardinalityBreaker;
import com.foggyframework.dataset.model.engine.pivot.PivotTelemetry;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportField;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportPlan;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportTuple;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.port.PivotRollupExecutionPort;
import com.foggyframework.dataset.model.semantic.port.SemanticSqlGeneration;
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

    private final PivotRollupExecutionPort pivotRollupExecutionPort;

    public NonAdditiveRollupExecutor(PivotRollupExecutionPort pivotRollupExecutionPort) {
        this.pivotRollupExecutionPort = pivotRollupExecutionPort;
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
        PivotTelemetry.auxQueryStarted(logger, model, grains.size(), auxMetrics.size());

        // 尝试 UNION ALL 批量合并
        boolean batchSuccess = tryBatchExecute(
                model, originalRequest, context, grains, auxMetrics,
                rowFields, colFields, survivingRowDomain, survivingColDomain, cache);

        if (!batchSuccess) {
            // 降级: 逐 grain 串行执行
            long serialStart = System.currentTimeMillis();
            logger.info("[Pivot] Falling back to per-grain serial execution");
            for (RollupGrain grain : grains) {
                executeGrainSerial(model, originalRequest, context, grain, auxMetrics,
                        rowFields, colFields, survivingRowDomain, survivingColDomain, cache);
            }
            PivotTelemetry.auxQueryCompleted(logger, model, "serial", grains.size(), grains.size(),
                    auxMetrics.size(), System.currentTimeMillis() - serialStart);
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
            PivotTelemetry.auxQueryCompleted(logger, model, "union_all", grains.size(), batches.size(),
                    auxMetrics.size(), elapsed);
            return true;

        } catch (Exception e) {
            PivotTelemetry.auxQueryFallback(logger, model, e);
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
        List<Map<String, Object>> rows = pivotRollupExecutionPort.executeRollupSql(
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

        List<DomainTransportPlan> domainTransportPlans = new ArrayList<>();
        SemanticQueryRequest auxRequest = buildGrainRequest(
                grain, auxMetrics, originalRequest, rowFields, colFields,
                survivingRowDomain, survivingColDomain, domainTransportPlans);
        logDomainTransportPlans(model, domainTransportPlans);

        SemanticSqlGeneration sqlResult = pivotRollupExecutionPort.generateRollupSql(model, auxRequest,
                withDomainTransportPlans(context, domainTransportPlans));
        if (sqlResult == null || sqlResult.sql() == null || sqlResult.sql().isBlank()) {
            logger.warn("[Pivot] generateSql returned null for grain={}", grain.getGrainKey());
            return null;
        }

        return new GrainSqlPart(grain, grainIndex, sqlResult.sql(), sqlResult.params());
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
            Set<List<Object>> survivingColDomain,
            List<DomainTransportPlan> domainTransportPlans) {

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
        // Stage 4 语义修正：使用完整 axisFields tuple 约束，grainFields 只决定 GROUP BY
        addSurvivingDomainConstraints(sliceItems, rowFields, colFields,
                survivingRowDomain, survivingColDomain, domainTransportPlans);

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

        List<DomainTransportPlan> domainTransportPlans = new ArrayList<>();
        SemanticQueryRequest auxRequest = buildGrainRequest(
                grain, auxMetrics, originalRequest, rowFields, colFields,
                survivingRowDomain, survivingColDomain, domainTransportPlans);
        logDomainTransportPlans(model, domainTransportPlans);

        logger.debug("[Pivot] Serial aux query: grain={}, fields={}", grain.getGrainKey(), grain.getGroupByFields());

        SemanticQueryResponse response = pivotRollupExecutionPort.queryModel(
                model, auxRequest, "execute", withDomainTransportPlans(context, domainTransportPlans));

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
     *
     * <p>Stage 4 语义修正：WHERE 约束始终基于完整的 axisFields tuple，
     * 与 grainFields（GROUP BY 粒度）无关。
     * grainFields 只决定辅助查询的 GROUP BY，不能限制 WHERE 的字段范围。</p>
     */
    private static void addSurvivingDomainSlice(
            List<SemanticQueryRequest.SliceItem> sliceItems,
            List<String> rowFields,
            List<String> colFields,
            Set<List<Object>> survivingRowDomain,
            Set<List<Object>> survivingColDomain) {

        addAxisDomainSlice(sliceItems, rowFields, survivingRowDomain);
        addAxisDomainSlice(sliceItems, colFields, survivingColDomain);
    }

    private static void addSurvivingDomainConstraints(
            List<SemanticQueryRequest.SliceItem> sliceItems,
            List<String> rowFields,
            List<String> colFields,
            Set<List<Object>> survivingRowDomain,
            Set<List<Object>> survivingColDomain,
            List<DomainTransportPlan> domainTransportPlans) {

        addAxisDomainConstraint(sliceItems, rowFields, survivingRowDomain, domainTransportPlans);
        addAxisDomainConstraint(sliceItems, colFields, survivingColDomain, domainTransportPlans);
    }

    // package-private for Stage 5A tests
    static void addAxisDomainConstraint(
            List<SemanticQueryRequest.SliceItem> sliceItems,
            List<String> axisFields,
            Set<List<Object>> domain,
            List<DomainTransportPlan> domainTransportPlans) {

        if (domain == null || domain.isEmpty()) return;
        if (axisFields == null || axisFields.isEmpty()) return;
        if (domain.size() <= MAX_IN_LIST_SIZE) {
            addAxisDomainSlice(sliceItems, axisFields, domain);
            return;
        }
        domainTransportPlans.add(buildDomainTransportPlan(axisFields, domain, domainTransportPlans.size()));
    }

    private static DomainTransportPlan buildDomainTransportPlan(
            List<String> axisFields,
            Set<List<Object>> domain,
            int index) {

        List<DomainTransportField> fields = axisFields.stream()
                .map(DomainTransportField::new)
                .collect(Collectors.toList());
        List<DomainTransportTuple> tuples = domain.stream()
                .map(tuple -> new DomainTransportTuple(new ArrayList<>(tuple)))
                .collect(Collectors.toList());

        return DomainTransportPlan.builder()
                .relationName("_pivot_domain_transport_" + index)
                .fields(fields)
                .tuples(tuples)
                .build();
    }

    private static SemanticRequestContext withDomainTransportPlans(
            SemanticRequestContext context,
            List<DomainTransportPlan> domainTransportPlans) {

        if (domainTransportPlans == null || domainTransportPlans.isEmpty()) {
            return context;
        }
        if (context == null) {
            context = SemanticRequestContext.empty();
        }
        List<DomainTransportPlan> merged = new ArrayList<>();
        if (context.getDomainTransportPlans() != null) {
            merged.addAll(context.getDomainTransportPlans());
        }
        merged.addAll(domainTransportPlans);
        return context.withDomainTransportPlans(merged);
    }

    private void logDomainTransportPlans(String model, List<DomainTransportPlan> domainTransportPlans) {
        if (domainTransportPlans == null || domainTransportPlans.isEmpty()) {
            return;
        }
        for (DomainTransportPlan plan : domainTransportPlans) {
            PivotTelemetry.domainTransportPlanned(logger, model, plan.getRelationName(),
                    plan.getFields().size(), plan.getTuples().size(), plan.parameterCount());
        }
    }

    /**
     * 对单个轴的 surviving domain 生成精确过滤条件
     *
     * <p>Stage 4 语义修正：</p>
     * <ul>
     *   <li>WHERE 约束基于完整 axisFields tuple，与 grainFields 无关。
     *       grainFields 只决定 GROUP BY 粒度，不决定 WHERE 约束字段范围。
     *       即使 subtotal grain=[category]，WHERE 仍然约束完整的 (category, product) tuple，
     *       确保 AVG/COUNT_DISTINCT 辅助查询不把 TopN 过滤掉的 product 算回来。</li>
     *   <li>单字段轴：生成 IN(非null值) + IS NULL 组合（通过 $or 连接）</li>
     *   <li>多字段轴：生成 OR-of-AND tuple constraint，null 值用 'is null' op 表达</li>
     *   <li>domain 超过 {@link #MAX_IN_LIST_SIZE}：fail-closed，抛出 {@link NonAdditiveRollupDomainTooLargeException}</li>
     * </ul>
     */
    // package-private for testing
    static void addAxisDomainSlice(
            List<SemanticQueryRequest.SliceItem> sliceItems,
            List<String> axisFields,
            Set<List<Object>> domain) {

        if (domain == null || domain.isEmpty()) return;
        if (axisFields == null || axisFields.isEmpty()) return;

        // domain 超限检查 — fail-closed（不能静默跳过）
        if (domain.size() > MAX_IN_LIST_SIZE) {
            throw new NonAdditiveRollupDomainTooLargeException(domain.size(), MAX_IN_LIST_SIZE);
        }

        if (axisFields.size() == 1) {
            // 单字段轴：IN(非null) + 可选 IS NULL
            String field = axisFields.get(0);
            Set<Object> nonNullValues = new LinkedHashSet<>();
            boolean hasNullTuple = false;

            for (List<Object> tuple : domain) {
                Object val = tuple.get(0);
                if (val == null) {
                    hasNullTuple = true;
                } else {
                    nonNullValues.add(val);
                }
            }

            if (!nonNullValues.isEmpty() && !hasNullTuple) {
                // 纯非 null 值：简单 IN
                SemanticQueryRequest.SliceItem slice = new SemanticQueryRequest.SliceItem();
                slice.setField(field);
                slice.setOp("in");
                slice.setValue(new ArrayList<>(nonNullValues));
                sliceItems.add(slice);
            } else if (!nonNullValues.isEmpty()) {
                // 混合 null + 非null：OR(IN(...), IS NULL)
                SemanticQueryRequest.SliceItem inCond = new SemanticQueryRequest.SliceItem();
                inCond.setField(field);
                inCond.setOp("in");
                inCond.setValue(new ArrayList<>(nonNullValues));

                SemanticQueryRequest.SliceItem isNullCond = new SemanticQueryRequest.SliceItem();
                isNullCond.setField(field);
                isNullCond.setOp("is null");

                SemanticQueryRequest.SliceItem orGroup = new SemanticQueryRequest.SliceItem();
                orGroup.setOr(List.of(inCond, isNullCond));
                sliceItems.add(orGroup);
            } else if (hasNullTuple) {
                // 全部是 null
                SemanticQueryRequest.SliceItem isNullCond = new SemanticQueryRequest.SliceItem();
                isNullCond.setField(field);
                isNullCond.setOp("is null");
                sliceItems.add(isNullCond);
            }
        } else {
            // 多字段轴：生成 OR-of-AND tuple constraint
            // 形如: OR( AND(cat='A', prod='p1'), AND(cat='B', prod IS NULL) )
            // 使用完整 axisFields 索引，保留所有字段的 tuple 相关性
            List<SemanticQueryRequest.SliceItem> andGroups = new ArrayList<>();
            for (List<Object> tuple : domain) {
                List<SemanticQueryRequest.SliceItem> andConditions = new ArrayList<>();

                for (int i = 0; i < axisFields.size(); i++) {
                    Object val = tuple.get(i);
                    SemanticQueryRequest.SliceItem cond = new SemanticQueryRequest.SliceItem();
                    cond.setField(axisFields.get(i));
                    if (val == null) {
                        cond.setOp("is null");
                    } else {
                        cond.setOp("=");
                        cond.setValue(val);
                    }
                    andConditions.add(cond);
                }

                if (!andConditions.isEmpty()) {
                    SemanticQueryRequest.SliceItem andGroup = new SemanticQueryRequest.SliceItem();
                    andGroup.setAnd(andConditions);
                    andGroups.add(andGroup);
                }
            }

            if (!andGroups.isEmpty()) {
                SemanticQueryRequest.SliceItem orGroup = new SemanticQueryRequest.SliceItem();
                orGroup.setOr(andGroups);
                sliceItems.add(orGroup);
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
