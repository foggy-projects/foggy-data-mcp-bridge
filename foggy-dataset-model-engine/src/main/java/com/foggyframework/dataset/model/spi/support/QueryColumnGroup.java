package com.foggyframework.dataset.model.spi.support;

import com.foggyframework.dataset.model.spi.DbQueryColumn;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QueryColumnGroup {

    String caption;

    List<DbQueryColumn> items=new ArrayList<>();

    public void addJdbcColumn(DbQueryColumn jdbcColumn) {
        items.add(jdbcColumn);
    }
}
