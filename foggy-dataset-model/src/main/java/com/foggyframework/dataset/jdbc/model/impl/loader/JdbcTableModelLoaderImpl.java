package com.foggyframework.dataset.jdbc.model.impl.loader;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.jdbc.model.def.JdbcModelDef;
import com.foggyframework.dataset.jdbc.model.def.query.JdbcQueryModelDef;
import com.foggyframework.dataset.jdbc.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.jdbc.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.jdbc.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.jdbc.model.impl.LoaderSupport;
import com.foggyframework.dataset.jdbc.model.impl.model.JdbcModelImpl;
import com.foggyframework.dataset.jdbc.model.spi.*;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.List;

@Slf4j
public class JdbcTableModelLoaderImpl extends LoaderSupport implements TableModelLoader, QueryModelBuilder {

    @Resource
    DataSource defaultDataSource;

    @Resource
    SqlFormulaService sqlFormulaService;

    public JdbcTableModelLoaderImpl(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader) {
        super(systemBundlesContext, fileFsscriptLoader);
    }


    @Override
    public JdbcModel load(Fsscript fScript, JdbcModelDef def, Bundle bundle) {
        DataSource dataSource = def.getDataSource() == null ? this.defaultDataSource : def.getDataSource();

        RX.notNull(dataSource, "加载模型时的数据源不得为空");
        RX.notNull(dataSource, "加载模型时的def不得为空");

        String tableName = def.getTableName();
        String viewSql = def.getViewSql();

        JdbcModelImpl jdbcModel = new JdbcModelImpl(dataSource, fScript);
        def.apply(jdbcModel);

        jdbcModel.setQueryObject(loadQueryObject(dataSource, tableName, viewSql, def.getSchema()));


        return jdbcModel;
    }

    @Override
    public String getTypeName() {
        return "jdbc";
    }


    @Override
    public QueryModelSupport build(JdbcQueryModelDef queryModelDef, Fsscript fsscript, List<JdbcModel> jdbcModelDxList) {
        JdbcModelImpl mainTm = jdbcModelDxList.get(0).getDecorate(JdbcModelImpl.class);
        if (mainTm == null) {
            //非mysql模型，不做处理
            return null;
        }
        /**
         * 检查，必须都是jdbc模型
         */
        for (JdbcModel jdbcModel : jdbcModelDxList) {
            JdbcModelImpl tm = jdbcModel.getDecorate(JdbcModelImpl.class);
            if (tm == null) {
                throw RX.throwB("查询模型%s中只能引用jdbc模型，但%s不是".formatted(queryModelDef.getName(), jdbcModel.getName()));
            }
        }

        DataSource ds = queryModelDef.getDataSource();

        if(ds == null) {
            for (JdbcModel jdbcModel : jdbcModelDxList) {
                JdbcModelImpl tm = jdbcModel.getDecorate(JdbcModelImpl.class);
                if (tm.getDataSource() != null) {
                    if (ds == null) {
                        ds = tm.getDataSource();
                    } else if (ds != tm.getDataSource()) {
                        throw RX.throwAUserTip("不同数据源的TM不能配置在一起");
                    }
                }
            }
        }

        JdbcQueryModelImpl qm = new JdbcQueryModelImpl(jdbcModelDxList,fsscript,sqlFormulaService,ds);
        queryModelDef.apply(qm);
        return qm;
    }
}
