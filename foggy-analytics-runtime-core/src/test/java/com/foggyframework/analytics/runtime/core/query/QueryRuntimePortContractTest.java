package com.foggyframework.analytics.runtime.core.query;

import com.foggyframework.analytics.definition.api.AnalyticsColumnSchema;
import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.definition.api.AnalyticsQueryRef;
import com.foggyframework.analytics.definition.api.AnalyticsQuerySpec;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryRuntimePortContractTest {

    @Test
    void opaqueBindingIsResolvedBeforeExecution() {
        QueryAuthorityResolver<ResolvedAuthority> resolver = request ->
                new ResolvedAuthority(request.binding().reference());
        AnalyticsModelDependency dependency = new AnalyticsModelDependency(
                new AnalyticsNamespaceRef("default"),
                "qm",
                "SalesOrder",
                AnalyticsModelRevision.fromSha256Hex("a".repeat(64)));
        QueryAuthorityRequest authorityRequest = new QueryAuthorityRequest(
                dependency,
                new QueryAuthorityBinding("host", "authority-42"),
                "request-1",
                "trace-1");
        ResolvedAuthority authority = resolver.resolve(authorityRequest);
        AnalyticsQuerySpec query = new AnalyticsQuerySpec(
                new AnalyticsQueryRef("sales"),
                authorityRequest.modelDependency().namespace(),
                "SalesOrder",
                List.of("amount"),
                List.of());
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("optionalRegion", null);
        QueryExecutionContext<ResolvedAuthority> context = new QueryExecutionContext<>(
                query,
                dependency,
                parameters,
                100,
                ZoneId.of("Asia/Shanghai"),
                Locale.SIMPLIFIED_CHINESE,
                authorityRequest.requestId(),
                authorityRequest.traceId(),
                authority);
        Map<String, Object> nullableRow = new LinkedHashMap<>();
        nullableRow.put("amount", null);
        QueryExecutor<ResolvedAuthority> executor = execution -> new QueryExecutionResult(
                List.of(new AnalyticsColumnSchema("amount", "decimal", false)),
                List.of(nullableRow),
                false,
                List.of("authority=" + execution.authority().reference()));

        QueryExecutionResult result = executor.execute(context);

        assertEquals("authority=authority-42", result.diagnostics().get(0));
        assertNull(context.parameters().get("optionalRegion"));
        assertEquals(100, context.rowLimit());
        assertNull(result.rows().get(0).get("amount"));
        assertEquals(
                dependency.modelRevision(),
                context.modelDependency().modelRevision());
        assertThrows(UnsupportedOperationException.class,
                () -> context.parameters().put("rawSql", "select 1"));
        AnalyticsModelDependency otherModel = new AnalyticsModelDependency(
                dependency.namespace(),
                "qm",
                "OtherModel",
                dependency.modelRevision());
        assertThrows(IllegalArgumentException.class, () -> new QueryExecutionContext<>(
                query,
                otherModel,
                parameters,
                100,
                ZoneId.of("Asia/Shanghai"),
                Locale.SIMPLIFIED_CHINESE,
                authorityRequest.requestId(),
                authorityRequest.traceId(),
                authority));
    }

    private record ResolvedAuthority(String reference) {
    }
}
