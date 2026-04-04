package com.foggyframework.dataset.db.model.impl;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.impl.utils.TableQueryObject;
import com.foggyframework.dataset.db.model.impl.utils.ViewSqlQueryObject;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
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

    /**
     * 在指定命名空间下查找FSScript文件
     *
     * @param name 文件名
     * @param pref 后缀（如"tm"、"qm"）
     * @param namespace 命名空间（空字符串或null表示默认命名空间）
     * @return Fsscript对象
     */
    protected Fsscript findFsscript(String name, String pref, String namespace){
        if(!name.endsWith(pref)){
            name = name+"."+pref;
        }
        BundleResource br = systemBundlesContext.findResourceByName(name, namespace, true);

        return fileFsscriptLoader.findLoadFsscript(br);
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
            try {
                sqlTable = dialect.getTableByNameWithSchema(dataSource, tableName, true, schema);
            } catch (Exception e) {
                throw RX.throwAUserTip(String.format(
                        "表 '%s' 加载失败（schema=%s）: %s。请检查该表是否存在于目标数据源中，或对应模块是否已安装。",
                        tableName, schema != null ? schema : "default", e.getMessage()));
            }

            if (sqlTable == null || sqlTable.getSqlColumns() == null || sqlTable.getSqlColumns().isEmpty()) {
                throw RX.throwAUserTip(String.format(
                        "表 '%s' 在数据源中不存在或无列信息（schema=%s）。请检查该表是否存在，或对应模块是否已安装。",
                        tableName, schema != null ? schema : "default"));
            }

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
