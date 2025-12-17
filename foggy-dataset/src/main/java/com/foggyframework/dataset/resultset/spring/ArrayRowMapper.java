package com.foggyframework.dataset.resultset.spring;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 优化从数据库查询返回的列名称
 * 呃，把xx_aa这样的列，转换成xxAa返回
 */
public class ArrayRowMapper implements RowMapper<Object[]> {

    public static final ArrayRowMapper DEFAULT = new ArrayRowMapper();

    public ArrayRowMapper() {
    }


    static Object[] mm(ResultSet resultSet) throws SQLException {
        int c = resultSet.getMetaData().getColumnCount();

        Object[] oo = new Object[c];
        for (int i = 1; i <= c; i++) {

            oo[i - 1] = resultSet.getObject(i);
        }
        return oo;
    }

    @Override
    public Object[] mapRow(ResultSet resultSet, int i) throws SQLException {

        return mm(resultSet);
    }
}
