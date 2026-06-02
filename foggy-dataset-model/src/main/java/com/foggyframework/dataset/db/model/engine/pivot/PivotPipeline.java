package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.engine.pivot.algo.*;
import com.foggyframework.dataset.db.model.engine.pivot.cascade.PivotCascadeException;
import com.foggyframework.dataset.db.model.engine.pivot.cascade.PivotCascadeRules;
import com.foggyframework.dataset.db.model.engine.pivot.rollup.*;
import com.foggyframework.dataset.db.model.engine.pivot.sql.PivotAxisDomainSqlPlanner;
import com.foggyframework.dataset.db.model.engine.pivot.sql.PivotPushdownUnsupportedException;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportField;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportPlan;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportTuple;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.plugins.query_execution.ManagedRelationOptions;
import com.foggyframework.dataset.db.model.plugins.query_execution.ManagedSqlRelation;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.MetricFilter;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotMetricItem;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotOptions;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.service.QueryFacade;
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
 *              （如果 dialect 支持 CTE/Window 且存在 having/limit，则 SQL 下放 having + TopN）
 *   Phase 1.5: 父子维度建树 — hierarchyMode=tree 时额外查询维度骨架
 *   Phase 2:   内存加工 — Having → TopN → Rollup规划 → 辅助查询 → CrossJoin → Subtotal(cache-aware)
 *              （SQL 下放场景下跳过 Having 和 TopN，直接进入 Rollup）
 *   Phase 3:   结果整形 — 转换为 tree / grid / flat
 * </pre>
 *
 * <p>职责边界：SQL 层只做最朴素的聚合，所有高级加工在内存完成。
 * 当 SQL pushdown 启用时，having/TopN 被 PivotAxisDomainSqlPlanner 下放到 SQL 层。</p>
 */
public class PivotPipeline {

    private static final Logger logger = LoggerFactory.getLogger(PivotPipeline.class);
    private static final int AXIS_DOMAIN_TRANSPORT_THRESHOLD = 500;

    private final SemanticQueryServiceV3 semanticQueryService;
    private final CardinalityBreaker cardinalityBreaker;
    private final QueryModelLoader queryModelLoader;
    private final QueryFacade queryFacade;

    public PivotPipeline(SemanticQueryServiceV3 semanticQueryService) {
        this(semanticQueryService, new CardinalityBreaker(), null, null);
    }

    public PivotPipeline(SemanticQueryServiceV3 semanticQueryService, CardinalityBreaker cardinalityBreaker) {
        this(semanticQueryService, cardinalityBreaker, null, null);
    }

    public PivotPipeline(SemanticQueryServiceV3 semanticQueryService,
                         CardinalityBreaker cardinalityBreaker,
                         QueryModelLoader queryModelLoader) {
        this(semanticQueryService, cardinalityBreaker, queryModelLoader, null);
    }

