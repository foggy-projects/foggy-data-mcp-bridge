package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.engine.pivot.algo.*;
import com.foggyframework.dataset.db.model.engine.pivot.rollup.*;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotMetricItem;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotOptions;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pivot 四阶段流水线编排器
 *
 * <p>Pipeline 执行流程：</p>
 * <pre>
 *   Phase 1:   SQL 萃取 — 调用现有 QueryFacade 执行朴素 GROUP BY
 *   Phase 1.5: 父子维度建树 — hierarchyMode=tree 时额外查询维度骨架
 *   Phase 2:   内存加工 — Having → TopN → Rollup规划 → 辅助查询 → CrossJoin → Subtotal(cache-aware)
 *   Phase 3:   结果整形 — 转换为 tree / grid / flat
 * </pre>
 *
 * <p>职责边界：SQL 层只做最朴素的聚合，所有高级加工在内存完成。</p>
 */
public class PivotPipeline {

    private static final Logger logger = LoggerFactory.getLogger(PivotPipeline.class);

    private final SemanticQueryServiceV3 semanticQueryService;
    private final CardinalityBreaker cardinalityBreaker;
    private final QueryModelLoader queryModelLoader;

    public PivotPipeline(SemanticQueryServiceV3 semanticQueryService) {
        this(semanticQueryService, new CardinalityBreaker(), null);
    }

    public PivotPipeline(SemanticQueryServiceV3 semanticQueryService, CardinalityBreaker cardinalityBreaker) {
        this(semanticQueryService, cardinalityBreaker, null);
    }

    public PivotPipeline(SemanticQueryServiceV3 semanticQueryService,
                         CardinalityBreaker cardinalityBreaker,
                         QueryModelLoader queryModelLoader) {
        this.semanticQueryService = semanticQueryService;
        this.cardinalityBreaker = cardinalityBreaker;
        this.queryModelLoader = queryModelLoader;
    }

