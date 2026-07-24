package com.foggyframework.dataset.model.proxy;

import com.foggyframework.core.ex.RX;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.PropertyHolder;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 聚合关系代理。
 *
 * <p>用于把一个 TM 先变成受控聚合 relation，再作为普通 join 右侧使用：
 * <pre>{@code
 * const sales = loadTableModel('FactSalesModel');
 * const fs = sales.filterEq(sales.orderStatus, 'COMPLETED')
 *     .groupBy('orderId')
 *     .as('fsByOrder');
 *
 * fo.leftJoin(fs).on(fo.orderId, fs.orderId)
 * }</pre>
 */
public class AggregateRelationProxy extends TableModelProxy {

    private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final List<ColumnRef> groupByColumns = new ArrayList<>();

    private final List<AggregateJoinBuilder.AggregateMeasure> measures = new ArrayList<>();

    private final List<AggregateJoinBuilder.AggregateFilter> filters = new ArrayList<>();

    public AggregateRelationProxy(String modelName) {
        super(modelName);
    }

    public AggregateRelationProxy(String modelName, String alias) {
        super(modelName, alias);
    }

    public static AggregateRelationProxy from(TableModelProxy source) {
        if (source instanceof AggregateRelationProxy aggregateRelationProxy) {
            return aggregateRelationProxy;
        }
        return new AggregateRelationProxy(source.getModelName(), source.getAlias());
    }

    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        return switch (methodName) {
            case "groupBy", "by" -> {
                addGroupBy(args);
                yield this;
            }
            case "sum", "avg", "min", "max" -> {
                addMeasure(methodName, args);
                yield this;
            }
            case "count" -> {
                addCount(args);
                yield this;
            }
            case "countDistinct", "countD", "count_distinct" -> {
                addCountDistinct(args);
                yield this;
            }
            case "groupConcat", "group_concat", "stringAgg", "string_agg" -> {
                addGroupConcat(args);
                yield this;
            }
            case "filterEq", "whereEq" -> {
                addFilter(args, "=");
                yield this;
            }
            case "filterNeq", "whereNeq" -> {
                addFilter(args, "<>");
                yield this;
            }
            case "filterGt", "whereGt" -> {
                addFilter(args, ">");
                yield this;
            }
            case "filterGte", "whereGte" -> {
                addFilter(args, ">=");
                yield this;
            }
            case "filterLt", "whereLt" -> {
                addFilter(args, "<");
                yield this;
            }
            case "filterLte", "whereLte" -> {
                addFilter(args, "<=");
                yield this;
            }
            case "filterIn", "whereIn" -> {
                addInFilter(args);
                yield this;
            }
            case "as" -> {
                setAlias(toAlias(args));
                // Aggregate relation aliases identify the generated SQL relation,
                // but historically do not qualify its public QM output fields.
                // ColumnRef still retains this runtime alias through getTableAlias(),
                // so owner-aware resolution does not require exposing it in schema.
                yield this;
            }
            default -> {
                Object result = super.invoke(evaluator, methodName, args);
                yield result == PropertyHolder.NO_MATCH ? PropertyHolder.NO_MATCH : result;
            }
        };
    }

    public List<ColumnRef> getGroupByColumns() {
        return Collections.unmodifiableList(groupByColumns);
    }

    public List<AggregateJoinBuilder.AggregateMeasure> getMeasures() {
        return Collections.unmodifiableList(measures);
    }

    public List<AggregateJoinBuilder.AggregateFilter> getFilters() {
        return Collections.unmodifiableList(filters);
    }

    private void addGroupBy(Object[] args) {
        RX.notNull(args, "aggregate relation groupBy 至少需要一个字段");
        if (args.length == 0) {
            throw RX.throwAUserTip("aggregate relation groupBy 至少需要一个字段");
        }
        for (Object arg : args) {
            groupByColumns.add(toColumnRef(arg));
        }
    }

    private void addMeasure(String functionName, Object[] args) {
        if (args == null || args.length == 0) {
            throw RX.throwAUserTip("aggregate relation " + functionName + " 至少需要一个字段");
        }
        ColumnRef column = toColumnRef(args[0]);
        String alias = args.length > 1 ? toMeasureAlias(args[1]) : defaultMeasureAlias(functionName, column);
        measures.add(new AggregateJoinBuilder.AggregateMeasure(
                AggregateJoinBuilder.AggregateFunction.valueOf(functionName.toUpperCase(Locale.ROOT)),
                column,
                alias));
    }

    private void addCount(Object[] args) {
        ColumnRef column = null;
        String alias = "rowCount";
        if (args != null && args.length == 1) {
            if (isColumnRefLike(args[0])) {
                column = toColumnRef(args[0]);
                alias = defaultMeasureAlias("count", column);
            } else {
                alias = toMeasureAlias(args[0]);
            }
        } else if (args != null && args.length >= 2) {
            column = toColumnRef(args[0]);
            alias = toMeasureAlias(args[1]);
        }
        measures.add(new AggregateJoinBuilder.AggregateMeasure(
                AggregateJoinBuilder.AggregateFunction.COUNT,
                column,
                alias));
    }

    private void addCountDistinct(Object[] args) {
        if (args == null || args.length == 0) {
            throw RX.throwAUserTip("aggregate relation countDistinct 至少需要一个字段");
        }
        ColumnRef column = toColumnRef(args[0]);
        String alias = args.length > 1 ? toMeasureAlias(args[1]) : defaultMeasureAlias("countDistinct", column);
        measures.add(new AggregateJoinBuilder.AggregateMeasure(
                AggregateJoinBuilder.AggregateFunction.COUNT_DISTINCT,
                column,
                alias));
    }

    private void addGroupConcat(Object[] args) {
        if (args == null || args.length == 0) {
            throw RX.throwAUserTip("aggregate relation groupConcat 至少需要一个字段");
        }
        ColumnRef column = toColumnRef(args[0]);
        String alias = args.length > 1 ? toMeasureAlias(args[1]) : defaultMeasureAlias("groupConcat", column);
        measures.add(new AggregateJoinBuilder.AggregateMeasure(
                AggregateJoinBuilder.AggregateFunction.GROUP_CONCAT,
                column,
                alias));
    }

    private void addFilter(Object[] args, String operator) {
        if (args == null || args.length < 2) {
            throw RX.throwAUserTip("aggregate relation filter 至少需要字段和值");
        }
        filters.add(new AggregateJoinBuilder.AggregateFilter(toColumnRef(args[0]), operator, args[1], false));
    }

    private void addInFilter(Object[] args) {
        if (args == null || args.length < 2) {
            throw RX.throwAUserTip("aggregate relation filterIn 至少需要字段和值集合");
        }
        filters.add(new AggregateJoinBuilder.AggregateFilter(toColumnRef(args[0]), "IN", toValueList(args[1]), true));
    }

    private ColumnRef toColumnRef(Object arg) {
        if (arg instanceof ColumnRef columnRef) {
            return columnRef;
        }
        if (arg instanceof DimensionProxy dimensionProxy) {
            return dimensionProxy.toColumnRef();
        }
        if (arg instanceof String text) {
            String field = text.trim();
            if (field.isEmpty()) {
                throw RX.throwAUserTip("aggregate relation 字段引用不能为空");
            }
            return new ColumnRef(this, field);
        }
        if (arg == null) {
            throw RX.throwAUserTip("aggregate relation 字段引用不能为空");
        }
        throw RX.throwAUserTip("aggregate relation 需要 ColumnRef、DimensionProxy 或字段名字符串，实际类型: " + arg.getClass().getName());
    }

    private static boolean isColumnRefLike(Object arg) {
        return arg instanceof ColumnRef || arg instanceof DimensionProxy;
    }

    private String toAlias(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            throw RX.throwAUserTip("aggregate relation alias 不能为空");
        }
        String alias = String.valueOf(args[0]).trim();
        if (alias.isEmpty()) {
            throw RX.throwAUserTip("aggregate relation alias 不能为空");
        }
        if (!SIMPLE_IDENTIFIER.matcher(alias).matches()) {
            throw RX.throwAUserTip("aggregate relation alias 仅支持简单标识符: " + alias);
        }
        return alias;
    }

    private static String toMeasureAlias(Object arg) {
        if (arg == null) {
            throw RX.throwAUserTip("aggregate relation 输出字段 alias 不能为空");
        }
        String alias = String.valueOf(arg).trim();
        if (alias.isEmpty()) {
            throw RX.throwAUserTip("aggregate relation 输出字段 alias 不能为空");
        }
        return alias;
    }

    private static String defaultMeasureAlias(String functionName, ColumnRef column) {
        return column.getAliasRef() + measureAliasSuffix(functionName);
    }

    private static String measureAliasSuffix(String functionName) {
        String normalized = functionName == null ? "" : functionName.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "countdistinct", "countd", "count_distinct" -> "CountDistinct";
            case "groupconcat", "group_concat", "stringagg", "string_agg" -> "GroupConcat";
            default -> capitalize(normalized);
        };
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static List<Object> toValueList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                values.add(Array.get(value, i));
            }
            return values;
        }
        return List.of(value);
    }
}
