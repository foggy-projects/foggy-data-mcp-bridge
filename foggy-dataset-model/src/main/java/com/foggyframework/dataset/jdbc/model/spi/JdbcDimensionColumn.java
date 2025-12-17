package com.foggyframework.dataset.jdbc.model.spi;

public interface JdbcDimensionColumn extends JdbcColumn {


    JdbcDimension getJdbcDimension();

    boolean isCaptionColumn();
}
