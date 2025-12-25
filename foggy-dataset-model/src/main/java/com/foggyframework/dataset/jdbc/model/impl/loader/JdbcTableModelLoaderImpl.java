package com.foggyframework.dataset.jdbc.model.impl.loader;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.jdbc.model.def.JdbcModelDef;
import com.foggyframework.dataset.jdbc.model.impl.LoaderSupport;
import com.foggyframework.dataset.jdbc.model.impl.model.JdbcModelImpl;
import com.foggyframework.dataset.jdbc.model.spi.*;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
@Slf4j
public class JdbcTableModelLoaderImpl extends LoaderSupport implements TableModelLoader {

    @Resource
    DataSource defaultDataSource;

    public JdbcTableModelLoaderImpl(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader) {
        super(systemBundlesContext, fileFsscriptLoader);
    }


    @Override
    public JdbcModel load( Fsscript fScript, JdbcModelDef def, Bundle bundle) {
        DataSource dataSource = def.getDataSource() == null ? this.defaultDataSource : def.getDataSource();

        RX.notNull(dataSource, "加载模型时的数据源不得为空");
        RX.notNull(dataSource, "加载模型时的def不得为空");

        String tableName = def.getTableName();
        String viewSql = def.getViewSql();

        JdbcModelImpl jdbcModel = new JdbcModelImpl(dataSource,fScript);
        def.apply(jdbcModel);

        jdbcModel.setQueryObject(loadQueryObject(dataSource, tableName, viewSql, def.getSchema()));


        return jdbcModel;
    }

    @Override
    public String getTypeName() {
        return "jdbc";
    }


}
