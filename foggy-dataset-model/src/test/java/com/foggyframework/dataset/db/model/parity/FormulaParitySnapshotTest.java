package com.foggyframework.dataset.db.model.parity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.engine.expression.CalculatedFieldService;
import com.foggyframework.dataset.db.model.engine.expression.SqlExpContext;
import com.foggyframework.dataset.db.model.engine.expression.SqlFragment;
import com.foggyframework.dataset.db.model.spi.DbColumnType;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.fsscript.parser.FsscriptDialect;
import com.foggyframework.fsscript.parser.spi.Exp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
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
 * §8.3 option 2). This class implements that offline lane:
 * <ul>
 *   <li>validate the catalog is well-formed JSON with {@code >= 30} positive
 *       entries (Step 5.1 acceptance)</li>
 *   <li>prove every catalog expression parses cleanly through
 *       {@code CalculatedFieldService.compileExpression} with the
 *       {@code SQL_EXPRESSION} dialect — giving an end-to-end AST gate on the
 *       Java parser + whitelist for the same inputs the Python side drives</li>
 *   <li>write {@code _parity_snapshot.json} with Java-rendered SQL fragments
 *       for the Python strict snapshot comparison</li>
 * </ul>
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
     * Materializes the Java-side snapshot JSON consumed by the Python strict
     * cross-engine parity test.
     */
    @Test
    @DisplayName("writes _parity_snapshot.json for Python strict parity compare")
    void shouldProduceSnapshot() throws Exception {
        Map<String, Object> catalog = readCatalog();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries =
                (List<Map<String, Object>>) catalog.get("expressions");

        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            if (!"positive".equals(entry.get("kind")) || Boolean.TRUE.equals(entry.get("java_skip"))) {
                continue;
            }
            String expression = (String) entry.get("expression");
            String id = (String) entry.get("id");
            String dialectName = (String) entry.get("dialect");

            Exp exp = CalculatedFieldService.compileExpression(expression, FsscriptDialect.SQL_EXPRESSION);
            SqlFragment fragment = evaluate(exp, dialectFor(dialectName));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("expression", expression);
            row.put("dialect", dialectName);
            row.put("sql_normalized", fragment.getSql());
            snapshots.add(row);
        }

        assertTrue(snapshots.size() >= 30,
                "expected to write >= 30 Java snapshots, got " + snapshots.size());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schema_version", "1");
        snapshot.put("source", "FormulaParitySnapshotTest");
        snapshot.put("snapshots", snapshots);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path targetPath = Path.of(
                "..",
                "..",
                "foggy-data-mcp-bridge-python",
                "tests",
                "integration",
                "_parity_snapshot.json"
        ).normalize();
        Files.createDirectories(targetPath.getParent());
        mapper.writeValue(targetPath.toFile(), snapshot);

        Path localCopy = Path.of("target", "parity", "_parity_snapshot.json");
        Files.createDirectories(localCopy.getParent());
        mapper.writeValue(localCopy.toFile(), snapshot);
        assertTrue(Files.exists(targetPath), "snapshot was not written: " + targetPath.toAbsolutePath());
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

    private SqlFragment evaluate(Exp exp, FDialect dialect) throws Exception {
        SqlExpContext context = new SqlExpContext(mockQueryModel(dialect), dialect, null);
        Method method = CalculatedFieldService.class.getDeclaredMethod(
                "evaluateExpression",
                Exp.class,
                SqlExpContext.class,
                org.springframework.context.ApplicationContext.class
        );
        method.setAccessible(true);
        return (SqlFragment) method.invoke(null, exp, context, null);
    }

    private JdbcQueryModel mockQueryModel(FDialect dialect) {
        return (JdbcQueryModel) Proxy.newProxyInstance(
                JdbcQueryModel.class.getClassLoader(),
                new Class<?>[]{JdbcQueryModel.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getDialect".equals(methodName)) {
                        return dialect;
                    }
                    if ("findJdbcColumnForSelectByName".equals(methodName)) {
                        return mockColumn((String) args[0]);
                    }
                    if ("getAlias".equals(methodName)) {
                        return "";
                    }
                    if ("getName".equals(methodName)) {
                        return "FormulaParitySnapshotQueryModel";
                    }
                    if ("getCaption".equals(methodName)) {
                        return "Formula parity snapshot query model";
                    }
                    if ("getShortAlias".equals(methodName)) {
                        return "FPS";
                    }
                    if ("toString".equals(methodName)) {
                        return "FormulaParitySnapshotQueryModel";
                    }
                    if ("hashCode".equals(methodName)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(methodName)) {
                        return proxy == args[0];
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private DbQueryColumn mockColumn(String name) {
        return (DbQueryColumn) Proxy.newProxyInstance(
                DbQueryColumn.class.getClassLoader(),
                new Class<?>[]{DbQueryColumn.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("getDeclare".equals(methodName) || "getDeclareOrder".equals(methodName)) {
                        return name;
                    }
                    if ("getName".equals(methodName) || "getAlias".equals(methodName)
                            || "getField".equals(methodName) || "getSqlColumnName".equals(methodName)) {
                        return name;
                    }
                    if ("getCaption".equals(methodName)) {
                        return name;
                    }
                    if ("getDescription".equals(methodName)) {
                        return null;
                    }
                    if ("getType".equals(methodName)) {
                        return typeFor(name);
                    }
                    if ("getSelectColumn".equals(methodName)) {
                        return proxy;
                    }
                    if ("isHasRef".equals(methodName) || "_isDeprecated".equals(methodName)
                            || "isMeasure".equals(methodName) || "isDimension".equals(methodName)
                            || "isProperty".equals(methodName) || "isCalculatedField".equals(methodName)
                            || "isCountColumn".equals(methodName)) {
                        return false;
                    }
                    if ("toString".equals(methodName)) {
                        return name;
                    }
                    if ("hashCode".equals(methodName)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(methodName)) {
                        return proxy == args[0];
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private DbColumnType typeFor(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("date") || lower.endsWith("at") || "today".equals(lower)) {
            return DbColumnType.DATETIME;
        }
        if (lower.contains("state") || lower.contains("type") || lower.contains("status")) {
            return DbColumnType.TEXT;
        }
        if (lower.contains("overdue")) {
            return DbColumnType.BOOL;
        }
        if (lower.endsWith("id") || lower.contains("$id")) {
            return DbColumnType.INTEGER;
        }
        return DbColumnType.NUMBER;
    }

    private FDialect dialectFor(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "postgres", "postgresql" -> FDialect.POSTGRES_DIALECT;
            case "sqlserver", "mssql" -> FDialect.SQLSERVER_DIALECT;
            case "sqlite" -> FDialect.SQLITE_DIALECT;
            default -> FDialect.MYSQL_DIALECT;
        };
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class || type == short.class || type == byte.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
