package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.dataset.jdbc.model.def.measure.JdbcFormulaDef;

public interface JdbcMeasureColumn extends JdbcColumn {


    JdbcMeasure getJdbcMeasure();


    JdbcFormulaDef getFormulaDef();
}
