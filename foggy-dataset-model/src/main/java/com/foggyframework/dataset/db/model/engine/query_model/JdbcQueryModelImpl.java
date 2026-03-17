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
        PagingResultImpl result = executeSql(execCtx, form, queryEngine);
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
        return new SqlGenerationResult(queryEngine.getSql(), queryEngine.getValues(), queryEngine);
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
