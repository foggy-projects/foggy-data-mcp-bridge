package com.foggyframework.dataset.jdbc.model.semantic.domain;

import com.foggyframework.dataset.jdbc.model.def.query.request.DbQueryRequestDef;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 语义归一化测试响应
 * 用于测试和查看$caption到$id的归一化处理结果
 */
@Data
@ApiModel("语义归一化测试响应")
public class SemanticNormalizeResponse {
    
    @ApiModelProperty(value = "原始请求")
    private SemanticQueryRequest originalRequest;
    
    @ApiModelProperty(value = "归一化后的JDBC请求")
    private DbQueryRequestDef normalizedJdbcRequest;
    
    @ApiModelProperty(value = "字段映射关系")
    private FieldMappings fieldMappings;
    
    @ApiModelProperty(value = "处理过程中的警告信息")
    private List<String> warnings;
    
    @ApiModelProperty(value = "扩展数据上下文")
    private Map<String, Object> contextData;
    
    @ApiModelProperty(value = "处理耗时(毫秒)")
    private Long processingTimeMs;
    
    @ApiModelProperty(value = "匹配失败信息列表")
    private List<MismatchInfo> mismatchInfos;
    
    /**
     * 匹配失败信息
     */
    @Data
    @ApiModel("匹配失败信息")
    public static class MismatchInfo {
        
        @ApiModelProperty(value = "字段名", example = "esOrderUserState$caption")
        private String fieldName;
        
        @ApiModelProperty(value = "失败的caption值", example = "集配发车1")
        private List<Object> failedCaptions;
        
        @ApiModelProperty(value = "该字段可用的枚举值")
        private List<DictItem> availableOptions;
        
        @ApiModelProperty(value = "处理方式", example = "IGNORED")
        private String handleAction;
        
        @ApiModelProperty(value = "错误消息")
        private String errorMessage;
        
        /**
         * 字典项
         */
        @Data
        @ApiModel("字典项")
        public static class DictItem {
            @ApiModelProperty(value = "ID值")
            private String id;
            
            @ApiModelProperty(value = "Caption显示值")
            private String caption;
            
            @ApiModelProperty(value = "描述信息")
            private String description;
        }
    }
    
    /**
     * 字段映射关系
     */
    @Data
    @ApiModel("字段映射关系")
    public static class FieldMappings {
        
        @ApiModelProperty(value = "caption到id的字段映射")
        private Map<String, String> captionToIdMapping;
        
        @ApiModelProperty(value = "过滤条件中的值映射")
        private Map<String, ValueMapping> sliceValueMappings;
        
        /**
         * 值映射
         */
        @Data
        @ApiModel("值映射")
        public static class ValueMapping {
            
            @ApiModelProperty(value = "原始caption值")
            private Object originalValue;
            
            @ApiModelProperty(value = "映射后的id值")
            private Object mappedValue;
            
            @ApiModelProperty(value = "映射状态")
            private String status; // SUCCESS, NOT_FOUND, PARTIAL
            
            @ApiModelProperty(value = "未找到的caption值")
            private List<Object> notFoundCaptions;
        }
    }
}