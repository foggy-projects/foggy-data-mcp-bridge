package com.foggyframework.dataviewer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqliteQueryContextStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsDslAcrossReopenAndExtendsOnlyMatchingLiveQuery() {
        Path database = tempDir.resolve("viewer.sqlite");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Instant now = Instant.now();
        String id = "a".repeat(32);
        CachedQueryContext context = CachedQueryContext.builder()
                .queryId(id).model("DemoWaybillQueryModel").namespace("demo")
                .columns(List.of("businessDate", "waybillCount"))
                .slice(List.of(new SliceRequestDef("businessDate", "=", "2026-09-29")))
                .having(List.of(new SliceRequestDef("waybillCount", "[]", List.of(1, 2))))
                .extData(Map.of("source", "demo"))
                .createdAt(now).expiresAt(now.plusSeconds(60)).build();
        new SqliteQueryContextStore(database, mapper, 1000).save(context);

        SqliteQueryContextStore reopened = new SqliteQueryContextStore(database, mapper, 1000);
        assertFalse(reopened.extendExpiry(id, context.getModel(), "other", now, now.plusSeconds(7200)));
        assertTrue(reopened.extendExpiry(id, context.getModel(), "demo", now, now.plusSeconds(7200)));
        reopened.updateEstimatedRowCount(id, 4L, now);
        CachedQueryContext restored = reopened.findActive(id, now.plusSeconds(61)).orElseThrow();
        assertEquals("businessDate", restored.toDbQueryRequestDef().getSlice().get(0).getField());
        assertEquals("waybillCount", restored.toDbQueryRequestDef().getHaving().get(0).getField());
        assertEquals(Map.of("source", "demo"), restored.getExtData());
        assertEquals(4L, restored.getEstimatedRowCount());
        assertTrue(reopened.findActive(id, now.plusSeconds(7201)).isEmpty());
        assertFalse(reopened.extendExpiry(id, context.getModel(), "demo", now.plusSeconds(7201),
                now.plusSeconds(9000)));
    }
}
