package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.def.dict.DbDictionaryDiscoveryDef;
import com.foggyframework.dataset.db.model.semantic.domain.DictionaryDiscoveryResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.DictionaryDiscoveryService;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticServiceV3Impl;
import com.foggyframework.dataset.db.model.spi.DbProperty;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DictionaryDiscoveryMetadataFailureTest {

    @Test
    void failedDictionaryDiscoveryDoesNotExposeRawRuntimeErrorToLlmMetadata() {
        SemanticServiceV3Impl service = new SemanticServiceV3Impl();
        DictionaryDiscoveryService discoveryService = mock(DictionaryDiscoveryService.class);
        ReflectionTestUtils.setField(service, "dictionaryDiscoveryService", discoveryService);

        DbDictionaryDiscoveryDef discovery = new DbDictionaryDiscoveryDef();
        discovery.setEnabled(true);

        DbProperty property = mock(DbProperty.class);
        when(property.getDictionaryDiscovery()).thenReturn(discovery);
        when(discoveryService.discover(eq("OrderQueryModel"), eq("status"), same(discovery),
                any(SemanticRequestContext.class)))
                .thenReturn(DictionaryDiscoveryResult.failed("physical table fact_order is unavailable"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = ReflectionTestUtils.invokeMethod(service,
                "buildDictionaryDiscoveryMetadata", property, "OrderQueryModel", "status",
                SemanticRequestContext.empty());

        assertNotNull(metadata);
        assertEquals(DictionaryDiscoveryResult.STATUS_FAILED, metadata.get("valuesStatus"));
        assertEquals("runtime dictionary discovery failed", metadata.get("error"));
        assertFalse(metadata.toString().contains("fact_order"));
        assertFalse(metadata.toString().contains("physical table"));
    }
}
