package com.foggyframework.dataset.resultset.spring;

import com.foggyframework.core.utils.StringUtils;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 优化从数据库查询返回的列名称
 * 呃，把xx_aa这样的列，转换成xxAa返回
 */
public class JavaColumnNameFixRowMapper implements RowMapper<Map<String, Object>> {

    Map<String, String> nameMap;

    public JavaColumnNameFixRowMapper() {
    }

    public JavaColumnNameFixRowMapper(Map<String, String> nameMap) {
        this.nameMap = nameMap;
    }

    public static JavaColumnNameFixRowMapper build(ResultSet resultSet) {
        try {

            return new JavaColumnNameFixRowMapper(mm(resultSet));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    static Map<String, String> mm(ResultSet resultSet) throws SQLException {
        int c = resultSet.getMetaData().getColumnCount();

        Map<String, String> nameMap = new HashMap<>();

        for (int i = 1; i <= c; i++) {
            String name = resultSet.getMetaData().getColumnLabel(i);
            nameMap.put(name, name);
            if (name.indexOf("_") > 0) {
                nameMap.put(name, StringUtils.to(name));
            }
        }
        return nameMap;
    }

    @Override
    public Map<String, Object> mapRow(ResultSet resultSet, int i) throws SQLException {
        if (nameMap == null) {
            nameMap = mm(resultSet);
        }
        Map<String, Object> map = new HashMap<>();

        for (Map.Entry<String, String> en : nameMap.entrySet()) {
            map.put(en.getValue(), resultSet.getObject(en.getKey()));
        }

        return map;
    }
}
