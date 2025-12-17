package com.foggyframework.dataset.mcp.enums;

/**
 * 用户角色枚举
 *
 * 定义不同的用户群体，每个角色可以访问不同的工具集
 */
public enum UserRole {

    /**
     * 管理员 - 拥有所有工具的访问权限
     */
    ADMIN("管理员", "拥有所有工具的完整访问权限"),

    /**
     * 业务人员 - 仅使用自然语言查询工具
     * 适合不需要了解技术细节的普通用户
     */
    BUSINESS("业务人员", "适合使用自然语言进行数据查询的普通用户"),

    /**
     * 数据分析师 - 使用专业数据处理工具
     * 不包含自然语言查询，需要专业的数据分析技能
     */
    ANALYST("数据分析师", "专业数据处理人员，使用结构化查询和高级分析工具");

    private final String displayName;
    private final String description;

    UserRole(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
