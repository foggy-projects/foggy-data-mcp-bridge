package com.foggyframework.dataset.model.engine.expression;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.WindowOrderDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.spi.*;
import com.foggyframework.dataset.model.spi.support.CalculatedDbColumn;
import com.foggyframework.fsscript.parser.spi.Exp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 计算字段处理器
 * <p>
 * 用于 JDBC/SQL 类型的 QueryModel，将计算字段表达式编译为 SQL 片段。
 * 共享逻辑（拓扑排序、表达式编译/求值）委托给 {@link CalculatedFieldService}，
 * 本类仅负责窗口函数包装等 SQL 特有能力。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
public class SqlCalculatedFieldProcessor implements CalculatedFieldProcessor {

    private static final Pattern AGGREGATE_FUNCTION_CALL = Pattern.compile(
            "\\b(sum|avg|count|countd|count_distinct|min|max|stddev_pop|stddev_samp|var_pop|var_samp)\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    private final JdbcQueryModel queryModel;
    private final FDialect dialect;
    private SqlExpContext context;
    private CalculateQueryContext calculateQueryContext;
    private boolean groupedQuery;
    private final Map<String, Integer> originalIndexes = new LinkedHashMap<>();
    private final List<CalculatedDbColumn> liftedColumns = new ArrayList<>();
    private int hiddenSequence;
    private final Map<String, String> projectionSources = new LinkedHashMap<>();


    public SqlCalculatedFieldProcessor(JdbcQueryModel queryModel, FDialect dialect) {
        this.queryModel = queryModel;
        this.dialect = dialect;
    }

    @Override
    public List<CalculatedDbColumn> processCalculatedFields(
            List<CalculatedFieldDef> calculatedFields,
            ApplicationContext appCtx) {
        if (calculatedFields == null || calculatedFields.isEmpty()) {
            return new ArrayList<>();
        }

        // 创建 SQL 表达式上下文
        this.context = new SqlExpContext(queryModel, dialect, appCtx);
        this.context.setCalculateQueryContext(calculateQueryContext);

        List<CalculatedFieldDef> normalizedFields = deduplicateQueryModelPredefinedFields(calculatedFields);

        projectionSources.clear();
        for (CalculatedFieldDef field : normalizedFields) {
            if (field.getOrigin() == CalculatedFieldDef.Origin.PLAIN_ALIAS) {
                projectionSources.put(field.getName(), field.getExpression());
            }
        }
        originalIndexes.clear();
        for (int i = 0; i < normalizedFields.size(); i++) {
            originalIndexes.putIfAbsent(normalizedFields.get(i).getName(), i);
        }
        liftedColumns.clear();
        hiddenSequence = 0;
        // 按依赖关系排序（委托 CalculatedFieldService）
        Set<String> declaredNames = new HashSet<>();
        for (CalculatedFieldDef field : normalizedFields) {
            if (!declaredNames.add(field.getName())) throw diagnostic(field, "CALCULATED_FIELD_DUPLICATE",
                    "Calculated field name is declared more than once.", "Use a unique name for each calculated field.");
            Set<String> refs;
            try { refs = new LinkedHashSet<>(CalculatedFieldService.extractColumnReferences(field.getExpression())); }
            catch (RuntimeException failure) {
                var error = diagnostic(field, "CALCULATED_FIELD_EXPRESSION_INVALID",
                        "Calculated expression could not be parsed.", "Check expression syntax and supported functions.");
                error.initCause(failure);
                throw error;
            }
            if (field.getPartitionBy() != null) refs.addAll(field.getPartitionBy());
            if (field.getWindowOrderBy() != null) field.getWindowOrderBy().forEach(order -> refs.add(order.getField()));
            if (refs.contains(field.getName()) && context.tryResolveModelColumn(field.getName()) == null)
                throw diagnostic(field, "CALCULATED_FIELD_DEPENDENCY_CYCLE", "Field references itself without a model source binding.",
                        "Remove the self-reference or reference an existing model field.");
        }
        List<CalculatedFieldDef> sortedFields;
        try { sortedFields = CalculatedFieldService.sortByDependencies(normalizedFields); }
        catch (CalculatedFieldService.DependencyCycleException failure) {
            CalculatedFieldDef source = normalizedFields.stream()
                    .filter(field -> failure.cycleFields.contains(field.getName())).findFirst().orElseThrow();
            var base = diagnostic(source, "CALCULATED_FIELD_DEPENDENCY_CYCLE", failure.getMessage(),
                    "Remove the cycle between calculated fields, including partitionBy/windowOrderBy references.");
            Map<String,Object> details = new LinkedHashMap<>(base.getDetails());
            details.put("cycleFields", failure.cycleFields);
            details.put("blockedFields", failure.blockedFields);
            throw new QueryCalculationException(base.getCode(), base.getField(), base.getPath(), failure.getMessage(),
                    base.getSuggestion(), details);
        }

        List<CalculatedDbColumn> result = new ArrayList<>(sortedFields.size());

        for (CalculatedFieldDef fieldDef : sortedFields) {
            CalculatedDbColumn column = doProcessCalculatedField(fieldDef, appCtx);
            result.addAll(liftedColumns);
            liftedColumns.clear();
            result.add(column);
        }

        return result;
    }

    @Override
    public CalculatedDbColumn processCalculatedField(
            CalculatedFieldDef fieldDef,
            ApplicationContext appCtx) {
        // 如果上下文不存在，创建一个新的
        if (this.context == null) {
            this.context = new SqlExpContext(queryModel, dialect, appCtx);
            this.context.setCalculateQueryContext(calculateQueryContext);
        }
        return doProcessCalculatedField(fieldDef, appCtx);
    }

    /**
     * 处理单个计算字段的内部实现
     * <p>
     * 相比 {@link CalculatedFieldService#processCalculatedField}，额外支持窗口函数（PARTITION BY / ORDER BY / frame）。
     * </p>
     */
    private CalculatedDbColumn doProcessCalculatedField(
            CalculatedFieldDef fieldDef,
            ApplicationContext appCtx) {
        // 验证必填字段
        RX.hasText(fieldDef.getName(), "CALCULATED_FIELD_EXPRESSION_INVALID: calculatedFields.name is required.");
        RX.hasText(fieldDef.getExpression(), "CALCULATED_FIELD_EXPRESSION_INVALID: calculatedFields['"
                + fieldDef.getName() + "'].expression is required.");

        // 检查名称是否已存在
        if (context.hasColumn(fieldDef.getName()) && !isInlineAggregateAlias(fieldDef)) {
            throw RX.throwAUserTip("CALCULATED_FIELD_NAME_COLLISION: 计算字段名称已存在: "
                    + fieldDef.getName()
                    + "。请更换 calculatedFields.name，或直接在 columns 中引用已有字段。");
        }

        try {
            // 1. 获取或编译表达式 AST（委托 CalculatedFieldService）
            Exp compiledExp = fieldDef.getCompiledExp();
            if (compiledExp == null) {
                compiledExp = CalculatedFieldService.compileExpression(fieldDef.getExpression());
                fieldDef.setCompiledExp(compiledExp);
            } else if (log.isDebugEnabled()) {
                log.debug("Reusing pre-compiled AST for field: {}", fieldDef.getName());
            }
            CalculateExpressionAnalyzer.validate(compiledExp);

            boolean windowClause = fieldDef.getPartitionBy() != null || fieldDef.getWindowOrderBy() != null
                    || StringUtils.isNotEmpty(fieldDef.getWindowFrame());
            boolean directWindow = windowClause && WindowExpressionLowering.windowCall(compiledExp);
            int dependencyDepth = dependencyDepth(compiledExp);
            if (windowClause) {
                if (!dialect.supportsWindowFunctions() || !dialect.supportsCte()) throw diagnostic(fieldDef,
                        "WINDOW_DIALECT_UNSUPPORTED", "This datasource cannot execute the required window result stages.",
                        "Use a datasource with window and CTE support; for calendar comparisons use a supported timeWindow query.");
                validateWindowDependencies(fieldDef, compiledExp);
                if (!directWindow) {
                    final int before = liftedColumns.size();
                    compiledExp = WindowExpressionLowering.lift(compiledExp, function -> {
                        validateWindowCall(fieldDef, function);
                        SqlFragment windowFragment = evaluateAtResultStage(function, appCtx);
                        windowFragment = wrapWithWindowClause(windowFragment, fieldDef, context, appCtx);
                        String hidden;
                        do { hidden = "__foggy_window_" + hiddenSequence++; }
                        while (context.hasColumn(hidden) || originalIndexes.containsKey(hidden));
                        CalculatedDbColumn lifted = new CalculatedDbColumn(hidden, hidden, windowFragment);
                        lifted.setResultStageDepth(1);
                        lifted.setNeedsCteWrapping(true);
                        context.registerCalculatedColumn(hidden, lifted);
                        liftedColumns.add(lifted);
                        return new com.foggyframework.dataset.model.engine.expression.sql.SqlColumnRefExp(hidden);
                    });
                    if (liftedColumns.size() == before) throw diagnostic(fieldDef, "WINDOW_FUNCTION_REQUIRED",
                            "Window options require a window or aggregate function node.", "Remove the window options from this scalar field.");
                    dependencyDepth = dependencyDepth(compiledExp);
                } else {
                    validateWindowCall(fieldDef, (com.foggyframework.dataset.model.engine.expression.sql.SqlFunctionExp)
                            WindowExpressionLowering.unwrap(compiledExp));
                }
            }
            if (!windowClause && dependencyDepth == 0) {
                WindowExpressionLowering.lift(compiledExp, function -> {
                    if (AllowedFunctions.isWindowFunction(function.getFunctionName())) throw diagnostic(fieldDef,
                            "WINDOW_SPECIFICATION_REQUIRED", "Window function requires windowOrderBy or partitionBy options.",
                            "Add windowOrderBy with an existing ordering field (and partitionBy when required).");
                    return function;
                });
            }
            boolean resultStage = windowClause || dependencyDepth > 0;
            if (!windowClause && dependencyDepth > 0 && WindowExpressionLowering.containsCall(compiledExp)) {
                throw diagnostic(fieldDef, "WINDOW_RESULT_REAGGREGATION_UNSUPPORTED",
                        "An aggregate/window call cannot consume a window result in this query.",
                        "Use a scalar calculated field over the window output; use a separate query for reaggregation.");
            }
            boolean aggregateMeasureFormula = !resultStage && isGroupedMeasureFormula(fieldDef);
            context.setAggregateMeasureReferences(aggregateMeasureFormula);
            context.setResultStageReferences(resultStage);
            SqlFragment sqlFragment;
            try {
                sqlFragment = CalculatedFieldService.evaluateExpression(compiledExp, context, appCtx);
            } finally {
                context.setAggregateMeasureReferences(false);
                context.setResultStageReferences(false);
            }
            if (directWindow) sqlFragment = wrapWithWindowClause(sqlFragment, fieldDef, context, appCtx);
            int stageDepth = directWindow ? 1 : dependencyDepth > 0 ? dependencyDepth + 1 : 0;
            if (stageDepth > 0) {
                sqlFragment.setHasWindow(true);
                sqlFragment.setHasAggregate(false);
                sqlFragment.setAggregationType(null);
            }

            // 2.2 如果推断了聚合类型，传递到 SqlFragment
            if (fieldDef.getAgg() != null
                    && sqlFragment.getAggregationType() == null
                    && !sqlFragment.isHasAggregate()
                    && !sqlFragment.isHasWindow()) {
                sqlFragment.setAggregationType(fieldDef.getAgg().toUpperCase());
                if (log.isDebugEnabled()) {
                    log.debug("Applied inferred aggregation from CalculatedFieldDef: {} -> agg={}",
                            fieldDef.getName(), fieldDef.getAgg());
                }
            }
            sqlFragment = CalculatedFieldService.applyEmptyDefault(sqlFragment, fieldDef);

            // 3. 创建 CalculatedJdbcColumn
            String caption = StringUtils.isNotEmpty(fieldDef.getCaption()) ? fieldDef.getCaption() : fieldDef.getName();
            CalculatedDbColumn column = new CalculatedDbColumn(
                    fieldDef.getName(),
                    caption,
                    sqlFragment,
                    fieldDef.getDescription()
            );

            // Mark the column as needing CTE wrapping if it went through wrapWithWindowClause
            if (stageDepth > 0) {
                column.setResultStageDepth(stageDepth);
                column.setNeedsCteWrapping(true);
            }

            // 4. 注册到上下文（支持后续字段引用）
            column.setOutputAlias(fieldDef.getAlias());
            context.registerCalculatedColumn(fieldDef.getName(), column);

            if (log.isDebugEnabled()) {
                log.debug("Processed calculated field: {} = {} (hasAggregate={})",
                        fieldDef.getName(), sqlFragment.getSql(), sqlFragment.isHasAggregate());
            }

            return column;

        } catch (QueryCalculationException e) {
            throw e;
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            QueryCalculationException typed = QueryCalculationException.find(e);
            if (typed != null) throw typed;
            throw diagnostic(fieldDef, "CALCULATED_FIELD_EXPRESSION_INVALID", e.getMessage(),
                    "Check the expression and its model/previous-stage field references.");
        }
    }

    private SqlFragment evaluateAtResultStage(Exp expression, ApplicationContext appCtx) {
        context.setResultStageReferences(true);
        try { return CalculatedFieldService.evaluateExpression(expression, context, appCtx); }
        finally { context.setResultStageReferences(false); }
    }

    private int dependencyDepth(Exp expression) {
        Set<String> refs = new LinkedHashSet<>();
        CalculatedFieldService.extractColumnReferences(expression, refs);
        int depth = 0;
        for (String ref : refs) {
            DbQueryColumn resolved = context.tryResolveColumn(ref);
            if (resolved instanceof CalculatedDbColumn calculated) depth = Math.max(depth, calculated.getResultStageDepth());
        }
        return depth;
    }

    private void validateWindowDependencies(CalculatedFieldDef field, Exp expression) {
        Set<String> refs = new LinkedHashSet<>();
        CalculatedFieldService.extractColumnReferences(expression, refs);
        if (field.getPartitionBy() != null) refs.addAll(field.getPartitionBy());
        if (field.getWindowOrderBy() != null) field.getWindowOrderBy().forEach(order -> refs.add(order.getField()));
        for (String ref : refs) {
            DbQueryColumn resolved = context.tryResolveColumn(ref);
            boolean staged = resolved instanceof CalculatedDbColumn calculated && calculated.getResultStageDepth() > 0;
            if (resolved == null || staged) {
                String member = "expression";
                if (field.getPartitionBy() != null && field.getPartitionBy().contains(ref)) member = "partitionBy[" + field.getPartitionBy().indexOf(ref) + "]";
                if (field.getWindowOrderBy() != null) for (int i = 0; i < field.getWindowOrderBy().size(); i++)
                    if (ref.equals(field.getWindowOrderBy().get(i).getField())) member = "windowOrderBy[" + i + "].field";
                String code = staged ? "WINDOW_ON_WINDOW_UNSUPPORTED" : "COMPOSE_WINDOW_ORDER_BY_UNRESOLVABLE";
                String base = field.getSourcePath() == null ? "calculatedFields[" + originalIndexes.getOrDefault(field.getName(), 0) + "]" : field.getSourcePath();
                throw new QueryCalculationException(code, field.getName(), base + "." + member,
                        "Window field cannot consume '" + ref + "' at this stage.",
                        "Use an existing source/grouped field for window arguments, partitionBy and windowOrderBy; scalar dependents may follow the window.",
                        Map.of("dependency", ref, "producerStage", staged ? "window_result" : "unresolved", "consumerStage", "window_result"));
            }
        }
    }

    private void validateWindowCall(CalculatedFieldDef field,
            com.foggyframework.dataset.model.engine.expression.sql.SqlFunctionExp function) {
        for (Exp arg : function.getArgs()) {
            if (WindowExpressionLowering.containsCall(arg)) throw diagnostic(field, "WINDOW_NESTED_AGGREGATE_UNSUPPORTED",
                    "Nested aggregate/window syntax inside " + function.getFunctionName() + " is unsupported in calculatedFields.",
                    "Select the grouped aggregate with an alias, then use that alias as the window argument (for example lag(amount)).");
        }
    }

    private QueryCalculationException diagnostic(CalculatedFieldDef field, String code, String message, String suggestion) {
        return new QueryCalculationException(code, field.getName(),
                (field.getSourcePath() == null ? "calculatedFields[" + originalIndexes.getOrDefault(field.getName(), 0) + "]" : field.getSourcePath()) + ".expression", message, suggestion,
                Map.of("consumerStage", "window_result", "field", field.getName(), "safeToAutoRepair", false));
    }

    private boolean isInlineAggregateAlias(CalculatedFieldDef fieldDef) {
        return fieldDef.getOrigin() == CalculatedFieldDef.Origin.INLINE_EXPRESSION
                && (StringUtils.isNotEmpty(fieldDef.getAgg()) || hasAggregateFunctionCall(fieldDef));
    }

    private boolean hasAggregateFunctionCall(CalculatedFieldDef fieldDef) {
        return fieldDef.getExpression() != null
                && AGGREGATE_FUNCTION_CALL.matcher(fieldDef.getExpression()).find();
    }

    private List<CalculatedFieldDef> deduplicateQueryModelPredefinedFields(List<CalculatedFieldDef> calculatedFields) {
        if (calculatedFields == null || calculatedFields.size() < 2
                || queryModel == null || queryModel.getPredefinedCalculatedFields() == null
                || queryModel.getPredefinedCalculatedFields().isEmpty()) {
            return calculatedFields;
        }

        Set<String> seenPredefinedNames = new HashSet<>();
        List<CalculatedFieldDef> result = new ArrayList<>(calculatedFields.size());
        boolean changed = false;
        for (CalculatedFieldDef fieldDef : calculatedFields) {
            if (isExactQueryModelPredefinedCalculatedField(fieldDef)) {
                if (!seenPredefinedNames.add(fieldDef.getName())) {
                    changed = true;
                    if (log.isDebugEnabled()) {
                        log.debug("Ignored duplicate QM predefined calculated field: {}", fieldDef.getName());
                    }
                    continue;
                }
            }
            result.add(fieldDef);
        }
        return changed ? result : calculatedFields;
    }

    private boolean isExactQueryModelPredefinedCalculatedField(CalculatedFieldDef fieldDef) {
        if (fieldDef == null || queryModel == null || queryModel.getPredefinedCalculatedFields() == null) {
            return false;
        }
        for (CalculatedFieldDef predefined : queryModel.getPredefinedCalculatedFields()) {
            if (predefined == fieldDef || sameCalculatedFieldDefinition(predefined, fieldDef)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameCalculatedFieldDefinition(CalculatedFieldDef left, CalculatedFieldDef right) {
        return left != null
                && right != null
                && Objects.equals(left.getName(), right.getName())
                && Objects.equals(left.getAlias(), right.getAlias())
                && Objects.equals(left.getExpression(), right.getExpression())
                && Objects.equals(left.getAgg(), right.getAgg())
                && Objects.equals(left.getPartitionBy(), right.getPartitionBy())
                && Objects.equals(left.getWindowOrderBy(), right.getWindowOrderBy())
                && Objects.equals(left.getWindowFrame(), right.getWindowFrame())
                && Objects.equals(left.getType(), right.getType())
                && Objects.equals(left.getEmptyDefault(), right.getEmptyDefault());
    }

    /**
     * 包装窗口函数 OVER 子句
     * <p>
     * 将已编译的 SQL 表达式追加 OVER (PARTITION BY ... ORDER BY ... frame) 子句。
     * partitionBy/windowOrderBy 中的列名会被解析为实际的 SQL 引用。
     * </p>
     *
     * @param baseSql  基础 SQL 片段（如 "RANK()" 或 "AVG(m1.amount)"）
     * @param fieldDef 计算字段定义（含 partitionBy/windowOrderBy/windowFrame）
     * @param context  SQL 表达式上下文
     * @param appCtx   Spring ApplicationContext
     * @return 包含完整 OVER 子句的 SqlFragment
     */
    private SqlFragment wrapWithWindowClause(SqlFragment baseSql, CalculatedFieldDef fieldDef,
                                              SqlExpContext context, ApplicationContext appCtx) {
        StringBuilder overClause = new StringBuilder();
        Set<DbQueryColumn> refs = new LinkedHashSet<>(baseSql.getReferencedColumns());

        // PARTITION BY
        if (fieldDef.getPartitionBy() != null && !fieldDef.getPartitionBy().isEmpty()) {
            overClause.append("PARTITION BY ");
            List<String> partitionSqls = new ArrayList<>();
            for (String colName : fieldDef.getPartitionBy()) {
                DbQueryColumn col = context.resolveColumn(colName);
                String partAlias = context.getAlias(col);
                String colSql = resolveColumnSql(col, partAlias, appCtx, true);
                partitionSqls.add(colSql);
                refs.add(col);
            }
            overClause.append(String.join(", ", partitionSqls));
        }

        // ORDER BY
        if (fieldDef.getWindowOrderBy() != null && !fieldDef.getWindowOrderBy().isEmpty()) {
            if (overClause.length() > 0) {
                overClause.append(" ");
            }
            overClause.append("ORDER BY ");
            List<String> orderSqls = new ArrayList<>();
            for (WindowOrderDef orderDef : fieldDef.getWindowOrderBy()) {
                String orderField = orderDef.getField();
                // In CTE Wrapping architecture, Window CFs are evaluated in Stage 2.
                // We resolve the column and reference it by its alias (which will be projected by Stage 1).
                DbQueryColumn col = context.tryResolveColumn(orderField);
                if (col == null) {
                    throw RX.throwAUserTip("COMPOSE_WINDOW_ORDER_BY_UNRESOLVABLE: calculatedFields["
                            + fieldDef.getName() + "].windowOrderBy field '"
                            + orderField + "' cannot be resolved. Use a valid base model measure, dimension, or prior calc-field name.");
                }
                String alias = context.getAlias(col);
                String colSql = resolveColumnSql(col, alias, appCtx, true);
                orderSqls.add(colSql + " " + orderDef.getNormalizedDir());
                refs.add(col);
            }
            overClause.append(String.join(", ", orderSqls));
        }

        // Window frame
        if (StringUtils.isNotEmpty(fieldDef.getWindowFrame())) {
            if (overClause.length() > 0) {
                overClause.append(" ");
            }
            overClause.append(fieldDef.getWindowFrame());
        }

        // ── CTE Wrapping: rewrite base expression to use alias-based column references ──
        // In two-stage CTE architecture, the base expression (before OVER) must reference
        // columns by their projected alias from Stage 1, not physical table expressions.
        // e.g., AVG(t1.sales_amount) → AVG("salesAmount")
        String rewrittenBaseSql = baseSql.getSql();

        return SqlFragment.windowFunction(
                rewrittenBaseSql,
                overClause.toString(),
                refs,
                baseSql.getInferredType()
        );
    }

    /**
     * 解析列的 SQL 表达式
     */
    private String resolveColumnSql(DbQueryColumn col, String alias, ApplicationContext appCtx) {
        return resolveColumnSql(col, alias, appCtx, false);
    }

    /**
     * 解析列的 SQL 表达式
     * <p>
     * 在 CTE Wrapping 架构中，如果当前正在生成 Stage 2（外层）的窗口函数，
     * 需要设置 asAlias=true，这样它引用的内层列（如 SUM(...)）会直接引用其投影出的别名/名称，
     * 而不是展开为内层的物理表达式。
     * </p>
     */
    private String resolveColumnSql(DbQueryColumn col, String alias, ApplicationContext appCtx, boolean asAlias) {
        if (asAlias) {
            return dialect.quoteIdentifier(JdbcModelQueryEngine.getCteProjectionAlias(col));
        } else {
            if (col instanceof CalculatedDbColumn) {
                return ((CalculatedDbColumn) col).getDeclare();
            }
            if (col.getSelectColumn() != null) {
                return col.getSelectColumn().getDeclare(appCtx, alias, dialect);
            }
            return alias != null ? alias + "." + col.getAlias() : col.getAlias();
        }
    }

    /**
     * Rewrite the base SQL expression for CTE wrapping.
     * <p>
     * Replaces physical column references (e.g., {@code t1.sales_amount}) with
     * their projected alias (e.g., {@code "salesAmount"}) so the expression is
     * valid in Stage 2 of a CTE-wrapped query.
     * </p>
     * <p>
     * Uses the SqlFragment's referenced columns to build a mapping from
     * physical SQL declare → alias SQL, then performs string replacement.
     * Falls back to the original SQL if no replacements can be made.
     * </p>
     */
    /**
     * 获取 SQL 表达式上下文
     */
    public SqlExpContext getContext() {
        return context;
    }

    public void setCalculateQueryContext(CalculateQueryContext calculateQueryContext) {
        this.calculateQueryContext = calculateQueryContext;
        if (this.context != null) {
            this.context.setCalculateQueryContext(calculateQueryContext);
        }
    }

    public void setGroupedQuery(boolean groupedQuery) {
        this.groupedQuery = groupedQuery;
    }

    private boolean isGroupedMeasureFormula(CalculatedFieldDef fieldDef) {
        if (fieldDef == null || StringUtils.isEmpty(fieldDef.getExpression())) {
            return false;
        }
        if (fieldDef.getOrigin() == CalculatedFieldDef.Origin.PLAIN_ALIAS) {
            return false;
        }
        if (!isSelectedField(fieldDef) && isQueryModelPredefinedCalculatedField(fieldDef)) {
            return false;
        }
        if (!groupedQuery && !isSoleSelectedPredefinedCalculatedField(fieldDef)) {
            return false;
        }
        if (fieldDef.getAgg() != null || fieldDef.getPartitionBy() != null || fieldDef.getWindowOrderBy() != null) {
            return false;
        }
        boolean baseAggregateMeasureRef = false;
        for (String ref : CalculatedFieldService.extractColumnReferences(fieldDef.getExpression())) {
            DbQueryColumn column = context.tryResolveColumn(ref);
            if (column instanceof CalculatedDbColumn calcColumn
                    && !calcColumn.hasWindow()
                    && calcColumn.getAggregationType() != null) {
                return true;
            }
            if (column != null
                    && column.isMeasure()
                    && column.getAggregation() != null
                    && column.getAggregation() != DbAggregation.NONE) {
                baseAggregateMeasureRef = true;
            }
        }
        return baseAggregateMeasureRef && !AGGREGATE_FUNCTION_CALL.matcher(fieldDef.getExpression()).find();
    }

    private String projectionSource(String field) {
        Set<String> visited = new HashSet<>();
        while (projectionSources.containsKey(field) && visited.add(field)) field = projectionSources.get(field);
        return field;
    }

    private boolean isSelectedField(CalculatedFieldDef fieldDef) {
        return calculateQueryContext != null
                && calculateQueryContext.getSelectedFields() != null
                && calculateQueryContext.getSelectedFields().stream()
                .map(this::projectionSource).anyMatch(fieldDef.getName()::equals);
    }

    private boolean isQueryModelPredefinedCalculatedField(CalculatedFieldDef fieldDef) {
        if (queryModel == null || queryModel.getPredefinedCalculatedFields() == null) {
            return false;
        }
        for (CalculatedFieldDef predefined : queryModel.getPredefinedCalculatedFields()) {
            if (predefined == fieldDef || fieldDef.getName().equals(predefined.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSoleSelectedPredefinedCalculatedField(CalculatedFieldDef fieldDef) {
        if (calculateQueryContext == null
                || calculateQueryContext.getSelectedFields() == null
                || calculateQueryContext.getSelectedFields().size() != 1
                || !fieldDef.getName().equals(projectionSource(calculateQueryContext.getSelectedFields().get(0)))) {
            return false;
        }
        for (CalculatedFieldDef predefined : queryModel.getPredefinedCalculatedFields()) {
            if (fieldDef.getName().equals(predefined.getName())) {
                return true;
            }
        }
        return false;
    }
}
