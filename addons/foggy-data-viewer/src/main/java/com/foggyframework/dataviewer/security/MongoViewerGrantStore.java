package com.foggyframework.dataviewer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import java.time.Instant;
import java.util.Optional;

/** Shared durable grants; deliberately no TTL index on link records. Revocation is atomic. */
public final class MongoViewerGrantStore implements ViewerGrantStore {
    private final MongoTemplate mongo;
    private final ObjectMapper mapper;
    private static final String LINKS="viewer_link_grants",SESSIONS="viewer_browser_sessions";
    public MongoViewerGrantStore(MongoTemplate mongo,ObjectMapper mapper) { this.mongo=mongo; this.mapper=mapper; }
    @Override public void saveGrant(Grant grant) { insert(LINKS,grant.digest(),grant,null); }
    @Override public Optional<Grant> grant(String digest) {
        Document d=mongo.findById(digest,Document.class,LINKS);
        if (d==null) return Optional.empty();
        Grant g=decode(d,Grant.class);
        String revoked=d.getString("revokedAt");
        return Optional.of(new Grant(g.digest(),g.queryId(),g.model(),g.namespace(),g.identity(),g.createdAt(),g.expiresAt(),revoked==null?null:Instant.parse(revoked)));
    }
    @Override public boolean revoke(String digest,Instant now) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(digest).and("revokedAt").is(null)),
                Update.update("revokedAt",now.toString()),LINKS);
        return mongo.exists(Query.query(Criteria.where("_id").is(digest)),LINKS);
    }
    @Override public void saveSession(Session session) { insert(SESSIONS,session.digest(),session,session.expiresAt()); }
    @Override public Optional<Session> session(String digest) {
        Document d=mongo.findById(digest,Document.class,SESSIONS);
        return d==null?Optional.empty():Optional.of(decode(d,Session.class));
    }
    @Override public void cleanupSessions(Instant now) {
        mongo.remove(Query.query(Criteria.where("expiresAt").lte(now.toEpochMilli())),SESSIONS);
    }
    private void insert(String collection,String digest,Object payload,Instant expiry) {
        try {
            Document d=new Document("_id",digest).append("payload",mapper.writeValueAsString(payload));
            if (expiry!=null) d.append("expiresAt",expiry.toEpochMilli());
            mongo.insert(d,collection);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot serialize viewer record",e); }
    }
    private <T> T decode(Document d,Class<T> type) {
        try { return mapper.readValue(d.getString("payload"),type); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot decode viewer record",e); }
    }
}
