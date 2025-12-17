package com.foggyframework.dataset.model.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.resultset.spring.JavaColumnNameFixRowMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.lang.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListBeanResultSetExtractor implements ResultSetExtractor<List<Map<String,Object>>> {
    @Nullable
    @Override
    public List<Map<String,Object>> extractData(ResultSet resultSet) throws SQLException, DataAccessException {
        JavaColumnNameFixRowMapper mapper = JavaColumnNameFixRowMapper.build(resultSet);
        List<Map<String,Object>> result = new ArrayList<>();

        int i = 0;
        while (resultSet.next()) {
            try {
                Map<String,Object> map = mapper.mapRow(resultSet, i);
                result.add(map);
                i++;
            } catch (Throwable e) {
                throw RX.throwB(e.getMessage(), null, e);
            }
        }

        return result;

    }
}
