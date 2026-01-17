package com.foggyframework.dataset.graphql.converter;

import graphql.language.Field;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * GraphQL SelectionSet 转换器
 * <p>
 * 负责将 GraphQL 的字段选择转换为 DSL 的 columns
 * </p>
 */
@Slf4j
public class SelectionSetConverter {

    /**
     * 转换 SelectionSet 为 columns 列表
     * <p>
     * GraphQL:
     * {
     *   orderId
     *   customer {
     *     name
     *     customerType
     *   }
     * }
     * <p>
     * DSL: ["orderId", "customer$caption", "customer$customerType"]
     * </p>
     *
     * @param selectionSet  GraphQL 选择集
     * @param parentContext 父级上下文（用于跳过 Connection 包装）
     * @return columns 列表
     */
    public List<String> convertToColumns(SelectionSet selectionSet, String parentContext) {
        if (selectionSet == null) {
            return new ArrayList<>();
        }

        List<String> columns = new ArrayList<>();

        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                Field field = (Field) selection;
                String fieldName = field.getName();

                // 跳过 Relay Connection 包装字段
                if (isConnectionWrapper(fieldName)) {
                    // 递归处理 edges.node 或直接处理
                    if ("edges".equals(fieldName)) {
                        // 查找 edges { node { ... } }
                        SelectionSet edgesSelection = field.getSelectionSet();
                        if (edgesSelection != null) {
                            for (Selection<?> edgeSel : edgesSelection.getSelections()) {
                                if (edgeSel instanceof Field && "node".equals(((Field) edgeSel).getName())) {
                                    Field nodeField = (Field) edgeSel;
                                    columns.addAll(convertToColumns(nodeField.getSelectionSet(), "node"));
                                }
                            }
                        }
                    }
                    continue;
                }

                // 跳过元数据字段
                if (isMetadataField(fieldName)) {
                    continue;
                }

                // 处理嵌套维度
                if (field.getSelectionSet() != null) {
                    columns.addAll(convertNestedDimension(fieldName, field.getSelectionSet(), ""));
                } else {
                    // 简单字段
                    columns.add(fieldName);
                }
            }
        }

        return columns;
    }

    /**
     * 转换嵌套维度
     * <p>
     * customer { name, customerType }
     * → ["customer$caption", "customer$customerType"]
     * <p>
     * product { category { name } }
     * → ["product.category$caption"]
     * </p>
     */
    private List<String> convertNestedDimension(String dimensionName, SelectionSet selectionSet, String prefix) {
        List<String> columns = new ArrayList<>();

        String currentPath = prefix.isEmpty() ? dimensionName : prefix + "." + dimensionName;

        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                Field field = (Field) selection;
                String fieldName = field.getName();

                // 处理特殊字段映射
                String mappedField = mapDimensionField(fieldName);

                if (field.getSelectionSet() != null) {
                    // 继续嵌套
                    columns.addAll(convertNestedDimension(fieldName, field.getSelectionSet(), currentPath));
                } else {
                    // 生成完整路径: customer$caption, product.category$caption
                    columns.add(currentPath + "$" + mappedField);
                }
            }
        }

        return columns;
    }

    /**
     * 映射 GraphQL 字段名到 DSL 字段名
     * <p>
     * name → caption
     * id → id
     * 其他 → 保持不变
     * </p>
     */
    private String mapDimensionField(String fieldName) {
        switch (fieldName) {
            case "name":
                return "caption";
            case "id":
                return "id";
            default:
                return fieldName;
        }
    }

    /**
     * 判断是否为 Connection 包装字段
     */
    private boolean isConnectionWrapper(String fieldName) {
        return "edges".equals(fieldName) ||
                "node".equals(fieldName) ||
                "pageInfo".equals(fieldName) ||
                "totalCount".equals(fieldName) ||
                "aggregates".equals(fieldName);
    }

    /**
     * 判断是否为元数据字段
     */
    private boolean isMetadataField(String fieldName) {
        return "cursor".equals(fieldName) ||
                "hasNextPage".equals(fieldName) ||
                "hasPreviousPage".equals(fieldName) ||
                "startCursor".equals(fieldName) ||
                "endCursor".equals(fieldName) ||
                "__typename".equals(fieldName);
    }
}
