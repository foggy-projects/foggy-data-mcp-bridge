package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.dataset.db.model.def.measure.DbFormulaDef;
import io.swagger.annotations.ApiModelProperty;

import java.math.BigDecimal;

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

    default BigDecimal getSemanticScaleFactor() {
        return null;
    }

    default String getSemanticUnit() {
        return null;
    }

    default String getSemanticUnitLabel() {
        return null;
    }
}
