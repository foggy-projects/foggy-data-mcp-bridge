package com.foggyframework.dataset.db.model.engine.query_model;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.expression.SqlCalculatedFieldProcessor;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.impl.model.TableModelSupport;
import com.foggyframework.dataset.db.model.interceptor.SqlLoggingInterceptor;
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
     * 支持 L2 缓存（SQL 级别）：在 SQL 生成后、执行前检查缓存。
     * </p>
     *
     * @param systemBundlesContext 系统上下文
     * @param context              查询上下文（可能已预处理）
     * @return 查询结果
     */
    public DbQueryResult queryJdbc(SystemBundlesContext systemBundlesContext, ModelResultContext context) {
        PagingRequest<DbQueryRequestDef> form = context.getRequest();
        DbQueryRequestDef queryRequest = form.getParam();

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(this, sqlFormulaService);

        /**
         * 构建查询语句（包含权限条件注入）
         */
        queryEngine.analysisQueryRequest(systemBundlesContext, context);

        String sql = queryEngine.getSql();
        List<?> params = queryEngine.getValues();
        String pagingSql = DbUtils.getDialect(dataSource).generatePagingSql(sql, form.getStart(), form.getLimit());

        // 【L2 缓存检查】在 SQL 生成后、执行前
        QueryCacheProvider cacheProvider = getQueryCacheProvider(context);
        boolean l2Enabled = QueryCacheProvider.isL2Enabled(context);

        if (l2Enabled && cacheProvider != null) {
            // 使用完整的分页 SQL 作为缓存键的一部分
            PagingResultImpl cached = cacheProvider.checkL2Cache(getName(), pagingSql, params, context);
            if (cached != null) {
                if (log.isDebugEnabled()) {
                    log.debug("L2 cache HIT for model={}", getName());
                }
                QueryCacheProvider.markL2Hit(context);
                return DbQueryResult.of(cached, queryEngine);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("L2 cache MISS for model={}", getName());
                }
            }
        }

        // 记录 SQL 日志（明细查询）
        if (sqlLoggingInterceptor != null) {
            sqlLoggingInterceptor.logSql(pagingSql, queryEngine.getValues());
        }

        long startTime = System.currentTimeMillis();

        List items;
        if (form.getLimit() < 0) {
            //前端传了小于0的值，意味着不需要查明细~
            items = Collections.EMPTY_LIST;
        } else {
            items = DataSourceQueryUtils.getDatasetTemplate(dataSource).getTemplate().queryForList(pagingSql, queryEngine.getValues().toArray(new Object[0]));
        }

        // 记录执行时间（明细查询）
        if (sqlLoggingInterceptor != null && form.getLimit() >= 0) {
            long duration = System.currentTimeMillis() - startTime;
            sqlLoggingInterceptor.logExecutionTime(this.getName(), duration);
        }

        //对items中的数据进行格式化
        for (DbColumn column : queryEngine.getJdbcQuery().getSelect().getColumns()) {
//            log.warn("1");
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

        /**
         * 查询汇总数据
         */
        Map<String, Object> totalData = null;
        int total = 0;
        if (form.getParam().isReturnTotal()) {
            // 记录 SQL 日志（汇总查询）
            if (sqlLoggingInterceptor != null) {
                sqlLoggingInterceptor.logSql(queryEngine.getAggSql(), queryEngine.getValues());
            }

            long aggStartTime = System.currentTimeMillis();

            totalData = DataSourceQueryUtils.getDatasetTemplate(dataSource).queryMapObject1(queryEngine.getAggSql(), queryEngine.getValues());

            // 记录执行时间（汇总查询）
            if (sqlLoggingInterceptor != null) {
                long aggDuration = System.currentTimeMillis() - aggStartTime;
                sqlLoggingInterceptor.logExecutionTime(this.getName() + " (COUNT)", aggDuration);
            }

            Number it = (Number) totalData.get("total");
            if (it != null) {
                total = it.intValue();
                totalData.put("total", total);
            }
        }

        PagingResultImpl result = PagingResultImpl.of(items, form.getStart(), form.getLimit(), totalData, total);

        // 【L2 缓存写入】
        if (l2Enabled && cacheProvider != null) {
            boolean l2Hit = context.getCacheConfig() != null && context.getCacheConfig().isL2CacheHit();
            if (!l2Hit) {
                cacheProvider.writeL2Cache(getName(), pagingSql, params, result, context);
                if (log.isDebugEnabled()) {
                    log.debug("L2 cache WRITE for model={}", getName());
                }
            }
        }

        return DbQueryResult.of(result, queryEngine);
    }

    /**
     * 从上下文获取缓存提供者
     */
    private QueryCacheProvider getQueryCacheProvider(ModelResultContext context) {
        if (context == null || context.getExtData() == null) {
            return null;
        }
        Object provider = context.getExtData().get("queryCacheProvider");
        if (provider instanceof QueryCacheProvider) {
            return (QueryCacheProvider) provider;
        }
        return null;
    }


    @Override
    public FDialect getDialect() {
        return DbUtils.getDialect(dataSource);
    }
}
