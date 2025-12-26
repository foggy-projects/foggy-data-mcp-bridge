package com.foggyframework.dataset.jdbc.model.def.dict;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字典定义
 *
 * 用于在 fsscript 中定义可复用的数据字典，避免为简单枚举值创建维度表。
 *
 * 使用示例：
 * <pre>
 * import { registerDict } from '@jdbcModelDictService';
 *
 * export const dicts = {
 *     payment_method: registerDict({
 *         id: 'payment_method',
 *         caption: '支付方式',
 *         items: [
 *             { value: '1', label: '现付' },
 *             { value: '2', label: '到付' }
 *         ]
 *     })
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DbDictDef {

    /**
     * 字典唯一标识，用于缓存和元数据去重
     */
    @ApiModelProperty("字典唯一标识")
    String id;

    /**
     * 字典显示名称
     */
    @ApiModelProperty("字典显示名称")
    String caption;

    /**
     * 字典描述
     */
    @ApiModelProperty("字典描述")
    String description;

    /**
     * 字典项列表
     */
    @ApiModelProperty("字典项列表")
    List<DbDictItemDef> items;

    // ========== 运行时缓存 ==========

    /**
     * value -> label 映射缓存
     */
    private transient Map<Object, String> valueToLabelMap;

    /**
     * label -> value 映射缓存
     */
    private transient Map<String, Object> labelToValueMap;

    /**
     * 获取 value -> label 映射
     */
    public Map<Object, String> getValueToLabelMap() {
        if (valueToLabelMap == null) {
            valueToLabelMap = new ConcurrentHashMap<>();
            if (items != null) {
                for (DbDictItemDef item : items) {
                    valueToLabelMap.put(item.getValue(), item.getLabel());
                }
            }
        }
        return valueToLabelMap;
    }

    /**
     * 获取 label -> value 映射
     */
    public Map<String, Object> getLabelToValueMap() {
        if (labelToValueMap == null) {
            labelToValueMap = new ConcurrentHashMap<>();
            if (items != null) {
                for (DbDictItemDef item : items) {
                    labelToValueMap.put(item.getLabel(), item.getValue());
                }
            }
        }
        return labelToValueMap;
    }

    /**
     * 根据 value 获取 label
     */
    public String getLabelByValue(Object value) {
        if (value == null) {
            return null;
        }
        return getValueToLabelMap().get(value);
    }

    /**
     * 根据 label 获取 value
     */
    public Object getValueByLabel(String label) {
        if (label == null) {
            return null;
        }
        return getLabelToValueMap().get(label);
    }

    /**
     * 获取字典项的简短描述，用于元数据生成
     * 格式: value1=label1, value2=label2, ...
     */
    public String getItemsSummary() {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            DbDictItemDef item = items.get(i);
            sb.append(item.getValue()).append("=").append(item.getLabel());
        }
        return sb.toString();
    }
}
