package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.expression.CalculatedFieldService;
import com.foggyframework.dataset.db.model.engine.expression.InlineExpressionParser;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.spi.PhysicalColumnMapping;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

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
        List<DeniedPhysicalColumn> deniedCols = ctx.getDeniedColumns();

        // 快速退出：两套机制都未启用
        if (fieldAccess == null && (deniedCols == null || deniedCols.isEmpty())) {
            return CONTINUE;
        }

        // deniedColumns → 通过映射缓存转换为 denied QM 字段集合
        Set<String> deniedQmFields = (deniedCols != null && !deniedCols.isEmpty())
                ? resolveDeniedQmFields(ctx) : Set.of();

        DbQueryRequestDef request = ctx.getRequest().getParam();

        // 构建计算字段名→表达式映射，用于传递依赖展开
        Map<String, String> calcFieldMap = buildCalculatedFieldMap(request.getCalculatedFields());

        // 1. 校验 columns
        validateColumns(request.getColumns(), fieldAccess, deniedQmFields, calcFieldMap);

        // 2. 校验 calculatedFields
        validateCalculatedFields(request.getCalculatedFields(), fieldAccess, deniedQmFields, calcFieldMap);

        // 3. 校验 slice（只校验用户 slice，system_slice 在 SystemSliceMergeStep 中后续合并）
        validateSlice(request.getSlice(), fieldAccess, deniedQmFields);

        // 4. 校验 orderBy
        validateOrderBy(request.getOrderBy(), fieldAccess, deniedQmFields, calcFieldMap);

        // 5. 校验 groupBy
        validateGroupBy(request.getGroupBy(), fieldAccess, deniedQmFields);

        if (log.isDebugEnabled()) {
            log.debug("FieldAccessPermission check passed.");
        }

        return CONTINUE;
    }

    /**
     * 将 deniedColumns 物理列黑名单通过 QM 映射缓存转换为 denied QM 字段集合
     */
    private Set<String> resolveDeniedQmFields(ModelResultContext ctx) {
        List<DeniedPhysicalColumn> denied = ctx.getDeniedColumns();
        if (denied == null || denied.isEmpty()) {
            return Set.of();
        }
        QueryModel qm = ctx.getQueryModel();
        if (qm == null) {
            return Set.of();
        }
        PhysicalColumnMapping mapping = qm.getPhysicalColumnMapping();
        if (mapping == null) {
            return Set.of();
        }
        return mapping.toDeniedQmFields(denied);
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
     */
    private void validateColumns(List<String> columns, Set<String> fieldAccess,
                                  Set<String> deniedQmFields, Map<String, String> calcFieldMap) {
        if (columns == null || columns.isEmpty()) {
            return;
        }
        for (String column : columns) {
            InlineExpressionParser.InlineExpression parsed = InlineExpressionParser.parse(column);
            if (parsed != null) {
                validateExpressionDeps(parsed.getExpression(), column, "columns", fieldAccess, deniedQmFields, calcFieldMap);
            } else {
                String baseField = stripDimensionSuffix(column);
                checkField(baseField, column, "columns", fieldAccess, deniedQmFields);
            }
        }
    }

    /**
     * 校验 calculatedFields 列表
     */
    private void validateCalculatedFields(List<CalculatedFieldDef> calculatedFields, Set<String> fieldAccess,
                                           Set<String> deniedQmFields, Map<String, String> calcFieldMap) {
        if (calculatedFields == null || calculatedFields.isEmpty()) {
            return;
        }
        for (CalculatedFieldDef field : calculatedFields) {
            if (field.getExpression() == null || field.getExpression().trim().isEmpty()) {
                continue;
            }
            validateExpressionDeps(field.getExpression(),
                    field.getName() != null ? field.getName() : field.getExpression(),
                    "calculatedFields", fieldAccess, deniedQmFields, calcFieldMap);
        }
    }

    /**
     * 校验 slice 条件列表（只校验用户 slice，不含 system_slice）
     */
    private void validateSlice(List<SliceRequestDef> slice, Set<String> fieldAccess,
                                Set<String> deniedQmFields) {
        if (slice == null || slice.isEmpty()) {
            return;
        }
        for (SliceRequestDef item : slice) {
            if (item.getField() != null) {
                String baseField = stripDimensionSuffix(item.getField());
                checkField(baseField, item.getField(), "slice", fieldAccess, deniedQmFields);
            }
        }
    }

    /**
     * 校验 orderBy 列表
     */
    private void validateOrderBy(List<OrderRequestDef> orderBy, Set<String> fieldAccess,
                                  Set<String> deniedQmFields, Map<String, String> calcFieldMap) {
        if (orderBy == null || orderBy.isEmpty()) {
            return;
        }
        for (OrderRequestDef item : orderBy) {
            String field = item.getField();
            if (field == null) {
                continue;
            }
            if (calcFieldMap.containsKey(field)) {
                validateExpressionDeps(calcFieldMap.get(field), field, "orderBy", fieldAccess, deniedQmFields, calcFieldMap);
                continue;
            }
            InlineExpressionParser.InlineExpression parsed = InlineExpressionParser.parse(field);
            if (parsed != null) {
                validateExpressionDeps(parsed.getExpression(), field, "orderBy", fieldAccess, deniedQmFields, calcFieldMap);
            } else {
                String baseField = stripDimensionSuffix(field);
                checkField(baseField, field, "orderBy", fieldAccess, deniedQmFields);
            }
        }
    }

    /**
     * 校验 groupBy 列表
     */
    private void validateGroupBy(List<GroupRequestDef> groupBy, Set<String> fieldAccess,
                                  Set<String> deniedQmFields) {
        if (groupBy == null || groupBy.isEmpty()) {
            return;
        }
        for (GroupRequestDef item : groupBy) {
            String field = item.getField();
            if (field == null) {
                continue;
            }
            String baseField = stripDimensionSuffix(field);
            checkField(baseField, field, "groupBy", fieldAccess, deniedQmFields);
        }
    }

    /**
     * 提取表达式依赖并逐一校验（支持传递依赖展开）
     */
    private void validateExpressionDeps(String expression, String displayName, String clause,
                                        Set<String> fieldAccess, Set<String> deniedQmFields,
                                        Map<String, String> calcFieldMap) {
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
            String baseDep = stripDimensionSuffix(dep);
            checkField(baseDep, dep, clause, fieldAccess, deniedQmFields);
        }
    }

    /**
     * 统一字段权限检查：白名单 + 黑名单
     * <p>
     * fieldAccess 非 null 时：字段（剥离后缀）必须在白名单中。
     * deniedQmFields 非空时：字段（原始名）不能在黑名单中。
     *
     * @param field          字段名（已剥离维度后缀，用于白名单匹配）
     * @param originalField  原始字段名（含维度后缀，用于黑名单匹配，如 "customer$customerType"）
     */
    private void checkField(String field, String originalField, String clause,
                             Set<String> fieldAccess, Set<String> deniedQmFields) {
        // 白名单检查（使用剥离后缀的基础名）
        if (fieldAccess != null && !fieldAccess.contains(field)) {
            throw RX.throwB("字段访问被拒绝: " + clause + " 中字段 '" + field
                    + "' 不在当前用户的可访问字段列表中");
        }
        // 黑名单检查（使用原始名匹配 — QM 映射缓存存的是完整 QM 字段名）
        if (!deniedQmFields.isEmpty()) {
            if (deniedQmFields.contains(originalField) || deniedQmFields.contains(field)) {
                throw RX.throwB("字段访问被拒绝: " + clause + " 中字段 '" + originalField
                        + "' 对应的物理列在受限列黑名单中");
            }
        }
    }

    /**
     * 去除维度后缀（$id / $caption）取基础字段名
     * <p>
     * 例如 "salesDate$id" → "salesDate"，"amount" → "amount"
     */
    public static String stripDimensionSuffix(String fieldName) {
        if (fieldName == null) {
            return null;
        }
        int idx = fieldName.indexOf('$');
        return idx > 0 ? fieldName.substring(0, idx) : fieldName;
    }
}
