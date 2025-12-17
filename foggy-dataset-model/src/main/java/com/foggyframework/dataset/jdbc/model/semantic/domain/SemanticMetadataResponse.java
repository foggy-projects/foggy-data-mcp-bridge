package com.foggyframework.dataset.jdbc.model.semantic.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Map;

/**
 * 语义元数据响应
 */
@ApiModel("语义元数据响应")
public class SemanticMetadataResponse {

    @ApiModelProperty("格式化的元数据内容（JSON或Markdown格式）")
    private String content;

    @ApiModelProperty("结构化的元数据（仅JSON格式时有值）")
    private Map<String, Object> data;

    @ApiModelProperty("内容格式：json|markdown")
    private String format;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}