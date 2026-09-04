package com.foggyframework.dataset.model.def.query;

import com.foggyframework.dataset.model.def.AiDef;
import com.foggyframework.dataset.model.path.DimensionPath;
import com.foggyframework.dataset.model.proxy.ColumnRef;
import com.foggyframework.dataset.model.proxy.DimensionProxy;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SelectColumnDef {

    AiDef ai;

    /**
     * 列名（name）
     * <p>列的唯一标识，用于在 QM 中查找列（通过 findJdbcQueryColumnByName 查找）。
     * <p>如果未设置，默认使用 ref 的值。
     */
    String name;

    /**
     * 字段引用
     * <ul>
     *   <li>V1 格式：String 类型，如 "orderId"</li>
     *   <li>V2 格式：{@link ColumnRef} 或 {@link DimensionProxy} 类型</li>
     * </ul>
     */
    Object ref;

    /**
     * 别名（alias）
     * <p>用户在 QM 中定义的列别名，用于避免多模型 JOIN 时重名问题。
     * <p>示例：在 QM 中定义 { ref: dc.customerType, alias: 'custType' }，
     * 则 alias 为 'custType'，用于在查询结果中重命名该字段。
     * <p>作用：
     * <ul>
     *   <li>在 SQL SELECT 子句中作为列别名（AS alias）</li>
     *   <li>在查询条件中作为字段名（WHERE alias = ?）</li>
     *   <li>在返回结果中作为字段名</li>
     * </ul>
     * <p>如果未设置，默认使用 ref 的值。
     */
    String alias;

    String caption;

    /** QM 计算字段公式 */
    String formula;
    /** 计算字段返回类型 */
    String type;
    /** 计算字段空值默认值，用于将聚合或表达式结果包裹为 COALESCE(expr, emptyDefault) */
    Object emptyDefault;
    /** 窗口函数 PARTITION BY */
    List<String> partitionBy;
    /** 窗口函数 ORDER BY */
    List<Map<String, Object>> windowOrderBy;
    /** 窗口帧 */
    String windowFrame;
    /** 业务描述（QM formula items 的使用说明，用于 AI/LLM 元数据输出） */
    String description;

    /** 字段扩展元数据；运行时仅由具体消费者解释白名单键。 */
    Map<String, Object> extData;

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
            return columnRef.getQualifiedAliasRef();
        }
        if (ref instanceof DimensionProxy dimensionProxy) {
            return dimensionProxy.getQualifiedAliasPath();
        }
        return ref.toString();
    }

    /**
     * 获取用于查找的 ref（使用 . 分隔路径）
     * <p>用于在 TableModel 中查找列/维度
     *
     * @return ref 字符串，如 "product.category$categoryId"
     */
    public String getRefForLookup() {
        if (ref == null) {
            return null;
        }
        if (ref instanceof String) {
            return (String) ref;
        }
        if (ref instanceof ColumnRef columnRef) {
            return columnRef.getQualifiedLookupRef();
        }
        if (ref instanceof DimensionProxy dimensionProxy) {
            return dimensionProxy.getQualifiedLookupPath();
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
     * 获取 DimensionPath
     * <p>从 ColumnRef 或 DimensionProxy 提取 DimensionPath
     *
     * @return DimensionPath 或 null
     */
    public DimensionPath getRefAsDimensionPath() {
        if (ref instanceof ColumnRef columnRef) {
            return columnRef.getDimensionPath();
        }
        if (ref instanceof DimensionProxy dimensionProxy) {
            return dimensionProxy.getDimensionPath();
        }
        if (ref instanceof String str) {
            return DimensionPath.parse(str);
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
