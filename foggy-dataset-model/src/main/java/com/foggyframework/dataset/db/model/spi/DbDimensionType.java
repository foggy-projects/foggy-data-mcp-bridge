package com.foggyframework.dataset.db.model.spi;

/**
 * 维度类型枚举
 */
public enum DbDimensionType {

    DATETIME,

    DICT,

    BOOL,

    NORMAL,

    /**
     * yyyy-MM-dd格式，或其他格式的日期字符串，由format决定
     */
    DAY,

    DOUBLE,

    INTEGER;

    /**
     * 从字符串转换为 JdbcDimensionType
     * @param value 字符串值
     * @return 对应的枚举值，如果没有匹配返回 null
     */
    public static DbDimensionType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 从 JdbcColumnType 转换为 JdbcDimensionType
     * @param columnType 列类型
     * @return 对应的维度类型，如果没有对应关系返回 NORMAL
     */
    public static DbDimensionType fromColumnType(DbColumnType columnType) {
        if (columnType == null) {
            return NORMAL;
        }
        switch (columnType) {
            case DATETIME:
                return DATETIME;
            case DICT:
                return DICT;
            case BOOL:
                return BOOL;
            case DAY:
                return DAY;
            case NUMBER:
            case MONEY:
                return DOUBLE;
            case INTEGER:
            case BIGINT:
                return INTEGER;
            default:
                return NORMAL;
        }
    }
}
