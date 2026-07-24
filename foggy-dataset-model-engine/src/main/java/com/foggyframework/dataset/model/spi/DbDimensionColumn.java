package com.foggyframework.dataset.model.spi;

public interface DbDimensionColumn extends DbColumn {


    DbDimension getDimension();

    boolean isCaptionColumn();
}
