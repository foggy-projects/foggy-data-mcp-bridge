package com.foggyframework.dataset.jdbc.model.impl.query;

import com.foggyframework.dataset.jdbc.model.impl.JdbcObjectSupport;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryCondition;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryCondType;

import lombok.Data;

@Data
public abstract class JdbcQueryConditionSupport extends JdbcObjectSupport implements JdbcQueryCondition {

    String field;
    /**
     * dict/dim/date/common
     * JdbcQueryCondType
     */
    JdbcQueryCondType type;

    String queryType;


}
