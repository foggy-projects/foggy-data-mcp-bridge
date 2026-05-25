package com.foggyframework.dataset.db.model.engine.query_model;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.expression.SqlCalculatedFieldProcessor;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.impl.model.TableModelSupport;
import com.foggyframework.dataset.db.model.interceptor.SqlLoggingInterceptor;
import com.foggyframework.dataset.db.model.plugins.query_execution.QueryExecutionContext;
import com.foggyframework.dataset.db.model.plugins.query_execution.QueryExecutionStep;
import com.foggyframework.dataset.db.model.plugins.query_execution.QueryExecutionStepExecutor;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.util.QueryErrorSanitizer;
import com.foggyframework.dataset.db.model.semantic.util.SanitizedQueryExecutionException;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.utils.DataSourceQueryUtils;
import com.foggyframework.dataset.utils.DbUtils;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Slf4j
public class JdbcQueryModelImpl extends QueryModelSupport implements JdbcQueryModel {


    DataSource dataSource;

    SqlFormulaService sqlFormulaService;

    /**
     * 计算字段处理器（延迟初始化）
     */
    private CalculatedFieldProcessor calculatedFieldProcessor;

    /**
     * SQL 日志拦截器（可选依赖）
     * <p>只有当 foggy.dataset.show-sql=true 时才会注入
     */
    private SqlLoggingInterceptor sqlLoggingInterceptor;

    /**
     * 查询执行步骤执行器
     * <p>
     * 管理预聚合重写、L2 缓存等步骤。
     * </p>
     */
    private QueryExecutionStepExecutor queryExecutionStepExecutor;

    public JdbcQueryModelImpl(List<TableModel> jdbcModelList, Fsscript fsscript, SqlFormulaService sqlFormulaService, DataSource dataSource) {
        super(jdbcModelList, fsscript);
        this.jdbcModel = jdbcModelList.get(0);
        this.sqlFormulaService = sqlFormulaService;
        this.dataSource = dataSource;
        this.fsscript = fsscript;
        this.jdbcModelList = jdbcModelList;
        // name2Alias 已在父类 QueryModelSupport 构造函数中统一注册
    }

    /**
     * 设置 SQL 日志拦截器（可选）
     * <p>由 Spring 容器自动注入（如果已启用）
     */
    public void setSqlLoggingInterceptor(SqlLoggingInterceptor sqlLoggingInterceptor) {
        this.sqlLoggingInterceptor = sqlLoggingInterceptor;
    }

    @Override
    public CalculatedFieldProcessor getCalculatedFieldProcessor() {
        if (calculatedFieldProcessor == null) {
            calculatedFieldProcessor = new SqlCalculatedFieldProcessor(this, getDialect());
        }
        return calculatedFieldProcessor;
    }


    @Override
    public DbQueryResult query(SystemBundlesContext systemBundlesContext, PagingRequest<DbQueryRequestDef> form) {
        // 创建新的上下文
        ModelResultContext context = new ModelResultContext(form, null);
        return query(systemBundlesContext, context);
    }

    @Override
    public DbQueryResult query(SystemBundlesContext systemBundlesContext, ModelResultContext context) {

        return queryJdbc(systemBundlesContext, context);
    }



