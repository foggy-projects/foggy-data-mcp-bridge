package com.foggyframework.dataset.jdbc.model.semantic.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;

/**
 * 语义元数据请求
 */
@ApiModel("语义元数据请求")
public class SemanticMetadataRequest {

    @ApiModelProperty(value = "QM模型列表", required = true, example = "[\"TmsCustomerModel\", \"TmsOrderModel\"]")
    private List<String> qmModels;

    @ApiModelProperty(value = "字段过滤列表，为空则返回所有字段", example = "[\"freightAmount\", \"customerName\"]")
    private List<String> fields;

    @ApiModelProperty(value = "是否包含示例数据", example = "true")
    private boolean includeExamples = false;

    @ApiModelProperty(value = "字段级别列表，空则返回level=1的字段", example = "[1, 2, 3]")
    private List<Integer> levels;

    public List<String> getQmModels() {
        return qmModels;
    }

    public void setQmModels(List<String> qmModels) {
        this.qmModels = qmModels;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    public boolean isIncludeExamples() {
        return includeExamples;
    }

    public void setIncludeExamples(boolean includeExamples) {
        this.includeExamples = includeExamples;
    }

    public List<Integer> getLevels() {
        return levels;
    }

    public void setLevels(List<Integer> levels) {
        this.levels = levels;
    }
}