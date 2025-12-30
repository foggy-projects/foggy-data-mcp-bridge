package com.foggyframework.dataset.db.model.spi.support;

import com.foggyframework.core.AbstractDecorate;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbColumnType;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.table.SqlColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.ApplicationContext;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AggregationDbColumn extends AbstractDecorate implements DbColumn {

    QueryObject queryObject;

    String alias;

    String declare;
    /**
     * JdbcColumnType
     */
    DbColumnType type;

    String groupByName;

    String description;

    /**
     * 聚合类型（如 SUM/AVG/COUNT/MAX/MIN）
     * <p>
     * 如果为 null 或 NONE，表示这是一个分组列（group by字段）；
     * 否则表示这是一个聚合列。
     * </p>
     */
    DbAggregation aggregation;

    @Override
    public Object getExtData() {
        return null;
    }

    @Override
    public AiObject getAi() {
        return null;
    }

    public AggregationDbColumn(QueryObject queryObject, String alias, String declare) {
        this.queryObject = queryObject;
        this.alias = alias;
        this.declare = declare;
        this.groupByName = declare;
        this.aggregation = DbAggregation.NONE;
    }

    public AggregationDbColumn(QueryObject queryObject, String alias, String declare, DbColumnType type) {
        this.queryObject = queryObject;
        this.alias = alias;
        this.declare = declare;
        this.type = type;
        this.groupByName = declare;
        this.aggregation = DbAggregation.NONE;
    }

    /**
     * 带聚合类型的构造函数
     * <p>
     * 推荐使用此构造函数，明确指定聚合类型，避免后续通过字符串解析判断。
     * </p>
     * <p>
     * 注意：只有分组列（aggregation = NONE）才会设置 groupByName，
     * 聚合列（SUM/AVG/COUNT/等）的 groupByName 为 null，不会出现在 GROUP BY 子句中。
     * </p>
     */
    public AggregationDbColumn(QueryObject queryObject, String alias, String declare,
                               DbColumnType type, DbAggregation aggregation) {
        this.queryObject = queryObject;
        this.alias = alias;
        this.declare = declare;
        this.type = type;
        // 只有分组列（NONE）才设置 groupByName，聚合列为 null
        this.groupByName = (aggregation == DbAggregation.NONE) ? declare : null;
        this.aggregation = aggregation;
    }

    @Override
    public String getDeclare(ApplicationContext appCtx,String alias) {
        return declare;
    }
    @Override
    public SqlColumn getSqlColumn() {
        return null;
    }

    @Override
    public String getCaption() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }


    @Override
    public boolean _isDeprecated() {
        return false;
    }

    public ObjectTransFormatter<?> getFormatter() {
        return null;
    }

    @Override
    public DbColumnType getType() {
        return type;
    }

    /**
     * 返回聚合类型
     * <p>
     * 如果为 null 或 NONE，表示这是分组列；
     * 否则表示这是聚合列（SUM/AVG/COUNT/MAX/MIN）。
     * </p>
     */
    @Override
    public DbAggregation getAggregation() {
        return aggregation;
    }
}
