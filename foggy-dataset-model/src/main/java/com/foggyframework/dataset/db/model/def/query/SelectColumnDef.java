package com.foggyframework.dataset.db.model.def.query;

import com.foggyframework.dataset.db.model.def.AiDef;
import com.foggyframework.dataset.db.model.proxy.ColumnRef;
import com.foggyframework.dataset.db.model.proxy.DimensionProxy;
import lombok.Data;

import java.util.Map;

@Data
public class SelectColumnDef {

    AiDef ai;

    String name;

    /**
     * 字段引用
     * <ul>
     *   <li>V1 格式：String 类型，如 "orderId"</li>
     *   <li>V2 格式：{@link ColumnRef} 或 {@link DimensionProxy} 类型</li>
     * </ul>
     */
    Object ref;

    String alias;

    String field;
    String caption;

    /**
     * 获取字符串形式的 ref（别名格式，使用 _ 分隔）
     *
     * @return ref 字符串，如 "product_category$categoryId"
     */
    public String getRefAsString() {
        if (ref == null) {
            return null;
        }
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

    /**
     * 获取 ColumnRef 类型的 ref
     * <p>如果 ref 是 DimensionProxy，会自动转换为 ColumnRef
     *
     * @return ColumnRef 或 null
     */
    public ColumnRef getRefAsColumnRef() {
        if (ref instanceof ColumnRef columnRef) {
            return columnRef;
        }
        if (ref instanceof DimensionProxy dimensionProxy) {
            return dimensionProxy.toColumnRef();
        }
        return null;
    }

    /**
     * 判断 ref 是否为 ColumnRef 或 DimensionProxy 类型
     *
     * @return 如果是 ColumnRef 或 DimensionProxy 返回 true
     */
    public boolean isColumnRefType() {
        return ref instanceof ColumnRef || ref instanceof DimensionProxy;
    }

    /**
     * 兼容旧代码：获取 ref（返回 String）
     * @deprecated 使用 {@link #getRefAsString()} 代替
     */
    @Deprecated
    public String getRef() {
        return getRefAsString();
    }
}
