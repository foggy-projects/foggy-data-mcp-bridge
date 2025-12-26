package com.foggyframework.dataset.db.model.impl;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.impl.utils.TableQueryObject;
import com.foggyframework.dataset.db.model.impl.utils.ViewSqlQueryObject;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.utils.DbUtils;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;

import javax.sql.DataSource;
import java.util.List;

public abstract class LoaderSupport {


    protected SystemBundlesContext systemBundlesContext;

    protected FileFsscriptLoader fileFsscriptLoader;

    public LoaderSupport(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader) {
        this.systemBundlesContext = systemBundlesContext;
        this.fileFsscriptLoader = fileFsscriptLoader;
    }

   protected Fsscript findFsscript(String name, String pref){
        if(!name.endsWith(pref)){
            name = name+"."+pref;
        }
       BundleResource br =systemBundlesContext.findResourceByName(name,true);

     return   fileFsscriptLoader.findLoadFsscript(br);

    }


    protected QueryObject loadQueryObject(DataSource dataSource, String tableName, String viewSql, String schema) {
        if (StringUtils.isTrimEmpty(viewSql) && StringUtils.isTrimEmpty(tableName)) {
            throw RX.throwAUserTip(DatasetMessages.modelTablenameRequired());
        }

        FDialect dialect = DbUtils.getDialect(dataSource);

        SqlTable sqlTable = null;
        QueryObject queryObject = null;
        if (StringUtils.isNotTrimEmpty(tableName)) {
            //优先根据表名读取
            sqlTable = dialect.getTableByNameWithSchema(dataSource, tableName, true, schema);

            queryObject = new TableQueryObject(sqlTable, schema);
        } else {
            //使用SQL
            List<SqlColumn> sqlColumnList = dialect.getColumnsBySql(dataSource, viewSql);

            sqlTable = new SqlTable();
            sqlTable.setSqlColumns(sqlColumnList);
            queryObject = new ViewSqlQueryObject(viewSql, sqlTable);

        }


        return queryObject;

    }
}
