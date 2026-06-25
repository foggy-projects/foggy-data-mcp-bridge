package com.foggyframework.dataset.db.model.proxy;

import com.foggyframework.core.ex.RX;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import jakarta.persistence.criteria.JoinType;
import lombok.Getter;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 聚合 JOIN 构建器。
 *
 * <p>用于在 QM V2 中声明右侧模型先按指定粒度聚合，再参与 JOIN：
 * <pre>{@code
 * fo.leftJoinAggregate(fs)
 *   .groupBy(fs.orderId)
 *   .sum(fs.salesAmount, 'salesAggAmount')
 *   .count('salesLineCount')
 *   .on(fo.orderId, fs.orderId)
 * }</pre>
 *
 * <p>该构建器只暴露受控的 groupBy/aggregate/filter 能力，不接收自由 SQL。
 */
public class AggregateJoinBuilder extends JoinBuilder {

    private final List<ColumnRef> groupByColumns = new ArrayList<>();

    private final List<AggregateMeasure> measures = new ArrayList<>();

    private final List<AggregateFilter> filters = new ArrayList<>();

    public AggregateJoinBuilder(TableModelProxy left, TableModelProxy right, JoinType joinType) {
        super(left, right, joinType);
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
            default -> super.invoke(evaluator, methodName, args);
        };
    }

    public List<ColumnRef> getGroupByColumns() {
        return Collections.unmodifiableList(groupByColumns);
    }

    public List<AggregateMeasure> getMeasures() {
        return Collections.unmodifiableList(measures);
    }

    public List<AggregateFilter> getFilters() {
        return Collections.unmodifiableList(filters);
    }

    private void addGroupBy(Object[] args) {
        RX.notNull(args, "aggregate join groupBy 至少需要一个字段");
        if (args.length == 0) {
            throw RX.throwAUserTip("aggregate join groupBy 至少需要一个字段");
        }
        for (Object arg : args) {
            groupByColumns.add(toColumnRef(arg));
        }
    }

    private void addMeasure(String functionName, Object[] args) {
        if (args == null || args.length == 0) {
            throw RX.throwAUserTip("aggregate join " + functionName + " 至少需要一个字段");
        }
        ColumnRef column = toColumnRef(args[0]);
        String alias = args.length > 1 ? toAlias(args[1]) : defaultMeasureAlias(functionName, column);
        measures.add(new AggregateMeasure(AggregateFunction.valueOf(functionName.toUpperCase(Locale.ROOT)), column, alias));
    }

    private void addCount(Object[] args) {
        ColumnRef column = null;
        String alias = "rowCount";
        if (args != null && args.length == 1) {
            if (isColumnRefLike(args[0])) {
                column = toColumnRef(args[0]);
                alias = defaultMeasureAlias("count", column);
            } else {
                alias = toAlias(args[0]);
            }
        } else if (args != null && args.length >= 2) {
            column = toColumnRef(args[0]);
            alias = toAlias(args[1]);
        }
        measures.add(new AggregateMeasure(AggregateFunction.COUNT, column, alias));
    }

    private void addCountDistinct(Object[] args) {
        if (args == null || args.length == 0) {
            throw RX.throwAUserTip("aggregate join countDistinct 至少需要一个字段");
        }
        ColumnRef column = toColumnRef(args[0]);
        String alias = args.length > 1 ? toAlias(args[1]) : defaultMeasureAlias("countDistinct", column);
        measures.add(new AggregateMeasure(AggregateFunction.COUNT_DISTINCT, column, alias));
    }

    private void addGroupConcat(Object[] args) {
        if (args == null || args.length == 0) {
            throw RX.throwAUserTip("aggregate join groupConcat 至少需要一个字段");
        }
        ColumnRef column = toColumnRef(args[0]);
        String alias = args.length > 1 ? toAlias(args[1]) : defaultMeasureAlias("groupConcat", column);
        measures.add(new AggregateMeasure(AggregateFunction.GROUP_CONCAT, column, alias));
    }

    private void addFilter(Object[] args, String operator) {
        if (args == null || args.length < 2) {
            throw RX.throwAUserTip("aggregate join filter 至少需要字段和值");
        }
        filters.add(new AggregateFilter(toColumnRef(args[0]), operator, args[1], false));
    }

    private void addInFilter(Object[] args) {
        if (args == null || args.length < 2) {
            throw RX.throwAUserTip("aggregate join filterIn 至少需要字段和值集合");
        }
        filters.add(new AggregateFilter(toColumnRef(args[0]), "IN", toValueList(args[1]), true));
    }

    private static ColumnRef toColumnRef(Object arg) {
        if (arg instanceof ColumnRef columnRef) {
            return columnRef;
        }
        if (arg instanceof DimensionProxy dimensionProxy) {
            return dimensionProxy.toColumnRef();
        }
        if (arg == null) {
            throw RX.throwAUserTip("aggregate join 字段引用不能为空");
        }
        throw RX.throwAUserTip("aggregate join 需要 ColumnRef 或 DimensionProxy，实际类型: " + arg.getClass().getName());
    }

    private static boolean isColumnRefLike(Object arg) {
        return arg instanceof ColumnRef || arg instanceof DimensionProxy;
    }

    private static String toAlias(Object arg) {
        if (arg == null) {
            throw RX.throwAUserTip("aggregate join 输出字段 alias 不能为空");
        }
        String alias = String.valueOf(arg).trim();
        if (alias.isEmpty()) {
            throw RX.throwAUserTip("aggregate join 输出字段 alias 不能为空");
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

    public enum AggregateFunction {
        SUM,
        AVG,
        MIN,
        MAX,
        COUNT,
        COUNT_DISTINCT,
        GROUP_CONCAT
    }

    @Getter
    public static class AggregateMeasure {
        private final AggregateFunction function;
        private final ColumnRef column;
        private final String alias;

        public AggregateMeasure(AggregateFunction function, ColumnRef column, String alias) {
            this.function = function;
            this.column = column;
            this.alias = alias;
        }
    }

    @Getter
    public static class AggregateFilter {
        private final ColumnRef column;
        private final String operator;
        private final Object value;
        private final boolean multiValue;

        public AggregateFilter(ColumnRef column, String operator, Object value, boolean multiValue) {
            this.column = column;
            this.operator = operator;
            this.value = value;
            this.multiValue = multiValue;
        }
    }
}
