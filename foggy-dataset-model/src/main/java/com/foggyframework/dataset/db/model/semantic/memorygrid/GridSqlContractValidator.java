package com.foggyframework.dataset.db.model.semantic.memorygrid;

import com.foggyframework.core.ex.RX;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared preflight guardrail for governed Grid SQL.
 */
public final class GridSqlContractValidator {

    public static final String SQL_NOT_DECLARED = "MEMORY_GRID_GRID_SQL_NOT_DECLARED";
    public static final String BINDING_INVALID = "MEMORY_GRID_GRID_SQL_BINDING_INVALID";
    public static final String STATEMENT_DENIED = "MEMORY_GRID_GRID_SQL_STATEMENT_DENIED";
    public static final String MULTI_STATEMENT_DENIED = "MEMORY_GRID_GRID_SQL_MULTI_STATEMENT_DENIED";
    public static final String RESOURCE_DENIED = "MEMORY_GRID_GRID_SQL_RESOURCE_DENIED";
    public static final String EXTERNAL_RESOURCE_DENIED = "MEMORY_GRID_GRID_SQL_EXTERNAL_RESOURCE_DENIED";

    private static final Pattern ALIAS_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Pattern TABLE_FUNCTION_PATTERN = Pattern.compile(
            "(?i)\\b(read_csv|read_parquet|read_json|read_ndjson|sqlite_scan|postgres_scan|mysql_scan|httpfs|glob)\\s*\\(");
    private static final Set<String> GOVERNED_SOURCE_ROUTES = Set.of(
            "DSL", "DSL_CTE", "SEMANTIC_SQL", "VIRTUAL_SQL_TO_DSL", "MEMORY_GRID_PLAN");
    private static final Set<String> DENIED_STATEMENT_PREFIXES = Set.of(
            "INSERT", "UPDATE", "DELETE", "MERGE", "UPSERT",
            "CREATE", "DROP", "ALTER", "TRUNCATE", "ATTACH", "DETACH",
            "COPY", "INSTALL", "LOAD", "PRAGMA", "CALL", "EXEC", "EXECUTE",
            "USE", "GRANT", "REVOKE", "COMMIT", "ROLLBACK", "SAVEPOINT");

    private GridSqlContractValidator() {
    }

    public static Map<String, Object> validate(MemoryGridRequest request) {
        if (request == null || isBlank(request.gridSql())) {
            throw RX.throwB(SQL_NOT_DECLARED + ": grid_sql must be provided.");
        }
        if (request.plan() != null && !request.plan().isEmpty()) {
            throw RX.throwB(BINDING_INVALID + ": grid_sql and memory_grid_plan are mutually exclusive.");
        }

        Map<String, MemoryGridInputBinding> bindings = validateBindings(request.bindings());
        String normalizedSql = normalizeSql(request.gridSql());
        rejectMultiStatement(normalizedSql);
        rejectExternalResource(normalizedSql);

        Statement statement = parse(normalizedSql);
        if (!(statement instanceof Select select)) {
            throw RX.throwB(STATEMENT_DENIED + ": only SELECT Grid SQL is allowed.");
        }
        if (select instanceof SetOperationList) {
            throw RX.throwB(STATEMENT_DENIED + ": set operations are not part of foggy-grid-sql-v1 preflight.");
        }
        if (select instanceof PlainSelect plainSelect
                && plainSelect.getIntoTables() != null
                && !plainSelect.getIntoTables().isEmpty()) {
            throw RX.throwB(STATEMENT_DENIED + ": SELECT INTO is not allowed for Grid SQL.");
        }

        Set<String> references = referencedTables(statement);
        validateReferences(references, bindings.keySet());
        List<String> orderedReferences = bindings.keySet().stream()
                .filter(references::contains)
                .toList();

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("grid_sql_supported", true);
        evidence.put("grid_sql_dialect", "foggy-grid-sql-v1");
        evidence.put("grid_sql_statement_type", "SELECT");
        evidence.put("grid_sql_aliases", orderedReferences);
        evidence.put("grid_sql_binding_aliases", List.copyOf(bindings.keySet()));
        evidence.put("grid_sql_with_items", withItemAliases(select));
        evidence.put("grid_sql_resource_validation", "ALIAS_ONLY");
        evidence.put("grid_sql_external_resource_validation", "NO_EXTERNAL_RESOURCE");
        if (request.hints().containsKey("outputLimit")) {
            evidence.put("output_limit", request.hints().get("outputLimit"));
        }
        return evidence;
    }

