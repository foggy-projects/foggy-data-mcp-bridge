package com.foggyframework.dataset.jdbc.model.engine.query_model;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.jdbc.model.def.query.request.JdbcQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.jdbc.model.engine.expression.SqlCalculatedFieldProcessor;
import com.foggyframework.dataset.jdbc.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQueryResult;
import com.foggyframework.dataset.jdbc.model.impl.model.JdbcModelSupport;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.jdbc.model.spi.*;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.utils.DataSourceQueryUtils;
import com.foggyframework.dataset.utils.DbUtils;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.*;

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

    public JdbcQueryModelImpl(List<JdbcModel> jdbcModelList, Fsscript fsscript, SqlFormulaService sqlFormulaService, DataSource dataSource) {
        super(jdbcModelList, fsscript);
        this.jdbcModel = jdbcModelList.get(0);
        this.sqlFormulaService = sqlFormulaService;
        this.dataSource = dataSource;
        this.fsscript = fsscript;
        this.jdbcModelList = jdbcModelList;
        for (JdbcModel model : jdbcModelList) {
            Object key = model.getQueryObject();
//            if(name2Alias.containsKey(key)){
//                throw new UnsupportedOperationException();
//            }
            //呃,临时 方案,确保下面的public String getAlias(QueryObject queryObject)能够得到正确的alias
            name2Alias.put(key, model.getAlias());
            name2Alias.put(model.getQueryObject().getDecorate(JdbcModelSupport.ModelQueryObject.class), model.getAlias());
        }
    }

    @Override
    public CalculatedFieldProcessor getCalculatedFieldProcessor() {
        if (calculatedFieldProcessor == null) {
            calculatedFieldProcessor = new SqlCalculatedFieldProcessor(this, getDialect());
        }
        return calculatedFieldProcessor;
    }


    @Override
    public JdbcQueryResult query(SystemBundlesContext systemBundlesContext, PagingRequest<JdbcQueryRequestDef> form) {
        // 创建新的上下文
        ModelResultContext context = new ModelResultContext(form, null);
        return query(systemBundlesContext, context);
    }

    @Override
    public JdbcQueryResult query(SystemBundlesContext systemBundlesContext, ModelResultContext context) {

        return queryJdbc(systemBundlesContext, context);
    }

    /**
     * 执行 JDBC 查询
     *
     * @param systemBundlesContext 系统上下文
     * @param context              查询上下文（可能已预处理）
     * @return 查询结果
     */
    public JdbcQueryResult queryJdbc(SystemBundlesContext systemBundlesContext, ModelResultContext context) {
        PagingRequest<JdbcQueryRequestDef> form = context.getRequest();
        JdbcQueryRequestDef queryRequest = form.getParam();

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(this, sqlFormulaService);

        /**
         * 构建 查询语句
         */
        queryEngine.analysisQueryRequest(systemBundlesContext, context);

        String pagingSql = DbUtils.getDialect(dataSource).generatePagingSql(queryEngine.getSql(), form.getStart(), form.getLimit());

        List items;
        if (form.getLimit() < 0) {
            //前端传了小于0的值，意味着不需要查明细~
            items = Collections.EMPTY_LIST;
        } else {
            items = DataSourceQueryUtils.getDatasetTemplate(dataSource).getTemplate().queryForList(pagingSql, queryEngine.getValues().toArray(new Object[0]));
        }

        //对items中的数据进行格式化
        for (JdbcColumn column : queryEngine.getJdbcQuery().getSelect().getColumns()) {
//            log.warn("1");
            if (column instanceof JdbcQueryColumn) {
                ObjectTransFormatter<?> ff = ((JdbcQueryColumn) column).getValueFormatter();
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
            totalData = DataSourceQueryUtils.getDatasetTemplate(dataSource).queryMapObject1(queryEngine.getAggSql(), queryEngine.getValues());
            Number it = (Number) totalData.get("total");
            if (it != null) {
                total = it.intValue();
                totalData.put("total", total);
            }
        }
        return JdbcQueryResult.of(PagingResultImpl.of(items, form.getStart(), form.getLimit(), totalData, total), queryEngine);
    }


    @Override
    public FDialect getDialect() {
        return DbUtils.getDialect(dataSource);
    }
}
