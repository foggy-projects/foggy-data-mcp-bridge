package com.foggyframework.dataset.model.support;

import com.foggyframework.core.ex.RX;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MapperBeanResultSetExtractor<T> implements ResultSetExtractor<List<T>> {
    public MapperBeanResultSetExtractor(RowMapper<T> mapper) {
        this.mapper = mapper;
    }

    RowMapper<T> mapper;

    @Nullable
    @Override
    public List<T> extractData(ResultSet resultSet) throws SQLException, DataAccessException {
        List<T> result = new ArrayList<>();

        int i = 0;
        while (resultSet.next()) {
            try {
                T map = mapper.mapRow(resultSet, i);
                result.add(map);
                i++;
            } catch (Throwable e) {
                throw RX.throwB(e.getMessage(), null, e);
            }
        }

        return result;

    }
}
