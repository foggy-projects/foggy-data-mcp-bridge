package com.foggyframework.dataset.mcp.config;

import lombok.Data;

import java.util.Map;

/**
 * 参数信息模型
 *
 * 对应工具配置中的参数定义
 */
@Data
public class ParameterInfo {

    /**
     * 参数名称
     */
    private String name;

    /**
     * 参数类型（string/number/boolean/object/array）
     */
    private String type;

    /**
     * 是否必填
     */
    private Boolean required;

    /**
     * 参数描述
     */
    private String description;

    /**
     * 示例值
     */
    private String example;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 属性说明（用于object类型）
     */
    private Map<String, String> properties;
}
