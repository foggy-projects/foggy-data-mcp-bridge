package com.foggyframework.dataset.db.model.impl.loader;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.def.DbModelDef;
import com.foggyframework.dataset.db.model.def.query.DbQueryModelDef;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.engine.query_model.DbQueryModelImpl;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.db.model.impl.LoaderSupport;
import com.foggyframework.dataset.db.model.impl.model.DbTableModelImpl;
import com.foggyframework.dataset.db.model.spi.QueryModelBuilder;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.TableModelLoader;
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
    public TableModel load(Fsscript fScript, DbModelDef def, Bundle bundle) {
        DataSource dataSource = def.getDataSource() == null ? this.defaultDataSource : def.getDataSource();

        RX.notNull(dataSource, "加载模型时的数据源不得为空");
        RX.notNull(dataSource, "加载模型时的def不得为空");

        String tableName = def.getTableName();
        String viewSql = def.getViewSql();

        DbTableModelImpl jdbcModel = new DbTableModelImpl(dataSource, fScript);
        def.apply(jdbcModel);

        jdbcModel.setQueryObject(loadQueryObject(dataSource, tableName, viewSql, def.getSchema()));


        return jdbcModel;
    }

    @Override
    public String getTypeName() {
        return "jdbc";
    }


    @Override
    public QueryModelSupport build(DbQueryModelDef queryModelDef, Fsscript fsscript, List<TableModel> jdbcModelDxList) {
        DbTableModelImpl mainTm = jdbcModelDxList.get(0).getDecorate(DbTableModelImpl.class);
        if (mainTm == null) {
            //非mysql模型，不做处理
            return null;
        }
        /**
         * 检查，必须都是jdbc模型
         */
        for (TableModel jdbcModel : jdbcModelDxList) {
            DbTableModelImpl tm = jdbcModel.getDecorate(DbTableModelImpl.class);
            if (tm == null) {
                throw RX.throwB("查询模型%s中只能引用jdbc模型，但%s不是".formatted(queryModelDef.getName(), jdbcModel.getName()));
            }
        }

        DataSource ds = queryModelDef.getDataSource();

        if(ds == null) {
            for (TableModel jdbcModel : jdbcModelDxList) {
                DbTableModelImpl tm = jdbcModel.getDecorate(DbTableModelImpl.class);
                if (tm.getDataSource() != null) {
                    if (ds == null) {
                        ds = tm.getDataSource();
                    } else if (ds != tm.getDataSource()) {
                        throw RX.throwAUserTip("不同数据源的TM不能配置在一起");
                    }
                }
            }
        }

        DbQueryModelImpl qm = new DbQueryModelImpl(jdbcModelDxList,fsscript,sqlFormulaService,ds);
        queryModelDef.apply(qm);
        return qm;
    }
}
