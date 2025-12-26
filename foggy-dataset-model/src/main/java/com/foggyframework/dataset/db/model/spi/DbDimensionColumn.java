package com.foggyframework.dataset.db.model.spi;

public interface DbDimensionColumn extends DbColumn {


    DbDimension getDimension();

    boolean isCaptionColumn();
}
