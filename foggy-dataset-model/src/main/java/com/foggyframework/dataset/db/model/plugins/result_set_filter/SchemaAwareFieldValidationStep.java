package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.expression.CalculatedFieldService;
import com.foggyframework.dataset.db.model.engine.expression.InlineExpressionParser;
import com.foggyframework.dataset.db.model.spi.DbDimension;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
/**
 * 基于当前 QueryModel schema 的字段存在性校验。
 *
 * <p>目标是把“字段名误用但存在明显候选字段”的问题拦截在 SQL 生成前，
 * 返回可恢复的高层错误，而不是落到底层数据库报错。</p>
 */
@Component
@Order(8)
public class SchemaAwareFieldValidationStep implements DataSetResultStep {

    private static final String ERROR_CODE = "INVALID_QUERY_FIELD";

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        QueryModel queryModel = ctx.getQueryModel();
        if (queryModel == null || ctx.isSkipQuery()) {
            return CONTINUE;
        }

        DbQueryRequestDef request = ctx.getRequest().getParam();
        Set<String> schemaFields = collectSchemaFields(queryModel, request);
        if (schemaFields.isEmpty()) {
            return CONTINUE;
        }

        Map<String, String> calcFieldMap = buildCalculatedFieldMap(request.getCalculatedFields());

