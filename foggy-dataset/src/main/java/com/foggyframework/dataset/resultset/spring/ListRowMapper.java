package com.foggyframework.dataset.resultset.spring;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 优化从数据库查询返回的列名称
 * 呃，把xx_aa这样的列，转换成xxAa返回
 */
public class ListRowMapper implements RowMapper<List<Object>> {

    public static final ListRowMapper DEFAULT = new ListRowMapper();

    public ListRowMapper() {
    }


    static List<Object> mm(ResultSet resultSet) throws SQLException {
        int c = resultSet.getMetaData().getColumnCount();

        List<Object> oo = new ArrayList<>(c);
        for (int i = 1; i <= c; i++) {

            oo.add(resultSet.getObject(i));
        }
        return oo;
    }

    @Override
    public List<Object> mapRow(ResultSet resultSet, int i) throws SQLException {

        return mm(resultSet);
    }
}
