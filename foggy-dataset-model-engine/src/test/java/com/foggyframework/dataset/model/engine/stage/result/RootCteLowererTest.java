package com.foggyframework.dataset.model.engine.stage.result;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("方案 B：domain root CTE 方言 lowering 契约")
class RootCteLowererTest {

    private final RootCteLowerer lowerer = new RootCteLowerer();

    @Test
    void postgresMovesColumnAliasesIntoValuesBody() {
        SqlGenerationResult.CteStage stage = lowerer.lower(
                valuesCte("VALUES (?, ?), (?, ?)"), FDialect.POSTGRES_DIALECT);

        assertEquals("domain_waybill", stage.alias());
        assertTrue(stage.sql().contains("AS __foggy_values(\"openingYear\", \"kind\")"), stage.sql());
        assertFalse(stage.alias().contains("("));
        assertEquals(List.of(2025, "A", 2026, "B"), stage.params());
    }

    @Test
    void sqliteAliasesGeneratedColumnNamesInsideBody() {
        SqlGenerationResult.CteStage stage = lowerer.lower(
                valuesCte("VALUES (?, ?), (?, ?)"), FDialect.SQLITE_DIALECT);

        assertEquals("domain_waybill", stage.alias());
        assertTrue(stage.sql().contains("column1 AS \"openingYear\""), stage.sql());
        assertTrue(stage.sql().contains("column2 AS \"kind\""), stage.sql());
        assertFalse(stage.sql().toUpperCase().contains("WITH "));
    }

    @Test
    void mysql8MovesColumnAliasesIntoValuesRowDerivedTable() {
        SqlGenerationResult.CteStage stage = lowerer.lower(
                valuesCte("VALUES ROW(?, ?), ROW(?, ?)"), FDialect.MYSQL8_DIALECT);

        assertEquals("domain_waybill", stage.alias());
        assertTrue(stage.sql().contains("AS __foggy_values(`openingYear`, `kind`)"), stage.sql());
        assertFalse(stage.alias().contains("("));
    }

    @Test
    void sqlServerKeepsProducerAliasedSelectBodyAndPureAlias() {
        ResultStagePlan.StructuredCte source = new ResultStagePlan.StructuredCte(
                "domain_waybill",
                List.of("openingYear", "kind"),
                new BoundSqlExpression(
                        "SELECT CAST(? AS NVARCHAR(4000)) AS [openingYear], "
                                + "CAST(? AS NVARCHAR(4000)) AS [kind] UNION ALL "
                                + "SELECT CAST(? AS NVARCHAR(4000)), CAST(? AS NVARCHAR(4000))",
                        List.of(2025, "A", 2026, "B")));

        SqlGenerationResult.CteStage stage = lowerer.lower(source, FDialect.SQLSERVER_DIALECT);

        assertEquals("domain_waybill", stage.alias());
        assertTrue(stage.sql().startsWith("SELECT CAST"));
        assertFalse(stage.alias().contains("("));
    }

    @Test
    void invalidBodyBindingsFailClosedBeforeRendering() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new ResultStagePlan.StructuredCte(
                        "domain_waybill", List.of("openingYear"),
                        new BoundSqlExpression("VALUES (?)", List.of())));
        assertTrue(ex.getMessage().contains("placeholder"));
    }

    private static ResultStagePlan.StructuredCte valuesCte(String body) {
        return new ResultStagePlan.StructuredCte(
                "domain_waybill",
                List.of("openingYear", "kind"),
                new BoundSqlExpression(body, List.of(2025, "A", 2026, "B")));
    }
}
