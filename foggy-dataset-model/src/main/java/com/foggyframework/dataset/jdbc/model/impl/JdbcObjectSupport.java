package com.foggyframework.dataset.jdbc.model.impl;

import com.foggyframework.core.AbstractDecorate;
import com.foggyframework.core.utils.NumberUtils;
import com.foggyframework.dataset.jdbc.model.spi.DbObject;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public abstract class JdbcObjectSupport extends AbstractDecorate implements DbObject {

    protected String name;

    protected String caption;

    protected String description;

    protected long flag;

    @ApiModelProperty("扩展数据")
    protected Object extData;

    protected AiObject ai;

    @Override
    public boolean _isDeprecated() {
        return NumberUtils.hasFlag(flag, DbObject.FLAG_DEPRECATED);
    }

//    @Override
//    public boolean _isNotImportant() {
//        return NumberUtils.hasFlag(flag, JdbcObject.FLAG_NOT_IMPORTANT);
//    }

}
