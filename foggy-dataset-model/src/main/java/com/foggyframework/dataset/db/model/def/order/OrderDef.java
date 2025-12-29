package com.foggyframework.dataset.db.model.def.order;

import com.foggyframework.dataset.db.model.proxy.ColumnRef;
import com.foggyframework.dataset.db.model.proxy.DimensionProxy;
import lombok.Data;

@Data
public class OrderDef {
    String name;

    /**
     * 字段引用（V2 格式）
     * <ul>
     *   <li>V1 格式：使用 name 字段</li>
     *   <li>V2 格式：{@link ColumnRef} 或 {@link DimensionProxy} 类型</li>
     * </ul>
     */
    Object ref;

    /**
     * desc or asc
     */
    String order;

    boolean nullLast;

    boolean nullFirst;

    /**
     * 获取排序字段名称（别名格式，使用 _ 分隔）
     * <p>优先使用 ref（支持 V2 格式），其次使用 name
     *
     * @return 字段名称，如 "product_category$categoryId"
     */
    public String getName() {
        if (ref != null) {
            if (ref instanceof String) {
                return (String) ref;
            }
            if (ref instanceof ColumnRef columnRef) {
                return columnRef.getAliasRef();
            }
            if (ref instanceof DimensionProxy dimensionProxy) {
                return dimensionProxy.getAliasPath();
            }
            return ref.toString();
        }
        return name;
    }
}
