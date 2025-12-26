package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.dataset.db.model.def.measure.DbFormulaDef;

import io.swagger.annotations.ApiModelProperty;

public interface DbMeasure extends DbObject {
    DbColumn getJdbcColumn();

    /**
     * JdbcColumnType
     * @return
     */
    DbColumnType getType();

    DbAggregation getAggregation();

    @ApiModelProperty("公式描述")
    DbFormulaDef getFormulaDef();
}