    /**
     * 执行 Pivot 透视查询
     *
     * @param model   模型名称
     * @param request 语义查询请求（已确认 isPivotMode() == true）
     * @param context 请求上下文
     * @return 语义查询响应（Pivot 格式）
     */
    public SemanticQueryResponse execute(String model, SemanticQueryRequest request,
                                          SemanticRequestContext context) {
        PivotRequest pivot = request.getPivot();
        long startTime = System.currentTimeMillis();

        // ===== 前置校验 =====
        validatePivotRequest(request);

        // ===== 提前加载 QueryModel（用于 S8.3 度量元数据 + Properties）=====
        QueryModel queryModel = null;
        if (queryModelLoader != null) {
            queryModel = queryModelLoader.getJdbcQueryModel(model, context.getNamespace());
        }

        // ===== S11: parentShare non-additive guard（需 queryModel 已加载）=====
        if (!pivot.getParentShareMetrics().isEmpty() && queryModel != null) {
            ParentShareCalculator.validateAdditivity(pivot, queryModel);
        }

        // ===== S12: baselineRatio guard =====
        if (!pivot.getBaselineRatioMetrics().isEmpty()) {
            if (queryModel != null) {
                BaselineRatioCalculator.validateAdditivity(pivot, queryModel);
            }
            // baselineRatio.of 必须在原生度量中声明
            List<String> nativeMetrics = pivot.getNativeMetricNames();
            for (PivotMetricItem br : pivot.getBaselineRatioMetrics()) {
                if (!nativeMetrics.contains(br.getOf())) {
                    throw new IllegalArgumentException(
                            "baselineRatio 派生指标 '" + br.getName() + "' 依赖的原生度量 '" + br.getOf() +
                            "' 未在 pivot.metrics 中声明");
                }
            }
            // baselineRatio 第一版要求 columns 必须有层级
            if (pivot.getColumns() == null || pivot.getColumns().isEmpty()) {
                throw new IllegalArgumentException("使用 baselineRatio 派生指标时，columns 轴不能为空");
            }
        }

        // ===== 检测 hierarchyMode=tree =====
        HierarchyContext hierarchyCtx = HierarchyContext.detect(pivot.getRows());
        if (hierarchyCtx.isTree() && !pivot.getBaselineRatioMetrics().isEmpty()) {
            throw new IllegalArgumentException("当前版本不支持 hierarchyMode=tree 与 baselineRatio 派生指标同时使用");
        }

        List<String> rowFields = extractFieldNames(pivot.getRows());
        List<String> colFields = extractFieldNames(pivot.getColumns());
        List<String> metrics = pivot.getSqlMetricNames();

        // 如果有 tree hierarchy，确保 Phase 1 带出 $id 字段
        if (hierarchyCtx.isTree()) {
            rowFields = hierarchyCtx.ensureIdField(rowFields);
        }

        // ===== Properties 函数依赖预校验 =====
        Set<String> allAxisFields = new LinkedHashSet<>();
        allAxisFields.addAll(rowFields);
        allAxisFields.addAll(colFields);

        List<PropertyResolver.ResolvedProperty> resolvedProps = Collections.emptyList();
        if (pivot.getProperties() != null && !pivot.getProperties().isEmpty()) {
            if (queryModel != null) {
                resolvedProps = PropertyResolver.resolve(queryModel, pivot.getProperties(), allAxisFields);
                logger.debug("[Pivot] Properties validated: {}", resolvedProps);
            } else {
                logger.debug("[Pivot] QueryModelLoader not available, properties will be included in Phase 1 as fallback");
            }
        }

        // ===== Phase 1: SQL 萃取（不含 properties）=====
        logger.debug("[Pivot] Phase 1: SQL aggregation for model={}", model);
        List<Map<String, Object>> resultSet = executePhase1(model, request, context,
                rowFields, colFields, metrics, queryModel);

        if (resultSet.isEmpty()) {
            return buildEmptyResponse(pivot, startTime);
        }

        // ===== Phase 1.5: 父子维度骨架查询 =====
        HierarchyTreeBuilder.Skeleton hierarchySkeleton = null;
        if (hierarchyCtx.isTree()) {
            logger.debug("[Pivot] Phase 1.5: Hierarchy skeleton query for dim={}", hierarchyCtx.getDimName());
            hierarchySkeleton = executeHierarchySkeleton(
                    model, request, context, hierarchyCtx.getDimName(), hierarchyCtx.getIdField());
        }

        // ===== Phase 2: 内存加工 =====
        logger.debug("[Pivot] Phase 2: Memory cube processing, {} rows", resultSet.size());

        // 2.1 轴级 Having 过滤
        resultSet = AxisHavingFilter.apply(resultSet, pivot.getRows(), metrics);
        resultSet = AxisHavingFilter.apply(resultSet, pivot.getColumns(), metrics);

        // 2.2 轴向 TopN 截断
        resultSet = AxisTopNTruncator.apply(resultSet, pivot.getRows());
        resultSet = AxisTopNTruncator.apply(resultSet, pivot.getColumns());

        // 提取域并执行基数熔断校验
        Set<List<Object>> rowDomain = CardinalityBreaker.extractRowDomain(resultSet, rowFields);
        Set<List<Object>> colDomain = CardinalityBreaker.extractColumnDomain(resultSet, colFields);
        cardinalityBreaker.checkEstimate(rowDomain.size(), colDomain.size(), pivot);

        PivotOptions options = pivot.getOptions() != null ? pivot.getOptions() : new PivotOptions();
        boolean needsSubtotal = options.isRowSubtotals() || options.isColumnSubtotals() || options.isGrandTotal();

        // 2.3 Rollup 规划与辅助查询（仅在需要小计/总计时执行）
        List<RollupMetricPlan> rollupPlans = Collections.emptyList();
        RollupCache rollupCache = new RollupCache();
        if (needsSubtotal) {
            rollupPlans = MetricAdditivityAnalyzer.analyze(
                    metrics, queryModel, request.getCalculatedFields());
            logger.debug("[Pivot] Phase 2.3: Rollup plans: {}", rollupPlans);

            // 检查是否有不支持的 metric 参与 subtotal
            for (RollupMetricPlan plan : rollupPlans) {
                if (plan.getStrategy() == RollupStrategy.UNSUPPORTED) {
                    throw new IllegalArgumentException(
                            "度量 '" + plan.getMetricName() + "' 的聚合类型（" +
                            plan.getAggregation() + "）不支持参与小计/总计。" +
                            "请移除该度量或关闭 rowSubtotals/columnSubtotals/grandTotal");
                }
            }

            // 如果有 non-additive metrics，执行辅助查询
            if (MetricAdditivityAnalyzer.hasNonAdditiveMetrics(rollupPlans)) {
                List<RollupGrain> grains = RollupGrainEnumerator.enumerate(rowFields, colFields, options);
                logger.debug("[Pivot] Phase 2.4: Auxiliary rollup queries, {} grains", grains.size());

                NonAdditiveRollupExecutor executor = new NonAdditiveRollupExecutor(semanticQueryService);
                rollupCache = executor.execute(model, request, context,
                        grains, rollupPlans, rowFields, colFields, rowDomain, colDomain);
            }
        }

        // 2.5 骨架补全
        if (options.isCrossjoin()) {
            resultSet = CrossJoinFiller.apply(resultSet, rowFields, colFields, metrics, rowDomain, colDomain);
        }

        // 2.6 小计/总计注入 (cache-aware)
        if (needsSubtotal) {
            resultSet = SubtotalInjector.apply(resultSet, rowFields, colFields, metrics, options,
                    rollupPlans, rollupCache);
        }

        // ===== Phase 2.7: Properties 后置贴合 =====
        if (!resolvedProps.isEmpty()) {
            logger.debug("[Pivot] Phase 2.7: Property attachment for {} properties", resolvedProps.size());
            Map<String, Map<Object, Map<String, Object>>> lookupTables =
                    executePropertyLookup(model, request, context, resolvedProps);
            PropertyAttacher.attach(resultSet, resolvedProps, lookupTables);
        }

        // ===== Phase 2.8: ParentShare 父级占比计算 =====
        if (!pivot.getParentShareMetrics().isEmpty()) {
            logger.debug("[Pivot] Phase 2.8: ParentShare calculation, {} metrics",
                    pivot.getParentShareMetrics().size());
            ParentShareCalculator.apply(resultSet, pivot, rowFields, colFields);
        }

        // ===== Phase 2.9: BaselineRatio 基准引用计算 =====
        if (!pivot.getBaselineRatioMetrics().isEmpty()) {
            logger.debug("[Pivot] Phase 2.9: BaselineRatio calculation, {} metrics",
                    pivot.getBaselineRatioMetrics().size());
            BaselineRatioCalculator.apply(resultSet, pivot, rowFields, colFields);
        }

        // ===== Phase 3: 结果整形 =====
        // S11: Phase 3 使用所有输出指标名（含 parentShare），而非仅 SQL 指标
        List<String> outputMetrics = pivot.getAllOutputMetricNames();
        PivotResult pivotResult;
        if (hierarchySkeleton != null && !hierarchySkeleton.isEmpty()) {
            // 使用 HierarchyTreeBuilder 替代 ResultShaper
            // 注意：tree + parentShare 已在 validate 中 fail-closed，此处不会包含 parentShare
            logger.debug("[Pivot] Phase 3: Hierarchy tree shaping, skeleton size={}", hierarchySkeleton.size());
            List<String> displayFields = extractFieldNames(pivot.getRows());
            List<PivotResult.TreeNode> treeData = HierarchyTreeBuilder.build(
                    resultSet, hierarchySkeleton, hierarchyCtx.getIdField(),
                    displayFields, colFields, outputMetrics);
            pivotResult = new PivotResult();
            pivotResult.setFormat("tree");
            pivotResult.setTreeData(treeData);
        } else {
            logger.debug("[Pivot] Phase 3: Result shaping to format={}", pivot.getOutputFormat());
            pivotResult = ResultShaper.shape(resultSet, pivot, rowFields, colFields, outputMetrics);
        }

        // ===== 构建响应 =====
        return buildPivotResponse(pivotResult, startTime);
    }

