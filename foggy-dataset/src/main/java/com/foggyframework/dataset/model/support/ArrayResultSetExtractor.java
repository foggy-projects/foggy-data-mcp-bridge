package com.foggyframework.dataset.model.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.resultset.spring.ArrayRowMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.lang.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ArrayResultSetExtractor implements ResultSetExtractor<List<Object[]>> {
public static final ArrayResultSetExtractor DEFAULT = new ArrayResultSetExtractor();

    @Nullable
    @Override
    public List<Object[]> extractData(ResultSet resultSet) throws SQLException, DataAccessException {
        ArrayRowMapper mapper = ArrayRowMapper.DEFAULT;
        List<Object[]> result = new ArrayList<>();

        int i = 0;
        while (resultSet.next()) {
            try {
                Object[] map = mapper.mapRow(resultSet, i);
                result.add(map);
                i++;
            } catch (Throwable e) {
                throw RX.throwB(e.getMessage(), null, e);
            }
        }

        return result;

    }
}
