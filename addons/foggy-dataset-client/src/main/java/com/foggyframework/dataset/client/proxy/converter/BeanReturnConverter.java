package com.foggyframework.dataset.client.proxy.converter;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.AllArgsConstructor;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class BeanReturnConverter<T> extends AbstractReturnConverter<T> implements ReturnConverter {
    Class<T> beanClazz;

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
        Object obj = items.get(0);
        if (beanClazz.isInstance(obj)) {
            return (T) obj;
        } else if (obj instanceof Map) {
            return (T) convertClass2Map((Class) beanClazz, (Map<String, Object>) obj);
        }
        throw RX.throwB("不支持的对象" + obj);
    }

    @Override
    public Class getBeanClazz(Type genericReturnType) {
        return beanClazz;
    }

    @Override
    public int getDefaultMaxLimit() {
        return 2;
    }
}
