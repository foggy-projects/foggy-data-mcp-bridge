package com.foggyframework.dataset.mcp.config;

import lombok.Data;

/**
 * 字段信息模型
 *
 * 对应返回值中的字段定义
 */
@Data
public class FieldInfo {

    /**
     * 字段名称
     */
    private String name;

    /**
     * 字段描述
     */
    private String description;
}
