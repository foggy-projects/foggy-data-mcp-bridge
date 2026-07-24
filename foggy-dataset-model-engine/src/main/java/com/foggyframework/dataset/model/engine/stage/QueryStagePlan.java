package com.foggyframework.dataset.model.engine.stage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QueryStagePlan {

    public static final String EXT_DATA_KEY = "queryStagePlan";
    public static final String VERSION = "v1";

    private final boolean enabled;
    private final String dialect;
    private final String renderStrategy;
    private final String finalCountStageId;
    private final String returnTotalStrategy;
    private final List<Stage> stages;
    private final List<String> fallbacks;
    private final List<String> unsupported;

    public QueryStagePlan(boolean enabled,
                          String dialect,
                          String renderStrategy,
                          String finalCountStageId,
                          String returnTotalStrategy,
                          List<Stage> stages,
                          List<String> fallbacks,
                          List<String> unsupported) {
        this.enabled = enabled;
        this.dialect = dialect;
        this.renderStrategy = renderStrategy;
        this.finalCountStageId = finalCountStageId;
        this.returnTotalStrategy = returnTotalStrategy;
        this.stages = List.copyOf(stages);
        this.fallbacks = List.copyOf(fallbacks);
        this.unsupported = List.copyOf(unsupported);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDialect() {
        return dialect;
    }

    public String getRenderStrategy() {
        return renderStrategy;
    }

    public String getFinalCountStageId() {
        return finalCountStageId;
    }

    public String getReturnTotalStrategy() {
        return returnTotalStrategy;
    }

    public List<Stage> getStages() {
        return stages;
    }

    public List<String> getFallbacks() {
        return fallbacks;
    }

    public List<String> getUnsupported() {
        return unsupported;
    }

    public boolean hasUnsupported() {
        return !unsupported.isEmpty();
    }

    public boolean hasUnsupported(String reason) {
        return unsupported.contains(reason);
    }

    public boolean hasStage(QueryStageType type) {
        for (Stage stage : stages) {
            if (stage.getType() == type) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPostAggregateStage() {
        return hasStage(QueryStageType.POST_AGGREGATE_STAGE);
    }

    public boolean hasWindowResultStage() {
        return hasStage(QueryStageType.WINDOW_RESULT_STAGE);
    }

    public boolean requiresPostAggregateRenderer() {
        return hasPostAggregateStage();
    }

    public boolean requiresWindowResultRenderer() {
        return !hasPostAggregateStage() && hasWindowResultStage();
    }

    public boolean usesCteRendering() {
        return "cte".equals(renderStrategy);
    }

    public boolean usesDerivedTableRendering() {
        return "derived".equals(renderStrategy);
    }

    public boolean requiresFinalStageAggSql() {
        for (Stage stage : stages) {
            if (stage.getType() != QueryStageType.FINAL_STAGE && stage.isRequiresSqlBoundary()) {
                return true;
            }
        }
        return false;
    }

    public boolean allowsPreAggFinalCountEquivalent() {
        if (!requiresFinalStageAggSql() || !"final-stage-count".equals(returnTotalStrategy)) {
            return false;
        }
        for (Stage stage : stages) {
            if ((stage.getType() == QueryStageType.AGGREGATE_STAGE
                    || stage.getType() == QueryStageType.POST_AGGREGATE_STAGE
                    || stage.getType() == QueryStageType.WINDOW_RESULT_STAGE)
                    && !stage.getFilterAliases().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public String countSqlInput() {
        if (!"final-stage-count".equals(returnTotalStrategy)) {
            return "disabled";
        }
        return "final-stage-sql-without-order";
    }

    public String aggSqlOptimizationPolicy() {
        if (requiresFinalStageAggSql()) {
            return "preserve-final-stage-sql";
        }
        return "optimizer-allowed";
    }

    public String preAggOptimizationPolicy() {
        if (requiresFinalStageAggSql()) {
            if (allowsPreAggFinalCountEquivalent()) {
                return "return-total-equivalent-only";
            }
            return "skip-final-stage-required";
        }
        return "optimizer-allowed";
    }

    public Map<String, Object> toDiagnosticsMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("version", VERSION);
        map.put("enabled", enabled);
        map.put("dialect", dialect);
        map.put("renderStrategy", renderStrategy);
        map.put("finalCountStageId", finalCountStageId);
        map.put("returnTotalStrategy", returnTotalStrategy);
        map.put("countSqlInput", countSqlInput());
        map.put("aggSqlOptimizationPolicy", aggSqlOptimizationPolicy());
        map.put("preAggOptimizationPolicy", preAggOptimizationPolicy());

        List<Map<String, Object>> stageMaps = new ArrayList<>(stages.size());
        for (Stage stage : stages) {
            stageMaps.add(stage.toDiagnosticsMap());
        }
        map.put("stages", stageMaps);
        map.put("fallbacks", fallbacks);
        map.put("unsupported", unsupported);
        return map;
    }

    public static class Stage {
        private final String id;
        private final QueryStageType type;
        private final String sqlAlias;
        private final List<String> inputAliases;
        private final List<String> outputAliases;
        private final List<String> filterAliases;
        private final List<String> orderAliases;
        private final boolean requiresSqlBoundary;
        private final int parameterCount;

        public Stage(String id,
                     QueryStageType type,
                     String sqlAlias,
                     List<String> inputAliases,
                     List<String> outputAliases,
                     List<String> filterAliases,
                     int parameterCount) {
            this(id, type, sqlAlias, inputAliases, outputAliases, filterAliases, List.of(), false, parameterCount);
        }

        public Stage(String id,
                     QueryStageType type,
                     String sqlAlias,
                     List<String> inputAliases,
                     List<String> outputAliases,
                     List<String> filterAliases,
                     List<String> orderAliases,
                     int parameterCount) {
            this(id, type, sqlAlias, inputAliases, outputAliases, filterAliases, orderAliases, false, parameterCount);
        }

        public Stage(String id,
                     QueryStageType type,
                     String sqlAlias,
                     List<String> inputAliases,
                     List<String> outputAliases,
                     List<String> filterAliases,
                     List<String> orderAliases,
                     boolean requiresSqlBoundary,
                     int parameterCount) {
            this.id = id;
            this.type = type;
            this.sqlAlias = sqlAlias;
            this.inputAliases = List.copyOf(inputAliases);
            this.outputAliases = List.copyOf(outputAliases);
            this.filterAliases = List.copyOf(filterAliases);
            this.orderAliases = List.copyOf(orderAliases);
            this.requiresSqlBoundary = requiresSqlBoundary;
            this.parameterCount = parameterCount;
        }

        public String getId() {
            return id;
        }

        public QueryStageType getType() {
            return type;
        }

        public String getSqlAlias() {
            return sqlAlias;
        }

        public List<String> getInputAliases() {
            return inputAliases;
        }

        public List<String> getOutputAliases() {
            return outputAliases;
        }

        public List<String> getFilterAliases() {
            return filterAliases;
        }

        public List<String> getOrderAliases() {
            return orderAliases;
        }

        public boolean isRequiresSqlBoundary() {
            return requiresSqlBoundary;
        }

        public int getParameterCount() {
            return parameterCount;
        }

        private Map<String, Object> toDiagnosticsMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("type", type.name());
            map.put("sqlAlias", sqlAlias);
            map.put("inputAliases", inputAliases);
            map.put("outputAliases", outputAliases);
            map.put("filterAliases", filterAliases);
            map.put("orderAliases", orderAliases);
            map.put("requiresSqlBoundary", requiresSqlBoundary);
            map.put("parameterCount", parameterCount);
            return map;
        }
    }
}
