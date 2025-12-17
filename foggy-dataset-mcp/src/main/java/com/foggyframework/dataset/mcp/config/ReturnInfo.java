package com.foggyframework.dataset.mcp.config;

import lombok.Data;

import java.util.List;

/**
 * 返回值信息模型
 *
 * 对应工具配置中的返回值定义
 */
@Data
public class ReturnInfo {

    /**
     * 返回类型
     */
    private String type;

    /**
     * 简要说明
     */
    private String summary;

    /**
     * 是否包含exports字段
     */
    private Boolean exportsField;

    /**
     * 字段列表
     */
    private List<FieldInfo> fields;
}
