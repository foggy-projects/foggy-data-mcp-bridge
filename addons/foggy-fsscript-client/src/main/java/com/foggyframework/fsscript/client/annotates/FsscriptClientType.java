package com.foggyframework.fsscript.client.annotates;

/**
 * Fsscript 客户端类型枚举
 */
public enum FsscriptClientType {
    AUTO_TYPE(0),
    EL_TYPE(1),
    FTXT_TYPE(2);

    private final int code;

    FsscriptClientType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * 根据 code 获取枚举值
     * @param code 类型代码
     * @return 对应的枚举值，未找到返回 AUTO_TYPE
     */
    public static FsscriptClientType fromCode(int code) {
        for (FsscriptClientType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return AUTO_TYPE;
    }
}
