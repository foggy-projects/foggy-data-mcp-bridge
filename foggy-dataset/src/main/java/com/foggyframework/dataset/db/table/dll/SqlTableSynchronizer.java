package com.foggyframework.dataset.db.table.dll;


import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.utils.DbUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * 用于同步表结构
 * 当传入的table和数据库中的table不一致时，会补上相应的字段
 * 注意，不会删除字段，也不会修改已有字段
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class SqlTableSynchronizer {

    /**
     * 新的表结构
     */
    SqlTable sqlTable;

    /**
     * 同步表结构
     * @param ds
     */
    public SqlTable syncStructure(DataSource ds) throws SQLException {
        SqlTable tableFromDb = DbUtils.getTableByName(ds,sqlTable.getName());

        JdbcUpdater updater = new JdbcUpdater(ds);
        if(tableFromDb == null){
            log.warn(String.format("表【%s】不存在，自动创建。", sqlTable.getName()));
            updater.addCreateScript(sqlTable);
            updater.execute(JdbcUpdater.MODE_NORMAL);
            return sqlTable;
        }else{
            log.warn(String.format("表【%s】已经存在，尝试更新表结构。", sqlTable.getName()));
            updater.addModifyScript(sqlTable);
            updater.execute(JdbcUpdater.MODE_NORMAL);
            return sqlTable;
        }

    }

    /**
     * 同步索引
     * @param ds
     */
    public void syncIndexes(DataSource ds) {

    }

}
