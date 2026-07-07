package com.foggyframework.dataset.db.model.engine.expression.sql;

import com.foggyframework.dataset.db.model.engine.expression.CalculateQueryContext;
import com.foggyframework.dataset.db.model.engine.expression.SqlExpContext;
import com.foggyframework.dataset.db.model.engine.expression.SqlExpHolder;
import com.foggyframework.dataset.db.model.engine.expression.SqlFragment;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Restricted CALCULATE lowering:
 * CALCULATE(SUM(metric), REMOVE(groupByDim...)).
 */
public class SqlCalculateExp extends AbstractExp<String> {

    private static final long serialVersionUID = 1L;

    private final List<Exp> args;

    public SqlCalculateExp(List<Exp> args) {
        super("CALCULATE");
        this.args = args == null ? List.of() : List.copyOf(args);
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        SqlExpContext ctx = (SqlExpContext) evaluator.getVar(SqlExpContext.CONTEXT_KEY);
        if (ctx == null || ctx.getCalculateQueryContext() == null) {
            throw new IllegalArgumentException("CALCULATE_CONTEXT_UNAVAILABLE");
        }
        CalculateQueryContext calculateCtx = ctx.getCalculateQueryContext();
        if (calculateCtx.isTimeWindowPostCalculatedFields()) {
            throw new IllegalArgumentException("CALCULATE_TIMEWINDOW_POST_CALC_UNSUPPORTED");
        }
        if (!calculateCtx.isSupportsGroupedAggregateWindow()) {
            throw new IllegalArgumentException("CALCULATE_WINDOW_UNSUPPORTED");
        }
        if (args.size() != 2) {
            throw new IllegalArgumentException("CALCULATE_EXPR_UNSUPPORTED");
        }

        Exp aggregateExp = unwrap(args.get(0));
        if (!(aggregateExp instanceof SqlFunctionExp aggregate)
                || !"SUM".equalsIgnoreCase(aggregate.getFunctionName())
                || aggregate.getArgs().size() != 1) {
            throw new IllegalArgumentException("CALCULATE_EXPR_UNSUPPORTED");
        }

        Exp removeExp = unwrap(args.get(1));
        if (!(removeExp instanceof SqlRemoveExp remove)) {
            throw new IllegalArgumentException("CALCULATE_EXPR_UNSUPPORTED");
        }

        List<String> removeFields = dedupe(remove.getFieldNames());
        if (removeFields.isEmpty()) {
            throw new IllegalArgumentException("CALCULATE_REMOVE_FIELD_NOT_GROUPED");
        }

        List<String> groupByFields = dedupe(calculateCtx.getGroupByFields());
        Set<String> groupBySet = new LinkedHashSet<>(groupByFields);
        for (String removeField : removeFields) {
            if (!groupBySet.contains(removeField)) {
                throw new IllegalArgumentException("CALCULATE_REMOVE_FIELD_NOT_GROUPED: " + removeField);
            }
            if (containsField(calculateCtx.getSystemSliceFields(), removeField)) {
                throw new IllegalArgumentException("CALCULATE_SYSTEM_SLICE_OVERRIDE_DENIED: " + removeField);
            }
        }

        SqlFragment aggregateFragment = (SqlFragment) aggregateExp.evalResult(evaluator);
        if (!aggregateFragment.isHasAggregate()
                || !"SUM".equalsIgnoreCase(String.valueOf(aggregateFragment.getAggregationType()))) {
            throw new IllegalArgumentException("CALCULATE_EXPR_UNSUPPORTED");
        }

        List<String> remaining = new ArrayList<>();
        for (String groupBy : groupByFields) {
            if (!removeFields.contains(groupBy)) {
                remaining.add(groupBy);
            }
        }

        Set<DbQueryColumn> refs = new LinkedHashSet<>(aggregateFragment.getReferencedColumns());
        List<String> partitionSqls = new ArrayList<>();
        for (String field : remaining) {
            DbQueryColumn col = ctx.resolveColumn(field);
            refs.add(col);
            partitionSqls.add(col.getDeclare(ctx.getAppCtx(), ctx.getAlias(col), ctx.getDialect()));
        }
        for (String field : removeFields) {
            refs.add(ctx.resolveColumn(field));
        }

        String over = partitionSqls.isEmpty()
                ? ""
                : "PARTITION BY " + String.join(", ", partitionSqls);
        SqlFragment fragment = SqlFragment.windowFunction(
                "SUM(" + aggregateFragment.getSql() + ")",
                over,
                refs,
                aggregateFragment.getInferredType());
        fragment.setAggregationType(null);
        return fragment;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return SqlFragment.class;
    }

    public List<Exp> getArgs() {
        return args;
    }

    private static Exp unwrap(Exp exp) {
        Exp current = exp;
        while (current instanceof SqlExpHolder) {
            Exp inner = ((SqlExpHolder) current).getInnerSqlExp();
            if (inner == null || inner == current) {
                break;
            }
            current = inner;
        }
        return current;
    }

    private static List<String> dedupe(List<String> fields) {
        return new ArrayList<>(new LinkedHashSet<>(fields));
    }

    private static boolean containsField(Set<String> fields, String candidate) {
        if (fields == null || fields.isEmpty()) {
            return false;
        }
        String base = baseField(candidate);
        for (String field : fields) {
            if (field.equals(candidate) || baseField(field).equals(base)) {
                return true;
            }
        }
        return false;
    }

    private static String baseField(String field) {
        if (field == null) {
            return "";
        }
        int idx = field.indexOf('$');
        return (idx >= 0 ? field.substring(0, idx) : field).toLowerCase(Locale.ROOT);
    }
}
