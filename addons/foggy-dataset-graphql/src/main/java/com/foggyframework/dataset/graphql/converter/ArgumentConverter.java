package com.foggyframework.dataset.graphql.converter;

import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import graphql.language.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphQL 参数转换器
 * <p>
 * 负责转换 GraphQL 的 where、orderBy 等参数为 DSL 格式
 * </p>
 */
@Slf4j
public class ArgumentConverter {

    /**
     * 转换 where 条件为 slice
     * <p>
     * GraphQL: where: { orderStatus: { _eq: "COMPLETED" }, _or: [...] }
     * DSL: [{ field: "orderStatus", op: "=", value: "COMPLETED" }, { $or: [...] }]
     * </p>
     */
    public List<SliceRequestDef> convertWhereToSlice(ObjectValue whereValue, Map<String, Object> variables) {
        if (whereValue == null) {
            return Collections.emptyList();
        }

        List<SliceRequestDef> slices = new ArrayList<>();

        for (ObjectField field : whereValue.getObjectFields()) {
            String fieldName = field.getName();

            // 处理逻辑操作符 _or / _and
            if ("_or".equals(fieldName)) {
                SliceRequestDef orSlice = new SliceRequestDef();
                Map<String, Object> orCondition = new HashMap<>();

                ArrayValue arrayValue = (ArrayValue) field.getValue();
                List<SliceRequestDef> orChildren = new ArrayList<>();

                for (Value<?> item : arrayValue.getValues()) {
                    List<SliceRequestDef> childSlices = convertWhereToSlice((ObjectValue) item, variables);
                    orChildren.addAll(childSlices);
                }

                orCondition.put("$or", orChildren);
                orSlice.setValue(orCondition);
                slices.add(orSlice);

            } else if ("_and".equals(fieldName)) {
                SliceRequestDef andSlice = new SliceRequestDef();
                Map<String, Object> andCondition = new HashMap<>();

                ArrayValue arrayValue = (ArrayValue) field.getValue();
                List<SliceRequestDef> andChildren = new ArrayList<>();

                for (Value<?> item : arrayValue.getValues()) {
                    List<SliceRequestDef> childSlices = convertWhereToSlice((ObjectValue) item, variables);
                    andChildren.addAll(childSlices);
                }

                andCondition.put("$and", andChildren);
                andSlice.setValue(andCondition);
                slices.add(andSlice);

            } else {
                // 普通字段条件
                SliceRequestDef slice = convertFieldCondition(fieldName, field.getValue(), variables);
                if (slice != null) {
                    slices.add(slice);
                }
            }
        }

        return slices;
    }

    /**
     * 转换单个字段条件
     * <p>
     * GraphQL: orderStatus: { _eq: "COMPLETED" }
     * DSL: { field: "orderStatus", op: "=", value: "COMPLETED" }
     * </p>
     */
    private SliceRequestDef convertFieldCondition(String fieldName, Value<?> value, Map<String, Object> variables) {
        if (!(value instanceof ObjectValue)) {
            // 简写形式: orderStatus: "COMPLETED" → orderStatus: { _eq: "COMPLETED" }
            SliceRequestDef slice = new SliceRequestDef();
            slice.setField(fieldName);
            slice.setOp("=");
            slice.setValue(extractValue(value, variables));
            return slice;
        }

        ObjectValue objValue = (ObjectValue) value;

        // 处理嵌套维度（如 customer: { name: { _eq: "..." } }）
        if (isNestedDimension(objValue)) {
            return convertNestedDimensionCondition(fieldName, objValue, variables);
        }

        // 处理操作符
        for (ObjectField opField : objValue.getObjectFields()) {
            String operator = opField.getName();
            SliceRequestDef slice = new SliceRequestDef();
            slice.setField(fieldName);

            switch (operator) {
                case "_eq":
                    slice.setOp("=");
                    slice.setValue(extractValue(opField.getValue(), variables));
                    return slice;

                case "_neq":
                case "_ne":
                    slice.setOp("!=");
                    slice.setValue(extractValue(opField.getValue(), variables));
                    return slice;

                case "_gt":
                    slice.setOp(">");
                    slice.setValue(extractValue(opField.getValue(), variables));
                    return slice;

                case "_gte":
                    slice.setOp(">=");
                    slice.setValue(extractValue(opField.getValue(), variables));
                    return slice;

                case "_lt":
                    slice.setOp("<");
                    slice.setValue(extractValue(opField.getValue(), variables));
                    return slice;

                case "_lte":
                    slice.setOp("<=");
                    slice.setValue(extractValue(opField.getValue(), variables));
                    return slice;

                case "_in":
                    slice.setOp("in");
                    slice.setValue(extractArrayValue(opField.getValue(), variables));
                    return slice;

                case "_nin":
                    slice.setOp("not in");
                    slice.setValue(extractArrayValue(opField.getValue(), variables));
                    return slice;

                case "_like":
                    slice.setOp("like");
                    slice.setValue(extractValue(opField.getValue(), variables));
                    return slice;

                case "_left_like":
                    slice.setOp("left_like");
                    slice.setValue(extractValue(opField.getValue(), variables));
                    return slice;

                case "_right_like":
                    slice.setOp("right_like");
                    slice.setValue(extractValue(opField.getValue(), variables));
                    return slice;

                case "_is_null":
                    boolean isNull = (Boolean) extractValue(opField.getValue(), variables);
                    slice.setOp(isNull ? "is null" : "is not null");
                    return slice;

                case "_range":
                    // 范围操作符 { from: "2024-01-01", to: "2024-07-01", toInclusive: false }
                    return convertRangeCondition(fieldName, (ObjectValue) opField.getValue(), variables);

                // DSL 特有的层级操作符
                case "_childrenOf":
                    slice.setOp("childrenOf");
                    slice.setValue(extractValue(opField.getValue(), variables));
                    return slice;

                case "_descendantsOf":
                    slice.setOp("descendantsOf");
                    slice.setValue(extractValue(opField.getValue(), variables));
                    return slice;

                case "_selfAndDescendantsOf":
                    slice.setOp("selfAndDescendantsOf");
                    slice.setValue(extractValue(opField.getValue(), variables));
                    return slice;

                default:
                    log.warn("不支持的操作符: {}", operator);
                    return null;
            }
        }

        return null;
    }

