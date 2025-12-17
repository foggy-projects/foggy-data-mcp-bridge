package com.foggyframework.dataset.db.table;


import com.foggyframework.core.ex.RX;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.core.utils.JsonUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.beanhelper.RequestBeanInjecter;
import com.foggyframework.dataset.db.DbObject;
import com.foggyframework.dataset.db.DbObjectType;
import com.foggyframework.dataset.db.dialect.FDialect;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.sql.Types;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class SqlColumn extends DbObject {
    public static String TYPE_VARCHAR = "VARCHAR";

    public static final int DEFAULT_PRECISION = 19;

    public static final int DEFAULT_SCALE = 2;

    private String caption;

    private String typeName;

    protected ObjectTransFormatter<?> formatter;

    private int jdbcType;

    private Integer length;
    // 默认都是允许空
    private boolean nullable = true;

    private String defaultValue;

    private static Map<String, Integer> typeName2JdbcType = new HashMap<>();

    static {
        // 字符串类型
        typeName2JdbcType.put("VARCHAR", Types.VARCHAR);
        typeName2JdbcType.put("CHAR", Types.CHAR);
        typeName2JdbcType.put("NVARCHAR", Types.NVARCHAR);
        typeName2JdbcType.put("NCHAR", Types.NCHAR);
        typeName2JdbcType.put("TEXT", Types.LONGVARCHAR);
        typeName2JdbcType.put("LONGTEXT", Types.LONGVARCHAR);
        typeName2JdbcType.put("CLOB", Types.CLOB);
        typeName2JdbcType.put("NCLOB", Types.NCLOB);
        // 布尔/位类型
        typeName2JdbcType.put("BIT", Types.BIT);
        typeName2JdbcType.put("BOOLEAN", Types.BOOLEAN);
        typeName2JdbcType.put("BOOL", Types.BOOLEAN);
        // 整数类型
        typeName2JdbcType.put("TINYINT", Types.TINYINT);
        typeName2JdbcType.put("SMALLINT", Types.SMALLINT);
        typeName2JdbcType.put("INTEGER", Types.INTEGER);
        typeName2JdbcType.put("INT", Types.INTEGER);
        typeName2JdbcType.put("BIGINT", Types.BIGINT);
        // 精确数值类型
        typeName2JdbcType.put("NUMERIC", Types.NUMERIC);
        typeName2JdbcType.put("DECIMAL", Types.DECIMAL);
        typeName2JdbcType.put("NUMBER", Types.DECIMAL);
        // 浮点类型
        typeName2JdbcType.put("FLOAT", Types.FLOAT);
        typeName2JdbcType.put("REAL", Types.REAL);
        typeName2JdbcType.put("DOUBLE", Types.DOUBLE);
        // 日期时间类型
        typeName2JdbcType.put("DATE", Types.DATE);
        typeName2JdbcType.put("TIME", Types.TIME);
        typeName2JdbcType.put("TIMESTAMP", Types.TIMESTAMP);
        typeName2JdbcType.put("DATETIME", Types.TIMESTAMP);
        // 二进制类型
        typeName2JdbcType.put("BINARY", Types.BINARY);
        typeName2JdbcType.put("VARBINARY", Types.VARBINARY);
        typeName2JdbcType.put("BLOB", Types.BLOB);
        typeName2JdbcType.put("LONGBLOB", Types.BLOB);
        // JSON/对象类型
        typeName2JdbcType.put("JSON", Types.OTHER);
        typeName2JdbcType.put("OBJECT", Types.JAVA_OBJECT);
    }

    public SqlColumn() {
        super();
    }

    public SqlColumn(String name, String caption, int type) {
        this(name, caption, type, 64);
    }

    public SqlColumn(String name, String caption, SqlColumnType type) {
        this(name, caption, typeName2JdbcType.get(type.name()), null);
    }

    public SqlColumn(String name, String caption, SqlColumnType type, Integer length) {
        this(name, caption, typeName2JdbcType.get(type.name()), length);
    }

    public SqlColumn(String name, String caption, SqlColumnType type, Integer length, String defaultValue) {
        this(name, caption, typeName2JdbcType.get(type.name()), length);
        this.defaultValue = defaultValue;
    }

    public static Integer _getTypeName2JdbcType(String typeName) {

        Integer v = typeName2JdbcType.get(typeName);
        if (v == null) {
            throw RX.throwA("无法根据typeName: " + typeName + "找到对应的jdbcType,目前支持的有: " + typeName2JdbcType);
        }
        return v;
    }

    public void setColumnType(String type) {
        setJdbcType(typeName2JdbcType.get(type));
    }

    public void setJdbcType(int type) {
        switch (type) {
            // 字符串类型
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.LONGVARCHAR:
            case Types.NVARCHAR:
            case Types.NCHAR:
            case Types.LONGNVARCHAR:
            case Types.CLOB:
            case Types.NCLOB:
                typeName = "VARCHAR";
                formatter = RequestBeanInjecter.getInstance().getObjectTransFormatter(String.class);
                break;
            // 布尔/位类型
            case Types.BIT:
            case Types.BOOLEAN:
                typeName = "BIT";
                formatter = RequestBeanInjecter.getInstance().getObjectTransFormatter(Boolean.class);
                break;
            // 小整数类型
            case Types.TINYINT:
                typeName = "TINYINT";
                formatter = RequestBeanInjecter.getInstance().getObjectTransFormatter(Integer.class);
                break;
            // 整数类型
            case Types.INTEGER:
                typeName = "INTEGER";
                formatter = RequestBeanInjecter.getInstance().getObjectTransFormatter(Integer.class);
                break;
            case Types.SMALLINT:
                typeName = "SMALLINT";
                formatter = RequestBeanInjecter.getInstance().getObjectTransFormatter(Integer.class);
                break;
            // 精确数值类型
            case Types.NUMERIC:
            case Types.DECIMAL:
                typeName = "DECIMAL";
                formatter = RequestBeanInjecter.getInstance().getObjectTransFormatter(java.math.BigDecimal.class);
                break;
            // 浮点类型
            case Types.FLOAT:
            case Types.REAL:
                typeName = "FLOAT";
                formatter = RequestBeanInjecter.getInstance().getObjectTransFormatter(Float.class);
                break;
            case Types.DOUBLE:
                typeName = "DOUBLE";
                formatter = RequestBeanInjecter.getInstance().getObjectTransFormatter(Double.class);
                break;
            // 长整数类型
            case Types.BIGINT:
                typeName = "BIGINT";
                formatter = RequestBeanInjecter.getInstance().getObjectTransFormatter(Long.class);
                break;
            // 日期时间类型
            case Types.DATE:
                typeName = "DATE";
                formatter = RequestBeanInjecter.getInstance().getObjectTransFormatter(java.util.Date.class);
                break;
            case Types.TIME:
            case Types.TIME_WITH_TIMEZONE:
                typeName = "TIME";
                formatter = RequestBeanInjecter.getInstance().getObjectTransFormatter(java.util.Date.class);
                break;
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                typeName = "TIMESTAMP";
                formatter = RequestBeanInjecter.getInstance().getObjectTransFormatter(java.util.Date.class);
                break;
            // 二进制类型
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
            case Types.BLOB:
                typeName = "BLOB";
                formatter = null;
                break;
            // JSON/对象类型
            case Types.OTHER:
            case Types.JAVA_OBJECT:
                typeName = "JSON";
                jdbcType = Types.OTHER;
                formatter = JSON_OBJECT_TRANSFORMATTERINSTANCE;
                break;
            default:
                typeName = "UNKNOW";
        }

        this.caption = this.caption + "  [" + typeName + "]";
        if (this.length == null) {
            this.length = 0;
        }
    }

    private static final JsonObjectTransFormatter JSON_OBJECT_TRANSFORMATTERINSTANCE = new JsonObjectTransFormatter();

    private static class JsonObjectTransFormatter implements ObjectTransFormatter {

        @Override
        public Object format(Object object) {
            if(object instanceof String || object instanceof Number){
                return object;
            }
            return JsonUtils.toJson(object);
        }

        @Override
        public Class<?> type() {
            return Object.class;
        }
    }

    public SqlColumn(String name, String caption, int type, Integer length) {
        super();
        this.name = name;
        this.caption = caption;
        this.jdbcType = type;
        this.length = length;
        setJdbcType(jdbcType);
    }

    public Object format2JdbcType(Object value) {
        if (StringUtils.isEmpty(value)) {
            switch (jdbcType) {
                // 字符串类型
                case Types.VARCHAR:
                case Types.CHAR:
                case Types.LONGVARCHAR:
                case Types.NVARCHAR:
                case Types.NCHAR:
                case Types.LONGNVARCHAR:
                case Types.CLOB:
                case Types.NCLOB:
                    return "";
                // 布尔/位类型
                case Types.BIT:
                case Types.BOOLEAN:
                    return false;
                // 整数类型
                case Types.TINYINT:
                case Types.INTEGER:
                case Types.SMALLINT:
                    return 0;
                // 精确数值类型
                case Types.NUMERIC:
                case Types.DECIMAL:
                    return java.math.BigDecimal.ZERO;
                // 浮点类型
                case Types.FLOAT:
                case Types.REAL:
                case Types.DOUBLE:
                    return 0.0;
                // 长整数类型
                case Types.BIGINT:
                    return 0L;
                // 日期时间类型
                case Types.DATE:
                case Types.TIME:
                case Types.TIME_WITH_TIMEZONE:
                case Types.TIMESTAMP:
                case Types.TIMESTAMP_WITH_TIMEZONE:
                    return new Date(System.currentTimeMillis());
                // 二进制类型
                case Types.BINARY:
                case Types.VARBINARY:
                case Types.LONGVARBINARY:
                case Types.BLOB:
                    return null;
                // JSON/对象类型
                case Types.OTHER:
                case Types.JAVA_OBJECT:
                    return null;
                default:
            }

        }
        if (formatter != null) {
            return formatter.format(value);
        }
        throw new RuntimeException();
    }

    @Override
    public DbObjectType getDbObjectType() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getSqlType(FDialect dialect) {
        return dialect.getTypeName(jdbcType, length, DEFAULT_PRECISION, DEFAULT_SCALE);
    }


    public boolean isUnique() {
        return false;
    }


    public String sqlConstraintString(FDialect dialect) {
        return "primary key (" + getQuotedName(dialect) +
                ')';
    }

}
