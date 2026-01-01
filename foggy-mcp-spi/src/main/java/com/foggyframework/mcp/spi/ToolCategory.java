package com.foggyframework.mcp.spi;

/**
 * 工具分类枚举
 * <p>
 * 用于对 MCP 工具进行分类，方便按角色过滤
 */
public enum ToolCategory {

    /**
     * 自然语言查询 - 适合普通业务用户
     * 支持用自然语言描述需求，系统自动转换为查询
     */
    NATURAL_LANGUAGE("自然语言查询", "使用自然语言进行数据查询，无需了解技术细节"),

    /**
     * 元数据管理 - 查询数据模型、字段等元数据信息
     */
    METADATA("元数据管理", "查询和管理数据模型的元数据信息"),

    /**
     * 数据查询 - 结构化的数据查询工具
     * 需要了解数据模型和查询语法
     */
    QUERY("数据查询", "执行结构化的数据查询操作"),

    /**
     * 数据可视化 - 图表生成和导出
     */
    VISUALIZATION("数据可视化", "生成图表和可视化报表"),

    /**
     * 数据导出 - 导出数据到各种格式
     */
    EXPORT("数据导出", "将查询结果导出为各种格式"),

    /**
     * 系统工具 - 系统级别的工具（如健康检查等）
     */
    SYSTEM("系统工具", "系统级别的管理和监控工具");

    private final String displayName;
    private final String description;

    ToolCategory(String displayName, String description) {
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