    /**
     * 执行 JDBC 查询
     * <p>
     * 查询流程：
     * <ol>
     *   <li>构建 SQL（analysisQueryRequest）</li>
     *   <li>执行 beforeExecute Steps（预聚合重写、L2 缓存检查等）</li>
     *   <li>生成分页 SQL</li>
     *   <li>检查是否跳过执行（缓存命中）</li>
     *   <li>执行 SQL 并格式化结果</li>
     *   <li>执行 afterExecute Steps（L2 缓存写入等）</li>
     * </ol>
     * </p>
     *
     * @param systemBundlesContext 系统上下文
     * @param context              查询上下文（可能已预处理）
     * @return 查询结果
     */
    public DbQueryResult queryJdbc(SystemBundlesContext systemBundlesContext, ModelResultContext context) {
        PagingRequest<DbQueryRequestDef> form = context.getRequest();

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(this, sqlFormulaService);

        // 1. 构建查询语句（包含权限条件注入）
        queryEngine.analysisQueryRequest(systemBundlesContext, context);

        // 2. 创建执行上下文
        QueryExecutionContext execCtx = createExecutionContext(systemBundlesContext, context, queryEngine);

        // 3. 执行 beforeExecute Steps（预聚合重写、L2 缓存检查等）
        if (queryExecutionStepExecutor != null && queryExecutionStepExecutor.hasSteps()) {
            queryExecutionStepExecutor.executeBeforeExecute(execCtx);
        }

        // 4. 检查是否跳过执行（如 L2 缓存命中）
        if (execCtx.isSkipExecution() && execCtx.getCachedResult() != null) {
            if (log.isDebugEnabled()) {
                log.debug("Skipping SQL execution, using cached result for model={}", getName());
            }
            return DbQueryResult.of(execCtx.getCachedResult(), queryEngine);
        }

        // 5. 执行 SQL
        //    Wrap executor errors with QueryErrorSanitizer so the message
        //    does not leak physical table alias / column names
        //    (BUG-007 v1.3).  We rethrow with the same runtime exception
        //    shape the callers already expect (LocalDatasetAccessor /
        //    McpService catch Exception and read getMessage()).
        PagingResultImpl result;
        try {
            result = executeSql(execCtx, form, queryEngine);
        } catch (RuntimeException e) {
            String sanitized = QueryErrorSanitizer.sanitize(e.getMessage(), this);
            if (sanitized != null && !sanitized.isEmpty() && !sanitized.equals(e.getMessage())) {
                throw new SanitizedQueryExecutionException(sanitized, e);
            }
            throw e;
        }
        execCtx.setExecutionResult(result);

        // 6. 执行 afterExecute Steps（L2 缓存写入等）
        if (queryExecutionStepExecutor != null && queryExecutionStepExecutor.hasSteps()) {
            queryExecutionStepExecutor.executeAfterExecute(execCtx);
        }

        return DbQueryResult.of(result, queryEngine);
    }

    /**
     * 仅生成 SQL，不执行
     *
     * <p>用于 CTE/子查询组合场景：复用 {@link #queryJdbc} 的 SQL 构建流程
     * （{@code analysisQueryRequest}），截取生成的 SQL 和参数后返回，
     * 不执行查询、不走预聚合/缓存步骤。</p>
     *
     * @param systemBundlesContext 系统上下文
     * @param context              查询上下文（已完成 beforeQuery pipeline）
     * @return SQL 生成结果（含 SQL、参数、查询引擎引用）
     */
    public SqlGenerationResult generateSql(SystemBundlesContext systemBundlesContext, ModelResultContext context) {
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(this, sqlFormulaService);
        queryEngine.analysisQueryRequest(systemBundlesContext, context);

        if (queryEngine.isCteWrapped()) {
            List<SqlGenerationResult.CteStage> stages = queryEngine.getCteStages();
            if (stages == null || stages.isEmpty()) {
                // Structured result: stage1 CTE body + outer SELECT are separate
                // so ComposePlanner can flatten them as sibling CTEs.
                stages = List.of(
                        new SqlGenerationResult.CteStage(
                                queryEngine.getCteStage1Alias(),
                                queryEngine.getCteStage1Sql(),
                                queryEngine.getCteStage1Params())
                );
            }
            List<Object> outerParams = queryEngine.getCteOuterSelectParams() == null
                    ? Collections.emptyList()
                    : queryEngine.getCteOuterSelectParams();
            return new SqlGenerationResult(
                    queryEngine.getCteOuterSelectSql(),
                    outerParams,
                    queryEngine,
                    stages);
        }
        return new SqlGenerationResult(queryEngine.getSql(), queryEngine.getValues(), queryEngine);
    }

