package com.foggyframework.dataset.db.model.spi.support;

import com.foggyframework.core.AbstractDecorate;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.db.model.engine.expression.SqlFragment;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.table.SqlColumn;
import lombok.Data;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.Set;

/**
 * 计算字段的 JdbcColumn 实现
 * <p>
 * 用于表示通过表达式动态计算的列。
 * 通过 SqlFragment 存储计算后的 SQL 表达式及其依赖的列。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
@Data
public class CalculatedJdbcColumn extends AbstractDecorate implements DbQueryColumn {

    /**
     * 字段名（在 columns 中引用的名称）
     */
    private final String name;

    /**
     * 显示名称
     */
    private final String caption;

    /**
     * SQL 片段（包含表达式和引用的列）
     */
    private final SqlFragment sqlFragment;

    /**
     * 描述
     */
    private String description;

    /**
     * 数据类型
     * <p>
     * 优先从 SqlFragment 推断类型，如果用户显式设置了 type 则使用用户设置的值。
     * </p>
     * JdbcDimensionType
     */
    private DbColumnType type;

    /**
     * 获取数据类型
     * <p>
     * 优先返回用户显式设置的 type，如果未设置则从 SqlFragment 推断类型。
     * </p>
     *
     * @return 类型代码字符串
     */
    public DbColumnType getType() {
        if (type != null) {
            return type;
        }
        return sqlFragment.getInferredType();
    }

    /**
     * 是否已被引用
     */
    private boolean hasRef;

    /**
     * 查询对象（可选，用于某些场景）
     */
    private QueryObject queryObject;

    public CalculatedJdbcColumn(String name, String caption, SqlFragment sqlFragment) {
        this.name = name;
        this.caption = caption != null ? caption : name;
        this.sqlFragment = sqlFragment;
    }

    public CalculatedJdbcColumn(String name, String caption, SqlFragment sqlFragment, String description) {
        this(name, caption, sqlFragment);
        this.description = description;
    }

    /**
     * 获取 SQL 声明
     * <p>
     * 直接返回计算好的 SQL 片段。
     * </p>
     */
    @Override
    public String getDeclare(ApplicationContext appCtx, String alias) {
        return sqlFragment.getSql();
    }

    @Override
    public String getDeclare() {
        return sqlFragment.getSql();
    }

    /**
     * 获取依赖的列
     * <p>
     * 用于自动 JOIN 分析和依赖追踪。
     * </p>
     */
    public Set<DbQueryColumn> getReferencedColumns() {
        return sqlFragment.getReferencedColumns();
    }

    @Override
    public String getAlias() {
        return name;
    }

    @Override
    public String getField() {
        return name;
    }

    @Override
    public SqlColumn getSqlColumn() {
        // 计算字段没有对应的物理列
        return null;
    }

    @Override
    public DbColumn getSelectColumn() {
        return this;
    }

    @Override
    public DbQueryCondition getJdbcQueryCond() {
        // 计算字段作为过滤条件时，直接使用 SQL 表达式
        return null;
    }

    @Override
    public Map<String, Object> getUi() {
        return null;
    }

    @Override
    public ObjectTransFormatter<?> getValueFormatter() {
        return null;
    }

    @Override
    public ObjectTransFormatter<?> getFormatter() {
        // 从 SqlFragment 的推断类型获取格式化器
        DbColumnType inferredType = sqlFragment.getInferredType();
        return inferredType.getFormatter();
    }

    @Override
    public Object getExtData() {
        return null;
    }

    @Override
    public AiObject getAi() {
        return null;
    }

    @Override
    public boolean _isDeprecated() {
        return false;
    }

    /**
     * 计算字段标识
     *
     * @return 始终返回 true
     */
    @Override
    public boolean isCalculatedField() {
        return true;
    }

    /**
     * 是否包含聚合函数
     * <p>
     * 从 SqlFragment 获取聚合信息。
     * </p>
     *
     * @return 如果表达式包含 SUM, AVG, COUNT 等聚合函数，返回 true
     */
    public boolean hasAggregate() {
        return sqlFragment != null && sqlFragment.isHasAggregate();
    }

    /**
     * 获取聚合函数类型
     * <p>
     * 仅当表达式是单一顶层聚合时返回类型（如 "SUM", "AVG"）。
     * 复合聚合表达式如 "sum(a) + count(*)" 返回 null。
     * </p>
     *
     * @return 聚合类型，或 null
     */
    public String getAggregationType() {
        return sqlFragment != null ? sqlFragment.getAggregationType() : null;
    }

    @Override
    public String toString() {
        return "CalculatedJdbcColumn{" +
                "name='" + name + '\'' +
                ", caption='" + caption + '\'' +
                ", sql='" + sqlFragment.getSql() + '\'' +
                '}';
    }
}
