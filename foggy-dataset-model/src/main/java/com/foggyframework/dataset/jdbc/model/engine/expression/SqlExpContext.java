package com.foggyframework.dataset.jdbc.model.engine.expression;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryColumn;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.jdbc.model.spi.support.CalculatedJdbcColumn;
import lombok.Data;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SQL 表达式执行上下文
 * <p>
 * 提供列解析、方言转换等能力，在 SqlExp 执行时使用。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
@Data
public class SqlExpContext {

    /**
     * 上下文在 ExpEvaluator 中的 key
     */
    public static final String CONTEXT_KEY = "__sqlExpContext";

    /**
     * 查询模型（用于解析列）
     */
    private final JdbcQueryModel queryModel;

    /**
     * 数据库方言（用于函数转换）
     */
    private final FDialect dialect;

    /**
     * Spring 上下文（用于 getDeclare 调用）
     */
    private final ApplicationContext appCtx;

    /**
     * 已注册的计算字段
     * <p>
     * 使用 LinkedHashMap 保持插入顺序，支持后面的计算字段引用前面的。
     * </p>
     */
    private final Map<String, CalculatedJdbcColumn> calculatedColumns = new LinkedHashMap<>();

    public SqlExpContext(JdbcQueryModel queryModel, FDialect dialect, ApplicationContext appCtx) {
        this.queryModel = queryModel;
        this.dialect = dialect;
        this.appCtx = appCtx;
    }

    /**
     * 解析列名
     * <p>
     * 支持:
     * <ul>
     *     <li>已注册的计算字段</li>
     *     <li>模型中的普通列</li>
     *     <li>维度列: dimension$caption, dimension$id</li>
     *     <li>带 formulaDef 的列</li>
     * </ul>
     * </p>
     *
     * @param columnName 列名
     * @return 对应的 JdbcQueryColumn
     * @throws RuntimeException 如果列不存在
     */
    public JdbcQueryColumn resolveColumn(String columnName) {
        // 1. 先查找计算字段
        CalculatedJdbcColumn calculated = calculatedColumns.get(columnName);
        if (calculated != null) {
            return calculated;
        }

        // 2. 从查询模型中查找
        return queryModel.findJdbcColumnForSelectByName(columnName, true);
    }

    /**
     * 尝试解析列名（不抛异常）
     *
     * @param columnName 列名
     * @return 对应的 JdbcQueryColumn，如果不存在返回 null
     */
    public JdbcQueryColumn tryResolveColumn(String columnName) {
        // 1. 先查找计算字段
        CalculatedJdbcColumn calculated = calculatedColumns.get(columnName);
        if (calculated != null) {
            return calculated;
        }

        // 2. 从查询模型中查找（不抛异常）
        return queryModel.findJdbcColumnForSelectByName(columnName, false);
    }

    /**
     * 检查列是否存在
     */
    public boolean hasColumn(String columnName) {
        return tryResolveColumn(columnName) != null;
    }

    /**
     * 注册计算字段
     * <p>
     * 支持计算字段之间的依赖顺序。
     * </p>
     *
     * @param name   字段名
     * @param column 计算字段列
     */
    public void registerCalculatedColumn(String name, CalculatedJdbcColumn column) {
        calculatedColumns.put(name, column);
    }

    /**
     * 获取列的表别名
     *
     * @param column 列对象
     * @return 表别名
     */
    public String getAlias(JdbcQueryColumn column) {
        if (column == null || column.getQueryObject() == null) {
            return null;
        }
        return column.getQueryObject().getAlias();
    }

    /**
     * 根据方言转换函数名
     * <p>
     * 用于处理不同数据库之间的函数差异。
     * 例如：MySQL 的 IFNULL vs PostgreSQL 的 COALESCE
     * </p>
     *
     * @param funcName 标准函数名
     * @return 方言特定的函数名
     */
    public String translateFunction(String funcName) {
        if (dialect == null) {
            return funcName;
        }

        // TODO: 实现方言特定的函数转换
        // 目前直接返回原函数名
        return funcName;
    }
}
