package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.pivot.algo.*;
import com.foggyframework.dataset.model.engine.pivot.cascade.PivotCascadeException;
import com.foggyframework.dataset.model.engine.pivot.cascade.PivotCascadeRules;
import com.foggyframework.dataset.model.engine.pivot.rollup.*;
import com.foggyframework.dataset.model.engine.pivot.sql.PivotAxisDomainSqlPlanner;
import com.foggyframework.dataset.model.engine.pivot.sql.PivotPushdownUnsupportedException;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportField;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportPlan;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportTuple;
import com.foggyframework.dataset.model.impl.query.QueryFieldBindings;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.plugins.query_execution.ManagedRelationOptions;
import com.foggyframework.dataset.model.plugins.query_execution.ManagedSqlRelation;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.port.ManagedRelationExecutionPort;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.model.semantic.domain.pivot.MetricFilter;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotMetricItem;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotOptions;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.model.semantic.permission.AuthorizationSignature;
import com.foggyframework.dataset.model.semantic.permission.AuthorizationSignatureService;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionException;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionService;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.semantic.port.PivotRollupExecutionPort;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;

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

    private final PivotRollupExecutionPort pivotRollupExecutionPort;
    private final CardinalityBreaker cardinalityBreaker;
    private final QueryModelLoader queryModelLoader;
    private final ManagedRelationExecutionPort queryFacade;
    private final PivotOuterCacheProvider outerResponseCache;
    private final OuterCacheOptions outerCacheOptions;
    private final PivotOuterCacheModelIdentityProvider modelIdentityProvider;
    private final ModelPermissionService modelPermissionService;
    private final AuthorizationSignatureService authorizationSignatureService;

    public PivotPipeline(PivotRollupExecutionPort pivotRollupExecutionPort) {
        this(pivotRollupExecutionPort, new CardinalityBreaker(), null, null);
    }

    public PivotPipeline(PivotRollupExecutionPort pivotRollupExecutionPort,
                         CardinalityBreaker cardinalityBreaker) {
        this(pivotRollupExecutionPort, cardinalityBreaker, null, null);
    }

    public PivotPipeline(PivotRollupExecutionPort pivotRollupExecutionPort,
                         CardinalityBreaker cardinalityBreaker,
                         QueryModelLoader queryModelLoader) {
        this(pivotRollupExecutionPort, cardinalityBreaker, queryModelLoader, null);
    }

    public PivotPipeline(PivotRollupExecutionPort pivotRollupExecutionPort,
                         CardinalityBreaker cardinalityBreaker,
                         QueryModelLoader queryModelLoader,
                         ManagedRelationExecutionPort queryFacade) {
        this(pivotRollupExecutionPort, cardinalityBreaker, queryModelLoader, queryFacade,
                OuterCacheOptions.disabled());
    }

    public PivotPipeline(PivotRollupExecutionPort pivotRollupExecutionPort,
                         CardinalityBreaker cardinalityBreaker,
                         QueryModelLoader queryModelLoader,
                         ManagedRelationExecutionPort queryFacade,
                         OuterCacheOptions outerCacheOptions) {
        this(pivotRollupExecutionPort, cardinalityBreaker, queryModelLoader, queryFacade, outerCacheOptions,
                PivotOuterCacheModelIdentityProvider.empty(), null);
    }

    public PivotPipeline(PivotRollupExecutionPort pivotRollupExecutionPort,
                         CardinalityBreaker cardinalityBreaker,
                         QueryModelLoader queryModelLoader,
                         ManagedRelationExecutionPort queryFacade,
                         OuterCacheOptions outerCacheOptions,
                         PivotOuterCacheModelIdentityProvider modelIdentityProvider,
                         PivotOuterCacheProvider outerResponseCache) {
        this(pivotRollupExecutionPort, cardinalityBreaker, queryModelLoader, queryFacade,
                outerCacheOptions, modelIdentityProvider, outerResponseCache,
                new ModelPermissionService(), new AuthorizationSignatureService());
    }

    public PivotPipeline(PivotRollupExecutionPort pivotRollupExecutionPort,
                         CardinalityBreaker cardinalityBreaker,
                         QueryModelLoader queryModelLoader,
                         ManagedRelationExecutionPort queryFacade,
                         OuterCacheOptions outerCacheOptions,
                         PivotOuterCacheModelIdentityProvider modelIdentityProvider,
                         PivotOuterCacheProvider outerResponseCache,
                         ModelPermissionService modelPermissionService,
                         AuthorizationSignatureService authorizationSignatureService) {
        this.pivotRollupExecutionPort = pivotRollupExecutionPort;
        this.cardinalityBreaker = cardinalityBreaker;
        this.queryModelLoader = queryModelLoader;
        this.queryFacade = queryFacade;
        this.outerCacheOptions = outerCacheOptions == null
                ? OuterCacheOptions.disabled()
                : outerCacheOptions.normalized();
        this.modelIdentityProvider = modelIdentityProvider == null
                ? PivotOuterCacheModelIdentityProvider.empty()
                : modelIdentityProvider;
        this.outerResponseCache = outerResponseCache == null
                ? new PivotOuterResponseCache(this.outerCacheOptions)
                : outerResponseCache;
        this.modelPermissionService = modelPermissionService;
        this.authorizationSignatureService = authorizationSignatureService;
    }

    public record OuterCacheOptions(boolean enabled,
                                    long ttlMillis,
                                    int maximumSize,
                                    String bundleFingerprint,
                                    String modelFreshnessToken) {

        private static final long DEFAULT_TTL_MILLIS = 300_000L;
        private static final int DEFAULT_MAXIMUM_SIZE = 256;

        public OuterCacheOptions(boolean enabled, long ttlMillis, int maximumSize) {
            this(enabled, ttlMillis, maximumSize, "", "");
        }

        public static OuterCacheOptions disabled() {
            return new OuterCacheOptions(false, DEFAULT_TTL_MILLIS, DEFAULT_MAXIMUM_SIZE, "", "");
        }

        OuterCacheOptions normalized() {
            long safeTtlMillis = ttlMillis > 0L ? ttlMillis : DEFAULT_TTL_MILLIS;
            int safeMaximumSize = maximumSize > 0 ? maximumSize : DEFAULT_MAXIMUM_SIZE;
            return new OuterCacheOptions(enabled, safeTtlMillis, safeMaximumSize,
                    normalizeToken(bundleFingerprint), normalizeToken(modelFreshnessToken));
        }

        private static String normalizeToken(String value) {
            return value == null ? "" : value.trim();
        }
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
        PreparedPivot prepared = preparePivot(model, request, context);
        context = prepared.context();
        QueryModel queryModel = prepared.queryModel();
        CatalogResolution<QueryModel> catalogResolution = prepared.catalogResolution();
        PivotOuterCacheStrongIdentity.Assessment strongIdentityAssessment = prepared.strongIdentityAssessment();
        PivotRequest pivot = request.getPivot();
        HierarchyContext hierarchyCtx = prepared.hierarchy();
        List<String> rowFields = prepared.rowFields();
        List<String> colFields = prepared.colFields();
        List<String> metrics = prepared.metrics();
        List<PropertyResolver.ResolvedProperty> resolvedProps = prepared.properties();
        boolean cascadeRequest = PivotCascadeRules.isCascadeRequest(pivot);
        long startTime = System.currentTimeMillis();
        PivotDiagnosticCollector diagnostics = new PivotDiagnosticCollector(model);
        AuthorizationSignature authorizationSignature =
                authorizeForOuterCache(model, request, prepared);

        boolean outerCacheEnabled = outerResponseCache.isEnabled();
        Optional<PivotOuterCacheSafeProvider.UnavailableEvent> cacheEnabledUnavailable =
                consumeOuterCacheProviderUnavailable();
        String cacheEligibilityStage = outerCacheEnabled
                ? PivotOuterCacheTelemetry.CACHE_STAGE
                : PivotOuterCacheTelemetry.TELEMETRY_STAGE;
        PivotOuterCacheTelemetry.Evaluation cacheEvaluation = PivotOuterCacheTelemetry.evaluate(
                model, queryModel, request, context, hierarchyCtx.isTree(), cascadeRequest, cacheEligibilityStage,
                resolveOuterCacheModelIdentity(
                        context, model, queryModel, strongIdentityAssessment),
                authorizationSignature);
        diagnostics.cacheIdentity(cacheEvaluation);
        if (!cacheEvaluation.refused()) {
            diagnostics.cacheLookup(cacheEvaluation.keyHash(), cacheEligibilityStage, cacheEvaluation.shapeClass());
        }
        recordOuterCacheProviderUnavailable(diagnostics, cacheEvaluation, cacheEligibilityStage,
                cacheEnabledUnavailable);
        if (cacheEvaluation.refused()) {
            diagnostics.cacheRefused(cacheEvaluation.keyHash(), cacheEligibilityStage,
                    cacheEvaluation.refusalReason(), cacheEvaluation.shapeClass());
        } else if (!outerCacheEnabled) {
            diagnostics.cacheMiss(cacheEvaluation.keyHash(), cacheEligibilityStage,
                    cacheEnabledUnavailable.isPresent()
                            ? PivotOuterCacheTelemetry.CACHE_PROVIDER_UNAVAILABLE_REASON
                            : PivotOuterCacheTelemetry.TELEMETRY_MISS_REASON,
                    cacheEvaluation.shapeClass());
        } else {
            PivotOuterCacheProvider.LookupResult cacheLookup = outerResponseCache.lookup(
                    cacheEvaluation.keyHash(), System.currentTimeMillis());
            Optional<PivotOuterCacheSafeProvider.UnavailableEvent> lookupUnavailable =
                    consumeOuterCacheProviderUnavailable();
            recordOuterCacheProviderUnavailable(diagnostics, cacheEvaluation, cacheEligibilityStage,
                    lookupUnavailable);
            if (cacheLookup.hit()) {
                diagnostics.cacheHit(cacheEvaluation.keyHash(), cacheEligibilityStage,
                        cacheLookup.ageMs(), outerResponseCache.name(), cacheEvaluation.shapeClass());
                return cachedResponse(cacheLookup.response(), startTime, diagnostics.snapshot());
            }
            if (cacheLookup.expired()) {
                diagnostics.cacheEvicted(cacheEvaluation.keyHash(), cacheEligibilityStage,
                        PivotOuterCacheTelemetry.CACHE_EXPIRED_REASON, cacheEvaluation.shapeClass());
            }
            String missReason;
            if (lookupUnavailable.isPresent()) {
                missReason = PivotOuterCacheTelemetry.CACHE_PROVIDER_UNAVAILABLE_REASON;
            } else if (cacheLookup.expired()) {
                missReason = PivotOuterCacheTelemetry.CACHE_EXPIRED_REASON;
            } else {
                missReason = PivotOuterCacheTelemetry.CACHE_MISS_REASON;
            }
            diagnostics.cacheMiss(cacheEvaluation.keyHash(), cacheEligibilityStage, missReason,
                    cacheEvaluation.shapeClass());
        }

        // Same static dependencies as validate, compiled under the EXECUTE permission action.
        // No business SQL is executed while preparing auxiliary plans.
        if (prepared.queryModel() != null && pivotRollupExecutionPort != null)
            for (var auxiliary : staticAuxiliaryRequests(request, prepared))
                pivotRollupExecutionPort.generateRollupSql(model, auxiliary, context);

        if (hasZeroWindow(pivot)) {
            // Empty windows still compile all requested fields under the execution action.
            pivotRollupExecutionPort.generateRollupSql(
                    model,
                    buildPhase1FlatRequest(request, rowFields, colFields, metrics, queryModel),
                    context);
            return buildEmptyResponse(
                    pivot,
                    startTime,
                    buildPivotCapabilityContract(
                            pivot,
                            pivot.getOutputFormat(),
                            hierarchyCtx,
                            null,
                            rowFields,
                            colFields,
                            outputMetricsForContract(pivot),
                            false,
                            false,
                            cascadeRequest),
                    diagnostics.snapshot());
        }

        // ===== Phase 1: SQL 萃取（不含 properties）=====
        // 检测是否可以使用 SQL pushdown（CTE + Window Function + 有 having/limit）
        boolean sqlPushdownUsed = false;
        boolean axisDomainSelectionUsed = false;
        boolean axisDomainSelectionRequest = hasAxisDomainSelectionRequest(pivot);
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
                PivotTelemetry.cascadeRefused(logger, model, sqlPushdownSkipReason, null);
                throw PivotCascadeException.sqlRequired(
                        "SQL pushdown is unavailable: " + sqlPushdownSkipReason + ".");
            }
            PivotTelemetry.sqlPushdownSkipped(logger, model, sqlPushdownSkipReason);
            diagnostics.sqlPushdownSkipped(sqlPushdownSkipReason);
            diagnostics.axisDomainSelectionStarted(sqlPushdownSkipReason);
            logger.debug("[Pivot] Phase 1: Axis domain selection path for model={}", model);
            AxisDomainSelectionResult axisDomainResult = executePhase1WithAxisDomainSelection(model, request, context,
                    rowFields, colFields, metrics, pivot, queryModel, diagnostics);
            resultSet = axisDomainResult.rows();
            baselineRatioExternalValues = axisDomainResult.baselineRatioExternalValues();
            baselineRatioEvidence = axisDomainResult.baselineRatioEvidence();
            axisDomainSelectionUsed = true;
        } else if (sqlPushdownSkipReason == null) {
            logger.debug("[Pivot] Phase 1: SQL pushdown path for model={}", model);
            try {
                PivotTelemetry.sqlPushdownAttempted(logger, model);
                diagnostics.sqlPushdownAttempted();
                long pushdownStart = System.currentTimeMillis();
                resultSet = executePhase1WithSqlPushdown(model, request, context,
                        rowFields, colFields, metrics, pivot, queryModel);
                sqlPushdownUsed = true;
                long pushdownDurationMs = System.currentTimeMillis() - pushdownStart;
                PivotTelemetry.sqlPushdownSucceeded(logger, model, resultSet.size(),
                        pushdownDurationMs);
                diagnostics.sqlPushdownSucceeded(resultSet.size(), pushdownDurationMs);
            } catch (PivotCascadeException e) {
                throw e;
            } catch (PivotPushdownUnsupportedException | UnsupportedOperationException e) {
                if (cascadeRequest) {
                    PivotTelemetry.cascadeRefused(logger, model, "plannerFailure", e);
                    throw PivotCascadeException.sqlRequired(
                            "Planner failure: " + e.getMessage(), e);
                }
                // Fail-closed: fallback to memory path
                PivotTelemetry.sqlPushdownFallback(logger, model, e);
                diagnostics.sqlPushdownFallback(e);
                resultSet = executePhase1(model, request, context,
                        rowFields, colFields, metrics, queryModel);
            }
        } else {
            if (cascadeRequest) {
                PivotTelemetry.cascadeRefused(logger, model, sqlPushdownSkipReason, null);
                throw PivotCascadeException.sqlRequired(
                        "SQL pushdown is unavailable: " + sqlPushdownSkipReason + ".");
            }
            PivotTelemetry.sqlPushdownSkipped(logger, model, sqlPushdownSkipReason);
            diagnostics.sqlPushdownSkipped(sqlPushdownSkipReason);
            logger.debug("[Pivot] Phase 1: Memory path for model={}", model);
            resultSet = executePhase1(model, request, context,
                    rowFields, colFields, metrics, queryModel);
        }

        if (resultSet.isEmpty()) {
            diagnostics.executionPath(sqlPushdownUsed, axisDomainSelectionUsed, cascadeRequest,
                    0, 0, 0);
            Map<String, Object> capabilityContract = buildPivotCapabilityContract(
                    pivot, pivot.getOutputFormat(), hierarchyCtx, null, rowFields, colFields, outputMetricsForContract(pivot),
                    sqlPushdownUsed, axisDomainSelectionUsed, cascadeRequest);
            SemanticQueryResponse response = buildEmptyResponse(pivot, startTime, capabilityContract, diagnostics.snapshot());
            return storeOuterCacheIfEligible(cacheEvaluation, cacheEligibilityStage, diagnostics, response, model, context);
        }

        // ===== Phase 1.5: 父子维度骨架查询 =====
        HierarchyTreeBuilder.Skeleton hierarchySkeleton = null;
        if (hierarchyCtx.isTree()) {
            logger.debug(
                    "[Pivot] Phase 1.5: Hierarchy skeleton query for dim={}",
                    hierarchyCtx.getDimName());
            hierarchySkeleton =
                    executeHierarchySkeleton(
                            model,
                            request,
                            context,
                            hierarchyCtx.getParentField(),
                            hierarchyCtx.getIdField(),
                            queryModel);
        }

        // ===== Phase 2: 内存加工 =====
        logger.debug("[Pivot] Phase 2: Memory cube processing, {} rows", resultSet.size());

        if (!sqlPushdownUsed && !axisDomainSelectionUsed) {
            if (requiresPrePageParentDenominator(pivot)) {
                // Having precedes the parent denominator, while member windows follow it.
                List<Map<String, Object>> beforeWindow =
                        applyMemberControls(
                                model,
                                request,
                                context,
                                rowFields,
                                colFields,
                                metrics,
                                queryModel,
                                resultSet,
                                false);
                parentShareExternalValues =
                        ParentShareCalculator.buildExternalParentAggIndex(
                                beforeWindow,
                                pivot,
                                rowFields,
                                colFields,
                                metricAggregations(metrics, queryModel));
                parentShareEvidence =
                        buildParentSharePrePageParentEvidence(
                                pivot,
                                rowFields,
                                colFields,
                                parentShareExternalValues,
                                beforeWindow.size());
            }
            if (hierarchyCtx.isTree()) {
                List<Map<String, Object>> allCells = resultSet;
                resultSet =
                        HierarchyTreeBuilder.window(
                                resultSet,
                                hierarchySkeleton,
                                hierarchyCtx.getIdField(),
                                hierarchyCtx.getTreeAxisField(),
                                metricAggregations(metrics, queryModel));
                if (hasAxisDomainOperations(pivot.getColumns())) {
                    var columnCells =
                            applyMemberControls(
                                    model,
                                    request,
                                    context,
                                    rowFields,
                                    colFields,
                                    metrics,
                                    queryModel,
                                    allCells,
                                    true);
                    Set<Map<String, Object>> selectedColumns = new HashSet<>(columnCells);
                    resultSet = resultSet.stream().filter(selectedColumns::contains).toList();
                }
            } else
                resultSet =
                        applyMemberControls(
                                model,
                                request,
                                context,
                                rowFields,
                                colFields,
                                metrics,
                                queryModel,
                                resultSet,
                                true);
        } else if (axisDomainSelectionUsed) {
            logger.debug(
                    "[Pivot] Phase 2: Skipping Having/TopN (already done by axis domain"
                            + " selection)");
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
            rollupPlans = prepared.rollupPlans();
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
                logger.debug(
                        "[Pivot] Phase 2.4: Auxiliary rollup queries, {} grains", grains.size());

                NonAdditiveRollupExecutor executor = new NonAdditiveRollupExecutor(pivotRollupExecutionPort, queryModel);
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
                            "Pivot subtotal/grandTotal: non-additive metric (AVG/COUNT_DISTINCT)"
                                    + " 的辅助查询 surviving domain 超过安全限制（"
                                    + e.getDomainSize()
                                    + " > "
                                    + e.getMaxAllowed()
                                    + "）。请减少 TopN limit 数量，或关闭"
                                    + " rowSubtotals/columnSubtotals/grandTotal。",
                            e);
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
            logger.debug(
                    "[Pivot] Phase 2.7: Property attachment for {} properties",
                    resolvedProps.size());
            Map<String, Map<Object, Map<String, Object>>> lookupTables =
                    executePropertyLookup(model, request, context, resolvedProps, queryModel);
            PropertyAttacher.attach(resultSet, resolvedProps, lookupTables);
        }

        // ===== Phase 2.8: ParentShare 父级占比计算 =====
        if (!pivot.getParentShareMetrics().isEmpty()) {
            logger.debug("[Pivot] Phase 2.8: ParentShare calculation, {} metrics",
                    pivot.getParentShareMetrics().size());
            ParentShareCalculator.apply(
                    resultSet,
                    pivot,
                    rowFields,
                    colFields,
                    parentShareExternalValues,
                    metricAggregations(metrics, queryModel));
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
            logger.debug(
                    "[Pivot] Phase 3: Hierarchy tree shaping, skeleton size={}",
                    hierarchySkeleton.size());
            List<String> displayFields = extractFieldNames(pivot.getRows());
            List<PivotResult.TreeNode> treeData = HierarchyTreeBuilder.build(
                    resultSet, hierarchySkeleton, hierarchyCtx.getIdField(),
                    displayFields, colFields, outputMetrics);
            HierarchyTreeBuilder.limitDepth(
                    treeData, hierarchyCtx.getTreeAxisField().getEffectiveExpandDepth());
            pivotResult = new PivotResult();
            pivotResult.setFormat("tree");
            pivotResult.setTreeData(treeData);
        } else {
            logger.debug("[Pivot] Phase 3: Result shaping to format={}", pivot.getOutputFormat());
            pivotResult = ResultShaper.shape(resultSet, pivot, rowFields, colFields, outputMetrics);
        }

        // ===== 构建响应 =====
        diagnostics.executionPath(sqlPushdownUsed, axisDomainSelectionUsed, cascadeRequest,
                resultSet.size(), rowDomain.size(), colDomain.size());
        Map<String, Object> capabilityContract = buildPivotCapabilityContract(
                pivot, pivotResult.getFormat(), hierarchyCtx, hierarchySkeleton, rowFields, colFields, outputMetrics,
                sqlPushdownUsed, axisDomainSelectionUsed, cascadeRequest);
        SemanticQueryResponse response = buildPivotResponse(pivotResult, startTime, baselineRatioEvidence,
                parentShareEvidence, capabilityContract, diagnostics.snapshot());
        return storeOuterCacheIfEligible(cacheEvaluation, cacheEligibilityStage, diagnostics, response, model, context);
    }

    /** Static Pivot validation. Never enumerate domains, query facts, or read/write result caches. */
    public SemanticQueryResponse validate(String model, SemanticQueryRequest request,
                                          SemanticRequestContext context) {
        context = (context == null ? SemanticRequestContext.empty() : context)
                .withPermissionAction(PermissionAction.VALIDATE);
        PreparedPivot prepared = preparePivot(model, request, context);
        context = prepared.context();
        PivotRequest pivot = request.getPivot();
        List<String> warnings = new ArrayList<>();
        validateFlatRequest(model, buildPhase1FlatRequest(request, prepared.rowFields(), prepared.colFields(),
                prepared.metrics(), prepared.queryModel()), context, warnings);
        for (var auxiliary : staticAuxiliaryRequests(request, prepared))
            validateFlatRequest(model, auxiliary, context, warnings);

        // Compile the same optional pushdown plan without executing the resulting SQL.
        String skip = getSqlPushdownSkipReason(pivot, prepared.hierarchy());
        if (hasAxisDomainSelectionRequest(pivot)) {
            if (PivotCascadeRules.isCascadeRequest(pivot)) {
                throw PivotCascadeException.sqlRequired(
                        "SQL pushdown is unavailable: axisDomainSelectionRequiresTwoPhaseQuery.");
            }
            for (List<AxisField> axis : List.of(pivot.getRows(),
                    pivot.getColumns() == null ? List.<AxisField>of() : pivot.getColumns())) {
                if (!axis.isEmpty() && hasAxisDomainSelectionRequest(axis.get(0))) {
                    validateFlatRequest(model, buildAxisDomainRequest(request, axis.get(0), prepared.metrics(),
                            prepared.queryModel(), true), context, warnings);
                }
            }
        } else if (skip == null) {
            try {
                planPhase1WithSqlPushdown(model, request, context, prepared.rowFields(), prepared.colFields(),
                        prepared.metrics(), pivot, prepared.queryModel());
            } catch (PivotPushdownUnsupportedException | UnsupportedOperationException failure) {
                if (PivotCascadeRules.isCascadeRequest(pivot)) {
                    throw PivotCascadeException.sqlRequired(
                            "Planner failure: " + failure.getMessage(), failure);
                }
                // Match execution's optional memory fallback.
            }
        } else if (PivotCascadeRules.isCascadeRequest(pivot)) {
            throw PivotCascadeException.sqlRequired("SQL pushdown is unavailable: " + skip + ".");
        }
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setWarnings(warnings.isEmpty() ? null : new ArrayList<>(new LinkedHashSet<>(warnings)));
        return response;
    }

    private List<SemanticQueryRequest> staticAuxiliaryRequests(
            SemanticQueryRequest request, PreparedPivot prepared) {
        List<SemanticQueryRequest> requests = new ArrayList<>();
        PivotRequest pivot = request.getPivot();
        {
            for (var axis :
                    List.of(
                            pivot.getRows(),
                            pivot.getColumns() == null ? List.<AxisField>of() : pivot.getColumns()))
                for (int i = 0; i < axis.size(); i++)
                    if (axis.stream().noneMatch(AxisField::isTreeMode)
                            && hasMemberControl(axis.get(i)))
                        requests.add(
                                buildMemberRequest(
                                        request,
                                        axis,
                                        i,
                                        prepared.metrics(),
                                        prepared.queryModel()));
        }
        if (!prepared.rollupPlans().isEmpty())
            requests.addAll(
                    new NonAdditiveRollupExecutor(pivotRollupExecutionPort, prepared.queryModel())
                            .validationRequests(
                                    request,
                                    RollupGrainEnumerator.enumerate(
                                            prepared.rowFields(),
                                            prepared.colFields(),
                                            pivot.getOptions()),
                                    prepared.rollupPlans(),
                                    prepared.rowFields(),
                                    prepared.colFields()));
        if (prepared.hierarchy().isTree())
            requests.add(
                    buildHierarchySkeletonRequest(
                            prepared.hierarchy().getParentField(),
                            prepared.hierarchy().getIdField()));
        for (var properties : groupProperties(prepared.properties()).values())
            requests.add(buildPropertyLookupRequest(request, properties));
        return requests;
    }

    private void validateFlatRequest(String model, SemanticQueryRequest flatRequest,
                                     SemanticRequestContext context, List<String> warnings) {
        SemanticQueryResponse response =
                pivotRollupExecutionPort.queryModel(model, flatRequest, "validate", context);
        if (response != null && response.getWarnings() != null) warnings.addAll(response.getWarnings());
    }

    /** Request shape, model identity and result-stage rules shared by validate and execute. */
    private PreparedPivot preparePivot(String model, SemanticQueryRequest request, SemanticRequestContext context) {
        context = context == null ? SemanticRequestContext.empty() : context;
        PivotRequest pivot = request.getPivot();

        // ===== 前置校验 =====
        validatePivotRequest(request);

        // ===== 提前加载 QueryModel（用于 S8.3 度量元数据 + Properties）=====
        CatalogResolution<QueryModel> catalogResolution = null;
        QueryModel queryModel = null;
        if (context.getCatalogResolution() != null) {
            catalogResolution = context.getCatalogResolution();
            if (!model.equals(catalogResolution.canonicalName())
                    || !com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity
                            .canonicalNamespace(context.getNamespace())
                            .equals(catalogResolution.catalogIdentity().namespace()))
                throw new IllegalArgumentException("PIVOT_CATALOG_PIN_MISMATCH");
            queryModel = catalogResolution.model();
        } else if (queryModelLoader != null) {
            catalogResolution = queryModelLoader.resolveJdbcQueryModel(model, context.getNamespace());
            queryModel = catalogResolution != null
                    ? catalogResolution.model()
                    : queryModelLoader.getJdbcQueryModel(model, context.getNamespace());
        }
        PivotOuterCacheStrongIdentity.Assessment strongIdentityAssessment =
                PivotOuterCacheStrongIdentity.assess(catalogResolution, context.getNamespace());
        if (strongIdentityAssessment.pinnable()) {
            context = context.withCatalogResolution(catalogResolution);
        }
        // Authorize the model before exposing metadata-dependent validation failures.
        // Downstream governed queries reuse the same request-local permission session.
        if (queryModel != null && queryModel.getModelPermissions() != null && modelPermissionService != null) {
            PermissionAction action = context.getPermissionAction() != null
                    ? context.getPermissionAction() : PermissionAction.EXECUTE;
            if (!modelPermissionService.evaluate(queryModel, context.getNamespace(), action,
                    context.getRequestIdentity(), context.getPermissionSession()).isAllow()) {
                throw ModelPermissionException.denied();
            }
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
        HierarchyContext hierarchyCtx = HierarchyContext.bind(pivot.getRows(), queryModel);
        if (hierarchyCtx.isTree() && !pivot.getBaselineRatioMetrics().isEmpty()) {
            throw new IllegalArgumentException(
                    "当前版本不支持 hierarchyMode=tree 与 baselineRatio 派生指标同时使用");
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
                logger.debug(
                        "[Pivot] QueryModelLoader not available, properties will be included in"
                                + " Phase 1 as fallback");
            }
        }

        Set<String> identities = new LinkedHashSet<>();
        for (String field : rowFields)
            if (!identities.add(field))
                throw new IllegalArgumentException("PIVOT_DUPLICATE_AXIS: " + field);
        for (String field : colFields)
            if (!identities.add(field))
                throw new IllegalArgumentException("PIVOT_DUPLICATE_AXIS: " + field);
        if (pivot.getProperties() != null)
            for (String field : pivot.getProperties())
                if (!identities.add(field))
                    throw new IllegalArgumentException("PIVOT_OUTPUT_COLLISION: " + field);
        for (String field : pivot.getAllOutputMetricNames())
            if (field.startsWith("_pivot_") || field.equals("_sys_meta") || !identities.add(field))
                throw new IllegalArgumentException("PIVOT_OUTPUT_COLLISION: " + field);
        if (identities.contains("_sys_meta")
                || identities.stream().anyMatch(f -> f.startsWith("_pivot_")))
            throw new IllegalArgumentException("PIVOT_RESERVED_OUTPUT");
        validateAxisControlReferences(pivot, rowFields, colFields, metrics);
        if (hierarchyCtx.isTree()) {
            AxisField tree = hierarchyCtx.getTreeAxisField();
            if (tree.getEffectiveExpandDepth() < -1)
                throw new IllegalArgumentException("PIVOT_TREE_DEPTH_INVALID");
            if (hasMemberControl(tree)) {
                Map<String, DbAggregation> aggs = metricAggregations(metrics, queryModel);
                Set<String> needed = new HashSet<>();
                if (tree.getHaving() != null)
                    tree.getHaving().forEach(f -> needed.add(f.getMetric()));
                if (tree.getOrderBy() != null)
                    tree.getOrderBy()
                            .forEach(
                                    f -> {
                                        String n = f.startsWith("-") ? f.substring(1) : f;
                                        if (metrics.contains(n)) needed.add(n);
                                    });
                for (String n : needed)
                    if (aggs.get(n) == null
                            || !Set.of(
                                            DbAggregation.SUM,
                                            DbAggregation.COUNT,
                                            DbAggregation.MIN,
                                            DbAggregation.MAX)
                                    .contains(aggs.get(n)))
                        throw new IllegalArgumentException(
                                "PIVOT_TREE_RANK_REAGGREGATION_UNSUPPORTED: " + n);
            }
        }
        List<RollupMetricPlan> rollupPlans = List.of();
        PivotOptions opts = pivot.getOptions();
        if (opts != null
                && (opts.isRowSubtotals() || opts.isColumnSubtotals() || opts.isGrandTotal())) {
            rollupPlans =
                    MetricAdditivityAnalyzer.analyze(
                            metrics, queryModel, request.getCalculatedFields());
            for (var plan : rollupPlans)
                if (plan.getStrategy() == RollupStrategy.UNSUPPORTED)
                    throw new IllegalArgumentException(
                            "PIVOT_ROLLUP_UNSUPPORTED: " + plan.getMetricName());
        }
        return new PreparedPivot(
                context,
                queryModel,
                catalogResolution,
                strongIdentityAssessment,
                hierarchyCtx,
                rowFields,
                colFields,
                metrics,
                resolvedProps,
                rollupPlans);
    }

    private record PreparedPivot(
            SemanticRequestContext context,
            QueryModel queryModel,
            CatalogResolution<QueryModel> catalogResolution,
            PivotOuterCacheStrongIdentity.Assessment strongIdentityAssessment,
            HierarchyContext hierarchy,
            List<String> rowFields,
            List<String> colFields,
            List<String> metrics,
            List<PropertyResolver.ResolvedProperty> properties,
            List<RollupMetricPlan> rollupPlans) {}

    private AuthorizationSignature authorizeForOuterCache(
            String model, SemanticQueryRequest request, PreparedPivot prepared) {
        if (!outerCacheOptions.enabled()
                || !prepared.strongIdentityAssessment().pinnable()
                || queryFacade == null
                || authorizationSignatureService == null) return null;
        List<SemanticQueryRequest> requests = new ArrayList<>();
        requests.add(
                buildPhase1FlatRequest(
                        request,
                        prepared.rowFields(),
                        prepared.colFields(),
                        prepared.metrics(),
                        prepared.queryModel()));
        requests.addAll(staticAuxiliaryRequests(request, prepared));
        List<AuthorizationSignature> signatures = new ArrayList<>();
        List<com.foggyframework.dataset.model.semantic.port.SemanticSqlGeneration> plans =
                new ArrayList<>();
        for (SemanticQueryRequest flat : requests) {
            ModelResultContext permissionContext =
                    buildManagedRelationContext(model, flat, prepared.context());
            try {
                ManagedSqlRelation relation =
                        queryFacade.prepareManagedRelation(
                                permissionContext,
                                ManagedRelationOptions.builder()
                                        .purpose("PivotCacheAuthorization")
                                        .disableInnerCacheShortCircuit(true)
                                        .build());
                if (!relation.isPermissionValidated()) return null;
                signatures.add(
                        authorizationSignatureService.compute(permissionContext).orElse(null));
                plans.add(
                        new com.foggyframework.dataset.model.semantic.port.SemanticSqlGeneration(
                                relation.getSql(), relation.getParams()));
            } catch (PivotPushdownUnsupportedException
                    | UnsupportedOperationException unsupported) {
                // An adapter unable to expose a complete governed plan is deliberately
                // non-cacheable.
                return null;
            }
        }
        return PivotOuterCacheTelemetry.authorizationSnapshot(signatures, plans);
    }

    private ModelResultContext.SecurityContext copySecurityContext(
            ModelResultContext.SecurityContext source
    ) {
        if (source == null) {
            return null;
        }
        return ModelResultContext.SecurityContext.builder()
                .authorization(source.getAuthorization())
                .userId(source.getUserId())
                .roles(source.getRoles() == null ? null : new ArrayList<>(source.getRoles()))
                .tenantId(source.getTenantId())
                .deptId(source.getDeptId())
                .attributes(
                        source.getAttributes() == null
                                ? null
                                : new LinkedHashMap<>(source.getAttributes()))
                .build();
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

        var dependencies = com.foggyframework.dataset.model.engine.expression.CalculatedDependencyGraph.forQuery(queryModel, current);
        for (CalculatedFieldDef field : current) referencedFields.addAll(com.foggyframework.dataset.model.engine.expression.CalculatedFieldDependencies.direct(field));
        dependencies.includeDependencies(referencedFields);
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

    private PivotOuterCacheTelemetry.ModelIdentity resolveOuterCacheModelIdentity(SemanticRequestContext context,
                                                                                  String model,
                                                                                  QueryModel queryModel,
                                                                                  PivotOuterCacheStrongIdentity.Assessment
                                                                                          strongIdentityAssessment) {
        PivotOuterCacheModelIdentity provided = PivotOuterCacheModelIdentity.empty();
        Class<? extends Throwable> providerFailureType = null;
        try {
            provided = modelIdentityProvider.resolve(context != null ? context.getNamespace() : null, model, queryModel);
        } catch (Exception e) {
            providerFailureType = e.getClass();
            logger.warn(
                    "[Pivot] Outer cache supplementary identity provider failed: model={},"
                            + " reasonClass={}",
                    model,
                    e.getClass().getSimpleName());
        }
        PivotOuterCacheModelIdentity normalized = provided == null
                ? PivotOuterCacheModelIdentity.empty()
                : provided.normalized();
        return PivotOuterCacheTelemetry.ModelIdentity.from(
                strongIdentityAssessment,
                normalized,
                outerCacheOptions.bundleFingerprint(),
                outerCacheOptions.modelFreshnessToken(),
                providerFailureType);
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
        SemanticQueryRequest flatRequest = buildPhase1FlatRequest(originalRequest, rowFields, colFields, metrics, queryModel);
        SemanticQueryResponse response =
                pivotRollupExecutionPort.queryModel(model, flatRequest, "execute", context);
        return QueryFieldBindings.logicalRows(response.getItems(), queryModel, flatRequest.getColumns(), flatRequest.getCalculatedFields());
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
            List<String> metrics, PivotRequest pivot, QueryModel queryModel,
            PivotDiagnosticCollector diagnostics) {

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
                model, originalRequest, pivot, rowDomain, columnDomain, context, diagnostics);
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
        Map<Object, Integer> rowOrder = new LinkedHashMap<>(), colOrder = new LinkedHashMap<>();
        if (rowDomain != null) for (Object value : rowDomain) rowOrder.put(value, rowOrder.size());
        if (columnDomain != null)
            for (Object value : columnDomain) colOrder.put(value, colOrder.size());
        filtered = new ArrayList<>(filtered);
        filtered.sort(
                Comparator.comparingInt(
                                (Map<String, Object> row) ->
                                        rowDomain == null
                                                ? 0
                                                : rowOrder.get(row.get(rowFields.get(0))))
                        .thenComparingInt(
                                row ->
                                        columnDomain == null
                                                ? 0
                                                : colOrder.get(row.get(colFields.get(0)))));
        BaselineRatioPrePageAxisDomainResult baselineRatioPrePageResult =
                executeBaselineRatioPrePageAxisDomain(model, originalRequest, context,
                        rowFields, colFields, metrics, pivot, queryModel,
                        rowDomain, columnDomain, prePageColumnDomain, diagnostics);
        return new AxisDomainSelectionResult(filtered, baselineRatioPrePageResult.values(),
                baselineRatioPrePageResult.evidence());
    }

    private DomainConstrainedCellRequest buildAxisDomainConstrainedCellRequest(
            SemanticQueryRequest originalRequest, PivotRequest pivot,
            Set<Object> rowDomain, Set<Object> columnDomain,
            SemanticRequestContext context) {
        return buildAxisDomainConstrainedCellRequest(
                null, originalRequest, pivot, rowDomain, columnDomain, context, null);
    }

    private DomainConstrainedCellRequest buildAxisDomainConstrainedCellRequest(
            String model,
            SemanticQueryRequest originalRequest, PivotRequest pivot,
            Set<Object> rowDomain, Set<Object> columnDomain,
            SemanticRequestContext context,
            PivotDiagnosticCollector diagnostics) {

        SemanticQueryRequest cellRequest = new SemanticQueryRequest();
        List<DomainTransportPlan> transportPlans = new ArrayList<>();
        cellRequest.setSlice(buildAxisDomainConstrainedSlice(originalRequest, pivot,
                rowDomain, columnDomain, transportPlans));
        cellRequest.setCalculatedFields(originalRequest.getCalculatedFields());
        recordDomainTransportPlans(model, transportPlans, diagnostics);

        SemanticRequestContext effectiveContext = context;
        if (!transportPlans.isEmpty()) {
            effectiveContext = withDomainTransportPlans(context, transportPlans);
        }
        return new DomainConstrainedCellRequest(cellRequest, effectiveContext);
    }

    private void recordDomainTransportPlans(String model,
                                            List<DomainTransportPlan> transportPlans,
                                            PivotDiagnosticCollector diagnostics) {
        if (transportPlans == null || transportPlans.isEmpty()) {
            return;
        }
        for (DomainTransportPlan plan : transportPlans) {
            if (model != null) {
                PivotTelemetry.domainTransportPlanned(logger, model, plan.getRelationName(),
                        plan.getFields().size(), plan.getTuples().size(), plan.parameterCount());
            }
            if (diagnostics != null) {
                diagnostics.domainTransportPlanned(plan);
            }
        }
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
            if (!domain.contains(null)) slices.add(inSlice(field, domain));
            else {
                SemanticQueryRequest.SliceItem isNull = new SemanticQueryRequest.SliceItem();
                isNull.setField(field);
                isNull.setOp("is null");
                Set<Object> nonNull =
                        domain.stream()
                                .filter(Objects::nonNull)
                                .collect(Collectors.toCollection(LinkedHashSet::new));
                if (nonNull.isEmpty()) slices.add(isNull);
                else {
                    SemanticQueryRequest.SliceItem either = new SemanticQueryRequest.SliceItem();
                    either.setOr(List.of(inSlice(field, nonNull), isNull));
                    slices.add(either);
                }
            }
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
        SemanticQueryRequest domainRequest = buildAxisDomainRequest(originalRequest, axisField, metrics, queryModel, applyWindow);
        List<String> columns = domainRequest.getColumns();

        SemanticQueryResponse response =
                pivotRollupExecutionPort.queryModel(model, domainRequest, "execute", context);
        List<Map<String, Object>> domainRows = QueryFieldBindings.logicalRows(response.getItems(), queryModel, columns, domainRequest.getCalculatedFields());
        boolean sqlWindow = applyWindow && axisField.getLimit() != null;
        if ((!sqlWindow
                        && (domainRows.size() > CardinalityBreaker.DEFAULT_ROW_LIMIT
                                || Boolean.TRUE.equals(response.getHasNext())
                                || (response.getPagination() != null
                                        && Boolean.TRUE.equals(
                                                response.getPagination().getHasMore()))))
                || (response.getTruncationInfo() != null && !response.getTruncationInfo().isEmpty())
                || (sqlWindow
                        && response.getPagination() != null
                        && response.getPagination().getLimit() != null
                        && response.getPagination().getLimit() < axisField.getLimit()))
            throw new IllegalArgumentException(
                    "PIVOT_AXIS_DOMAIN_LIMIT: complete domain exceeds "
                            + CardinalityBreaker.DEFAULT_ROW_LIMIT
                            + " members");

        domainRows = AxisHavingFilter.apply(domainRows, List.of(axisField), metrics);
        sortAxisDomainRows(domainRows, axisField);
        if (applyWindow && !sqlWindow) {
            domainRows = applyAxisDomainWindow(domainRows, axisField);
        }

        Set<Object> domain = new LinkedHashSet<>();
        for (Map<String, Object> row : domainRows) {
            domain.add(row.get(field));
        }
        return domain;
    }

    private SemanticQueryRequest buildAxisDomainRequest(SemanticQueryRequest originalRequest, AxisField axisField,
                                                         List<String> metrics, QueryModel queryModel, boolean applyWindow) {
        SemanticQueryRequest domainRequest = new SemanticQueryRequest();

        List<String> columns = new ArrayList<>();
        columns.add(axisField.getField());
        columns.addAll(metrics);
        domainRequest.setColumns(columns);

        List<SemanticQueryRequest.GroupByItem> groupBy = new ArrayList<>();
        groupBy.add(new SemanticQueryRequest.GroupByItem(axisField.getField(), null));
        for (String metric : metrics) {
            groupBy.add(new SemanticQueryRequest.GroupByItem(metric, resolveMetricAggregation(metric, queryModel)));
        }
        domainRequest.setGroupBy(groupBy);
        domainRequest.setSlice(mergeSlices(originalRequest.getSlice(), axisField.getDomainSlice()));
        domainRequest.setHaving(toHavingSlices(axisField.getHaving()));
        domainRequest.setOrderBy(buildAxisDomainOrderBy(axisField));
        domainRequest.setCalculatedFields(originalRequest.getCalculatedFields());
        if (applyWindow && axisField.getLimit() != null) {
            domainRequest.setStart(axisField.getEffectiveOffset());
            domainRequest.setLimit(axisField.getLimit());
        } else domainRequest.setLimit(CardinalityBreaker.DEFAULT_ROW_LIMIT + 1);
        domainRequest.setReturnTotal(false);

        return domainRequest;
    }

    private BaselineRatioPrePageAxisDomainResult executeBaselineRatioPrePageAxisDomain(
            String model, SemanticQueryRequest originalRequest,
            SemanticRequestContext context,
            List<String> rowFields, List<String> colFields,
            List<String> metrics, PivotRequest pivot, QueryModel queryModel,
            Set<Object> rowDomain, Set<Object> visibleColumnDomain, Set<Object> prePageColumnDomain,
            PivotDiagnosticCollector diagnostics) {

        if (!requiresPrePageAxisDomainBaseline(pivot) ||
                prePageColumnDomain == null || prePageColumnDomain.isEmpty() ||
                colFields == null || colFields.size() != 1) {
            return BaselineRatioPrePageAxisDomainResult.empty();
        }

        List<Object> orderedColumnDomain = new ArrayList<>(prePageColumnDomain);
        orderedColumnDomain.removeIf(Objects::isNull);
        orderedColumnDomain.sort(this::compareAxisDomainValues);
        if (orderedColumnDomain.isEmpty()) return BaselineRatioPrePageAxisDomainResult.empty();
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
                model, originalRequest, pivot, rowDomain, baselineColumnDomain, context, diagnostics);
        List<Map<String, Object>> baselineRows = executePhase1(model, baselineRequest.request(),
                baselineRequest.context(), rowFields, colFields, metrics, queryModel);

        if (baselineRows.isEmpty()) {
            Map<String, Map<String, Number>> missing = new LinkedHashMap<>();
            for (var br : pivot.getBaselineRatioMetrics())
                missing.put(br.getName(), Collections.emptyMap());
            return new BaselineRatioPrePageAxisDomainResult(missing, List.of());
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
        evidence.put(
                "visibleAxisDomainSize",
                visibleColumnDomain == null ? null : visibleColumnDomain.size());
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

    private Map<String, DbAggregation> metricAggregations(List<String> metrics, QueryModel model) {
        Map<String, DbAggregation> result = new LinkedHashMap<>();
        for (String metric : metrics) {
            DbAggregation agg = MetricAdditivityAnalyzer.resolveAggregation(metric, model);
            if (agg == null && model == null) agg = DbAggregation.SUM;
            if (agg != null) result.put(metric, agg);
        }
        return result;
    }

    private boolean hasMemberControl(AxisField axis) {
        return (axis.getHaving() != null && !axis.getHaving().isEmpty())
                || axis.getLimit() != null
                || (axis.getOrderBy() != null && !axis.getOrderBy().isEmpty());
    }

    private SemanticQueryRequest buildMemberRequest(
            SemanticQueryRequest original,
            List<AxisField> axis,
            int level,
            List<String> metrics,
            QueryModel model) {
        var fields = axis.subList(0, level + 1).stream().map(AxisField::getField).toList();
        AxisField field = axis.get(level);
        Set<String> controls = new LinkedHashSet<>();
        if (field.getHaving() != null) field.getHaving().forEach(f -> controls.add(f.getMetric()));
        if (field.getOrderBy() != null)
            field.getOrderBy().forEach(o -> controls.add(o.startsWith("-") ? o.substring(1) : o));
        return buildPhase1FlatRequest(
                original,
                fields,
                List.of(),
                metrics.stream().filter(controls::contains).toList(),
                model);
    }

    private List<Map<String, Object>> applyMemberControls(
            String model,
            SemanticQueryRequest original,
            SemanticRequestContext context,
            List<String> rows,
            List<String> columns,
            List<String> metrics,
            QueryModel queryModel,
            List<Map<String, Object>> cells,
            boolean window) {
        if (queryModel == null) {
            var filtered = AxisHavingFilter.apply(cells, original.getPivot().getRows(), metrics);
            filtered = AxisHavingFilter.apply(filtered, original.getPivot().getColumns(), metrics);
            if (!window) return filtered;
            filtered =
                    AxisTopNTruncator.apply(
                            filtered,
                            original.getPivot().getRows(),
                            metricAggregations(metrics, null));
            return AxisTopNTruncator.apply(
                    filtered, original.getPivot().getColumns(), metricAggregations(metrics, null));
        }
        List<Map<String, Object>> filtered = new ArrayList<>(cells);
        List<Map<List<Object>, Integer>> outputOrders = new ArrayList<>();
        for (var axis :
                List.of(
                        original.getPivot().getRows(),
                        original.getPivot().getColumns() == null
                                ? List.<AxisField>of()
                                : original.getPivot().getColumns())) {
            if (axis.stream().anyMatch(AxisField::isTreeMode)) {
                outputOrders.add(
                        domainOrder(cells, axis.stream().map(AxisField::getField).toList()));
                continue;
            }
            List<Map<String, Object>> axisCells = new ArrayList<>(cells);
            for (int i = 0; i < axis.size(); i++) {
                AxisField field = axis.get(i);
                if (!hasMemberControl(field)
                        || (!window && (field.getHaving() == null || field.getHaving().isEmpty())))
                    continue;
                var request = buildMemberRequest(original, axis, i, metrics, queryModel);
                var response =
                        pivotRollupExecutionPort.queryModel(model, request, "execute", context);
                var members =
                        QueryFieldBindings.logicalRows(
                                response.getItems(),
                                queryModel,
                                request.getColumns(),
                                request.getCalculatedFields());
                if (members.size() >= request.getLimit())
                    throw new IllegalArgumentException("PIVOT_MEMBER_DOMAIN_LIMIT");
                if (field.getHaving() != null)
                    members =
                            members.stream()
                                    .filter(
                                            r ->
                                                    field.getHaving().stream()
                                                            .allMatch(
                                                                    f ->
                                                                            f.evaluate(
                                                                                    (Number)
                                                                                            r.get(
                                                                                                    f
                                                                                                            .getMetric()))))
                                    .toList();
                List<String> keys =
                        axis.subList(0, i + 1).stream().map(AxisField::getField).toList();
                // Remove children of already eliminated parents before ranking their siblings.
                Set<List<Object>> visible = new HashSet<>();
                for (var row : axisCells) visible.add(PivotCoordinates.tuple(row, keys));
                members =
                        members.stream()
                                .filter(r -> visible.contains(PivotCoordinates.tuple(r, keys)))
                                .toList();
                if (window) {
                    Map<List<Object>, Integer> parentOrder =
                            domainOrder(axisCells, keys.subList(0, keys.size() - 1));
                    members = new ArrayList<>(members);
                    members.sort(
                            Comparator.comparingInt(
                                    r ->
                                            parentOrder.get(
                                                    PivotCoordinates.tuple(
                                                            r, keys.subList(0, keys.size() - 1)))));
                    List<AxisField> rankingAxis = new ArrayList<>();
                    for (int j = 0; j < i; j++) {
                        var parent = new AxisField();
                        parent.setField(axis.get(j).getField());
                        rankingAxis.add(parent);
                    }
                    var rankingField = new AxisField();
                    rankingField.setField(field.getField());
                    rankingField.setLimit(field.getLimit());
                    rankingField.setStart(field.getStart());
                    rankingField.setOffset(field.getOffset());
                    List<String> orders =
                            new ArrayList<>(
                                    field.getOrderBy() == null ? List.of() : field.getOrderBy());
                    if (orders.stream()
                            .noneMatch(
                                    o ->
                                            o.equals(field.getField())
                                                    || o.equals("-" + field.getField())))
                        orders.add(field.getField());
                    rankingField.setOrderBy(orders);
                    rankingAxis.add(rankingField);
                    members = AxisTopNTruncator.apply(members, rankingAxis);
                }
                Set<List<Object>> selected = new HashSet<>();
                for (var member : members) selected.add(PivotCoordinates.tuple(member, keys));
                Map<List<Object>, Integer> selectedOrder = domainOrder(members, keys);
                axisCells =
                        axisCells.stream()
                                .filter(r -> selected.contains(PivotCoordinates.tuple(r, keys)))
                                .sorted(
                                        Comparator.comparingInt(
                                                r ->
                                                        selectedOrder.get(
                                                                PivotCoordinates.tuple(r, keys))))
                                .toList();
            }
            List<String> axisKeys = axis.stream().map(AxisField::getField).toList();
            Set<List<Object>> axisDomain = new HashSet<>();
            for (var row : axisCells) axisDomain.add(PivotCoordinates.tuple(row, axisKeys));
            outputOrders.add(domainOrder(axisCells, axisKeys));
            filtered =
                    filtered.stream()
                            .filter(r -> axisDomain.contains(PivotCoordinates.tuple(r, axisKeys)))
                            .toList();
        }
        List<Map<String, Object>> ordered = new ArrayList<>(filtered);
        List<String> displayRows = extractFieldNames(original.getPivot().getRows());
        List<String> displayColumns = extractFieldNames(original.getPivot().getColumns());
        ordered.sort(
                Comparator.comparingInt(
                                (Map<String, Object> row) ->
                                        outputOrders
                                                .get(0)
                                                .get(PivotCoordinates.tuple(row, displayRows)))
                        .thenComparingInt(
                                row ->
                                        outputOrders
                                                .get(1)
                                                .get(PivotCoordinates.tuple(row, displayColumns))));
        return ordered;
    }

    private Map<List<Object>, Integer> domainOrder(
            List<Map<String, Object>> members, List<String> fields) {
        Map<List<Object>, Integer> result = new LinkedHashMap<>();
        for (var row : members)
            result.putIfAbsent(PivotCoordinates.tuple(row, fields), result.size());
        return result;
    }

    private String buildPivotKey(Map<String, Object> row, List<String> fields) {
        return PivotCoordinates.key(row, fields);
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
            tieBreaker.setNullLast(true);
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
        item.setNullLast(true);
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
        List<String> orders =
                new ArrayList<>(
                        axisField.getOrderBy() == null ? List.of() : axisField.getOrderBy());
        if (orders.stream()
                .noneMatch(
                        o ->
                                o.equals(axisField.getField())
                                        || o.equals("-" + axisField.getField())))
            orders.add(axisField.getField());
        return PivotOrdering.comparator(orders);
    }

    private List<Map<String, Object>> applyAxisDomainWindow(List<Map<String, Object>> rows, AxisField axisField) {
        int start = axisField.getEffectiveOffset();
        if (start >= rows.size()) {
            return Collections.emptyList();
        }
        int end = rows.size();
        if (axisField.getLimit() != null) {
            end = (int) Math.min((long) start + axisField.getLimit(), rows.size());
        }
        return new ArrayList<>(rows.subList(start, end));
    }

    private int compareAxisDomainValues(Object a, Object b) {
        return PivotOrdering.compare(a, b, false);
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
        if (java.util.stream.Stream.concat(
                        pivot.getRows().stream(),
                        pivot.getColumns() == null
                                ? java.util.stream.Stream.empty()
                                : pivot.getColumns().stream())
                .anyMatch(
                        axis ->
                                axis.getOrderBy() != null
                                        && !axis.getOrderBy().isEmpty()
                                        && axis.getLimit() == null))
            return "orderingOnlyRequiresMemberQuery";
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

        PlannedPhase1 phase = planPhase1WithSqlPushdown(model, originalRequest, context, rowFields, colFields, metrics, pivot, queryModel);
        ManagedSqlRelation baseRelation = phase.relation();
        PivotAxisDomainSqlPlanner.PlannedSql planned = phase.sql();
        // 5. 执行最终 SQL
        com.foggyframework.dataset.model.engine.query.DbQueryResult dbResult =
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

    private PlannedPhase1 planPhase1WithSqlPushdown(String model, SemanticQueryRequest originalRequest,
                                                   SemanticRequestContext context, List<String> rowFields,
                                                   List<String> colFields, List<String> metrics,
                                                   PivotRequest pivot, QueryModel queryModel) {
        // 1. 构建与 executePhase1 相同的扁平请求
        SemanticQueryRequest flatRequest = buildPhase1FlatRequest(originalRequest, rowFields, colFields, metrics, queryModel);

        // 2. 构建 ModelResultContext（复用 SemanticQueryServiceV3 的模式）
        com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext resultContext =
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

        String logicalSql = QueryFieldBindings.logicalSql(baseRelation.getSql(), baseRelation.getDialect(), queryModel,
                flatRequest.getColumns(), flatRequest.getCalculatedFields());
        var logicalMetrics = new ArrayList<com.foggyframework.dataset.model.plugins.query_execution.ManagedMetricMetadata>();
        for (var metadata : baseRelation.getMetrics()) {
            String logicalName = metadata.getMetricName();
            for (String metric : metrics) if (metadata.getMetricName().equals(QueryFieldBindings.outputName(queryModel, metric, flatRequest.getCalculatedFields()))) logicalName = metric;
            logicalMetrics.add(com.foggyframework.dataset.model.plugins.query_execution.ManagedMetricMetadata.builder()
                    .metricName(logicalName).additiveKind(metadata.getAdditiveKind()).aggregationFunction(metadata.getAggregationFunction()).build());
        }
        baseRelation = new ManagedSqlRelation(logicalSql, baseRelation.getParams(), baseRelation.getDialect(),
                baseRelation.getQueryEngine(), baseRelation.getExecutionContext(), baseRelation.isWrappable(),
                baseRelation.isPermissionValidated(), baseRelation.isPreAggApplied(), logicalMetrics);
        // 4. 用 Planner 生成包装 SQL（having + TopN CTE）
        PivotAxisDomainSqlPlanner.PlannedSql planned = PivotAxisDomainSqlPlanner.plan(
                baseRelation, pivot, rowFields, colFields, metrics);

        return new PlannedPhase1(baseRelation, planned);
    }

    private record PlannedPhase1(ManagedSqlRelation relation, PivotAxisDomainSqlPlanner.PlannedSql sql) { }

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
    private com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext buildManagedRelationContext(
            String model, SemanticQueryRequest flatRequest, SemanticRequestContext reqContext) {
        // 构建 JDBC 请求
        com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef queryDef =
                new com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef();
        queryDef.setQueryModel(model);
        queryDef.setReturnTotal(false);
        queryDef.setStrictColumns(true);
        queryDef.setColumns(flatRequest.getColumns());

        // groupBy
        if (flatRequest.getGroupBy() != null) {
            List<com.foggyframework.dataset.model.def.query.request.GroupRequestDef> jdbcGroupBy = new ArrayList<>();
            for (SemanticQueryRequest.GroupByItem item : flatRequest.getGroupBy()) {
                com.foggyframework.dataset.model.def.query.request.GroupRequestDef g =
                        new com.foggyframework.dataset.model.def.query.request.GroupRequestDef();
                g.setField(item.getField());
                g.setAgg(item.getAgg());
                jdbcGroupBy.add(g);
            }
            queryDef.setGroupBy(jdbcGroupBy);
        }

        // slice
        if (flatRequest.getSlice() != null) {
            List<com.foggyframework.dataset.model.def.query.request.SliceRequestDef> jdbcSlice = new ArrayList<>();
            for (SemanticQueryRequest.SliceItem sliceItem : flatRequest.getSlice())
                jdbcSlice.add(toJdbcSlice(sliceItem));
            queryDef.setSlice(jdbcSlice);
        }

        // calculatedFields
        if (flatRequest.getCalculatedFields() != null) {
            queryDef.setCalculatedFields(new ArrayList<>(flatRequest.getCalculatedFields()));
        }

        com.foggyframework.dataset.client.domain.PagingRequest<com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef> pagingRequest =
                new com.foggyframework.dataset.client.domain.PagingRequest<>();
        pagingRequest.setParam(queryDef);
        pagingRequest.setStart(0);
        pagingRequest.setPageSize(flatRequest.getLimit());

        com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext resultContext =
                new com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext();
        resultContext.setRequest(pagingRequest);
        resultContext.setCacheConfig(ModelResultContext.QueryCacheConfig.disabled());
        resultContext.setQueryType(com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext.QueryType.SEMANTIC);
        resultContext.setNamespace(reqContext.getNamespace());
        resultContext.setSecurityContext(copySecurityContext(reqContext.getSecurityContext()));
        resultContext.setRequestIdentity(reqContext.getRequestIdentity());
        resultContext.setPermissionSession(reqContext.getPermissionSession());
        resultContext.setPermissionAction(
                reqContext.getPermissionAction() != null
                        ? reqContext.getPermissionAction()
                        : PermissionAction.EXECUTE);
        resultContext.setFieldAccess(reqContext.getFieldAccess());
        resultContext.setDeniedColumns(reqContext.getDeniedColumns());
        resultContext.setSystemSlice(reqContext.getSystemSlice());
        if (reqContext.getCatalogResolution() != null) {
            resultContext.pinCatalogResolution(
                    reqContext.getCatalogResolution(), reqContext.getNamespace());
        }

        return resultContext;
    }

    private SliceRequestDef toJdbcSlice(SemanticQueryRequest.SliceItem item) {
        SliceRequestDef result =
                new SliceRequestDef(item.getField(), item.getOp(), item.getValue());
        result.setMaxDepth(item.getMaxDepth());
        result.setExpr(item.getExpr());
        if (item.getOr() != null)
            result.setOr(
                    item.getOr().stream()
                            .map(
                                    v ->
                                            (com.foggyframework.dataset.model.def.query.request
                                                            .CondRequestDef)
                                                    toJdbcSlice(v))
                            .toList());
        if (item.getAnd() != null)
            result.setAnd(
                    item.getAnd().stream()
                            .map(
                                    v ->
                                            (com.foggyframework.dataset.model.def.query.request
                                                            .CondRequestDef)
                                                    toJdbcSlice(v))
                            .toList());
        return result;
    }

    private boolean hasZeroWindow(PivotRequest pivot) {
        return java.util.stream.Stream.concat(
                        pivot.getRows().stream(),
                        pivot.getColumns() == null
                                ? java.util.stream.Stream.empty()
                                : pivot.getColumns().stream())
                .anyMatch(axis -> Integer.valueOf(0).equals(axis.getLimit()));
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
            List<PropertyResolver.ResolvedProperty> resolvedProps, QueryModel queryModel) {

        Map<String, List<PropertyResolver.ResolvedProperty>> byDim = groupProperties(resolvedProps);

        Map<String, Map<Object, Map<String, Object>>> result = new LinkedHashMap<>();

        for (Map.Entry<String, List<PropertyResolver.ResolvedProperty>> entry : byDim.entrySet()) {
            String dimName = entry.getKey();
            List<PropertyResolver.ResolvedProperty> props = entry.getValue();
            String keyField = props.get(0).getLookupKeyField();

            SemanticQueryRequest lookupRequest = buildPropertyLookupRequest(originalRequest, props);
            List<String> selectFields = lookupRequest.getColumns();
            List<String> propFieldNames = props.stream().map(PropertyResolver.ResolvedProperty::getFullFieldName).toList();

            logger.debug("[Pivot] Property lookup: dim={}, fields={}", dimName, selectFields);

            SemanticQueryResponse response = pivotRollupExecutionPort.queryModel(
                    model, lookupRequest, "execute", context);

            List<Map<String, Object>> lookupRows = QueryFieldBindings.logicalRows(response.getItems(), queryModel, selectFields, lookupRequest.getCalculatedFields());
            for (var row : lookupRows)
                for (var property : props)
                    row.put(
                            property.getFullFieldName(),
                            row.get(property.getLookupPropertyField()));
            if (lookupRows.size() >= lookupRequest.getLimit())
                throw new IllegalArgumentException("PIVOT_PROPERTY_DOMAIN_LIMIT");

            Map<Object, Map<String, Object>> lookupTable =
                    PropertyAttacher.buildLookupTable(lookupRows, keyField, propFieldNames);

            result.put(dimName, lookupTable);
        }

        return result;
    }

    private Map<String, List<PropertyResolver.ResolvedProperty>> groupProperties(List<PropertyResolver.ResolvedProperty> properties) {
        return properties.stream().collect(Collectors.groupingBy(PropertyResolver.ResolvedProperty::getDimensionName));
    }

    private SemanticQueryRequest buildPropertyLookupRequest(SemanticQueryRequest originalRequest,
                                                            List<PropertyResolver.ResolvedProperty> properties) {
        List<String> fields = new ArrayList<>();
        fields.add(properties.get(0).getLookupKeyField());
        for (var property : properties) fields.add(property.getLookupPropertyField());
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(fields);
        request.setDistinct(true);
        request.setSlice(originalRequest.getSlice());
        request.setCalculatedFields(originalRequest.getCalculatedFields());
        request.setLimit(CardinalityBreaker.DEFAULT_ROW_LIMIT);
        request.setReturnTotal(false);
        return request;
    }

    private SemanticQueryRequest buildHierarchySkeletonRequest(String dimension, String idField) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of(idField, dimension));
        request.setDistinct(true);
        // No user slice: keep the existing complete skeleton contract; governed systemSlice still applies.
        request.setLimit(CardinalityBreaker.DEFAULT_ROW_LIMIT);
        request.setReturnTotal(false);
        return request;
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
            String dimName, String idField, QueryModel queryModel) {

        String parentField = dimName;

        SemanticQueryRequest skeletonRequest = buildHierarchySkeletonRequest(dimName, idField);

        logger.debug("[Pivot] Hierarchy skeleton: SELECT DISTINCT {}, {} FROM {}",
                idField, parentField, model);

        SemanticQueryResponse response = pivotRollupExecutionPort.queryModel(
                model, skeletonRequest, "execute", context);

        List<Map<String, Object>> rows = QueryFieldBindings.logicalRows(response.getItems(), queryModel,
                skeletonRequest.getColumns(), skeletonRequest.getCalculatedFields());

        // 骨架截断警告：如果触达 limit 上限，可能丢失部分层级关系
        if (rows.size() >= CardinalityBreaker.DEFAULT_ROW_LIMIT) {
            throw new IllegalArgumentException(
                    "PIVOT_TREE_SKELETON_LIMIT: " + CardinalityBreaker.DEFAULT_ROW_LIMIT);
        }

        logger.debug("[Pivot] Hierarchy skeleton loaded: {} nodes", rows.size());
        return HierarchyTreeBuilder.Skeleton.fromRows(rows, idField, parentField);
    }


    /**
     * 前置校验
     */
    private void validatePivotRequest(SemanticQueryRequest request) {
        PivotRequest pivot = request.getPivot();
        validateAxisShape(pivot.getRows(), "rows");
        validateAxisShape(pivot.getColumns(), "columns");
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
                    "pivot 模式不支持顶层 orderBy。请使用 pivot.rows[*].orderBy 或 pivot.columns[*].orderBy"
                            + " 作为透视轴排序控制");
        }
        if (request.getLimit() != null) {
            throw new IllegalArgumentException(
                    "pivot 模式不支持顶层 limit。请使用 pivot.rows[*].limit 或 pivot.columns[*].limit 作为透视轴"
                            + " TopN 控制");
        }
        if (request.getPostAggregateCalculations() != null && !request.getPostAggregateCalculations().isEmpty()) {
            throw new IllegalArgumentException(
                    "pivot 模式不支持顶层 postAggregateCalculations。请使用 pivot.metrics 的结构化派生指标，或拆分为普通"
                            + " query_model result-stage 请求");
        }
        if (request.getPostSlice() != null && !request.getPostSlice().isEmpty()) {
            throw new IllegalArgumentException(
                    "pivot 模式不支持顶层 postSlice。Pivot 轴过滤请使用 pivot.rows[*].having /"
                            + " pivot.columns[*].having；结果阶段过滤请拆分请求");
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
                    "hierarchyMode=tree 当前仅支持 rows 轴。请将 '"
                            + colHierarchy.getTreeAxisField().getField()
                            + "' 从 columns 移到 rows，或移除 hierarchyMode");
        }

        if (rowHierarchy.isTree()) {
            if (!pivot.getBaselineRatioMetrics().isEmpty())
                throw new IllegalArgumentException(
                        "当前版本不支持 hierarchyMode=tree 与 baselineRatio 派生指标同时使用");
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

    private void validateAxisShape(List<AxisField> fields, String axis) {
        if (fields == null) return;
        for (int i = 0; i < fields.size(); i++) {
            AxisField field = fields.get(i);
            if (field == null || field.getField() == null || field.getField().isBlank()) {
                throw pivotReferenceFailure(
                        null,
                        "pivot." + axis + "[" + i + "].field",
                        "Axis field must not be empty");
            }
        }
    }

    private void validateAxisControlReferences(PivotRequest pivot, List<String> rows,
                                               List<String> columns, List<String> metrics) {
        Set<String> sortable = new LinkedHashSet<>(rows);
        sortable.addAll(columns);
        sortable.addAll(metrics);
        validateAxisControlReferences(pivot.getRows(), "rows", sortable, metrics);
        validateAxisControlReferences(pivot.getColumns(), "columns", sortable, metrics);
    }

    private void validateAxisControlReferences(List<AxisField> fields, String axis,
                                               Set<String> sortable, List<String> metrics) {
        if (fields == null) return;
        for (int i = 0; i < fields.size(); i++) {
            AxisField field = fields.get(i);
            String path = "pivot." + axis + "[" + i + "]";
            if (field.getOrderBy() != null) {
                for (int j = 0; j < field.getOrderBy().size(); j++) {
                    String order = field.getOrderBy().get(j);
                    String name =
                            order != null && order.startsWith("-") ? order.substring(1) : order;
                    boolean atGrain =
                            fields.subList(0, i + 1).stream()
                                    .anyMatch(f -> Objects.equals(f.getField(), name));
                    if (!sortable.contains(name) || (!metrics.contains(name) && !atGrain)) {
                        throw pivotReferenceFailure(
                                name,
                                path + ".orderBy[" + j + "]",
                                "Axis ordering must reference a coordinate at its member grain or a"
                                        + " selected SQL metric");
                    }
                }
            }
            if (field.getHaving() != null) {
                for (int j = 0; j < field.getHaving().size(); j++) {
                    MetricFilter filter = field.getHaving().get(j);
                    if (filter == null || !metrics.contains(filter.getMetric())) {
                        throw pivotReferenceFailure(
                                filter == null ? null : filter.getMetric(),
                                path + ".having[" + j + "].metric",
                                "Axis having must reference a selected SQL metric");
                    }
                    if (filter.getOp() == null
                            || !Set.of(">", ">=", "<", "<=", "=", "!=").contains(filter.getOp())
                            || filter.getValue() == null) {
                        throw pivotReferenceFailure(filter.getMetric(), path + ".having[" + j + "]",
                                "Axis having requires a supported comparison and numeric value");
                    }
                }
            }
        }
    }

    private com.foggyframework.dataset.model.engine.expression.QueryCalculationException pivotReferenceFailure(
            String field, String path, String message) {
        return new com.foggyframework.dataset.model.engine.expression.QueryCalculationException(
                "PIVOT_INVALID_REFERENCE",
                field,
                path,
                message,
                "Use the logical field names in pivot axes and metrics; output aliases are not"
                        + " input synonyms.",
                Map.of("path", path));
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
                    "domainSlice/start/offset 当前仅支持单层 rows 和单层 columns。多层轴分页需要显式 domain tree/cursor"
                            + " 语义");
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
                        "domainSlice/start/offset 与 parentShare 组合时必须显式指定"
                                + " denominatorScope=prePageParent");
            }
            if (!isSupportedParentSharePrePageParentWindow(pivot)) {
                throw new IllegalArgumentException(
                        "denominatorScope=prePageParent 当前仅支持多层 rows 子级 start/offset/limit 窗口，" +
                        "暂不支持 domainSlice 或 columns 轴窗口");
            }
        }
        if (!pivot.getBaselineRatioMetrics().isEmpty() && !allBaselineRatioMetricsUsePrePageAxisDomain(pivot)) {
            throw new IllegalArgumentException(
                    "domainSlice/start/offset 与 baselineRatio 组合时必须显式指定"
                            + " baselineScope=prePageAxisDomain");
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
        validateDerivedMetricsNotUsedAsAxisControls(
                pivot.getColumns(), "columns", derivedMetricNames);
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
            if ((field.getStart() != null || field.getOffset() != null)
                    && (field.getLimit() == null)) {
                throw new IllegalArgumentException(
                        axisName + "." + fieldName + " 使用 start/offset 时必须同时指定非负 limit");
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
        contract.put(
                "tree_axis_contract",
                buildTreeAxisContract(hierarchyCtx, hierarchySkeleton, outputFormat));
        contract.put(
                "drilldown_contract",
                buildDrilldownContract(pivot, axisDomainSelectionUsed, cascadeRequest));
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
            axis.put(
                    "has_domain_slice",
                    field.getDomainSlice() != null && !field.getDomainSlice().isEmpty());
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

    private SemanticQueryResponse cachedResponse(SemanticQueryResponse cached,
                                                 long startTime,
                                                 List<Map<String, Object>> pivotDiagnostics) {
        SemanticQueryResponse response = PivotOuterResponseCache.copyResponse(cached);
        if (response.getDebug() == null) {
            response.setDebug(new SemanticQueryResponse.DebugInfo());
        }
        response.getDebug().setDurationMs(System.currentTimeMillis() - startTime);
        replacePivotDiagnostics(response, pivotDiagnostics);
        return response;
    }

    private Optional<PivotOuterCacheSafeProvider.UnavailableEvent> consumeOuterCacheProviderUnavailable() {
        if (outerResponseCache instanceof PivotOuterCacheSafeProvider safeProvider) {
            return safeProvider.consumeLastUnavailable();
        }
        return Optional.empty();
    }

    private void recordOuterCacheProviderUnavailable(PivotDiagnosticCollector diagnostics,
                                                     PivotOuterCacheTelemetry.Evaluation cacheEvaluation,
                                                     String cacheEligibilityStage,
                                                     Optional<PivotOuterCacheSafeProvider.UnavailableEvent> unavailable) {
        if (diagnostics == null || cacheEvaluation == null || unavailable == null || unavailable.isEmpty()) {
            return;
        }
        diagnostics.cacheProviderUnavailable(cacheEvaluation.keyHash(), cacheEligibilityStage,
                unavailable.get(), cacheEvaluation.shapeClass());
    }

    SemanticQueryResponse storeOuterCacheIfEligible(PivotOuterCacheTelemetry.Evaluation cacheEvaluation,
                                                    String cacheEligibilityStage,
                                                    PivotDiagnosticCollector diagnostics,
                                                    SemanticQueryResponse response) {
        return storeOuterCacheIfEligible(cacheEvaluation, cacheEligibilityStage, diagnostics, response, null, null);
    }

    SemanticQueryResponse storeOuterCacheIfEligible(PivotOuterCacheTelemetry.Evaluation cacheEvaluation,
                                                    String cacheEligibilityStage,
                                                    PivotDiagnosticCollector diagnostics,
                                                    SemanticQueryResponse response,
                                                    String model,
                                                    SemanticRequestContext context) {
        boolean outerCacheEnabled = outerResponseCache.isEnabled();
        Optional<PivotOuterCacheSafeProvider.UnavailableEvent> enabledUnavailable =
                consumeOuterCacheProviderUnavailable();
        recordOuterCacheProviderUnavailable(diagnostics, cacheEvaluation, cacheEligibilityStage, enabledUnavailable);
        if (!outerCacheEnabled
                || cacheEvaluation == null
                || cacheEvaluation.refused()) {
            if (cacheEvaluation != null && enabledUnavailable.isPresent()) {
                diagnostics.cacheStoreSkipped(cacheEvaluation.keyHash(), cacheEligibilityStage,
                        PivotOuterCacheTelemetry.CACHE_PROVIDER_UNAVAILABLE_REASON, cacheEvaluation.shapeClass());
                replacePivotDiagnostics(response, diagnostics.snapshot());
            }
            return response;
        }
        if (hasWarnings(response)) {
            diagnostics.cacheStoreSkipped(cacheEvaluation.keyHash(), cacheEligibilityStage,
                    PivotOuterCacheTelemetry.CACHE_STORE_SKIPPED_WARNING_REASON, cacheEvaluation.shapeClass());
            replacePivotDiagnostics(response, diagnostics.snapshot());
            return response;
        }
        int payloadBytes = outerResponseCache.estimatePayloadBytes(response);
        recordOuterCacheProviderUnavailable(diagnostics, cacheEvaluation, cacheEligibilityStage,
                consumeOuterCacheProviderUnavailable());
        diagnostics.cacheStore(cacheEvaluation.keyHash(), cacheEligibilityStage, payloadBytes,
                outerResponseCache.ttlMillis(), cacheEvaluation.shapeClass());
        recordOuterCacheProviderUnavailable(diagnostics, cacheEvaluation, cacheEligibilityStage,
                consumeOuterCacheProviderUnavailable());
        replacePivotDiagnostics(response, diagnostics.snapshot());
        outerResponseCache.store(cacheEvaluation.keyHash(), response, System.currentTimeMillis(),
                context != null ? context.getNamespace() : null, model);
        Optional<PivotOuterCacheSafeProvider.UnavailableEvent> storeUnavailable =
                consumeOuterCacheProviderUnavailable();
        recordOuterCacheProviderUnavailable(diagnostics, cacheEvaluation, cacheEligibilityStage, storeUnavailable);
        if (storeUnavailable.isPresent()) {
            diagnostics.cacheStoreSkipped(cacheEvaluation.keyHash(), cacheEligibilityStage,
                    PivotOuterCacheTelemetry.CACHE_PROVIDER_UNAVAILABLE_REASON, cacheEvaluation.shapeClass());
            replacePivotDiagnostics(response, diagnostics.snapshot());
        }
        return response;
    }

    public int evictOuterCache(String namespace, String model) {
        return outerResponseCache.evict(namespace, model);
    }

    private boolean hasWarnings(SemanticQueryResponse response) {
        return response != null && response.getWarnings() != null && !response.getWarnings().isEmpty();
    }

    private void replacePivotDiagnostics(SemanticQueryResponse response, List<Map<String, Object>> pivotDiagnostics) {
        if (response == null) {
            return;
        }
        if (response.getDebug() == null) {
            response.setDebug(new SemanticQueryResponse.DebugInfo());
        }
        Map<String, Object> extra = response.getDebug().getExtra();
        if (extra == null) {
            extra = new LinkedHashMap<>();
            response.getDebug().setExtra(extra);
        }
        if (pivotDiagnostics != null && !pivotDiagnostics.isEmpty()) {
            extra.put("pivotDiagnostics", pivotDiagnostics);
        }
    }

    /**
     * 构建空结果响应
     */
    private SemanticQueryResponse buildEmptyResponse(PivotRequest pivot, long startTime,
                                                     Map<String, Object> capabilityContract,
                                                     List<Map<String, Object>> pivotDiagnostics) {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(Collections.emptyList());
        response.setTotal(0L);

        SemanticQueryResponse.DebugInfo debugInfo = new SemanticQueryResponse.DebugInfo();
        debugInfo.setDurationMs(System.currentTimeMillis() - startTime);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("pipeline", "pivot");
        extra.put("format", pivot.getOutputFormat());
        extra.put("pivotEngineContract", capabilityContract);
        if (pivotDiagnostics != null && !pivotDiagnostics.isEmpty()) {
            extra.put("pivotDiagnostics", pivotDiagnostics);
        }
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
            Map<String, Object> capabilityContract,
            List<Map<String, Object>> pivotDiagnostics) {
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
        if (pivotDiagnostics != null && !pivotDiagnostics.isEmpty()) {
            extra.put("pivotDiagnostics", pivotDiagnostics);
        }
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
