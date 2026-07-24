package com.foggyframework.dataset.model.impl.query;

import com.foggyframework.dataset.model.impl.DbObjectSupport;
import com.foggyframework.dataset.model.spi.DbQueryCondType;
import com.foggyframework.dataset.model.spi.DbQueryCondition;
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
