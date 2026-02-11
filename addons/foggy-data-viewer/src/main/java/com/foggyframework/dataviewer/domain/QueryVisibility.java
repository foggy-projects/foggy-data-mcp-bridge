package com.foggyframework.dataviewer.domain;

/**
 * 查询可见性范围
 */
public enum QueryVisibility {
    /** 仅自己可见 */
    PRIVATE,
    /** 同部门可见 */
    DEPARTMENT,
    /** 同租户可见 */
    TENANT
}
