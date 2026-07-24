package com.foggyframework.dataset.model.impl.column;

import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.impl.AiObject;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.DbColumnType;
import com.foggyframework.dataset.model.spi.QueryObject;
import com.foggyframework.dataset.db.table.SqlColumn;
import lombok.Getter;
import org.springframework.context.ApplicationContext;

/**
 * 无效字段实现
 * <p>
 * 用于在模型加载时字段验证失败的占位实现，避免 NullPointerException。
 * 当字段在 TM 文件中定义但在实际数据库表中不存在时使用此类。
 * </p>
 *
 * <p>设计原则：
 * <ul>
 *   <li>不抛出异常 - 所有方法返回安全的默认值</li>
 *   <li>标记为无效 - 通过 {@link #isValid()} 返回 false</li>
 *   <li>保留错误原因 - 通过 {@link #getErrorReason()} 获取失败原因</li>
 * </ul>
 * </p>
 *
 * @author Foggy Framework
 * @since 8.3.0
 */
@Getter
public class InvalidDbColumn implements DbColumn {

    /**
     * 字段名称（原始配置中的名称）
     */
    private final String columnName;

    /**
     * 错误原因
     */
    private final String errorReason;

    /**
     * 占位的 QueryObject（用于避免 NPE）
     */
    private final QueryObject queryObject;

    /**
     * 字段类型（默认为 TEXT）
     */
    private final DbColumnType columnType;

    public InvalidDbColumn(String columnName, String errorReason) {
        this(columnName, errorReason, null, DbColumnType.TEXT);
    }

    public InvalidDbColumn(String columnName, String errorReason, QueryObject queryObject) {
        this(columnName, errorReason, queryObject, DbColumnType.TEXT);
    }

    public InvalidDbColumn(String columnName, String errorReason, QueryObject queryObject, DbColumnType columnType) {
        this.columnName = columnName;
        this.errorReason = errorReason;
        this.queryObject = queryObject != null ? queryObject : createPlaceholderQueryObject();
        this.columnType = columnType != null ? columnType : DbColumnType.TEXT;
    }

    /**
     * 判断字段是否有效
     *
     * @return 始终返回 false
     */
    public boolean isValid() {
        return false;
    }

    // ==================== DbColumn 接口实现 ====================

    @Override
    public String getName() {
        return columnName;
    }

    @Override
    public String getCaption() {
        return columnName + " (Invalid)";
    }

    @Override
    public String getDescription() {
        return "Invalid column: " + errorReason;
    }

    @Override
    public String getAlias() {
        return columnName;
    }

    @Override
    public QueryObject getQueryObject() {
        return queryObject;
    }

    @Override
    public SqlColumn getSqlColumn() {
        // 返回一个虚拟的 SqlColumn，避免 NPE
        return new SqlColumn(columnName, "VARCHAR", java.sql.Types.VARCHAR, 255);
    }

    @Override
    public String getSqlColumnName() {
        return columnName;
    }

    @Override
    public DbColumnType getType() {
        return columnType;
    }

    @Override
    public String getDeclare() {
        // 返回安全的声明，避免在 SQL 生成时出错
        return "NULL /* Invalid column: " + columnName + " */";
    }

    @Override
    public String getDeclare(ApplicationContext appCtx, String alias) {
        return "NULL /* Invalid column: " + columnName + " */";
    }

    @Override
    public String getDeclare(ApplicationContext appCtx, String alias, FDialect dialect) {
        return "NULL /* Invalid column: " + columnName + " */";
    }

    @Override
    public ObjectTransFormatter<?> getFormatter() {
        return null;
    }

    @Override
    public ObjectTransFormatter<?> getFormatter(boolean errorIfNull) {
        if (errorIfNull) {
            throw new RuntimeException("Column [" + columnName + "] is invalid: " + errorReason);
        }
        return null;
    }

    @Override
    public boolean isMeasure() {
        return false;
    }

    @Override
    public boolean isDimension() {
        return false;
    }

    @Override
    public boolean isProperty() {
        return false;
    }

    @Override
    public boolean _isDeprecated() {
        return false;
    }

    @Override
    public AiObject getAi() {
        return null;
    }

    @Override
    public Object getExtData() {
        return null;
    }

    @Override
    public <T> T getDecorate(Class<T> decorateClazz) {
        return null;
    }

    @Override
    public boolean isInDecorate(Object target) {
        return false;
    }

    @Override
    public Object getRoot() {
        return this;
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建占位的 QueryObject
     */
    private QueryObject createPlaceholderQueryObject() {
        // 使用已有的 SimpleQueryObject 而不是创建匿名实现
        com.foggyframework.dataset.model.spi.support.SimpleQueryObject placeholder =
                new com.foggyframework.dataset.model.spi.support.SimpleQueryObject();
        placeholder.setName("InvalidTable");
        placeholder.setCaption("Invalid Table");
        placeholder.setAlias("invalid");
        return placeholder;
    }

    @Override
    public String toString() {
        return String.format("InvalidDbColumn{name='%s', reason='%s'}", columnName, errorReason);
    }
}
