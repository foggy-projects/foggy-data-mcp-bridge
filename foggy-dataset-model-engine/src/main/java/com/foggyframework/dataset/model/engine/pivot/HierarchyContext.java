package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Hierarchy 上下文值对象
 *
 * <p>封装 hierarchyMode=tree 检测结果，避免 PivotPipeline.execute()
 * 中的临时变量膨胀。</p>
 */
public class HierarchyContext {

    private static final Logger logger = LoggerFactory.getLogger(HierarchyContext.class);
    private static final HierarchyContext NONE = new HierarchyContext(null, null, null);

    private final AxisField treeAxisField;
    private final String dimName;
    private final String idField;
    private String parentField;

    private HierarchyContext(AxisField treeAxisField, String dimName, String idField) {
        this.treeAxisField = treeAxisField;
        this.dimName = dimName;
        this.idField = idField;
        this.parentField = dimName + "$parentId";
    }

    /**
     * 从 rows 轴字段列表中检测 hierarchyMode=tree
     *
     * @param rows 行轴字段列表
     * @return HierarchyContext（如果没有 tree 字段返回 NONE）
     */
    public static HierarchyContext detect(List<AxisField> rows) {
        if (rows == null) return NONE;

        AxisField treeField = rows.stream()
                .filter(AxisField::isTreeMode)
                .findFirst()
                .orElse(null);

        if (treeField == null) return NONE;

        String field = treeField.getField();
        int dollarIdx = field.indexOf('$');
        String dimName = dollarIdx > 0 ? field.substring(0, dollarIdx) : field;
        String idField = dimName + "$id";

        return new HierarchyContext(treeField, dimName, idField);
    }

    public static HierarchyContext bind(
            List<AxisField> rows, com.foggyframework.dataset.model.spi.QueryModel model) {
        HierarchyContext shape = detect(rows);
        if (!shape.isTree() || model == null) return shape;
        String dimension = PivotFieldBinding.dimension(model, shape.treeAxisField.getField());
        HierarchyContext bound =
                new HierarchyContext(
                        shape.treeAxisField,
                        dimension,
                        PivotFieldBinding.roleOnAxis(
                                        model,
                                        dimension,
                                        "id",
                                        rows.stream().map(AxisField::getField).toList())
                                .orElseGet(() -> PivotFieldBinding.role(model, dimension, "id")));
        bound.parentField = PivotFieldBinding.role(model, dimension, "parentId");
        for (var field : rows) {
            if (!dimension.equals(PivotFieldBinding.dimension(model, field.getField())))
                throw new IllegalArgumentException(
                        "PIVOT_TREE_GRAIN_UNSUPPORTED: extra rows must belong to the tree node"
                                + " dimension");
            if (field != shape.treeAxisField
                    && (field.getLimit() != null
                            || field.getHaving() != null
                            || field.getOrderBy() != null))
                throw new IllegalArgumentException(
                        "PIVOT_TREE_CONTROL_UNSUPPORTED: controls belong on the tree field");
        }
        return bound;
    }

    public String getParentField() {
        return parentField;
    }

    public boolean isTree() {
        return treeAxisField != null;
    }

    public String getDimName() {
        return dimName;
    }

    public String getIdField() {
        return idField;
    }

    public AxisField getTreeAxisField() {
        return treeAxisField;
    }

    /**
     * 确保 rowFields 包含 $id 字段。如果不包含，返回新列表（前插 $id）。
     *
     * @param rowFields 原始 rowFields
     * @return 可能追加了 $id 的 rowFields
     */
    public List<String> ensureIdField(List<String> rowFields) {
        if (!isTree()) return rowFields;
        if (rowFields.contains(idField)) return rowFields;

        List<String> result = new ArrayList<>(rowFields);
        result.add(0, idField);
        logger.debug("[Pivot] HierarchyMode=tree: implicit $id field added: {}", idField);
        return result;
    }
}
