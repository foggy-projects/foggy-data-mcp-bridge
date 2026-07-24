package com.foggyframework.dataset.model.engine.preagg;

import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.model.spi.*;
import com.foggyframework.dataset.model.spi.preagg.TimeGranularity;
import com.foggyframework.dataset.model.spi.support.AggregationDbColumn;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 预聚合查询需求构建器
 * <p>
 * 从 JdbcQuery 和 DbQueryRequestDef 中提取查询需求，用于预聚合匹配。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class PreAggQueryRequirementBuilder {

    private static final Pattern INLINE_AGGREGATE_PATTERN = Pattern.compile(
            "(?i)^\\s*(sum|count|min|max)\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*\\)"
                    + "\\s+as\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s*$"
    );
    private static final Pattern AGGREGATE_EXPRESSION_PATTERN = Pattern.compile(
            "(?i)^\\s*(sum|count|min|max)\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*\\)\\s*$"
    );
    private static final Pattern SLICE_EXPRESSION_FIELD_PATTERN = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_$]*"
    );

    /**
     * 从查询请求和 JdbcQuery 构建查询需求
     *
     * @param queryRequest 查询请求
     * @param jdbcQuery    已构建的 JdbcQuery（包含解析后的列信息）
     * @param queryModel   查询模型
     * @return 查询需求
     */
    public PreAggQueryRequirement build(DbQueryRequestDef queryRequest,
                                         JdbcQuery jdbcQuery,
                                         JdbcQueryModel queryModel) {
        return buildInternal(queryRequest, jdbcQuery, queryModel, false);
    }

    /**
     * Builds the independently provable requirement used by final-stage
     * returnTotal. Aggregate calculated projections such as
     * {@code sum(salesAmount) as teamSales} are reduced to their source
     * semantic measure; every other calculated projection remains
     * unsupported.
     */
    public PreAggQueryRequirement buildFinalStage(DbQueryRequestDef queryRequest,
                                                   JdbcQuery jdbcQuery,
                                                   JdbcQueryModel queryModel) {
        return buildInternal(queryRequest, jdbcQuery, queryModel, true);
    }

    /**
     * Builds the legacy returnTotal requirement. Its aggregate SQL may ignore
     * dimensions projected by the main query, but it must share the same
     * predicate provenance and fail-closed rules as the main rewrite.
     */
    public PreAggQueryRequirement buildAggregate(DbQueryRequestDef queryRequest,
                                                  JdbcQuery jdbcQuery,
                                                  JdbcQueryModel queryModel) {
        PreAggQueryRequirement requirement =
                buildInternal(queryRequest, jdbcQuery, queryModel, false);
        // The matcher uses this flag as its aggregate-optimization
        // applicability gate even when the original request is ungrouped.
        requirement.setHasGroupBy(true);
        return requirement;
    }

    private PreAggQueryRequirement buildInternal(DbQueryRequestDef queryRequest,
                                                  JdbcQuery jdbcQuery,
                                                  JdbcQueryModel queryModel,
                                                  boolean finalStage) {
        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        boolean unsupportedProjection = false;

        // 从 SELECT 列中提取维度和度量
        JdbcQuery.JdbcSelect select = jdbcQuery.getSelect();
        if (select != null && select.getColumns() != null) {
            if (log.isDebugEnabled()) {
                log.debug("JdbcQuery has {} columns in select", select.getColumns().size());
                for (int i = 0; i < select.getColumns().size(); i++) {
                    DbColumn col = select.getColumns().get(i);
                    log.debug("  Column[{}]: name={}, class={}, isDimension={}, isMeasure={}, isProperty={}",
                            i, col != null ? col.getName() : "null",
                            col != null ? col.getClass().getSimpleName() : "null",
                            col != null && col.isDimension(),
                            col != null && col.isMeasure(),
                            col != null && col.isProperty());
                }
            }
            for (DbColumn column : select.getColumns()) {
                if (!processColumn(column, requirement, queryModel, finalStage)) {
                    unsupportedProjection = true;
                }
            }
        } else {
            log.warn("JdbcQuery select is null or has no columns! select={}", select);
        }

        if (finalStage && !addFinalStageMeasureRequirements(
                queryRequest, jdbcQuery, queryModel, requirement)) {
            unsupportedProjection = true;
        }

        // 判断是否有 GROUP BY：
        // 1. 显式设置了 groupBy
        // 2. 隐式 GROUP BY：同时有维度（或维度属性）和度量时，SQL 会自动添加 GROUP BY
        boolean hasExplicitGroupBy = queryRequest.hasGroupBy();
        boolean hasImplicitGroupBy = !requirement.getDimensionNames().isEmpty()
                && !requirement.getMeasureAggregations().isEmpty();
        requirement.setHasGroupBy(hasExplicitGroupBy || hasImplicitGroupBy);

        applyPredicateRequirements(queryRequest, jdbcQuery, queryModel,
                requirement, finalStage, unsupportedProjection);

        if (log.isDebugEnabled()) {
            log.debug("Built query requirement: {} (explicitGroupBy={}, implicitGroupBy={}, hasWhereConditions={}, hasCustomSqlConditions={})",
                    requirement, hasExplicitGroupBy, hasImplicitGroupBy,
                    requirement.isHasWhereConditions(), requirement.isHasCustomSqlConditions());
        }

        return requirement;
    }

    private void applyPredicateRequirements(DbQueryRequestDef queryRequest,
                                            JdbcQuery jdbcQuery,
                                            JdbcQueryModel queryModel,
                                            PreAggQueryRequirement requirement,
                                            boolean finalStage,
                                            boolean initialCustomSqlConditions) {
        boolean hasWhereConditions = false;
        boolean hasCustomSqlConditions = initialCustomSqlConditions;

        // Structured request slices can be rebuilt against a materialization.
        if (queryRequest.getSlice() != null && !queryRequest.getSlice().isEmpty()) {
            hasWhereConditions = true;
            extractSliceColumns(queryRequest.getSlice(), requirement, queryModel);
            // The final-stage builder has a strict predicate prover for
            // $field/$expr. Main and legacy aggregate rewrites still use the
            // permissive predicate renderer, so they must refuse complex
            // predicates instead of risking a partially rebuilt WHERE clause.
            if (!finalStage && containsComplexSliceCondition(queryRequest.getSlice())) {
                hasCustomSqlConditions = true;
            }
        }

        // JdbcQuery.WHERE contains both compiled request slices and predicates
        // added by access builders/query scripts. Only the former can be
        // rebuilt against a pre-aggregation table.
        if (jdbcQuery.getWhere() != null && !jdbcQuery.getWhere().isEmpty()) {
            hasWhereConditions = true;
            boolean hasRequestSlices = queryRequest.getSlice() != null
                    && !queryRequest.getSlice().isEmpty();
            if (!hasRequestSlices || jdbcQuery.isRawSqlConditionAdded()
                    || jdbcQuery.isNonSliceWhereConditionAdded()) {
                hasCustomSqlConditions = true;
            }
        }

        // Neither main nor final-stage builders reconstruct HAVING today.
        boolean hasRequestHaving = queryRequest.getHaving() != null
                && !queryRequest.getHaving().isEmpty();
        boolean hasCompiledHaving = jdbcQuery.getHaving() != null
                && !jdbcQuery.getHaving().isEmpty();
        if (hasRequestHaving || hasCompiledHaving) {
            hasCustomSqlConditions = true;
        }

        requirement.setHasWhereConditions(hasWhereConditions);
        requirement.setHasCustomSqlConditions(hasCustomSqlConditions);
    }

    private boolean containsComplexSliceCondition(List<? extends CondRequestDef> conditions) {
        if (conditions == null) {
            return false;
        }
        for (CondRequestDef condition : conditions) {
            if (condition == null) {
                continue;
            }
            if (condition._isExpressionCondition() || condition._isFieldReference()) {
                return true;
            }
            if (condition._isLogicalGroup()
                    && containsComplexSliceCondition(condition._getGroupChildren())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 Slice 列表中提取涉及的列
     *
     * @param slices      Slice 条件列表
     * @param requirement 查询需求
     */
    private void extractSliceColumns(List<SliceRequestDef> slices,
                                     PreAggQueryRequirement requirement,
                                     JdbcQueryModel queryModel) {
        for (SliceRequestDef slice : slices) {
            extractSliceColumn(slice, requirement, queryModel);
        }
    }

    /**
     * 从单个 Slice 中提取涉及的列（递归处理 $or/$and 组合）
     */
    private void extractSliceColumn(CondRequestDef cond,
                                    PreAggQueryRequirement requirement,
                                    JdbcQueryModel queryModel) {
        if (cond == null) {
            return;
        }

        // $expr has no single left field. Collect every semantic token for
        // temporal-grain requirements; strict predicate reconstruction still
        // performs the final expression proof.
        if (cond._isExpressionCondition()) {
            if (cond.getExpr() != null) {
                Matcher matcher = SLICE_EXPRESSION_FIELD_PATTERN.matcher(cond.getExpr());
                while (matcher.find()) {
                    extractSliceTimeGranularity(matcher.group(), requirement, queryModel);
                }
            }
            return;
        }

        // 处理逻辑组合条件
        if (cond._isLogicalGroup()) {
            List<CondRequestDef> children = cond._getGroupChildren();
            if (children != null) {
                for (CondRequestDef child : children) {
                    extractSliceColumn(child, requirement, queryModel);
                }
            }
            return;
        }

        // 处理简单条件
        String field = cond.getField();
        if (field != null && !field.isEmpty()) {
            requirement.addSliceColumn(field);
            extractSliceTimeGranularity(field, requirement, queryModel);
            if (cond._isFieldReference()) {
                extractSliceTimeGranularity(cond._getReferencedField(), requirement, queryModel);
            }
            if (log.isDebugEnabled()) {
                log.debug("Extracted slice column: {}", field);
            }
        }
    }

    /**
     * A predicate on a time dimension's caption/id still requires the
     * dimension's natural grain. Without recording that requirement, a DAY
     * predicate could be matched to a MONTH materialization merely because
     * caption/id are otherwise implicit dimension properties.
     */
    private void extractSliceTimeGranularity(String field,
                                             PreAggQueryRequirement requirement,
                                             JdbcQueryModel queryModel) {
        if (queryModel == null || field == null || field.isBlank()) {
            return;
        }

        int dollarIndex = field.indexOf('$');
        String dimensionName = dollarIndex > 0 ? field.substring(0, dollarIndex) : field;
        String propertyName = dollarIndex > 0 && dollarIndex < field.length() - 1
                ? field.substring(dollarIndex + 1)
                : null;
        TimeGranularity granularity = null;

        DbColumn semanticColumn = queryModel.findJdbcColumnForCond(field, false, true);
        DbDimensionColumn dimensionColumn = semanticColumn == null
                ? null
                : semanticColumn.getDecorate(DbDimensionColumn.class);
        if (dimensionColumn == null) {
            DbColumn baseDimensionColumn =
                    queryModel.findJdbcColumnForCond(dimensionName, false, true);
            if (baseDimensionColumn != null) {
                dimensionColumn = baseDimensionColumn.getDecorate(DbDimensionColumn.class);
            }
        }

        DbDimension dimension = dimensionColumn != null ? dimensionColumn.getDimension() : null;
        DbDimension temporalDimension = findTemporalDimension(queryModel, dimensionName, dimension);
        if (temporalDimension != null) {
            dimension = temporalDimension;
            if (dimension.getDimensionPath() != null) {
                dimensionName = dimension.getDimensionPath().toDotFormat();
            }
            TimeGranularity naturalGranularity = detectNaturalTimeGranularity(dimension);
            if (naturalGranularity != null) {
                if (propertyName != null) {
                    granularity = TIME_PROPERTY_GRANULARITY.get(propertyName.toLowerCase());
                }
                if (granularity == null && propertyName != null
                        && !"caption".equalsIgnoreCase(propertyName)
                        && !"id".equalsIgnoreCase(propertyName)) {
                    granularity = detectColumnGranularity(semanticColumn);
                }
                if (granularity == null && (propertyName == null
                        || "caption".equalsIgnoreCase(propertyName)
                        || "id".equalsIgnoreCase(propertyName))) {
                    granularity = naturalGranularity;
                }
            }
        }

        if (granularity != null) {
            requirement.setTimeGranularity(dimensionName, granularity);
        }
    }

    private DbDimension findTemporalDimension(JdbcQueryModel queryModel,
                                              String dimensionName,
                                              DbDimension resolvedDimension) {
        if (detectNaturalTimeGranularity(resolvedDimension) != null) {
            return resolvedDimension;
        }

        DbDimension queryDimension = queryModel.findDimension(dimensionName);
        if (detectNaturalTimeGranularity(queryDimension) != null) {
            return queryDimension;
        }

        if (queryModel.getJdbcModel() != null) {
            DbDimension modelDimension =
                    queryModel.getJdbcModel().findJdbcDimensionByName(dimensionName);
            if (detectNaturalTimeGranularity(modelDimension) != null) {
                return modelDimension;
            }
        }
        return null;
    }

    /**
     * 处理单个列，提取维度或度量信息
     */
    private boolean processColumn(DbColumn column, PreAggQueryRequirement requirement,
                                  JdbcQueryModel queryModel, boolean finalStage) {
        if (column == null) {
            return false;
        }

        DbAggregation requestedAggregation = column.getAggregation();
        DbColumn semanticColumn = resolveSemanticColumn(column, queryModel);
        if (semanticColumn == null) {
            log.warn("Cannot resolve aggregate projection alias '{}' to a semantic model field; "
                    + "pre-aggregation will be refused", column.getAlias());
            return false;
        }

        boolean aggregateProjection = column instanceof AggregationDbColumn
                && requestedAggregation != null
                && requestedAggregation != DbAggregation.NONE;
        if (aggregateProjection && !semanticColumn.isMeasure()
                && !(finalStage && semanticColumn.isCalculatedField())) {
            log.warn("Aggregate projection '{}' targets non-measure semantic field '{}'; "
                            + "pre-aggregation will be refused",
                    requestedAggregation, semanticColumn.getName());
            return false;
        }

        // 跳过计算字段（暂不支持预聚合）
        if (semanticColumn.isCalculatedField()) {
            if (finalStage) {
                // Deferred to addFinalStageMeasureRequirements(), which only
                // accepts a single supported aggregate over a semantic measure.
                return true;
            }
            if (log.isDebugEnabled()) {
                log.debug("Refusing pre-aggregation for calculated field: {}", semanticColumn.getName());
            }
            return false;
        }

        // 记录列的类型以便调试
        if (log.isDebugEnabled()) {
            log.debug("Processing column: name={}, isDimension={}, isMeasure={}, isProperty={}",
                    semanticColumn.getName(), semanticColumn.isDimension(),
                    semanticColumn.isMeasure(), semanticColumn.isProperty());
        }

        // 判断是维度还是度量
        if (semanticColumn.isDimension()) {
            processDimensionColumn(semanticColumn, requirement, queryModel);
            return true;
        } else if (semanticColumn.isMeasure()) {
            processMeasureColumn(semanticColumn, requestedAggregation, requirement);
            return true;
        } else if (semanticColumn.isProperty()) {
            processPropertyColumn(semanticColumn, requirement, queryModel);
            return true;
        }
        return false;
    }

    private boolean addFinalStageMeasureRequirements(DbQueryRequestDef queryRequest,
                                                      JdbcQuery jdbcQuery,
                                                      JdbcQueryModel queryModel,
                                                      PreAggQueryRequirement requirement) {
        Set<String> selectedCalculatedAliases = new LinkedHashSet<>();
        if (jdbcQuery.getSelect() != null && jdbcQuery.getSelect().getColumns() != null) {
            for (DbColumn column : jdbcQuery.getSelect().getColumns()) {
                DbColumn semanticColumn = resolveSemanticColumn(column, queryModel);
                if (semanticColumn != null && semanticColumn.isCalculatedField()) {
                    selectedCalculatedAliases.add(
                            column.getAlias() != null ? column.getAlias() : semanticColumn.getName());
                }
            }
        }

        Set<String> supportedAliases = new LinkedHashSet<>();
        if (queryRequest.getColumns() != null) {
            for (String columnDef : queryRequest.getColumns()) {
                if (columnDef == null) {
                    continue;
                }
                Matcher matcher = INLINE_AGGREGATE_PATTERN.matcher(columnDef.trim());
                if (!matcher.matches()) {
                    continue;
                }
                DbAggregation aggregation = DbAggregation.valueOf(matcher.group(1).toUpperCase());
                if (!addFinalStageMeasure(
                        requirement, queryModel, matcher.group(2), aggregation)) {
                    return false;
                }
                supportedAliases.add(matcher.group(3));
            }
        }

        if (queryRequest.getCalculatedFields() != null) {
            for (CalculatedFieldDef fieldDef : queryRequest.getCalculatedFields()) {
                if (fieldDef == null || fieldDef.getName() == null
                        || !selectedCalculatedAliases.contains(fieldDef.getName())
                        || fieldDef.getExpression() == null) {
                    continue;
                }
                Matcher matcher = AGGREGATE_EXPRESSION_PATTERN.matcher(fieldDef.getExpression().trim());
                if (!matcher.matches()) {
                    return false;
                }
                DbAggregation expressionAggregation =
                        DbAggregation.valueOf(matcher.group(1).toUpperCase());
                if (fieldDef.getAgg() != null && !fieldDef.getAgg().isBlank()) {
                    DbAggregation declaredAggregation;
                    try {
                        declaredAggregation = DbAggregation.valueOf(fieldDef.getAgg().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                    if (declaredAggregation != expressionAggregation) {
                        return false;
                    }
                }
                if (!addFinalStageMeasure(
                        requirement, queryModel, matcher.group(2), expressionAggregation)) {
                    return false;
                }
                supportedAliases.add(fieldDef.getName());
            }
        }

        return supportedAliases.containsAll(selectedCalculatedAliases);
    }

    private boolean addFinalStageMeasure(PreAggQueryRequirement requirement,
                                         JdbcQueryModel queryModel,
                                         String measureName,
                                         DbAggregation aggregation) {
        DbColumn measure = queryModel.findJdbcColumnForCond(measureName, false, true);
        if (measure == null || !measure.isMeasure() || measure.isCalculatedField()) {
            return false;
        }
        DbAggregation existing = requirement.getMeasureAggregations().get(measure.getName());
        if (existing != null && existing != aggregation) {
            return false;
        }
        requirement.addMeasure(measure.getName(), aggregation);
        return true;
    }

    private DbColumn resolveSemanticColumn(DbColumn column, JdbcQueryModel queryModel) {
        if (!(column instanceof AggregationDbColumn)) {
            return column;
        }
        String alias = column.getAlias();
        if (alias == null || alias.isEmpty()) {
            return null;
        }
        return queryModel.findJdbcColumnForCond(alias, false, true);
    }

    /**
     * 处理维度列
     */
    private void processDimensionColumn(DbColumn column, PreAggQueryRequirement requirement,
                                         JdbcQueryModel queryModel) {
        DbDimensionColumn dimColumn = column.getDecorate(DbDimensionColumn.class);
        if (dimColumn == null) {
            return;
        }

        DbDimension dimension = dimColumn.getDimension();
        if (dimension == null) {
            return;
        }

        String dimensionName = dimension.getName();

        // 使用维度路径作为名称（如 product.category）
        if (dimension.getDimensionPath() != null) {
            dimensionName = dimension.getDimensionPath().toDotFormat();
        }

        requirement.addDimension(dimensionName);

        // 检测时间粒度
        TimeGranularity granularity = detectTimeGranularity(column, dimension);
        if (granularity != null) {
            requirement.setTimeGranularity(dimensionName, granularity);
        }

        // 如果是属性列，添加属性信息
        String propertyName = extractPropertyName(column);
        if (propertyName != null) {
            requirement.addDimensionProperty(dimensionName, propertyName);
        }
    }

    /**
     * 时间粒度属性映射：属性名 -> TimeGranularity
     */
    private static final Map<String, TimeGranularity> TIME_PROPERTY_GRANULARITY = Map.of(
            "year", TimeGranularity.YEAR,
            "quarter", TimeGranularity.QUARTER,
            "month", TimeGranularity.MONTH,
            "week", TimeGranularity.WEEK,
            "day", TimeGranularity.DAY,
            "hour", TimeGranularity.HOUR,
            "minute", TimeGranularity.MINUTE
    );

    /**
     * 处理属性列
     * <p>
     * 属性列的处理：从列名中解析维度和属性名。
     * 列名格式通常为：{dimensionName}${propertyName}
     * 例如：customer$memberLevel, product$categoryName
     * </p>
     * <p>
     * 注意：属性列引用了某个维度，因此也需要将维度添加到需求中。
     * </p>
     * <p>
     * 特殊处理：时间粒度属性（如 salesDate$month）会被识别并设置为查询粒度。
     * </p>
     */
    private void processPropertyColumn(DbColumn column, PreAggQueryRequirement requirement,
                                        JdbcQueryModel queryModel) {
        DbPropertyColumn propColumn = column.getDecorate(DbPropertyColumn.class);
        if (propColumn == null) {
            return;
        }

        DbProperty property = propColumn.getProperty();
        if (property == null) {
            return;
        }

        // 从列名中解析维度和属性名
        // 列名格式：{dimensionName}${propertyName}
        String columnName = column.getName();
        if (columnName == null) {
            return;
        }

        int dollarIndex = columnName.indexOf('$');
        if (dollarIndex > 0 && dollarIndex < columnName.length() - 1) {
            String dimensionName = columnName.substring(0, dollarIndex);
            String propertyName = columnName.substring(dollarIndex + 1);
            // 属性列引用了维度，需要同时添加维度和属性
            requirement.addDimension(dimensionName);
            requirement.addDimensionProperty(dimensionName, propertyName);

            // 只有已被语义模型证明为时间维度，year/month/day 等属性
            // 才能解释为时间粒度；普通维度的同名属性仍按普通属性匹配。
            DbDimensionColumn dimensionColumn = column.getDecorate(DbDimensionColumn.class);
            DbDimension dimension = dimensionColumn != null ? dimensionColumn.getDimension() : null;
            DbDimension temporalDimension =
                    findTemporalDimension(queryModel, dimensionName, dimension);
            TimeGranularity granularity = temporalDimension != null
                    ? TIME_PROPERTY_GRANULARITY.get(propertyName.toLowerCase())
                    : null;
            if (granularity != null) {
                requirement.setTimeGranularity(dimensionName, granularity);
                if (log.isDebugEnabled()) {
                    log.debug("Detected time granularity from property: dimension={}, property={}, granularity={}",
                            dimensionName, propertyName, granularity);
                }
            }
        }
    }

    /**
     * 处理度量列
     */
    private void processMeasureColumn(DbColumn column, DbAggregation requestedAggregation,
                                      PreAggQueryRequirement requirement) {
        String measureName = column.getName();
        DbAggregation aggregation = requestedAggregation;

        if (aggregation == null || aggregation == DbAggregation.NONE) {
            aggregation = column.getAggregation();
        }
        if (aggregation == null || aggregation == DbAggregation.NONE) {
            aggregation = DbAggregation.SUM;
        }

        requirement.addMeasure(measureName, aggregation);
    }

    /**
     * 从列中检测时间粒度
     * <p>
     * 根据列的类型或名称推断时间粒度。
     * </p>
     */
    private TimeGranularity detectTimeGranularity(DbColumn column, DbDimension dimension) {
        TimeGranularity detected = detectColumnGranularity(column);
        if (detected != null) {
            return detected;
        }

        // A time dimension caption/id denotes the dimension's natural grain.
        // Query-model wrappers often expose it as TEXT, so inspect the
        // underlying caption and key columns before giving up.
        if (dimension != null) {
            detected = detectColumnGranularity(dimension.getCaptionDbColumn());
            if (detected == null) {
                detected = detectColumnGranularity(dimension.getPrimaryKeyDbColumn());
            }
            if (detected == null && dimension.getTimeRole() != null
                    && !dimension.getTimeRole().isBlank()) {
                String timeRole = dimension.getTimeRole().toLowerCase();
                if (timeRole.contains("time")) {
                    detected = TimeGranularity.MINUTE;
                } else if (timeRole.contains("date")) {
                    detected = TimeGranularity.DAY;
                }
            }
            if (detected == null && dimension.getName() != null) {
                String dimensionName = dimension.getName().toLowerCase();
                if (dimensionName.endsWith("date") || dimensionName.endsWith("_date")) {
                    detected = TimeGranularity.DAY;
                } else if (dimensionName.endsWith("time") || dimensionName.endsWith("_time")) {
                    detected = TimeGranularity.MINUTE;
                }
            }
        }
        return detected;
    }

    /**
     * Returns a time dimension's natural grain using semantic/type evidence
     * only. This deliberately avoids classifying ordinary dimensions from
     * names such as productDate or month.
     */
    private TimeGranularity detectNaturalTimeGranularity(DbDimension dimension) {
        if (dimension == null) {
            return null;
        }

        String timeRole = dimension.getTimeRole();
        if (timeRole != null && !timeRole.isBlank()) {
            String normalizedRole = timeRole.toLowerCase();
            if (normalizedRole.contains("time")) {
                return TimeGranularity.MINUTE;
            }
            if (normalizedRole.contains("date")) {
                return TimeGranularity.DAY;
            }
        }

        TimeGranularity captionType = detectColumnTypeGranularity(dimension.getCaptionDbColumn());
        return captionType != null
                ? captionType
                : detectColumnTypeGranularity(dimension.getPrimaryKeyDbColumn());
    }

    private TimeGranularity detectColumnTypeGranularity(DbColumn column) {
        if (column == null) {
            return null;
        }
        if (column.getType() == DbColumnType.DAY) {
            return TimeGranularity.DAY;
        }
        if (column.getType() == DbColumnType.DATETIME) {
            return TimeGranularity.MINUTE;
        }
        return null;
    }

    private TimeGranularity detectColumnGranularity(DbColumn column) {
        if (column == null) {
            return null;
        }

        String columnName = column.getName() != null ? column.getName().toLowerCase() : "";

        // 根据列名推断粒度
        if (columnName.contains("year") || columnName.endsWith("_year")) {
            return TimeGranularity.YEAR;
        }
        if (columnName.contains("quarter") || columnName.endsWith("_quarter")) {
            return TimeGranularity.QUARTER;
        }
        if (columnName.contains("month") || columnName.endsWith("_month")) {
            return TimeGranularity.MONTH;
        }
        if (columnName.contains("week") || columnName.endsWith("_week")) {
            return TimeGranularity.WEEK;
        }
        if (columnName.contains("day") || columnName.endsWith("_day") || columnName.endsWith("_date")) {
            return TimeGranularity.DAY;
        }
        if (columnName.contains("hour") || columnName.endsWith("_hour")) {
            return TimeGranularity.HOUR;
        }
        if (columnName.contains("minute") || columnName.endsWith("_minute")) {
            return TimeGranularity.MINUTE;
        }

        // 根据列类型推断
        DbColumnType columnType = column.getType();
        if (columnType == DbColumnType.DAY) {
            return TimeGranularity.DAY;
        }
        if (columnType == DbColumnType.DATETIME) {
            return TimeGranularity.MINUTE; // 默认分钟级
        }

        return null;
    }

    /**
     * 从列名中提取属性名
     * <p>
     * 例如：从 "product$category_name" 提取 "category_name"
     * </p>
     */
    private String extractPropertyName(DbColumn column) {
        String columnName = column.getName();
        if (columnName == null) {
            return null;
        }

        // 检查是否包含属性分隔符 $
        int lastDollar = columnName.lastIndexOf('$');
        if (lastDollar > 0 && lastDollar < columnName.length() - 1) {
            return columnName.substring(lastDollar + 1);
        }

        return null;
    }
}