    /**
     * Phase 1: 调用现有语义查询服务执行朴素 GROUP BY（不含 properties）
     *
     * <p>S8.3.0: metric 的聚合类型从 QueryModel/TM 的 DbMeasure.getAggregation() 读取，
     * 不再对所有 metric 硬编码 SUM。</p>
     */
    private List<Map<String, Object>> executePhase1(String model, SemanticQueryRequest originalRequest,
                                                     SemanticRequestContext context,
                                                     List<String> rowFields, List<String> colFields,
                                                     List<String> metrics, QueryModel queryModel) {
        // 构建扁平查询请求
        SemanticQueryRequest flatRequest = new SemanticQueryRequest();

        // columns = rows + columns + metrics（不含 properties，避免扩大分组粒度）
        List<String> allColumns = new ArrayList<>();
        allColumns.addAll(rowFields);
        allColumns.addAll(colFields);
        allColumns.addAll(metrics);
        flatRequest.setColumns(allColumns);

        // groupBy = rows + columns (维度字段)，metrics 用度量元数据中的聚合类型
        List<SemanticQueryRequest.GroupByItem> groupBy = new ArrayList<>();
        for (String dim : rowFields) {
            groupBy.add(new SemanticQueryRequest.GroupByItem(dim, null));
        }
        for (String dim : colFields) {
            groupBy.add(new SemanticQueryRequest.GroupByItem(dim, null));
        }
        for (String metric : metrics) {
            String aggStr = resolveMetricAggregation(metric, queryModel);
            groupBy.add(new SemanticQueryRequest.GroupByItem(metric, aggStr));
        }
        flatRequest.setGroupBy(groupBy);

        // 透传 slice 和 calculatedFields
        flatRequest.setSlice(originalRequest.getSlice());
        flatRequest.setCalculatedFields(originalRequest.getCalculatedFields());

        // 不分页，取全量
        flatRequest.setLimit(CardinalityBreaker.DEFAULT_ROW_LIMIT * CardinalityBreaker.DEFAULT_COL_LIMIT);
        flatRequest.setReturnTotal(false);

        SemanticQueryResponse response = semanticQueryService.queryModel(model, flatRequest, "execute", context);
        return response.getItems() != null ? response.getItems() : Collections.emptyList();
    }

