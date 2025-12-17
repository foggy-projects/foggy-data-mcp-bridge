package com.foggyframework.dataset.model;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class QueryConfig {
    public static final QueryConfig DEFAULT = new QueryConfig();
    public static final QueryConfig DEFAULT_JAVA_FORMAT = new QueryConfig();

    static {
        DEFAULT.format = QueryConfigFormat.NO_FORMAT;
        DEFAULT_JAVA_FORMAT.format = QueryConfigFormat.JAVA_FORMAT;
    }

    int format;
    @ApiModelProperty("1表示返回ListResultSet")
    int returnType;
}
