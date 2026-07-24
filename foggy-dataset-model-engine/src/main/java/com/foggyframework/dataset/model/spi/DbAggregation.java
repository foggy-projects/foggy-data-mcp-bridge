package com.foggyframework.dataset.model.spi;

public enum DbAggregation {
    SUM, AVG,COUNT,MAX,NONE,MIN,GROUP_CONCAT,CUSTOM,PK,
    COUNT_DISTINCT,
    STDDEV_POP, STDDEV_SAMP, VAR_POP, VAR_SAMP,
    WINDOW
}
