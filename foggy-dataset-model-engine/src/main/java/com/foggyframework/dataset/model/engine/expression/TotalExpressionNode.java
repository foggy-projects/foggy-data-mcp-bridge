package com.foggyframework.dataset.model.engine.expression;

import com.foggyframework.dataset.db.dialect.FDialect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Structured companion to a generated {@link SqlFragment}.
 *
 * <p>It preserves aggregate leaves, calculated references and scalar
 * composition without parsing generated SQL text. The main query keeps using
 * {@code SqlFragment.sql}; totalData replaces aggregate leaves with merged
 * state expressions.</p>
 */
public final class TotalExpressionNode {

    public enum Kind {
        RAW, REFERENCE, BINARY, UNARY, FUNCTION, CUSTOM_FUNCTION, TEMPLATE, AGGREGATE
    }

    private final Kind kind;
    private final String token;
    private final String fallbackSql;
    private final List<TotalExpressionNode> children;
    private final AggregateLeaf aggregateLeaf;

    private TotalExpressionNode(Kind kind,
                                String token,
                                String fallbackSql,
                                List<TotalExpressionNode> children,
                                AggregateLeaf aggregateLeaf) {
        this.kind = kind;
        this.token = token;
        this.fallbackSql = fallbackSql;
        this.children = children == null ? List.of() : List.copyOf(children);
        this.aggregateLeaf = aggregateLeaf;
    }

    public static TotalExpressionNode raw(String sql) {
        return new TotalExpressionNode(Kind.RAW, sql, null, List.of(), null);
    }

    public static TotalExpressionNode reference(String alias, String fallbackSql) {
        return new TotalExpressionNode(Kind.REFERENCE, alias, fallbackSql, List.of(), null);
    }

    public static TotalExpressionNode binary(TotalExpressionNode left,
                                             String operator,
                                             TotalExpressionNode right) {
        return new TotalExpressionNode(Kind.BINARY, operator, null,
                List.of(orRaw(left, "NULL"), orRaw(right, "NULL")), null);
    }

    public static TotalExpressionNode unary(String operator, TotalExpressionNode operand) {
        return new TotalExpressionNode(Kind.UNARY, operator, null,
                List.of(orRaw(operand, "NULL")), null);
    }

    public static TotalExpressionNode function(String functionName,
                                               List<TotalExpressionNode> arguments) {
        return new TotalExpressionNode(Kind.FUNCTION, functionName, null,
                safeChildren(arguments), null);
    }

    public static TotalExpressionNode customFunction(String functionName,
                                                     List<TotalExpressionNode> arguments) {
        return new TotalExpressionNode(Kind.CUSTOM_FUNCTION, functionName, null,
                safeChildren(arguments), null);
    }

    public static TotalExpressionNode template(String template,
                                               List<TotalExpressionNode> arguments) {
        return new TotalExpressionNode(Kind.TEMPLATE, template, null,
                safeChildren(arguments), null);
    }

    public static TotalExpressionNode aggregate(String aggregation,
                                                BoundSqlExpression argument) {
        AggregateLeaf leaf = new AggregateLeaf(
                aggregation, argument, startsWithDistinct(argument == null ? null : argument.sql()));
        return new TotalExpressionNode(Kind.AGGREGATE, aggregation, null, List.of(), leaf);
    }

    private static boolean startsWithDistinct(String sql) {
        if (sql == null) {
            return false;
        }
        String trimmed = sql.stripLeading();
        if (!trimmed.regionMatches(true, 0, "DISTINCT", 0, "DISTINCT".length())) {
            return false;
        }
        return trimmed.length() == "DISTINCT".length()
                || Character.isWhitespace(trimmed.charAt("DISTINCT".length()));
    }

    private static TotalExpressionNode orRaw(TotalExpressionNode node, String fallback) {
        return node == null ? raw(fallback) : node;
    }

    private static List<TotalExpressionNode> safeChildren(List<TotalExpressionNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        List<TotalExpressionNode> safe = new ArrayList<>(nodes.size());
        for (TotalExpressionNode node : nodes) {
            safe.add(orRaw(node, "NULL"));
        }
        return safe;
    }

    public Kind getKind() {
        return kind;
    }

    public String getReferenceAlias() {
        return kind == Kind.REFERENCE ? token : null;
    }

    public AggregateLeaf getAggregateLeaf() {
        return aggregateLeaf;
    }

    public List<TotalExpressionNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public boolean containsAggregate() {
        if (kind == Kind.AGGREGATE) {
            return true;
        }
        return children.stream().anyMatch(TotalExpressionNode::containsAggregate);
    }

