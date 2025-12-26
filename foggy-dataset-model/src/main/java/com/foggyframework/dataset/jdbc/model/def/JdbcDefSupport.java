package com.foggyframework.dataset.jdbc.model.def;

import com.foggyframework.core.utils.NumberUtils;
import com.foggyframework.dataset.jdbc.model.impl.JdbcObjectSupport;
import com.foggyframework.dataset.jdbc.model.spi.DbObject;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import com.foggyframework.dataset.jdbc.model.impl.AiObject;

import java.util.Map;

@Data
public abstract class JdbcDefSupport {

    String name;

    String caption;

    String description;

    @ApiModelProperty("标记为不推荐使用后，模型前端再配置")
    boolean deprecated;

    @ApiModelProperty("扩展数据")

    Map<String, Object> extData;

    AiDef ai;

    public void apply(JdbcObjectSupport jdbcObjectSupport) {
        jdbcObjectSupport.setName(name);
        jdbcObjectSupport.setCaption(caption);
        jdbcObjectSupport.setDescription(description);
        if (deprecated) {
            jdbcObjectSupport.setFlag(NumberUtils.addFlag(jdbcObjectSupport.getFlag(), DbObject.FLAG_DEPRECATED));
        }

        if(ai!=null){
            jdbcObjectSupport.setAi(AiObject.of(ai));
        }
    }
}
