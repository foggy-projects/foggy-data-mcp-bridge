package com.foggyframework.dataset.model.engine.expression;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.engine.compose.plan.TimeAxisResolver;
import com.foggyframework.dataset.model.engine.expression.sql.*;
import com.foggyframework.dataset.model.spi.*;
import com.foggyframework.fsscript.parser.spi.Exp;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import java.util.*;

/** Request-local binding of the row-stage dependency closure of a calendar axis. */
public record TimeAxisBinding(TimeAxisResolver.Axis axis, List<CalculatedFieldDef> fields) {
    public TimeAxisBinding { fields = List.copyOf(fields); }
    public Set<String> names() {
        return fields.stream().map(CalculatedFieldDef::getName).collect(java.util.stream.Collectors.toSet());
    }

    public static TimeAxisBinding resolve(QueryModel model, String name, List<CalculatedFieldDef> requested,
                                          ApplicationContext app) {
        Map<String, CalculatedFieldDef> definitions = new LinkedHashMap<>();
        if (model != null && model.getPredefinedCalculatedFields() != null)
            model.getPredefinedCalculatedFields().forEach(f -> definitions.put(f.getName(), f));
        Set<String> predefined = Set.copyOf(definitions.keySet());
        Set<String> conflicts = new HashSet<>();
        if (requested != null) for (var field : requested) {
            var previous = definitions.putIfAbsent(field.getName(), field);
            if (previous != null && (!predefined.contains(field.getName())
                    || !SqlCalculatedFieldProcessor.sameCalculatedFieldDefinition(previous, field))) conflicts.add(field.getName());
        }
        if (!definitions.containsKey(name)) return new TimeAxisBinding(TimeAxisResolver.resolve(model, name), List.of());
        if (!(model instanceof JdbcQueryModel jdbc)) throw invalid(name, "FIELD_NOT_TIME", "A JDBC calendar axis is required");
        List<CalculatedFieldDef> closure = new ArrayList<>();
        collect(name, definitions, new LinkedHashSet<>(), new HashSet<>(), closure);
        for (var field : closure) if (conflicts.contains(field.getName()))
            throw invalid(field.getName(), "AXIS_NAME_COLLISION", "Calendar axis dependency has conflicting definitions");
        SqlExpContext context = new SqlExpContext(jdbc, jdbc.getDialect(), app);
        for (CalculatedFieldDef field : closure) {
            if ((field.getAgg() != null && !field.getAgg().isBlank())
                    || field.getPartitionBy() != null && !field.getPartitionBy().isEmpty()
                    || field.getWindowOrderBy() != null && !field.getWindowOrderBy().isEmpty()
                    || field.getWindowFrame() != null && !field.getWindowFrame().isBlank())
                throw invalid(field.getName(), "AXIS_STAGE_UNSUPPORTED", "Calendar axes must be row-stage expressions");
            Exp expression = CalculatedFieldService.compileExpression(field.getExpression());
            validateExpression(expression, field.getName());
            validateConditions(expression, field.getName(), context, app);
            var column = CalculatedFieldService.processCalculatedField(field, context, app);
            if (column.getSqlFragment().isHasAggregate() || column.getSqlFragment().isHasWindow())
                throw invalid(field.getName(), "AXIS_STAGE_UNSUPPORTED", "Calendar axes cannot depend on aggregate/window results");
            if (field.getEmptyDefault() != null && (column.getType() == DbColumnType.DAY || column.getType() == DbColumnType.DATETIME))
                throw invalid(field.getName(), "FIELD_NOT_TIME", "Use an explicitly typed date expression for a date fallback, not emptyDefault");
        }
        var column = context.tryResolveColumn(name);
        if (column == null || (column.getType() != DbColumnType.DAY && column.getType() != DbColumnType.DATETIME))
            throw invalid(name, "FIELD_NOT_TIME", "Calculated calendar axis must have an unambiguous date type");
        return new TimeAxisBinding(new TimeAxisResolver.Axis(name, name, null), closure);
    }

