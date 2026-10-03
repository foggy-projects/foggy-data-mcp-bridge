package com.foggyframework.dataviewer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;

/** Single-instance persistent grants and sessions; only SHA-256 digests of bearer secrets. */
public final class SqliteViewerGrantStore implements ViewerGrantStore {
    private final String url;
    private final ObjectMapper mapper;

    public SqliteViewerGrantStore(Path path, ObjectMapper mapper) {
        this.mapper = mapper;
        try {
            path = path.toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());
            url = "jdbc:sqlite:" + path;
            try (Connection c = open(); Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("CREATE TABLE IF NOT EXISTS viewer_link_grant (digest TEXT PRIMARY KEY, payload TEXT NOT NULL, revoked_at TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS viewer_browser_session (digest TEXT PRIMARY KEY, payload TEXT NOT NULL, expires_at INTEGER NOT NULL)");
                s.execute("CREATE INDEX IF NOT EXISTS viewer_browser_session_expiry ON viewer_browser_session(expires_at)");
            }
        } catch (Exception e) { throw new IllegalStateException("Cannot initialize viewer authorization store", e); }
    }

    @Override public void saveGrant(Grant grant) { insert("viewer_link_grant", grant.digest(), grant, null); }
    @Override public Optional<Grant> grant(String digest) {
        try (Connection c = open(); PreparedStatement s = c.prepareStatement("SELECT payload, revoked_at FROM viewer_link_grant WHERE digest=?")) {
            s.setString(1, digest);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return Optional.empty();
                Grant g = mapper.readValue(r.getString(1), Grant.class);
                String revoked = r.getString(2);
                return Optional.of(new Grant(g.digest(), g.queryId(), g.model(), g.namespace(), g.identity(),
                        g.createdAt(), g.expiresAt(), revoked == null ? null : Instant.parse(revoked)));
            }
        } catch (Exception e) { throw new IllegalStateException("Cannot read viewer grant", e); }
    }
    @Override public boolean revoke(String digest, Instant now) {
        try (Connection c = open(); PreparedStatement s = c.prepareStatement("UPDATE viewer_link_grant SET revoked_at=COALESCE(revoked_at,?) WHERE digest=?")) {
            s.setString(1, now.toString()); s.setString(2, digest);
            return s.executeUpdate() == 1;
        } catch (SQLException e) { throw new IllegalStateException("Cannot revoke viewer grant", e); }
    }
    @Override public void saveSession(Session session) {
        insert("viewer_browser_session", session.digest(), session, session.expiresAt());
    }
    @Override public Optional<Session> session(String digest) {
        try (Connection c = open(); PreparedStatement s = c.prepareStatement("SELECT payload FROM viewer_browser_session WHERE digest=?")) {
            s.setString(1, digest);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(mapper.readValue(r.getString(1), Session.class)) : Optional.empty();
            }
        } catch (Exception e) { throw new IllegalStateException("Cannot read viewer session", e); }
    }
    @Override public void cleanupSessions(Instant now) {
        try (Connection c=open(); PreparedStatement s=c.prepareStatement("DELETE FROM viewer_browser_session WHERE expires_at<=?")) {
            s.setLong(1,now.toEpochMilli()); s.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("Cannot clean viewer sessions",e); }
    }
    private void insert(String table, String digest, Object payload, Instant expiry) {
        String sql = expiry == null ? "INSERT INTO " + table + " (digest,payload) VALUES (?,?)"
                : "INSERT INTO " + table + " (digest,payload,expires_at) VALUES (?,?,?)";
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, digest); s.setString(2, mapper.writeValueAsString(payload));
            if (expiry != null) s.setLong(3, expiry.toEpochMilli());
            s.executeUpdate();
        } catch (Exception e) { throw new IllegalStateException("Cannot save viewer authorization record", e); }
    }
    private Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement s = c.createStatement()) { s.execute("PRAGMA busy_timeout=5000"); }
        catch (SQLException e) { c.close(); throw e; }
        return c;
    }
}
