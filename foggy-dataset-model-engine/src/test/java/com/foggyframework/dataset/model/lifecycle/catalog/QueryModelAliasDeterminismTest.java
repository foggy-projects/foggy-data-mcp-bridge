package com.foggyframework.dataset.model.lifecycle.catalog;

import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.test.JdbcModelTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Regression coverage for discovery-first deterministic short aliases. */
@SpringBootTest(classes = JdbcModelTestApplication.class)
@ActiveProfiles("sqlite")
class QueryModelAliasDeterminismTest {

    private static final String CHANNEL_MODEL = "DimChannelQueryModel";
    private static final String CUSTOMER_MODEL = "DimCustomerQueryModel";

    @Autowired
    private QueryModelLoader queryModelLoader;

    @Autowired
    private CatalogSnapshotStore catalogSnapshotStore;

    @Test
    void collidingAliasesMustBeStableAcrossForwardAndReverseLoadOrder() {
        try {
            queryModelLoader.clearByNamespace(null);
            Map<String, String> forward = loadAliases(CHANNEL_MODEL, CUSTOMER_MODEL);
            CatalogGeneration forwardGeneration = catalogSnapshotStore.current("")
                    .orElseThrow().identity().generation();

            QueryModel channel = queryModelLoader.getJdbcQueryModel(CHANNEL_MODEL, null);
            QueryModel byAlias = queryModelLoader.getJdbcQueryModel(forward.get(CHANNEL_MODEL), null);
            assertSame(channel, byAlias, "alias must resolve inside the same committed snapshot");
            assertEquals(forwardGeneration, catalogSnapshotStore.current("")
                    .orElseThrow().identity().generation(), "plain reads must not advance generation");

            queryModelLoader.clearByNamespace(null);
            Map<String, String> reverse = loadAliases(CUSTOMER_MODEL, CHANNEL_MODEL);

            assertEquals(forward, reverse,
                    () -> "canonical-to-alias map must be order-independent; forward="
                            + forward + ", reverse=" + reverse);
        } finally {
            queryModelLoader.clearByNamespace(null);
        }
    }

    private Map<String, String> loadAliases(String first, String second) {
        Map<String, String> aliases = new LinkedHashMap<>();
        loadAlias(first, aliases);
        loadAlias(second, aliases);
        return aliases;
    }

    private void loadAlias(String canonicalName, Map<String, String> aliases) {
        QueryModel queryModel = queryModelLoader.getJdbcQueryModel(canonicalName, null);
        assertNotNull(queryModel, () -> "query model must load: " + canonicalName);
        assertNotNull(queryModel.getShortAlias(), () -> "query model alias must exist: " + canonicalName);
        aliases.put(canonicalName, queryModel.getShortAlias());
    }
}
