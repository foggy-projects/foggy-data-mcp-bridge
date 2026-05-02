package com.foggyframework.dataset.db.model.engine.pivot.algo;

import com.foggyframework.dataset.db.model.engine.pivot.PivotResult;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotLayout;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 结果整形器 (Result Shaper)
 *
 * <p>将扁平的 ResultSet + 系统元数据转换为 tree / grid / flat 三种输出格式。</p>
 */
public class ResultShaper {

    private static final String SYS_META_KEY = "_sys_meta";

    /**
     * 执行结果整形
     *
     * @param resultSet 加工后的扁平结果集
     * @param pivot     Pivot 请求
     * @param rowFields 行轴字段名列表
     * @param colFields 列轴字段名列表
     * @param metrics   度量字段名列表
     * @return PivotResult
     */
    public static PivotResult shape(List<Map<String, Object>> resultSet,
                                     PivotRequest pivot,
                                     List<String> rowFields,
                                     List<String> colFields,
                                     List<String> metrics) {
        String format = pivot.getOutputFormat() != null ? pivot.getOutputFormat() : "tree";

        PivotResult result = new PivotResult();
        result.setFormat(format);

        if (pivot.getLayout() != null) {
            Map<String, Object> layoutMap = new LinkedHashMap<>();
            layoutMap.put("metricPlacement", pivot.getLayout().getMetricPlacement());
            result.setLayout(layoutMap);
        }

        switch (format) {
            case "grid":
                shapeGrid(result, resultSet, rowFields, colFields, metrics);
                break;
            case "flat":
                shapeFlat(result, resultSet);
                break;
            case "tree":
            default:
                shapeTree(result, resultSet, rowFields, colFields, metrics);
                break;
        }

        return result;
    }

    // ========== tree 格式 ==========

    private static void shapeTree(PivotResult result, List<Map<String, Object>> resultSet,
                                   List<String> rowFields, List<String> colFields,
                                   List<String> metrics) {
        // 按行轴第一个字段分组构建嵌套树
        List<PivotResult.TreeNode> rootNodes = buildTreeLevel(resultSet, rowFields, colFields, metrics, 0);
        result.setTreeData(rootNodes);
    }

    @SuppressWarnings("unchecked")
    private static List<PivotResult.TreeNode> buildTreeLevel(
            List<Map<String, Object>> rows,
            List<String> rowFields,
            List<String> colFields,
            List<String> metrics,
            int level) {

        if (level >= rowFields.size() || rows.isEmpty()) {
            return Collections.emptyList();
        }

        String currentField = rowFields.get(level);
        boolean isLeaf = (level == rowFields.size() - 1);

        // 按当前层级分组（保持插入顺序）
        Map<Object, List<Map<String, Object>>> groups = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> {
                            Object value = row.get(currentField);
                            return value != null ? value : "__null__";
                        },
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<PivotResult.TreeNode> nodes = new ArrayList<>();

        for (Map.Entry<Object, List<Map<String, Object>>> entry : groups.entrySet()) {
            PivotResult.TreeNode node = new PivotResult.TreeNode();

            // 节点坐标
            Map<String, Object> nodeMap = new LinkedHashMap<>();
            nodeMap.put(currentField, entry.getKey());
            node.setNode(nodeMap);

            // 小计标记
            boolean isSubtotal = false;
            Map<String, Object> firstRow = entry.getValue().get(0);
            Object meta = firstRow.get(SYS_META_KEY);
            if (meta instanceof Map) {
                Map<String, Object> metaMap = (Map<String, Object>) meta;
                isSubtotal = Boolean.TRUE.equals(metaMap.get("isRowSubtotal"))
                        || Boolean.TRUE.equals(metaMap.get("isGrandTotal"));
                node.setSysMeta(metaMap);
            }
            node.setSubtotal(isSubtotal);

            if (isLeaf || isSubtotal) {
                // 叶子节点或小计节点：构建 cells
                Map<String, Object> cells = new LinkedHashMap<>();
                for (Map<String, Object> row : entry.getValue()) {
                    String cellKey = PivotAlgoUtils.buildCellKey(row, colFields);
                    for (String metric : metrics) {
                        String fullKey = cellKey.isEmpty()
                                ? metric
                                : cellKey + "|" + metric;
                        cells.put(fullKey, row.get(metric));
                    }
                }
                node.setCells(cells);
            } else {
                // 非叶子节点：递归构建子节点
                node.setChildren(buildTreeLevel(entry.getValue(), rowFields, colFields, metrics, level + 1));
            }

            nodes.add(node);
        }

        return nodes;
    }



