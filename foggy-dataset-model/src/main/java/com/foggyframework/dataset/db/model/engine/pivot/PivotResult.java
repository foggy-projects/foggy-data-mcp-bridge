package com.foggyframework.dataset.db.model.engine.pivot;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Pivot 结果集包装
 *
 * <p>封装四阶段流水线的最终输出，支持 tree / grid / flat 三种格式。</p>
 */
@Data
public class PivotResult {

    /** 输出格式 */
    private String format;

    // ========== tree 格式 ==========
    /** 树形嵌套数据（format=tree 时使用） */
    private List<TreeNode> treeData;

    // ========== grid 格式 ==========
    /** 行头坐标（format=grid 时使用） */
    private List<Map<String, Object>> rowHeaders;

    /** 列头坐标（format=grid 时使用） */
    private List<Map<String, Object>> columnHeaders;

    /** 数据矩阵 [rowIndex][colIndex]（format=grid 时使用） */
    private List<List<Object>> cells;

    // ========== flat 格式 ==========
    /** 扁平数据（format=flat 时使用） */
    private List<Map<String, Object>> flatData;

    // ========== 元数据 ==========
    /** 布局信息 */
    private Map<String, Object> layout;

    /** 警告信息 */
    private List<String> warnings;

    /**
     * 树形节点
     */
    @Data
    public static class TreeNode {
        /** 当前节点的维度坐标 */
        private Map<String, Object> node;

        /** 附属属性 */
        private Map<String, Object> properties;

        /** 是否为小计行 */
        private boolean subtotal;

        /** 单元格数据：key 格式为 [col1_value]|[col2_value]|[metric_name] */
        private Map<String, Object> cells;

        /** 子节点 */
        private List<TreeNode> children;

        /** 系统元数据 */
        private Map<String, Object> sysMeta;
    }
}