    /**
     * 准备受管关系代数
     *
     * <p>该阶段会构建基础 SQL，并执行允许在 PREPARE_MANAGED_RELATION 阶段运行的
     * beforeExecute steps（例如物理列权限校验、预聚合重写）。
     * 不会执行数据库查询。返回被加工好的 ManagedSqlRelation（含 capability metadata）。</p>
     *
     * @param systemBundlesContext 系统上下文
     * @param context              查询上下文
     * @param options              准备选项
     * @return 准备好的受管关系代数
     */
    public com.foggyframework.dataset.db.model.plugins.query_execution.ManagedSqlRelation prepareManagedRelation(
            SystemBundlesContext systemBundlesContext, ModelResultContext context, 
            com.foggyframework.dataset.db.model.plugins.query_execution.ManagedRelationOptions options) {
        
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(this, sqlFormulaService);
        queryEngine.analysisQueryRequest(systemBundlesContext, context);

        QueryExecutionContext execCtx = createExecutionContext(systemBundlesContext, context, queryEngine);
        execCtx.setManagedRelationOptions(options);

        if (queryExecutionStepExecutor != null && queryExecutionStepExecutor.hasSteps()) {
            queryExecutionStepExecutor.executeBeforeExecute(com.foggyframework.dataset.db.model.plugins.query_execution.QueryExecutionPhase.PREPARE_MANAGED_RELATION, execCtx);
        }

        // ===== Dialect capability validation =====
        FDialect dialect = getDialect();
        if (options != null && options.getRequiredDialectCapabilities() != null) {
            for (com.foggyframework.dataset.db.model.plugins.query_execution.ManagedRelationOptions.DialectCapability cap 
                    : options.getRequiredDialectCapabilities()) {
                switch (cap) {
                    case CTE:
                        if (!dialect.supportsCte()) {
                            throw new UnsupportedOperationException(
                                "Dialect " + dialect.getProductName() + " does not support CTE, required by purpose: " +
                                (options.getPurpose() != null ? options.getPurpose() : "unknown"));
                        }
                        break;
                    case WINDOW_FUNCTION:
                        if (!dialect.supportsWindowFunctions()) {
                            throw new UnsupportedOperationException(
                                "Dialect " + dialect.getProductName() + " does not support Window Functions, required by purpose: " +
                                (options.getPurpose() != null ? options.getPurpose() : "unknown"));
                        }
                        break;
                }
            }
        }

        // ===== Capability metadata =====
        // permissionValidated: default false (fail-closed). PhysicalColumnPermissionStep
        // sets "permissionValidated" = true when it completes successfully.
        boolean permissionValidated = Boolean.TRUE.equals(execCtx.getExtData("permissionValidated"));
        // preAggUsed: PreAggRewriteStep stores the preAgg name as a String, not Boolean
        boolean preAggApplied = execCtx.getExtData("preAggUsed") != null;
        boolean wrappable = permissionValidated && dialect.supportsCte();

        // Build metric metadata from queryModel measures
        java.util.List<com.foggyframework.dataset.db.model.plugins.query_execution.ManagedMetricMetadata> metricMetadataList =
                buildMetricMetadata(context);

        // 注意：prepare 阶段不短路执行，即使缓存命中也会继续，除非外层决定使用它。
        return new com.foggyframework.dataset.db.model.plugins.query_execution.ManagedSqlRelation(
                execCtx.getSql(),
                execCtx.getParams(),
                dialect,
                queryEngine,
                execCtx,
                wrappable,
                permissionValidated,
                preAggApplied,
                metricMetadataList
        );
    }

    /**
     * 从 queryModel 度量元数据构建 ManagedMetricMetadata 列表
     */
    private java.util.List<com.foggyframework.dataset.db.model.plugins.query_execution.ManagedMetricMetadata> buildMetricMetadata(
            ModelResultContext context) {
        java.util.List<com.foggyframework.dataset.db.model.plugins.query_execution.ManagedMetricMetadata> result = new java.util.ArrayList<>();
        // Iterate over all measures in the queryModel
        for (TableModel tm : getJdbcModelList()) {
            if (tm.getMeasures() == null) continue;
            for (DbMeasure measure : tm.getMeasures()) {
                DbAggregation agg = measure.getAggregation();
                com.foggyframework.dataset.db.model.plugins.query_execution.AdditiveKind kind;
                String aggFunc;
                if (agg == null || agg == DbAggregation.NONE || agg == DbAggregation.PK) {
                    kind = com.foggyframework.dataset.db.model.plugins.query_execution.AdditiveKind.UNKNOWN;
                    aggFunc = "SUM";
                } else {
                    switch (agg) {
                        case SUM:
                        case COUNT:
                        case MIN:
                        case MAX:
                            kind = com.foggyframework.dataset.db.model.plugins.query_execution.AdditiveKind.ADDITIVE;
                            break;
                        case AVG:
                        case COUNT_DISTINCT:
                            kind = com.foggyframework.dataset.db.model.plugins.query_execution.AdditiveKind.NON_ADDITIVE;
                            break;
                        default:
                            kind = com.foggyframework.dataset.db.model.plugins.query_execution.AdditiveKind.UNKNOWN;
                            break;
                    }
                    aggFunc = agg.name();
                }
                result.add(com.foggyframework.dataset.db.model.plugins.query_execution.ManagedMetricMetadata.builder()
                        .metricName(measure.getName())
                        .additiveKind(kind)
                        .aggregationFunction(aggFunc)
                        .build());
            }
        }
        return result;
    }

