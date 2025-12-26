package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.dataset.jdbc.model.def.DbModelDef;
import com.foggyframework.fsscript.parser.spi.Fsscript;

public interface TableModelLoader {


    TableModel load(Fsscript fScript, DbModelDef def, Bundle bundle);

    String getTypeName();
}
