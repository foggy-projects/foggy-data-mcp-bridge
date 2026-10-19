package com.foggyframework.dataset.model.semantic;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.engine.compose.runtime.ComposeRuntimeBundle;
import com.foggyframework.dataset.model.engine.compose.runtime.ComposeRuntimeHolder;
import com.foggyframework.dataset.model.engine.compose.runtime.ScriptRuntime;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.spi.NamedDataSourceResolver;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.test.JdbcModelTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Issue #139: real model loading, semantic compilation and JDBC execution. */
@SpringBootTest(classes = {JdbcModelTestApplication.class, TimeWindowNamespaceRoutingIT.RoutingConfiguration.class})
@ActiveProfiles("sqlite")
class TimeWindowNamespaceRoutingIT {
    private static final String MODEL = "Issue139InvoiceQueryModel";
    private static final String TABLE = "issue139_invoice";
    private static final List<String> NAMESPACES = List.of("", "issue139-a", "issue139-b");

    @Autowired SemanticQueryServiceV3 service;
    @Autowired QueryModelLoader loader;
    @Autowired SystemBundlesContext bundles;
    @Autowired DataSource defaultDataSource;
    @Autowired RoutingResolver resolver;
    @TempDir Path directory;

    @BeforeEach
    void prepare() throws Exception {
        assertNull(ComposeRuntimeHolder.currentBundle());
        new JdbcTemplate(defaultDataSource).execute("DROP TABLE IF EXISTS " + TABLE);
        resolver.sources.clear();
        for (String namespace : NAMESPACES) {
            loader.clearByNamespace(namespace);
            if (!namespace.isEmpty()) {
                SQLiteDataSource source = new SQLiteDataSource();
                source.setUrl("jdbc:sqlite:" + directory.resolve(namespace + ".db"));
                resolver.sources.put(namespace, source);
                createInvoices(source, namespace.endsWith("a") ? 10 : 100);
            }
            Path root = directory.resolve(bundleName(namespace));
            Files.createDirectories(root.resolve("model"));
            Files.createDirectories(root.resolve("query"));
            Files.writeString(root.resolve("model/Issue139InvoiceTableModel.tm"), """
                    export const model = {
                      name: 'Issue139InvoiceTableModel', tableName: 'issue139_invoice', idColumn: 'id',
                      properties: [{column: 'id', name: 'id', type: 'INTEGER'},
                                   {column: 'origin', name: 'origin', type: 'STRING'}],
                      dimensions: [{name: 'invoiceDate', foreignKey: 'invoice_date',
                        primaryKey: 'invoice_date', captionColumn: 'invoice_date',
                        type: 'DATETIME', timeRole: 'business_date'}],
                      measures: [{column: 'amount', name: 'amount', type: 'NUMBER', aggregation: 'sum'}]
                    };
                    """);
            Files.writeString(root.resolve("query").resolve(MODEL + ".qm"), """
                    const invoice = loadTableModel('Issue139InvoiceTableModel');
                    export const queryModel = {
                      name: 'Issue139InvoiceQueryModel', model: invoice,
                      columnGroups: [{caption: 'Invoice', items: [
                        {ref: invoice.id}, {ref: invoice.origin},
                        {ref: invoice.invoiceDate}, {ref: invoice.amount}]}], accesses: []
                    };
                    """);
            assertTrue(bundles.addExternalBundle(bundleName(namespace), namespace, root.toString(), false));
        }
    }

    @AfterEach
    void clean() {
        for (String namespace : NAMESPACES) {
            if (bundles.containBundle(bundleName(namespace))) {
                bundles.removeBundle(bundleName(namespace));
            }
            loader.clearByNamespace(namespace);
        }
        new JdbcTemplate(defaultDataSource).execute("DROP TABLE IF EXISTS " + TABLE);
        resolver.sources.clear();
        assertNull(ComposeRuntimeHolder.currentBundle());
    }

    @Test
    void namedTimeWindowExecutesWhenDefaultDatabaseHasNoTable() {
        assertEquals(0, new JdbcTemplate(defaultDataSource).queryForObject(
                "SELECT count(*) FROM sqlite_master WHERE name = ?", Integer.class, TABLE));
        assertInvoices(execute("issue139-a", true), "A", 10, true);
        assertInvoices(execute("issue139-a", false), "A", 10, false);
    }

    @Test
    void sameNamedModelsAndTablesUseTheirOwnNamespaceData() {
        createInvoices(defaultDataSource, 1000);
        // Alternate namespaces to exercise both cold and cached model resolution.
        for (String namespace : List.of("issue139-a", "issue139-b", "", "issue139-a")) {
            int amount = namespace.isEmpty() ? 1000 : namespace.endsWith("a") ? 10 : 100;
            String origin = amount == 10 ? "A" : amount == 100 ? "B" : "DEFAULT";
            assertInvoices(execute(namespace, true), origin, amount, true);
            assertInvoices(execute(namespace, false), origin, amount, false);
            assertInvoices(compose(namespace), origin, amount, false);
        }
    }

    @Test
    void explicitTimeWindowNamespaceOverridesUnrelatedAmbientComposeNamespace() {
        createInvoices(defaultDataSource, 1000);
        ComposeRuntimeBundle outer = ComposeRuntimeBundle.builder()
                .ctx(composeContext("issue139-b")).semanticService(service).dialect("sqlite").build();
        ComposeRuntimeHolder.Token token = ComposeRuntimeHolder.setBundle(outer);
        try {
            assertInvoices(execute("issue139-a", true), "A", 10, true);
            assertInvoices(execute("", true), "DEFAULT", 1000, true);
            assertSame(outer, ComposeRuntimeHolder.currentBundle());
        } finally {
            ComposeRuntimeHolder.popBundle(token);
        }
    }

