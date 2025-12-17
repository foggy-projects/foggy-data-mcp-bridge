package com.foggyframework.dataset.utils;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.table.SqlColumnType;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.db.table.dll.JdbcUpdater;
import com.foggyframework.dataset.db.table.dll.SqlTableSynchronizer;
import com.foggyframework.fsscript.fun.Iif;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 负责把在fstep文件中定义表结构，转换成数据库的表
 */
@Slf4j
public final class JdbcTableUtils {

    public static final String[] TABLE_KEY = new String[]{"name", "tableName"};
    public static final String ID_COLUMN_KEY = "idColumn";
    public static final String COLUMNS_KEY = "columns";
    public static final String INDEXES_KEY = "indexes";
    public static final String TYPES_KEY = "types";

    private static final TableColumnDef DEFAULT_TABLE_COLUMN_DEF = new TableColumnDef(SqlColumnType.VARCHAR, 190, Collections.EMPTY_LIST);

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TableColumnDef {
        SqlColumnType type;

        Integer length;

        List<Object> columns;

        public static TableColumnDef readFromMap(Map typeDef) {
            String type = (String) typeDef.get("type");

            SqlColumnType ct = SqlColumnType.VARCHAR;
            if (!StringUtils.isEmpty(type)) {
                ct = SqlColumnType.valueOf(type.toUpperCase());
            }
            Integer length = (Integer) typeDef.get("length");
            List<Object> columns = (List<Object>) typeDef.get("columns");
            if (columns == null) {
                columns = Collections.EMPTY_LIST;
            }
            return new TableColumnDef(ct, length, columns);
        }

    }

    /**
     * tableDef结构定义见
     * https://www.yuque.com/alipaycxksmxu35q/yb12um/sgye0x
     */
    public static SqlTable createOrUpdateTable(DataSource dataSource, Object tableDef) throws SQLException {
        Assert.notNull(dataSource, "dataSource不能为空");
        if (tableDef instanceof String) {
            //呃，只传了表名，说明是脚本编写者自己定义了表结构，所以我们只需要根据表名，从数据库读取即可
            String tableName = (String) tableDef;
            log.debug("未定义表结构，根据传入的表名: " + tableName + " 从数据库中加载表信息");
            return DbUtils.getTableByName(dataSource, (String) tableDef, true);
        }

        if (!(tableDef instanceof Map)) {
            throw RX.throwB("tableDef必须是Map对象: " + tableDef);
        }

        Map<String, Object> tableDefMap = (Map<String, Object>) tableDef;
        SqlTableBuilder tableBuilder = genTableBuilder(tableDefMap);
        SqlTable sqlTable = tableBuilder.buildSqlTable();

        log.debug("准备生成表");
        new SqlTableSynchronizer(sqlTable).syncStructure(dataSource);
        log.debug("准备索引");

        JdbcUpdater jdbcUpdater = new JdbcUpdater(dataSource);
        for (SqlColumnBuilder columnBuild : tableBuilder.getColumnBuilders()) {
            if (columnBuild.index) {
                jdbcUpdater.addIndex(sqlTable, sqlTable.getSqlColumn(columnBuild.getName(), true));
            }
        }
        jdbcUpdater.execute(JdbcUpdater.MODE_SKIP_ERROR);

        return sqlTable;
    }

    public static SqlTableBuilder genTableBuilder(Map<String, Object> tableDefMap) {
//读取表结构
        SqlTableBuilder tableBuilder = readSqlTableBuild(tableDefMap);
        log.debug("根据etl脚本中的定义，准备创建或更新表: " + tableBuilder.getName());

        List<SqlColumnBuilder> columnBuilds = new ArrayList<>();
        tableBuilder.setColumnBuilders(columnBuilds);
        //处理ID列
        SqlColumnBuilder idColumnBuild = tableDefMap.get(ID_COLUMN_KEY) == null ? null : readSqlColumnBuild(tableBuilder, tableDefMap.get(ID_COLUMN_KEY), DEFAULT_TABLE_COLUMN_DEF);
        if (idColumnBuild != null) {
            log.debug("加载" + tableBuilder + "的ID列: " + idColumnBuild);
            tableBuilder.setIdColumnBuilder(idColumnBuild);
        } else {
            log.debug(tableBuilder + "未定义ID列");
        }

        List<Object> columns = (List<Object>) tableDefMap.get(COLUMNS_KEY);
        if (columns == null) {
            throw RX.throwB("未定义字段: " + COLUMNS_KEY);
        }
        for (Object column : columns) {
            if (column == null) {
                continue;
            }
            SqlColumnBuilder build = readSqlColumnBuild(tableBuilder, column, DEFAULT_TABLE_COLUMN_DEF);
            if(idColumnBuild!=null && StringUtils.equalsIgnoreCase(idColumnBuild.getName(),build.getName())){
                //在columns中重复定义了id列，我们忽略它
                continue;
            }
            columnBuilds.add(build);
        }
        //2022-04-29 加入types支持，见https://www.yuque.com/alipaycxksmxu35q/yb12um/sgye0x
        List<Map<String, Object>> types = (List<Map<String, Object>>) tableDefMap.get(TYPES_KEY);
        if (types != null) {
            for (Map<String, Object> type : types) {
                TableColumnDef def = TableColumnDef.readFromMap(type);
                for (Object column : def.columns) {
                    if (column == null) {
                        continue;
                    }
                    readSqlColumnBuild(tableBuilder, column, def);
                }
            }
        }
        //2022-04-29 加入indexes支持，见https://www.yuque.com/alipaycxksmxu35q/yb12um/sgye0x
        List<String> indexes = (List<String>) tableDefMap.get(INDEXES_KEY);
        if (indexes != null) {
            for (String index : indexes) {
                SqlColumnBuilder column = tableBuilder.getColumnBuilderByName(index);
                Assert.notNull(column, "在indexes中定义了列【" + index + "】的索引，但并未实际定义该列");
                column.setIndex(true);
            }

        }
        return tableBuilder;
    }


