package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.dataset.jdbc.model.def.measure.DbFormulaDef;

public interface DbMeasureColumn extends DbColumn {


    DbMeasure getJdbcMeasure();


    DbFormulaDef getFormulaDef();
}
