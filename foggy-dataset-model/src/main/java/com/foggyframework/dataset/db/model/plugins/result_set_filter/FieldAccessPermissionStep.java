package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.expression.CalculatedFieldService;
import com.foggyframework.dataset.db.model.engine.expression.InlineExpressionParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 运行时列权限校验步骤
 * <p>
 * 在 beforeQuery 阶段校验请求中引用的所有字段是否在 {@code fieldAccess} 白名单内。
 * 当 {@code fieldAccess} 为 null 时不做任何限制（向后兼容）。
 * </p>
 * <p>
 * 校验范围：
 * <ul>
 *   <li>columns（含内联表达式的依赖字段提取）</li>
 *   <li>calculatedFields（按表达式依赖集合判定）</li>
 *   <li>slice / orderBy / groupBy 中的字段引用</li>
 * </ul>
 * <p>
 * 对无法解析依赖的表达式采用 fail-closed 策略：拒绝而非放行。
 * </p>
 *
 * @since 8.2.0
 */
@Component
@Order(-25)
@Slf4j
public class FieldAccessPermissionStep implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        Set<String> fieldAccess = ctx.getFieldAccess();
        if (fieldAccess == null) {
            return CONTINUE;
        }

        DbQueryRequestDef request = ctx.getRequest().getParam();

        // 构建计算字段名→表达式映射，用于传递依赖展开
        Map<String, String> calcFieldMap = buildCalculatedFieldMap(request.getCalculatedFields());

        // 1. 校验 columns
        validateColumns(request.getColumns(), fieldAccess, calcFieldMap);

        // 2. 校验 calculatedFields
        validateCalculatedFields(request.getCalculatedFields(), fieldAccess, calcFieldMap);

        // 3. 校验 slice
        validateSlice(request.getSlice(), fieldAccess);

        // 4. 校验 orderBy
        validateOrderBy(request.getOrderBy(), fieldAccess, calcFieldMap);

        // 5. 校验 groupBy
        validateGroupBy(request.getGroupBy(), fieldAccess);

        if (log.isDebugEnabled()) {
            log.debug("FieldAccessPermission check passed. Allowed fields: {}", fieldAccess);
        }

        return CONTINUE;
    }

    /**
     * 构建计算字段名→表达式映射（用于传递依赖展开）
     */
    private Map<String, String> buildCalculatedFieldMap(List<CalculatedFieldDef> calculatedFields) {
        if (calculatedFields == null || calculatedFields.isEmpty()) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (CalculatedFieldDef field : calculatedFields) {
            if (field.getName() != null && field.getExpression() != null) {
                map.put(field.getName(), field.getExpression());
            }
        }
        return map;
    }

    /**
     * 校验 columns 列表
     * <p>
     * 纯字段名直接检查白名单；内联表达式（含运算符/函数）先提取依赖再逐一检查。
     * 表达式中引用的计算字段名会传递展开为基础字段。
     */
    private void validateColumns(List<String> columns, Set<String> fieldAccess,
                                  Map<String, String> calcFieldMap) {
        if (columns == null || columns.isEmpty()) {
            return;
        }
        for (String column : columns) {
            InlineExpressionParser.InlineExpression parsed = InlineExpressionParser.parse(column);
            if (parsed != null) {
                // 内联表达式：提取依赖字段（传递展开计算字段引用）
                validateExpressionDeps(parsed.getExpression(), column, "columns", fieldAccess, calcFieldMap);
            } else {
                // 纯字段名（可能带 $id/$caption 后缀）
                String baseField = stripDimensionSuffix(column);
                checkField(baseField, "columns", fieldAccess);
            }
        }
    }

    /**
     * 校验 calculatedFields 列表
     * <p>
     * 对每个计算字段的表达式做传递依赖展开：如果表达式引用了其他计算字段，
     * 递归解析到基础字段后再校验白名单。
     */
    private void validateCalculatedFields(List<CalculatedFieldDef> calculatedFields, Set<String> fieldAccess,
                                           Map<String, String> calcFieldMap) {
        if (calculatedFields == null || calculatedFields.isEmpty()) {
            return;
        }
        for (CalculatedFieldDef field : calculatedFields) {
            if (field.getExpression() == null || field.getExpression().trim().isEmpty()) {
                continue;
            }
            validateExpressionDeps(field.getExpression(),
                    field.getName() != null ? field.getName() : field.getExpression(),
                    "calculatedFields", fieldAccess, calcFieldMap);
        }
    }

    /**
     * 校验 slice 条件列表
     */
    private void validateSlice(List<SliceRequestDef> slice, Set<String> fieldAccess) {
        if (slice == null || slice.isEmpty()) {
            return;
        }
        for (SliceRequestDef item : slice) {
            if (item.getField() != null) {
                String baseField = stripDimensionSuffix(item.getField());
                checkField(baseField, "slice", fieldAccess);
            }
        }
    }

    /**
     * 校验 orderBy 列表
     */
    private void validateOrderBy(List<OrderRequestDef> orderBy, Set<String> fieldAccess,
                                  Map<String, String> calcFieldMap) {
        if (orderBy == null || orderBy.isEmpty()) {
            return;
        }
        for (OrderRequestDef item : orderBy) {
            String field = item.getField();
            if (field == null) {
                continue;
            }
            // orderBy 可能引用计算字段别名 — 需要传递展开
            if (calcFieldMap.containsKey(field)) {
                // 引用了一个计算字段，展开校验其基础依赖
                validateExpressionDeps(calcFieldMap.get(field), field, "orderBy", fieldAccess, calcFieldMap);
                continue;
            }
            // orderBy 可能是表达式或纯字段名
            InlineExpressionParser.InlineExpression parsed = InlineExpressionParser.parse(field);
            if (parsed != null) {
                validateExpressionDeps(parsed.getExpression(), field, "orderBy", fieldAccess, calcFieldMap);
            } else {
                String baseField = stripDimensionSuffix(field);
                checkField(baseField, "orderBy", fieldAccess);
            }
        }
    }

    /**
     * 校验 groupBy 列表
     */
    private void validateGroupBy(List<GroupRequestDef> groupBy, Set<String> fieldAccess) {
        if (groupBy == null || groupBy.isEmpty()) {
            return;
        }
        for (GroupRequestDef item : groupBy) {
            String field = item.getField();
            if (field == null) {
                continue;
            }
            String baseField = stripDimensionSuffix(field);
            checkField(baseField, "groupBy", fieldAccess);
        }
    }

    /**
     * 提取表达式依赖并逐一校验（支持传递依赖展开）
     * <p>
     * 如果表达式引用了 {@code calcFieldMap} 中的计算字段，会递归解析到基础字段。
     * fail-closed：解析失败时拒绝而非放行。
     */
    private void validateExpressionDeps(String expression, String displayName, String clause,
                                        Set<String> fieldAccess, Map<String, String> calcFieldMap) {
        Set<String> deps;
        try {
            deps = CalculatedFieldService.resolveBaseColumnReferences(expression, calcFieldMap);
        } catch (Exception e) {
            // fail-closed：无法解析依赖时拒绝
            throw RX.throwB("表达式依赖无法解析，已拒绝访问（fail-closed）: '" + displayName
                    + "' in " + clause + " — " + e.getMessage());
        }

        if (deps.isEmpty()) {
            // 纯字面量表达式（如 "100"），放行
            return;
        }

        for (String dep : deps) {
            if (!fieldAccess.contains(dep)) {
                throw RX.throwB("字段访问被拒绝: " + clause + " 中字段 '" + dep
                        + "' 不在当前用户的可访问字段列表中");
            }
        }
    }

    /**
     * 检查单个字段是否在白名单内
     */
    private void checkField(String field, String clause, Set<String> fieldAccess) {
        if (!fieldAccess.contains(field)) {
            throw RX.throwB("字段访问被拒绝: " + clause + " 中字段 '" + field
                    + "' 不在当前用户的可访问字段列表中");
        }
    }

    /**
     * 去除维度后缀（$id / $caption）取基础字段名
     * <p>
     * 例如 "salesDate$id" → "salesDate"，"amount" → "amount"
     */
    static String stripDimensionSuffix(String fieldName) {
        if (fieldName == null) {
            return null;
        }
        int idx = fieldName.indexOf('$');
        return idx > 0 ? fieldName.substring(0, idx) : fieldName;
    }
}
