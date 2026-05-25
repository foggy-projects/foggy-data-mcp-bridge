package com.foggyframework.dataset.db.model.semantic.service.impl;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.JsonUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.compose.schema.AliasExtractor;
import com.foggyframework.dataset.db.model.engine.compose.schema.ColumnAliasParts;
import com.foggyframework.dataset.db.model.engine.expression.InlineExpressionParser;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportPlan;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.memorygrid.GridSqlContractValidator;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridDialectDescriptor;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridEngine;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridExecutionResult;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridRequest;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridValidation;
import com.foggyframework.dataset.db.model.semantic.service.DimensionMemberLoader;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.support.DslCteDslRequestMapper;
import com.foggyframework.dataset.db.model.semantic.support.DslCtePlanValidator;
import com.foggyframework.dataset.db.model.semantic.support.SemanticSqlDslRequestMapper;
import com.foggyframework.dataset.db.model.semantic.support.SemanticSqlToDslMapper;
import com.foggyframework.dataset.db.model.semantic.support.SemanticSqlWhitelistValidator;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.DbQueryCondition;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.semantic.util.CaseInsensitiveFieldResolver;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.utils.DataSourceQueryUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

import java.util.*;
import java.util.stream.Collectors;

/**
 * V3版本语义查询服务实现
 *
 * <p>核心简化：字段名直接使用，无需判断和拼接后缀</p>
 *
 * <p>与V2的区别：</p>
 * <ul>
 *   <li>不再需要将 $caption 归一化为 $id（因为 $caption 已经是独立字段）</li>
 *   <li>不再需要自动补全 $id/$caption 后缀</li>
 *   <li>所有字段直接透传给底层服务</li>
 * </ul>
 *
 * <p>V3 仍然保留的功能：</p>
 * <ul>
 *   <li>slice 中 $caption 字段的值转换（caption值 -> id值）</li>
 *   <li>columns 和 groupBy 的对齐校验</li>
 * </ul>
 */
@Service
public class SemanticQueryServiceV3Impl implements SemanticQueryServiceV3 {

    private static final Logger logger = LoggerFactory.getLogger(SemanticQueryServiceV3Impl.class);

    @Resource
    private QueryFacade queryFacade;

    @Resource
    private QueryModelLoader queryModelLoader;

    @Resource
    private DimensionMemberLoader dimensionMemberLoader;

    private MemoryGridEngine memoryGridEngine;

    @Resource
    private DataSource dataSource;

    @Autowired(required = false)
    public void setMemoryGridEngine(MemoryGridEngine memoryGridEngine) {
        this.memoryGridEngine = memoryGridEngine;
    }

    /** Pivot 流水线（延迟初始化，避免循环依赖） */
    private volatile com.foggyframework.dataset.db.model.engine.pivot.PivotPipeline pivotPipeline;

    private com.foggyframework.dataset.db.model.engine.pivot.PivotPipeline getPivotPipeline() {
        if (pivotPipeline == null) {
            synchronized (this) {
                if (pivotPipeline == null) {
                    pivotPipeline = new com.foggyframework.dataset.db.model.engine.pivot.PivotPipeline(
                            this, new com.foggyframework.dataset.db.model.engine.pivot.CardinalityBreaker(),
                            queryModelLoader, queryFacade);
                }
            }
        }
        return pivotPipeline;
    }

    @Override
    public SemanticQueryResponse queryModel(String model, SemanticQueryRequest request, String mode,
                                            SemanticRequestContext context) {
        SemanticQueryResponse terminal = terminalResponseIfAny(request);
        if (terminal != null) {
            return terminal;
        }
        SemanticQueryResponse semanticSqlPlan = semanticSqlPlanResponseIfAny(model, request, context);
        if (semanticSqlPlan != null) {
            return semanticSqlPlan;
        }
        SemanticQueryResponse memoryGridExecution = memoryGridExecutionResponseIfAny(request, context);
        if (memoryGridExecution != null) {
            return memoryGridExecution;
        }
        SemanticQueryResponse memoryGridPlan = memoryGridPlanResponseIfAny(request, context);
        if (memoryGridPlan != null) {
            return memoryGridPlan;
        }
        SemanticQueryResponse dslCtePlan = dslCtePlanResponseIfAny(request);
        if (dslCtePlan != null) {
            return dslCtePlan;
        }
        return queryModelInternal(model, request, mode, context);
    }

    /**
     * 执行查询（内部方法，直接接收 SemanticRequestContext 避免参数膨胀）
     */
    private SemanticQueryResponse queryModelInternal(String model, SemanticQueryRequest request, String mode,
                                                      SemanticRequestContext reqContext) {
        ModelResultContext.SecurityContext securityContext = reqContext.getSecurityContext();
        String namespace = reqContext.getNamespace();
        Set<String> fieldAccess = reqContext.getFieldAccess();
        if ("validate".equals(mode)) {
            return validateQueryInternal(model, request, namespace);
        }

        // === 9.0.0 Pivot Pipeline 路由 ===
        if (request.isPivotMode()) {
            return getPivotPipeline().execute(model, request, reqContext);
        }

        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            throw RX.throwB("请指定查询字段");
        }

        long startTime = System.currentTimeMillis();

        // 1. 创建上下文
        QueryContextV3 context = new QueryContextV3();
        context.model = model;
        context.originalRequest = request;

        // 2. 构建初始JDBC请求
        PagingRequest<DbQueryRequestDef> jdbcRequest = buildJdbcRequest(model, request, context, namespace);

        // 3. 处理 slice 中的 $caption 值转换（如果需要）
        // 注意：这里在 beforeQuery 之前处理，因为需要先转换好 slice
        if (request.getSlice() != null) {
            List<SliceRequestDef> processedSlice = processSliceValues(model, request.getSlice(), request, context);
            jdbcRequest.getParam().setSlice(processedSlice);
        }
        if (request.getHaving() != null) {
            List<SliceRequestDef> processedHaving = processSliceValues(model, request.getHaving(), request, context);
            jdbcRequest.getParam().setHaving(processedHaving);
        }

        // 4. 创建ModelResultContext，标记为语义查询，设置SecurityContext、Namespace和列权限
        ModelResultContext resultContext = new ModelResultContext();
        resultContext.setRequest(jdbcRequest);
        resultContext.setQueryType(ModelResultContext.QueryType.SEMANTIC);
        resultContext.setSecurityContext(securityContext);
        resultContext.setNamespace(namespace);
        resultContext.setFieldAccess(fieldAccess);
        resultContext.setDeniedColumns(reqContext.getDeniedColumns());
        resultContext.setSystemSlice(reqContext.getSystemSlice());

        // 将请求中的 hints 和 timeWindow 传递到 extData
        Map<String, Object> extData = new HashMap<>();
        if (request.getHints() != null && !request.getHints().isEmpty()) {
            extData.putAll(request.getHints());
        }
        if (request.getTimeWindow() != null && !request.getTimeWindow().isEmpty()) {
            extData.put("timeWindow", request.getTimeWindow());
        }
        if (request.getCalculatedFields() != null && !request.getCalculatedFields().isEmpty()) {
            extData.put("calculatedFields", new ArrayList<>(request.getCalculatedFields()));
        }
        putDomainTransportPlans(extData, reqContext);
        if (!extData.isEmpty()) {
            resultContext.setExtData(extData);
        }

        // 5. 使用 QueryFacade 执行完整查询生命周期（beforeQuery -> query -> process）
        DbQueryResult dbQueryResult = queryFacade.queryModelResult(resultContext);
        PagingResultImpl queryResult = resultContext.getPagingResult();
        context.extData = resultContext.getExtData();

        if (resultContext.isSkipQuery() && context.extData != null
                && (context.extData.containsKey("timeWindowPlan") || context.extData.containsKey("comparativePlan"))) {
            com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan compPlan =
                    (com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan) context.extData.getOrDefault(
                            "timeWindowPlan", context.extData.get("comparativePlan"));
            com.foggyframework.dataset.db.model.engine.compose.context.Principal principal = com.foggyframework.dataset.db.model.engine.compose.context.Principal.builder()
                .userId(securityContext != null && securityContext.getUserId() != null ? securityContext.getUserId() : "system")
                .deptId(securityContext != null ? securityContext.getDeptId() : null)
                .tenantId(securityContext != null ? securityContext.getTenantId() : null)
                .build();
            com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext compCtx = com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext.builder()
                .principal(principal)
                .namespace(namespace)
                .authorityResolver(req -> {
                    Map<String, com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding> bindings = new java.util.HashMap<>();
                    for (String m : req.modelNames()) {
                        bindings.put(m, com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding.builder().build());
                    }
                    return com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution.builder().bindings(bindings).build();
                })
                .build();
                
            try {
                // Read dialect resolved by TimeWindowInterceptor
                String dialect = context.extData != null && context.extData.containsKey("timeWindowDialect")
                        ? (String) context.extData.get("timeWindowDialect")
                        : "mysql";
                List<Map<String, Object>> rows = com.foggyframework.dataset.db.model.engine.compose.runtime.PlanExecution.executePlan(compPlan, compCtx, this, dialect);
                
                queryResult = new PagingResultImpl();
                queryResult.setItems(new ArrayList<>(rows));
                queryResult.setTotal((long)rows.size());
                
                dbQueryResult = DbQueryResult.of(queryResult, dbQueryResult != null ? dbQueryResult.getQueryEngine() : null);
            } catch (Exception e) {
                throw com.foggyframework.core.ex.RX.throwB("执行比较分析(Comparative)计划失败: " + e.getMessage(), e);
            }
        }

        // 6. 构建响应
        SemanticQueryResponse response = buildResponse(
                jdbcRequest.getParam(),
                queryResult,
                context,
                dbQueryResult != null && dbQueryResult.getQueryEngine() != null ? dbQueryResult.getQueryEngine().getJdbcQueryModel() : queryModelLoader.getJdbcQueryModel(model, reqContext.getNamespace())
        );

        // 7. 添加调试信息
        addDebugInfo(response, context, startTime, jdbcRequest.getParam(), dbQueryResult);

