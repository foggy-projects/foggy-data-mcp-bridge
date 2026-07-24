package com.foggyframework.dataset.model.spi;

import com.foggyframework.dataset.model.def.measure.DbFormulaDef;

public interface DbMeasureColumn extends DbColumn {


    DbMeasure getJdbcMeasure();


    DbFormulaDef getFormulaDef();
}
