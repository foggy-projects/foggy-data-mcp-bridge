package com.foggyframework.dataset.jdbc.model.common.query;

import io.swagger.annotations.ApiModelProperty;

/**
 * 查询条件类型枚举
 */
public enum CondType {
    @ApiModelProperty("等于")
    EQ("="),

    @ApiModelProperty("in")
    IN("in"),

    @ApiModelProperty("bitIn")
    BIT_IN("bit_in"),

    @ApiModelProperty(value = "like", notes = "字符串左右自动补%，例如查'3',会补成 '%3%'")
    LIKE("like"),

    @ApiModelProperty(value = "left_like", notes = "字符串左侧自动补%，例如查'3',会补成 '%3'")
    LEFT_LIKE("left_like"),

    @ApiModelProperty(value = "right_like", notes = "字符串右侧自动补%，例如查'3',会补成 '3%'")
    RIGHT_LIKE("right_like"),

    @ApiModelProperty(value = "范围[]", notes = "闭区间")
    RANGE_EE("[]"),

    @ApiModelProperty("范围[)")
    RANGE_ER("[)"),

    @ApiModelProperty("范围()")
    RANGE_RR("()"),

    @ApiModelProperty("范围(]")
    RANGE_RE("(]");

    private final String code;

    CondType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 根据 code 获取枚举值
     * @param code 条件类型代码
     * @return 对应的枚举值，未找到返回 null
     */
    public static CondType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CondType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return code;
    }
}
