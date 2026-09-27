package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.domain.CachedQueryContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCachedQueryStoreTest {
    @Test
    void expiresAndBoundsPreviewLinks() {
        InMemoryCachedQueryStore store = new InMemoryCachedQueryStore(1);
        Instant now = Instant.now();
        store.save(context("first", now.minusSeconds(2), now.plusSeconds(30)));
        assertThat(store.findUnexpired("first", now)).isPresent();
        store.save(context("second", now, now.plusSeconds(30)));
        assertThat(store.findUnexpired("first", now)).isEmpty();
        assertThat(store.findUnexpired("second", now)).isPresent();
        assertThat(store.findUnexpired("second", now.plusSeconds(31))).isEmpty();
    }

    private CachedQueryContext context(String id, Instant created, Instant expires) {
        return CachedQueryContext.builder()
                .queryId(id)
                .createdAt(created)
                .expiresAt(expires)
                .build();
    }
}
