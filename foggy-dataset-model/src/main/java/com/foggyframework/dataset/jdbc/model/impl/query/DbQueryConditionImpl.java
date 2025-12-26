package com.foggyframework.dataset.jdbc.model.impl.query;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.spi.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DbQueryConditionImpl extends DbQueryConditionSupport {

    QueryModel queryModel;

    DbColumn jdbcColumn;

    DbDimension dimension;

    DbProperty property;

    @Override
    public String getField() {
        return StringUtils.isEmpty(field) ? jdbcColumn.getField() : field;
    }

    @Override
    public String getFormat() {
        return property==null?null:property.getFormat();
    }
    @Override
    public String getValueFormat() {
        return property==null?null:property.getFormat();
    }

    
}
