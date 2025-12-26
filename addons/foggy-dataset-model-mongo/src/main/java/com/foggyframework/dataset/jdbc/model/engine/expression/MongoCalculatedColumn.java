package com.foggyframework.dataset.jdbc.model.engine.expression;

import com.foggyframework.core.AbstractDecorate;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.jdbc.model.impl.AiObject;
import com.foggyframework.dataset.jdbc.model.spi.DbColumn;
import com.foggyframework.dataset.jdbc.model.spi.DbColumnType;
import com.foggyframework.dataset.jdbc.model.spi.DbQueryColumn;
import com.foggyframework.dataset.jdbc.model.spi.DbQueryCondition;
import com.foggyframework.dataset.jdbc.model.spi.QueryObject;
import lombok.Data;
import org.bson.Document;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.Set;

/**
 * MongoDB 计算字段列
 * <p>
 * 用于表示通过表达式动态计算的 MongoDB 列。
 * 通过 MongoFragment 存储计算后的 MongoDB 聚合表达式及其依赖的列。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
@Data
public class MongoCalculatedColumn extends AbstractDecorate implements DbQueryColumn {

    /**
     * 字段名（在 columns 中引用的名称）
     */
    private final String name;

    /**
     * 显示名称
     */
    private final String caption;

    /**
     * MongoDB 表达式片段
     */
    private final MongoFragment mongoFragment;

    /**
     * 描述
     */
    private String description;

    /**
     * 数据类型
     */
    private DbColumnType type;

    /**
     * 是否已被引用
     */
    private boolean hasRef;

    /**
     * 查询对象（可选）
     */
    private QueryObject queryObject;

    public MongoCalculatedColumn(String name, String caption, MongoFragment mongoFragment) {
        this.name = name;
        this.caption = caption != null ? caption : name;
        this.mongoFragment = mongoFragment;
    }

    public MongoCalculatedColumn(String name, String caption, MongoFragment mongoFragment, String description) {
        this(name, caption, mongoFragment);
        this.description = description;
    }

    /**
     * 获取数据类型
     */
    @Override
    public DbColumnType getType() {
        if (type != null) {
            return type;
        }
        return mongoFragment.getInferredType();
    }

    /**
     * 获取 MongoDB 表达式（用于 $addFields）
     *
     * @return MongoDB 表达式对象
     */
    public Object getMongoExpression() {
        return mongoFragment.getExpression();
    }

    /**
     * 获取依赖的列
     */
    public Set<DbQueryColumn> getReferencedColumns() {
        return mongoFragment.getReferencedColumns();
    }

    /**
     * 生成 $addFields 的条目
     *
     * @return Document { fieldName: expression }
     */
    public Document toAddFieldsEntry() {
        return mongoFragment.asAddFieldsEntry(name);
    }

    @Override
    public String getDeclare(ApplicationContext appCtx, String alias) {
        // MongoDB 不使用 SQL 声明，返回字段引用
        return "$" + name;
    }

    @Override
    public String getDeclare() {
        return "$" + name;
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
        DbColumnType inferredType = mongoFragment.getInferredType();
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
     */
    @Override
    public boolean isCalculatedField() {
        return true;
    }

    /**
     * 是否包含聚合函数
     */
    public boolean hasAggregate() {
        return mongoFragment != null && mongoFragment.isHasAggregate();
    }

    /**
     * 获取聚合函数类型
     */
    public String getAggregationType() {
        return mongoFragment != null ? mongoFragment.getAggregationType() : null;
    }

    @Override
    public String toString() {
        return "MongoCalculatedColumn{" +
                "name='" + name + '\'' +
                ", caption='" + caption + '\'' +
                ", expression=" + mongoFragment +
                '}';
    }
}
