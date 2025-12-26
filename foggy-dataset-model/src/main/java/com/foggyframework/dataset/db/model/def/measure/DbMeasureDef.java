package com.foggyframework.dataset.db.model.def.measure;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.DbDefSupport;
import com.foggyframework.dataset.db.model.impl.measure.DbMeasureSupport;
import com.foggyframework.dataset.db.model.spi.DbColumnType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Data
public class DbMeasureDef extends DbDefSupport {

    String column;

    String alias;

    /**
     * JdbcColumnType
     */
    String type;

    String aggregation;

    @ApiModelProperty("公式描述")
    DbFormulaDef formulaDef;

    public void apply(DbMeasureSupport measure) {
        super.apply(measure);
        BeanUtils.copyProperties(this, measure, "type"); // 排除 type，因为类型不同
        // 手动转换 type
        if (StringUtils.isNotEmpty(type)) {
            measure.setType(DbColumnType.fromCode(type));
        }
    }
}
