package com.foggyframework.dataset.jdbc.model.impl.query;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.spi.JdbcColumn;
import com.foggyframework.dataset.jdbc.model.spi.JdbcDimension;
import com.foggyframework.dataset.jdbc.model.spi.JdbcProperty;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryModel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JdbcQueryConditionImpl extends JdbcQueryConditionSupport {

    JdbcQueryModel queryModel;

    JdbcColumn jdbcColumn;

    JdbcDimension dimension;

    JdbcProperty property;

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
