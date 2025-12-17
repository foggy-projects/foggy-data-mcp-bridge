package com.foggyframework.dataset.db.table;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuerySqlTable {

    SqlTable sqlTable;

    JdbcTemplate jdbcTemplate;

    public Map<String, Object> getObject(String id) {
        return sqlTable.getObject(jdbcTemplate, id);
    }

    public List<Map<String, Object>> queryForList(Map<String, Object> config) {

        return sqlTable.queryForList(jdbcTemplate, config);
    }

    public Map<String, Object> queryForMap(Map<String, Object> config) {

        return sqlTable.queryForMap(jdbcTemplate, config);
    }
}
