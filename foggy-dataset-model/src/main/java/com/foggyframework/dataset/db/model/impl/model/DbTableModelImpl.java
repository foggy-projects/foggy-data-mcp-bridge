package com.foggyframework.dataset.db.model.impl.model;

import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;

import javax.sql.DataSource;

@Getter
public class DbTableModelImpl extends TableModelSupport {
    Fsscript fScript;

    DataSource dataSource;

    public DbTableModelImpl() {

    }

    public DbTableModelImpl(DataSource dataSource, Fsscript fScript) {
        this.dataSource = dataSource;
        this.fScript = fScript;
    }

    
}
