package com.foggyframework.dataset.db.model.utils;

import com.foggyframework.core.utils.StringUtils;

/**
 * JDBC 模型命名工具类
 * MongoDB 相关方法已移至 addons/foggy-dataset-model-mongo/MongoModelNamedUtils
 */
public class JdbcModelNamedUtils {

    public static String toAliasName(String sqlColumnName) {
        return StringUtils.to(sqlColumnName);
    }

}