    /**
     * 从 QueryModel/TM 解析 metric 的默认聚合类型字符串
     *
     * <p>S8.3.0: 用于 Phase 1 生成正确的 GroupByItem.agg。</p>
     *
     * @return 聚合类型字符串（如 "SUM", "AVG", "COUNT_DISTINCT"），找不到时返回 "SUM"（向后兼容）
     */
    private String resolveMetricAggregation(String metricName, QueryModel queryModel) {
        DbAggregation agg = MetricAdditivityAnalyzer.resolveAggregation(metricName, queryModel);
        if (agg == null) {
            // 找不到度量元数据（可能是 calculatedField），默认 SUM 向后兼容
            return "SUM";
        }

        return switch (agg) {
            case SUM -> "SUM";
            case AVG -> "AVG";
            case COUNT -> "COUNT";
            case COUNT_DISTINCT -> "COUNT_DISTINCT";
            case MIN -> "MIN";
            case MAX -> "MAX";
            case NONE, PK -> "SUM"; // 无聚合属性的度量默认 SUM
            default -> "SUM";
        };
    }

    /**
     * 为已验证的 properties 执行辅助维度查询，构建 lookup table
     *
     * <p>对每个关联维度发一次 SELECT DISTINCT dim$id, dim$prop1, dim$prop2 ...
     * 构建 Map&lt;dimKeyValue, Map&lt;propFieldName, propValue&gt;&gt;</p>
     */
    private Map<String, Map<Object, Map<String, Object>>> executePropertyLookup(
            String model, SemanticQueryRequest originalRequest,
            SemanticRequestContext context,
            List<PropertyResolver.ResolvedProperty> resolvedProps) {

        // 按维度名分组
        Map<String, List<PropertyResolver.ResolvedProperty>> byDim = resolvedProps.stream()
                .collect(Collectors.groupingBy(PropertyResolver.ResolvedProperty::getDimensionName));

        Map<String, Map<Object, Map<String, Object>>> result = new LinkedHashMap<>();

        for (Map.Entry<String, List<PropertyResolver.ResolvedProperty>> entry : byDim.entrySet()) {
            String dimName = entry.getKey();
            List<PropertyResolver.ResolvedProperty> props = entry.getValue();
            String keyField = props.get(0).getLookupKeyField();

            // 构建辅助查询：SELECT DISTINCT keyField, prop1, prop2, ...
            List<String> selectFields = new ArrayList<>();
            selectFields.add(keyField);
            List<String> propFieldNames = new ArrayList<>();
            for (PropertyResolver.ResolvedProperty p : props) {
                selectFields.add(p.getFullFieldName());
                propFieldNames.add(p.getFullFieldName());
            }

            SemanticQueryRequest lookupRequest = new SemanticQueryRequest();
            lookupRequest.setColumns(selectFields);
            lookupRequest.setDistinct(true);
            lookupRequest.setSlice(originalRequest.getSlice()); // 透传 slice 保持数据一致性
            lookupRequest.setLimit(CardinalityBreaker.DEFAULT_ROW_LIMIT);
            lookupRequest.setReturnTotal(false);

            logger.debug("[Pivot] Property lookup: dim={}, fields={}", dimName, selectFields);

            SemanticQueryResponse response = semanticQueryService.queryModel(
                    model, lookupRequest, "execute", context);

            List<Map<String, Object>> lookupRows = response.getItems() != null
                    ? response.getItems() : Collections.emptyList();

            Map<Object, Map<String, Object>> lookupTable =
                    PropertyAttacher.buildLookupTable(lookupRows, keyField, propFieldNames);

            result.put(dimName, lookupTable);
        }

        return result;
    }

