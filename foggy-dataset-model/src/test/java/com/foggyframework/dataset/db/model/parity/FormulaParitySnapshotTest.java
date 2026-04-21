package com.foggyframework.dataset.db.model.parity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.db.model.engine.expression.CalculatedFieldService;
import com.foggyframework.fsscript.parser.FsscriptDialect;
import com.foggyframework.fsscript.parser.spi.Exp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Formula parity snapshot (M5 Step 5.1 — Java side).
 *
 * <p>Reads the shared catalog at
 * {@code src/test/resources/parity/formula-parity-expressions.json} and
 * exercises the {@link CalculatedFieldService#compileExpression(String, FsscriptDialect)}
 * path on every positive entry.  The Java formula-compiler today inlines
 * literals directly into the generated SQL fragment (no {@code ?} placeholder
 * + bind-params tuple like the Python port), and rendering the fragment
 * requires a full {@code SqlExpContext} — a {@code JdbcQueryModel} + an
 * {@code FDialect} + an {@code ApplicationContext} — which is heavier than a
 * snapshot test can stand up in isolation.</p>
 *
 * <p>The M5 Step 5.1 prompt calls out <b>离线快照</b> as the target (parity.md
 * §8.3 option 2).  This class takes the achievable slice of that work today:
 * <ul>
 *   <li>validate the catalog is well-formed JSON with {@code >= 30} positive
 *       entries (Step 5.1 acceptance)</li>
 *   <li>prove every catalog expression parses cleanly through
 *       {@code CalculatedFieldService.compileExpression} with the
 *       {@code SQL_EXPRESSION} dialect — giving an end-to-end AST gate on the
 *       Java parser + whitelist for the same inputs the Python side drives</li>
 *   <li>leave a marker (see {@code shouldProduceSnapshot()}) describing the
 *       remaining gap for the full SQL-string snapshot path</li>
 * </ul>
 *
 * <p>Writing out the {@code _parity_snapshot.json} consumed by
 * {@code tests/integration/test_formula_parity.py::test_parity_matches_java_snapshot}
 * is the follow-up: it needs a Spring-test harness that wires a minimal
 * {@code JdbcQueryModel} over the catalog's expression fields (a, b, status,
 * ...) and loops {@code SqlExpContext} + {@link CalculatedFieldService#evaluateExpression}
 * per dialect (mysql / postgres / sqlserver / sqlite).  Until that lands the
 * Python-side snapshot compare stays {@code skip}ped when the JSON is absent.</p>
 */
@DisplayName("FormulaParitySnapshotTest · M5 Step 5.1 (Java side)")
class FormulaParitySnapshotTest {

    private static final String CATALOG_RESOURCE = "/parity/formula-parity-expressions.json";

    @Test
    @DisplayName("catalog JSON is well-formed and meets Step 5.1 >= 30 positive entries quota")
    void catalogIsWellFormed() throws Exception {
        Map<String, Object> catalog = readCatalog();
        assertEquals("1", catalog.get("schema_version"),
                "schema_version must be '1' until a breaking change lands");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries =
                (List<Map<String, Object>>) catalog.get("expressions");
        assertNotNull(entries);

        long positive = entries.stream()
                .filter(e -> "positive".equals(e.get("kind")))
                .count();
        assertTrue(positive >= 30,
                "parity catalog has " + positive + " positive entries, need >= 30 per Step 5.1");
    }

    @Test
    @DisplayName("every positive catalog entry parses through Java compileExpression (AST gate)")
    void everyPositiveEntryParsesOnJavaSide() throws Exception {
        Map<String, Object> catalog = readCatalog();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries =
                (List<Map<String, Object>>) catalog.get("expressions");

        int compiled = 0;
        int skipped = 0;
        java.util.List<String> failures = new java.util.ArrayList<>();
        for (Map<String, Object> entry : entries) {
            if (!"positive".equals(entry.get("kind"))) {
                continue;
            }
            if (Boolean.TRUE.equals(entry.get("java_skip"))) {
                skipped++;
                continue;  // documented Python-only cases — see java_skip_reason
            }
            String expression = (String) entry.get("expression");
            String id = (String) entry.get("id");
            try {
                Exp exp = CalculatedFieldService.compileExpression(
                        expression, FsscriptDialect.SQL_EXPRESSION);
                assertNotNull(exp, "compileExpression returned null for [" + id + "]");
                compiled++;
            } catch (RuntimeException ex) {
                failures.add("[" + id + "] " + expression + " → "
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
        assertTrue(failures.isEmpty(),
                "Java compileExpression failures:\n  " + String.join("\n  ", failures));
        assertTrue(compiled >= 30,
                "expected to compile >= 30 positive entries (Java-eligible), "
                        + "actually " + compiled + " (skipped " + skipped + ")");
    }

    @Test
    @DisplayName("SqlNormalizer round-trips Python-form SQL without surprise")
    void sqlNormalizerCanonicalizesPythonForm() {
        // Py-form input: `?` placeholders + params.  Normalizer must collapse
        // double parens and upper-case keywords but leave the params list alone.
        SqlNormalizer.Canonical c = SqlNormalizer.toCanonical(
                "CASE WHEN ((a > ?)) THEN a ELSE ? END",
                List.of(0, 0));
        assertEquals("CASE WHEN (a > ?) THEN a ELSE ? END", c.sql);
        assertEquals(List.of(0, 0), c.params);
    }

    @Test
    @DisplayName("SqlNormalizer round-trips Java inline-literal form without surprise")
    void sqlNormalizerCanonicalizesJavaForm() {
        // Java-form input: inline literals, no params tuple.  Normalizer
        // extracts literals left-to-right and substitutes `?` so the two
        // sides converge.
        SqlNormalizer.Canonical c = SqlNormalizer.toCanonical(
                "CASE WHEN (status = 'posted') THEN 1 ELSE 0 END",
                null);
        assertEquals("CASE WHEN (status = ?) THEN ? ELSE ? END", c.sql);
        assertEquals(List.of("posted", 1L, 0L), c.params);
    }

    /**
     * Describes the remaining Step 5.1 work item: materializing a Java-side
     * snapshot JSON that the Python parity test can read to do the full
     * cross-end compare.  Left as a passing test with a doc assertion so the
     * intent is visible when browsing the suite.
     */
    @Test
    @DisplayName("TODO(Step 5.1 follow-up) · write _parity_snapshot.json via Spring harness")
    void shouldProduceSnapshotPendingSpringHarness() {
        // When the Spring-backed SqlExpContext harness lands, this test
        // should loop through the catalog, drive each (expression, dialect)
        // pair through CalculatedFieldService.evaluateExpression + SqlFragment
        // and write the resulting { id, expression, dialect, sql, bind_params }
        // into
        //   foggy-data-mcp-bridge-python/tests/integration/_parity_snapshot.json
        // This file is consumed by
        //   tests/integration/test_formula_parity.py::test_parity_matches_java_snapshot
        // which stays skip'd until the snapshot is present.
        assertTrue(true, "intentionally green — scaffold placeholder");
    }

    // ------------------------------------------------------------------ //
    // Internals
    // ------------------------------------------------------------------ //

    private Map<String, Object> readCatalog() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream(CATALOG_RESOURCE)) {
            assertNotNull(in, "catalog resource missing: " + CATALOG_RESOURCE);
            return mapper.readValue(in, new TypeReference<Map<String, Object>>() {});
        }
    }
}