    private static Map<String, MemoryGridInputBinding> validateBindings(List<MemoryGridInputBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            throw RX.throwB(BINDING_INVALID + ": grid_sql requires at least one result_handle binding.");
        }
        Map<String, MemoryGridInputBinding> aliases = new LinkedHashMap<>();
        Set<String> normalizedAliases = new LinkedHashSet<>();
        for (MemoryGridInputBinding binding : bindings) {
            if (binding == null) {
                throw RX.throwB(BINDING_INVALID + ": binding must be non-null.");
            }
            String alias = binding.alias();
            if (isBlank(alias) || !ALIAS_PATTERN.matcher(alias).matches()) {
                throw RX.throwB(BINDING_INVALID + ": binding alias must be a short SQL identifier.");
            }
            String normalizedAlias = alias.toLowerCase(Locale.ROOT);
            if (!normalizedAliases.add(normalizedAlias)) {
                throw RX.throwB(BINDING_INVALID + ": duplicate binding alias '" + alias + "'.");
            }
            if (isBlank(binding.resultHandle())) {
                throw RX.throwB(BINDING_INVALID + ": binding '" + alias + "' must reference a result_handle.");
            }
            if (isBlank(binding.sourceRoute())
                    || !GOVERNED_SOURCE_ROUTES.contains(binding.sourceRoute().toUpperCase(Locale.ROOT))) {
                throw RX.throwB(BINDING_INVALID + ": binding '" + alias + "' must come from a governed source route.");
            }
            if (binding.metadata() == null || !binding.metadata().containsKey("row_limit")) {
                throw RX.throwB(BINDING_INVALID + ": binding '" + alias + "' must carry a row_limit metadata entry.");
            }
            aliases.put(normalizedAlias, binding);
        }
        return aliases;
    }

    private static String normalizeSql(String sql) {
        String stripped = stripComments(sql).trim();
        if (stripped.endsWith(";")) {
            stripped = stripped.substring(0, stripped.length() - 1).trim();
        }
        if (isBlank(stripped)) {
            throw RX.throwB(SQL_NOT_DECLARED + ": grid_sql must be non-empty.");
        }
        String firstToken = stripped.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        if (DENIED_STATEMENT_PREFIXES.contains(firstToken)) {
            throw RX.throwB(STATEMENT_DENIED + ": statement '" + firstToken + "' is not allowed for Grid SQL.");
        }
        return stripped;
    }

    private static void rejectMultiStatement(String sql) {
        if (sql.indexOf(';') >= 0) {
            throw RX.throwB(MULTI_STATEMENT_DENIED + ": only one Grid SQL statement is allowed.");
        }
    }

    private static void rejectExternalResource(String sql) {
        if (TABLE_FUNCTION_PATTERN.matcher(sql).find()) {
            throw RX.throwB(EXTERNAL_RESOURCE_DENIED + ": external table functions are not allowed for Grid SQL.");
        }
    }

    private static Statement parse(String sql) {
        try {
            return CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException ex) {
            throw RX.throwB(STATEMENT_DENIED + ": grid_sql is not valid SQL.");
        }
    }

    private static Set<String> referencedTables(Statement statement) {
        Set<String> raw = new TablesNamesFinder().getTables(statement);
        Set<String> normalized = new LinkedHashSet<>();
        for (String table : raw) {
            normalized.add(unquote(table).toLowerCase(Locale.ROOT));
        }
        if (normalized.isEmpty()) {
            throw RX.throwB(RESOURCE_DENIED + ": Grid SQL must reference at least one bound alias.");
        }
        return normalized;
    }

    private static void validateReferences(Set<String> references, Set<String> aliases) {
        for (String reference : references) {
            if (reference.contains(".") || !aliases.contains(reference)) {
                throw RX.throwB(RESOURCE_DENIED + ": table reference '" + reference
                        + "' is not a declared result_handle alias.");
            }
        }
    }

    private static List<String> withItemAliases(Select select) {
        if (select.getWithItemsList() == null || select.getWithItemsList().isEmpty()) {
            return List.of();
        }
        return select.getWithItemsList().stream()
                .map(WithItem::getUnquotedAliasName)
                .filter(alias -> !isBlank(alias))
                .toList();
    }

    private static String stripComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)--.*$", " ");
    }

    private static String unquote(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("`") && trimmed.endsWith("`"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