    /**
     * Phase 1.5: 查询维度邻接表骨架（SELECT DISTINCT nodeId, parentId）
     *
     * <p>Fail-Closed：查询失败时直接抛出异常，不做降级。
     * 降级到普通树会因隐式注入的 $id 字段产生错误的树结构。</p>
     */
    private HierarchyTreeBuilder.Skeleton executeHierarchySkeleton(
            String model, SemanticQueryRequest originalRequest,
            SemanticRequestContext context,
            String dimName, String idField) {

        String parentField = dimName + "$parentId";

        // 构建辅助查询：SELECT DISTINCT id, parentId FROM model
        SemanticQueryRequest skeletonRequest = new SemanticQueryRequest();
        skeletonRequest.setColumns(List.of(idField, parentField));
        skeletonRequest.setDistinct(true);
        // 不透传 slice — 骨架需要完整的维度层级关系
        skeletonRequest.setLimit(CardinalityBreaker.DEFAULT_ROW_LIMIT);
        skeletonRequest.setReturnTotal(false);

        logger.debug("[Pivot] Hierarchy skeleton: SELECT DISTINCT {}, {} FROM {}",
                idField, parentField, model);

        SemanticQueryResponse response = semanticQueryService.queryModel(
                model, skeletonRequest, "execute", context);

        List<Map<String, Object>> rows = response.getItems() != null
                ? response.getItems() : Collections.emptyList();

        // 骨架截断警告：如果触达 limit 上限，可能丢失部分层级关系
        if (rows.size() >= CardinalityBreaker.DEFAULT_ROW_LIMIT) {
            logger.warn("[Pivot] Hierarchy skeleton may be incomplete: limit={} reached for dim={}. " +
                    "Tree structure may be partially correct.",
                    CardinalityBreaker.DEFAULT_ROW_LIMIT, dimName);
        }

        logger.debug("[Pivot] Hierarchy skeleton loaded: {} nodes", rows.size());
        return HierarchyTreeBuilder.Skeleton.fromRows(rows, idField, parentField);
    }


