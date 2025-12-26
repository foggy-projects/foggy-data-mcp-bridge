package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.core.utils.beanhelper.RequestBeanInjecter;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.Date;

/**
 * JDBC 列类型枚举
 * <p>
 * 定义数据模型中支持的列类型，包含与 {@link java.sql.Types} 的映射关系
 * 和对应的格式化器。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
public enum DbColumnType {

    /**
     * 字典类型（通常映射为整数）
     */
    DICT("DICT", Types.INTEGER, Integer.class),

    /**
     * 金额类型
     */
    MONEY("MONEY", Types.DECIMAL, BigDecimal.class),

    /**
     * 纯日期类型 (yyyy-MM-dd)
     */
    DAY("DAY", Types.DATE, Date.class),

    /**
     * 日期时间类型 (yyyy-MM-dd HH:mm:ss)
     */
    DATETIME("DATETIME", Types.TIMESTAMP, Date.class),

    /**
     * 数值类型
     */
    NUMBER("NUMBER", Types.DECIMAL, BigDecimal.class),

    /**
     * 整数类型
     */
    INTEGER("INTEGER", Types.INTEGER, Integer.class),

    /**
     * 长整数类型
     */
    BIGINT("BIGINT", Types.BIGINT, Long.class),

    /**
     * 文本类型
     */
    TEXT("TEXT", Types.VARCHAR, String.class),

    /**
     * 字符串类型（TEXT的别名，用于向后兼容）
     */
    STRING("STRING", Types.VARCHAR, String.class),

    /**
     * 布尔类型
     */
    BOOL("BOOL", Types.BOOLEAN, Boolean.class),

    /**
     * 未知类型（默认）
     */
    UNKNOWN("UNKNOWN", Types.OTHER, Object.class);

    // ==========================================
    // 向后兼容的常量（保持原有接口的使用方式）
    // ==========================================

    /** @deprecated 使用 {@link #DICT} 枚举值代替 */
    public static final String _DICT = "DICT";
    /** @deprecated 使用 {@link #MONEY} 枚举值代替 */
    public static final String _MONEY = "MONEY";
    /** @deprecated 使用 {@link #DAY} 枚举值代替 */
    public static final String _DAY = "DAY";
    /** @deprecated 使用 {@link #DATETIME} 枚举值代替 */
    public static final String _DATETIME = "DATETIME";
    /** @deprecated 使用 {@link #NUMBER} 枚举值代替 */
    public static final String _NUMBER = "NUMBER";
    /** @deprecated 使用 {@link #TEXT} 枚举值代替 */
    public static final String _TEXT = "TEXT";
    /** @deprecated 使用 {@link #STRING} 枚举值代替 */
    public static final String _STRING = "STRING";
    /** @deprecated 使用 {@link #BOOL} 枚举值代替 */
    public static final String _BOOL = "BOOL";

    // ==========================================
    // 枚举属性
    // ==========================================

    private final String code;
    private final int jdbcType;
    private final Class<?> javaType;

    DbColumnType(String code, int jdbcType, Class<?> javaType) {
        this.code = code;
        this.jdbcType = jdbcType;
        this.javaType = javaType;
    }

    /**
     * 获取类型代码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取对应的 {@link java.sql.Types} 值
     */
    public int getJdbcType() {
        return jdbcType;
    }

    /**
     * 获取对应的 Java 类型
     */
    public Class<?> getJavaType() {
        return javaType;
    }

    /**
     * 获取对应的格式化器
     *
     * @return 格式化器，如果无法获取则返回默认透传格式化器
     */
    public ObjectTransFormatter<?> getFormatter() {
        try {
            ObjectTransFormatter<?> formatter = RequestBeanInjecter.getInstance().getObjectTransFormatter(javaType);
            return formatter != null ? formatter : PASSTHROUGH_FORMATTER;
        } catch (Exception e) {
            return PASSTHROUGH_FORMATTER;
        }
    }

    /**
     * 默认透传格式化器
     * <p>
     * 用于 UNKNOWN 类型或无法推断类型时，直接返回原始值不做任何转换。
     * </p>
     */
    public static final ObjectTransFormatter<Object> PASSTHROUGH_FORMATTER = new ObjectTransFormatter<Object>() {
        @Override
        public Object format(Object object) {
            return object;
        }

        @Override
        public Class<Object> type() {
            return Object.class;
        }
    };

    /**
     * 根据代码查找枚举值
     *
     * @param code 类型代码
     * @return 枚举值，未找到返回 {@link #UNKNOWN}
     */
    public static DbColumnType fromCode(String code) {
        if (code == null) {
            return UNKNOWN;
        }
        // 处理常见别名
        String upperCode = code.toUpperCase();
        if ("DECIMAL".equals(upperCode) || "NUMERIC".equals(upperCode) ||
            "FLOAT".equals(upperCode) || "DOUBLE".equals(upperCode) || "REAL".equals(upperCode) ||
            "BIGDECIMAL".equals(upperCode)) {
            return NUMBER;
        }
        if ("VARCHAR".equals(upperCode) || "CHAR".equals(upperCode) ||
            "NVARCHAR".equals(upperCode) || "CLOB".equals(upperCode)) {
            return TEXT;
        }
        if ("TIMESTAMP".equals(upperCode) || "TIME".equals(upperCode)) {
            return DATETIME;
        }
        if ("DATE".equals(upperCode)) {
            return DAY;
        }
        if ("BIT".equals(upperCode) || "BOOLEAN".equals(upperCode)) {
            return BOOL;
        }
        if ("SMALLINT".equals(upperCode) || "TINYINT".equals(upperCode) || "INT".equals(upperCode)) {
            return INTEGER;
        }
        if ("LONG".equals(upperCode)) {
            return BIGINT;
        }
        for (DbColumnType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    /**
     * 根据 {@link java.sql.Types} 值查找枚举值
     *
     * @param jdbcType JDBC 类型值
     * @return 枚举值，未找到返回 {@link #UNKNOWN}
     */
    public static DbColumnType fromJdbcType(int jdbcType) {
        switch (jdbcType) {
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
                return INTEGER;
            case Types.BIGINT:
                return BIGINT;
            case Types.DECIMAL:
            case Types.NUMERIC:
            case Types.FLOAT:
            case Types.DOUBLE:
            case Types.REAL:
                return NUMBER;
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.LONGVARCHAR:
            case Types.NVARCHAR:
            case Types.NCHAR:
            case Types.CLOB:
                return TEXT;
            case Types.BOOLEAN:
            case Types.BIT:
                return BOOL;
            case Types.DATE:
                return DAY;
            case Types.TIMESTAMP:
            case Types.TIME:
                return DATETIME;
            default:
                return UNKNOWN;
        }
    }

    @Override
    public String toString() {
        return code;
    }
}
