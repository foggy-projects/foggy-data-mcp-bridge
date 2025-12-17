package com.foggyframework.dataset.db.data.dll;


import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.table.SqlColumn;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;

@Slf4j
public class OnDuplicateKeyBuilder extends RowEditBuilder {


    //    public List<Object> columns;
    InsertBuilder insertBuilder;

//	public OnDuplicateKeyBuilder(SqlTable sqlTable) {
//		super(sqlTable);
//	}

    public OnDuplicateKeyBuilder(InsertBuilder builder) {
        super(builder.sqlTable);
        insertBuilder = builder;
    }

    public String genByConfigs(Map<String, Object> configs) {
        StringBuilder root = new StringBuilder();
        genByConfigs1(root, configs);
        return root.toString();
    }

    public void genByConfigs1(StringBuilder root, Map<String, Object> configs) {

        insertBuilder.genSql(root);

        root.append(" on duplicate key update ");
        Map<String, String> updates = configs==null?null:(Map<String, String>) configs.get("updates");
        if (updates == null) {
            updates = Collections.EMPTY_MAP;
        }
        int s = insertBuilder.columns.size();
        for (IdxSqlColumn idxSqlColumn : insertBuilder.columns) {
            SqlColumn sc = idxSqlColumn.sqlColumn;
            if (sqlTable.isIdColumn(sc)) {
                //-1是为了排队id列，
                s--;
                continue;
            }
            String str = updates.get(sc.getName());
            if (str != null) {
                root.append(sc.getName()).append("=").append(str);
            } else {
                root.append(sc.getName()).append("=").append("values(").append(sc.getName()).append(")");
            }
            if (s != 1) {
                root.append(",");
            }
            s--;
        }
        if (StringUtils.equals(",", root.substring(root.length() - 1))) {
            root.deleteCharAt(root.length() - 1);
        }

    }

}
