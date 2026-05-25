package com.foggyframework.dataset.db.model.engine.pivot.algo;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;

import java.util.*;
import java.util.stream.Collectors;

/**
2.1 轴级 domainSlice 过滤 (Memory Path)
*
* 对给定的结果集，在 Group By/聚合阶段，根据 domainSlice 进行成员的域过滤。
* 过滤的语义是：任何在 domainSlice 中幸存的维度成员，其在原 baseRelation 里的所有 cell 都会完整保留下来。
*/
public class AxisDomainSliceFilter {

    /**
     * 对结果集应用轴级 domainSlice 过滤
     *
     * @param resultSet  当前结果集
     * @param axisFields 轴字段列表（可能携带 domainSlice 条件）
     * @return 过滤后的结果集
     */
    public static List<Map<String, Object>> apply(List<Map<String, Object>> resultSet,
                                                   List<AxisField> axisFields) {
        if (axisFields == null || resultSet == null || resultSet.isEmpty()) {
            return resultSet;
        }

        List<Map<String, Object>> filtered = new ArrayList<>(resultSet);

        for (AxisField axisField : axisFields) {
            if (axisField.getDomainSlice() == null || axisField.getDomainSlice().isEmpty()) {
                continue;
            }

            String fieldName = axisField.getField();
            Set<Object> survivingMembers = new HashSet<>();

            // 1. 查找在该 axisField.domainSlice 条件下所有存活的维度成员
            for (Map<String, Object> row : filtered) {
                boolean passAll = true;
                for (SemanticQueryRequest.SliceItem sliceItem : axisField.getDomainSlice()) {
                    if (!evaluateSliceItem(sliceItem, row)) {
                        passAll = false;
                        break;
                    }
                }
                if (passAll) {
                    survivingMembers.add(row.getOrDefault(fieldName, "__null__"));
                }
            }

            // 2. 根据存活的维度成员过滤整个结果集，保留存活成员的所有 Cells (Cell Preservation)
            filtered = filtered.stream()
                    .filter(row -> survivingMembers.contains(row.getOrDefault(fieldName, "__null__")))
                    .collect(Collectors.toList());
        }

        return filtered;
    }

    /**
     * 递归评估 SliceItem
     */
    public static boolean evaluateSliceItem(SemanticQueryRequest.SliceItem item, Map<String, Object> row) {
        if (item == null) {
            return true;
        }
        if (item._isOrGroup()) {
            List<SemanticQueryRequest.SliceItem> orList = item.getOr();
            if (orList == null || orList.isEmpty()) {
                return true;
            }
            for (SemanticQueryRequest.SliceItem sub : orList) {
                if (evaluateSliceItem(sub, row)) {
                    return true;
                }
            }
            return false;
        }
        if (item._isAndGroup()) {
            List<SemanticQueryRequest.SliceItem> andList = item.getAnd();
            if (andList == null || andList.isEmpty()) {
                return true;
            }
            for (SemanticQueryRequest.SliceItem sub : andList) {
                if (!evaluateSliceItem(sub, row)) {
                    return false;
                }
            }
            return true;
        }

        String field = item.getField();
        if (field == null) {
            return true;
        }
        Object rowValue = row.get(field);
        String op = item.getOp();
        if (op == null) {
            return true;
        }
        Object filterVal = item.getValue();

        switch (op.toLowerCase()) {
            case "=":
            case "eq":
                if (rowValue == null) return filterVal == null;
                if (filterVal == null) return false;
                return Objects.toString(rowValue).equals(Objects.toString(filterVal));
            case "!=":
            case "ne":
                if (rowValue == null) return filterVal != null;
                if (filterVal == null) return true;
                return !Objects.toString(rowValue).equals(Objects.toString(filterVal));
            case ">":
            case "gt":
                if (rowValue instanceof Number && filterVal instanceof Number) {
                    return ((Number) rowValue).doubleValue() > ((Number) filterVal).doubleValue();
                }
                if (rowValue == null || filterVal == null) return false;
                return Objects.toString(rowValue).compareTo(Objects.toString(filterVal)) > 0;
            case ">=":
            case "ge":
                if (rowValue instanceof Number && filterVal instanceof Number) {
                    return ((Number) rowValue).doubleValue() >= ((Number) filterVal).doubleValue();
                }
                if (rowValue == null || filterVal == null) return false;
                return Objects.toString(rowValue).compareTo(Objects.toString(filterVal)) >= 0;
            case "<":
            case "lt":
                if (rowValue instanceof Number && filterVal instanceof Number) {
                    return ((Number) rowValue).doubleValue() < ((Number) filterVal).doubleValue();
                }
                if (rowValue == null || filterVal == null) return false;
                return Objects.toString(rowValue).compareTo(Objects.toString(filterVal)) < 0;
            case "<=":
            case "le":
                if (rowValue instanceof Number && filterVal instanceof Number) {
                    return ((Number) rowValue).doubleValue() <= ((Number) filterVal).doubleValue();
                }
                if (rowValue == null || filterVal == null) return false;
                return Objects.toString(rowValue).compareTo(Objects.toString(filterVal)) <= 0;
            case "in":
                if (rowValue == null) return false;
                if (filterVal instanceof Collection) {
                    for (Object v : (Collection<?>) filterVal) {
                        if (Objects.toString(rowValue).equals(Objects.toString(v))) {
                            return true;
                        }
                    }
                } else if (filterVal != null && filterVal.getClass().isArray()) {
                    Object[] arr = (Object[]) filterVal;
                    for (Object v : arr) {
                        if (Objects.toString(rowValue).equals(Objects.toString(v))) {
                            return true;
                        }
                    }
                } else {
                    return Objects.toString(rowValue).equals(Objects.toString(filterVal));
                }
                return false;
            case "not in":
            case "not_in":
                if (rowValue == null) return true;
                if (filterVal instanceof Collection) {
                    for (Object v : (Collection<?>) filterVal) {
                        if (Objects.toString(rowValue).equals(Objects.toString(v))) {
                            return false;
                        }
                    }
                } else if (filterVal != null && filterVal.getClass().isArray()) {
                    Object[] arr = (Object[]) filterVal;
                    for (Object v : arr) {
                        if (Objects.toString(rowValue).equals(Objects.toString(v))) {
                            return false;
                        }
                    }
                } else {
                    return !Objects.toString(rowValue).equals(Objects.toString(filterVal));
                }
                return true;
            case "like":
                if (rowValue == null || filterVal == null) return false;
                String rowStr = Objects.toString(rowValue);
                String filterStr = Objects.toString(filterVal).replace("%", ".*");
                return rowStr.matches("(?i)" + filterStr);
            case "is null":
            case "isnull":
                return rowValue == null;
            case "is not null":
            case "notnull":
                return rowValue != null;
            default:
                return false;
        }
    }
}
