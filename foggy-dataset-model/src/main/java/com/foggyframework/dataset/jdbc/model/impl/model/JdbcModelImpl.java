package com.foggyframework.dataset.jdbc.model.impl.model;

import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;

import javax.sql.DataSource;

@Getter
public class JdbcModelImpl extends JdbcModelSupport {
    Fsscript fScript;

    DataSource dataSource;

    public JdbcModelImpl() {

    }

    public JdbcModelImpl(DataSource dataSource,Fsscript fScript) {
        this.dataSource = dataSource;
        this.fScript = fScript;
    }

    
}
