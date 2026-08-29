package com.foggyframework.dataset.model.engine.stage.result;

import com.foggyframework.dataset.db.dialect.DbType;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Lowers a structured prerequisite CTE to the existing pure-alias CTE contract. */
public final class RootCteLowerer {

    public SqlGenerationResult.CteStage lower(
            ResultStagePlan.StructuredCte source,
            FDialect dialect) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(dialect, "dialect");
        BoundSqlExpression body = source.body();
        if (!body.hasCompleteBindings()) {
            throw new IllegalArgumentException(
                    "CTE '" + source.alias() + "' placeholder/value count mismatch");
        }
        if (source.columnAliases().isEmpty()) {
            return new SqlGenerationResult.CteStage(source.alias(), body.sql(), body.values());
        }

        DbType dbType = dialect.getDbType();
        String lowered = switch (dbType) {
            case POSTGRESQL -> postgresBody(source, dialect);
            case SQLITE -> sqliteBody(source, dialect);
            case MYSQL -> mysqlBody(source, dialect);
            case SQLSERVER -> body.sql();
            default -> throw new IllegalArgumentException(
                    "ROOT_CTE_COLUMN_LOWERING_UNSUPPORTED: " + dbType);
        };
        return new SqlGenerationResult.CteStage(source.alias(), lowered, body.values());
    }

    private String postgresBody(ResultStagePlan.StructuredCte source, FDialect dialect) {
        return "SELECT *\nFROM (\n" + source.body().sql() + "\n) AS __foggy_values("
                + quotedColumns(source, dialect) + ")";
    }

    private String sqliteBody(ResultStagePlan.StructuredCte source, FDialect dialect) {
        List<String> projections = new ArrayList<>();
        for (int i = 0; i < source.columnAliases().size(); i++) {
            projections.add("column" + (i + 1) + " AS "
                    + dialect.quoteIdentifier(source.columnAliases().get(i)));
        }
        return "SELECT " + String.join(", ", projections)
                + "\nFROM (\n" + source.body().sql() + "\n)";
    }

    private String mysqlBody(ResultStagePlan.StructuredCte source, FDialect dialect) {
        if (!dialect.supportsCte()) {
            throw new IllegalArgumentException("ROOT_CTE_COLUMN_LOWERING_UNSUPPORTED: mysql without CTE");
        }
        List<String> projections = new ArrayList<>();
        for (String column : source.columnAliases()) {
            projections.add("__foggy_values." + dialect.quoteIdentifier(column));
        }
        return "SELECT " + String.join(", ", projections)
                + "\nFROM (\n" + source.body().sql() + "\n) AS __foggy_values("
                + quotedColumns(source, dialect) + ")";
    }

    private String quotedColumns(ResultStagePlan.StructuredCte source, FDialect dialect) {
        List<String> quoted = new ArrayList<>();
        for (String column : source.columnAliases()) {
            quoted.add(dialect.quoteIdentifier(column));
        }
        return String.join(", ", quoted);
    }
}
