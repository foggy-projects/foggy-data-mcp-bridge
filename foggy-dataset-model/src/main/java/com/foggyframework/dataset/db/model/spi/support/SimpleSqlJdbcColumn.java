package com.foggyframework.dataset.db.model.spi.support;

import com.foggyframework.core.AbstractDecorate;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbColumnType;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.table.SqlColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimpleSqlJdbcColumn extends AbstractDecorate implements DbColumn {

    QueryObject queryObject;

    SqlColumn sqlColumn;

    String alias;

    String name;

    String caption;

//    String description;

    @Override
    public DbColumnType getType() {
        return null;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public boolean _isDeprecated() {
        return false;
    }


    @Override
    public Object getExtData() {
        return null;
    }

    @Override
    public AiObject getAi() {
        return null;
    }


    
}
