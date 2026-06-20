package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DslCteAssemblySupport {

    private static final Pattern SAFE_CTE_ALIAS_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    private DslCteAssemblySupport() {
    }

    static void validateCrossModelBaseSql(SqlGenerationResult base, String errorPrefix) {
        if (base == null || base.getSql() == null || base.getSql().isBlank()) {
            throw RX.throwB(errorPrefix + "_BASE_SQL_MISSING");
        }
        String sql = base.getSql().trim();
        if (sql.regionMatches(true, 0, "WITH ", 0, 5)) {
            throw RX.throwB(errorPrefix + "_BASE_WITH_UNSUPPORTED");
        }
    }

    static String requireSafeCteAlias(String alias, String errorPrefix) {
        if (alias == null || !SAFE_CTE_ALIAS_PATTERN.matcher(alias).matches()) {
            throw RX.throwB(errorPrefix + "_BASE_CTE_ALIAS_UNSAFE: " + alias);
        }
        return alias;
    }

    static String rewriteCteAliases(String sql, Map<String, String> aliasMap) {
        if (sql == null || aliasMap.isEmpty()) {
            return sql;
        }
        String rewritten = sql;
        for (Map.Entry<String, String> entry : aliasMap.entrySet()) {
            String original = entry.getKey();
            String replacement = entry.getValue();
            rewritten = rewritten.replace("\"" + original + "\"", "\"" + replacement + "\"")
                    .replace("`" + original + "`", "`" + replacement + "`")
                    .replace("[" + original + "]", "[" + replacement + "]");
            Pattern bareAlias = Pattern.compile(
                    "(?i)(?<![A-Za-z0-9_$])" + Pattern.quote(original) + "(?![A-Za-z0-9_$])");
            rewritten = bareAlias.matcher(rewritten).replaceAll(Matcher.quoteReplacement(replacement));
        }
        return rewritten;
    }

    static final class CrossModelBaseCteWriter {
        private final StringBuilder sql;
        private final List<Object> params;
        private boolean hasPrevious;

        CrossModelBaseCteWriter(StringBuilder sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }

        void appendBase(String alias, SqlGenerationResult base, String errorPrefix) {
            requireSafeCteAlias(alias, errorPrefix);
            validateCrossModelBaseSql(base, errorPrefix);
            Map<String, String> aliasMap = new LinkedHashMap<>();
            if (base.hasCteStages()) {
                for (SqlGenerationResult.CteStage stage : base.getCteStages()) {
                    String stageAlias = requireSafeCteAlias(stage.alias(), errorPrefix);
                    if (aliasMap.containsKey(stageAlias)) {
                        throw RX.throwB(errorPrefix + "_BASE_CTE_ALIAS_DUPLICATE: " + stageAlias);
                    }
                    String renamedStageAlias = alias + "_" + stageAlias;
                    appendSeparator();
                    sql.append(renamedStageAlias)
                            .append(" AS (\n")
                            .append(rewriteCteAliases(stage.sql(), aliasMap))
                            .append("\n)");
                    if (stage.params() != null) {
                        params.addAll(stage.params());
                    }
                    aliasMap.put(stageAlias, renamedStageAlias);
                }
            }

            appendSeparator();
            sql.append(alias)
                    .append(" AS (\n")
                    .append(rewriteCteAliases(base.getSql().trim(), aliasMap))
                    .append("\n)");
            if (base.getParams() != null) {
                params.addAll(base.getParams());
            }
        }

        private void appendSeparator() {
            if (hasPrevious) {
                sql.append(",\n");
            } else {
                hasPrevious = true;
            }
        }
    }
}
