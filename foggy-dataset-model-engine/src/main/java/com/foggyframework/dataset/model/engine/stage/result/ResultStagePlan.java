package com.foggyframework.dataset.model.engine.stage.result;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import com.foggyframework.dataset.model.engine.stage.QueryStagePlan;
import com.foggyframework.dataset.model.engine.stage.QueryStageType;
import com.foggyframework.dataset.model.spi.DbColumnType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Engine-internal executable companion for {@link QueryStagePlan}.
 *
 * <p>The diagnostics plan remains the only topology source. This type binds
 * SQL expressions and values to that topology without deriving a second one.</p>
 */
public final class ResultStagePlan {

    private ResultStagePlan() {
    }

    public enum Mode {
        MAIN,
        TOTAL
    }

    public enum ColumnRole {
        PUBLIC_RESULT,
        HIDDEN_DEPENDENCY,
        INTERNAL_AGGREGATE_STATE,
        RESULT_STAGE_ONLY
    }

    public record Column(
            String alias,
            ColumnRole role,
            String producerStageId,
            String lastConsumerStageId,
            DbColumnType type,
            String sourceLineage,
            BoundSqlExpression expression) {

        public Column {
            requireText(alias, "column alias");
            Objects.requireNonNull(role, "column role");
            requireText(producerStageId, "producer stage id");
            requireText(lastConsumerStageId, "last consumer stage id");
            type = type == null ? DbColumnType.UNKNOWN : type;
            sourceLineage = sourceLineage == null ? alias : sourceLineage;
            requireComplete(expression, "column '" + alias + "'");
        }
    }

    public record Stage(
            String stageId,
            QueryStageType type,
            String renderAlias,
            List<Column> computedColumns,
            List<BoundSqlExpression> filters,
            List<BoundSqlExpression> orders) {

        public Stage {
            requireText(stageId, "stage id");
            Objects.requireNonNull(type, "stage type");
            requireText(renderAlias, "stage render alias");
            computedColumns = immutable(computedColumns);
            filters = immutable(filters);
            orders = immutable(orders);
            for (BoundSqlExpression filter : filters) {
                requireComplete(filter, "filter at stage '" + stageId + "'");
            }
            for (BoundSqlExpression order : orders) {
                requireComplete(order, "order at stage '" + stageId + "'");
                if (!order.values().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Parameterized result-stage order is not supported at stage '" + stageId + "'");
                }
            }
        }

        public static Stage metadata(String stageId, QueryStageType type, String renderAlias) {
            return new Stage(stageId, type, renderAlias, List.of(), List.of(), List.of());
        }

        public Stage withComputedColumns(List<Column> columns) {
            return new Stage(stageId, type, renderAlias, columns, filters, orders);
        }
    }

    public static final class Graph {
        private final QueryStagePlan diagnostics;
        private final List<Stage> stages;
        private final Map<String, Integer> indexByStageId;

        private Graph(QueryStagePlan diagnostics, List<Stage> stages) {
            this.diagnostics = diagnostics;
            this.stages = List.copyOf(stages);
            Map<String, Integer> indexes = new LinkedHashMap<>();
            for (int i = 0; i < stages.size(); i++) {
                indexes.put(stages.get(i).stageId(), i);
            }
            this.indexByStageId = Map.copyOf(indexes);
        }

        public static Graph create(QueryStagePlan diagnostics, List<Stage> stages) {
            Objects.requireNonNull(diagnostics, "diagnostics");
            stages = immutable(stages);
            if (diagnostics.hasUnsupported()) {
                throw new IllegalArgumentException(
                        "Result-stage graph refused by planner: " + diagnostics.getUnsupported());
            }
            List<QueryStagePlan.Stage> diagnosticStages = diagnostics.getStages();
            if (diagnosticStages.size() != stages.size()) {
                throw new IllegalArgumentException(
                        "Result-stage specs must have a one-to-one mapping with QueryStagePlan stages");
            }
            Set<String> ids = new HashSet<>();
            Map<String, Integer> indexes = new LinkedHashMap<>();
            for (int i = 0; i < stages.size(); i++) {
                Stage stage = stages.get(i);
                QueryStagePlan.Stage diagnostic = diagnosticStages.get(i);
                if (!diagnostic.getId().equals(stage.stageId())
                        || diagnostic.getType() != stage.type()) {
                    throw new IllegalArgumentException(
                            "Result-stage stage mismatch at index " + i
                                    + ": diagnostics=" + diagnostic.getId() + "/" + diagnostic.getType()
                                    + ", executable=" + stage.stageId() + "/" + stage.type());
                }
                if (!ids.add(stage.stageId())) {
                    throw new IllegalArgumentException("Duplicate result-stage id '" + stage.stageId() + "'");
                }
                indexes.put(stage.stageId(), i);
            }
            for (int i = 0; i < stages.size(); i++) {
                Stage stage = stages.get(i);
                Set<String> aliases = new HashSet<>();
                for (Column column : stage.computedColumns()) {
                    if (!stage.stageId().equals(column.producerStageId())) {
                        throw new IllegalArgumentException(
                                "Column '" + column.alias() + "' producer stage does not match '"
                                        + stage.stageId() + "'");
                    }
                    Integer lastConsumer = indexes.get(column.lastConsumerStageId());
                    if (lastConsumer == null || lastConsumer < i) {
                        throw new IllegalArgumentException(
                                "Column '" + column.alias() + "' has invalid last consumer stage '"
                                        + column.lastConsumerStageId() + "'");
                    }
                    if (!aliases.add(column.alias())) {
                        throw new IllegalArgumentException(
                                "Duplicate computed column alias '" + column.alias()
                                        + "' at stage '" + stage.stageId() + "'");
                    }
                }
            }
            return new Graph(diagnostics, stages);
        }

