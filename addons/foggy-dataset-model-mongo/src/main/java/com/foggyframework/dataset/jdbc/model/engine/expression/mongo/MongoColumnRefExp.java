package com.foggyframework.dataset.jdbc.model.engine.expression.mongo;

import com.foggyframework.dataset.jdbc.model.engine.expression.MongoExpContext;
import com.foggyframework.dataset.jdbc.model.engine.expression.MongoFragment;
import com.foggyframework.dataset.jdbc.model.engine.expression.MongoCalculatedColumn;
import com.foggyframework.dataset.jdbc.model.spi.DbQueryColumn;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * MongoDB 列引用表达式
 * <p>
 * 将列名解析为 MongoDB 字段引用，格式为 "$fieldName"。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
@Getter
public class MongoColumnRefExp extends AbstractExp<String> {

    private static final long serialVersionUID = 1L;

    private final String columnName;

    public MongoColumnRefExp(String columnName) {
        super(columnName);
        this.columnName = columnName;
    }

    @Override
    public Object evalValue(ExpEvaluator context) {
        MongoExpContext expContext = (MongoExpContext) context.getVar(MongoExpContext.CONTEXT_KEY);

        if (expContext == null) {
            throw new RuntimeException("MongoExpContext not found in evaluator");
        }

        // 检查是否是已注册的计算字段
        MongoCalculatedColumn calculatedColumn = expContext.getCalculatedColumn(columnName);
        if (calculatedColumn != null) {
            calculatedColumn.setHasRef(true);
            // 返回计算字段的表达式片段（合并依赖）
            MongoFragment f = new MongoFragment();
            f.setExpression(calculatedColumn.getMongoExpression());
            f.getReferencedColumns().addAll(calculatedColumn.getReferencedColumns());
            f.setInferredType(calculatedColumn.getType());
            f.setHasAggregate(calculatedColumn.hasAggregate());
            f.setAggregationType(calculatedColumn.getAggregationType());
            return f;
        }

        // 解析普通列
        DbQueryColumn column = expContext.tryResolveColumn(columnName);
        if (column != null) {
            String fieldName = expContext.resolveFieldName(columnName);
            return MongoFragment.ofColumn(column, fieldName);
        }

        // 列不存在
        throw new RuntimeException("Column not found: " + columnName);
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return MongoFragment.class;
    }

    @Override
    public String toString() {
        return "[MongoColumnRef:" + columnName + "]";
    }
}
