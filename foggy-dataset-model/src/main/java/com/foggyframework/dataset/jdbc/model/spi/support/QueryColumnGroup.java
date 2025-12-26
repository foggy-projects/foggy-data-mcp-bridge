package com.foggyframework.dataset.jdbc.model.spi.support;

import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryColumn;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QueryColumnGroup {

    String caption;

    List<JdbcQueryColumn> items=new ArrayList<>();

    public void addJdbcColumn(JdbcQueryColumn jdbcColumn) {
        items.add(jdbcColumn);
    }
}
