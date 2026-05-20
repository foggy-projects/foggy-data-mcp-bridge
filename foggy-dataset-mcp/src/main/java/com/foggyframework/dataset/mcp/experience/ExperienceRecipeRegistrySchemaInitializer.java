package com.foggyframework.dataset.mcp.experience;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ExperienceRecipeRegistrySchemaInitializer {
    static final int SCHEMA_VERSION = 1;
    static final String SCHEMA_RESOURCE =
            "db/experience-recipe-registry/V1__experience_recipe_registry.sql";

    private static final String SCHEMA_HISTORY_TABLE = "experience_recipe_registry_schema_history";

    private final JdbcTemplate jdbcTemplate;

    ExperienceRecipeRegistrySchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void initializeSchema() {
        createSchemaHistoryTableIfMissing();
        if (!schemaVersionApplied() || registryTablesMissing()) {
            executeSchemaResource();
            recordSchemaVersion();
        }
        ensureRegistryRecordVersionColumn();
        ensureRegistryTextColumn("namespace_scope");
        ensureRegistryTextColumn("tenant_scope");
        ensureRegistryTextColumn("permission_tags");
        createIndexIfMissing(
                "experience_recipe_registry",
                "idx_exp_recipe_registry_discovery",
                "CREATE INDEX idx_exp_recipe_registry_discovery "
                        + "ON experience_recipe_registry(status, active_for_discovery)");
        createIndexIfMissing(
                "experience_recipe_registry_event",
                "uk_exp_recipe_registry_event_idem",
                "CREATE UNIQUE INDEX uk_exp_recipe_registry_event_idem "
                        + "ON experience_recipe_registry_event(idempotency_key)");
    }

    private void createSchemaHistoryTableIfMissing() {
        if (tableExists(SCHEMA_HISTORY_TABLE)) {
            return;
        }
        jdbcTemplate.execute("""
                CREATE TABLE experience_recipe_registry_schema_history (
                    version INTEGER PRIMARY KEY,
                    description VARCHAR(255) NOT NULL,
                    applied_at TIMESTAMP NOT NULL
                )
                """);
    }

    private boolean schemaVersionApplied() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM experience_recipe_registry_schema_history
                WHERE version = ?
                """, Integer.class, SCHEMA_VERSION);
        return count != null && count > 0;
    }

    private boolean registryTablesMissing() {
        return !tableExists("experience_recipe_registry")
                || !tableExists("experience_recipe_registry_event")
                || !tableExists("experience_recipe_closure_event");
    }

    private void executeSchemaResource() {
        String sql = readSchemaResource();
        for (String statement : splitStatements(sql)) {
            if (!statement.isBlank()) {
                jdbcTemplate.execute(statement);
            }
        }
    }

    private String readSchemaResource() {
        ClassPathResource resource = new ClassPathResource(SCHEMA_RESOURCE);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Failed to read experience recipe registry schema resource: " + SCHEMA_RESOURCE, ex);
        }
    }

    private List<String> splitStatements(String sql) {
        String stripped = sql.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("--"))
                .reduce("", (left, right) -> left + "\n" + right);
        return Arrays.stream(stripped.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isBlank())
                .toList();
    }

    private void recordSchemaVersion() {
        if (schemaVersionApplied()) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO experience_recipe_registry_schema_history(version, description, applied_at)
                VALUES (?, ?, ?)
                """, SCHEMA_VERSION, "experience recipe registry v1", Timestamp.from(Instant.now()));
    }

    private void createIndexIfMissing(String tableName, String indexName, String createSql) {
        if (indexExists(tableName, indexName)) {
            return;
        }
        jdbcTemplate.execute(createSql);
    }

    private void ensureRegistryRecordVersionColumn() {
        if (!tableExists("experience_recipe_registry")
                || columnExists("experience_recipe_registry", "record_version")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE experience_recipe_registry ADD COLUMN record_version INTEGER");
        jdbcTemplate.update("""
                UPDATE experience_recipe_registry
                SET record_version = 1
                WHERE record_version IS NULL
                """);
    }

    private void ensureRegistryTextColumn(String columnName) {
        if (!tableExists("experience_recipe_registry")
                || columnExists("experience_recipe_registry", columnName)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE experience_recipe_registry ADD COLUMN " + columnName + " VARCHAR(1000)");
    }

    private boolean tableExists(String tableName) {
        try {
            DataSource dataSource = jdbcTemplate.getDataSource();
            if (dataSource == null) {
                return false;
            }
            try (var connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                for (String candidate : identifierCandidates(tableName)) {
                    try (ResultSet rs = metaData.getTables(null, null, candidate, new String[]{"TABLE"})) {
                        if (rs.next()) {
                            return true;
                        }
                    }
                }
                return false;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to inspect experience recipe registry table: " + tableName, ex);
        }
    }

    private boolean indexExists(String tableName, String indexName) {
        try {
            DataSource dataSource = jdbcTemplate.getDataSource();
            if (dataSource == null) {
                return false;
            }
            try (var connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                for (String tableCandidate : identifierCandidates(tableName)) {
                    try (ResultSet rs = metaData.getIndexInfo(null, null, tableCandidate, false, false)) {
                        while (rs.next()) {
                            if (indexName.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to inspect experience recipe registry index: " + indexName, ex);
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        try {
            DataSource dataSource = jdbcTemplate.getDataSource();
            if (dataSource == null) {
                return false;
            }
            try (var connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                for (String tableCandidate : identifierCandidates(tableName)) {
                    for (String columnCandidate : identifierCandidates(columnName)) {
                        try (ResultSet rs = metaData.getColumns(null, null, tableCandidate, columnCandidate)) {
                            if (rs.next()) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to inspect experience recipe registry column: " + columnName, ex);
        }
    }

    private Set<String> identifierCandidates(String identifier) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(identifier);
        candidates.add(identifier.toUpperCase(Locale.ROOT));
        candidates.add(identifier.toLowerCase(Locale.ROOT));
        return candidates;
    }
}
