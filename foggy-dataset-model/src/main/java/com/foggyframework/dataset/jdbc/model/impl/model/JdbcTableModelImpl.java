package com.foggyframework.dataset.jdbc.model.impl.model;

import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;

import javax.sql.DataSource;

@Getter
public class JdbcTableModelImpl extends TableModelSupport {
    Fsscript fScript;

    DataSource dataSource;

    public JdbcTableModelImpl() {

    }

    public JdbcTableModelImpl(DataSource dataSource, Fsscript fScript) {
        this.dataSource = dataSource;
        this.fScript = fScript;
    }

    
}
