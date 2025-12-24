package com.foggyframework.dataset.client.test.support;

import com.foggyframework.dataset.client.annotates.DataSetClient;
import com.foggyframework.dataset.client.annotates.DataSetQuery;
import com.foggyframework.dataset.client.annotates.OnDuplicate;
import com.foggyframework.dataset.model.PagingResult;

import java.util.List;
import java.util.Map;

/**
 * 测试用 DataSetClient 接口
 */
@DataSetClient
public interface TestDataSetClient {

    /**
     * 根据方法名推断数据集名称：User
     */
    List<UserDto> findUser(Map<String, Object> params);

    /**
     * 根据方法名推断数据集名称：User
     */
    UserDto getUser(Long id);

    /**
     * 根据方法名推断数据集名称：Order
     */
    List<Map<String, Object>> queryOrder(Map<String, Object> params);

    /**
     * 显式指定数据集名称
     */
    @DataSetQuery(name = "UserDetail", maxLimit = 100)
    PagingResult<UserDto> findUserDetail(Map<String, Object> params);

    /**
     * 显式指定数据集名称，返回单条记录
     */
    @DataSetQuery(name = "UserDetail")
    UserDto findUserDetailById(Long id);

    /**
     * 返回简单类型
     */
    @DataSetQuery(name = "UserCount")
    Long countUsers();

    /**
     * 返回 List<String>
     */
    @DataSetQuery(name = "UserNames")
    List<String> findUserNames();

    /**
     * OnDuplicate 单条插入/更新
     */
    @OnDuplicate(table = "t_order")
    int saveOrder(OrderForm form);

    /**
     * OnDuplicate 批量插入/更新
     */
    @OnDuplicate(table = "t_order", versionColumn = "version")
    int batchSaveOrders(List<OrderForm> forms);
}
