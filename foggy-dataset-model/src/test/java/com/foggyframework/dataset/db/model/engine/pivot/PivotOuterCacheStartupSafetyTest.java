package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.test.JdbcModelTestApplication;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = JdbcModelTestApplication.class, properties = {
        "foggy.dataset.pivot.outer-cache.enabled=true",
        "foggy.dataset.pivot.outer-cache.fail-on-provider-unavailable=false"
})
@DisplayName("Pivot outer-cache startup safety")
class PivotOuterCacheStartupSafetyTest {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private DatasetProperties datasetProperties;

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Test
    @DisplayName("enabled outer-cache does not require Redis or an external provider bean")
    void testEnabledOuterCacheDoesNotRequireExternalProvider() {
        assertTrue(datasetProperties.getPivot().getOuterCache().isEnabled());
        assertTrue(applicationContext.getBeansOfType(PivotOuterCacheProvider.class).isEmpty());
        assertDoesNotThrow(() -> assertEquals(0, semanticQueryServiceV3.evictPivotOuterCache("ns-a", "SalesQM")));
    }
}