    /**
     * 前置校验
     */
    private void validatePivotRequest(SemanticQueryRequest request) {
        PivotRequest pivot = request.getPivot();

        // pivot 与 columns 互斥
        if (request.getColumns() != null && !request.getColumns().isEmpty()) {
            throw new IllegalArgumentException("pivot 与 columns 不能同时出现。使用 pivot 模式时请移除 columns 字段");
        }

        // pivot 与 timeWindow 互斥
        if (request.getTimeWindow() != null && !request.getTimeWindow().isEmpty()) {
            throw new IllegalArgumentException(
                    "timeWindow 与 pivot 模式互斥。时间智能需求请使用 calculatedFields + CALCULATE/OFFSET 表达");
        }

        // 基本完整性校验
        if (pivot.getRows() == null || pivot.getRows().isEmpty()) {
            throw new IllegalArgumentException("pivot.rows 不能为空");
        }
        if (pivot.getMetricItems() == null || pivot.getMetricItems().isEmpty()) {
            throw new IllegalArgumentException("pivot.metrics 不能为空");
        }

        // S11: 校验 metric items
        pivot.validateMetrics();

        // ===== hierarchyMode=tree 守卫规则 =====
        HierarchyContext rowHierarchy = HierarchyContext.detect(pivot.getRows());
        HierarchyContext colHierarchy = HierarchyContext.detect(pivot.getColumns());

        // columns 轴不支持 hierarchyMode=tree
        if (colHierarchy.isTree()) {
            throw new IllegalArgumentException(
                    "hierarchyMode=tree 当前仅支持 rows 轴。请将 '" + colHierarchy.getTreeAxisField().getField() +
                    "' 从 columns 移到 rows，或移除 hierarchyMode");
        }

        if (rowHierarchy.isTree()) {
            // tree + outputFormat != tree 拒绝
            String format = pivot.getOutputFormat() != null ? pivot.getOutputFormat() : "tree";
            if (!"tree".equals(format)) {
                throw new IllegalArgumentException(
                        "hierarchyMode=tree 仅支持 outputFormat=tree。当前 outputFormat='" + format +
                        "'，请改为 tree 或移除 hierarchyMode");
            }

            // tree + crossjoin 拒绝
            PivotOptions options = pivot.getOptions();
            if (options != null && options.isCrossjoin()) {
                throw new IllegalArgumentException(
                        "hierarchyMode=tree 与 crossjoin=true 不兼容。" +
                        "父子层级树无法生成笛卡尔积骨架，请移除 crossjoin 或 hierarchyMode");
            }

            // rows 中只能有一个 tree 字段
            long treeCount = pivot.getRows().stream().filter(AxisField::isTreeMode).count();
            if (treeCount > 1) {
                throw new IllegalArgumentException(
                        "rows 中最多只能有一个 hierarchyMode=tree 字段。当前有 " + treeCount + " 个");
            }

            // ===== S8.3: tree + non-additive + subtotals 拒绝 =====
            PivotOptions opts = pivot.getOptions();
            boolean hasSubtotal = opts != null &&
                    (opts.isRowSubtotals() || opts.isColumnSubtotals() || opts.isGrandTotal());
            if (hasSubtotal) {
                // 此处无法直接判断 non-additive（QueryModel 未加载），
                // 在 execute() 中 rollupPlans 生成后再做精细判定不合适，
                // 因此对 tree + subtotals 整体先拒绝（第一版约束）
                throw new IllegalArgumentException(
                        "hierarchyMode=tree 暂不支持小计/总计辅助聚合。" +
                        "请移除 rowSubtotals/columnSubtotals/grandTotal，或移除 hierarchyMode");
            }
        }

        // options 合法性校验
        if (pivot.getOptions() != null) {
            pivot.getOptions().validate(pivot);
        }

        // S11: parentShare 守卫规则
        if (!pivot.getParentShareMetrics().isEmpty()) {
            List<String> rowFieldNames = extractFieldNames(pivot.getRows());
            List<String> colFieldNames = extractFieldNames(pivot.getColumns());
            ParentShareCalculator.validateParentShareMetrics(pivot, rowFieldNames, colFieldNames);
        }

        // S11: parentShare non-additive guard（需 queryModel，在 pipeline 层执行）
        // 注意：此处 queryModel 尚未加载，延迟到 execute() 中处理
    }

