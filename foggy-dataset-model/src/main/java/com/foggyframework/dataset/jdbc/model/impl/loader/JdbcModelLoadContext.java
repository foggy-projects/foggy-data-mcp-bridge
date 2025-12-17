package com.foggyframework.dataset.jdbc.model.impl.loader;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.dataset.jdbc.model.def.JdbcModelDef;
import com.foggyframework.dataset.jdbc.model.spi.JdbcModel;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.sql.DataSource;

@Data
@NoArgsConstructor
public class JdbcModelLoadContext {
    DataSource dataSource;

    JdbcModelDef def;

    JdbcModel jdbcModel;

    Bundle bundle;

    public JdbcModelLoadContext(DataSource dataSource, JdbcModelDef def, JdbcModel jdbcModel, Bundle bundle) {
        this.dataSource = dataSource;
        this.def = def;
        this.jdbcModel = jdbcModel;
        this.bundle = bundle;
    }
}
