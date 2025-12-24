package com.foggyframework.dataset.client.proxy.converter;

import com.foggyframework.dataset.model.PagingResultImpl;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public interface ReturnConverter {
    /**
     * 用于JDBC结果集的行转换
     *
     * @param genericReturnType 泛型返回类型
     * @return RowMapper
     */
    RowMapper getRowMapper(Type genericReturnType);

    /**
     * 把pagingResult转换成需要返回的数据
     *
     * @param pagingResult 分页结果
     * @return 转换后的结果
     */
    Object convertPagingResult(PagingResultImpl pagingResult);

    /**
     * 把items转换成需要返回的数据
     *
     * @param start 起始位置
     * @param limit 限制数量
     * @param items 列表数据
     * @return 转换后的结果
     */
    Object convertList(int start, int limit, List items);

    /**
     * 当返回对象是 PagingResult<BB> 这种情况时 genericReturnType = PagingResult<BB> ，返回BB.class
     *
     * @param genericReturnType 泛型返回类型
     * @return Bean类
     */
    Class getBeanClazz(Type genericReturnType);

    /**
     * 当查询对象是KPI时，或有需要时，会调用这个转换
     *
     * @param genericReturnType 泛型返回类型
     * @param queryMap          查询结果Map
     * @return 转换后的结果
     */
    Object convertMap(Type genericReturnType, Map<String, Object> queryMap);

    /**
     * 在未指定的情况下，是否返回总行数
     *
     * @return 是否返回总数
     */
    boolean getDefaultReturnTotal();

    int getDefaultMaxLimit();
}
