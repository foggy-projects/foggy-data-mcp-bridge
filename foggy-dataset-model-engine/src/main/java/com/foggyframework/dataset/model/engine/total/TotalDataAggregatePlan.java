package com.foggyframework.dataset.model.engine.total;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import com.foggyframework.dataset.model.engine.expression.TotalExpressionNode;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbColumnType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Request-scoped plan for merging grouped aggregate states into totalData.
 */
public final class TotalDataAggregatePlan {

    public enum LoweringStatus {
        NOT_APPLICABLE,
        LOWERED,
        REFUSED
    }

    public record AggregateLeafId(String ownerAlias, int ordinal) {
    }

    public record AggregateStateSpec(AggregateLeafId leafId,
                                     DbAggregation aggregation,
                                     BoundSqlExpression source,
                                     DbColumnType type,
                                     String valueAlias,
                                     String sumAlias,
                                     String countAlias) {
    }

    private final LoweringStatus status;
    private final String refusalReason;
    private final List<String> publicAliases;
    private final Map<String, TotalExpressionNode> expressions;
    private final List<AggregateStateSpec> states;
    private final IdentityHashMap<TotalExpressionNode.AggregateLeaf, AggregateStateSpec> leafStates;

    private TotalDataAggregatePlan(LoweringStatus status,
                                   String refusalReason,
                                   List<String> publicAliases,
                                   Map<String, TotalExpressionNode> expressions,
                                   List<AggregateStateSpec> states,
                                   IdentityHashMap<TotalExpressionNode.AggregateLeaf, AggregateStateSpec> leafStates) {
        this.status = status;
        this.refusalReason = refusalReason;
        this.publicAliases = publicAliases == null ? List.of() : List.copyOf(publicAliases);
        this.expressions = expressions == null
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(expressions));
        this.states = states == null ? List.of() : List.copyOf(states);
        this.leafStates = leafStates == null ? new IdentityHashMap<>() : new IdentityHashMap<>(leafStates);
    }

    public static TotalDataAggregatePlan notApplicable() {
        return new TotalDataAggregatePlan(
                LoweringStatus.NOT_APPLICABLE, null, List.of(), Map.of(), List.of(), null);
    }

    public static TotalDataAggregatePlan refused(String reason) {
        return new TotalDataAggregatePlan(
                LoweringStatus.REFUSED, reason, List.of(), Map.of(), List.of(), null);
    }

    public static TotalDataAggregatePlan lowered(List<String> publicAliases,
                                                 Map<String, TotalExpressionNode> expressions,
                                                 List<AggregateStateSpec> states,
                                                 IdentityHashMap<TotalExpressionNode.AggregateLeaf,
                                                         AggregateStateSpec> leafStates) {
        return new TotalDataAggregatePlan(
                LoweringStatus.LOWERED, null, publicAliases, expressions, states, leafStates);
    }

    public LoweringStatus getStatus() {
        return status;
    }

    public String getRefusalReason() {
        return refusalReason;
    }

    public List<String> getPublicAliases() {
        return publicAliases;
    }

    public List<AggregateStateSpec> getStates() {
        return states;
    }

    public boolean requiresIndependentAggregateStates() {
        return status == LoweringStatus.LOWERED && !states.isEmpty();
    }

    public String renderPublicExpression(String alias, FDialect dialect, String sourceAlias) {
        if (!expressions.containsKey(alias)) {
            return null;
        }
        return renderAlias(alias, dialect, sourceAlias, new LinkedHashSet<>());
    }

    private String renderAlias(String alias,
                               FDialect dialect,
                               String sourceAlias,
                               Set<String> rendering) {
        TotalExpressionNode node = expressions.get(alias);
        if (node == null) {
            return null;
        }
        if (!rendering.add(alias)) {
            throw new IllegalStateException("Calculated totalData dependency cycle: " + rendering);
        }
        try {
            return node.render(
                    dialect,
                    reference -> renderAlias(reference, dialect, sourceAlias, rendering),
                    leaf -> renderLeaf(leaf, dialect, sourceAlias));
        } finally {
            rendering.remove(alias);
        }
    }

    private String renderLeaf(TotalExpressionNode.AggregateLeaf leaf,
                              FDialect dialect,
                              String sourceAlias) {
        AggregateStateSpec state = leafStates.get(leaf);
        if (state == null) {
            throw new IllegalStateException("Aggregate leaf is not bound to a totalData state");
        }
        if (state.aggregation() == DbAggregation.AVG) {
            String numerator = "SUM(" + qualified(sourceAlias, state.sumAlias(), dialect) + ")";
            String denominator = "SUM(" + qualified(sourceAlias, state.countAlias(), dialect) + ")";
            return TotalDataSqlDialect.safeRatio(dialect, numerator, denominator);
        }
        String value = qualified(sourceAlias, state.valueAlias(), dialect);
        return switch (state.aggregation()) {
            case SUM, COUNT -> "SUM(" + value + ")";
            case MIN -> "MIN(" + value + ")";
            case MAX, PK -> "MAX(" + value + ")";
            default -> throw new IllegalStateException(
                    "Unsupported totalData state merge: " + state.aggregation());
        };
    }

    private String qualified(String sourceAlias, String alias, FDialect dialect) {
        String quoted = dialect.quoteIdentifier(alias);
        return StringUtils.isEmpty(sourceAlias) ? quoted : sourceAlias + "." + quoted;
    }

    /** Builder keeps stable leaf identity and preorder assignment in one place. */
    public static final class Builder {
        private final List<String> publicAliases = new ArrayList<>();
        private final Map<String, TotalExpressionNode> expressions = new LinkedHashMap<>();
        private final List<AggregateStateSpec> states = new ArrayList<>();
        private final IdentityHashMap<TotalExpressionNode.AggregateLeaf, AggregateStateSpec> leafStates =
                new IdentityHashMap<>();
        private String refusalReason;
        private int stateIndex;

        public void addPublicExpression(String alias, TotalExpressionNode expression) {
            publicAliases.add(alias);
            if (expression != null) {
                expressions.put(alias, expression);
            }
        }

        public void addDependencyExpression(String alias, TotalExpressionNode expression) {
            if (expression != null) {
                expressions.putIfAbsent(alias, expression);
            }
        }

        public boolean hasExpression(String alias) {
            return expressions.containsKey(alias);
        }

        public TotalExpressionNode getExpression(String alias) {
            return expressions.get(alias);
        }

        public void refuse(String reason) {
            if (refusalReason == null) {
                refusalReason = reason;
            }
        }

        public boolean isRefused() {
            return refusalReason != null;
        }

        public void bindLeaves(String ownerAlias, TotalExpressionNode expression, DbColumnType type) {
            if (expression == null || isRefused()) {
                return;
            }
            int[] ordinal = {0};
            expression.visitAggregateLeaves(leaf -> bindLeaf(ownerAlias, ordinal[0]++, leaf, type));
        }

        private void bindLeaf(String ownerAlias,
                              int ordinal,
                              TotalExpressionNode.AggregateLeaf leaf,
                              DbColumnType type) {
            if (leafStates.containsKey(leaf) || isRefused()) {
                return;
            }
            DbAggregation aggregation = parseAggregation(leaf.aggregation());
            if (aggregation == null || !isMergeable(aggregation)) {
                refuse("aggregate '" + leaf.aggregation() + "' in '" + ownerAlias
                        + "' has no mergeable totalData state");
                return;
            }
            if (leaf.distinct()
                    && (aggregation == DbAggregation.AVG
                    || aggregation == DbAggregation.SUM
                    || aggregation == DbAggregation.COUNT)) {
                refuse("distinct aggregate '" + leaf.aggregation() + "' in '" + ownerAlias
                        + "' has no cross-group set state");
                return;
            }
            if (!leaf.argument().hasCompleteBindings()) {
                refuse("aggregate '" + leaf.aggregation() + "' in '" + ownerAlias
                        + "' has incomplete JDBC parameter bindings");
                return;
            }
            int index = stateIndex++;
            AggregateLeafId leafId = new AggregateLeafId(ownerAlias, ordinal);
            AggregateStateSpec state;
            if (aggregation == DbAggregation.AVG) {
                state = new AggregateStateSpec(
                        leafId, aggregation, leaf.argument(), type, null,
                        "__foggy_avg_sum_" + index,
                        "__foggy_avg_count_" + index);
            } else {
                state = new AggregateStateSpec(
                        leafId, aggregation, leaf.argument(), type,
                        "__foggy_agg_state_" + index, null, null);
            }
            states.add(state);
            leafStates.put(leaf, state);
        }

        public TotalDataAggregatePlan build() {
            if (refusalReason != null) {
                return TotalDataAggregatePlan.refused(refusalReason);
            }
            return TotalDataAggregatePlan.lowered(
                    publicAliases, expressions, states, leafStates);
        }

        private DbAggregation parseAggregation(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return DbAggregation.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        private boolean isMergeable(DbAggregation aggregation) {
            return aggregation == DbAggregation.SUM
                    || aggregation == DbAggregation.COUNT
                    || aggregation == DbAggregation.MIN
                    || aggregation == DbAggregation.MAX
                    || aggregation == DbAggregation.PK
                    || aggregation == DbAggregation.AVG;
        }
    }
}
