package com.foggyframework.dataset.model.engine.stage.result;

import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import com.foggyframework.dataset.model.spi.DbColumn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Request-scoped prepare result shared by MAIN and TOTAL.
 *
 * <p>This is an engine-internal contract. It keeps the normalized result-stage
 * graph and the physical/semantic base projection bindings in one immutable
 * object before either SQL visitor runs.</p>
 */
public final class ResultStagePreparation {

    private final ResultStagePlan.Graph graph;
    private final BaseProjectionPlan baseProjectionPlan;

    public ResultStagePreparation(
            ResultStagePlan.Graph graph,
            BaseProjectionPlan baseProjectionPlan) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.baseProjectionPlan = Objects.requireNonNull(
                baseProjectionPlan, "baseProjectionPlan");
        validateProjection(baseProjectionPlan.main());
        validateProjection(baseProjectionPlan.total());
    }

    public ResultStagePlan.Graph graph() {
        return graph;
    }

    public BaseProjectionPlan baseProjectionPlan() {
        return baseProjectionPlan;
    }

    public List<DbColumn> sourceColumns(ResultStagePlan.Mode mode) {
        return projection(mode).projections().stream()
                .map(Projection::source)
                .toList();
    }

    public List<Object> expressionValues(ResultStagePlan.Mode mode) {
        return projection(mode).expressionValues();
    }

    public ResultStagePlan.Executable bind(
            ResultStagePlan.Mode mode,
            List<ResultStagePlan.StructuredCte> prerequisiteCtes,
            String boundThroughStageId,
            BoundSqlExpression body,
            List<ResultStagePlan.FinalProjection> finalProjection) {
        BaseProjection selected = projection(mode);
        ResultStagePlan.RootSql root = new ResultStagePlan.RootSql(
                prerequisiteCtes,
                boundThroughStageId,
                body,
                selected.columns());
        return ResultStagePlan.Executable.bind(graph, mode, root, finalProjection);
    }

    private BaseProjection projection(ResultStagePlan.Mode mode) {
        Objects.requireNonNull(mode, "mode");
        return mode == ResultStagePlan.Mode.MAIN
                ? baseProjectionPlan.main() : baseProjectionPlan.total();
    }

    private void validateProjection(BaseProjection projection) {
        Set<String> aliases = new HashSet<>();
        for (Projection binding : projection.projections()) {
            ResultStagePlan.Column column = binding.column();
            if (!aliases.add(column.alias())) {
                throw new IllegalArgumentException(
                        "Duplicate base projection alias '" + column.alias() + "'");
            }
            int producer = graph.indexOf(column.producerStageId());
            int lastConsumer = graph.indexOf(column.lastConsumerStageId());
            if (producer < 0) {
                throw new IllegalArgumentException(
                        "Base projection '" + column.alias()
                                + "' has unknown producer stage '"
                                + column.producerStageId() + "'");
            }
            if (lastConsumer < producer) {
                throw new IllegalArgumentException(
                        "Base projection '" + column.alias()
                                + "' has invalid last consumer stage '"
                                + column.lastConsumerStageId() + "'");
            }
        }
    }

    public record Projection(
            DbColumn source,
            ResultStagePlan.Column column) {

        public Projection {
            Objects.requireNonNull(source, "projection source");
            Objects.requireNonNull(column, "projection column");
        }
    }

    public static final class BaseProjection {
        private final List<Projection> projections;
        private final List<Object> expressionValues;
        private final List<ResultStagePlan.Column> columns;

        public BaseProjection(
                List<Projection> projections,
                List<Object> expressionValues) {
            this.projections = projections == null
                    ? List.of() : List.copyOf(projections);
            this.expressionValues = expressionValues == null
                    ? List.of() : List.copyOf(expressionValues);
            List<ResultStagePlan.Column> metadata = new ArrayList<>();
            for (Projection projection : this.projections) {
                metadata.add(projection.column());
            }
            this.columns = List.copyOf(metadata);
        }

        public List<Projection> projections() {
            return projections;
        }

        public List<Object> expressionValues() {
            return expressionValues;
        }

        public List<ResultStagePlan.Column> columns() {
            return columns;
        }
    }

    public record BaseProjectionPlan(
            BaseProjection main,
            BaseProjection total) {

        public BaseProjectionPlan {
            Objects.requireNonNull(main, "main base projection");
            Objects.requireNonNull(total, "total base projection");
        }
    }
}