        validateColumns(request.getColumns(), queryModel, schemaFields, calcFieldMap);
        validateGroupBy(request.getGroupBy(), queryModel, schemaFields);
        validateSlice(request.getSlice(), queryModel, schemaFields);
        validateOrderBy(request.getOrderBy(), queryModel, schemaFields, calcFieldMap);
        validateCalculatedFields(request.getCalculatedFields(), queryModel, schemaFields, calcFieldMap);
        return CONTINUE;
    }

    private Set<String> collectSchemaFields(QueryModel queryModel, DbQueryRequestDef request) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        List<DbQueryColumn> queryColumns = queryModel.getJdbcQueryColumns();
        if (queryColumns != null) {
            for (DbQueryColumn column : queryColumns) {
                if (column != null && column.getField() != null && !column.getField().isBlank()) {
                    fields.add(column.getField());
                }
            }
        }

        List<CalculatedFieldDef> predefined = queryModel.getPredefinedCalculatedFields();
        if (predefined != null) {
            for (CalculatedFieldDef field : predefined) {
                if (field != null && field.getName() != null && !field.getName().isBlank()) {
                    fields.add(field.getName());
                }
            }
        }

        if (request.getCalculatedFields() != null) {
            for (CalculatedFieldDef field : request.getCalculatedFields()) {
                if (field != null && field.getName() != null && !field.getName().isBlank()) {
                    fields.add(field.getName());
                }
            }
        }
        return fields;
    }

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

    private void validateColumns(
            List<String> columns,
            QueryModel queryModel,
            Set<String> schemaFields,
            Map<String, String> calcFieldMap
    ) {
        if (columns == null) {
            return;
        }
        for (String column : columns) {
            if (column == null || column.isBlank()) {
                continue;
            }
            InlineExpressionParser.InlineExpression parsed = InlineExpressionParser.parse(column);
            if (parsed != null) {
                validateExpressionDeps(parsed.getExpression(), parsed.getAlias(), queryModel, schemaFields, calcFieldMap);
                continue;
            }
            validateField(column, queryModel, schemaFields);
        }
    }

    private void validateGroupBy(List<GroupRequestDef> groupBy, QueryModel queryModel, Set<String> schemaFields) {
        if (groupBy == null) {
            return;
        }
        for (GroupRequestDef item : groupBy) {
            if (item == null || item.getField() == null || item.getField().isBlank()) {
                continue;
            }
            validateField(item.getField(), queryModel, schemaFields);
        }
    }

    private void validateSlice(List<SliceRequestDef> slice, QueryModel queryModel, Set<String> schemaFields) {
        if (slice == null) {
            return;
        }
        for (SliceRequestDef item : slice) {
            validateSliceItem(item, queryModel, schemaFields);
        }
    }

    private void validateSliceItem(SliceRequestDef item, QueryModel queryModel, Set<String> schemaFields) {
        if (item == null) {
            return;
        }
        if (item.getField() != null && !item.getField().isBlank()) {
            validateField(item.getField(), queryModel, schemaFields);
        }
        if (item.getAnd() != null) {
            for (CondRequestDef child : item.getAnd()) {
                validateCondItem(child, queryModel, schemaFields);
            }
        }
        if (item.getOr() != null) {
            for (CondRequestDef child : item.getOr()) {
                validateCondItem(child, queryModel, schemaFields);
            }
        }
    }

    private void validateCondItem(CondRequestDef item, QueryModel queryModel, Set<String> schemaFields) {
        if (item == null) {
            return;
        }
        if (item.getField() != null && !item.getField().isBlank()) {
            validateField(item.getField(), queryModel, schemaFields);
        }
        if (item.getAnd() != null) {
            for (CondRequestDef child : item.getAnd()) {
                validateCondItem(child, queryModel, schemaFields);
            }
        }
        if (item.getOr() != null) {
            for (CondRequestDef child : item.getOr()) {
                validateCondItem(child, queryModel, schemaFields);
            }
        }
    }

    private void validateOrderBy(
            List<OrderRequestDef> orderBy,
            QueryModel queryModel,
            Set<String> schemaFields,
            Map<String, String> calcFieldMap
    ) {
        if (orderBy == null) {
            return;
        }
        for (OrderRequestDef item : orderBy) {
            if (item == null || item.getField() == null || item.getField().isBlank()) {
                continue;
            }
            String field = item.getField();
            if (calcFieldMap.containsKey(field)) {
                validateExpressionDeps(calcFieldMap.get(field), field, queryModel, schemaFields, calcFieldMap);
                continue;
            }
            InlineExpressionParser.InlineExpression parsed = InlineExpressionParser.parse(field);
            if (parsed != null) {
                validateExpressionDeps(parsed.getExpression(), parsed.getAlias(), queryModel, schemaFields, calcFieldMap);
                continue;
            }
            validateField(field, queryModel, schemaFields);
        }
    }

    private void validateCalculatedFields(
            List<CalculatedFieldDef> calculatedFields,
            QueryModel queryModel,
            Set<String> schemaFields,
            Map<String, String> calcFieldMap
    ) {
        if (calculatedFields == null) {
            return;
        }
        for (CalculatedFieldDef field : calculatedFields) {
            if (field == null || field.getExpression() == null || field.getExpression().isBlank()) {
                continue;
            }
            validateExpressionDeps(field.getExpression(), field.getName(), queryModel, schemaFields, calcFieldMap);
        }
    }

    private void validateExpressionDeps(
            String expression,
            String displayName,
            QueryModel queryModel,
            Set<String> schemaFields,
            Map<String, String> calcFieldMap
    ) {
        Set<String> deps;
        try {
            deps = CalculatedFieldService.resolveBaseColumnReferences(expression, calcFieldMap);
        } catch (Exception e) {
            return;
        }
        for (String dep : deps) {
            validateField(dep, queryModel, schemaFields);
        }
    }

    private void validateField(String field, QueryModel queryModel, Set<String> schemaFields) {
        if (field == null || field.isBlank()) {
            return;
        }
        // 8.4.0.beta backlog B-03 strict path · Foggy QM 公开契约约束：
        // 维度本身不是可投影列 —— 必须通过 ``$id`` / ``$caption`` /
        // ``$<custom_attr>`` 引用其属性。LLM 看到的元数据形态（仅
        // ``$attr``）与引擎实际接受的列引用形态必须对齐。
        // Python 端等价改造：``v1.7`` ``DbTableModelImpl.resolve_field_strict``。
        // 这里在所有路径之前先判定，因为：
        // (a) FK-style dim：bare 名不在 schemaFields，会落到下方 OLD 路径报
        //     ``INVALID_QUERY_FIELD`` —— 但错误码不一致，无法跨端 parity
        // (b) self-attribute dim：bare 名可能在 schemaFields，OLD 路径直接
        //     接受 —— 这是 v1.3 容忍 bug
        // 两类都改写为 ``COLUMN_FIELD_NOT_FOUND`` + hint，cross-end 对齐 Python。
        if (!field.contains("$") && isBareDimensionReference(field, queryModel)) {
            rejectBareDimension(field, queryModel);
        }
        if (schemaFields.contains(field)) {
            return;
        }
        List<String> suggestions = suggest(field, schemaFields);
        String modelName = queryModel.getName();
        String message = "Field '" + field + "' not found in model '" + modelName + "'.";
        if (!suggestions.isEmpty()) {
            message += " Did you mean '" + suggestions.get(0) + "'?";
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("errorCode", ERROR_CODE);
        payload.put("model", modelName);
        payload.put("invalidField", field);
        payload.put("suggestions", suggestions);
        throw RX.throwB(message, payload);
    }

    /**
     * 判断给定字段名是否对应模型上的一个裸维度（即没有 ``$<attr>`` 后缀的
     * 维度引用）。返回 ``true`` 时调用方应当 fail-loud + 给出提示。
     *
     * <p>仅当字段名同时满足：(a) 模型上有同名 ``DbDimension``；(b) 同名的
     * 注册项不是来自 measure 或 property（即并非"度量名碰巧叫 X 同时也存在
     * 维度 X"的边界 case）—— 才认定为裸维度引用。当前实现：先用
     * ``findDimension`` 直接命中；若该名称同时是 measure 或 property，仍按
     * measure/property 解析（避免误伤）。</p>
     */
    private boolean isBareDimensionReference(String field, QueryModel queryModel) {
        DbDimension dim;
        try {
            dim = queryModel.findDimension(field);
        } catch (Exception e) {
            return false;
        }
        if (dim == null) {
            return false;
        }
        // 边界保护：若同名 measure / property 存在则按非裸-dim 解析路径走，
        // 避免在罕见冲突命名场景下误拒。
        try {
            if (queryModel.findProperty(field, false) != null) {
                return false;
            }
        } catch (Exception ignore) {
            // findProperty 可能在某些模型形态下抛 RuntimeException —— 忽略，
            // 保留 isBare=true 判定。
        }
        return true;
    }

    private void rejectBareDimension(String field, QueryModel queryModel) {
        String modelName = queryModel.getName();
        String hintCaption = field + "$caption";
        String hintId = field + "$id";
        String message = "COLUMN_FIELD_NOT_FOUND: column '" + field + "' references "
                + "dimension '" + field + "' directly. Dimensions are not projectable; "
                + "reference an attribute (e.g. '" + hintCaption + "' or '" + hintId
                + "'). Hint: did you mean '" + hintCaption + "'?";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("errorCode", "COLUMN_FIELD_NOT_FOUND");
        payload.put("model", modelName);
        payload.put("invalidField", field);
        payload.put("suggestions", List.of(hintCaption, hintId));
        throw RX.throwB(message, payload);
    }

    private List<String> suggest(String invalidField, Set<String> schemaFields) {
        List<ScoredSuggestion> scored = new ArrayList<>();
        for (String candidate : schemaFields) {
            int score = similarityScore(invalidField, candidate);
            if (score > 0) {
                scored.add(new ScoredSuggestion(candidate, score));
            }
        }
        scored.sort(Comparator
                .comparingInt(ScoredSuggestion::score).reversed()
                .thenComparing(ScoredSuggestion::field));

        List<String> result = new ArrayList<>();
        for (ScoredSuggestion item : scored) {
            if (result.size() >= 3) {
                break;
            }
            result.add(item.field());
        }
        return result;
    }

    private int similarityScore(String invalidField, String candidate) {
        String invalidNorm = normalize(invalidField);
        String candidateNorm = normalize(candidate);
        String invalidTail = tail(invalidField);
        String candidateTail = tail(candidate);

        if (invalidNorm.equals(candidateNorm)) {
            return 1000;
        }

        int score = 0;
        if (invalidTail.equalsIgnoreCase(candidateTail)) {
            score += 800;
        }
        if (candidateNorm.endsWith(invalidTail.toLowerCase(Locale.ROOT))) {
            score += 500;
        }
        if (candidateTail.toLowerCase(Locale.ROOT).contains(invalidTail.toLowerCase(Locale.ROOT))
                || invalidTail.toLowerCase(Locale.ROOT).contains(candidateTail.toLowerCase(Locale.ROOT))) {
            score += 200;
        }

        int distance = levenshtein(invalidNorm, candidateNorm);
        int maxLen = Math.max(invalidNorm.length(), candidateNorm.length());
        int distanceScore = Math.max(0, 200 - distance * 40);
        score += distanceScore;

        if (maxLen > 0) {
            double ratio = 1.0 - ((double) distance / maxLen);
            if (ratio >= 0.55d) {
                score += (int) Math.round(ratio * 100);
            }
        }

        return score >= 220 ? score : 0;
    }

    private String normalize(String field) {
        return field == null ? "" : field.replaceAll("[$_\\-\\s]", "").toLowerCase(Locale.ROOT);
    }

    private String tail(String field) {
        if (field == null) {
            return "";
        }
        int idx = field.lastIndexOf('$');
        return idx >= 0 && idx + 1 < field.length() ? field.substring(idx + 1) : field;
    }

    private int levenshtein(String left, String right) {
        int[][] dp = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[left.length()][right.length()];
    }

    private record ScoredSuggestion(String field, int score) {
    }
}
