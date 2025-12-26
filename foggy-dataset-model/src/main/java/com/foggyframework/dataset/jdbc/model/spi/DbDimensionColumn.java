package com.foggyframework.dataset.jdbc.model.spi;

public interface DbDimensionColumn extends DbColumn {


    DbDimension getDimension();

    boolean isCaptionColumn();
}
