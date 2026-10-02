package com.foggyframework.dataviewer.security;

import java.time.Instant;
import java.util.Optional;

/** Grants are durable authorization records, never one-time exchange codes or query caches. */
public interface ViewerGrantStore {
    void saveGrant(Grant grant);
    Optional<Grant> grant(String digest);
    boolean revoke(String digest, Instant now);
    void saveSession(Session session);
    Optional<Session> session(String digest);
    default void cleanupSessions(Instant now) { }

    record Grant(String digest, String queryId, String model, String namespace,
                 ViewerIdentityProvider.Identity identity, Instant createdAt, Instant expiresAt,
                 Instant revokedAt) { }
    record Session(String digest, String grantDigest, Instant issuedAt, Instant expiresAt) { }
}
