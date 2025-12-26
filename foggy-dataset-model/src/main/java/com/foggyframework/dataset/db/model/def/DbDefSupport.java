package com.foggyframework.dataset.db.model.def;

import com.foggyframework.core.utils.NumberUtils;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.impl.DbObjectSupport;
import com.foggyframework.dataset.db.model.spi.DbObject;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

@Data
public abstract class DbDefSupport {

    String name;

    String caption;

    String description;

    @ApiModelProperty("标记为不推荐使用后，模型前端再配置")
    boolean deprecated;

    @ApiModelProperty("扩展数据")

    Map<String, Object> extData;

    AiDef ai;

    public void apply(DbObjectSupport dbObjectSupport) {
        dbObjectSupport.setName(name);
        dbObjectSupport.setCaption(caption);
        dbObjectSupport.setDescription(description);
        if (deprecated) {
            dbObjectSupport.setFlag(NumberUtils.addFlag(dbObjectSupport.getFlag(), DbObject.FLAG_DEPRECATED));
        }

        if(ai!=null){
            dbObjectSupport.setAi(AiObject.of(ai));
        }
    }
}
