package com.foggyframework.dataset.model.impl.vector;

import com.foggyframework.dataset.model.spi.DbObject;
import com.foggyframework.dataset.model.spi.QueryModel;

/**
 * 向量查询模型接口
 * <p>
 * 扩展 QueryModel 接口，专门用于向量数据库的相似度检索。
 */
public interface VectorQueryModel extends DbObject, QueryModel {

    /**
     * 获取向量数据库配置
     */
    VectorDbConfig getVectorDbConfig();

    /**
     * 获取向量字段名称
     */
    String getVectorFieldName();

    /**
     * 获取集合名称
     */
    String getCollectionName();
}
