package com.foggyframework.dataset.jdbc.model.def.dict;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 字典项定义
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JdbcDictItemDef {

    /**
     * 字典项的值（存储在数据库中的值）
     */
    Object value;

    /**
     * 字典项的显示标签
     */
    String label;

    /**
     * 字典项的描述（可选）
     */
    String description;

    public JdbcDictItemDef(Object value, String label) {
        this.value = value;
        this.label = label;
    }
}