    /**
     * 执行受管关系代数
     *
     * <p>外层将 ManagedSqlRelation 包装成新的 SQL 后，调用此方法真正执行数据库查询。
     * 将会触发执行后的错误脱敏、数据格式化，以及 EXECUTE_MANAGED_RELATION 阶段的 afterExecute steps。</p>
     *
     * @param executionContext 之前准备阶段的上下文
     * @param finalSql         外层包装后的最终 SQL
     * @param finalParams      外层包装后的最终参数
     * @return 查询结果
     */
    public DbQueryResult executeManagedRelation(QueryExecutionContext executionContext, String finalSql, List<Object> finalParams) {
        executionContext.setSql(finalSql);
        executionContext.setParams(finalParams);
        PagingRequest<DbQueryRequestDef> form = executionContext.getModelResultContext().getRequest();
        JdbcModelQueryEngine queryEngine = executionContext.getQueryEngine();

        // 重新生成分页 SQL，因为最终 SQL 已被外层替换
        String pagingSql = getDialect().generatePagingSql(finalSql, form.getStart(), form.getLimit());
        executionContext.setPagingSql(pagingSql);

        if (executionContext.isSkipExecution() && executionContext.getCachedResult() != null) {
            return DbQueryResult.of(executionContext.getCachedResult(), queryEngine);
        }

        PagingResultImpl result;
        try {
            result = executeSql(executionContext, form, queryEngine);
        } catch (RuntimeException e) {
            String sanitized = QueryErrorSanitizer.sanitize(e.getMessage(), this);
            if (sanitized != null && !sanitized.isEmpty() && !sanitized.equals(e.getMessage())) {
                throw new SanitizedQueryExecutionException(sanitized, e);
            }
            throw e;
        }
        executionContext.setExecutionResult(result);

        if (queryExecutionStepExecutor != null && queryExecutionStepExecutor.hasSteps()) {
            queryExecutionStepExecutor.executeAfterExecute(com.foggyframework.dataset.db.model.plugins.query_execution.QueryExecutionPhase.EXECUTE_MANAGED_RELATION, executionContext);
        }

        return DbQueryResult.of(result, queryEngine);
    }

    /**
     * 创建查询执行上下文
     */
    @SuppressWarnings("unchecked")
    private QueryExecutionContext createExecutionContext(SystemBundlesContext systemBundlesContext,
                                                          ModelResultContext context,
                                                          JdbcModelQueryEngine queryEngine) {
        PagingRequest<DbQueryRequestDef> form = context.getRequest();

        QueryExecutionContext execCtx = new QueryExecutionContext();
        execCtx.setSystemBundlesContext(systemBundlesContext);
        execCtx.setModelResultContext(context);
        execCtx.setQueryEngine(queryEngine);
        execCtx.setDataSource(dataSource);
        execCtx.setModelName(getName());

        // 初始 SQL 和参数（可能被 Step 修改）
        execCtx.setSql(queryEngine.getSql());
        execCtx.setParams(queryEngine.getValues());

        // 生成分页 SQL
        String pagingSql = DbUtils.getDialect(dataSource).generatePagingSql(
                execCtx.getSql(), form.getStart(), form.getLimit());
        execCtx.setPagingSql(pagingSql);

        return execCtx;
    }