        return response;
    }

    @Override
    public SemanticQueryResponse validateQuery(String model, SemanticQueryRequest request,
                                               SemanticRequestContext context) {
        SemanticQueryResponse terminal = terminalResponseIfAny(request);
        if (terminal != null) {
            return terminal;
        }
        SemanticQueryResponse semanticSqlPlan = semanticSqlPlanResponseIfAny(model, request, context);
        if (semanticSqlPlan != null) {
            return semanticSqlPlan;
        }
        SemanticQueryResponse memoryGridPlan = memoryGridPlanResponseIfAny(request, context);
        if (memoryGridPlan != null) {
            return memoryGridPlan;
        }
        SemanticQueryResponse dslCtePlan = dslCtePlanResponseIfAny(request);
        if (dslCtePlan != null) {
            return dslCtePlan;
        }
        return validateQueryInternal(model, request, context.getNamespace());
    }

    @Override
    public SqlGenerationResult generateSql(String model, SemanticQueryRequest request,
                                           SemanticRequestContext context) {
        if (isTerminalPlan(request)) {
            throw RX.throwB("TERMINAL_PLAN_NOT_EXECUTABLE: CLARIFY/REJECT terminal plans must not enter SQL generation.");
        }
        if (isSemanticSqlPlan(request)) {
            QueryModel queryModel = queryModelLoader.getJdbcQueryModel(model, context.getNamespace());
            semanticSqlAstValidation(model, request, queryModel, context);
            Map<String, Object> dslPlan = SemanticSqlToDslMapper.map(model, request.getSemanticSql(), queryModel, context);
            if (semanticSqlCompileToDslEnabled(request)) {
                SemanticSqlDslRequestMapper.BridgeResult bridge =
                        SemanticSqlDslRequestMapper.toDslRequest(model, dslPlan);
                bridge.requireReady();
                SemanticQueryRequest dslRequest = bridge.request();
                dslRequest.setHints(request.getHints() == null ? null : new HashMap<>(request.getHints()));
                return generateSql(model, dslRequest, context);
            }
            throw RX.throwB("SEMANTIC_SQL_EXECUTION_NOT_IMPLEMENTED: Semantic SQL AST whitelist passed, but SQL-to-DSL compilation is not part of P0.");
        }
        if (isMemoryGridRequest(request)) {
            memoryGridValidation(request, context);
            throw RX.throwB("MEMORY_GRID_EXECUTION_NOT_IMPLEMENTED: Memory Grid guardrail passed, but in-memory execution is not part of P0.");
        }
        if (isDslCtePlan(request)) {
            dslCteValidation(request);
            if (dslCteCompileToDslEnabled(request)) {
                DslCteDslRequestMapper.BridgeResult bridge =
                        DslCteDslRequestMapper.toDslRequest(model, request.getExecutablePlan());
                if (bridge.ready()) {
                    SemanticQueryRequest dslRequest = bridge.request();
                    dslRequest.setHints(request.getHints() == null ? null : new HashMap<>(request.getHints()));
                    SqlGenerationResult baseSql = generateSql(bridge.model(), dslRequest, context);
                    return DslCteDslRequestMapper.applyTopLevelLimitIfDeclared(
                            baseSql, request.getExecutablePlan());
                }
                DslCteDslRequestMapper.ResultStageWindowBridgeResult resultStageBridge =
                        DslCteDslRequestMapper.toResultStageWindowBridge(model, request.getExecutablePlan());
                if (resultStageBridge.ready()) {
                    SemanticQueryRequest baseRequest = resultStageBridge.baseRequest();
                    baseRequest.setHints(request.getHints() == null ? null : new HashMap<>(request.getHints()));
                    SqlGenerationResult baseSql = generateSql(resultStageBridge.model(), baseRequest, context);
                    return resultStageBridge.wrap(baseSql);
                }
                DslCteDslRequestMapper.ResultStageMetricRatioBridgeResult metricRatioBridge =
                        DslCteDslRequestMapper.toResultStageMetricRatioBridge(model, request.getExecutablePlan());
                if (metricRatioBridge.ready()) {
                    SemanticQueryRequest baseRequest = metricRatioBridge.baseRequest();
                    baseRequest.setHints(request.getHints() == null ? null : new HashMap<>(request.getHints()));
                    SqlGenerationResult baseSql = generateSql(metricRatioBridge.model(), baseRequest, context);
                    return metricRatioBridge.wrap(baseSql);
                }
                DslCteDslRequestMapper.CrossModelFunnelMoneyAttributionBridgeResult moneyAttributionBridge =
                        DslCteDslRequestMapper.toCrossModelFunnelMoneyAttributionBridge(
                                model, request.getExecutablePlan());
                if (moneyAttributionBridge.ready()) {
                    SqlGenerationResult denominatorSql = null;
                    if (moneyAttributionBridge.denominatorRequest() != null) {
                        SemanticQueryRequest denominatorRequest = moneyAttributionBridge.denominatorRequest();
                        denominatorRequest.setHints(request.getHints() == null ? null : new HashMap<>(request.getHints()));
                        denominatorSql = generateSql(
                                moneyAttributionBridge.denominatorModel(), denominatorRequest, context);
                    }
                    SemanticQueryRequest leftRequest = moneyAttributionBridge.leftRequest();
                    leftRequest.setHints(request.getHints() == null ? null : new HashMap<>(request.getHints()));
                    SemanticQueryRequest rightRequest = moneyAttributionBridge.rightRequest();
                    rightRequest.setHints(request.getHints() == null ? null : new HashMap<>(request.getHints()));
                    SqlGenerationResult leftSql = generateSql(moneyAttributionBridge.leftModel(), leftRequest, context);
                    SqlGenerationResult rightSql = generateSql(moneyAttributionBridge.rightModel(), rightRequest, context);
                    if (denominatorSql != null) {
                        return moneyAttributionBridge.wrap(denominatorSql, leftSql, rightSql);
                    }
                    return moneyAttributionBridge.wrap(leftSql, rightSql);
                }
                if (moneyAttributionBridge.relevant()) {
                    throw RX.throwB("DSL_CTE_DSL_BRIDGE_NOT_SUPPORTED: " + moneyAttributionBridge.unsupported());
                }
                DslCteDslRequestMapper.CrossModelFunnelTimeAttributionBridgeResult timeAttributionBridge =
                        DslCteDslRequestMapper.toCrossModelFunnelTimeAttributionBridge(
                                model, request.getExecutablePlan());
                if (timeAttributionBridge.ready()) {
                    SemanticQueryRequest denominatorRequest = timeAttributionBridge.denominatorRequest();
                    denominatorRequest.setHints(request.getHints() == null ? null : new HashMap<>(request.getHints()));
                    SemanticQueryRequest leftRequest = timeAttributionBridge.leftRequest();
                    leftRequest.setHints(request.getHints() == null ? null : new HashMap<>(request.getHints()));
                    SemanticQueryRequest rightRequest = timeAttributionBridge.rightRequest();
                    rightRequest.setHints(request.getHints() == null ? null : new HashMap<>(request.getHints()));
                    SqlGenerationResult denominatorSql = generateSql(
                            timeAttributionBridge.denominatorModel(), denominatorRequest, context);
                    SqlGenerationResult leftSql = generateSql(timeAttributionBridge.leftModel(), leftRequest, context);
                    SqlGenerationResult rightSql = generateSql(timeAttributionBridge.rightModel(), rightRequest, context);
                    return timeAttributionBridge.wrap(denominatorSql, leftSql, rightSql);
                }
                if (timeAttributionBridge.relevant()) {
                    throw RX.throwB("DSL_CTE_DSL_BRIDGE_NOT_SUPPORTED: " + timeAttributionBridge.unsupported());
                }
                DslCteDslRequestMapper.CrossModelFunnelSourceRateBridgeResult funnelSourceRateBridge =
                        DslCteDslRequestMapper.toCrossModelFunnelSourceRateBridge(model, request.getExecutablePlan());
                if (funnelSourceRateBridge.ready()) {
                    SemanticQueryRequest denominatorRequest = funnelSourceRateBridge.denominatorRequest();
                    denominatorRequest.setHints(request.getHints() == null ? null : new HashMap<>(request.getHints()));
                    SemanticQueryRequest leftRequest = funnelSourceRateBridge.leftRequest();
                    leftRequest.setHints(request.getHints() == null ? null : new HashMap<>(request.getHints()));
                    SemanticQueryRequest rightRequest = funnelSourceRateBridge.rightRequest();
                    rightRequest.setHints(request.getHints() == null ? null : new HashMap<>(request.getHints()));
                    SqlGenerationResult denominatorSql = generateSql(
                            funnelSourceRateBridge.denominatorModel(), denominatorRequest, context);
                    SqlGenerationResult leftSql = generateSql(funnelSourceRateBridge.leftModel(), leftRequest, context);
                    SqlGenerationResult rightSql = generateSql(funnelSourceRateBridge.rightModel(), rightRequest, context);
                    return funnelSourceRateBridge.wrap(denominatorSql, leftSql, rightSql);
                }
                DslCteDslRequestMapper.CrossModelJoinAlignBridgeResult joinAlignBridge =
                        DslCteDslRequestMapper.toCrossModelJoinAlignBridge(model, request.getExecutablePlan());
                if (joinAlignBridge.ready()) {
                    SemanticQueryRequest leftRequest = joinAlignBridge.leftRequest();
                    leftRequest.setHints(request.getHints() == null ? null : new HashMap<>(request.getHints()));
                    SemanticQueryRequest rightRequest = joinAlignBridge.rightRequest();
                    rightRequest.setHints(request.getHints() == null ? null : new HashMap<>(request.getHints()));
                    SqlGenerationResult leftSql = generateSql(joinAlignBridge.leftModel(), leftRequest, context);
                    SqlGenerationResult rightSql = generateSql(joinAlignBridge.rightModel(), rightRequest, context);
                    return joinAlignBridge.wrap(leftSql, rightSql);
                }
                throw RX.throwB("DSL_CTE_DSL_BRIDGE_NOT_SUPPORTED: "
                        + combinedDslCteUnsupported(bridge, resultStageBridge, metricRatioBridge,
                        moneyAttributionBridge, timeAttributionBridge, funnelSourceRateBridge, joinAlignBridge));
            }
            throw RX.throwB("DSL_CTE_EXECUTION_NOT_IMPLEMENTED: DSL_CTE stage contract passed, but staged SQL execution is not part of P0.");
        }
        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            throw RX.throwB("请指定查询字段");
        }

        String namespace = context.getNamespace();
        ModelResultContext.SecurityContext securityContext = context.getSecurityContext();

        // 1. 构建上下文（复用现有逻辑）
        QueryContextV3 qctx = new QueryContextV3();
        qctx.model = model;
        qctx.originalRequest = request;

        // 2. 构建初始JDBC请求
        PagingRequest<DbQueryRequestDef> jdbcRequest = buildJdbcRequest(model, request, qctx, namespace);

        // 3. 处理 slice 中的 $caption 值转换
        if (request.getSlice() != null) {
            List<SliceRequestDef> processedSlice = processSliceValues(model, request.getSlice(), request, qctx);
            jdbcRequest.getParam().setSlice(processedSlice);
        }

        // 4. 创建ModelResultContext（含列权限）
        ModelResultContext resultContext = new ModelResultContext();
        resultContext.setRequest(jdbcRequest);
        resultContext.setQueryType(ModelResultContext.QueryType.SEMANTIC);
        resultContext.setSecurityContext(securityContext);
        resultContext.setNamespace(namespace);
        resultContext.setFieldAccess(context.getFieldAccess());
        resultContext.setDeniedColumns(context.getDeniedColumns());
        resultContext.setSystemSlice(context.getSystemSlice());

        Map<String, Object> extData = new HashMap<>();
        if (request.getHints() != null && !request.getHints().isEmpty()) {
            extData.putAll(request.getHints());
        }
        if (request.getTimeWindow() != null && !request.getTimeWindow().isEmpty()) {
            extData.put("timeWindow", request.getTimeWindow());
        }
        // Stage 5: pass calculatedFields to extData so TimeWindowInterceptor
        // can build the outer post-calc projection wrapper.
        if (request.getCalculatedFields() != null && !request.getCalculatedFields().isEmpty()) {
            extData.put("calculatedFields", new ArrayList<>(request.getCalculatedFields()));
        }
        putDomainTransportPlans(extData, context);
        if (!extData.isEmpty()) {
            resultContext.setExtData(extData);
        }

        // 5. 走 beforeQuery pipeline 然后截取 SQL（不执行）
        SqlGenerationResult result = queryFacade.buildSqlOnly(resultContext);
        if (resultContext.getExtData() != null
                && (resultContext.getExtData().containsKey("timeWindowPlan")
                || resultContext.getExtData().containsKey("comparativePlan"))) {
            com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan timeWindowPlan =
                    (com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan) resultContext.getExtData().getOrDefault(
                            "timeWindowPlan", resultContext.getExtData().get("comparativePlan"));
            com.foggyframework.dataset.db.model.engine.compose.ComposedSql composedSql =
                    compileTimeWindowPlan(timeWindowPlan, context, resultContext.getExtData());
            return new SqlGenerationResult(composedSql.getSql(), composedSql.getParams(), null);
        }
        return result;
    }

    @Override
    public Optional<String> resolveFieldSqlExpression(String model, String field, String namespace) {
        if (StringUtils.isEmpty(model) || StringUtils.isEmpty(field)) {
            return Optional.empty();
        }
        QueryModel queryModel = queryModelLoader.getJdbcQueryModel(model, namespace);
        if (queryModel == null) {
            return Optional.empty();
        }

        DbColumn column = null;
        DbQueryCondition condition = queryModel.findJdbcQueryCondByField(field);
        if (condition != null) {
            column = condition.getColumn();
        }
        if (column == null) {
            DbQueryColumn queryColumn = queryModel.findJdbcQueryColumnByName(field, false);
            if (queryColumn != null) {
                column = queryColumn.getSelectColumn() != null
                        ? queryColumn.getSelectColumn()
                        : queryColumn;
            }
        }
        if (column == null) {
            return Optional.empty();
        }

        String alias = queryModel.getAlias(column.getQueryObject());
        return Optional.of(column.getDeclare(null, alias));
    }

    private void putDomainTransportPlans(Map<String, Object> extData, SemanticRequestContext context) {
        if (context != null
                && context.getDomainTransportPlans() != null
                && !context.getDomainTransportPlans().isEmpty()) {
            extData.put(DomainTransportPlan.EXT_DATA_KEY, new ArrayList<>(context.getDomainTransportPlans()));
        }
    }

    private SemanticQueryResponse terminalResponseIfAny(SemanticQueryRequest request) {
        if (!isTerminalPlan(request)) {
            return null;
        }
        if (request.getExecutablePlan() != null) {
            throw RX.throwB("TERMINAL_PLAN_MUST_NOT_HAVE_EXECUTABLE_PLAN: CLARIFY/REJECT terminal plans must not carry executable_plan.");
        }
        String terminal = normalizeTerminal(request.getStatus());
        if (terminal == null) {
            terminal = normalizeTerminal(request.getRoute());
        }
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of());
        response.setWarnings(List.of());

        SemanticQueryResponse.ExecutionInfo execution = new SemanticQueryResponse.ExecutionInfo();
        execution.setRoute(firstNonBlank(request.getRoute(), terminal));
        execution.setStatus(terminal);
        execution.setRiskFlags(request.getRiskFlags() != null ? List.copyOf(request.getRiskFlags()) : List.of());
        execution.setWhy(request.getWhy() != null ? List.copyOf(request.getWhy()) : List.of());
        execution.setClarifyingQuestions(request.getClarifyingQuestions() != null
                ? List.copyOf(request.getClarifyingQuestions())
                : List.of());
        execution.setExecutablePlan(null);
        execution.setErrorCode(terminal + "_TERMINAL");
        response.setExecution(execution);

        SemanticQueryResponse.SemanticInfo semantic = new SemanticQueryResponse.SemanticInfo();
        semantic.setEmptyResult(true);
        semantic.setEmptyReason(terminal + "_TERMINAL");
        semantic.setShouldAnswerDirectly(true);
        response.setSemantic(semantic);
        return response;
    }

    private boolean isTerminalPlan(SemanticQueryRequest request) {
        if (request == null) {
            return false;
        }
        return normalizeTerminal(request.getStatus()) != null || normalizeTerminal(request.getRoute()) != null;
    }

    private SemanticQueryResponse semanticSqlPlanResponseIfAny(String model, SemanticQueryRequest request,
                                                               SemanticRequestContext context) {
        if (!isSemanticSqlPlan(request)) {
            return null;
        }
        QueryModel queryModel = queryModelLoader.getJdbcQueryModel(model, context.getNamespace());
        Map<String, Object> astValidation = semanticSqlAstValidation(model, request, queryModel, context);
        Map<String, Object> dslPlan = SemanticSqlToDslMapper.map(model, request.getSemanticSql(), queryModel, context);
        SemanticSqlDslRequestMapper.BridgeResult bridge = SemanticSqlDslRequestMapper.toDslRequest(model, dslPlan);
        dslPlan.put("dsl_bridge_status", bridge.status());
        if (bridge.ready()) {
            dslPlan.put("dsl_request", bridge.request());
        } else {
            dslPlan.put("dsl_bridge_unsupported", bridge.unsupported());
        }
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of());
        response.setWarnings(List.of());

        SemanticQueryResponse.ExecutionInfo execution = new SemanticQueryResponse.ExecutionInfo();
        execution.setRoute(firstNonBlank(request.getRoute(), "SEMANTIC_SQL"));
        execution.setStatus(firstNonBlank(request.getStatus(), "PLAN_READY"));
        execution.setRiskFlags(request.getRiskFlags() != null ? List.copyOf(request.getRiskFlags()) : List.of());
        execution.setWhy(request.getWhy() != null ? List.copyOf(request.getWhy()) : List.of());
        execution.setClarifyingQuestions(List.of());
        execution.setExecutablePlan(request.getExecutablePlan());
        execution.setSemanticSql(request.getSemanticSql());
        execution.setAstValidation(astValidation);
        execution.setSemanticSqlDslPlan(dslPlan);
        execution.setErrorCode(null);
        response.setExecution(execution);
        return response;
    }

    private Map<String, Object> semanticSqlAstValidation(String model, SemanticQueryRequest request,
                                                          SemanticRequestContext context) {
        if (request == null || StringUtils.isEmpty(request.getSemanticSql())) {
            throw RX.throwB("SEMANTIC_SQL_FIELD_NOT_DECLARED: semantic_sql must be provided for SEMANTIC_SQL route.");
        }
        QueryModel queryModel = queryModelLoader.getJdbcQueryModel(model, context.getNamespace());
        return SemanticSqlWhitelistValidator.validate(model, request.getSemanticSql(), queryModel, context);
    }

    private boolean semanticSqlCompileToDslEnabled(SemanticQueryRequest request) {
        if (request == null || request.getHints() == null) {
            return false;
        }
        Object value = request.getHints().get("semanticSqlCompileToDsl");
        return Boolean.TRUE.equals(value) || (value instanceof String text && "true".equalsIgnoreCase(text));
    }

    private Map<String, Object> semanticSqlAstValidation(String model, SemanticQueryRequest request,
                                                          QueryModel queryModel,
                                                          SemanticRequestContext context) {
        if (request == null || StringUtils.isEmpty(request.getSemanticSql())) {
            throw RX.throwB("SEMANTIC_SQL_FIELD_NOT_DECLARED: semantic_sql must be provided for SEMANTIC_SQL route.");
        }
        return SemanticSqlWhitelistValidator.validate(model, request.getSemanticSql(), queryModel, context);
    }

    private boolean isSemanticSqlPlan(SemanticQueryRequest request) {
        if (request == null) {
            return false;
        }
        if (!StringUtils.isEmpty(request.getSemanticSql())) {
            return true;
        }
        String route = request.getRoute();
        return route != null && "SEMANTIC_SQL".equals(route.trim().toUpperCase(Locale.ROOT));
    }

    private SemanticQueryResponse memoryGridPlanResponseIfAny(SemanticQueryRequest request,
                                                              SemanticRequestContext context) {
        if (!isMemoryGridRequest(request)) {
            return null;
        }
        Map<String, Object> validation = memoryGridValidation(request, context);
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of());
        response.setWarnings(List.of());

        SemanticQueryResponse.ExecutionInfo execution = new SemanticQueryResponse.ExecutionInfo();
        execution.setRoute(firstNonBlank(request.getRoute(), "MEMORY_GRID"));
        execution.setStatus(firstNonBlank(request.getStatus(), "PLAN_READY"));
        execution.setRiskFlags(request.getRiskFlags() != null ? List.copyOf(request.getRiskFlags()) : List.of());
        execution.setWhy(request.getWhy() != null ? List.copyOf(request.getWhy()) : List.of());
        execution.setClarifyingQuestions(List.of());
        execution.setExecutablePlan(request.getExecutablePlan());
        execution.setMemoryGridPlan(request.getMemoryGridPlan());
        execution.setGridSql(request.getGridSql());
        execution.setMemoryGridValidation(validation);
        execution.setErrorCode(null);
        response.setExecution(execution);
        return response;
    }

    private SemanticQueryResponse memoryGridExecutionResponseIfAny(SemanticQueryRequest request,
                                                                   SemanticRequestContext context) {
        if (!isMemoryGridRequest(request) || !memoryGridExecuteEnabled(request)) {
            return null;
        }
        MemoryGridRequest memoryGridRequest = memoryGridRequest(request);
        Map<String, Object> validation = memoryGridValidation(request, context);
        MemoryGridExecutionResult result = memoryGridEngine().execute(memoryGridRequest, context);
        validation.putAll(result.validation());

        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(result.rows());
        response.setTotal((long) result.rows().size());
        response.setWarnings(List.of());

        SemanticQueryResponse.ExecutionInfo execution = new SemanticQueryResponse.ExecutionInfo();
        execution.setRoute(firstNonBlank(request.getRoute(), "MEMORY_GRID"));
        execution.setStatus("EXECUTED");
        execution.setRiskFlags(request.getRiskFlags() != null ? List.copyOf(request.getRiskFlags()) : List.of());
        execution.setWhy(request.getWhy() != null ? List.copyOf(request.getWhy()) : List.of());
        execution.setClarifyingQuestions(List.of());
        execution.setMemoryGridPlan(request.getMemoryGridPlan());
        execution.setGridSql(request.getGridSql());
        execution.setMemoryGridValidation(validation);
        execution.setMemoryGridExecutionSummary(result.summary());
        execution.setErrorCode(null);
        response.setExecution(execution);
        return response;
    }

    private Map<String, Object> memoryGridValidation(SemanticQueryRequest request,
                                                     SemanticRequestContext context) {
        MemoryGridRequest memoryGridRequest = memoryGridRequest(request);
        MemoryGridEngine engine = memoryGridEngine();
        MemoryGridDialectDescriptor dialect = engine.dialect();
        Map<String, Object> validationEvidence = new LinkedHashMap<>();
        appendMemoryGridDialectEvidence(validationEvidence, dialect);

        if (hasGridSql(request)) {
            if (dialect == null || !dialect.gridSqlSupported()) {
                throw RX.throwB("MEMORY_GRID_GRID_SQL_NOT_SUPPORTED: "
                        + firstNonBlank(dialect == null ? null : dialect.engineId(), "configured MemoryGridEngine")
                        + " does not support grid_sql.");
            }
            validationEvidence.putAll(GridSqlContractValidator.validate(memoryGridRequest));
        } else if (!hasMemoryGridPlan(request)) {
            throw RX.throwB("MEMORY_GRID_UNBOUNDED_INPUT: memory_grid_plan or grid_sql must be provided for MEMORY_GRID route.");
        }

        MemoryGridValidation validation = engine.validate(memoryGridRequest, context);
        validationEvidence.putAll(validation.evidence());
        appendMemoryGridDialectEvidence(validationEvidence, dialect);
        return validationEvidence;
    }

    private MemoryGridEngine memoryGridEngine() {
        if (memoryGridEngine == null) {
            throw RX.throwB("MEMORY_GRID_ENGINE_NOT_CONFIGURED: no MemoryGridEngine is configured.");
        }
        return memoryGridEngine;
    }

    private MemoryGridRequest memoryGridRequest(SemanticQueryRequest request) {
        if (request == null) {
            return new MemoryGridRequest(Map.of(), null, List.of(), Map.of(), null);
        }
        return new MemoryGridRequest(
                request.getMemoryGridPlan(),
                request.getGridSql(),
                request.getMemoryGridBindings(),
                request.getHints(),
                request.getExecutablePlan());
    }

    private boolean memoryGridExecuteEnabled(SemanticQueryRequest request) {
        if (request == null || request.getHints() == null) {
            return false;
        }
        Object value = request.getHints().get("memoryGridExecute");
        return Boolean.TRUE.equals(value) || (value instanceof String text && "true".equalsIgnoreCase(text));
    }

    private boolean isMemoryGridRequest(SemanticQueryRequest request) {
        if (request == null) {
            return false;
        }
        if (hasMemoryGridPlan(request) || hasGridSql(request)) {
            return true;
        }
        String route = request.getRoute();
        return route != null && "MEMORY_GRID".equals(route.trim().toUpperCase(Locale.ROOT));
    }

    private boolean hasMemoryGridPlan(SemanticQueryRequest request) {
        return request != null
                && request.getMemoryGridPlan() != null
                && !request.getMemoryGridPlan().isEmpty();
    }

    private boolean hasGridSql(SemanticQueryRequest request) {
        return request != null && !StringUtils.isTrimEmpty(request.getGridSql());
    }

    private void appendMemoryGridDialectEvidence(Map<String, Object> validationEvidence,
                                                 MemoryGridDialectDescriptor dialect) {
        if (dialect == null) {
            return;
        }
        validationEvidence.put("memory_grid_engine", dialect.engineId());
        validationEvidence.put("memory_grid_dialect", dialect.dialectId());
        validationEvidence.put("memory_grid_plan_supported", dialect.planSupported());
        validationEvidence.put("memory_grid_grid_sql_supported", dialect.gridSqlSupported());
    }

    private SemanticQueryResponse dslCtePlanResponseIfAny(SemanticQueryRequest request) {
        if (!isDslCtePlan(request)) {
            return null;
        }
        Map<String, Object> validation = dslCteValidation(request);
        DslCteDslRequestMapper.BridgeResult bridge =
                DslCteDslRequestMapper.toDslRequest(null, request.getExecutablePlan());
        if (bridge.ready()) {
            validation.put("dsl_bridge_status", bridge.status());
            validation.put("dsl_bridge_model", bridge.model());
            validation.put("dsl_request", bridge.request());
        } else {
            DslCteDslRequestMapper.ResultStageWindowBridgeResult resultStageBridge =
                    DslCteDslRequestMapper.toResultStageWindowBridge(null, request.getExecutablePlan());
            if (resultStageBridge.ready()) {
                validation.put("dsl_bridge_status", resultStageBridge.status());
                validation.put("dsl_bridge_model", resultStageBridge.model());
                validation.put("dsl_request", resultStageBridge.baseRequest());
                validation.put("dsl_result_stage_window", resultStageBridge.summary());
            } else {
                DslCteDslRequestMapper.ResultStageMetricRatioBridgeResult metricRatioBridge =
                        DslCteDslRequestMapper.toResultStageMetricRatioBridge(null, request.getExecutablePlan());
                if (metricRatioBridge.ready()) {
                    validation.put("dsl_bridge_status", metricRatioBridge.status());
                    validation.put("dsl_bridge_model", metricRatioBridge.model());
                    validation.put("dsl_request", metricRatioBridge.baseRequest());
                    validation.put("dsl_result_stage_metric_ratio", metricRatioBridge.summary());
                } else {
                    DslCteDslRequestMapper.CrossModelFunnelMoneyAttributionBridgeResult moneyAttributionBridge =
                            DslCteDslRequestMapper.toCrossModelFunnelMoneyAttributionBridge(
                                    null, request.getExecutablePlan());
                    if (moneyAttributionBridge.ready()) {
                        validation.put("dsl_bridge_status", moneyAttributionBridge.status());
                        Map<String, Object> models = new LinkedHashMap<>();
                        if (moneyAttributionBridge.denominatorModel() != null) {
                            models.put("denominator", moneyAttributionBridge.denominatorModel());
                        }
                        models.put("left", moneyAttributionBridge.leftModel());
                        models.put("right", moneyAttributionBridge.rightModel());
                        validation.put("dsl_bridge_models", models);
                        if (moneyAttributionBridge.denominatorRequest() != null) {
                            validation.put("dsl_denominator_request", moneyAttributionBridge.denominatorRequest());
                        }
                        validation.put("dsl_left_request", moneyAttributionBridge.leftRequest());
                        validation.put("dsl_right_request", moneyAttributionBridge.rightRequest());
                        validation.put("dsl_cross_model_funnel_money_attribution",
                                moneyAttributionBridge.summary());
                    } else if (moneyAttributionBridge.relevant()) {
                        validation.put("dsl_bridge_status", moneyAttributionBridge.status());
                        validation.put("dsl_bridge_unsupported", moneyAttributionBridge.unsupported());
                    } else {
                        DslCteDslRequestMapper.CrossModelFunnelTimeAttributionBridgeResult timeAttributionBridge =
                                DslCteDslRequestMapper.toCrossModelFunnelTimeAttributionBridge(
                                        null, request.getExecutablePlan());
                        if (timeAttributionBridge.ready()) {
                            validation.put("dsl_bridge_status", timeAttributionBridge.status());
                            Map<String, Object> models = new LinkedHashMap<>();
                            models.put("denominator", timeAttributionBridge.denominatorModel());
                            models.put("left", timeAttributionBridge.leftModel());
                            models.put("right", timeAttributionBridge.rightModel());
                            validation.put("dsl_bridge_models", models);
                            validation.put("dsl_denominator_request", timeAttributionBridge.denominatorRequest());
                            validation.put("dsl_left_request", timeAttributionBridge.leftRequest());
                            validation.put("dsl_right_request", timeAttributionBridge.rightRequest());
                            validation.put("dsl_cross_model_funnel_time_attribution",
                                    timeAttributionBridge.summary());
                        } else if (timeAttributionBridge.relevant()) {
                            validation.put("dsl_bridge_status", timeAttributionBridge.status());
                            validation.put("dsl_bridge_unsupported", timeAttributionBridge.unsupported());
                        } else {
                            DslCteDslRequestMapper.CrossModelFunnelSourceRateBridgeResult funnelSourceRateBridge =
                                    DslCteDslRequestMapper.toCrossModelFunnelSourceRateBridge(
                                            null, request.getExecutablePlan());
                            if (funnelSourceRateBridge.ready()) {
                                validation.put("dsl_bridge_status", funnelSourceRateBridge.status());
                                Map<String, Object> models = new LinkedHashMap<>();
                                models.put("denominator", funnelSourceRateBridge.denominatorModel());
                                models.put("left", funnelSourceRateBridge.leftModel());
                                models.put("right", funnelSourceRateBridge.rightModel());
                                validation.put("dsl_bridge_models", models);
                                validation.put("dsl_denominator_request", funnelSourceRateBridge.denominatorRequest());
                                validation.put("dsl_left_request", funnelSourceRateBridge.leftRequest());
                                validation.put("dsl_right_request", funnelSourceRateBridge.rightRequest());
                                validation.put("dsl_cross_model_funnel_source_rate", funnelSourceRateBridge.summary());
                            } else {
                                DslCteDslRequestMapper.CrossModelJoinAlignBridgeResult joinAlignBridge =
                                        DslCteDslRequestMapper.toCrossModelJoinAlignBridge(
                                                null, request.getExecutablePlan());
                                validation.put("dsl_bridge_status",
                                        joinAlignBridge.ready() ? joinAlignBridge.status() : bridge.status());
                                if (joinAlignBridge.ready()) {
                                    Map<String, Object> models = new LinkedHashMap<>();
                                    models.put("left", joinAlignBridge.leftModel());
                                    models.put("right", joinAlignBridge.rightModel());
                                    validation.put("dsl_bridge_models", models);
                                    validation.put("dsl_left_request", joinAlignBridge.leftRequest());
                                    validation.put("dsl_right_request", joinAlignBridge.rightRequest());
                                    validation.put("dsl_cross_model_join_align", joinAlignBridge.summary());
                                } else {
                                    validation.put("dsl_bridge_unsupported",
                                            combinedDslCteUnsupported(bridge, resultStageBridge, metricRatioBridge,
                                                    moneyAttributionBridge, timeAttributionBridge,
                                                    funnelSourceRateBridge, joinAlignBridge));
                                }
                            }
                        }
                    }
                }
            }
        }
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of());
        response.setWarnings(List.of());

        SemanticQueryResponse.ExecutionInfo execution = new SemanticQueryResponse.ExecutionInfo();
        execution.setRoute(firstNonBlank(request.getRoute(), "DSL_CTE"));
        execution.setStatus(firstNonBlank(request.getStatus(), "PLAN_READY"));
        execution.setRiskFlags(request.getRiskFlags() != null ? List.copyOf(request.getRiskFlags()) : List.of());
        execution.setWhy(request.getWhy() != null ? List.copyOf(request.getWhy()) : List.of());
        execution.setClarifyingQuestions(List.of());
        execution.setExecutablePlan(request.getExecutablePlan());
        execution.setDslCteValidation(validation);
        execution.setErrorCode(null);
        response.setExecution(execution);
        return response;
    }

    private List<String> combinedDslCteUnsupported(DslCteDslRequestMapper.BridgeResult bridge,
                                                   DslCteDslRequestMapper.ResultStageWindowBridgeResult resultStageBridge) {
        return combinedDslCteUnsupported(bridge, resultStageBridge, null);
    }

    private List<String> combinedDslCteUnsupported(DslCteDslRequestMapper.BridgeResult bridge,
                                                   DslCteDslRequestMapper.ResultStageWindowBridgeResult resultStageBridge,
                                                   DslCteDslRequestMapper.ResultStageMetricRatioBridgeResult metricRatioBridge) {
        return combinedDslCteUnsupported(bridge, resultStageBridge, metricRatioBridge, null);
    }

    private List<String> combinedDslCteUnsupported(DslCteDslRequestMapper.BridgeResult bridge,
                                                   DslCteDslRequestMapper.ResultStageWindowBridgeResult resultStageBridge,
                                                   DslCteDslRequestMapper.ResultStageMetricRatioBridgeResult metricRatioBridge,
                                                   DslCteDslRequestMapper.CrossModelJoinAlignBridgeResult joinAlignBridge) {
        return combinedDslCteUnsupported(bridge, resultStageBridge, metricRatioBridge, null, null, null,
                joinAlignBridge);
    }

    private List<String> combinedDslCteUnsupported(DslCteDslRequestMapper.BridgeResult bridge,
                                                   DslCteDslRequestMapper.ResultStageWindowBridgeResult resultStageBridge,
                                                   DslCteDslRequestMapper.ResultStageMetricRatioBridgeResult metricRatioBridge,
                                                   DslCteDslRequestMapper.CrossModelFunnelSourceRateBridgeResult funnelSourceRateBridge,
                                                   DslCteDslRequestMapper.CrossModelJoinAlignBridgeResult joinAlignBridge) {
        return combinedDslCteUnsupported(bridge, resultStageBridge, metricRatioBridge, null, null,
                funnelSourceRateBridge, joinAlignBridge);
    }

    private List<String> combinedDslCteUnsupported(DslCteDslRequestMapper.BridgeResult bridge,
                                                   DslCteDslRequestMapper.ResultStageWindowBridgeResult resultStageBridge,
                                                   DslCteDslRequestMapper.ResultStageMetricRatioBridgeResult metricRatioBridge,
                                                   DslCteDslRequestMapper.CrossModelFunnelMoneyAttributionBridgeResult moneyAttributionBridge,
                                                   DslCteDslRequestMapper.CrossModelFunnelTimeAttributionBridgeResult timeAttributionBridge,
                                                   DslCteDslRequestMapper.CrossModelFunnelSourceRateBridgeResult funnelSourceRateBridge,
                                                   DslCteDslRequestMapper.CrossModelJoinAlignBridgeResult joinAlignBridge) {
        Set<String> unsupported = new LinkedHashSet<>();
        if (bridge != null && bridge.unsupported() != null) {
            unsupported.addAll(bridge.unsupported());
        }
        if (resultStageBridge != null && resultStageBridge.unsupported() != null) {
            unsupported.addAll(resultStageBridge.unsupported());
        }
        if (metricRatioBridge != null && metricRatioBridge.unsupported() != null) {
            unsupported.addAll(metricRatioBridge.unsupported());
        }
        if (moneyAttributionBridge != null && moneyAttributionBridge.unsupported() != null) {
            unsupported.addAll(moneyAttributionBridge.unsupported());
        }
        if (timeAttributionBridge != null && timeAttributionBridge.unsupported() != null) {
            unsupported.addAll(timeAttributionBridge.unsupported());
        }
        if (funnelSourceRateBridge != null && funnelSourceRateBridge.unsupported() != null) {
            unsupported.addAll(funnelSourceRateBridge.unsupported());
        }
        if (joinAlignBridge != null && joinAlignBridge.unsupported() != null) {
            unsupported.addAll(joinAlignBridge.unsupported());
        }
        return List.copyOf(unsupported);
    }

    private Map<String, Object> dslCteValidation(SemanticQueryRequest request) {
        if (request == null || request.getExecutablePlan() == null) {
            throw RX.throwB("DSL_CTE_PLAN_NOT_DECLARED: executable_plan.cte_plan must be provided for DSL_CTE route.");
        }
        return DslCtePlanValidator.validate(request.getExecutablePlan());
    }

    private boolean dslCteCompileToDslEnabled(SemanticQueryRequest request) {
        if (request == null || request.getHints() == null) {
            return false;
        }
        Object value = request.getHints().get("dslCteCompileToDsl");
        return Boolean.TRUE.equals(value) || (value instanceof String text && "true".equalsIgnoreCase(text));
    }

    private boolean isDslCtePlan(SemanticQueryRequest request) {
        if (request == null) {
            return false;
        }
        String route = request.getRoute();
        return route != null && "DSL_CTE".equals(route.trim().toUpperCase(Locale.ROOT));
    }

    private String normalizeTerminal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "CLARIFY".equals(normalized) || "REJECT".equals(normalized) ? normalized : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private com.foggyframework.dataset.db.model.engine.compose.ComposedSql compileTimeWindowPlan(
            com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan plan,
            SemanticRequestContext requestContext,
            Map<String, Object> extData) {
        ModelResultContext.SecurityContext securityContext = requestContext.getSecurityContext();
        com.foggyframework.dataset.db.model.engine.compose.context.Principal principal =
                com.foggyframework.dataset.db.model.engine.compose.context.Principal.builder()
                        .userId(securityContext != null && securityContext.getUserId() != null ? securityContext.getUserId() : "system")
                        .deptId(securityContext != null ? securityContext.getDeptId() : null)
                        .tenantId(securityContext != null ? securityContext.getTenantId() : null)
                        .build();
        com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext composeContext =
                com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext.builder()
                        .principal(principal)
                        .namespace(requestContext.getNamespace())
                        .authorityResolver(req -> {
                            Map<String, com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding> bindings = new HashMap<>();
                            for (String m : req.modelNames()) {
                                bindings.put(m, com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding.builder().build());
                            }
                            return com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution.builder()
                                    .bindings(bindings)
                                    .build();
                        })
                        .build();
        String dialect = extData != null && extData.containsKey("timeWindowDialect")
                ? (String) extData.get("timeWindowDialect")
                : "mysql";
        return com.foggyframework.dataset.db.model.engine.compose.compilation.ComposeSqlCompiler.compilePlanToSql(
                plan,
                composeContext,
                com.foggyframework.dataset.db.model.engine.compose.compilation.ComposeSqlCompiler.CompileOptions.builder()
                        .semanticService(this)
                        .dialect(dialect)
                        .build());
    }

    /**
     * 带命名空间的验证查询（内部方法）
     */
    private SemanticQueryResponse validateQueryInternal(String model, SemanticQueryRequest request, String namespace) {
        SemanticQueryResponse response = new SemanticQueryResponse();

        // V3 的验证主要检查字段是否存在
        QueryModel queryModel = queryModelLoader.getJdbcQueryModel(model, namespace);
        if (queryModel == null) {
            throw RX.throwB("模型不存在: " + model);
        }

        List<String> warnings = new ArrayList<>();

        // 检查 columns 中的字段
        if (request.getColumns() != null) {
            for (String col : request.getColumns()) {
                DbQueryColumn queryColumn = queryModel.findJdbcQueryColumnByName(col, false);
                if (queryColumn == null) {
                    warnings.add("字段不存在: " + col);
                }
            }
        }
        if (request.getSlice() != null) {
            for (SemanticQueryRequest.SliceItem slice : request.getSlice()) {
                if (StringUtils.isEmpty(slice.getField())) {
                    throw RX.throwB(JsonUtils.toJson(slice) + "中的name字段不能为空");
                }
            }
        }
        if (request.getHaving() != null) {
            for (SemanticQueryRequest.SliceItem having : request.getHaving()) {
                if (!having._isLogicalGroup() && StringUtils.isEmpty(having.getField())) {
                    throw RX.throwB(JsonUtils.toJson(having) + "中的name字段不能为空");
                }
            }
        }
        if (request.getPostSlice() != null) {
            for (SemanticQueryRequest.SliceItem postSlice : request.getPostSlice()) {
                if (!postSlice._isLogicalGroup() && StringUtils.isEmpty(postSlice.getField())) {
                    throw RX.throwB(JsonUtils.toJson(postSlice) + "中的name字段不能为空");
                }
            }
        }

        // 检查 groupBy 和 columns 的对齐
        if (request.getGroupBy() != null && request.getColumns() != null) {
            Set<String> columnSet = new HashSet<>(request.getColumns());
            for (SemanticQueryRequest.GroupByItem item : request.getGroupBy()) {
                // 非度量字段（没有聚合类型）必须在 columns 中
                if ((item.getAgg() == null || item.getAgg().isEmpty())
                        && !columnSet.contains(item.getField())) {
                    warnings.add("groupBy 字段 " + item.getField() + " 必须出现在 columns 中");
                }
            }
        }

        response.setWarnings(warnings.isEmpty() ? null : warnings);
        return response;
    }

    /**
     * 构建JDBC查询请求（V3版本：直接透传字段名）
     */
    private PagingRequest<DbQueryRequestDef> buildJdbcRequest(String model, SemanticQueryRequest request,
                                                               QueryContextV3 context, String namespace) {
        DbQueryRequestDef queryDef = new DbQueryRequestDef();
        queryDef.setQueryModel(model);
        queryDef.setReturnTotal(request.getReturnTotal());
        queryDef.setStrictColumns(true);
        queryDef.setDistinct(Boolean.TRUE.equals(request.getDistinct()));
        queryDef.setWithSubtotals(Boolean.TRUE.equals(request.getWithSubtotals()));

        // 获取模型定义用于字段校验（带命名空间）
        QueryModel queryModel = queryModelLoader.getJdbcQueryModel(model, namespace);

        // --- Case-insensitive canonical field resolution ---
        if (CaseInsensitiveFieldResolver.isEnabled() && queryModel != null) {
            Set<String> fieldNames = new LinkedHashSet<>();
            for (DbQueryColumn col : queryModel.getJdbcQueryColumns()) {
                if (col.getName() != null) {
                    fieldNames.add(col.getName());
                }
            }
            for (CalculatedFieldDef calc : queryModel.getPredefinedCalculatedFields()) {
                if (calc.getName() != null) {
                    fieldNames.add(calc.getName());
                }
            }
            if (request.getCalculatedFields() != null) {
                for (CalculatedFieldDef calc : request.getCalculatedFields()) {
                    if (calc.getName() != null) {
                        fieldNames.add(calc.getName());
                    }
                }
            }
            if (request.getPostAggregateCalculations() != null) {
                for (var calc : request.getPostAggregateCalculations()) {
                    if (calc.getName() != null) {
                        fieldNames.add(calc.getName());
                    }
                }
            }
            CaseInsensitiveFieldResolver ciResolver = new CaseInsensitiveFieldResolver(fieldNames);
            resolveRequestFieldsCaseInsensitive(request, ciResolver);
        }

        // 复制 columns 和 groupBy 以便修改
        List<String> columns = new ArrayList<>(request.getColumns());
        List<SemanticQueryRequest.GroupByItem> groupByItems = request.getGroupBy() != null
                ? new ArrayList<>(request.getGroupBy())
                : null;

        // 自动对齐 columns 和 groupBy 中的维度字段
        if (groupByItems != null && !groupByItems.isEmpty()) {
            alignColumnsAndGroupBy(columns, groupByItems, queryModel, context);

            // 校验 groupBy 中的非度量字段必须在 columns 中
            validateGroupByFieldsInColumns(groupByItems, columns);
        }

        queryDef.setColumns(columns);
        queryDef.setCalculatedFields(request.getCalculatedFields() == null
                ? null
                : new ArrayList<>(request.getCalculatedFields()));
        queryDef.setPostAggregateCalculations(request.getPostAggregateCalculations() == null
                ? null
                : new ArrayList<>(request.getPostAggregateCalculations()));

        // 转换过滤条件（V3：字段名直接使用）
        if (request.getSlice() != null) {
            List<SliceRequestDef> jdbcSlice = request.getSlice().stream()
                    .map(this::convertToJdbcSlice)
                    .collect(Collectors.toList());
            queryDef.setSlice(jdbcSlice);
        }
        if (request.getHaving() != null) {
            List<SliceRequestDef> jdbcHaving = request.getHaving().stream()
                    .map(this::convertToJdbcSlice)
                    .collect(Collectors.toList());
            queryDef.setHaving(jdbcHaving);
        }
        if (request.getPostSlice() != null) {
            List<SliceRequestDef> jdbcPostSlice = request.getPostSlice().stream()
                    .map(this::convertToJdbcSlice)
                    .collect(Collectors.toList());
            queryDef.setPostSlice(jdbcPostSlice);
        }

        // 转换分组（V3：字段名直接使用）
        if (groupByItems != null) {
            List<GroupRequestDef> jdbcGroupBy = groupByItems.stream()
                    .map(item -> {
                        GroupRequestDef group = new GroupRequestDef();
                        group.setField(item.getField());
                        group.setAgg(item.getAgg());
                        return group;
                    })
                    .collect(Collectors.toList());
            queryDef.setGroupBy(jdbcGroupBy);
        }

        // 转换排序（V3：对维度字段自动补充后缀）
        if (request.getOrderBy() != null) {
            List<OrderRequestDef> jdbcOrderBy = request.getOrderBy().stream()
                    .map(item -> {
                        OrderRequestDef order = new OrderRequestDef();
                        String field = normalizeOrderByField(item.getField(), queryModel, context);
                        order.setField(field);
                        order.setDir(item.getDir());
                        return order;
                    })
                    .collect(Collectors.toList());
            queryDef.setOrderBy(jdbcOrderBy);
        }

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryDef);

        if (request.getStart() != null) {
            pagingRequest.setStart(request.getStart());
        }
        if (request.getLimit() != null) {
            pagingRequest.setPageSize(request.getLimit());
        }

        return pagingRequest;
    }

    /**
     * 处理 slice 中的值转换
     *
     * <p>V3 仍然需要处理的场景：</p>
     * <ul>
     *   <li>当 slice 使用 $caption 字段且传入的是 caption 值时，需要转换为 id 值</li>
     *   <li>例如：slice 使用 customer$caption = "张三"，需要转为对应的 customer_id</li>
     * </ul>
     */
    private List<SliceRequestDef> processSliceValues(String model, List<SemanticQueryRequest.SliceItem> slice,
                                                     SemanticQueryRequest request, QueryContextV3 context) {
        List<SliceRequestDef> processed = new ArrayList<>();

        for (SemanticQueryRequest.SliceItem item : slice) {
            // $or/$and 逻辑组：递归转换子条件
            if (item._isLogicalGroup()) {
                processed.add(convertToJdbcSlice(item));
                continue;
            }

            SliceRequestDef sliceDef = new SliceRequestDef();
            sliceDef.setField(item.getField());
            sliceDef.setOp(item.getOp());
            sliceDef.setValue(item.getValue());
            processed.add(sliceDef);
        }

        return processed;
    }

    private SliceRequestDef convertToJdbcSlice(SemanticQueryRequest.SliceItem item) {
        // $or/$and 逻辑组：递归转换子条件
        if (item._isLogicalGroup()) {
            SliceRequestDef groupDef = new SliceRequestDef();
            List<CondRequestDef> children = new ArrayList<>();
            for (SemanticQueryRequest.SliceItem child : item._getGroupChildren()) {
                children.add(convertToJdbcSlice(child));
            }
            if (item._isOrGroup()) {
                groupDef.setOr(children);
            } else {
                groupDef.setAnd(children);
            }
            return groupDef;
        }

        SliceRequestDef slice = new SliceRequestDef();
        slice.setField(item.getField());
        slice.setOp(item.getOp());
        slice.setValue(item.getValue());
        return slice;
    }

    /**
     * 构建响应
     */
    private SemanticQueryResponse buildResponse(DbQueryRequestDef request, PagingResultImpl queryResult,
                                                QueryContextV3 context, QueryModel queryModel) {
        SemanticQueryResponse response = new SemanticQueryResponse();

        // 转换数据项
        int returnedCount = 0;
        if (queryResult.getItems() != null) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object row : queryResult.getItems()) {
                if (row instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> item = (Map<String, Object>) row;
                    items.add(item);
                } else {
                    Map<String, Object> item = new HashMap<>();
                    item.put("data", row);
                    items.add(item);
                }
            }
            response.setItems(items);
            returnedCount = items.size();
        }

        // 获取实际使用的分页参数
        int actualStart = queryResult.getStart();
        int actualLimit = queryResult.getLimit();
        Long totalCount = queryResult.getTotal() > 0 ? queryResult.getTotal() : null;

        // 判断是否有更多数据
        boolean hasMore = false;
        if (totalCount != null) {
            // 有总数时，精确判断
            hasMore = (actualStart + returnedCount) < totalCount;
        } else {
            // 无总数时，根据返回条数判断（返回条数等于 limit 说明可能有更多）
            hasMore = returnedCount > 0 && returnedCount >= actualLimit;
        }

        // 构建分页信息
        SemanticQueryResponse.PaginationInfo pagination = new SemanticQueryResponse.PaginationInfo();
        pagination.setStart(actualStart);
        pagination.setLimit(actualLimit);
        pagination.setReturned(returnedCount);
        pagination.setTotalCount(totalCount);
        pagination.setHasMore(hasMore);
        pagination.setRangeDescription(buildRangeDescription(actualStart, returnedCount,actualLimit, totalCount, hasMore));
        response.setPagination(pagination);

        if (returnedCount == 0) {
            SemanticQueryResponse.SemanticInfo semantic = new SemanticQueryResponse.SemanticInfo();
            semantic.setEmptyResult(true);
            semantic.setEmptyReason("NO_MATCHING_ROWS");
            semantic.setShouldAnswerDirectly(true);
            response.setSemantic(semantic);
        }

        // 设置分页信息（保留原有字段以保持兼容性）
        response.setTotal(queryResult.getTotal());
        response.setHasNext(hasMore);
        response.setTotalData(queryResult.getTotalData());

        // 设置警告信息（合并语义层 warnings + 引擎层 engineWarnings）
        if (context.extData != null && context.extData.containsKey("engineWarnings")) {
            @SuppressWarnings("unchecked")
            List<String> engineWarnings = (List<String>) context.extData.get("engineWarnings");
            context.warnings.addAll(engineWarnings);
        }
        response.setWarnings(context.warnings.isEmpty() ? null : context.warnings);

        // 构建 Schema 信息（包含 summary）
        response.setSchema(buildSchemaInfo(queryModel, request, queryResult));

        // 设置数据截断信息（如果存在）
        if (context.extData != null && context.extData.containsKey("truncationInfo")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> truncationInfo = (Map<String, Object>) context.extData.get("truncationInfo");
            response.setTruncationInfo(truncationInfo);
        }

        return response;
    }

    /**
     * 构建数据范围描述（人类可读）
     */
    private String buildRangeDescription(int start, int returned,int actualLimit, Long totalCount, boolean hasMore) {
        if (returned == 0) {
            return "无数据";
        }

        int from = start + 1;
        int to = start + returned;

        StringBuilder sb = new StringBuilder();
        sb.append("显示第 ").append(from).append("-").append(to).append(" 条");

        if (totalCount != null && totalCount > 0) {
            sb.append("，共 ").append(totalCount).append(" 条");
        } else if (hasMore) {
            if(returned == actualLimit) {
                sb.append("，可能还有更多数据");
            }else{
                sb.append("，还有更多数据");
            }
        }

        return sb.toString();
    }

    /**
     * 构建结果集 Schema 信息（含 Markdown summary）
     */
    private SemanticQueryResponse.SchemaInfo buildSchemaInfo(QueryModel queryModel,
                                                             DbQueryRequestDef request,
                                                             PagingResultImpl queryResult) {
        SemanticQueryResponse.SchemaInfo schemaInfo = new SemanticQueryResponse.SchemaInfo();
        List<SemanticQueryResponse.SchemaInfo.ColumnDef> columnDefs = new ArrayList<>();

        List<String> columns = request.getColumns();
        if (columns != null && queryModel != null) {
            for (String columnName : columns) {
                DbQueryColumn queryColumn = queryModel.findJdbcQueryColumnByName(columnName, false);

                SemanticQueryResponse.SchemaInfo.ColumnDef columnDef =
                        new SemanticQueryResponse.SchemaInfo.ColumnDef();
                columnDef.setName(columnName);

                if (queryColumn != null) {
                    columnDef.setTitle(queryColumn.getCaption());
                    columnDef.setDataType(queryColumn.getType());
                }

                columnDefs.add(columnDef);
            }
        }

        schemaInfo.setColumns(columnDefs);

        // 生成 Markdown summary
        schemaInfo.setSummary(buildSchemaSummary(columnDefs, request, queryResult));

        return schemaInfo;
    }

    /**
     * 生成 Markdown 格式的结果摘要（仅聚合查询时生成）
     */
    private String buildSchemaSummary(List<SemanticQueryResponse.SchemaInfo.ColumnDef> columnDefs,
                                      DbQueryRequestDef request,
                                      PagingResultImpl queryResult) {
        List<GroupRequestDef> groupBy = request.getGroupBy();

        // 无分组时不生成 summary
        if (groupBy == null || groupBy.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        // 提取分组维度和度量指标
        List<String> dimensions = new ArrayList<>();
        List<String> measures = new ArrayList<>();

        Set<String> groupByNames = new HashSet<>();
        for (GroupRequestDef g : groupBy) {
            if (g.getAgg() == null || g.getAgg().isEmpty()) {
                // 无聚合类型 = 分组维度
                groupByNames.add(g.getField());
            }
        }

        for (SemanticQueryResponse.SchemaInfo.ColumnDef col : columnDefs) {
            if (groupByNames.contains(col.getName())) {
                dimensions.add(col.getTitle() != null ? col.getTitle() : col.getName());
            } else {
                // 找到对应的聚合类型
                String aggType = findAggregationType(col.getName(), groupBy);
                if (aggType != null) {
                    measures.add(col.getTitle() != null
                            ? col.getTitle() + "(" + translateAggType(aggType) + ")"
                            : col.getName());
                } else {
                    measures.add(col.getTitle() != null ? col.getTitle() : col.getName());
                }
            }
        }

        // 构建摘要
        if (!dimensions.isEmpty() && !measures.isEmpty()) {
            sb.append("按 ").append(String.join("、", dimensions)).append(" 分组");
            sb.append("，统计 ").append(String.join("、", measures));
        } else if (!dimensions.isEmpty()) {
            sb.append("按 ").append(String.join("、", dimensions)).append(" 分组");
        }

        // 添加数据量信息
        if (queryResult != null && queryResult.getItems() != null) {
            int rowCount = queryResult.getItems().size();
            sb.append("，返回 ").append(rowCount).append(" 条数据");
            if (queryResult.getTotal() > rowCount) {
                sb.append("（共 ").append(queryResult.getTotal()).append(" 条）");
            }
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 查找列的聚合类型
     */
    private String findAggregationType(String columnName, List<GroupRequestDef> groupBy) {
        if (groupBy == null) return null;
        for (GroupRequestDef g : groupBy) {
            if (columnName.equals(g.getField()) && g.getAgg() != null && !g.getAgg().isEmpty()) {
                return g.getAgg();
            }
        }
        return null;
    }

    /**
     * 翻译聚合类型为中文
     */
    private String translateAggType(String aggType) {
        if (aggType == null) return "";
        return switch (aggType.toUpperCase()) {
            case "SUM" -> "求和";
            case "AVG" -> "平均";
            case "COUNT" -> "计数";
            case "MAX" -> "最大";
            case "MIN" -> "最小";
            default -> aggType;
        };
    }

    /**
     * 添加调试信息
     */
    private void addDebugInfo(SemanticQueryResponse response, QueryContextV3 context, long startTime,
                              DbQueryRequestDef normalizedRequest, DbQueryResult dbQueryResult) {
        SemanticQueryResponse.DebugInfo debugInfo = new SemanticQueryResponse.DebugInfo();
        debugInfo.setDurationMs(System.currentTimeMillis() - startTime);

        SemanticQueryResponse.DebugInfo.NormalizedRequest normalized = new SemanticQueryResponse.DebugInfo.NormalizedRequest();
        normalized.setSlice(toSemanticSliceItems(normalizedRequest.getSlice()));
        normalized.setHaving(toSemanticSliceItems(normalizedRequest.getHaving()));
        normalized.setGroupBy(toSemanticGroupByItems(normalizedRequest.getGroupBy()));
        normalized.setOrderBy(toSemanticOrderItems(normalizedRequest.getOrderBy()));
        debugInfo.setNormalized(normalized);

        if (dbQueryResult.getQueryEngine() instanceof JdbcModelQueryEngine queryEngine) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("sql", normalizeDebugSql(queryEngine.getSql()));
            extra.put("aggSql", normalizeDebugSql(queryEngine.getAggSql()));
            extra.put("params", queryEngine.getValues());
            debugInfo.setExtra(extra);
        }

        response.setDebug(debugInfo);
    }

    private List<SemanticQueryRequest.SliceItem> toSemanticSliceItems(List<SliceRequestDef> slice) {
        if (slice == null) {
            return null;
        }
        return slice.stream().map(this::toSemanticSliceItem).collect(Collectors.toList());
    }

    private SemanticQueryRequest.SliceItem toSemanticSliceItem(CondRequestDef cond) {
        SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
        item.setField(cond.getField());
        item.setOp(cond.getOp());
        item.setValue(cond.getValue());

        if (cond.getOr() != null && !cond.getOr().isEmpty()) {
            item.setOr(cond.getOr().stream().map(this::toSemanticSliceItem).collect(Collectors.toList()));
        }
        if (cond.getAnd() != null && !cond.getAnd().isEmpty()) {
            item.setAnd(cond.getAnd().stream().map(this::toSemanticSliceItem).collect(Collectors.toList()));
        }
        return item;
    }

    private List<SemanticQueryRequest.GroupByItem> toSemanticGroupByItems(List<GroupRequestDef> groupBy) {
        if (groupBy == null) {
            return null;
        }
        return groupBy.stream()
                .map(item -> new SemanticQueryRequest.GroupByItem(item.getField(), item.getAgg()))
                .collect(Collectors.toList());
    }

    private List<SemanticQueryRequest.OrderItem> toSemanticOrderItems(List<OrderRequestDef> orderBy) {
        if (orderBy == null) {
            return null;
        }
        return orderBy.stream().map(item -> {
            SemanticQueryRequest.OrderItem orderItem = new SemanticQueryRequest.OrderItem();
            orderItem.setField(item.getField());
            orderItem.setDir(item.getDir());
            return orderItem;
        }).collect(Collectors.toList());
    }

    private String normalizeDebugSql(String sql) {
        if (sql == null) {
            return null;
        }
        return sql
                .replaceAll("(?i)\\bnot\\s+in\\s*\\(", "NOT IN (")
                .replaceAll("(?i)\\bin\\s*\\(", "IN (");
    }

    /**
     * 自动对齐 columns 和 groupBy 中的维度字段
     *
     * <p>当 columns 中有维度的 $caption 但 groupBy 中只有 $id（或反之），
     * 自动补全缺失的字段并添加警告信息。</p>
     *
     * @param columns      列列表（会被修改）
     * @param groupByItems 分组项列表（会被修改）
     * @param queryModel   查询模型
     * @param context      查询上下文（用于记录警告）
     */
    private void alignColumnsAndGroupBy(List<String> columns, List<SemanticQueryRequest.GroupByItem> groupByItems,
                                        QueryModel queryModel, QueryContextV3 context) {
        // 收集 columns 中的维度字段（按基础名分组）
        Map<String, Set<String>> columnDimensions = new HashMap<>();
        for (String col : columns) {
            if (col.contains("$")) {
                String baseName = col.substring(0, col.lastIndexOf('$'));
                String suffix = col.substring(col.lastIndexOf('$') + 1);
                columnDimensions.computeIfAbsent(baseName, k -> new HashSet<>()).add(suffix);
            }
        }

        // 收集 groupBy 中的非度量维度字段（按基础名分组）
        Map<String, Set<String>> groupByDimensions = new HashMap<>();
        for (SemanticQueryRequest.GroupByItem item : groupByItems) {
            // 跳过度量字段（有聚合类型的）
            if (item.getAgg() != null && !item.getAgg().isEmpty()) {
                continue;
            }
            String field = item.getField();
            if (field.contains("$")) {
                String baseName = field.substring(0, field.lastIndexOf('$'));
                String suffix = field.substring(field.lastIndexOf('$') + 1);
                groupByDimensions.computeIfAbsent(baseName, k -> new HashSet<>()).add(suffix);
            }
        }

        // 找出需要对齐的维度字段
        Set<String> allDimensionBases = new HashSet<>();
        allDimensionBases.addAll(columnDimensions.keySet());
        allDimensionBases.addAll(groupByDimensions.keySet());

        Set<String> columnsSet = new HashSet<>(columns);
        Set<String> groupByFieldSet = groupByItems.stream()
                .map(SemanticQueryRequest.GroupByItem::getField)
                .collect(Collectors.toSet());

        for (String baseName : allDimensionBases) {
            Set<String> colSuffixes = columnDimensions.getOrDefault(baseName, Collections.emptySet());
            Set<String> grpSuffixes = groupByDimensions.getOrDefault(baseName, Collections.emptySet());

            // columns 有 caption，检查是否需要补充 id 到 columns
            if (colSuffixes.contains("caption") && grpSuffixes.contains("id") && !colSuffixes.contains("id")) {
                String fieldToAdd = baseName + "$id";
                if (!columnsSet.contains(fieldToAdd) && queryModel.findJdbcQueryColumnByName(fieldToAdd, false) != null) {
                    columns.add(fieldToAdd);
                    columnsSet.add(fieldToAdd);
                    context.warnings.add("columns 自动补充字段 " + fieldToAdd + "（与 groupBy 对齐）");
                }
            }

            // columns 有 id，检查是否需要补充 caption 到 columns
            if (colSuffixes.contains("id") && grpSuffixes.contains("caption") && !colSuffixes.contains("caption")) {
                String fieldToAdd = baseName + "$caption";
                if (!columnsSet.contains(fieldToAdd) && queryModel.findJdbcQueryColumnByName(fieldToAdd, false) != null) {
                    columns.add(fieldToAdd);
                    columnsSet.add(fieldToAdd);
                    context.warnings.add("columns 自动补充字段 " + fieldToAdd + "（与 groupBy 对齐）");
                }
            }

            // groupBy 有 id，检查是否需要补充 caption 到 groupBy
            if (grpSuffixes.contains("id") && colSuffixes.contains("caption") && !grpSuffixes.contains("caption")) {
                String fieldToAdd = baseName + "$caption";
                if (!groupByFieldSet.contains(fieldToAdd) && queryModel.findJdbcQueryColumnByName(fieldToAdd, false) != null) {
                    SemanticQueryRequest.GroupByItem newItem = new SemanticQueryRequest.GroupByItem();
                    newItem.setField(fieldToAdd);
                    groupByItems.add(newItem);
                    groupByFieldSet.add(fieldToAdd);
                    context.warnings.add("groupBy 自动补充字段 " + fieldToAdd + "（与 columns 对齐）");
                }
            }

            // groupBy 有 caption，检查是否需要补充 id 到 groupBy
            if (grpSuffixes.contains("caption") && colSuffixes.contains("id") && !grpSuffixes.contains("id")) {
                String fieldToAdd = baseName + "$id";
                if (!groupByFieldSet.contains(fieldToAdd) && queryModel.findJdbcQueryColumnByName(fieldToAdd, false) != null) {
                    SemanticQueryRequest.GroupByItem newItem = new SemanticQueryRequest.GroupByItem();
                    newItem.setField(fieldToAdd);
                    groupByItems.add(newItem);
                    groupByFieldSet.add(fieldToAdd);
                    context.warnings.add("groupBy 自动补充字段 " + fieldToAdd + "（与 columns 对齐）");
                }
            }
        }
    }

    /**
     * 校验 groupBy 中的非度量字段必须在 columns 中
     *
     * @param groupByItems 分组项列表
     * @param columns      列列表
     */
    private void validateGroupByFieldsInColumns(List<SemanticQueryRequest.GroupByItem> groupByItems, List<String> columns) {
        Set<String> columnsSet = new HashSet<>(columns);

        // 解析 inline expression 的别名，加入 columnsSet
        // 例如 "YEAR(dateOrder) as year" → 将 "year" 加入集合
        for (String col : columns) {
            InlineExpressionParser.InlineExpression inlineExpr = InlineExpressionParser.parse(col);
            if (inlineExpr != null && inlineExpr.hasAlias()) {
                columnsSet.add(inlineExpr.getAlias());
            } else {
                // G5 v2-patch-2: 解析 plain alias "base AS alias"，将 base 字段名加入集合
                // 这样 groupBy 引用 base 字段时能通过校验（如 columns=[{field:'salesAmount',as:'revenue'}], groupBy=['salesAmount']）
                try {
                    ColumnAliasParts parts = AliasExtractor.extract(col);
                    if (parts.hasAlias()) {
                        columnsSet.add(parts.expression());  // base field name
                    }
                } catch (IllegalArgumentException ignored) {
                    // 解析失败 → 非 alias 列，忽略
                }
            }
        }

        // 收集 columns 中的维度基础名
        Set<String> columnBases = new HashSet<>();
        for (String col : columns) {
            if (col.contains("$")) {
                columnBases.add(col.substring(0, col.lastIndexOf('$')));
            }
        }

        for (SemanticQueryRequest.GroupByItem item : groupByItems) {
            // 跳过度量字段（有聚合类型的）
            if (item.getAgg() != null && !item.getAgg().isEmpty()) {
                continue;
            }

            String field = item.getField();

            // 检查字段是否在 columns 中
            if (!columnsSet.contains(field)) {
                // 检查是否是维度字段的变体（例如 columns 中有 product$caption，groupBy 中有 product$id）
                if (field.contains("$")) {
                    String baseName = field.substring(0, field.lastIndexOf('$'));
                    if (!columnBases.contains(baseName)) {
                        throw RX.throwB("groupBy 字段 " + field + " 必须出现在 columns 中（或 columns 中有其对应的维度字段）");
                    }
                } else {
                    // 非维度字段必须完全匹配
                    throw RX.throwB("groupBy 字段 " + field + " 必须出现在 columns 中");
                }
            }
        }
    }

    /**
     * 规范化 orderBy 字段名
     *
     * <p>对于没有后缀的维度字段，自动补充 $id 后缀</p>
     *
     * @param field      原始字段名
     * @param queryModel 查询模型
     * @param context    查询上下文（用于记录警告）
     * @return 规范化后的字段名
     */
    private String normalizeOrderByField(String field, QueryModel queryModel, QueryContextV3 context) {
        // 如果字段已有后缀，直接返回
        if (field.contains("$")) {
            return field;
        }

        // 检查是否是度量字段（直接存在于模型中）
        DbQueryColumn directColumn = queryModel.findJdbcQueryColumnByName(field, false);
        if (directColumn != null) {
            // 是度量字段，直接返回
            return field;
        }

        // 尝试补充 $id 后缀
        String fieldWithId = field + "$id";
        DbQueryColumn columnWithId = queryModel.findJdbcQueryColumnByName(fieldWithId, false);
        if (columnWithId != null) {
            context.warnings.add("orderBy 字段 " + field + " 自动补充为 " + fieldWithId);
            return fieldWithId;
        }

        // 尝试补充 $caption 后缀
        String fieldWithCaption = field + "$caption";
        DbQueryColumn columnWithCaption = queryModel.findJdbcQueryColumnByName(fieldWithCaption, false);
        if (columnWithCaption != null) {
            context.warnings.add("orderBy 字段 " + field + " 自动补充为 " + fieldWithCaption);
            return fieldWithCaption;
        }

        // 都找不到，返回原字段名，让底层报错
        return field;
    }

    /**
     * V3查询上下文
     */
    private static class QueryContextV3 {
        String model;
        SemanticQueryRequest originalRequest;
        Map<String, Object> extData = new HashMap<>();
        List<String> warnings = new ArrayList<>();
    }

    // ---- M7: raw-SQL execution for Compose Query ----

    @Override
    public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
        if (dataSource == null) {
            throw new RuntimeException(
                "executeSql failed: DataSource not injected into SemanticQueryServiceV3Impl;"
                + " host must configure a primary DataSource bean");
        }
        try {
            Object[] paramsArray = params == null ? new Object[0] : params.toArray(new Object[0]);
            return DataSourceQueryUtils.getDatasetTemplate(dataSource)
                    .getTemplate()
                    .queryForList(sql, paramsArray);
        } catch (Exception e) {
            throw new RuntimeException("executeSql failed: " + e.getMessage(), e);
        }
    }

    // ---- Case-insensitive field resolution helper ----

    /**
     * Resolve field names in the request to their canonical casing.
     * <p>
     * Mutates the request in-place: columns, slice/having field keys,
     * orderBy fields, and groupBy fields are resolved.
     * </p>
     */
    private static final java.util.regex.Pattern BARE_FIELD_RE =
            java.util.regex.Pattern.compile("^[A-Za-z_]\\w*(?:\\$\\w+)?$");
    private static final java.util.regex.Pattern BARE_ALIAS_RE =
            java.util.regex.Pattern.compile("^\\s*([A-Za-z_]\\w*(?:\\$\\w+)?)\\s+as\\s+([A-Za-z_]\\w*)\\s*$",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    private void resolveRequestFieldsCaseInsensitive(SemanticQueryRequest request,
                                                      CaseInsensitiveFieldResolver resolver) {
        // 1. columns (bare identifiers and bare aliases; skip expressions like "sum(amount) as x")
        if (request.getColumns() != null) {
            List<String> resolved = new ArrayList<>();
            for (String col : request.getColumns()) {
                if (col == null) {
                    resolved.add(null);
                    continue;
                }
                String trimmed = col.trim();
                java.util.regex.Matcher aliasMatch = BARE_ALIAS_RE.matcher(trimmed);
                if (BARE_FIELD_RE.matcher(trimmed).matches()) {
                    resolved.add(resolver.resolve(trimmed));
                } else if (aliasMatch.matches()) {
                    resolved.add(resolver.resolve(aliasMatch.group(1)) + " AS " + aliasMatch.group(2));
                } else {
                    resolved.add(col);
                }
            }
            request.setColumns(resolved);
        }

        // 2. slice
        resolveSliceFieldNames(request.getSlice(), resolver);
        resolveSliceFieldNames(request.getHaving(), resolver);
        resolveSliceFieldNames(request.getPostSlice(), resolver);

        // 3. orderBy
        if (request.getOrderBy() != null) {
            for (SemanticQueryRequest.OrderItem item : request.getOrderBy()) {
                if (item.getField() != null) {
                    item.setField(resolver.resolve(item.getField()));
                }
            }
        }

        // 4. groupBy
        if (request.getGroupBy() != null) {
            for (SemanticQueryRequest.GroupByItem item : request.getGroupBy()) {
                if (item.getField() != null) {
                    item.setField(resolver.resolve(item.getField()));
                }
            }
        }
    }

    private void resolveSliceFieldNames(List<SemanticQueryRequest.SliceItem> items,
                                         CaseInsensitiveFieldResolver resolver) {
        if (items == null) return;
        for (SemanticQueryRequest.SliceItem item : items) {
            if (item.getField() != null) {
                item.setField(resolver.resolve(item.getField()));
            }
            if (item.getOr() != null) {
                resolveSliceFieldNames(item.getOr(), resolver);
            }
            if (item.getAnd() != null) {
                resolveSliceFieldNames(item.getAnd(), resolver);
            }
        }
    }
}
