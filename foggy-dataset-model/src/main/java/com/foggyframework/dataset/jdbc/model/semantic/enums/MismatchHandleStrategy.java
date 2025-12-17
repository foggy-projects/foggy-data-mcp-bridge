package com.foggyframework.dataset.jdbc.model.semantic.enums;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel("匹配失败处理策略")
public enum MismatchHandleStrategy {
    
    @ApiModelProperty("中止查询（抛出异常）")
    ABORT("中止查询"),
    
    @ApiModelProperty("忽略该条件继续查询（返回警告信息）")
    IGNORE("忽略条件");
    
    private final String description;
    
    MismatchHandleStrategy(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}