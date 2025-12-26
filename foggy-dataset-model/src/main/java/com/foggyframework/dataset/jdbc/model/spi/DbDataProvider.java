package com.foggyframework.dataset.jdbc.model.spi;

import java.util.Map;

public interface DbDataProvider {


    DbDimensionType getDimensionType();

    Map<String,Object> getExtData();

    String getName();

    <T>T getExtDataValue(String key);
}