    /**
     * 从 AxisField 列表提取字段名
     */
    private List<String> extractFieldNames(List<AxisField> axisFields) {
        if (axisFields == null) return Collections.emptyList();
        return axisFields.stream()
                .map(AxisField::getField)
                .collect(Collectors.toList());
    }

    /**
     * 构建空结果响应
     */
    private SemanticQueryResponse buildEmptyResponse(PivotRequest pivot, long startTime) {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(Collections.emptyList());
        response.setTotal(0L);

        SemanticQueryResponse.DebugInfo debugInfo = new SemanticQueryResponse.DebugInfo();
        debugInfo.setDurationMs(System.currentTimeMillis() - startTime);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("pipeline", "pivot");
        extra.put("format", pivot.getOutputFormat());
        debugInfo.setExtra(extra);
        response.setDebug(debugInfo);

        return response;
    }

    /**
     * 将 PivotResult 转换为 SemanticQueryResponse
     */
    private SemanticQueryResponse buildPivotResponse(PivotResult pivotResult, long startTime) {
        SemanticQueryResponse response = new SemanticQueryResponse();

        // 将 pivot 结果放入 items（对于 flat 模式）或专用字段
        // 为兼容现有 SemanticQueryResponse 结构，将 pivot 结果放入 extData
        Map<String, Object> pivotData = new LinkedHashMap<>();
        pivotData.put("format", pivotResult.getFormat());

        switch (pivotResult.getFormat()) {
            case "tree":
                pivotData.put("data", pivotResult.getTreeData());
                break;
            case "grid":
                pivotData.put("rowHeaders", pivotResult.getRowHeaders());
                pivotData.put("columnHeaders", pivotResult.getColumnHeaders());
                pivotData.put("cells", pivotResult.getCells());
                break;
            case "flat":
            default:
                response.setItems(pivotResult.getFlatData());
                break;
        }

        if (pivotResult.getLayout() != null) {
            pivotData.put("layout", pivotResult.getLayout());
        }

        // 通过 items 返回 pivot 格式化数据（tree/grid 模式包装为单条 pivotData）
        if (!"flat".equals(pivotResult.getFormat())) {
            response.setItems(List.of(pivotData));
        }

        response.setWarnings(pivotResult.getWarnings());

        SemanticQueryResponse.DebugInfo debugInfo = new SemanticQueryResponse.DebugInfo();
        debugInfo.setDurationMs(System.currentTimeMillis() - startTime);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("pipeline", "pivot");
        extra.put("format", pivotResult.getFormat());
        debugInfo.setExtra(extra);
        response.setDebug(debugInfo);

        return response;
    }
}
