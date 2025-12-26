package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.dataset.db.model.def.measure.DbFormulaDef;

public interface DbMeasureColumn extends DbColumn {


    DbMeasure getJdbcMeasure();


    DbFormulaDef getFormulaDef();
}
