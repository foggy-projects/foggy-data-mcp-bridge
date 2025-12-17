package com.foggyframework.dataset.jdbc.model.semantic.enums;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel("Caption匹配模式")
public enum CaptionMatchMode {
    
    @ApiModelProperty("精准匹配（默认）")
    EXACT("精准匹配"),
    
    @ApiModelProperty("模糊匹配")
    FUZZY("模糊匹配");
    
    private final String description;
    
    CaptionMatchMode(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}