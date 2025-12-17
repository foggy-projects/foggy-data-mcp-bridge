package com.foggyframework.dataset.jdbc.model.def.property;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.def.JdbcDefSupport;
import com.foggyframework.dataset.jdbc.model.def.measure.JdbcFormulaDef;
import com.foggyframework.dataset.jdbc.model.impl.property.JdbcPropertyImpl;
import com.foggyframework.dataset.jdbc.model.spi.JdbcColumnType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Data
public class JdbcPropertyDef extends JdbcDefSupport {

    String alias;

    String column;

    String type;

    String format;

    String aggregationFormula;

    @ApiModelProperty("公式描述")
    JdbcFormulaDef formulaDef;

    @ApiModelProperty(value = "字典引用", notes = "引用通过 registerDict 注册的字典ID，用于将数据库中的值转换为显示标签")
    String dictRef;

    public void apply(JdbcPropertyImpl property) {
        super.apply(property);
        BeanUtils.copyProperties(this, property, "type"); // 排除 type，因为类型不同
        // 手动转换 type
        if (StringUtils.isNotEmpty(type)) {
            property.setType(JdbcColumnType.fromCode(type));
        }
    }
}
