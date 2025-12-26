package com.foggyframework.dataset.db.model.impl.query;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.impl.DbObjectSupport;
import com.foggyframework.dataset.db.model.spi.DbProperty;
import com.foggyframework.dataset.db.model.spi.DbQueryProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DbQueryPropertyImpl extends DbObjectSupport implements DbQueryProperty {

    DbProperty dbProperty;

    DbQueryAccessImpl queryAccess;

    public DbQueryPropertyImpl(DbProperty dbProperty) {
        this.dbProperty = dbProperty;
    }

    @Override
    public AiObject getAi() {
        return ai == null ? dbProperty.getAi() : null;
    }

    @Override
    public String getName() {
        return StringUtils.isEmpty(super.getName())? dbProperty.getName() : super.getName();
    }
    @Override
    public String getCaption() {
        return StringUtils.isEmpty(super.getCaption())? dbProperty.getCaption() : super.getCaption();
    }


    @Override
    public DbProperty getJdbcProperty() {
        return dbProperty;
    }
}
