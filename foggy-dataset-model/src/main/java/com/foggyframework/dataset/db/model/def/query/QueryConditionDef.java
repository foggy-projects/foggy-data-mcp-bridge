package com.foggyframework.dataset.db.model.def.query;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.impl.query.DbQueryConditionImpl;
import com.foggyframework.dataset.db.model.spi.DbQueryCondType;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Data
public class QueryConditionDef {

    String name;

    String field;

    String column;

    String queryType;

    String type;

    public void apply(DbQueryConditionImpl cond) {
        BeanUtils.copyProperties(this, cond, "type"); // 排除 type
        // 手动转换 type
        if (StringUtils.isNotEmpty(type)) {
            try {
                cond.setType(DbQueryCondType.valueOf(type.toUpperCase()));
            } catch (IllegalArgumentException e) {
                // 如果 type 不是有效的枚举值，忽略
            }
        }
    }
}
