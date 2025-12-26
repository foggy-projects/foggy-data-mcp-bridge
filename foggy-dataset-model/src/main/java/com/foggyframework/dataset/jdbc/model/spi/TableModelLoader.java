package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.dataset.jdbc.model.def.JdbcModelDef;
import com.foggyframework.fsscript.parser.spi.Fsscript;

public interface TableModelLoader {


    TableModel load(Fsscript fScript, JdbcModelDef def, Bundle bundle);

    String getTypeName();
}
