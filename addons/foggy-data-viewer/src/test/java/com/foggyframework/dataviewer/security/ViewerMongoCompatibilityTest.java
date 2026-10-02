package com.foggyframework.dataviewer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.repository.CachedQueryRepository;
import com.foggyframework.dataviewer.service.MongoQueryContextStore;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Mongo is optional; verifies backend contract without requiring a shared database in this demo. */
class ViewerMongoCompatibilityTest {
    @Test void permanentQueryHasNoMongoTtlAndFiniteQueryStillExpires() {
        var repository=mock(CachedQueryRepository.class); var q=CachedQueryContext.builder().queryId("q").model("m").namespace("ns")
                .expiresAt(Instant.now().plusSeconds(60)).build();
        when(repository.findById("q")).thenReturn(Optional.of(q));
        var store=new MongoQueryContextStore(repository);
        assertTrue(store.extendExpiry("q","m","ns",Instant.now(),null));
        verify(repository).save(q); assertNull(q.getExpiresAt());
        assertTrue(store.findActive("q",Instant.now().plusSeconds(86400)).isPresent());
        q.setExpiresAt(Instant.now().minusSeconds(1)); assertTrue(store.findActive("q",Instant.now()).isEmpty());
    }
    @Test void sharedGrantUsesDigestAndAtomicRevocationRecord() throws Exception {
        var mongo=mock(MongoTemplate.class); var mapper=new ObjectMapper().findAndRegisterModules();
        var grant=new ViewerGrantStore.Grant("digest","q","m","ns",new ViewerIdentityProvider.Identity("ref","v1"),Instant.now(),null,null);
        Document d=new Document("_id","digest").append("payload",mapper.writeValueAsString(grant)).append("revokedAt",Instant.now().toString());
        when(mongo.findById("digest",Document.class,"viewer_link_grants")).thenReturn(d);
        when(mongo.exists(any(Query.class),eq("viewer_link_grants"))).thenReturn(true);
        var store=new MongoViewerGrantStore(mongo,mapper);
        assertNotNull(store.grant("digest").orElseThrow().revokedAt());
        assertTrue(store.revoke("digest",Instant.now()));
        verify(mongo).updateFirst(any(Query.class),any(org.springframework.data.mongodb.core.query.Update.class),eq("viewer_link_grants"));
    }
}
