package com.foggyframework.dataset.client.proxy.converter;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.springframework.util.Assert;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class SimpleObjectReturnConverter<T> extends AbstractReturnConverter<T> implements ReturnConverter {

    public SimpleObjectReturnConverter(ObjectTransFormatter formatter) {
        Assert.notNull(formatter, "SimpleObjectReturnConverter的formatter不得为空~~");
        this.formatter = formatter;
    }

    ObjectTransFormatter formatter;

    @Override
    public T convertPagingResult(PagingResultImpl pagingResult) {
        return convertList(pagingResult.getStart(), pagingResult.getLimit(), pagingResult.getItems());
    }

    @Override
    public T convertList(int start, int limit, List items) {
        if (items.isEmpty()) {
            return null;
        }
        if (items.size() > 1) {
            throw RX.throwB("查询期望返回空或一条数据，但实际返回的多于一条");
        }
        Map obj = (Map) items.get(0);

        return (T) convertClass2Map(null, obj);
    }

    public Object convertClass2Map(Class cls, Map<String, Object> queryMap) {
        if (queryMap.size() > 1) {
            throw RX.throwB("简单类型只允许返回一个字段！即select结果集，只允许有一列");
        }
        return formatter.format(queryMap.values().iterator().next());
    }

    @Override
    public Class getBeanClazz(Type genericReturnType) {
        return null;
    }

    @Override
    public int getDefaultMaxLimit() {
        return 2;
    }
}
