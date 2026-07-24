package com.foggyframework.dataset.model.def.measure;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.model.def.DbDefSupport;
import com.foggyframework.dataset.model.impl.measure.DbMeasureSupport;
import com.foggyframework.dataset.model.spi.DbColumnType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.util.Map;

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

    @ApiModelProperty(value = "方言专属公式", notes = "key 为方言标识（postgresql/mysql/sqlserver/sqlite/oracle），value 为该方言的公式定义。优先级高于 formulaDef")
    Map<String, DbFormulaDef> dialectFormulaDef;

    @ApiModelProperty(value = "语义缩放因子", notes = "用于物理金额列或公式结果按语义单位读取，例如分转元配置 100")
    BigDecimal semanticScaleFactor;

    @ApiModelProperty(value = "语义单位编码", notes = "例如 CNY、USD、percent")
    String semanticUnit;

    @ApiModelProperty(value = "语义单位显示名", notes = "例如 元、美元、百分比")
    String semanticUnitLabel;

    public void apply(DbMeasureSupport measure) {
        super.apply(measure);
        BeanUtils.copyProperties(this, measure, "type"); // 排除 type，因为类型不同
        // 手动转换 type
        if (StringUtils.isNotEmpty(type)) {
            measure.setType(DbColumnType.fromCode(type));
        }
    }
}
