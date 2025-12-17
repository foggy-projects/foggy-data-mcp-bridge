package com.foggyframework.dataset.jdbc.model.semantic.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;
import java.util.Map;

/**
 * 语义值解析响应
 */
@ApiModel("语义值解析响应")
public class SemanticResolveResponse {

    @ApiModelProperty("解析结果列表")
    private List<ResolvedItem> resolvedItems;

    public List<ResolvedItem> getResolvedItems() {
        return resolvedItems;
    }

    public void setResolvedItems(List<ResolvedItem> resolvedItems) {
        this.resolvedItems = resolvedItems;
    }

    /**
     * 解析结果项
     */
    @ApiModel("解析结果项")
    public static class ResolvedItem {

        @ApiModelProperty("字段名称")
        private String fieldName;

        @ApiModelProperty("原始语义值")
        private String semanticValue;

        @ApiModelProperty("解析后的实际值")
        private Object actualValue;

        @ApiModelProperty("是否匹配成功")
        private boolean matched;

        @ApiModelProperty("匹配类型：exact|alias|fuzzy|passthrough")
        private String matchType;

        @ApiModelProperty("匹配置信度")
        private double confidence;

        @ApiModelProperty("备选项（仅当有多个匹配项时）")
        private List<Alternative> alternatives;

        @ApiModelProperty("字典项详情（仅字典类型字段）")
        private Map<String, Object> dictItem;

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

        public Object getActualValue() {
            return actualValue;
        }

        public void setActualValue(Object actualValue) {
            this.actualValue = actualValue;
        }

        public boolean isMatched() {
            return matched;
        }

        public void setMatched(boolean matched) {
            this.matched = matched;
        }

        public String getMatchType() {
            return matchType;
        }

        public void setMatchType(String matchType) {
            this.matchType = matchType;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }

        public List<Alternative> getAlternatives() {
            return alternatives;
        }

        public void setAlternatives(List<Alternative> alternatives) {
            this.alternatives = alternatives;
        }

        public Map<String, Object> getDictItem() {
            return dictItem;
        }

        public void setDictItem(Map<String, Object> dictItem) {
            this.dictItem = dictItem;
        }
    }

    /**
     * 备选项
     */
    @ApiModel("备选项")
    public static class Alternative {

        @ApiModelProperty("备选值")
        private Object value;

        @ApiModelProperty("显示名称")
        private String caption;

        @ApiModelProperty("置信度")
        private double confidence;

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public String getCaption() {
            return caption;
        }

        public void setCaption(String caption) {
            this.caption = caption;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }
    }
}