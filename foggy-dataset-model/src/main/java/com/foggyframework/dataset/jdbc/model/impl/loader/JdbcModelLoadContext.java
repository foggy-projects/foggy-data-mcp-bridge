package com.foggyframework.dataset.jdbc.model.impl.loader;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.dataset.jdbc.model.def.DbModelDef;
import com.foggyframework.dataset.jdbc.model.spi.TableModel;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.sql.DataSource;

@Data
@NoArgsConstructor
public class JdbcModelLoadContext {
    DataSource dataSource;

    DbModelDef def;

    TableModel jdbcModel;

    Bundle bundle;

    public JdbcModelLoadContext(DataSource dataSource, DbModelDef def, TableModel jdbcModel, Bundle bundle) {
        this.dataSource = dataSource;
        this.def = def;
        this.jdbcModel = jdbcModel;
        this.bundle = bundle;
    }
}
