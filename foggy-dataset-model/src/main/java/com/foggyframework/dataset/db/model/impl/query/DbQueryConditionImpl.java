package com.foggyframework.dataset.db.model.impl.query;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbDimension;
import com.foggyframework.dataset.db.model.spi.DbProperty;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DbQueryConditionImpl extends DbQueryConditionSupport {

    QueryModel queryModel;

    DbColumn column;

    DbDimension dimension;

    DbProperty property;

    @Override
    public String getField() {
        return StringUtils.isEmpty(field) ? column.getField() : field;
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