    /**
     * 执行 SQL 查询
     */
    private PagingResultImpl executeSql(QueryExecutionContext execCtx,
                                         PagingRequest<DbQueryRequestDef> form,
                                         JdbcModelQueryEngine queryEngine) {
        // 重新生成分页 SQL（因为 SQL 可能被 Step 修改）
        String pagingSql = DbUtils.getDialect(dataSource).generatePagingSql(
                execCtx.getSql(), form.getStart(), form.getLimit());
        execCtx.setPagingSql(pagingSql);

        // 记录 SQL 日志（明细查询）
        if (sqlLoggingInterceptor != null) {
            sqlLoggingInterceptor.logSql(pagingSql, execCtx.getParams());
        }

        long startTime = System.currentTimeMillis();

        List items;
        if (form.getLimit() < 0) {
            // 前端传了小于0的值，意味着不需要查明细
            items = Collections.EMPTY_LIST;
        } else {
            List<Object> params = execCtx.getParams();
            items = DataSourceQueryUtils.getDatasetTemplate(dataSource).getTemplate()
                    .queryForList(pagingSql, params.toArray(new Object[0]));
        }

        // 记录执行时间
        long duration = System.currentTimeMillis() - startTime;
        execCtx.setExecutionTimeMs(duration);

        if (sqlLoggingInterceptor != null && form.getLimit() >= 0) {
            sqlLoggingInterceptor.logExecutionTime(this.getName(), duration);
        }

        // 对 items 中的数据进行格式化
        formatItems(items, queryEngine);

        // 查询汇总数据
        Map<String, Object> totalData = null;
        int total = 0;
        if (form.getParam().isReturnTotal()) {
            totalData = queryTotalData(execCtx, queryEngine);
            Number it = (Number) totalData.get("total");
            if (it != null) {
                total = it.intValue();
                totalData.put("total", total);
            }
        }

        return PagingResultImpl.of(items, form.getStart(), form.getLimit(), totalData, total);
    }

    /**
     * 格式化查询结果
     */
    private void formatItems(List items, JdbcModelQueryEngine queryEngine) {
        for (DbColumn column : queryEngine.getJdbcQuery().getSelect().getColumns()) {
            if (column instanceof DbQueryColumn) {
                ObjectTransFormatter<?> ff = ((DbQueryColumn) column).getValueFormatter();
                if (ff != null) {
                    String name = column.getName();
                    for (Object item : items) {
                        if (item instanceof Map) {
                            Map mm = (Map) item;
                            Object v = ff.format(mm.get(name));
                            mm.put(name, v);
                        }
                    }
                }
            }
        }
    }

    /**
     * 查询汇总数据
     * <p>
     * 优先使用预聚合表查询（如果可用），否则使用原始查询。
     * </p>
     */
    private Map<String, Object> queryTotalData(QueryExecutionContext execCtx,
                                                JdbcModelQueryEngine queryEngine) {
        // 检查是否有预聚合聚合 SQL（用于明细查询 + returnTotal 场景）
        String aggSql;
        List<Object> aggParams;
        boolean usingPreAgg = false;

        if (execCtx.getPreAggAggregateSql() != null) {
            // 使用预聚合表查询
            aggSql = execCtx.getPreAggAggregateSql();
            aggParams = execCtx.getPreAggAggregateParams();
            usingPreAgg = true;
            log.info("Using pre-aggregation '{}' for aggregate query (returnTotal)",
                    execCtx.getPreAggAggregatePreAggName());
        } else {
            // 使用原始查询
            aggSql = queryEngine.getAggSql();
            aggParams = queryEngine.getValues();
        }

        // 记录 SQL 日志（汇总查询）
        if (sqlLoggingInterceptor != null) {
            sqlLoggingInterceptor.logSql(aggSql, aggParams);
        }

        long aggStartTime = System.currentTimeMillis();

        Map<String, Object> totalData = DataSourceQueryUtils.getDatasetTemplate(dataSource)
                .queryMapObject1(aggSql, aggParams);

        // 记录执行时间（汇总查询）
        if (sqlLoggingInterceptor != null) {
            long aggDuration = System.currentTimeMillis() - aggStartTime;
            String suffix = usingPreAgg ? " (COUNT/PreAgg)" : " (COUNT)";
            sqlLoggingInterceptor.logExecutionTime(this.getName() + suffix, aggDuration);
        }

        return totalData;
    }


    @Override
    public FDialect getDialect() {
        return DbUtils.getDialect(dataSource);
    }
}
