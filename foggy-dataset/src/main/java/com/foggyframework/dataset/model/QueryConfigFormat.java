package com.foggyframework.dataset.model;

public interface QueryConfigFormat {
    /**
     * 返回的列会被转换成java的格式
     */
    @Deprecated
    int JAVA_FORMAT = 0;
    /**
     * 不做转换
     */
    int NO_FORMAT = 1;

    int JAVA_FORMAT_N = 2;

}
