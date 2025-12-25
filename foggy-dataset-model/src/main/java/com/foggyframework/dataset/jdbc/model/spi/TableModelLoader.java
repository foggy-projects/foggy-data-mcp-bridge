package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.dataset.jdbc.model.def.JdbcModelDef;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.sql.DataSource;

public interface TableModelLoader {


    JdbcModel load(Fsscript fScript, JdbcModelDef def, Bundle bundle);

    String getTypeName();
}
