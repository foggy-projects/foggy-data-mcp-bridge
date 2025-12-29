package com.foggyframework.dataset.db.model.def.query;

import com.foggyframework.dataset.db.model.def.AiDef;
import com.foggyframework.dataset.db.model.proxy.ColumnRef;
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
     *   <li>V2 格式：{@link ColumnRef} 类型，如 fo.orderId</li>
     * </ul>
     */
    Object ref;

    String alias;

    String field;
    String caption;

    /**
     * 获取字符串形式的 ref
     *
     * @return ref 字符串，如果是 ColumnRef 则返回其 fullRef
     */
    public String getRefAsString() {
        if (ref == null) {
            return null;
        }
        if (ref instanceof String) {
            return (String) ref;
        }
        if (ref instanceof ColumnRef columnRef) {
            return columnRef.getFullRef();
        }
        return ref.toString();
    }

    /**
     * 获取 ColumnRef 类型的 ref（如果是）
     *
     * @return ColumnRef 或 null
     */
    public ColumnRef getRefAsColumnRef() {
        return ref instanceof ColumnRef ? (ColumnRef) ref : null;
    }

    /**
     * 判断 ref 是否为 ColumnRef 类型
     *
     * @return 如果是 ColumnRef 返回 true
     */
    public boolean isColumnRefType() {
        return ref instanceof ColumnRef;
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
