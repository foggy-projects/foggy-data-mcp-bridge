package com.foggyframework.dataset.db.model.impl.utils;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.table.SqlTable;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public  class TableQueryObject extends QueryObjectSupport {
    String tableName;

    String body;

    protected String schema;

    public TableQueryObject(SqlTable sqlTable,String schema) {
        super(sqlTable);
        tableName  = sqlTable.getName();
        this.schema = schema;
        this.body = buildBody();
    }

    @Override
    public String getBody() {
        return body;
    }



    private String buildBody() {
        if (StringUtils.isEmpty(getSchema())) {
            return tableName;
        }
        return "`" + getSchema() + "`." + tableName;
    }

    
}
