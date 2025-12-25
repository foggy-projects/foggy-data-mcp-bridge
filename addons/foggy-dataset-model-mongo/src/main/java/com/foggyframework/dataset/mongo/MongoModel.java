package com.foggyframework.dataset.mongo;

import com.foggyframework.dataset.model.KpiModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.QueryExpEvaluator;

/**
 * MongoDB 模型接口
 * 支持基于 fsscript 的 MongoDB 查询
 */
public interface MongoModel extends KpiModel {

    /**
     * 执行分页查询
     */
    PagingResultImpl queryPaging(QueryExpEvaluator ee);
}