    /**
     * 转换范围条件
     * <p>
     * GraphQL: _range: { from: "2024-01-01", to: "2024-07-01", fromInclusive: true, toInclusive: false }
     * DSL: { field: "orderDate", op: "[)", value: ["2024-01-01", "2024-07-01"] }
     * </p>
     */
    private SliceRequestDef convertRangeCondition(String fieldName, ObjectValue rangeValue, Map<String, Object> variables) {
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField(fieldName);

        Object from = null;
        Object to = null;
        boolean fromInclusive = true;
        boolean toInclusive = false;

        for (ObjectField field : rangeValue.getObjectFields()) {
            switch (field.getName()) {
                case "from":
                    from = extractValue(field.getValue(), variables);
                    break;
                case "to":
                    to = extractValue(field.getValue(), variables);
                    break;
                case "fromInclusive":
                    fromInclusive = (Boolean) extractValue(field.getValue(), variables);
                    break;
                case "toInclusive":
                    toInclusive = (Boolean) extractValue(field.getValue(), variables);
                    break;
            }
        }

        // 确定操作符: [], [), (], ()
        String op;
        if (fromInclusive && toInclusive) {
            op = "[]";
        } else if (fromInclusive && !toInclusive) {
            op = "[)";
        } else if (!fromInclusive && toInclusive) {
            op = "(]";
        } else {
            op = "()";
        }

        slice.setOp(op);
        slice.setValue(Arrays.asList(from, to));

        return slice;
    }

    /**
     * 判断是否为嵌套维度条件
     * <p>
     * customer: { name: { _eq: "..." } } → true
     * orderStatus: { _eq: "..." } → false
     * </p>
     */
    private boolean isNestedDimension(ObjectValue objValue) {
        // 如果字段中没有操作符（不以 _ 开头），则认为是嵌套维度
        return objValue.getObjectFields().stream()
                .noneMatch(field -> field.getName().startsWith("_"));
    }

    /**
     * 转换嵌套维度条件
     * <p>
     * customer: { customerType: { _eq: "VIP" } }
     * → { field: "customer$customerType", op: "=", value: "VIP" }
     * </p>
     */
    private SliceRequestDef convertNestedDimensionCondition(String dimensionName, ObjectValue value, Map<String, Object> variables) {
        // 递归处理嵌套字段
        for (ObjectField field : value.getObjectFields()) {
            String nestedField = dimensionName + "$" + field.getName();
            return convertFieldCondition(nestedField, field.getValue(), variables);
        }
        return null;
    }

    /**
     * 提取值（处理变量引用）
     */
    private Object extractValue(Value<?> value, Map<String, Object> variables) {
        if (value instanceof VariableReference) {
            String varName = ((VariableReference) value).getName();
            return variables.get(varName);
        } else if (value instanceof IntValue) {
            return ((IntValue) value).getValue().intValue();
        } else if (value instanceof FloatValue) {
            return ((FloatValue) value).getValue().doubleValue();
        } else if (value instanceof StringValue) {
            return ((StringValue) value).getValue();
        } else if (value instanceof BooleanValue) {
            return ((BooleanValue) value).isValue();
        } else if (value instanceof NullValue) {
            return null;
        } else if (value instanceof EnumValue) {
            return ((EnumValue) value).getName();
        }
        return null;
    }

    /**
     * 提取数组值
     */
    private List<Object> extractArrayValue(Value<?> value, Map<String, Object> variables) {
        if (value instanceof ArrayValue) {
            return ((ArrayValue) value).getValues().stream()
                    .map(v -> extractValue(v, variables))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * 转换 orderBy 参数
     * <p>
     * GraphQL: orderBy: [{ totalAmount: desc }, { orderId: asc }]
     * DSL: [{ field: "totalAmount", dir: "desc" }, { field: "orderId", dir: "asc" }]
     * </p>
     */
    public List<OrderRequestDef> convertOrderBy(ArrayValue orderByValue, Map<String, Object> variables) {
        if (orderByValue == null) {
            return Collections.emptyList();
        }

        List<OrderRequestDef> orderList = new ArrayList<>();

        for (Value<?> item : orderByValue.getValues()) {
            if (item instanceof ObjectValue) {
                ObjectValue objValue = (ObjectValue) item;

                for (ObjectField field : objValue.getObjectFields()) {
                    OrderRequestDef order = new OrderRequestDef();
                    order.setField(field.getName());

                    String direction = extractValue(field.getValue(), variables).toString();
                    order.setDir(direction.toLowerCase());

                    orderList.add(order);
                }
            }
        }

        return orderList;
    }
}
