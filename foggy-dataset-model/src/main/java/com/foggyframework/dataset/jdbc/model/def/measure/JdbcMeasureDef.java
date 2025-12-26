package com.foggyframework.dataset.jdbc.model.def.measure;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.def.JdbcDefSupport;
import com.foggyframework.dataset.jdbc.model.impl.measure.JdbcMeasureSupport;
import com.foggyframework.dataset.jdbc.model.spi.DbColumnType;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Data
public class JdbcMeasureDef extends JdbcDefSupport {

    String column;

    String alias;

    /**
     * JdbcColumnType
     */
    String type;

    String aggregation;

    @ApiModelProperty("公式描述")
    DbFormulaDef formulaDef;

    public void apply(JdbcMeasureSupport measure) {
        super.apply(measure);
        BeanUtils.copyProperties(this, measure, "type"); // 排除 type，因为类型不同
        // 手动转换 type
        if (StringUtils.isNotEmpty(type)) {
            measure.setType(DbColumnType.fromCode(type));
        }
    }
}