    // ========== grid 格式 ==========

    private static void shapeGrid(PivotResult result, List<Map<String, Object>> resultSet,
                                   List<String> rowFields, List<String> colFields,
                                   List<String> metrics) {
        // 提取唯一的行头和列头
        List<Map<String, Object>> rowHeaders = new ArrayList<>();
        List<Map<String, Object>> columnHeaders = new ArrayList<>();
        Set<List<Object>> seenRows = new LinkedHashSet<>();
        Set<List<Object>> seenCols = new LinkedHashSet<>();

        for (Map<String, Object> row : resultSet) {
            List<Object> rowKey = rowFields.stream()
                    .map(f -> row.getOrDefault(f, null))
                    .collect(Collectors.toList());
            if (seenRows.add(rowKey)) {
                Map<String, Object> header = new LinkedHashMap<>();
                for (int i = 0; i < rowFields.size(); i++) {
                    header.put(rowFields.get(i), rowKey.get(i));
                }
                // 标记小计
                Object meta = row.get(SYS_META_KEY);
                if (meta instanceof Map) {
                    header.put("isSubtotal", true);
                }
                rowHeaders.add(header);
            }

            List<Object> colKey = colFields.stream()
                    .map(f -> row.getOrDefault(f, null))
                    .collect(Collectors.toList());
            // 每个度量占一个列头位置
            for (String metric : metrics) {
                List<Object> fullColKey = new ArrayList<>(colKey);
                fullColKey.add(metric);
                if (seenCols.add(fullColKey)) {
                    Map<String, Object> header = new LinkedHashMap<>();
                    for (int i = 0; i < colFields.size(); i++) {
                        header.put(colFields.get(i), colKey.get(i));
                    }
                    header.put("metric", metric);
                    columnHeaders.add(header);
                }
            }
        }

        // 构建坐标索引
        Map<List<Object>, Map<String, Object>> dataIndex = new HashMap<>();
        for (Map<String, Object> row : resultSet) {
            List<Object> fullKey = new ArrayList<>();
            for (String rf : rowFields) fullKey.add(row.get(rf));
            for (String cf : colFields) fullKey.add(row.get(cf));
            dataIndex.put(fullKey, row);
        }

        // 构建 cells 矩阵
        List<List<Object>> cells = new ArrayList<>();
        for (Map<String, Object> rh : rowHeaders) {
            List<Object> cellRow = new ArrayList<>();
            // 对每个列头找值
            for (Map<String, Object> ch : columnHeaders) {
                List<Object> lookupKey = new ArrayList<>();
                for (String rf : rowFields) lookupKey.add(rh.get(rf));
                for (String cf : colFields) lookupKey.add(ch.get(cf));
                String metric = (String) ch.get("metric");

                Map<String, Object> dataRow = dataIndex.get(lookupKey);
                cellRow.add(dataRow != null ? dataRow.get(metric) : null);
            }
            cells.add(cellRow);
        }

        result.setRowHeaders(rowHeaders);
        result.setColumnHeaders(columnHeaders);
        result.setCells(cells);
    }

    // ========== flat 格式 ==========

    private static void shapeFlat(PivotResult result, List<Map<String, Object>> resultSet) {
        result.setFlatData(resultSet);
    }
}
