package com.foggyframework.dataset.db.model.lifecycle.gate;

import com.foggyframework.dataset.db.model.test.JdbcModelTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(classes = JdbcModelTestApplication.class)
class RequiredDatabasePreflightIT {

    private static final Logger log = LoggerFactory.getLogger(RequiredDatabasePreflightIT.class);

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

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            DatabaseIdentity identity = DatabaseIdentity.from(metadata, connection);

            expected.verify(metadata, identity);
            verifySentinel();

            log.info(
                    "V933 database preflight passed: kind={}, product={}, version={}.{}, coordinate={}, catalog={}, schema={}",
                    expected.id,
                    metadata.getDatabaseProductName(),
                    metadata.getDatabaseMajorVersion(),
                    metadata.getDatabaseMinorVersion(),
                    identity.coordinate,
                    display(identity.catalog),
                    display(identity.schema)
            );
        }
    }

    private void verifySentinel() {
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

    private static String display(String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }

    private enum RequiredDatabase {
        SQLITE("sqlite", "sqlite", 3, 30, null, null, null, null),
        MYSQL57("mysql57", "mysql", 5, 7, "127.0.0.1", 13306, "foggy_test", null),
        POSTGRES15("postgres15", "postgresql", 15, null, "localhost", 15432, "foggy_test", "public");

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
            String value = System.getProperty("v933.expectedDatabase");
            assertNotNull(value, "v933.expectedDatabase is required");
            for (RequiredDatabase database : values()) {
                if (database.id.equals(value)) {
                    return database;
                }
            }
            return fail("Unsupported v933.expectedDatabase: " + value);
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
            } else {
                assertEquals(requiredMajor, actualMajor, "unexpected database major version");
                if (requiredMinor != null) {
                    assertEquals(requiredMinor, actualMinor, "unexpected database minor version");
                }
            }

            if (this == SQLITE) {
                assertEquals("sqlite:<shared-memory>", identity.coordinate, "unexpected SQLite identity");
                return;
            }

            assertEquals(expectedHost, identity.host, "unexpected database host");
            assertEquals(expectedPort, identity.port, "unexpected database port");
            assertEquals(expectedCatalog, identity.catalog, "unexpected database catalog");
            if (expectedSchema != null) {
                assertEquals(expectedSchema, identity.schema, "unexpected database schema");
            }
        }
    }

    private record StatusSentinel(String code, int sortOrder) {
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
                if (!metadata.getURL().startsWith("jdbc:sqlite:file::memory:")) {
                    fail("SQLite preflight requires the configured shared-memory database");
                }
                return new DatabaseIdentity("sqlite:<shared-memory>", null, null, catalog, schema);
            }

            URI uri = URI.create(metadata.getURL().substring("jdbc:".length()));
            String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            String coordinate = uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + "/" + database;
            return new DatabaseIdentity(coordinate, uri.getHost(), uri.getPort(), catalog, schema);
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
