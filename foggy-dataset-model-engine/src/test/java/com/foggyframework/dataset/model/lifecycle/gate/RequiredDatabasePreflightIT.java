package com.foggyframework.dataset.model.lifecycle.gate;

import com.foggyframework.dataset.model.test.JdbcModelTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(classes = JdbcModelTestApplication.class)
class RequiredDatabasePreflightIT {

    private static final Logger log = LoggerFactory.getLogger(RequiredDatabasePreflightIT.class);
    private static final String CONTRACT_SENTINEL_CANONICAL =
            "v934_test_sentinel|contract_version|9.3.4\n";
    private static final String CONTRACT_SENTINEL_SHA256 =
            "cef04c4c1269e1293bf243e61e0a9672697bfd55b0bca48297943026bd82c191";

    private static final List<StatusSentinel> EXPECTED_ORDER_STATUSES = List.of(
            new StatusSentinel("PENDING", 1),
            new StatusSentinel("PAID", 2),
            new StatusSentinel("SHIPPED", 3),
            new StatusSentinel("COMPLETED", 4),
            new StatusSentinel("CANCELLED", 5)
    );

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void verifiesProductVersionPhysicalIdentityAndSentinel() throws Exception {
        RequiredDatabase expected = RequiredDatabase.fromRequiredProperty();
        boolean v934Contract = RequiredDatabase.isV934Contract();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            DatabaseIdentity identity = DatabaseIdentity.from(metadata, connection);

            expected.verify(metadata, identity);
            verifyOrderStatusSentinel();
            if (v934Contract) {
                verifyContractSentinel();
            }

            log.info(
                    "{} database preflight passed: kind={}, product={}, version={}, coordinate={}, catalog={}, schema={}, sentinel_sha256={}",
                    v934Contract ? "V934" : "V933",
                    expected.id,
                    metadata.getDatabaseProductName(),
                    metadata.getDatabaseProductVersion(),
                    identity.coordinate,
                    display(identity.catalog),
                    display(identity.schema),
                    v934Contract ? CONTRACT_SENTINEL_SHA256 : "<not-required>"
            );
        }
    }

    private void verifyOrderStatusSentinel() {
        List<StatusSentinel> actual = jdbcTemplate.query(
                "SELECT status_code, sort_order FROM dict_status " +
                        "WHERE status_type = 'ORDER_STATUS' ORDER BY sort_order",
                (resultSet, rowNum) -> new StatusSentinel(
                        resultSet.getString("status_code"),
                        resultSet.getInt("sort_order")
                )
        );
        assertEquals(EXPECTED_ORDER_STATUSES, actual, "ORDER_STATUS sentinel fixture mismatch");
    }

    private void verifyContractSentinel() {
        List<ContractSentinel> contractSentinels = jdbcTemplate.query(
                "SELECT sentinel_key, sentinel_value FROM v934_test_sentinel ORDER BY sentinel_key",
                (resultSet, rowNum) -> new ContractSentinel(
                        resultSet.getString("sentinel_key"),
                        resultSet.getString("sentinel_value")
                )
        );
        assertEquals(
                List.of(new ContractSentinel("contract_version", "9.3.4")),
                contractSentinels,
                "9.3.4 contract sentinel fixture mismatch"
        );
        String canonical = contractSentinels.stream()
                .map(sentinel -> "v934_test_sentinel|" + sentinel.key() + "|" + sentinel.value() + "\n")
                .reduce("", String::concat);
        assertEquals(CONTRACT_SENTINEL_CANONICAL, canonical, "sentinel canonical form mismatch");
        assertEquals(CONTRACT_SENTINEL_SHA256, sha256(canonical), "sentinel SHA-256 mismatch");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }

    private static String configuredRunScopedSqliteUrl() {
        String configured = System.getProperty("v934.sqlite.expectedUrl");
        if (configured == null || configured.isBlank()) {
            return null;
        }
        assertTrue(RequiredDatabase.isV934Contract(),
                "v934.sqlite.expectedUrl is only valid for the V934 contract");
        String expected = configured.trim();
        assertTrue(expected.startsWith("jdbc:sqlite:/"),
                "run-scoped SQLite URL must contain an absolute file path");
        assertFalse(expected.contains(":memory:"),
                "run-scoped SQLite authority must not use an in-memory database");
        assertFalse(expected.contains("?"),
                "run-scoped SQLite authority URL must not contain unreviewed parameters");
        return expected;
    }

    private enum RequiredDatabase {
        SQLITE("sqlite", "sqlite", 3, 30, null, null, null, null),
        MYSQL57("mysql57", "mysql", 5, 7, "127.0.0.1", 13306, "foggy_test", null),
        MYSQL8("mysql8", "mysql", 8, 0, "127.0.0.1", 13308, "foggy_test", null),
        POSTGRES15("postgres15", "postgresql", 15, null,
                "127.0.0.1", 15432, "foggy_test", "public"),
        SQLSERVER2022("sqlserver2022", "microsoft sql server", 16, 0,
                "127.0.0.1", 11433, "foggy_test", "dbo");

        private final String id;
        private final String productToken;
        private final int requiredMajor;
        private final Integer requiredMinor;
        private final String expectedHost;
        private final Integer expectedPort;
        private final String expectedCatalog;
        private final String expectedSchema;

        RequiredDatabase(
                String id,
                String productToken,
                int requiredMajor,
                Integer requiredMinor,
                String expectedHost,
                Integer expectedPort,
                String expectedCatalog,
                String expectedSchema
        ) {
            this.id = id;
            this.productToken = productToken;
            this.requiredMajor = requiredMajor;
            this.requiredMinor = requiredMinor;
            this.expectedHost = expectedHost;
            this.expectedPort = expectedPort;
            this.expectedCatalog = expectedCatalog;
            this.expectedSchema = expectedSchema;
        }

        static RequiredDatabase fromRequiredProperty() {
            String v934 = System.getProperty("v934.expectedDatabase");
            String v933 = System.getProperty("v933.expectedDatabase");
            if (v934 != null && !v934.isBlank() && v933 != null && !v933.isBlank()) {
                assertEquals(v934, v933,
                        "v934.expectedDatabase conflicts with compatibility property v933.expectedDatabase");
            }
            boolean v934Contract = v934 != null && !v934.isBlank();
            String value = v934Contract ? v934 : v933;
            assertNotNull(value, v934Contract
                    ? "v934.expectedDatabase is required"
                    : "v933.expectedDatabase is required");
            for (RequiredDatabase database : values()) {
                if (database.id.equals(value.trim())) {
                    if (!v934Contract && (database == MYSQL8 || database == SQLSERVER2022)) {
                        return fail("Unsupported v933.expectedDatabase: " + value);
                    }
                    return database;
                }
            }
            return fail("Unsupported " + (v934Contract
                    ? "v934.expectedDatabase: "
                    : "v933.expectedDatabase: ") + value);
        }

        static boolean isV934Contract() {
            String value = System.getProperty("v934.expectedDatabase");
            return value != null && !value.isBlank();
        }

        void verify(DatabaseMetaData metadata, DatabaseIdentity identity) throws SQLException {
            String product = metadata.getDatabaseProductName().toLowerCase(Locale.ROOT);
            assertTrue(product.contains(productToken), "unexpected database product: " + product);

            int actualMajor = metadata.getDatabaseMajorVersion();
            int actualMinor = metadata.getDatabaseMinorVersion();
            if (this == SQLITE) {
                assertTrue(
                        actualMajor > requiredMajor || actualMajor == requiredMajor && actualMinor >= requiredMinor,
                        "SQLite 3.30+ is required, actual=" + actualMajor + "." + actualMinor
                );
                assertEquals("3.42.0", metadata.getDatabaseProductVersion(),
                        "unexpected SQLite engine version");
                assertEquals("SQLite JDBC", metadata.getDriverName(),
                        "unexpected SQLite JDBC driver");
                assertEquals("3.42.0.0", metadata.getDriverVersion(),
                        "unexpected SQLite JDBC artifact version");
                String runScopedUrl = configuredRunScopedSqliteUrl();
                assertEquals(runScopedUrl != null
                                ? runScopedUrl
                                : "jdbc:sqlite:file::memory:?cache=shared",
                        metadata.getURL(),
                        "unexpected SQLite JDBC URL/storage mode");
            } else {
                assertEquals(requiredMajor, actualMajor, "unexpected database major version");
                if (requiredMinor != null) {
                    assertEquals(requiredMinor, actualMinor, "unexpected database minor version");
                }
            }
            if (this == MYSQL57) {
                assertEquals("5.7.44-log", metadata.getDatabaseProductVersion(),
                        "unexpected MySQL 5.7 exact product version");
            }

            if (this == SQLITE) {
                assertEquals(configuredRunScopedSqliteUrl() != null
                                ? "sqlite:<run-scoped-file>"
                                : "sqlite:<shared-memory>",
                        identity.coordinate, "unexpected SQLite identity");
                assertEquals(expectedCatalog, identity.catalog, "unexpected SQLite catalog");
                assertEquals(expectedSchema, identity.schema, "unexpected SQLite schema");
                return;
            }

            assertEquals(expectedHost, identity.host, "unexpected database host");
            assertEquals(expectedPort, identity.port, "unexpected database port");
            assertEquals(expectedCatalog, identity.catalog, "unexpected database catalog");
            assertEquals(expectedSchema, identity.schema, "unexpected database schema");
        }
    }

    private record StatusSentinel(String code, int sortOrder) {
    }

    private record ContractSentinel(String key, String value) {
    }

    private record DatabaseIdentity(
            String coordinate,
            String host,
            Integer port,
            String catalog,
            String schema
    ) {
        static DatabaseIdentity from(DatabaseMetaData metadata, Connection connection) throws SQLException {
            String product = metadata.getDatabaseProductName().toLowerCase(Locale.ROOT);
            String catalog = connection.getCatalog();
            String schema = safeSchema(connection);

            if (product.contains("sqlite")) {
                String runScopedUrl = configuredRunScopedSqliteUrl();
                if (runScopedUrl != null) {
                    assertEquals(runScopedUrl, metadata.getURL(),
                            "SQLite metadata URL must match the run-scoped contract");
                    return new DatabaseIdentity(
                            "sqlite:<run-scoped-file>", null, null, catalog, schema);
                }
                if (!metadata.getURL().startsWith("jdbc:sqlite:file::memory:")) {
                    fail("SQLite preflight requires the configured shared-memory database");
                }
                return new DatabaseIdentity("sqlite:<shared-memory>", null, null, catalog, schema);
            }

            if (product.contains("microsoft sql server")) {
                return fromSqlServer(metadata.getURL(), catalog, schema);
            }

            URI uri = URI.create(metadata.getURL().substring("jdbc:".length()));
            String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            String coordinate = uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + "/" + database;
            return new DatabaseIdentity(coordinate, uri.getHost(), uri.getPort(), catalog, schema);
        }

        private static DatabaseIdentity fromSqlServer(
                String jdbcUrl,
                String catalog,
                String schema
        ) {
            String prefix = "jdbc:sqlserver://";
            if (!jdbcUrl.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                fail("unexpected SQL Server JDBC URL: " + jdbcUrl);
            }
            String remainder = jdbcUrl.substring(prefix.length());
            int propertyStart = remainder.indexOf(';');
            String endpoint = propertyStart < 0 ? remainder : remainder.substring(0, propertyStart);
            String databaseName = null;
            if (propertyStart >= 0) {
                for (String property : remainder.substring(propertyStart + 1).split(";")) {
                    int equals = property.indexOf('=');
                    if (equals > 0
                            && property.substring(0, equals).trim()
                            .equalsIgnoreCase("databaseName")) {
                        databaseName = property.substring(equals + 1).trim();
                    }
                }
            }
            assertNotNull(databaseName,
                    "SQL Server JDBC URL must contain an explicit databaseName property");
            assertEquals(catalog, databaseName,
                    "SQL Server JDBC URL databaseName must match the active catalog");
            int separator = endpoint.lastIndexOf(':');
            if (separator <= 0 || separator == endpoint.length() - 1) {
                fail("SQL Server JDBC URL must contain an explicit host and port");
            }
            String host = endpoint.substring(0, separator);
            int port;
            try {
                port = Integer.parseInt(endpoint.substring(separator + 1));
            } catch (NumberFormatException invalidPort) {
                return fail("invalid SQL Server JDBC port: " + endpoint);
            }
            String coordinate = "sqlserver://" + host + ":" + port
                    + ";databaseName=" + databaseName;
            return new DatabaseIdentity(coordinate, host, port, catalog, schema);
        }

        private static String safeSchema(Connection connection) throws SQLException {
            try {
                return connection.getSchema();
            } catch (SQLFeatureNotSupportedException ignored) {
                return null;
            }
        }
    }
}
