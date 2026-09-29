package com.foggyframework.dataviewer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataviewer.domain.CachedQueryContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Local, single-instance query context store. Query results are never stored here. */
public final class SqliteQueryContextStore implements QueryContextStore {

    private final String jdbcUrl;
    private final ObjectMapper objectMapper;
    private final long cleanupIntervalMillis;
    private final AtomicLong lastCleanupMillis = new AtomicLong();

    public SqliteQueryContextStore(Path databasePath, ObjectMapper objectMapper, long cleanupIntervalMillis) {
        if (databasePath == null || objectMapper == null) {
            throw new IllegalArgumentException("SQLite database path and ObjectMapper are required");
        }
        try {
            Path absolutePath = databasePath.toAbsolutePath().normalize();
            Files.createDirectories(absolutePath.getParent());
            this.jdbcUrl = "jdbc:sqlite:" + absolutePath;
            this.objectMapper = objectMapper;
            this.cleanupIntervalMillis = Math.max(1_000, cleanupIntervalMillis);
            try (Connection connection = open(); Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("CREATE TABLE IF NOT EXISTS viewer_query_context ("
                        + "query_id TEXT PRIMARY KEY, model TEXT NOT NULL, namespace TEXT, "
                        + "created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, "
                        + "estimated_row_count INTEGER, payload_json TEXT NOT NULL)");
                statement.execute("CREATE INDEX IF NOT EXISTS viewer_query_context_expires_idx "
                        + "ON viewer_query_context(expires_at)");
            }
            cleanup(Instant.now());
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Could not initialize DataViewer SQLite query context store", e);
        }
    }

    @Override
    public CachedQueryContext save(CachedQueryContext context) {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO viewer_query_context "
                        + "(query_id, model, namespace, created_at, expires_at, estimated_row_count, payload_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, context.getQueryId());
            statement.setString(2, context.getModel());
            statement.setString(3, context.getNamespace());
            statement.setLong(4, context.getCreatedAt().toEpochMilli());
            statement.setLong(5, context.getExpiresAt().toEpochMilli());
            if (context.getEstimatedRowCount() == null) {
                statement.setNull(6, java.sql.Types.BIGINT);
            } else {
                statement.setLong(6, context.getEstimatedRowCount());
            }
            statement.setString(7, objectMapper.writeValueAsString(context));
            statement.executeUpdate();
            cleanupIfDue(Instant.now());
            return context;
        } catch (SQLException | JsonProcessingException e) {
            throw new IllegalStateException("Could not save DataViewer query context", e);
        }
    }

    @Override
    public Optional<CachedQueryContext> findActive(String queryId, Instant now) {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "SELECT payload_json, expires_at, estimated_row_count FROM viewer_query_context "
                        + "WHERE query_id = ? AND expires_at > ?")) {
            statement.setString(1, queryId);
            statement.setLong(2, now.toEpochMilli());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                CachedQueryContext context = objectMapper.readValue(result.getString(1), CachedQueryContext.class);
                context.setExpiresAt(Instant.ofEpochMilli(result.getLong(2)));
                long estimatedRowCount = result.getLong(3);
                context.setEstimatedRowCount(result.wasNull() ? null : estimatedRowCount);
                return Optional.of(context);
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Could not read DataViewer query context", e);
        }
    }

    @Override
    public boolean extendExpiry(String queryId, String model, String namespace, Instant now, Instant expiresAt) {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE viewer_query_context SET expires_at = ? "
                        + "WHERE query_id = ? AND model = ? AND namespace IS ? AND expires_at > ?")) {
            statement.setLong(1, expiresAt.toEpochMilli());
            statement.setString(2, queryId);
            statement.setString(3, model);
            statement.setString(4, namespace);
            statement.setLong(5, now.toEpochMilli());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not extend DataViewer query context expiry", e);
        }
    }

    @Override
    public void updateEstimatedRowCount(String queryId, Long estimatedRowCount, Instant now) {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE viewer_query_context SET estimated_row_count = ? "
                        + "WHERE query_id = ? AND expires_at > ?")) {
            if (estimatedRowCount == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setLong(1, estimatedRowCount);
            }
            statement.setString(2, queryId);
            statement.setLong(3, now.toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update DataViewer query context row count", e);
        }
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    private void cleanupIfDue(Instant now) {
        long timestamp = now.toEpochMilli();
        long previous = lastCleanupMillis.get();
        if (timestamp - previous >= cleanupIntervalMillis && lastCleanupMillis.compareAndSet(previous, timestamp)) {
            cleanup(now);
        }
    }

    private void cleanup(Instant now) {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM viewer_query_context WHERE expires_at <= ?")) {
            statement.setLong(1, now.toEpochMilli());
            statement.executeUpdate();
            lastCleanupMillis.set(now.toEpochMilli());
        } catch (SQLException e) {
            throw new IllegalStateException("Could not clean expired DataViewer query contexts", e);
        }
    }
}
