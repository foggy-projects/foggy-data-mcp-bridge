package com.foggyframework.dataset.jdbc.model.impl.query;

import com.foggyframework.dataset.jdbc.model.impl.DbObjectSupport;
import com.foggyframework.dataset.jdbc.model.spi.DbQueryCondition;
import com.foggyframework.dataset.jdbc.model.spi.DbQueryCondType;

import lombok.Data;

@Data
public abstract class DbQueryConditionSupport extends DbObjectSupport implements DbQueryCondition {

    String field;
    /**
     * dict/dim/date/common
     * JdbcQueryCondType
     */
    DbQueryCondType type;

    String queryType;


}