    public void visitAggregateLeaves(java.util.function.Consumer<AggregateLeaf> visitor) {
        if (kind == Kind.AGGREGATE) {
            visitor.accept(aggregateLeaf);
            return;
        }
        children.forEach(child -> child.visitAggregateLeaves(visitor));
    }

    public void visitReferences(java.util.function.Consumer<String> visitor) {
        if (kind == Kind.REFERENCE) {
            visitor.accept(token);
            return;
        }
        children.forEach(child -> child.visitReferences(visitor));
    }

    public String render(FDialect dialect,
                         Function<String, String> referenceRenderer,
                         Function<AggregateLeaf, String> aggregateRenderer) {
        return switch (kind) {
            case RAW -> token;
            case REFERENCE -> {
                String rendered = referenceRenderer.apply(token);
                yield rendered == null ? fallbackSql : rendered;
            }
            case BINARY -> "(" + renderChild(0, dialect, referenceRenderer, aggregateRenderer)
                    + " " + token + " "
                    + renderChild(1, dialect, referenceRenderer, aggregateRenderer) + ")";
            case UNARY -> "(" + token + " "
                    + renderChild(0, dialect, referenceRenderer, aggregateRenderer) + ")";
            case FUNCTION -> token + "(" + renderArguments(dialect, referenceRenderer, aggregateRenderer) + ")";
            case CUSTOM_FUNCTION -> renderCustomFunction(dialect, referenceRenderer, aggregateRenderer);
            case TEMPLATE -> renderTemplate(dialect, referenceRenderer, aggregateRenderer);
            case AGGREGATE -> aggregateRenderer.apply(aggregateLeaf);
        };
    }

    private String renderChild(int index,
                               FDialect dialect,
                               Function<String, String> referenceRenderer,
                               Function<AggregateLeaf, String> aggregateRenderer) {
        return children.get(index).render(dialect, referenceRenderer, aggregateRenderer);
    }

    private String renderArguments(FDialect dialect,
                                   Function<String, String> referenceRenderer,
                                   Function<AggregateLeaf, String> aggregateRenderer) {
        List<String> rendered = new ArrayList<>(children.size());
        for (TotalExpressionNode child : children) {
            rendered.add(child.render(dialect, referenceRenderer, aggregateRenderer));
        }
        return String.join(", ", rendered);
    }

    private String renderCustomFunction(FDialect dialect,
                                        Function<String, String> referenceRenderer,
                                        Function<AggregateLeaf, String> aggregateRenderer) {
        List<String> args = new ArrayList<>(children.size());
        for (TotalExpressionNode child : children) {
            args.add(child.render(dialect, referenceRenderer, aggregateRenderer));
        }
        String upper = token == null ? "" : token.toUpperCase();
        if (("IF".equals(upper) || "IIF".equals(upper)) && args.size() == 3) {
            return "CASE WHEN " + args.get(0) + " THEN " + args.get(1)
                    + " ELSE " + args.get(2) + " END";
        }
        if ("IS_NULL".equals(upper) && args.size() == 1) {
            return "(" + args.get(0) + " IS NULL)";
        }
        if ("IS_NOT_NULL".equals(upper) && args.size() == 1) {
            return "(" + args.get(0) + " IS NOT NULL)";
        }
        if ("BETWEEN".equals(upper) && args.size() == 3) {
            return "(" + args.get(0) + " BETWEEN " + args.get(1)
                    + " AND " + args.get(2) + ")";
        }
        String dialectSql = dialect == null ? null : dialect.buildFunctionCall(token, args);
        return dialectSql != null ? dialectSql : token + "(" + String.join(", ", args) + ")";
    }

    private String renderTemplate(FDialect dialect,
                                  Function<String, String> referenceRenderer,
                                  Function<AggregateLeaf, String> aggregateRenderer) {
        String result = token;
        for (int i = 0; i < children.size(); i++) {
            result = result.replace("{" + i + "}",
                    children.get(i).render(dialect, referenceRenderer, aggregateRenderer));
        }
        return result;
    }

    public static final class AggregateLeaf {
        private final String aggregation;
        private final BoundSqlExpression argument;
        private final boolean distinct;

        private AggregateLeaf(String aggregation,
                              BoundSqlExpression argument,
                              boolean distinct) {
            this.aggregation = Objects.requireNonNull(aggregation, "aggregation");
            this.argument = Objects.requireNonNull(argument, "argument");
            this.distinct = distinct;
        }

        public String aggregation() {
            return aggregation;
        }

        public BoundSqlExpression argument() {
            return argument;
        }

        public boolean distinct() {
            return distinct;
        }
    }
}
