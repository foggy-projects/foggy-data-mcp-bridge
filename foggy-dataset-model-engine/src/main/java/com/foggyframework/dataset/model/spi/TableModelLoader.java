package com.foggyframework.dataset.model.spi;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.dataset.model.def.DbModelDef;
import com.foggyframework.fsscript.parser.spi.Fsscript;

public interface TableModelLoader {

    TableModel load(Fsscript fScript, DbModelDef def, Bundle bundle);

    String getTypeName();
}