    @Test
    void wrongNamespaceAndUnknownModelFailEvenWhenDefaultHasMatchingTable() {
        createInvoices(defaultDataSource, 1000);
        for (boolean timeWindow : List.of(false, true)) {
            assertThrows(RuntimeException.class, () -> execute("issue139-typo", timeWindow));
            assertThrows(RuntimeException.class, () -> service.queryModel("Issue139MissingModel",
                    request(timeWindow), "execute", SemanticRequestContext.ofNamespace("issue139-a")));
        }
        RuntimeException routeError = assertThrows(RuntimeException.class, () -> service.executeSql(
                "SELECT * FROM " + TABLE, List.of(), MODEL, "issue139-typo"));
        assertTrue(routeError.getMessage().contains("route resolution failed"), routeError.getMessage());
        assertTrue(routeError.getMessage().contains("issue139-typo"), routeError.getMessage());
        assertThrows(RuntimeException.class, () -> compose("issue139-typo"));
    }

    @Test
    void legacyRawSqlStillRoutesInsideComposeAndUnroutedSqlUsesDefault() {
        createInvoices(defaultDataSource, 1000);
        String sql = "SELECT origin, amount FROM " + TABLE + " WHERE id = ?";
        assertEquals("DEFAULT", service.executeSql(sql, List.of(1), null).get(0).get("origin"));
        ComposeRuntimeHolder.Token token = ComposeRuntimeHolder.setBundle(ComposeRuntimeBundle.builder()
                .ctx(composeContext("issue139-b")).semanticService(service).dialect("sqlite").build());
        try {
            Map<String, Object> row = service.executeSql(sql, List.of(1), MODEL).get(0);
            assertEquals("B", row.get("origin"));
            assertNumber(100, row.get("amount"));
            assertThrows(RuntimeException.class, () -> service.executeSql(sql, List.of(1), "MissingModel"));
        } finally {
            ComposeRuntimeHolder.popBundle(token);
        }
    }

    private List<Map<String, Object>> execute(String namespace, boolean timeWindow) {
        return service.queryModel(MODEL, request(timeWindow), "execute",
                SemanticRequestContext.ofNamespace(namespace)).getItems();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> compose(String namespace) {
        return (List<Map<String, Object>>) ScriptRuntime.runScript("""
                return dsl({model: 'Issue139InvoiceQueryModel',
                  columns: ['invoiceDate$id', 'origin', 'amount'],
                  groupBy: ['invoiceDate$id', 'origin'], orderBy: ['invoiceDate$id']});
                """, composeContext(namespace), service, "sqlite").value();
    }

    private static SemanticQueryRequest request(boolean timeWindow) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(timeWindow
                ? List.of("invoiceDate$id", "origin", "amount", "amount__rolling_7d")
                : List.of("invoiceDate$id", "origin", "amount"));
        request.setGroupBy(List.of("invoiceDate$id", "origin").stream().map(field -> {
            SemanticQueryRequest.GroupByItem item = new SemanticQueryRequest.GroupByItem();
            item.setField(field);
            return item;
        }).toList());
        SemanticQueryRequest.OrderItem order = new SemanticQueryRequest.OrderItem();
        order.setField("invoiceDate$id");
        order.setDir("asc");
        request.setOrderBy(List.of(order));
        if (timeWindow) {
            request.setTimeWindow(Map.of("field", "invoiceDate$id", "grain", "day",
                    "comparison", "rolling_7d", "targetMetrics", List.of("amount")));
        }
        return request;
    }

    private static ComposeQueryContext composeContext(String namespace) {
        return ComposeQueryContext.builder().namespace(namespace)
                .principal(Principal.builder().userId("issue139-test").build())
                .authorityResolver(request -> {
                    Map<String, ModelBinding> bindings = new LinkedHashMap<>();
                    request.modelNames().forEach(name -> bindings.put(name, ModelBinding.builder().build()));
                    return AuthorityResolution.builder().bindings(bindings).build();
                }).build();
    }

    private static void createInvoices(DataSource source, int firstAmount) {
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE " + TABLE
                + " (id INTEGER PRIMARY KEY, invoice_date TEXT, origin TEXT, amount NUMERIC)");
        String origin = firstAmount == 10 ? "A" : firstAmount == 100 ? "B" : "DEFAULT";
        jdbc.update("INSERT INTO " + TABLE + " VALUES (1, '2024-01-01', ?, ?)", origin, firstAmount);
        jdbc.update("INSERT INTO " + TABLE + " VALUES (2, '2024-01-02', ?, ?)", origin, firstAmount * 2);
    }

    private static void assertInvoices(List<Map<String, Object>> rows, String origin, int amount, boolean window) {
        assertEquals(2, rows.size(), rows.toString());
        for (int i = 0; i < 2; i++) {
            assertEquals(origin, rows.get(i).get("origin"));
            assertNumber(amount * (i + 1), rows.get(i).get("amount"));
            if (window) assertNumber(i == 0 ? amount : amount * 3, rows.get(i).get("amount__rolling_7d"));
        }
    }

    private static void assertNumber(int expected, Object actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));
    }

    private static String bundleName(String namespace) {
        return namespace.isEmpty() ? "issue139-default-bundle" : namespace + "-bundle";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RoutingConfiguration {
        @Bean @Primary RoutingResolver issue139RoutingResolver() { return new RoutingResolver(); }
    }

    static class RoutingResolver implements NamedDataSourceResolver {
        final Map<String, DataSource> sources = new LinkedHashMap<>();
        @Override public DataSource resolve(String name) { return sources.get(name); }
        @Override public DataSource resolveDefault(String namespace) { return sources.get(namespace); }
        @Override public boolean isConfigured(String name) { return sources.containsKey(name); }
    }
}
