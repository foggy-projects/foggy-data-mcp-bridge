package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslCteAssemblySupportTest {

    @Test
    @DisplayName("Cross-model base writer hoists structured CTE stages and preserves bind order")
    void crossModelBaseWriterHoistsStructuredCteStages() {
        SqlGenerationResult base = new SqlGenerationResult(
                "SELECT * FROM stage2 WHERE outer_col = ?",
                List.of("outer"),
                null,
                List.of(
                        new SqlGenerationResult.CteStage(
                                "stage1",
                                "SELECT id FROM source_table WHERE a = ?",
                                List.of("a")),
                        new SqlGenerationResult.CteStage(
                                "stage2",
                                "SELECT id FROM stage1 WHERE b = ?",
                                List.of("b"))));
        StringBuilder sql = new StringBuilder("WITH ");
        List<Object> params = new ArrayList<>();

        new DslCteAssemblySupport.CrossModelBaseCteWriter(sql, params)
                .appendBase("left_base", base, "DSL_CTE_JOIN_ALIGN_LEFT");

        String assembled = sql.toString();
        assertTrue(assembled.contains("left_base_stage1 AS"));
        assertTrue(assembled.contains("left_base_stage2 AS"));
        assertTrue(assembled.contains("FROM left_base_stage1 WHERE b = ?"));
        assertTrue(assembled.contains("left_base AS"));
        assertTrue(assembled.contains("FROM left_base_stage2 WHERE outer_col = ?"));
        assertEquals(List.of("a", "b", "outer"), params);
    }

    @Test
    @DisplayName("Cross-model base writer rejects raw SQL that already starts with WITH")
    void crossModelBaseWriterRejectsRawWithBaseSql() {
        SqlGenerationResult rawWith = new SqlGenerationResult(
                "WITH stage1 AS (SELECT 1) SELECT * FROM stage1",
                List.of(),
                null);
        StringBuilder sql = new StringBuilder("WITH ");
        List<Object> params = new ArrayList<>();

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> new DslCteAssemblySupport.CrossModelBaseCteWriter(sql, params)
                        .appendBase("left_base", rawWith, "DSL_CTE_JOIN_ALIGN_LEFT"));

        assertTrue(error.getMessage().contains("DSL_CTE_JOIN_ALIGN_LEFT_BASE_WITH_UNSUPPORTED"));
    }
}
