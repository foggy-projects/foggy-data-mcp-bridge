package com.foggyframework.dataset.db.model.engine.expression;

import com.foggyframework.dataset.db.model.impl.mongo.MongoQueryModel;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * MongoDB 表达式上下文
 * <p>
 * 提供列解析能力，将列名转换为 MongoDB 字段引用。
 * 维护计算字段注册表，支持计算字段链式依赖。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
@Getter
public class MongoExpContext {

    /**
     * 上下文变量名（用于表达式求值时访问）
     */
    public static final String CONTEXT_KEY = "__mongoExpContext__";

    /**
     * 查询模型
     */
    private final MongoQueryModel queryModel;

    /**
     * Spring 应用上下文
     */
    private final ApplicationContext appCtx;

    /**
     * 已注册的计算字段列
     * <p>
     * key: 计算字段名
     * value: 对应的 MongoCalculatedColumn
     * </p>
     */
    private final Map<String, MongoCalculatedColumn> calculatedColumns = new HashMap<>();

    public MongoExpContext(MongoQueryModel queryModel, ApplicationContext appCtx) {
        this.queryModel = queryModel;
        this.appCtx = appCtx;
    }

    /**
     * 解析列名，返回 MongoDB 字段名
     *
     * @param columnName 列名
     * @return MongoDB 字段名
     * @throws RuntimeException 如果列不存在
     */
    public String resolveFieldName(String columnName) {
        // 1. 先查找已注册的计算字段
        MongoCalculatedColumn calculatedColumn = calculatedColumns.get(columnName);
        if (calculatedColumn != null) {
            calculatedColumn.setHasRef(true);
            return columnName; // 计算字段使用其名称作为字段名
        }

        // 2. 从查询模型中查找
        DbQueryColumn queryColumn = queryModel.findJdbcColumnForSelectByName(columnName, false);
        if (queryColumn != null) {
            DbColumn selectColumn = queryColumn.getSelectColumn();
            if (selectColumn != null && selectColumn.getSqlColumn() != null) {
                return selectColumn.getSqlColumn().getName();
            }
            return columnName;
        }

        // 3. 尝试从模型中直接查找
        DbColumn jdbcColumn = queryModel.findJdbcColumn(columnName);
        if (jdbcColumn != null) {
            if (jdbcColumn.getSqlColumn() != null) {
                return jdbcColumn.getSqlColumn().getName();
            }
            return columnName;
        }

        throw new RuntimeException("列不存在: " + columnName);
    }

    /**
     * 尝试解析列，返回 JdbcQueryColumn
     *
     * @param columnName 列名
     * @return JdbcQueryColumn，如果不存在返回 null
     */
    public DbQueryColumn tryResolveColumn(String columnName) {
        // 1. 先查找计算字段
        MongoCalculatedColumn calculatedColumn = calculatedColumns.get(columnName);
        if (calculatedColumn != null) {
            return calculatedColumn;
        }

        // 2. 从查询模型中查找
        return queryModel.findJdbcColumnForSelectByName(columnName, false);
    }

    /**
     * 解析列，返回 JdbcQueryColumn
     *
     * @param columnName 列名
     * @return JdbcQueryColumn
     * @throws RuntimeException 如果列不存在
     */
    public DbQueryColumn resolveColumn(String columnName) {
        DbQueryColumn column = tryResolveColumn(columnName);
        if (column == null) {
            throw new RuntimeException("列不存在: " + columnName);
        }
        return column;
    }

    /**
     * 检查列是否存在
     *
     * @param columnName 列名
     * @return 是否存在
     */
    public boolean hasColumn(String columnName) {
        if (calculatedColumns.containsKey(columnName)) {
            return true;
        }
        return queryModel.findJdbcColumnForSelectByName(columnName, false) != null;
    }

    /**
     * 注册计算字段列
     *
     * @param name   计算字段名
     * @param column 计算字段列
     */
    public void registerCalculatedColumn(String name, MongoCalculatedColumn column) {
        if (log.isDebugEnabled()) {
            log.debug("Registering calculated column: {}", name);
        }
        calculatedColumns.put(name, column);
    }

    /**
     * 获取计算字段
     *
     * @param name 计算字段名
     * @return 计算字段列，如果不存在返回 null
     */
    public MongoCalculatedColumn getCalculatedColumn(String name) {
        return calculatedColumns.get(name);
    }
}
