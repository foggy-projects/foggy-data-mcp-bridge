package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.dataset.jdbc.model.def.measure.JdbcFormulaDef;

import io.swagger.annotations.ApiModelProperty;

public interface JdbcMeasure extends DbObject {
    JdbcColumn getJdbcColumn();

    /**
     * JdbcColumnType
     * @return
     */
    JdbcColumnType getType();

    JdbcAggregation getAggregation();

    @ApiModelProperty("公式描述")
    JdbcFormulaDef getFormulaDef();
}