    public PivotPipeline(SemanticQueryServiceV3 semanticQueryService,
                         CardinalityBreaker cardinalityBreaker,
                         QueryModelLoader queryModelLoader,
                         QueryFacade queryFacade) {
        this.semanticQueryService = semanticQueryService;
        this.cardinalityBreaker = cardinalityBreaker;
        this.queryModelLoader = queryModelLoader;
        this.queryFacade = queryFacade;
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
        injectPredefinedCalculatedFields(request, queryModel);
        PivotCascadeRules.validateAdditivity(pivot, queryModel, request.getCalculatedFields());

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
        // 检测是否可以使用 SQL pushdown（CTE + Window Function + 有 having/limit）
        boolean sqlPushdownUsed = false;
        boolean axisDomainSelectionUsed = false;
        boolean axisDomainSelectionRequest = hasAxisDomainSelectionRequest(pivot);
        boolean cascadeRequest = PivotCascadeRules.isCascadeRequest(pivot);
        List<Map<String, Object>> resultSet;
        Map<String, Map<String, Number>> baselineRatioExternalValues = Collections.emptyMap();
        List<Map<String, Object>> baselineRatioEvidence = Collections.emptyList();
        Map<String, Map<String, Number>> parentShareExternalValues = Collections.emptyMap();
        List<Map<String, Object>> parentShareEvidence = Collections.emptyList();

        String sqlPushdownSkipReason = axisDomainSelectionRequest
                ? "axisDomainSelectionRequiresTwoPhaseQuery"
                : getSqlPushdownSkipReason(pivot, hierarchyCtx);
        if (axisDomainSelectionRequest) {
            if (cascadeRequest) {
                throw PivotCascadeException.sqlRequired(
                        "SQL pushdown is unavailable: " + sqlPushdownSkipReason + ".");
            }
            PivotTelemetry.sqlPushdownSkipped(logger, model, sqlPushdownSkipReason);
            logger.debug("[Pivot] Phase 1: Axis domain selection path for model={}", model);
            AxisDomainSelectionResult axisDomainResult = executePhase1WithAxisDomainSelection(model, request, context,
                    rowFields, colFields, metrics, pivot, queryModel);
            resultSet = axisDomainResult.rows();
            baselineRatioExternalValues = axisDomainResult.baselineRatioExternalValues();
            baselineRatioEvidence = axisDomainResult.baselineRatioEvidence();
            axisDomainSelectionUsed = true;
        } else if (sqlPushdownSkipReason == null) {
            logger.debug("[Pivot] Phase 1: SQL pushdown path for model={}", model);
            try {
                PivotTelemetry.sqlPushdownAttempted(logger, model);
                long pushdownStart = System.currentTimeMillis();
                resultSet = executePhase1WithSqlPushdown(model, request, context,
                        rowFields, colFields, metrics, pivot, queryModel);
                sqlPushdownUsed = true;
                PivotTelemetry.sqlPushdownSucceeded(logger, model, resultSet.size(),
                        System.currentTimeMillis() - pushdownStart);
            } catch (PivotCascadeException e) {
                throw e;
            } catch (PivotPushdownUnsupportedException | UnsupportedOperationException e) {
                if (cascadeRequest) {
                    throw PivotCascadeException.sqlRequired(
                            "Planner failure: " + e.getMessage(), e);
                }
                // Fail-closed: fallback to memory path
                PivotTelemetry.sqlPushdownFallback(logger, model, e);
                resultSet = executePhase1(model, request, context,
                        rowFields, colFields, metrics, queryModel);
            }
        } else {
            if (cascadeRequest) {
                throw PivotCascadeException.sqlRequired(
                        "SQL pushdown is unavailable: " + sqlPushdownSkipReason + ".");
            }
            PivotTelemetry.sqlPushdownSkipped(logger, model, sqlPushdownSkipReason);
            logger.debug("[Pivot] Phase 1: Memory path for model={}", model);
            resultSet = executePhase1(model, request, context,
                    rowFields, colFields, metrics, queryModel);
        }

        if (resultSet.isEmpty()) {
            Map<String, Object> capabilityContract = buildPivotCapabilityContract(
                    pivot, pivot.getOutputFormat(), hierarchyCtx, null, rowFields, colFields, outputMetricsForContract(pivot),
                    sqlPushdownUsed, axisDomainSelectionUsed, cascadeRequest);
            return buildEmptyResponse(pivot, startTime, capabilityContract);
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

        if (!sqlPushdownUsed && !axisDomainSelectionUsed) {
            // 2.1 轴级 Having 过滤（SQL pushdown 场景已在 SQL 层完成）
            resultSet = AxisHavingFilter.apply(resultSet, pivot.getRows(), metrics);
            resultSet = AxisHavingFilter.apply(resultSet, pivot.getColumns(), metrics);

            if (requiresPrePageParentDenominator(pivot)) {
                parentShareExternalValues = ParentShareCalculator.buildExternalParentAggIndex(
                        resultSet, pivot, rowFields, colFields);
                parentShareEvidence = buildParentSharePrePageParentEvidence(
                        pivot, rowFields, colFields, parentShareExternalValues, resultSet.size());
            }

            // 2.2 轴向 TopN 截断（SQL pushdown 场景已在 SQL 层完成）
            resultSet = AxisTopNTruncator.apply(resultSet, pivot.getRows());
            resultSet = AxisTopNTruncator.apply(resultSet, pivot.getColumns());
        } else if (axisDomainSelectionUsed) {
            logger.debug("[Pivot] Phase 2: Skipping Having/TopN (already done by axis domain selection)");
        } else {
            logger.debug("[Pivot] Phase 2: Skipping Having/TopN (already done in SQL pushdown)");
        }


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
                try {
                    rollupCache = executor.execute(model, request, context,
                            grains, rollupPlans, rowFields, colFields, rowDomain, colDomain);
                } catch (NonAdditiveRollupDomainTooLargeException e) {
                    // Stage 4 fail-closed: SQL pushdown 后 surviving domain 超限，
                    // 无法为 non-additive subtotal 生成精确 tuple 约束。
                    // 不能静默近似（静默近似会让小计包含被 TopN 过滤的成员），
                    // 因此向用户报错，要求降低 TopN limit 或关闭 rowSubtotals/grandTotal。
                    PivotTelemetry.domainLimitExceeded(logger, model, e.getDomainSize(), e.getMaxAllowed(),
                            sqlPushdownUsed, rowDomain.size(), colDomain.size());
                    throw new IllegalStateException(
                            "Pivot subtotal/grandTotal: non-additive metric (AVG/COUNT_DISTINCT) 的辅助查询 " +
                            "surviving domain 超过安全限制（" + e.getDomainSize() + " > " + e.getMaxAllowed() + "）。" +
                            "请减少 TopN limit 数量，或关闭 rowSubtotals/columnSubtotals/grandTotal。", e);
                }
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
            ParentShareCalculator.apply(resultSet, pivot, rowFields, colFields, parentShareExternalValues);
        }

        // ===== Phase 2.9: BaselineRatio 基准引用计算 =====
        if (!pivot.getBaselineRatioMetrics().isEmpty()) {
            logger.debug("[Pivot] Phase 2.9: BaselineRatio calculation, {} metrics",
                    pivot.getBaselineRatioMetrics().size());
            BaselineRatioCalculator.apply(resultSet, pivot, rowFields, colFields, baselineRatioExternalValues);
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
        Map<String, Object> capabilityContract = buildPivotCapabilityContract(
                pivot, pivotResult.getFormat(), hierarchyCtx, hierarchySkeleton, rowFields, colFields, outputMetrics,
                sqlPushdownUsed, axisDomainSelectionUsed, cascadeRequest);
        return buildPivotResponse(pivotResult, startTime, baselineRatioEvidence, parentShareEvidence,
                capabilityContract);
    }

    private void injectPredefinedCalculatedFields(SemanticQueryRequest request, QueryModel queryModel) {
        if (request == null || queryModel == null) {
            return;
        }
        List<CalculatedFieldDef> predefined = queryModel.getPredefinedCalculatedFields();
        if (predefined == null || predefined.isEmpty()) {
            return;
        }

        Set<String> referencedFields = collectSemanticReferences(request);
        if (referencedFields.isEmpty()) {
            return;
        }

        Set<String> predefinedNames = predefined.stream()
                .map(CalculatedFieldDef::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (predefinedNames.isEmpty()) {
            return;
        }

        List<CalculatedFieldDef> current = request.getCalculatedFields() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.getCalculatedFields());
        List<String> replaced = new ArrayList<>();
        current.removeIf(field -> {
            if (field != null && predefinedNames.contains(field.getName())) {
                replaced.add(field.getName());
                return true;
            }
            return false;
        });
        if (!replaced.isEmpty()) {
            logger.warn("以下 Pivot calculatedFields 为 QM 预定义计算字段，已使用模型预定义公式覆盖: {}", replaced);
        }

        Set<String> existingNames = current.stream()
                .filter(Objects::nonNull)
                .map(CalculatedFieldDef::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<CalculatedFieldDef> toInject = new ArrayList<>();
        for (CalculatedFieldDef field : predefined) {
            if (field == null || field.getName() == null) {
                continue;
            }
            if (referencedFields.contains(field.getName()) && !existingNames.contains(field.getName())) {
                toInject.add(field);
            }
        }
        if (toInject.isEmpty() && replaced.isEmpty()) {
            return;
        }

        current.addAll(0, toInject);
        request.setCalculatedFields(current);
        if (!toInject.isEmpty()) {
            logger.debug("[Pivot] 注入了 {} 个 QM 预定义计算字段: {}", toInject.size(),
                    toInject.stream().map(CalculatedFieldDef::getName).collect(Collectors.toList()));
        }
    }

    private Set<String> collectSemanticReferences(SemanticQueryRequest request) {
        Set<String> fields = new LinkedHashSet<>();
        collectStringFields(request.getColumns(), fields);
        collectSemanticSliceFields(request.getSlice(), fields);
        collectSemanticSliceFields(request.getHaving(), fields);
        collectSemanticSliceFields(request.getPostSlice(), fields);
        if (request.getGroupBy() != null) {
            for (SemanticQueryRequest.GroupByItem group : request.getGroupBy()) {
                if (group != null) {
                    addField(group.getField(), fields);
                }
            }
        }
        if (request.getOrderBy() != null) {
            for (SemanticQueryRequest.OrderItem order : request.getOrderBy()) {
                if (order != null) {
                    addField(order.getField(), fields);
                }
            }
        }

        PivotRequest pivot = request.getPivot();
        if (pivot != null) {
            if (pivot.getMetricItems() != null) {
                for (PivotMetricItem metric : pivot.getMetricItems()) {
                    if (metric == null) {
                        continue;
                    }
                    addField(metric.getName(), fields);
                    addField(metric.getOf(), fields);
                }
            }
            collectAxisReferences(pivot.getRows(), fields);
            collectAxisReferences(pivot.getColumns(), fields);
        }
        return fields;
    }

    private void collectAxisReferences(List<AxisField> axisFields, Set<String> fields) {
        if (axisFields == null) {
            return;
        }
        for (AxisField axis : axisFields) {
            if (axis == null) {
                continue;
            }
            addField(axis.getField(), fields);
            collectOrderSpecFields(axis.getOrderBy(), fields);
            collectSemanticSliceFields(axis.getDomainSlice(), fields);
            if (axis.getHaving() != null) {
                for (MetricFilter filter : axis.getHaving()) {
                    if (filter != null) {
                        addField(filter.getMetric(), fields);
                    }
                }
            }
        }
    }

    private void collectSemanticSliceFields(List<SemanticQueryRequest.SliceItem> slices, Set<String> fields) {
        if (slices == null) {
            return;
        }
        for (SemanticQueryRequest.SliceItem slice : slices) {
            if (slice == null) {
                continue;
            }
            addField(slice.getField(), fields);
            collectSemanticSliceFields(slice.getAnd(), fields);
            collectSemanticSliceFields(slice.getOr(), fields);
        }
    }

    private void collectStringFields(List<String> values, Set<String> fields) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addField(value, fields);
        }
    }

    private void collectOrderSpecFields(List<String> specs, Set<String> fields) {
        if (specs == null) {
            return;
        }
        for (String spec : specs) {
            if (spec == null) {
                continue;
            }
            addField(spec.startsWith("-") ? spec.substring(1) : spec, fields);
        }
    }

