package com.foggyframework.dataset.client.proxy.converter.list;

import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.client.proxy.converter.AbstractReturnConverter;
import com.foggyframework.dataset.client.proxy.converter.ReturnConverter;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 用于List<String>等简单类型列表
 *
 * @param <T> 元素类型
 */
public abstract class AbstractListC1ReturnConverter<T> extends AbstractReturnConverter<List<T>> implements ReturnConverter, RowMapper<T> {

    @Override
    public RowMapper getRowMapper(Type genericReturnType) {
        return this;
    }

    @Override
    public T mapRow(ResultSet resultSet, int i) throws SQLException {
        return getFormat().format(resultSet.getObject(1));
    }

    @Override
    public List convertPagingResult(PagingResultImpl pagingResult) {
        return convertList(pagingResult.getStart(), pagingResult.getLimit(), pagingResult.getItems());
    }

    @Override
    public List convertList(int start, int limit, List items) {
        return items;
    }

    protected abstract ObjectTransFormatter<T> getFormat();

    @Override
    public int getDefaultMaxLimit() {
        return 99999;
    }
}
