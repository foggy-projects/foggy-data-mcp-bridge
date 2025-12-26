package com.foggyframework.dataset.jdbc.model.impl;

import com.foggyframework.core.AbstractDecorate;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.jdbc.model.spi.JdbcColumn;
import com.foggyframework.dataset.jdbc.model.spi.DbObject;
import lombok.Data;

@Data
public abstract class JdbcColumnSupport extends AbstractDecorate implements JdbcColumn, DbObject {

    protected SqlColumn sqlColumn;

    public JdbcColumnSupport(SqlColumn sqlColumn) {
        this.sqlColumn = sqlColumn;
    }

    @Override
    public boolean _isDeprecated() {
        return false;
    }


}
