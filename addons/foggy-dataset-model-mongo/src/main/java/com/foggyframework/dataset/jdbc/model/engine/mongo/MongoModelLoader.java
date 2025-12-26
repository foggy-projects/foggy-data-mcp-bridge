package com.foggyframework.dataset.jdbc.model.engine.mongo;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.dataset.jdbc.model.def.JdbcModelDef;
import com.foggyframework.dataset.jdbc.model.spi.JdbcModel;
import com.foggyframework.fsscript.parser.spi.Fsscript;

public interface MongoModelLoader {
    JdbcModel load(Fsscript fScript, JdbcModelDef def, Bundle bundle);
}
