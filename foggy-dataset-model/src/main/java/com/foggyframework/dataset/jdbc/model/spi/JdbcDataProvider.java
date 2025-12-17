package com.foggyframework.dataset.jdbc.model.spi;

import java.util.Map;

public interface JdbcDataProvider {


    JdbcDimensionType getDimensionType();

    Map<String,Object> getExtData();

    String getName();

    <T>T getExtDataValue(String key);
}
