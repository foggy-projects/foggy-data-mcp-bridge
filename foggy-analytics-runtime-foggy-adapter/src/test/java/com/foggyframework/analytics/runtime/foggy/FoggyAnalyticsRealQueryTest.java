package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelDigest;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.definition.api.AnalyticsQueryRef;
import com.foggyframework.analytics.definition.api.AnalyticsQuerySpec;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityBinding;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityRequest;
import com.foggyframework.analytics.runtime.core.query.QueryExecutionContext;
import com.foggyframework.analytics.runtime.core.query.QueryExecutionResult;
import com.foggyframework.core.annotates.EnableFoggyFramework;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.port.SemanticModelCatalogReadPort;
import com.foggyframework.dataset.model.semantic.port.SemanticQueryExecutionPort;
import com.foggyframework.dataset.model.spi.NamedDataSourceResolver;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = FoggyAnalyticsRealQueryTest.TestApplication.class)
@ActiveProfiles("analytics-adapter")
class FoggyAnalyticsRealQueryTest {

    private static final String MODEL = "AnalyticsSalesQueryModel";

    @Resource
    private SemanticModelCatalogReadPort catalogReadPort;

    @Resource
    private SemanticQueryExecutionPort queryExecutionPort;

    @Resource
    private CatalogSnapshotStore catalogSnapshotStore;

    @Test
    void executesMinimalRealTmQmAgainstSqliteThroughExactCatalogPin() {
        var catalogView = catalogReadPort.namespaceCatalogView("");
        FoggyStableModelDigestReadPort digestReadPort =
                new FoggyCatalogStableModelDigestReadPort(
                        catalogSnapshotStore);
        AnalyticsModelDigest modelDigest = digestReadPort.findDigest(
                        new FoggyModelDigestLookup(
                                catalogView.identity(),
                                "qm",
                                MODEL))
                .orElseThrow();
        AnalyticsModelDependency dependency = new AnalyticsModelDependency(
                new AnalyticsNamespaceRef("default"),
                "qm",
                MODEL,
                modelDigest);
        FoggyQueryAuthorityResolver authorityResolver = new FoggyQueryAuthorityResolver(
                catalogReadPort,
                digestReadPort,
                (request, resolution) -> SemanticRequestContext.ofNamespace(""));
        FoggyAnalyticsAuthority authority = authorityResolver.resolve(
                new QueryAuthorityRequest(
                        dependency,
                        new QueryAuthorityBinding("test", "anonymous-read"),
                        "request-real-1",
                        "trace-real-1"));
        AnalyticsQuerySpec querySpec = new AnalyticsQuerySpec(
                new AnalyticsQueryRef("sales-by-region"),
                dependency.namespace(),
                MODEL,
                List.of("region", "amount"),
                List.of("region"));
        FoggyAnalyticsQueryExecutor executor = new FoggyAnalyticsQueryExecutor(
                queryExecutionPort);

        QueryExecutionResult result = executor.execute(new QueryExecutionContext<>(
                querySpec,
                dependency,
                Map.of(),
                10,
                ZoneId.of("Asia/Shanghai"),
                Locale.SIMPLIFIED_CHINESE,
                "request-real-1",
                "trace-real-1",
                authority));

        assertEquals("", authority.engineNamespace());
        assertEquals(authority.catalogIdentity(),
                authority.semanticRequestContext().getCatalogResolution().catalogIdentity());
        assertEquals(List.of("string", "decimal"), result.columns().stream()
                .map(column -> column.type())
                .toList());
        assertEquals(
                Map.of("east", "16", "west", "7"),
                amountsByRegion(result.rows()));
        assertFalse(result.truncated());
        assertTrue(result.diagnostics().isEmpty());
    }

    private static Map<String, String> amountsByRegion(List<Map<String, Object>> rows) {
        Map<String, String> amounts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            amounts.put(
                    String.valueOf(row.get("region")),
                    new BigDecimal(String.valueOf(row.get("amount")))
                            .stripTrailingZeros()
                            .toPlainString());
        }
        return amounts;
    }

    @SpringBootApplication
    @EnableFoggyFramework(bundleName = "foggy-analytics-adapter-test")
    static class TestApplication {

        @Bean
        NamedDataSourceResolver analyticsNamedDataSourceResolver(DataSource dataSource) {
            return new NamedDataSourceResolver() {
                @Override
                public DataSource resolve(String name) {
                    return null;
                }

                @Override
                public boolean isConfigured(String name) {
                    return false;
                }
            };
        }
    }
}