    private static void collect(String name, Map<String, CalculatedFieldDef> definitions, Set<String> path,
                                Set<String> done, List<CalculatedFieldDef> result) {
        if (!definitions.containsKey(name) || done.contains(name)) return;
        if (!path.add(name)) throw invalid(name, "AXIS_DEPENDENCY_CYCLE", "Circular calendar axis dependency: " + path);
        var original = definitions.get(name);
        for (String ref : CalculatedFieldDependencies.direct(original)) collect(ref, definitions, path, done, result);
        path.remove(name); done.add(name);
        // Never attach compiled state or change origins on shared predefined/model definitions.
        var copy = new CalculatedFieldDef(); BeanUtils.copyProperties(original, copy); copy.setCompiledExp(null);
        result.add(copy);
    }

    private static void validateExpression(Exp expression, String field) {
        Exp node = WindowExpressionLowering.unwrap(expression);
        if (node instanceof SqlCalculateExp) throw invalid(field, "AXIS_STAGE_UNSUPPORTED", "CALCULATE is not a row-stage date");
        if (node instanceof SqlFunctionExp f) {
            String function = f.getFunctionName().toUpperCase(Locale.ROOT);
            if (AllowedFunctions.isAggregateFunction(function) || AllowedFunctions.isWindowFunction(function))
                throw invalid(field, "AXIS_STAGE_UNSUPPORTED", "Aggregate/window date axes are unsupported");
            if (Set.of("NOW", "CURRENT_DATE", "CURRENT_TIMESTAMP", "SYSDATE", "GETDATE", "RAND", "RANDOM", "UUID").contains(function))
                throw invalid(field, "AXIS_NONDETERMINISTIC", "Calendar axis must be deterministic");
            f.getArgs().forEach(a -> validateExpression(a, field));
        } else if (node instanceof SqlBinaryExp b) {
            validateExpression(b.getLeft(), field); validateExpression(b.getRight(), field);
        } else if (node instanceof SqlUnaryExp u) validateExpression(u.getOperand(), field);
        else if (node instanceof SqlListExp l) l.getItems().forEach(a -> validateExpression(a, field));
        else if (!(node instanceof SqlColumnRefExp) && !(node instanceof SqlLiteralExp)
                && !(node instanceof com.foggyframework.fsscript.exp.NullExp))
            throw invalid(field, "AXIS_EXPRESSION_UNSUPPORTED", "Calendar axes accept SQL expressions only; use == for equality, never assignment");
    }

    private static QueryCalculationException invalid(String field, String code, String message) {
        return new QueryCalculationException("TIMEWINDOW_" + code, field, "timeWindow.field", message,
                "Use a deterministic row-stage date expression with authorized dependencies.", Map.of());
    }

    private static void validateConditions(Exp expression, String field, SqlExpContext context, ApplicationContext app) {
        Exp node = WindowExpressionLowering.unwrap(expression);
        if (node instanceof SqlFunctionExp f) {
            if (Set.of("IF", "IIF").contains(f.getFunctionName().toUpperCase(Locale.ROOT)) && !f.getArgs().isEmpty()) {
                var condition = CalculatedFieldService.evaluateExpression(f.getArgs().get(0), context, app);
                if (condition.getInferredType() != DbColumnType.BOOL && !"NULL".equalsIgnoreCase(condition.getSql()))
                    throw invalid(field, "AXIS_EXPRESSION_UNSUPPORTED", "Date condition must be boolean; implicit truth-value conversion is not portable");
            }
            f.getArgs().forEach(a -> validateConditions(a, field, context, app));
        } else if (node instanceof SqlBinaryExp b) {
            validateConditions(b.getLeft(), field, context, app); validateConditions(b.getRight(), field, context, app);
        } else if (node instanceof SqlUnaryExp u) validateConditions(u.getOperand(), field, context, app);
        else if (node instanceof SqlListExp l) l.getItems().forEach(a -> validateConditions(a, field, context, app));
    }
}
