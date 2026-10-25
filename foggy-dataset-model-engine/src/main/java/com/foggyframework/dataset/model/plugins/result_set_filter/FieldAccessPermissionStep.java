package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.compose.schema.AliasExtractor;
import com.foggyframework.dataset.model.engine.compose.schema.ColumnAliasParts;
import com.foggyframework.dataset.model.engine.expression.CalculatedDependencyGraph;
import com.foggyframework.dataset.model.engine.expression.CalculatedFieldDependencies;
import com.foggyframework.dataset.model.engine.expression.InlineExpressionParser;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.model.spi.PhysicalColumnMapping;
import com.foggyframework.dataset.model.spi.QueryModel;

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
    public java.util.Optional<String> pivotCacheScope(ModelResultContext context) {
        return java.util.Optional.of("engine-governed-v1");
    }

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

        // Synthetic member columns are already bound relative paths. Their '$'
        // segments are real identifiers, not ordinary QM dimension permission groups.
        // Generated calendar keys have no user-facing permission identity. This origin is
        // @JsonIgnore; every expression dependency is still checked below against the caller.
        if (fieldAccess != null && ctx.getRequest().getParam().getCalculatedFields() != null) {
            fieldAccess = new LinkedHashSet<>(fieldAccess);
            for (CalculatedFieldDef field : ctx.getRequest().getParam().getCalculatedFields()) {
                if (field.getOrigin() == CalculatedFieldDef.Origin.TIME_WINDOW_INTERNAL) fieldAccess.add(field.getName());
            }
        }
        PermissionAccess access = new PermissionAccess(fieldAccess, deniedQmFields,
                ctx.getQueryModel() != null && ctx.getQueryModel().getName() != null
                        && ctx.getQueryModel().getName().contains("#"));
        DbQueryRequestDef request = ctx.getRequest().getParam();

        // 构建计算字段名→表达式映射，用于传递依赖展开
        Map<String, CalculatedFieldDef> definitions = buildCalculatedFieldMap(request.getCalculatedFields());
        if (ctx.getQueryModel() != null) definitions.putAll(CalculatedFieldDependencies.index(ctx.getQueryModel().getPredefinedCalculatedFields()));
        CalculatedDependencyGraph calcFieldMap;
        try {
            calcFieldMap = CalculatedDependencyGraph.forQuery(ctx.getQueryModel(), request.getCalculatedFields());
        } catch (RuntimeException failure) {
            throw RX.throwB("表达式依赖无法解析，已拒绝访问（fail-closed）: " + failure.getMessage());
        }

        // 1. 校验 columns
        validateColumns(request.getColumns(), access, calcFieldMap);

        // 2. 校验 calculatedFields
        validateCalculatedFields(request.getCalculatedFields(), access, calcFieldMap);

        // 3. 校验 slice/having（只校验用户 slice，system_slice 在 SystemSliceMergeStep 中后续合并）
        validateSlice(request.getSlice(), access, calcFieldMap, "slice");
        validateSlice(request.getHaving(), access, calcFieldMap, "having");

        // 4. 校验 orderBy
        Map<String, String> orderBindings = new LinkedHashMap<>();
        if (request.getColumns() != null) for (String column : request.getColumns()) {
            var binding = com.foggyframework.dataset.model.impl.query.QueryFieldBindings.bind(
                    ctx.getQueryModel(), column, new ArrayList<>(definitions.values()));
            String outputName = binding.outputName();
            if (!outputName.equals(binding.expression()) && !orderBindings.containsKey(outputName)) {
                orderBindings.put(outputName, binding.expression());
            }
        }
        validateOrderBy(request.getOrderBy(), access, calcFieldMap, orderBindings);

        // 5. 校验 groupBy
        validateGroupBy(request.getGroupBy(), access, calcFieldMap);

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
    private Map<String, CalculatedFieldDef> buildCalculatedFieldMap(List<CalculatedFieldDef> calculatedFields) {
        return CalculatedFieldDependencies.index(calculatedFields);
    }

    /**
     * 校验 columns 列表
     * <p>
     * G5 v2-patch-2：F4 plain-field alias-only 形态 {@code "base AS alias"} 在
     * {@code InlineExpressionParser.parse()} 返回 null 时，由 {@link AliasExtractor}
     * 剥离 alias，仅按 base 字段做 fieldAccess / deniedColumns 校验，alias 不进入权限维度。
     * 函数表达式 {@code "SUM(x) AS y"} 由 {@code parse()} 接管，不走到此分支。
     */
    private void validateColumns(List<String> columns, PermissionAccess access, CalculatedDependencyGraph calcFieldMap) {
        if (columns == null || columns.isEmpty()) {
            return;
        }
        for (String column : columns) {
            InlineExpressionParser.InlineExpression parsed = InlineExpressionParser.parse(column);
            if (parsed != null) {
                validateExpressionDeps(
                        parsed.getExpression(), column, "columns", access, calcFieldMap);
            } else {
                String fieldForCheck = stripAliasIfPresent(column);
                String baseField = stripDimensionSuffix(fieldForCheck);
                checkField(baseField, fieldForCheck, "columns", access);
                if (calcFieldMap.containsKey(fieldForCheck)) validateExpressionDeps(fieldForCheck, fieldForCheck,
                        "columns", access, calcFieldMap);
            }
        }
    }

    /**
     * G5 v2-patch-2：剥离 plain-field alias，仅返回 base 表达式。
     * <p>
     * 仅处理 {@code "base AS alias"} 形态（{@link AliasExtractor} 已限定 alias 必须是
     * 简单 identifier）。函数表达式由 {@link InlineExpressionParser#parse(String)} 接管，
     * 本方法不应在 {@code parse() != null} 的分支调用。
     *
     * <p>失败保护：{@link AliasExtractor#extract(String)} 抛 {@code IllegalArgumentException}
     * 时（如空串、仅空白），fallback 到原始字符串，由下游校验暴露原错误信息。
     */
    private static String stripAliasIfPresent(String column) {
        if (column == null) {
            return null;
        }
        try {
            ColumnAliasParts parts = AliasExtractor.extract(column);
            return parts.hasAlias() ? parts.expression() : column;
        } catch (IllegalArgumentException ex) {
            return column;
        }
    }

    /**
     * 校验 calculatedFields 列表
     */
    private void validateCalculatedFields(List<CalculatedFieldDef> calculatedFields, PermissionAccess access, CalculatedDependencyGraph calcFieldMap) {
        if (calculatedFields == null || calculatedFields.isEmpty()) {
            return;
        }
        for (CalculatedFieldDef field : calculatedFields) {
            if (field.getName() != null && !field.getName().trim().isEmpty()) {
                String baseField = stripDimensionSuffix(field.getName());
                checkField(baseField, field.getName(), "calculatedFields", access);
            }
            if (field.getExpression() == null || field.getExpression().trim().isEmpty()) {
                continue;
            }
            validateExpressionDeps(field.getName(),
                    field.getName() != null ? field.getName() : field.getExpression(),
                    "calculatedFields", access, calcFieldMap);
        }
    }

    /**
     * 校验 slice 条件列表（只校验用户 slice，不含 system_slice）
     */
    private void validateSlice(List<SliceRequestDef> slice, PermissionAccess access, CalculatedDependencyGraph calcFieldMap,
                                String clause) {
        if (slice == null || slice.isEmpty()) {
            return;
        }
        for (SliceRequestDef item : slice) {
            validateCondItem(item, access, calcFieldMap, clause);
        }
    }

    private void validateCondItem(com.foggyframework.dataset.model.def.query.request.CondRequestDef item,
                                  PermissionAccess access,
                                  CalculatedDependencyGraph calcFieldMap, String clause) {
        if (item == null) {
            return;
        }
        if (item.getField() != null) {
            String field = item.getField();
            if (calcFieldMap.containsKey(field)) {
                validateExpressionDeps(field, field, clause, access, calcFieldMap);
            } else {
                String baseField = stripDimensionSuffix(field);
                checkField(baseField, field, clause, access);
            }
        }
        if (item.getExpr() != null && !item.getExpr().isBlank()) {
            validateExpressionDeps(item.getExpr(), item.getExpr(), clause, access, calcFieldMap);
        }
        if (item.getValue() instanceof Map<?, ?> reference
                && reference.get("$field") instanceof String field) {
            validateExpressionDeps(field, field, clause, access, calcFieldMap);
        }
        if (item.getAnd() != null) {
            for (com.foggyframework.dataset.model.def.query.request.CondRequestDef child : item.getAnd()) {
                validateCondItem(child, access, calcFieldMap, clause);
            }
        }
        if (item.getOr() != null) {
            for (com.foggyframework.dataset.model.def.query.request.CondRequestDef child : item.getOr()) {
                validateCondItem(child, access, calcFieldMap, clause);
            }
        }
    }

    /**
     * 校验 orderBy 列表
     */
    private void validateOrderBy(List<OrderRequestDef> orderBy, PermissionAccess access, CalculatedDependencyGraph calcFieldMap,
                                  Map<String, String> outputBindings) {
        if (orderBy == null || orderBy.isEmpty()) {
            return;
        }
        for (OrderRequestDef item : orderBy) {
            String field = item.getField();
            if (field == null) {
                continue;
            }
            if (outputBindings.containsKey(field)) {
                validateExpressionDeps(
                        outputBindings.get(field), field, "orderBy", access, calcFieldMap);
                continue;
            }
            if (calcFieldMap.containsKey(field)) {
                validateExpressionDeps(field, field, "orderBy", access, calcFieldMap);
                continue;
            }
            InlineExpressionParser.InlineExpression parsed = InlineExpressionParser.parse(field);
            if (parsed != null) {
                validateExpressionDeps(
                        parsed.getExpression(), field, "orderBy", access, calcFieldMap);
            } else {
                String baseField = stripDimensionSuffix(field);
                checkField(baseField, field, "orderBy", access);
            }
        }
    }

    /**
     * 校验 groupBy 列表
     */
    private void validateGroupBy(List<GroupRequestDef> groupBy, PermissionAccess access, CalculatedDependencyGraph calcFieldMap) {
        if (groupBy == null || groupBy.isEmpty()) {
            return;
        }
        for (GroupRequestDef item : groupBy) {
            String field = item.getField();
            if (field == null) {
                continue;
            }
            String baseField = stripDimensionSuffix(field);
            checkField(baseField, field, "groupBy", access);
            if (calcFieldMap.containsKey(field))
                validateExpressionDeps(field, field, "groupBy", access, calcFieldMap);
        }
    }

    /**
     * 提取表达式依赖并逐一校验（支持传递依赖展开）
     */
    private void validateExpressionDeps(String expression, String displayName, String clause,
                                        PermissionAccess access,
                                        CalculatedDependencyGraph calcFieldMap) {
        Set<String> deps;
        try {
            deps = calcFieldMap.base(expression);
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
            checkField(baseDep, dep, clause, access);
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
    private record PermissionAccess(Set<String> allowed, Set<String> denied, boolean exactNames) { }

    private void checkField(String field, String originalField, String clause, PermissionAccess access) {
        String permissionField = access.exactNames() ? originalField : field;
        if (access.allowed() != null && !access.allowed().contains(permissionField)) {
            throw RX.throwB("字段访问被拒绝: " + clause + " 中字段 '" + permissionField
                    + "' 不在当前用户的可访问字段列表中");
        }
        if (access.denied().contains(originalField) || access.denied().contains(permissionField)) {
            throw RX.throwB("字段访问被拒绝: " + clause + " 中字段 '" + originalField
                    + "' 对应的物理列在受限列黑名单中");
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
