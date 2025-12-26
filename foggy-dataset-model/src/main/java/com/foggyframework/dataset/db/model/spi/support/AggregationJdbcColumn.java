package com.foggyframework.dataset.db.model.spi.support;

import com.foggyframework.core.AbstractDecorate;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbColumnType;
import com.foggyframework.dataset.db.model.spi.QueryObject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.ApplicationContext;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AggregationJdbcColumn extends AbstractDecorate implements DbColumn {

    QueryObject queryObject;

    String alias;

    String declare;
    /**
     * JdbcColumnType
     */
    DbColumnType type;

    String groupByName;

    String description;

    @Override
    public Object getExtData() {
        return null;
    }

    @Override
    public AiObject getAi() {
        return null;
    }

    public AggregationJdbcColumn(QueryObject queryObject, String alias, String declare) {
        this.queryObject = queryObject;
        this.alias = alias;
        this.declare = declare;
        this.groupByName = declare;
    }

    public AggregationJdbcColumn(QueryObject queryObject, String alias, String declare, DbColumnType type) {
        this.queryObject = queryObject;
        this.alias = alias;
        this.declare = declare;
        this.type = type;
        this.groupByName = declare;
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
}
