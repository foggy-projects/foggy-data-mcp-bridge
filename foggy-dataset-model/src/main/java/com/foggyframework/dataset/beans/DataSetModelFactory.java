package com.foggyframework.dataset.beans;

import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryModelLoader;
import com.foggyframework.dataset.jdbc.model.spi.QueryModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DataSetModel 工厂类
 * 用于根据名称获取数据集模型（JdbcQueryModel）
 */
@Component
@RequiredArgsConstructor
public class DataSetModelFactory {

    private final JdbcQueryModelLoader jdbcQueryModelLoader;

    public QueryModel getDataSetModel(String name) {
        return getDataSetModel(name, true);
    }

    public QueryModel getDataSetModel(String name, boolean errorIfNotFound) {
        try {
            return jdbcQueryModelLoader.getJdbcQueryModel(name);
        } catch (Exception e) {
            if (errorIfNotFound) {
                throw e;
            }
            return null;
        }
    }
}
