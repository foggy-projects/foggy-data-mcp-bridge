package com.foggyframework.dataset.db.model.engine.formula;

/**
 * JDBC 链接类型枚举
 */
public enum JdbcLink {
    AND(1, "and"),
    OR(2, "or");

    private final int code;
    private final String linkStr;

    JdbcLink(int code, String linkStr) {
        this.code = code;
        this.linkStr = linkStr;
    }

    public int getCode() {
        return code;
    }

    public String getLinkStr() {
        return linkStr;
    }

    /**
     * 根据 code 获取枚举值
     * @param code 链接类型代码
     * @return 对应的枚举值，默认返回 AND
     */
    public static JdbcLink fromCode(int code) {
        for (JdbcLink link : values()) {
            if (link.code == code) {
                return link;
            }
        }
        return AND;
    }

    /**
     * 获取链接字符串（兼容旧接口）
     * @param link 链接类型代码
     * @return 链接字符串
     * @deprecated 请使用 {@link #fromCode(int)} 获取枚举后调用 {@link #getLinkStr()}
     */
    @Deprecated
    public static String getLinkStr(int link) {
        return fromCode(link).getLinkStr();
    }
}