    static SqlTableBuilder readSqlTableBuild(Map tableDef) {
        String name = null;
        for (String s : TABLE_KEY) {
            name = (String) ((Map) tableDef).get(s);
            if (StringUtils.isNotEmpty(name)) {
                break;
            }
        }
        if (StringUtils.isEmpty(name)) {
            throw RX.throwB("未定义表名: " + TABLE_KEY[0]);
        }
        SqlTable sqlTable = new SqlTable(name);
        return new SqlTableBuilder(name, null, null);

    }

    static SqlColumnBuilder readSqlColumnBuild(SqlTableBuilder sqlTableBuilder, Object columnDef, TableColumnDef tableColumnDef) {
        if (columnDef == null) {
            throw RX.throwB("列定义不能为空" + sqlTableBuilder.getName());
        }
        Integer length = null;
        SqlColumnType type = null;
        String name;
        boolean index;
        String defaultValue = null;
        SqlColumnBuilder sqlColumnBuild = null;
        if (columnDef instanceof String) {
            index = false;
            name = (String) columnDef;
        } else if (columnDef instanceof Map) {
            Map mm = (Map) columnDef;
            index = Iif.check(mm.get("index"));
            name = (String) mm.get("name");
            length = (Integer) mm.get("length");
            String strType = (String) mm.get("type");
            Object defaultValue1 = mm.get("defaultValue");
            defaultValue = defaultValue1 == null ? null : defaultValue1.toString();
            if (!StringUtils.isEmpty(strType)) {
                type = SqlColumnType.valueOf(strType.toUpperCase());
            }
        } else {
            throw new UnsupportedOperationException("不支持的columnDef:" + columnDef);
        }
        if (length == null && type == SqlColumnType.VARCHAR && tableColumnDef.length != null) {
            length = tableColumnDef.length;
        }
        sqlColumnBuild = sqlTableBuilder.getColumnBuilderByName(name);

        if (sqlColumnBuild == null) {
            if (type == null) {
                //在 sqlColumnBuild 不存在的情况下，如果未定义type，则使用默认的。
                type = tableColumnDef.type;
            }
            if (length == null) {
                //在 sqlColumnBuild 不存在的情况下，如果未定义length，则使用默认的。
                length = tableColumnDef.length;
            }
            sqlColumnBuild = new SqlColumnBuilder(length, type, name, index, defaultValue);
        } else {
            //更新定义
            if (length == null) {
                //未定义length，尝试使用tableColumnDef中定义的
                length = tableColumnDef.length;
            }

            if (length != null) {
                //如果tableColumnDef中定义length，则使用它
                sqlColumnBuild.setLength(length);
            }

            if (type == null) {
                //如果未定义type，则使用它sqlColumnBuild中的
                type = tableColumnDef.type;
            }
            if (type != null) {
                sqlColumnBuild.setType(type);
            }

            if (index) {
                sqlColumnBuild.setIndex(index);
            }
            sqlColumnBuild.setDefaultValue(defaultValue);
        }


        return sqlColumnBuild;
    }


    public static SqlTable createOrUpdateSqlTable(DataSource dataSource, SqlTable sqlTable) throws SQLException {
        Assert.notNull(dataSource, "dataSource不能为空");
        log.debug("准备生成表");
        new SqlTableSynchronizer(sqlTable).syncStructure(dataSource);
        log.debug("准备索引");

        JdbcUpdater jdbcUpdater = new JdbcUpdater(dataSource);
//        for (SqlColumnBuilder columnBuild : tableBuilder.getColumnBuilders()) {
//            if (columnBuild.index) {
//                jdbcUpdater.addIndex(sqlTable, sqlTable.getSqlColumn(columnBuild.getName(), true));
//            }
//        }
        jdbcUpdater.execute(JdbcUpdater.MODE_SKIP_ERROR);
        return sqlTable;
    }
}
