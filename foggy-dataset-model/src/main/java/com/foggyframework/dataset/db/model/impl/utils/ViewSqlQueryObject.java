package com.foggyframework.dataset.db.model.impl.utils;

import com.foggyframework.dataset.db.table.SqlTable;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public  class ViewSqlQueryObject extends QueryObjectSupport {
    String viewSql;

    public ViewSqlQueryObject(String  viewSql,SqlTable sqlTable) {
        super(sqlTable);
        this.viewSql = "("+viewSql+")";
    }

    @Override
    public String getBody() {
        return viewSql;
    }


    
}