    private void addField(String field, Set<String> fields) {
        if (field != null && !field.isBlank()) {
            fields.add(field);
        }
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
     * Phase 1: 轴域选择路径。
     *
     * <p>v3.7 的 domainSlice/start/offset 只用于选择轴成员集合；cell 聚合仍只使用顶层 slice。
     * 这样可以避免“候选运单满足 noPaidValue > 0，但同一运单下 noPaidValue = 0 的科目 cell 被误删”的问题。</p>
     */
    private AxisDomainSelectionResult executePhase1WithAxisDomainSelection(
            String model, SemanticQueryRequest originalRequest,
            SemanticRequestContext context,
            List<String> rowFields, List<String> colFields,
            List<String> metrics, PivotRequest pivot, QueryModel queryModel) {

        Set<Object> rowDomain = executeAxisDomainSelection(model, originalRequest, context,
                pivot.getRows(), metrics, queryModel);
        Set<Object> columnDomain = executeAxisDomainSelection(model, originalRequest, context,
                pivot.getColumns(), metrics, queryModel);
        Set<Object> prePageColumnDomain = null;
        if (requiresPrePageAxisDomainBaseline(pivot) && hasAxisDomainSelectionRequestOnAxis(pivot.getColumns())) {
            prePageColumnDomain = executeAxisDomainSelection(model, originalRequest, context,
                    pivot.getColumns(), metrics, queryModel, false);
        }

        if ((rowDomain != null && rowDomain.isEmpty()) || (columnDomain != null && columnDomain.isEmpty())) {
            return new AxisDomainSelectionResult(Collections.emptyList(), Collections.emptyMap(), Collections.emptyList());
        }

        DomainConstrainedCellRequest cellRequest = buildAxisDomainConstrainedCellRequest(
                originalRequest, pivot, rowDomain, columnDomain, context);
        List<Map<String, Object>> cellRows = executePhase1(model, cellRequest.request(), cellRequest.context(),
                rowFields, colFields, metrics, queryModel);
        if (cellRows.isEmpty()) {
            return new AxisDomainSelectionResult(cellRows, Collections.emptyMap(), Collections.emptyList());
        }

        List<Map<String, Object>> filtered = cellRows;
        if (rowDomain != null) {
            filtered = filterByAxisDomain(filtered, pivot.getRows().get(0).getField(), rowDomain);
        }
        if (columnDomain != null) {
            filtered = filterByAxisDomain(filtered, pivot.getColumns().get(0).getField(), columnDomain);
        }
        BaselineRatioPrePageAxisDomainResult baselineRatioPrePageResult =
                executeBaselineRatioPrePageAxisDomain(model, originalRequest, context,
                        rowFields, colFields, metrics, pivot, queryModel,
                        rowDomain, columnDomain, prePageColumnDomain);
        return new AxisDomainSelectionResult(filtered, baselineRatioPrePageResult.values(),
                baselineRatioPrePageResult.evidence());
    }

    private DomainConstrainedCellRequest buildAxisDomainConstrainedCellRequest(
            SemanticQueryRequest originalRequest, PivotRequest pivot,
            Set<Object> rowDomain, Set<Object> columnDomain,
            SemanticRequestContext context) {

        SemanticQueryRequest cellRequest = new SemanticQueryRequest();
        List<DomainTransportPlan> transportPlans = new ArrayList<>();
        cellRequest.setSlice(buildAxisDomainConstrainedSlice(originalRequest, pivot,
                rowDomain, columnDomain, transportPlans));
        cellRequest.setCalculatedFields(originalRequest.getCalculatedFields());

        SemanticRequestContext effectiveContext = context;
        if (!transportPlans.isEmpty()) {
            effectiveContext = withDomainTransportPlans(context, transportPlans);
        }
        return new DomainConstrainedCellRequest(cellRequest, effectiveContext);
    }

    private List<SemanticQueryRequest.SliceItem> buildAxisDomainConstrainedSlice(
            SemanticQueryRequest originalRequest, PivotRequest pivot,
            Set<Object> rowDomain, Set<Object> columnDomain,
            List<DomainTransportPlan> transportPlans) {

        List<SemanticQueryRequest.SliceItem> slices = new ArrayList<>();
        if (originalRequest.getSlice() != null) {
            slices.addAll(originalRequest.getSlice());
        }
        if (rowDomain != null) {
            addAxisDomainConstraint(slices, pivot.getRows().get(0).getField(),
                    rowDomain, transportPlans, "row");
        }
        if (columnDomain != null) {
            addAxisDomainConstraint(slices, pivot.getColumns().get(0).getField(),
                    columnDomain, transportPlans, "column");
        }
        return slices.isEmpty() ? null : slices;
    }

    private void addAxisDomainConstraint(List<SemanticQueryRequest.SliceItem> slices,
                                         String field,
                                         Set<Object> domain,
                                         List<DomainTransportPlan> transportPlans,
                                         String axisName) {
        if (domain.size() <= AXIS_DOMAIN_TRANSPORT_THRESHOLD) {
            slices.add(inSlice(field, domain));
            return;
        }
        transportPlans.add(buildSingleFieldDomainTransportPlan(
                "_pivot_axis_domain_" + axisName + "_" + transportPlans.size(), field, domain));
    }

    private DomainTransportPlan buildSingleFieldDomainTransportPlan(String relationName,
                                                                     String field,
                                                                     Set<Object> domain) {
        List<DomainTransportTuple> tuples = domain.stream()
                .map(value -> new DomainTransportTuple(Collections.singletonList(value)))
                .collect(Collectors.toList());
        return DomainTransportPlan.builder()
                .relationName(relationName)
                .fields(Collections.singletonList(new DomainTransportField(field)))
                .tuples(tuples)
                .build();
    }

    private SemanticRequestContext withDomainTransportPlans(
            SemanticRequestContext context,
            List<DomainTransportPlan> transportPlans) {
        if (transportPlans == null || transportPlans.isEmpty()) {
            return context;
        }
        if (context == null) {
            context = SemanticRequestContext.empty();
        }
        List<DomainTransportPlan> merged = new ArrayList<>();
        if (context.getDomainTransportPlans() != null) {
            merged.addAll(context.getDomainTransportPlans());
        }
        merged.addAll(transportPlans);
        return context.withDomainTransportPlans(merged);
    }

    private SemanticQueryRequest.SliceItem inSlice(String field, Set<Object> values) {
        SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
        item.setField(field);
        item.setOp("in");
        item.setValue(new ArrayList<>(values));
        return item;
    }

    /**
     * 查询并裁剪单个轴的候选成员集合。
     *
     * <p>MVP 只支持单层 rows/columns。多层隐式父子分区分页需要独立的 domain tree/cursor 设计。</p>
     */
    private Set<Object> executeAxisDomainSelection(
            String model, SemanticQueryRequest originalRequest,
            SemanticRequestContext context,
            List<AxisField> axisFields,
            List<String> metrics, QueryModel queryModel) {
        return executeAxisDomainSelection(model, originalRequest, context,
                axisFields, metrics, queryModel, true);
    }

    private Set<Object> executeAxisDomainSelection(
            String model, SemanticQueryRequest originalRequest,
            SemanticRequestContext context,
            List<AxisField> axisFields,
            List<String> metrics, QueryModel queryModel,
            boolean applyWindow) {

        if (axisFields == null || axisFields.isEmpty()) {
            return null;
        }

        AxisField axisField = axisFields.get(0);
        if (!hasAxisDomainSelectionRequest(axisField)) {
            return null;
        }

        String field = axisField.getField();
        SemanticQueryRequest domainRequest = new SemanticQueryRequest();

        List<String> columns = new ArrayList<>();
        columns.add(field);
        columns.addAll(metrics);
        domainRequest.setColumns(columns);

        List<SemanticQueryRequest.GroupByItem> groupBy = new ArrayList<>();
        groupBy.add(new SemanticQueryRequest.GroupByItem(field, null));
        for (String metric : metrics) {
            groupBy.add(new SemanticQueryRequest.GroupByItem(metric, resolveMetricAggregation(metric, queryModel)));
        }
        domainRequest.setGroupBy(groupBy);
        domainRequest.setSlice(mergeSlices(originalRequest.getSlice(), axisField.getDomainSlice()));
        domainRequest.setHaving(toHavingSlices(axisField.getHaving()));
        domainRequest.setOrderBy(buildAxisDomainOrderBy(axisField));
        if (applyWindow) {
            domainRequest.setStart(axisField.getEffectiveOffset());
        }
        domainRequest.setCalculatedFields(originalRequest.getCalculatedFields());
        domainRequest.setLimit(applyWindow ? resolveAxisDomainQueryLimit(axisField) : CardinalityBreaker.DEFAULT_ROW_LIMIT);
        domainRequest.setReturnTotal(false);

        SemanticQueryResponse response = semanticQueryService.queryModel(model, domainRequest, "execute", context);
        List<Map<String, Object>> domainRows = response.getItems() != null
                ? new ArrayList<>(response.getItems())
                : new ArrayList<>();

        domainRows = AxisHavingFilter.apply(domainRows, List.of(axisField), metrics);
        sortAxisDomainRows(domainRows, axisField);
        if (applyWindow && !isAxisDomainWindowPushedDown(axisField)) {
            domainRows = applyAxisDomainWindow(domainRows, axisField);
        }

        Set<Object> domain = new LinkedHashSet<>();
        for (Map<String, Object> row : domainRows) {
            domain.add(row.get(field));
        }
        return domain;
    }

    private BaselineRatioPrePageAxisDomainResult executeBaselineRatioPrePageAxisDomain(
            String model, SemanticQueryRequest originalRequest,
            SemanticRequestContext context,
            List<String> rowFields, List<String> colFields,
            List<String> metrics, PivotRequest pivot, QueryModel queryModel,
            Set<Object> rowDomain, Set<Object> visibleColumnDomain, Set<Object> prePageColumnDomain) {

        if (!requiresPrePageAxisDomainBaseline(pivot) ||
                prePageColumnDomain == null || prePageColumnDomain.isEmpty() ||
                colFields == null || colFields.size() != 1) {
            return BaselineRatioPrePageAxisDomainResult.empty();
        }

        List<Object> orderedColumnDomain = new ArrayList<>(prePageColumnDomain);
        Set<Object> baselineColumnDomain = new LinkedHashSet<>();
        Map<String, Object> targetColumnByMetric = new LinkedHashMap<>();
        for (PivotMetricItem br : pivot.getBaselineRatioMetrics()) {
            Object target = "last".equals(br.getBaseline())
                    ? orderedColumnDomain.get(orderedColumnDomain.size() - 1)
                    : orderedColumnDomain.get(0);
            targetColumnByMetric.put(br.getName(), target);
            baselineColumnDomain.add(target);
        }

        DomainConstrainedCellRequest baselineRequest = buildAxisDomainConstrainedCellRequest(
                originalRequest, pivot, rowDomain, baselineColumnDomain, context);
        List<Map<String, Object>> baselineRows = executePhase1(model, baselineRequest.request(),
                baselineRequest.context(), rowFields, colFields, metrics, queryModel);

        if (baselineRows.isEmpty()) {
            return BaselineRatioPrePageAxisDomainResult.empty();
        }

        String columnField = colFields.get(0);
        Map<String, Map<String, Number>> result = new LinkedHashMap<>();
        List<Map<String, Object>> evidence = new ArrayList<>();
        for (PivotMetricItem br : pivot.getBaselineRatioMetrics()) {
            Object targetColumn = targetColumnByMetric.get(br.getName());
            Map<String, Number> byRow = new LinkedHashMap<>();
            for (Map<String, Object> row : baselineRows) {
                if (!Objects.equals(targetColumn, row.get(columnField))) {
                    continue;
                }
                Object value = row.get(br.getOf());
                if (value instanceof Number number) {
                    byRow.put(buildPivotKey(row, rowFields), number);
                }
            }
            result.put(br.getName(), byRow);
            evidence.add(buildBaselineRatioPrePageAxisDomainEvidence(
                    br, columnField, targetColumn, visibleColumnDomain, orderedColumnDomain, byRow));
        }
        return new BaselineRatioPrePageAxisDomainResult(result, evidence);
    }

    private Map<String, Object> buildBaselineRatioPrePageAxisDomainEvidence(
            PivotMetricItem metric, String columnField, Object baselineColumnKey,
            Set<Object> visibleColumnDomain, List<Object> orderedColumnDomain,
            Map<String, Number> baselineValuesByRow) {

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("metric", metric.getName());
        evidence.put("of", metric.getOf());
        evidence.put("axis", metric.getAxis());
        evidence.put("baseline", metric.getBaseline());
        evidence.put("baselineScope", metric.getBaselineScope());
        evidence.put("columnField", columnField);
        evidence.put("baselineColumnKey", baselineColumnKey);
        evidence.put("baselineColumnVisible",
                visibleColumnDomain == null || visibleColumnDomain.contains(baselineColumnKey));
        evidence.put("prePageAxisDomainSize", orderedColumnDomain.size());
        evidence.put("visibleAxisDomainSize", visibleColumnDomain == null ? null : visibleColumnDomain.size());
        evidence.put("baselineRows", baselineValuesByRow.size());
        evidence.put("source", "auxiliaryBaselineRelation");
        return evidence;
    }

    private boolean requiresPrePageAxisDomainBaseline(PivotRequest pivot) {
        return pivot != null && pivot.getBaselineRatioMetrics().stream()
                .anyMatch(metric -> "prePageAxisDomain".equals(metric.getBaselineScope()));
    }

    private boolean requiresPrePageParentDenominator(PivotRequest pivot) {
        return pivot != null && pivot.getParentShareMetrics().stream()
                .anyMatch(metric -> "prePageParent".equals(metric.getDenominatorScope()));
    }

    private List<Map<String, Object>> buildParentSharePrePageParentEvidence(
            PivotRequest pivot, List<String> rowFields, List<String> colFields,
            Map<String, Map<String, Number>> parentValuesByMetric, int prePageRows) {

        if (parentValuesByMetric == null || parentValuesByMetric.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> evidence = new ArrayList<>();
        for (PivotMetricItem metric : pivot.getParentShareMetrics()) {
            if (!"prePageParent".equals(metric.getDenominatorScope())) {
                continue;
            }
            Map<String, Number> parentValues = parentValuesByMetric.getOrDefault(
                    metric.getName(), Collections.emptyMap());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("metric", metric.getName());
            item.put("of", metric.getOf());
            item.put("axis", metric.getAxis() == null ? "rows" : metric.getAxis());
            item.put("level", metric.getLevel());
            item.put("parentLevel", metric.getParentLevel());
            item.put("denominatorScope", metric.getDenominatorScope());
            item.put("rowFields", rowFields);
            item.put("columnFields", colFields);
            item.put("prePageRows", prePageRows);
            item.put("parentGroups", parentValues.size());
            item.put("source", "preTopNParentAggIndex");
            evidence.add(item);
        }
        return evidence;
    }

    private String buildPivotKey(Map<String, Object> row, List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return "ALL";
        }
        StringBuilder sb = new StringBuilder();
        for (String field : fields) {
            Object val = row.get(field);
            sb.append(val != null ? val.toString() : "null").append("|");
        }
        return sb.toString();
    }

    private List<SemanticQueryRequest.SliceItem> toHavingSlices(List<MetricFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        List<SemanticQueryRequest.SliceItem> result = new ArrayList<>();
        for (MetricFilter filter : filters) {
            SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
            item.setField(filter.getMetric());
            item.setOp(filter.getOp());
            item.setValue(filter.getValue());
            result.add(item);
        }
        return result;
    }

    private List<SemanticQueryRequest.OrderItem> buildAxisDomainOrderBy(AxisField axisField) {
        List<SemanticQueryRequest.OrderItem> orderItems = new ArrayList<>();
        Set<String> orderedFields = new LinkedHashSet<>();
        if (axisField.getOrderBy() != null) {
            for (String spec : axisField.getOrderBy()) {
                SemanticQueryRequest.OrderItem item = toOrderItem(spec);
                orderItems.add(item);
                orderedFields.add(item.getField());
            }
        }
        if (!orderedFields.contains(axisField.getField())) {
            SemanticQueryRequest.OrderItem tieBreaker = new SemanticQueryRequest.OrderItem();
            tieBreaker.setField(axisField.getField());
            tieBreaker.setDir("asc");
            orderItems.add(tieBreaker);
        }
        return orderItems;
    }

    private SemanticQueryRequest.OrderItem toOrderItem(String spec) {
        SemanticQueryRequest.OrderItem item = new SemanticQueryRequest.OrderItem();
        if (spec != null && spec.startsWith("-")) {
            item.setField(spec.substring(1));
            item.setDir("desc");
        } else {
            item.setField(spec);
            item.setDir("asc");
        }
        return item;
    }

    private List<SemanticQueryRequest.SliceItem> mergeSlices(List<SemanticQueryRequest.SliceItem> globalSlice,
                                                             List<SemanticQueryRequest.SliceItem> domainSlice) {
        if ((globalSlice == null || globalSlice.isEmpty()) && (domainSlice == null || domainSlice.isEmpty())) {
            return null;
        }
        List<SemanticQueryRequest.SliceItem> merged = new ArrayList<>();
        if (globalSlice != null) {
            merged.addAll(globalSlice);
        }
        if (domainSlice != null) {
            merged.addAll(domainSlice);
        }
        return merged;
    }

    private List<Map<String, Object>> filterByAxisDomain(List<Map<String, Object>> rows,
                                                          String field,
                                                          Set<Object> domain) {
        if (domain.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream()
                .filter(row -> domain.contains(row.get(field)))
                .collect(Collectors.toList());
    }

    private void sortAxisDomainRows(List<Map<String, Object>> rows, AxisField axisField) {
        Comparator<Map<String, Object>> comparator = buildAxisDomainComparator(axisField);
        rows.sort(comparator);
    }

    private Comparator<Map<String, Object>> buildAxisDomainComparator(AxisField axisField) {
        Comparator<Map<String, Object>> comparator = null;
        List<String> orderBySpecs = axisField.getOrderBy();
        if (orderBySpecs != null) {
            for (String spec : orderBySpecs) {
                boolean desc = spec.startsWith("-");
                String fieldName = desc ? spec.substring(1) : spec;
                Comparator<Map<String, Object>> fieldComparator =
                        (a, b) -> compareAxisDomainValues(a.get(fieldName), b.get(fieldName));
                if (desc) {
                    fieldComparator = fieldComparator.reversed();
                }
                comparator = comparator == null ? fieldComparator : comparator.thenComparing(fieldComparator);
            }
        }

        Comparator<Map<String, Object>> stableTieBreaker =
                (a, b) -> compareAxisDomainValues(a.get(axisField.getField()), b.get(axisField.getField()));
        return comparator == null ? stableTieBreaker : comparator.thenComparing(stableTieBreaker);
    }

    private List<Map<String, Object>> applyAxisDomainWindow(List<Map<String, Object>> rows, AxisField axisField) {
        int start = axisField.getEffectiveOffset();
        if (start >= rows.size()) {
            return Collections.emptyList();
        }
        int end = rows.size();
        if (axisField.getLimit() != null) {
            end = Math.min(start + axisField.getLimit(), rows.size());
        }
        return new ArrayList<>(rows.subList(start, end));
    }

    private int resolveAxisDomainQueryLimit(AxisField axisField) {
        if (axisField.getLimit() != null) {
            return axisField.getLimit();
        }
        return CardinalityBreaker.DEFAULT_ROW_LIMIT;
    }

    private boolean isAxisDomainWindowPushedDown(AxisField axisField) {
        return axisField.getLimit() != null && axisField.getLimit() > 0;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compareAxisDomainValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }

        if (a instanceof Comparable && b instanceof Comparable && a.getClass().isInstance(b)) {
            return ((Comparable) a).compareTo(b);
        }

        return a.toString().compareTo(b.toString());
    }

    /**
     * 判断当前 PivotRequest 是否可以使用 SQL pushdown
     *
     * <p>条件：
     * <ol>
     *   <li>queryFacade 必须已注入（否则无法走 managedRelation 路径）</li>
     *   <li>至少一个轴字段有 having 或 limit（否则没有 pushdown 意义）</li>
     *   <li>不是 hierarchyMode=tree（tree 模式的 limit 语义在父子维度层，无法简单 SQL 化）</li>
     *   <li>不包含 parentShare/baselineRatio 派生指标（它们是后处理指标，不能参与 SQL 下放）</li>
     * </ol></p>
     */
    public static boolean SQL_PUSHDOWN_ENABLED = true; // For testing

    private String getSqlPushdownSkipReason(PivotRequest pivot, HierarchyContext hierarchyCtx) {
        if (!SQL_PUSHDOWN_ENABLED) {
            return "disabled";
        }
        if (queryFacade == null) {
            return "queryFacadeUnavailable";
        }
        if (hierarchyCtx.isTree()) {
            return "hierarchyTree";
        }
        if (!pivot.getParentShareMetrics().isEmpty() || !pivot.getBaselineRatioMetrics().isEmpty()) {
            // parentShare/baselineRatio 依赖内存后处理，不能混用 SQL pushdown
            // （未来如果 pushdown 和后处理可以独立分离，这个约束可以放宽）
            return "derivedMetricPostProcessing";
        }
        if (!hasAxisDomainOperations(pivot.getRows()) && !hasAxisDomainOperations(pivot.getColumns())) {
            return "noAxisHavingOrLimit";
        }
        return null;
    }

    /**
     * 检查轴字段是否有 having 或 limit（需要 domain 级 SQL 下放的操作）
     */
    private boolean hasAxisDomainOperations(List<AxisField> fields) {
        if (fields == null) return false;
        for (AxisField f : fields) {
            if (f.getLimit() != null && f.getLimit() > 0) return true;
            if (f.getHaving() != null && !f.getHaving().isEmpty()) return true;
        }
        return false;
    }

    private boolean hasAxisDomainSelectionRequest(PivotRequest pivot) {
        if (hasAxisDomainSliceRequest(pivot.getRows()) || hasAxisDomainSliceRequest(pivot.getColumns())) {
            return true;
        }
        // start/offset 的单层轴语义仍走独立轴域查询；多层 rows 的 start/offset
        // 属于 per-parent window，由 SQL pushdown 或内存 AxisTopNTruncator 处理。
        boolean singleRowAxisWindow = pivot.getRowLevelCount() <= 1 && hasAxisStartOffsetRequest(pivot.getRows());
        boolean singleColumnAxisWindow = pivot.getColumnLevelCount() <= 1 && hasAxisStartOffsetRequest(pivot.getColumns());
        return singleRowAxisWindow || singleColumnAxisWindow;
    }

    private boolean hasAxisDomainSelectionRequest(AxisField field) {
        return field != null && (
                (field.getDomainSlice() != null && !field.getDomainSlice().isEmpty()) ||
                field.getStart() != null ||
                field.getOffset() != null
        );
    }

    private boolean hasAxisDomainSelectionRequestOnAxis(List<AxisField> fields) {
        if (fields == null) return false;
        for (AxisField field : fields) {
            if (hasAxisDomainSelectionRequest(field)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAxisDomainSliceRequest(List<AxisField> fields) {
        if (fields == null) return false;
        for (AxisField field : fields) {
            if (field.getDomainSlice() != null && !field.getDomainSlice().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAxisStartOffsetRequest(List<AxisField> fields) {
        if (fields == null) return false;
        for (AxisField field : fields) {
            if (field.getStart() != null || field.getOffset() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Phase 1 SQL Pushdown 路径
     *
     * <p>使用 QueryFacade.prepareManagedRelation 获取受管基础 SQL，
     * 然后用 PivotAxisDomainSqlPlanner 包装 Having + TopN CTE，
     * 最后用 QueryFacade.executeManagedRelation 执行最终 SQL。</p>
     *
     * <p>如果 Planner 因 non-additive 或 dialect 不支持而 fail-closed，
     * 将抛出 UnsupportedOperationException，由调用方捕获并 fallback。</p>
     */
    private List<Map<String, Object>> executePhase1WithSqlPushdown(
            String model, SemanticQueryRequest originalRequest,
            SemanticRequestContext context,
            List<String> rowFields, List<String> colFields,
            List<String> metrics, PivotRequest pivot, QueryModel queryModel) {

        // 1. 构建与 executePhase1 相同的扁平请求
        SemanticQueryRequest flatRequest = buildPhase1FlatRequest(originalRequest, rowFields, colFields, metrics, queryModel);

        // 2. 构建 ModelResultContext（复用 SemanticQueryServiceV3 的模式）
        com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext resultContext =
                buildManagedRelationContext(model, flatRequest, context);

        // 3. 准备受管关系代数
        ManagedRelationOptions options = ManagedRelationOptions.builder()
                .purpose("PivotAxisDomainSqlPlanner")
                .wrappableRequired(true)
                .disableInnerCacheShortCircuit(true)
                .requireStableAliases(true)
                .requireDialectCapability(ManagedRelationOptions.DialectCapability.CTE)
                .requireDialectCapability(ManagedRelationOptions.DialectCapability.WINDOW_FUNCTION)
                .build();

        ManagedSqlRelation baseRelation = queryFacade.prepareManagedRelation(resultContext, options);

        // 4. 用 Planner 生成包装 SQL（having + TopN CTE）
        PivotAxisDomainSqlPlanner.PlannedSql planned = PivotAxisDomainSqlPlanner.plan(
                baseRelation, pivot, rowFields, colFields, metrics);

        // 5. 执行最终 SQL
        com.foggyframework.dataset.db.model.engine.query.DbQueryResult dbResult =
                queryFacade.executeManagedRelation(baseRelation, planned.getSql(), planned.getParams());

        if (dbResult.getPagingResult() != null && dbResult.getPagingResult().getItems() != null) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object row : dbResult.getPagingResult().getItems()) {
                if (row instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) row;
                    items.add(map);
                }
            }
            return items;
        }
        return Collections.emptyList();
    }

    /**
     * 构建 Phase 1 的扁平查询请求（复用自 executePhase1 的逻辑）
     */
    private SemanticQueryRequest buildPhase1FlatRequest(SemanticQueryRequest originalRequest,
                                                        List<String> rowFields, List<String> colFields,
                                                        List<String> metrics, QueryModel queryModel) {
        SemanticQueryRequest flatRequest = new SemanticQueryRequest();
        List<String> allColumns = new ArrayList<>();
        allColumns.addAll(rowFields);
        allColumns.addAll(colFields);
        allColumns.addAll(metrics);
        flatRequest.setColumns(allColumns);

        List<SemanticQueryRequest.GroupByItem> groupBy = new ArrayList<>();
        for (String dim : rowFields) {
            groupBy.add(new SemanticQueryRequest.GroupByItem(dim, null));
        }
        for (String dim : colFields) {
            groupBy.add(new SemanticQueryRequest.GroupByItem(dim, null));
        }
        // Use resolveMetricAggregation for consistency with the memory path
        for (String metric : metrics) {
            String aggStr = resolveMetricAggregation(metric, queryModel);
            groupBy.add(new SemanticQueryRequest.GroupByItem(metric, aggStr));
        }
        flatRequest.setGroupBy(groupBy);
        flatRequest.setSlice(originalRequest.getSlice());
        flatRequest.setCalculatedFields(originalRequest.getCalculatedFields());
        flatRequest.setLimit(CardinalityBreaker.DEFAULT_ROW_LIMIT * CardinalityBreaker.DEFAULT_COL_LIMIT);
        flatRequest.setReturnTotal(false);
        return flatRequest;
    }

    /**
     * 构建用于 prepareManagedRelation 的 ModelResultContext
     */
    private com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext buildManagedRelationContext(
            String model, SemanticQueryRequest flatRequest, SemanticRequestContext reqContext) {
        // 构建 JDBC 请求
        com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef queryDef =
                new com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef();
        queryDef.setQueryModel(model);
        queryDef.setReturnTotal(false);
        queryDef.setStrictColumns(true);
        queryDef.setColumns(flatRequest.getColumns());

        // groupBy
        if (flatRequest.getGroupBy() != null) {
            List<com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef> jdbcGroupBy = new ArrayList<>();
            for (SemanticQueryRequest.GroupByItem item : flatRequest.getGroupBy()) {
                com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef g =
                        new com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef();
                g.setField(item.getField());
                g.setAgg(item.getAgg());
                jdbcGroupBy.add(g);
            }
            queryDef.setGroupBy(jdbcGroupBy);
        }

        // slice
        if (flatRequest.getSlice() != null) {
            List<com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef> jdbcSlice = new ArrayList<>();
            for (SemanticQueryRequest.SliceItem sliceItem : flatRequest.getSlice()) {
                com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef s =
                        new com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef();
                s.setField(sliceItem.getField());
                s.setOp(sliceItem.getOp());
                s.setValue(sliceItem.getValue());
                jdbcSlice.add(s);
            }
            queryDef.setSlice(jdbcSlice);
        }

        // calculatedFields
        if (flatRequest.getCalculatedFields() != null) {
            queryDef.setCalculatedFields(new ArrayList<>(flatRequest.getCalculatedFields()));
        }

        com.foggyframework.dataset.client.domain.PagingRequest<com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef> pagingRequest =
                new com.foggyframework.dataset.client.domain.PagingRequest<>();
        pagingRequest.setParam(queryDef);
        pagingRequest.setStart(0);
        pagingRequest.setPageSize(flatRequest.getLimit());

        com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext resultContext =
                new com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext();
        resultContext.setRequest(pagingRequest);
        resultContext.setQueryType(com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext.QueryType.SEMANTIC);
        resultContext.setNamespace(reqContext.getNamespace());
        resultContext.setSecurityContext(reqContext.getSecurityContext());
        resultContext.setFieldAccess(reqContext.getFieldAccess());
        resultContext.setDeniedColumns(reqContext.getDeniedColumns());
        resultContext.setSystemSlice(reqContext.getSystemSlice());

        return resultContext;
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
        PivotCascadeRules.validateRequestShape(pivot);

        // pivot 与 columns 互斥
        if (request.getColumns() != null && !request.getColumns().isEmpty()) {
            throw new IllegalArgumentException("pivot 与 columns 不能同时出现。使用 pivot 模式时请移除 columns 字段");
        }

        // pivot 与 timeWindow 互斥
        if (request.getTimeWindow() != null && !request.getTimeWindow().isEmpty()) {
            throw new IllegalArgumentException(
                    "timeWindow 与 pivot 模式互斥。时间智能需求请使用 timeWindow 普通聚合；行列透视请使用 pivot；同时需要时请拆成两个查询");
        }

        // pivot 轴 TopN / 排序只能写在轴字段上，避免顶层分页排序被误解释为轴控件
        if (request.getOrderBy() != null && !request.getOrderBy().isEmpty()) {
            throw new IllegalArgumentException(
                    "pivot 模式不支持顶层 orderBy。请使用 pivot.rows[*].orderBy 或 pivot.columns[*].orderBy 作为透视轴排序控制");
        }
        if (request.getLimit() != null) {
            throw new IllegalArgumentException(
                    "pivot 模式不支持顶层 limit。请使用 pivot.rows[*].limit 或 pivot.columns[*].limit 作为透视轴 TopN 控制");
        }
        if (request.getPostAggregateCalculations() != null && !request.getPostAggregateCalculations().isEmpty()) {
            throw new IllegalArgumentException(
                    "pivot 模式不支持顶层 postAggregateCalculations。请使用 pivot.metrics 的结构化派生指标，或拆分为普通 query_model result-stage 请求");
        }
        if (request.getPostSlice() != null && !request.getPostSlice().isEmpty()) {
            throw new IllegalArgumentException(
                    "pivot 模式不支持顶层 postSlice。Pivot 轴过滤请使用 pivot.rows[*].having / pivot.columns[*].having；结果阶段过滤请拆分请求");
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

        validateDerivedMetricsNotUsedAsAxisControls(pivot);

        validateAxisDomainSelectionRequest(pivot);

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

            // ===== S8.3: tree + non-additive + subtotals 降级容错 =====
            PivotOptions opts = pivot.getOptions();
            boolean hasSubtotal = opts != null &&
                    (opts.isRowSubtotals() || opts.isColumnSubtotals() || opts.isGrandTotal());
            if (hasSubtotal) {
                // Fail-Closed: 针对 tree 模式不支持的 subtotal，改为静默忽略而不是抛出异常
                opts.setRowSubtotals(false);
                opts.setColumnSubtotals(false);
                opts.setGrandTotal(false);
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

    private void validateAxisDomainSelectionRequest(PivotRequest pivot) {
        validateAxisFieldsDomainSelection(pivot.getRows(), "rows");
        validateAxisFieldsDomainSelection(pivot.getColumns(), "columns");

        boolean hasDomainSlice = hasAxisDomainSliceRequest(pivot.getRows()) ||
                hasAxisDomainSliceRequest(pivot.getColumns());
        boolean hasStartOffset = hasAxisStartOffsetRequest(pivot.getRows()) ||
                hasAxisStartOffsetRequest(pivot.getColumns());
        if (!hasDomainSlice && !hasStartOffset) {
            return;
        }

        if (hasDomainSlice && (pivot.getRowLevelCount() > 1 || pivot.getColumnLevelCount() > 1)) {
            throw new IllegalArgumentException(
                    "domainSlice/start/offset 当前仅支持单层 rows 和单层 columns。多层轴分页需要显式 domain tree/cursor 语义");
        }
        if (pivot.getColumnLevelCount() > 1 && hasAxisStartOffsetRequest(pivot.getColumns())) {
            throw new IllegalArgumentException(
                    "start/offset 当前仅支持单层 columns；多层 rows 可作为 per-parent window 使用");
        }

        if (pivot.hasHierarchyField()) {
            throw new IllegalArgumentException("domainSlice/start/offset 当前不支持 hierarchyMode=tree");
        }

        if (!pivot.getParentShareMetrics().isEmpty()) {
            if (!allParentShareMetricsUsePrePageParent(pivot)) {
                throw new IllegalArgumentException(
                        "domainSlice/start/offset 与 parentShare 组合时必须显式指定 denominatorScope=prePageParent");
            }
            if (!isSupportedParentSharePrePageParentWindow(pivot)) {
                throw new IllegalArgumentException(
                        "denominatorScope=prePageParent 当前仅支持多层 rows 子级 start/offset/limit 窗口，" +
                        "暂不支持 domainSlice 或 columns 轴窗口");
            }
        }
        if (!pivot.getBaselineRatioMetrics().isEmpty() && !allBaselineRatioMetricsUsePrePageAxisDomain(pivot)) {
            throw new IllegalArgumentException(
                    "domainSlice/start/offset 与 baselineRatio 组合时必须显式指定 baselineScope=prePageAxisDomain");
        }
    }

    private void validateDerivedMetricsNotUsedAsAxisControls(PivotRequest pivot) {
        Set<String> derivedMetricNames = pivot.getMetricItems().stream()
                .filter(PivotMetricItem::isDerived)
                .map(PivotMetricItem::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());
        if (derivedMetricNames.isEmpty()) {
            return;
        }
        validateDerivedMetricsNotUsedAsAxisControls(pivot.getRows(), "rows", derivedMetricNames);
        validateDerivedMetricsNotUsedAsAxisControls(pivot.getColumns(), "columns", derivedMetricNames);
    }

    private void validateDerivedMetricsNotUsedAsAxisControls(
            List<AxisField> fields, String axisName, Set<String> derivedMetricNames) {
        if (fields == null) {
            return;
        }
        for (AxisField field : fields) {
            if (field == null) {
                continue;
            }
            if (field.getOrderBy() != null) {
                for (String orderBy : field.getOrderBy()) {
                    String metric = normalizeAxisOrderByMetric(orderBy);
                    if (derivedMetricNames.contains(metric)) {
                        throw new IllegalArgumentException(axisName + "." + field.getField()
                                + ".orderBy 不支持引用派生 Pivot 指标 '" + metric
                                + "'。parentShare/baselineRatio 是后处理输出，只能展示，不能参与轴级排序或 TopN");
                    }
                }
            }
            if (field.getHaving() != null) {
                for (MetricFilter filter : field.getHaving()) {
                    if (filter != null && derivedMetricNames.contains(filter.getMetric())) {
                        throw new IllegalArgumentException(axisName + "." + field.getField()
                                + ".having 不支持引用派生 Pivot 指标 '" + filter.getMetric()
                                + "'。parentShare/baselineRatio 是后处理输出，只能展示，不能参与轴级过滤");
                    }
                }
            }
        }
    }

    private String normalizeAxisOrderByMetric(String orderBy) {
        if (orderBy == null || orderBy.isBlank()) {
            return orderBy;
        }
        return orderBy.charAt(0) == '-' ? orderBy.substring(1) : orderBy;
    }

    private boolean allBaselineRatioMetricsUsePrePageAxisDomain(PivotRequest pivot) {
        return pivot.getBaselineRatioMetrics().stream()
                .allMatch(metric -> "prePageAxisDomain".equals(metric.getBaselineScope()));
    }

    private boolean allParentShareMetricsUsePrePageParent(PivotRequest pivot) {
        return pivot.getParentShareMetrics().stream()
                .allMatch(metric -> "prePageParent".equals(metric.getDenominatorScope()));
    }

    private boolean isSupportedParentSharePrePageParentWindow(PivotRequest pivot) {
        return !hasAxisDomainSliceRequest(pivot.getRows())
                && !hasAxisDomainSliceRequest(pivot.getColumns())
                && hasAxisStartOffsetRequest(pivot.getRows())
                && !hasAxisStartOffsetRequest(pivot.getColumns())
                && pivot.getRowLevelCount() >= 2;
    }

    private void validateAxisFieldsDomainSelection(List<AxisField> fields, String axisName) {
        if (fields == null) {
            return;
        }
        for (AxisField field : fields) {
            String fieldName = field.getField();
            if (field.getStart() != null && field.getStart() < 0) {
                throw new IllegalArgumentException(axisName + "." + fieldName + ".start 不能小于 0");
            }
            if (field.getOffset() != null && field.getOffset() < 0) {
                throw new IllegalArgumentException(axisName + "." + fieldName + ".offset 不能小于 0");
            }
            if (field.getLimit() != null && field.getLimit() < 0) {
                throw new IllegalArgumentException(axisName + "." + fieldName + ".limit 不能小于 0");
            }
            if ((field.getStart() != null || field.getOffset() != null) &&
                    (field.getLimit() == null || field.getLimit() <= 0)) {
                throw new IllegalArgumentException(axisName + "." + fieldName +
                        " 使用 start/offset 时必须同时指定正数 limit");
            }
            if (field.getStart() != null && field.getOffset() != null &&
                    !field.getStart().equals(field.getOffset())) {
                throw new IllegalArgumentException(axisName + "." + fieldName +
                        " 不能同时指定不同的 start 和 offset");
            }
        }
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

    private List<String> outputMetricsForContract(PivotRequest pivot) {
        return pivot == null ? Collections.emptyList() : pivot.getAllOutputMetricNames();
    }

    private Map<String, Object> buildPivotCapabilityContract(
            PivotRequest pivot,
            String outputFormat,
            HierarchyContext hierarchyCtx,
            HierarchyTreeBuilder.Skeleton hierarchySkeleton,
            List<String> rowFields,
            List<String> colFields,
            List<String> outputMetrics,
            boolean sqlPushdownUsed,
            boolean axisDomainSelectionUsed,
            boolean cascadeRequest) {

        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("name", "pivot_engine_capability_contract");
        contract.put("version", "v1");
        contract.put("signed", true);
        contract.put("output_format", outputFormat);
        contract.put("row_fields", rowFields);
        contract.put("column_fields", colFields);
        contract.put("metrics", outputMetrics);

        Map<String, Object> executionPath = new LinkedHashMap<>();
        executionPath.put("sql_pushdown_used", sqlPushdownUsed);
        executionPath.put("axis_domain_selection_used", axisDomainSelectionUsed);
        executionPath.put("cascade_generate_used", cascadeRequest);
        executionPath.put("memory_shaping_used", true);
        contract.put("execution_path", executionPath);

        contract.put("axes", buildAxisContracts(pivot));
        contract.put("tree_axis_contract", buildTreeAxisContract(hierarchyCtx, hierarchySkeleton, outputFormat));
        contract.put("drilldown_contract", buildDrilldownContract(pivot, axisDomainSelectionUsed, cascadeRequest));
        contract.put("required_capabilities", buildPivotRequiredCapabilities(
                hierarchyCtx, axisDomainSelectionUsed, cascadeRequest, hasPerParentWindowRequest(pivot)));
        return contract;
    }

    private List<Map<String, Object>> buildAxisContracts(PivotRequest pivot) {
        List<Map<String, Object>> axes = new ArrayList<>();
        addAxisContracts(axes, "rows", pivot == null ? null : pivot.getRows());
        addAxisContracts(axes, "columns", pivot == null ? null : pivot.getColumns());
        return axes;
    }

    private void addAxisContracts(List<Map<String, Object>> axes, String axisName, List<AxisField> fields) {
        if (fields == null) {
            return;
        }
        for (int i = 0; i < fields.size(); i++) {
            AxisField field = fields.get(i);
            Map<String, Object> axis = new LinkedHashMap<>();
            axis.put("axis", axisName);
            axis.put("level", i);
            axis.put("field", field.getField());
            axis.put("hierarchy_mode", field.getHierarchyMode());
            axis.put("limit", field.getLimit());
            axis.put("start", field.getStart());
            axis.put("offset", field.getOffset());
            axis.put("effective_offset", field.getEffectiveOffset());
            axis.put("has_domain_slice", field.getDomainSlice() != null && !field.getDomainSlice().isEmpty());
            axis.put("has_having", field.getHaving() != null && !field.getHaving().isEmpty());
            axis.put("order_by", field.getOrderBy());
            axes.add(axis);
        }
    }

    private Map<String, Object> buildTreeAxisContract(
            HierarchyContext hierarchyCtx,
            HierarchyTreeBuilder.Skeleton hierarchySkeleton,
            String outputFormat) {
        Map<String, Object> tree = new LinkedHashMap<>();
        boolean active = hierarchyCtx != null && hierarchyCtx.isTree();
        tree.put("signed", active);
        tree.put("active", active);
        tree.put("supported_scope", "rows_axis_parent_child_tree_only");
        tree.put("output_format_required", "tree");
        tree.put("output_format", outputFormat);
        tree.put("hierarchy_field", active ? hierarchyCtx.getTreeAxisField().getField() : null);
        tree.put("dimension", active ? hierarchyCtx.getDimName() : null);
        tree.put("id_field", active ? hierarchyCtx.getIdField() : null);
        tree.put("skeleton_nodes", hierarchySkeleton == null ? null : hierarchySkeleton.size());
        tree.put("unsupported_combinations", List.of(
                "columns_axis_tree",
                "output_format_not_tree",
                "crossjoin",
                "domainSlice_start_offset",
                "baselineRatio",
                "cascade_generate"));
        return tree;
    }

    private Map<String, Object> buildDrilldownContract(
            PivotRequest pivot,
            boolean axisDomainSelectionUsed,
            boolean cascadeRequest) {
        boolean perParentWindow = hasPerParentWindowRequest(pivot);
        Map<String, Object> drilldown = new LinkedHashMap<>();
        drilldown.put("signed", axisDomainSelectionUsed || cascadeRequest || perParentWindow);
        drilldown.put("axis_domain_selection_used", axisDomainSelectionUsed);
        drilldown.put("per_parent_window_used", perParentWindow);
        drilldown.put("cascade_generate_used", cascadeRequest);
        drilldown.put("supported_shapes", List.of(
                "single_level_axis_domainSlice",
                "single_level_axis_start_offset_limit",
                "multi_level_rows_child_start_offset_limit",
                "rows_two_level_cascade_topn_c2_v1"));
        drilldown.put("unsigned_shapes", List.of(
                "domain_tree_cursor",
                "interactive_expand_collapse_state",
                "multi_level_domainSlice",
                "columns_multi_level_start_offset",
                "tree_axis_domainSlice_start_offset"));
        return drilldown;
    }

    private List<String> buildPivotRequiredCapabilities(
            HierarchyContext hierarchyCtx,
            boolean axisDomainSelectionUsed,
            boolean cascadeRequest,
            boolean perParentWindow) {
        List<String> capabilities = new ArrayList<>();
        capabilities.add("pivot_phase_pipeline");
        capabilities.add("memory_result_shaping");
        if (hierarchyCtx != null && hierarchyCtx.isTree()) {
            capabilities.add("rows_axis_parent_child_tree");
            capabilities.add("hierarchy_skeleton_aux_query");
        }
        if (axisDomainSelectionUsed) {
            capabilities.add("axis_domain_selection_two_phase_query");
        }
        if (cascadeRequest) {
            capabilities.add("rows_two_level_cascade_generate_c2_v1");
        }
        if (perParentWindow) {
            capabilities.add("rows_child_per_parent_window");
        }
        return capabilities;
    }

    private boolean hasPerParentWindowRequest(PivotRequest pivot) {
        return pivot != null
                && pivot.getRowLevelCount() >= 2
                && hasAxisStartOffsetRequest(pivot.getRows())
                && !hasAxisDomainSliceRequest(pivot.getRows())
                && !hasAxisStartOffsetRequest(pivot.getColumns());
    }

    private record DomainConstrainedCellRequest(SemanticQueryRequest request, SemanticRequestContext context) {}

    private record AxisDomainSelectionResult(List<Map<String, Object>> rows,
                                             Map<String, Map<String, Number>> baselineRatioExternalValues,
                                             List<Map<String, Object>> baselineRatioEvidence) {}

    private record BaselineRatioPrePageAxisDomainResult(Map<String, Map<String, Number>> values,
                                                        List<Map<String, Object>> evidence) {
        private static BaselineRatioPrePageAxisDomainResult empty() {
            return new BaselineRatioPrePageAxisDomainResult(Collections.emptyMap(), Collections.emptyList());
        }
    }

    /**
     * 构建空结果响应
     */
    private SemanticQueryResponse buildEmptyResponse(PivotRequest pivot, long startTime,
                                                     Map<String, Object> capabilityContract) {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(Collections.emptyList());
        response.setTotal(0L);

        SemanticQueryResponse.DebugInfo debugInfo = new SemanticQueryResponse.DebugInfo();
        debugInfo.setDurationMs(System.currentTimeMillis() - startTime);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("pipeline", "pivot");
        extra.put("format", pivot.getOutputFormat());
        extra.put("pivotEngineContract", capabilityContract);
        debugInfo.setExtra(extra);
        response.setDebug(debugInfo);

        return response;
    }

    /**
     * 将 PivotResult 转换为 SemanticQueryResponse
     */
    private SemanticQueryResponse buildPivotResponse(PivotResult pivotResult, long startTime,
            List<Map<String, Object>> baselineRatioEvidence,
            List<Map<String, Object>> parentShareEvidence,
            Map<String, Object> capabilityContract) {
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
        extra.put("pivotEngineContract", capabilityContract);
        if (baselineRatioEvidence != null && !baselineRatioEvidence.isEmpty()) {
            extra.put("baselineRatioEvidence", baselineRatioEvidence);
        }
        if (parentShareEvidence != null && !parentShareEvidence.isEmpty()) {
            extra.put("parentShareEvidence", parentShareEvidence);
        }
        debugInfo.setExtra(extra);
        response.setDebug(debugInfo);

        return response;
    }
}
