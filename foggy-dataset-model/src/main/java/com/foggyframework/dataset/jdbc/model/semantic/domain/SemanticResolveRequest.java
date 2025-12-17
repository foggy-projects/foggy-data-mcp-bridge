package com.foggyframework.dataset.jdbc.model.semantic.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;

/**
 * 语义值解析请求
 */
@ApiModel("语义值解析请求")
public class SemanticResolveRequest {

    @ApiModelProperty(value = "QM模型名称", required = true, example = "TmsOrderModel")
    private String qmModel;

    @ApiModelProperty(value = "需要解析的语义值列表", required = true)
    private List<ResolveItem> resolveItems;

    public String getQmModel() {
        return qmModel;
    }

    public void setQmModel(String qmModel) {
        this.qmModel = qmModel;
    }

    public List<ResolveItem> getResolveItems() {
        return resolveItems;
    }

    public void setResolveItems(List<ResolveItem> resolveItems) {
        this.resolveItems = resolveItems;
    }

    /**
     * 解析项
     */
    @ApiModel("解析项")
    public static class ResolveItem {

        @ApiModelProperty(value = "字段名称", required = true, example = "ownerTeam.teamCaption")
        private String fieldName;

        @ApiModelProperty(value = "语义值", required = true, example = "传化")
        private String semanticValue;

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getSemanticValue() {
            return semanticValue;
        }

        public void setSemanticValue(String semanticValue) {
            this.semanticValue = semanticValue;
        }
    }
}