        public QueryStagePlan diagnostics() {
            return diagnostics;
        }

        public List<Stage> stages() {
            return stages;
        }

        public int indexOf(String stageId) {
            Integer index = indexByStageId.get(stageId);
            return index == null ? -1 : index;
        }

        public Stage stage(String stageId) {
            int index = indexOf(stageId);
            return index < 0 ? null : stages.get(index);
        }
    }

    public record StructuredCte(
            String alias,
            List<String> columnAliases,
            BoundSqlExpression body) {

        public StructuredCte {
            requireSafeIdentifier(alias, "CTE alias");
            columnAliases = immutable(columnAliases);
            for (String columnAlias : columnAliases) {
                requireText(columnAlias, "CTE column alias");
            }
            requireComplete(body, "CTE '" + alias + "'");
        }
    }

    public record RootSql(
            List<StructuredCte> prerequisiteCtes,
            String boundThroughStageId,
            BoundSqlExpression body,
            List<Column> columns) {

        public RootSql {
            prerequisiteCtes = immutable(prerequisiteCtes);
            requireText(boundThroughStageId, "bound-through stage id");
            requireComplete(body, "root SQL");
            columns = immutable(columns);
        }
    }

    public record FinalProjection(
            String alias,
            ColumnRole role,
            DbColumnType type,
            BoundSqlExpression expression) {

        public FinalProjection {
            requireText(alias, "final projection alias");
            Objects.requireNonNull(role, "final projection role");
            type = type == null ? DbColumnType.UNKNOWN : type;
            requireComplete(expression, "final projection '" + alias + "'");
        }
    }

    public record Executable(
            Graph graph,
            Mode mode,
            RootSql root,
            List<FinalProjection> finalProjection) {

        public Executable {
            Objects.requireNonNull(graph, "graph");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(root, "root");
            finalProjection = immutable(finalProjection);
        }

        public static Executable bind(
                Graph graph,
                Mode mode,
                RootSql root,
                List<FinalProjection> finalProjection) {
            Objects.requireNonNull(graph, "graph");
            Objects.requireNonNull(root, "root");
            if (graph.indexOf(root.boundThroughStageId()) < 0) {
                throw new IllegalArgumentException(
                        "Root SQL is bound through unknown stage '" + root.boundThroughStageId() + "'");
            }
            requireComplete(root.body(), "root SQL");
            for (StructuredCte cte : root.prerequisiteCtes()) {
                requireComplete(cte.body(), "CTE '" + cte.alias() + "'");
            }
            Set<String> aliases = new HashSet<>();
            for (FinalProjection projection : immutable(finalProjection)) {
                requireComplete(projection.expression(), "final projection '" + projection.alias() + "'");
                if (!aliases.add(projection.alias())) {
                    throw new IllegalArgumentException(
                            "Duplicate final projection alias '" + projection.alias() + "'");
                }
            }
            return new Executable(graph, mode, root, finalProjection);
        }
    }

    public record RenderResult(
            String outerSql,
            String outerSqlWithoutOrder,
            List<Object> outerValues,
            List<SqlGenerationResult.CteStage> cteStages) {

        public RenderResult {
            requireText(outerSql, "outer SQL");
            requireText(outerSqlWithoutOrder, "outer SQL without order");
            outerValues = immutable(outerValues);
            cteStages = immutable(cteStages);
        }

        public String assembledSql() {
            if (cteStages.isEmpty()) {
                return outerSql;
            }
            StringBuilder sql = new StringBuilder("WITH ");
            for (int i = 0; i < cteStages.size(); i++) {
                if (i > 0) {
                    sql.append(",\n");
                }
                SqlGenerationResult.CteStage cte = cteStages.get(i);
                sql.append(cte.alias()).append(" AS (\n")
                        .append(cte.sql()).append("\n)");
            }
            return sql.append("\n").append(outerSql).toString();
        }

        public String assembledSqlWithoutOrder() {
            if (cteStages.isEmpty()) {
                return outerSqlWithoutOrder;
            }
            StringBuilder sql = new StringBuilder("WITH ");
            for (int i = 0; i < cteStages.size(); i++) {
                if (i > 0) {
                    sql.append(",\n");
                }
                SqlGenerationResult.CteStage cte = cteStages.get(i);
                sql.append(cte.alias()).append(" AS (\n")
                        .append(cte.sql()).append("\n)");
            }
            return sql.append("\n").append(outerSqlWithoutOrder).toString();
        }

        public List<Object> assembledValues() {
            if (cteStages.isEmpty()) {
                return outerValues;
            }
            List<Object> values = new ArrayList<>();
            for (SqlGenerationResult.CteStage cte : cteStages) {
                if (cte.params() != null) {
                    values.addAll(cte.params());
                }
            }
            values.addAll(outerValues);
            return List.copyOf(values);
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static void requireComplete(BoundSqlExpression expression, String owner) {
        Objects.requireNonNull(expression, owner + " expression");
        if (!expression.hasCompleteBindings()) {
            throw new IllegalArgumentException(owner + " placeholder/value count mismatch");
        }
    }

    private static void requireText(String value, String name) {
        if (StringUtils.isEmpty(value)) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }

    private static void requireSafeIdentifier(String value, String name) {
        requireText(value, name);
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(name + " must be a plain identifier: " + value);
        }
    }
}
