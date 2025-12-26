package com.foggyframework.dataset.db.model.impl;

import com.foggyframework.core.AbstractDecorate;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbObject;
import com.foggyframework.dataset.db.table.SqlColumn;
import lombok.Data;

@Data
public abstract class DbColumnSupport extends AbstractDecorate implements DbColumn, DbObject {

    protected SqlColumn sqlColumn;

    public DbColumnSupport(SqlColumn sqlColumn) {
        this.sqlColumn = sqlColumn;
    }

    @Override
    public boolean _isDeprecated() {
        return false;
    }


}
