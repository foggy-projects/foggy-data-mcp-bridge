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
import com.foggyframework.dataset.db.model.spi.DbDimensionType;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /**
     * Top-level {@code <expr> AS <alias>} pattern (case-insensitive on AS).
     * Mirrors {@code InlineExpressionParser.AS_PATTERN} — kept local because
     * the parser's pattern is private.
     */
    private static final Pattern TRAILING_AS_PATTERN = Pattern.compile(
            "^(.+?)\\s+[Aa][Ss]\\s+(\\w+)\\s*$"
    );

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
        validateSlice(request.getHaving(), queryModel, schemaFields);
        validateSlice(request.getPostSlice(), queryModel, schemaFields);
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
        if (request.getPostAggregateCalculations() != null) {
            for (var field : request.getPostAggregateCalculations()) {
                if (field != null && field.getName() != null && !field.getName().isBlank()) {
                    fields.add(field.getName());
                }
            }
        }
        if (request.getColumns() != null) {
            for (String column : request.getColumns()) {
                InlineExpressionParser.InlineExpression parsed = InlineExpressionParser.parse(column);
                if (parsed != null && parsed.getAlias() != null && !parsed.getAlias().isBlank()) {
                    fields.add(parsed.getAlias());
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
            // orderBy is a field or selected-alias reference; direct expressions must be selected first.
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
        // QM contract: ordinary dimensions are not directly projectable; bare-dim
        // refs (with or without trailing "AS alias") fail-loud here. Time
        // dimension roots registered as real query columns are allowed for date
        // filtering/timeWindow semantics.
        // Runs before the schemaFields lookup so the error code unifies
        // to COLUMN_FIELD_NOT_FOUND regardless of whether the bare name
        // happens to be registered (FK-style dims aren't, self-attribute
        // ones are — both must reject identically).
        Matcher asMatcher = TRAILING_AS_PATTERN.matcher(field.trim());
        if (asMatcher.matches()) {
            String baseExpr = asMatcher.group(1).trim();
            String userAlias = asMatcher.group(2).trim();
            if (!baseExpr.contains("$") && isBareDimensionReference(baseExpr, queryModel)) {
                rejectBareDimension(baseExpr, userAlias, queryModel, field);
            }
        } else if (!field.contains("$") && isBareDimensionReference(field, queryModel)) {
            rejectBareDimension(field, null, queryModel, field);
        }
        if (schemaFields.contains(field)) {
            return;
        }
        try {
            if (queryModel.findJdbcColumnForSelectByName(field, false) != null) {
                return;
            }
        } catch (Exception ignored) {
            // Keep validation fail-loud below with schema suggestions.
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
     * Returns {@code true} if {@code field} names a {@link DbDimension}
     * on {@code queryModel} and is not shadowed by a same-named property
     * (rare conflict case — keep the property path so we don't false-reject).
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
        if (isProjectableTimeDimensionRoot(field, dim, queryModel)) {
            return false;
        }
        try {
            if (queryModel.findProperty(field, false) != null) {
                return false;
            }
        } catch (Exception ignore) {
            // findProperty may throw on some model shapes; treat as
            // "no shadow property" and keep isBare=true.
        }
        return true;
    }

    private boolean isProjectableTimeDimensionRoot(String field, DbDimension dim, QueryModel queryModel) {
        DbDimensionType type = dim.getType();
        if (type != DbDimensionType.DATETIME && type != DbDimensionType.DAY) {
            return false;
        }
        try {
            return queryModel.findJdbcQueryColumnByName(field, false) != null;
        } catch (Exception ignore) {
            return false;
        }
    }

    /**
     * Throw {@code COLUMN_FIELD_NOT_FOUND} for a bare-dim reference.
     *
     * @param dimName    the bare dimension name (without $-attribute or AS alias)
     * @param userAlias  optional trailing {@code AS <alias>} the user wrote;
     *                   when present, the suggested fix preserves it so the
     *                   user's copy-paste fix carries the same alias
     * @param queryModel target QM model (for error payload)
     * @param invalidField the original column entry verbatim (may include
     *                     " AS alias"); reported in the payload so the
     *                     caller error message points to exactly what the
     *                     user wrote
     */
    private void rejectBareDimension(
            String dimName,
            String userAlias,
            QueryModel queryModel,
            String invalidField) {
        String modelName = queryModel.getName();
        String hintCaption = dimName + "$caption";
        String hintId = dimName + "$id";
        String suggestedFix = userAlias != null
                ? hintCaption + " AS " + userAlias
                : hintCaption;
        String message = "COLUMN_FIELD_NOT_FOUND: column '" + invalidField + "' references "
                + "dimension '" + dimName + "' directly. Dimensions are not projectable; "
                + "reference an attribute (e.g. '" + hintCaption + "' or '" + hintId
                + "'). Hint: did you mean '" + suggestedFix + "'?";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("errorCode", "COLUMN_FIELD_NOT_FOUND");
        payload.put("model", modelName);
        payload.put("invalidField", invalidField);
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
