package io.foggytest.pivot;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.runtime.*;
import com.foggyframework.dataset.model.spi.NamedDataSourceResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PivotNamespaceIsolationTest {
    @TempDir Path root;

    @Test
    void observePublishedAuxiliaryDatasourceRouting() throws Exception {
        Map<String, DataSource> sources = new HashMap<>();
        for (String ns : List.of("tenant-a", "tenant-b")) {
            var s = new SQLiteDataSource();
            s.setUrl("jdbc:sqlite:" + root.resolve(ns + ".sqlite"));
            sources.put(ns, s);
        }
        NamedDataSourceResolver resolver =
                new NamedDataSourceResolver() {
                    public DataSource resolve(String name) {
                        return sources.get(name);
                    }

                    public DataSource resolveDefault(String namespace) {
                        return sources.get(namespace);
                    }

                    public boolean isConfigured(String name) {
                        return sources.containsKey(name);
                    }
                };
        new WebApplicationContextRunner()
                .withUserConfiguration(AuditApplication.class)
                .withBean("auditNamedResolver", NamedDataSourceResolver.class, () -> resolver)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:sqlite:" + root.resolve("default.sqlite"),
                        "spring.datasource.driver-class-name=org.sqlite.JDBC",
                        "foggy.data-viewer.enabled=false",
                        "foggy.runtime-api.enabled=false")
                .run(
                        ctx -> {
                            assertNull(ctx.getStartupFailure());
                            var bundles = ctx.getBean(SystemBundlesContext.class);
                            var service = ctx.getBean(SemanticQueryServiceV3.class);
                            var mapper = ctx.getBean(SemanticQueryPayloadMapper.class);
                            sources.put("", ctx.getBean(DataSource.class));
                            Map<String, Object> report = new LinkedHashMap<>();
                            try {
                                for (String ns : List.of("", "tenant-a", "tenant-b")) {
                                    int unit = ns.isEmpty() ? 1000 : ns.endsWith("a") ? 10 : 100;
                                    var jdbc = new JdbcTemplate(sources.get(ns));
                                    jdbc.execute(
                                            "CREATE TABLE audit_route(id INTEGER,category TEXT,amount NUMERIC)");
                                    jdbc.update(
                                            "INSERT INTO audit_route VALUES(1,'X',?),(2,'X',?),(3,'Y',?)",
                                            unit,
                                            unit * 2,
                                            unit * 3);
                                    Path models =
                                            Files.createDirectory(
                                                    root.resolve(
                                                            ns.isEmpty() ? "default-models" : ns));
                                    Files.writeString(
                                            models.resolve("AuditRoute.tm"),
                                            "export const model={name:'AuditRoute',tableName:'audit_route',idColumn:'id',properties:[{name:'id',column:'id',type:'INTEGER'},{name:'category',column:'category',type:'STRING'}],measures:[{name:'average',column:'amount',type:'NUMBER',aggregation:'avg'}]};");
                                    Files.writeString(
                                            models.resolve("AuditRouteQM.qm"),
                                            "const m=loadTableModel('AuditRoute');export const queryModel={name:'AuditRouteQM',model:m,loader:'v2',columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.category},{ref:m.average}]}],accesses:[]};");
                                    Files.writeString(
                                            models.resolve("AuditRouteFiltered.qm"),
                                            "const m=loadTableModel('AuditRoute');export const queryModel={name:'AuditRouteFiltered',model:m,loader:'v2',columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.category},{ref:m.average}]}],accesses:[],modelPermissions:{mode:'resolver',resolver:(ctx)=>{return {allow:true,rowPredicates:[ctx.predicate.eq('category','X')]};}}};");
                                    assertTrue(
                                            bundles.addExternalBundle(
                                                    "audit-route-"
                                                            + (ns.isEmpty() ? "default" : ns),
                                                    ns,
                                                    models.toString(),
                                                    false));
                                }
                                for (boolean total : List.of(false, true)) {
                                    var req =
                                            mapper.toQueryRequest(
                                                    Map.of(
                                                            "pivot",
                                                            Map.of(
                                                                    "rows",
                                                                    List.of("category"),
                                                                    "metrics",
                                                                    List.of("average"),
                                                                    "outputFormat",
                                                                    "flat",
                                                                    "options",
                                                                    Map.of("grandTotal", total))));
                                    assertNotNull(
                                            service.validateQuery(
                                                    "AuditRouteQM",
                                                    req,
                                                    SemanticRequestContext.ofNamespace(
                                                            "tenant-a")));
                                    report.put(
                                            total ? "tenantAWithTotal" : "tenantANoTotal",
                                            service.queryModel(
                                                    "AuditRouteQM",
                                                    req,
                                                    "execute",
                                                    SemanticRequestContext.ofNamespace(
                                                            "tenant-a")));
                                }
                                var filteredRequest =
                                        mapper.toQueryRequest(
                                                Map.of(
                                                        "pivot",
                                                        Map.of(
                                                                "rows",
                                                                List.of("category"),
                                                                "metrics",
                                                                List.of("average"),
                                                                "outputFormat",
                                                                "flat",
                                                                "options",
                                                                Map.of("grandTotal", true))));
                                assertNotNull(
                                        service.validateQuery(
                                                "AuditRouteFiltered",
                                                filteredRequest,
                                                SemanticRequestContext.ofNamespace("tenant-a")));
                                var filtered =
                                        service.queryModel(
                                                "AuditRouteFiltered",
                                                filteredRequest,
                                                "execute",
                                                SemanticRequestContext.ofNamespace("tenant-a"));
                                for (var row : filtered.getItems())
                                    assertEquals(
                                            0,
                                            new java.math.BigDecimal("15")
                                                    .compareTo(
                                                            new java.math.BigDecimal(
                                                                    row.get("average")
                                                                            .toString())));
                                report.put("filteredTenantA", filtered);
                                var denied =
                                        SemanticRequestContext.ofDeniedColumns(
                                                "tenant-a",
                                                null,
                                                List.of(
                                                        new com.foggyframework.dataset.model
                                                                .semantic.domain
                                                                .DeniedPhysicalColumn(
                                                                null, "audit_route", "amount")));
                                assertThrows(
                                        RuntimeException.class,
                                        () ->
                                                service.validateQuery(
                                                        "AuditRouteQM", filteredRequest, denied));
                                assertThrows(
                                        RuntimeException.class,
                                        () ->
                                                service.queryModel(
                                                        "AuditRouteQM",
                                                        filteredRequest,
                                                        "execute",
                                                        denied));
                                var outer =
                                        ComposeRuntimeBundle.builder()
                                                .ctx(
                                                        ComposeQueryContext.builder()
                                                                .namespace("tenant-b")
                                                                .principal(
                                                                        com.foggyframework.dataset
                                                                                .model.engine
                                                                                .compose.context
                                                                                .Principal.builder()
                                                                                .userId("audit")
                                                                                .build())
                                                                .authorityResolver(
                                                                        req ->
                                                                                com.foggyframework
                                                                                        .dataset
                                                                                        .model
                                                                                        .engine
                                                                                        .compose
                                                                                        .security
                                                                                        .AuthorityResolution
                                                                                        .builder()
                                                                                        .bindings(
                                                                                                Map
                                                                                                        .of())
                                                                                        .build())
                                                                .build())
                                                .semanticService(service)
                                                .dialect("sqlite")
                                                .build();
                                var token = ComposeRuntimeHolder.setBundle(outer);
                                try {
                                    var req =
                                            mapper.toQueryRequest(
                                                    Map.of(
                                                            "pivot",
                                                            Map.of(
                                                                    "rows",
                                                                    List.of("category"),
                                                                    "metrics",
                                                                    List.of("average"),
                                                                    "outputFormat",
                                                                    "flat",
                                                                    "options",
                                                                    Map.of("grandTotal", true))));
                                    report.put(
                                            "tenantAUnderAmbientB",
                                            service.queryModel(
                                                    "AuditRouteQM",
                                                    req,
                                                    "execute",
                                                    SemanticRequestContext.ofNamespace(
                                                            "tenant-a")));
                                    assertSame(outer, ComposeRuntimeHolder.currentBundle());
                                } finally {
                                    ComposeRuntimeHolder.popBundle(token);
                                }
                                for (String key :
                                        List.of("tenantAWithTotal", "tenantAUnderAmbientB")) {
                                    var response =
                                            (com.foggyframework.dataset.model.semantic.domain
                                                            .SemanticQueryResponse)
                                                    report.get(key);
                                    var grand =
                                            response.getItems().stream()
                                                    .filter(
                                                            r ->
                                                                    "GRAND_TOTAL"
                                                                            .equals(
                                                                                    r.get(
                                                                                            "category")))
                                                    .findFirst()
                                                    .orElseThrow();
                                    assertEquals(
                                            0,
                                            new java.math.BigDecimal("20")
                                                    .compareTo(
                                                            new java.math.BigDecimal(
                                                                    grand.get("average")
                                                                            .toString())),
                                            key);
                                }
                                String evidence = System.getenv("FOGGY_TIMEAXIS_EVIDENCE");
                                if (evidence != null) {
                                    Path out = Path.of(evidence);
                                    Files.createDirectories(out);
                                    new ObjectMapper()
                                            .writerWithDefaultPrettyPrinter()
                                            .writeValue(
                                                    out.resolve("namespace-observation.json")
                                                            .toFile(),
                                                    report);
                                }
                                System.out.println(
                                        "AUDIT_NAMESPACE_OBSERVATION "
                                                + new ObjectMapper().writeValueAsString(report));
                            } finally {
                                for (String ns : List.of("", "tenant-a", "tenant-b"))
                                    bundles.removeBundle(
                                            "audit-route-" + (ns.isEmpty() ? "default" : ns));
                            }
                        });
    }

    @Test
    void boundBatchNeverResolvesAnAmbientModelOrPrimaryDatasource() throws Exception {
        var source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + root.resolve("pinned.sqlite"));
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE marker(origin TEXT)");
        jdbc.update("INSERT INTO marker VALUES('PINNED')");
        var service =
                new com.foggyframework.dataset.model.semantic.service.impl
                        .SemanticQueryServiceV3Impl();
        var loader =
                org.mockito.Mockito.mock(
                        com.foggyframework.dataset.model.spi.QueryModelLoader.class);
        var primary = org.mockito.Mockito.mock(DataSource.class);
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "queryModelLoader", loader);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "dataSource", primary);
        var binding =
                org.mockito.Mockito.mock(com.foggyframework.dataset.model.spi.JdbcQueryModel.class);
        org.mockito.Mockito.when(binding.getDataSource()).thenReturn(source);
        var result =
                service.executeBoundRollupSql(
                        "SELECT origin FROM marker",
                        List.of(),
                        "missing-route",
                        SemanticRequestContext.ofNamespace("tenant-a"),
                        binding);
        assertEquals("PINNED", result.get(0).get("origin"));
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        service.executeBoundRollupSql(
                                "SELECT origin FROM marker",
                                List.of(),
                                "missing-route",
                                SemanticRequestContext.empty(),
                                null));
        org.mockito.Mockito.verifyNoInteractions(loader, primary);
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(
            excludeName = {
                "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
            })
    static class AuditApplication {}
}
