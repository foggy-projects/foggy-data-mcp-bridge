package com.foggyframework.dataset.jdbc.model.engine.expression;

import com.foggyframework.dataset.jdbc.model.spi.JdbcColumnType;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryColumn;
import com.foggyframework.dataset.jdbc.model.spi.support.CalculatedJdbcColumn;

import java.util.Set;

/**
 * MongoDB 计算字段列到 CalculatedJdbcColumn 的适配器
 * <p>
 * 由于接口返回类型是 CalculatedJdbcColumn（为了兼容 JDBC 实现），
 * 但 MongoDB 使用 MongoCalculatedColumn，所以需要这个适配器。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
public class MongoCalculatedColumnAdapter extends CalculatedJdbcColumn {

    private final MongoCalculatedColumn mongoColumn;

    public MongoCalculatedColumnAdapter(MongoCalculatedColumn mongoColumn) {
        // 创建一个空的 SqlFragment（用于兼容基类）
        super(mongoColumn.getName(), mongoColumn.getCaption(), createDummySqlFragment(mongoColumn));
        this.mongoColumn = mongoColumn;
        this.setDescription(mongoColumn.getDescription());
    }

    /**
     * 创建一个虚拟的 SqlFragment（用于兼容基类构造函数）
     * 实际不会使用这个 SqlFragment
     */
    private static com.foggyframework.dataset.jdbc.model.engine.expression.SqlFragment createDummySqlFragment(MongoCalculatedColumn mongoColumn) {
        com.foggyframework.dataset.jdbc.model.engine.expression.SqlFragment dummy = new com.foggyframework.dataset.jdbc.model.engine.expression.SqlFragment();
        dummy.setSql("$" + mongoColumn.getName());
        dummy.setInferredType(mongoColumn.getType());
        dummy.setHasAggregate(mongoColumn.hasAggregate());
        dummy.setAggregationType(mongoColumn.getAggregationType());
        return dummy;
    }

    /**
     * 获取原始的 MongoDB 计算字段列
     */
    public MongoCalculatedColumn getMongoColumn() {
        return mongoColumn;
    }

    /**
     * 获取 MongoDB 表达式
     */
    public Object getMongoExpression() {
        return mongoColumn.getMongoExpression();
    }

    @Override
    public JdbcColumnType getType() {
        return mongoColumn.getType();
    }

    @Override
    public Set<JdbcQueryColumn> getReferencedColumns() {
        return mongoColumn.getReferencedColumns();
    }

    @Override
    public boolean hasAggregate() {
        return mongoColumn.hasAggregate();
    }

    @Override
    public String getAggregationType() {
        return mongoColumn.getAggregationType();
    }

    @Override
    public String toString() {
        return "MongoCalculatedColumnAdapter{" +
                "name='" + getName() + '\'' +
                ", mongoColumn=" + mongoColumn +
                '}';
    }
}
