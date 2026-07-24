package com.foggyframework.dataset.db.model.lifecycle.namespace;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.plugins.query_execution.ManagedRelationOptions;
import com.foggyframework.dataset.db.model.plugins.query_execution.ManagedSqlRelation;
import com.foggyframework.dataset.db.model.plugins.query_execution.QueryExecutionContext;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultFilterManager;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.service.impl.QueryFacadeImpl;
import com.foggyframework.dataset.db.model.spi.NamespaceContext;
import com.foggyframework.dataset.db.model.spi.NamespaceScope;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.api.QueryFacadeRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NamespaceProductionEntryRestorationTest {

    private static final String MODEL_NAME = "NamespaceRestorationProbe";
    private static final String OUTER_NAMESPACE = "outer-a";

    @AfterEach
    void clearNamespace() {
        NamespaceContext.clear();
    }

    @Test
    void namedQueryEntryMasksThenRestoresOuterNamespaceOnNormalReturn() {
        AtomicReference<String> namespaceSeenInside = new AtomicReference<>();
        QueryFacadeImpl facade = facadeThatSkipsPhysicalQuery("inner-b", namespaceSeenInside);

        try (NamespaceScope ignored = NamespaceContext.open(OUTER_NAMESPACE)) {
            facade.queryModelResult(context("inner-b"));

            assertAll(
                    () -> assertEquals("inner-b", namespaceSeenInside.get(),
                            "named inner entry must mask the outer namespace"),
                    () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                            "normal return must restore the named outer namespace")
            );
        }

        assertNull(NamespaceContext.getNamespace(), "closing the root scope must restore unset");
    }

    @Test
    void namedQueryEntryRestoresOuterNamespaceWhenLoaderThrows() {
        AtomicReference<String> namespaceSeenInside = new AtomicReference<>();
        IllegalStateException marker = new IllegalStateException("controlled query-model load failure");
        QueryModelLoader loader = throwingLoader("inner-error", namespaceSeenInside, marker);
        QueryFacadeImpl facade = facade(loader, mock(DataSetResultFilterManager.class));

        try (NamespaceScope ignored = NamespaceContext.open(OUTER_NAMESPACE)) {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> facade.queryModelResult(context("inner-error"))
            );

            assertAll(
                    () -> assertSame(marker, thrown, "the controlled failure must be preserved"),
                    () -> assertEquals("inner-error", namespaceSeenInside.get(),
                            "exceptional inner entry must mask the outer namespace"),
                    () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                            "exceptional return must restore the named outer namespace")
            );
        }
    }

    @Test
    void explicitDefaultQueryEntryMasksThenRestoresOuterNamespace() {
        AtomicReference<String> namespaceSeenInside = new AtomicReference<>();
        QueryFacadeImpl facade = facadeThatSkipsPhysicalQuery("", namespaceSeenInside);

        try (NamespaceScope ignored = NamespaceContext.open(OUTER_NAMESPACE)) {
            facade.queryModelResult(context(""));

            assertAll(
                    () -> assertEquals("", namespaceSeenInside.get(),
                            "explicit default must not inherit the named outer namespace"),
                    () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                            "closing explicit default must restore the named outer namespace")
            );
        }
    }

    @Test
    void explicitNullContextMeansDefaultRatherThanInheritedNamespace() {
        AtomicReference<String> namespaceSeenInside = new AtomicReference<>();
        QueryFacadeImpl facade = facadeThatSkipsPhysicalQuery("", namespaceSeenInside);

        try (NamespaceScope ignored = NamespaceContext.open(OUTER_NAMESPACE)) {
            facade.queryModelResult(context(null));

            assertAll(
                    () -> assertEquals("", namespaceSeenInside.get(),
                            "a provided context with null namespace means explicit default"),
                    () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                            "explicit null/default must restore the outer namespace")
            );
        }
    }

    @Test
    void explicitNullNamespaceOverloadMeansDefaultRatherThanInheritedNamespace() {
        List<String> namespacesPassedToLoader = new ArrayList<>();
        List<String> namespacesSeenInside = new ArrayList<>();
        QueryFacadeImpl facade = facadeThatCapturesAndSkips(namespacesPassedToLoader, namespacesSeenInside);

        try (NamespaceScope ignored = NamespaceContext.open(OUTER_NAMESPACE)) {
            facade.queryModelData(form(), (String) null);

            assertAll(
                    () -> assertEquals(List.of(""), namespacesPassedToLoader,
                            "the explicit namespace overload must encode null as default"),
                    () -> assertEquals(List.of(""), namespacesSeenInside,
                            "the explicit null overload must mask the outer namespace"),
                    () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                            "the explicit null overload must restore the outer namespace")
            );
        }
    }

    @Test
    void namespaceLessQueryOverloadsInheritCanonicalOuterAndPassItToLoader() {
        List<String> namespacesPassedToLoader = new ArrayList<>();
        List<String> namespacesSeenInside = new ArrayList<>();
        QueryFacadeImpl facade = facadeThatCapturesAndSkips(namespacesPassedToLoader, namespacesSeenInside);

        try (NamespaceScope ignored = NamespaceContext.open("  " + OUTER_NAMESPACE + "  ")) {
            facade.queryModelResult(form());
            facade.queryModelData(form());

            assertAll(
                    () -> assertEquals(List.of(OUTER_NAMESPACE, OUTER_NAMESPACE), namespacesPassedToLoader,
                            "namespace-less overloads must pass the canonical inherited namespace to the loader"),
                    () -> assertEquals(List.of(OUTER_NAMESPACE, OUTER_NAMESPACE), namespacesSeenInside,
                            "both loader calls must execute inside the inherited namespace"),
                    () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                            "each nested query must restore its outer scope")
            );
        }
    }

    @Test
    void namespaceLessQueryOverloadsUseExplicitDefaultAtRootAndRestoreUnset() {
        List<String> namespacesPassedToLoader = new ArrayList<>();
        List<String> namespacesSeenInside = new ArrayList<>();
        QueryFacadeImpl facade = facadeThatCapturesAndSkips(namespacesPassedToLoader, namespacesSeenInside);

        facade.queryModelResult(form());
        facade.queryModelData(form());

        assertAll(
                () -> assertEquals(List.of("", ""), namespacesPassedToLoader,
                        "root namespace-less overloads must pass canonical default to the loader"),
                () -> assertEquals(List.of("", ""), namespacesSeenInside,
                        "root namespace-less loader calls must run inside explicit default"),
                () -> assertNull(NamespaceContext.getNamespace(),
                        "closing each root query scope must restore unset")
        );
    }

    @Test
    void publicDtoQueryWithoutNamespaceInheritsAndRestoresOuterNamespace() {
        List<String> namespacesPassedToLoader = new ArrayList<>();
        List<String> namespacesSeenInside = new ArrayList<>();
        QueryFacadeImpl facade = facadeThatCapturesAndSkips(namespacesPassedToLoader, namespacesSeenInside);

        try (NamespaceScope ignored = NamespaceContext.open(OUTER_NAMESPACE)) {
            facade.query(QueryFacadeRequest.builder(Map.of("queryModel", MODEL_NAME)).build());

            assertAll(
                    () -> assertEquals(List.of(OUTER_NAMESPACE), namespacesPassedToLoader),
                    () -> assertEquals(List.of(OUTER_NAMESPACE), namespacesSeenInside),
                    () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                            "the DTO entry must restore its inherited outer scope")
            );
        }
    }

    @Test
    void publicDtoQueryWithExplicitNullNamespaceUsesDefaultAndRestoresOuterNamespace() {
        List<String> namespacesPassedToLoader = new ArrayList<>();
        List<String> namespacesSeenInside = new ArrayList<>();
        QueryFacadeImpl facade = facadeThatCapturesAndSkips(namespacesPassedToLoader, namespacesSeenInside);

        try (NamespaceScope ignored = NamespaceContext.open(OUTER_NAMESPACE)) {
            facade.query(QueryFacadeRequest.builder(Map.of("queryModel", MODEL_NAME))
                    .namespace(null)
                    .build());

            assertAll(
                    () -> assertEquals(List.of(""), namespacesPassedToLoader),
                    () -> assertEquals(List.of(""), namespacesSeenInside),
                    () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                            "the DTO entry must restore the outer scope after explicit default")
            );
        }
    }

    @Test
    void buildSqlOnlyRestoresOuterNamespaceWhenLoaderThrows() {
        assertLoaderFailureRestoresOuter(
                "  build-inner  ",
                "build-inner",
                (facade, context) -> facade.buildSqlOnly(context)
        );
    }

    @Test
    void prepareManagedRelationRestoresOuterNamespaceWhenLoaderThrows() {
        assertLoaderFailureRestoresOuter(
                "  prepare-inner  ",
                "prepare-inner",
                (facade, context) -> facade.prepareManagedRelation(
                        context,
                        ManagedRelationOptions.builder().purpose("namespace restoration probe").build())
        );
    }

    @Test
    void executeManagedRelationRestoresOuterNamespaceWhenEngineThrows() {
        String innerNamespace = "execute-inner";
        AtomicReference<String> namespaceSeenInside = new AtomicReference<>();
        IllegalStateException marker = new IllegalStateException("controlled managed execution failure");
        JdbcModelQueryEngine queryEngine = mock(JdbcModelQueryEngine.class);
        when(queryEngine.getJdbcQueryModel()).thenAnswer(invocation -> {
            namespaceSeenInside.set(NamespaceContext.getNamespace());
            throw marker;
        });

        QueryExecutionContext executionContext = new QueryExecutionContext();
        executionContext.setModelResultContext(context("  " + innerNamespace + "  "));
        ManagedSqlRelation relation = new ManagedSqlRelation(
                "SELECT 1", List.of(), null, queryEngine, executionContext);
        QueryFacadeImpl facade = facade(mock(QueryModelLoader.class), mock(DataSetResultFilterManager.class));

        try (NamespaceScope ignored = NamespaceContext.open(OUTER_NAMESPACE)) {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> facade.executeManagedRelation(relation, "SELECT 1", List.of())
            );

            assertAll(
                    () -> assertSame(marker, thrown, "the controlled execution failure must be preserved"),
                    () -> assertEquals(innerNamespace, namespaceSeenInside.get(),
                            "managed execution must run inside its canonical explicit namespace"),
                    () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                            "managed execution failure must restore the outer namespace")
            );
        }
    }

    @Test
    void executeManagedRelationWithoutResultContextInheritsAndRestoresOuterNamespace() {
        AtomicReference<String> namespaceSeenInside = new AtomicReference<>();
        IllegalStateException marker = new IllegalStateException("controlled inherited execution failure");
        JdbcModelQueryEngine queryEngine = mock(JdbcModelQueryEngine.class);
        when(queryEngine.getJdbcQueryModel()).thenAnswer(invocation -> {
            namespaceSeenInside.set(NamespaceContext.getNamespace());
            throw marker;
        });
        ManagedSqlRelation relation = new ManagedSqlRelation(
                "SELECT 1", List.of(), null, queryEngine, null);
        QueryFacadeImpl facade = facade(mock(QueryModelLoader.class), mock(DataSetResultFilterManager.class));

        try (NamespaceScope ignored = NamespaceContext.open(OUTER_NAMESPACE)) {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> facade.executeManagedRelation(relation, "SELECT 1", List.of())
            );

            assertAll(
                    () -> assertSame(marker, thrown),
                    () -> assertEquals(OUTER_NAMESPACE, namespaceSeenInside.get(),
                            "missing relation context must inherit the current scope"),
                    () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                            "inherited managed execution must restore the outer scope")
            );
        }
    }

    private void assertLoaderFailureRestoresOuter(
            String requestedNamespace,
            String effectiveNamespace,
            FacadeInvocation invocation
    ) {
        AtomicReference<String> namespaceSeenInside = new AtomicReference<>();
        IllegalStateException marker = new IllegalStateException("controlled " + effectiveNamespace + " failure");
        QueryModelLoader loader = throwingLoader(effectiveNamespace, namespaceSeenInside, marker);
        QueryFacadeImpl facade = facade(loader, mock(DataSetResultFilterManager.class));

        try (NamespaceScope ignored = NamespaceContext.open(OUTER_NAMESPACE)) {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> invocation.invoke(facade, context(requestedNamespace))
            );

            assertAll(
                    () -> assertSame(marker, thrown, "the controlled loader failure must be preserved"),
                    () -> assertEquals(effectiveNamespace, namespaceSeenInside.get(),
                            "entry must expose its canonical explicit namespace to the loader"),
                    () -> assertEquals(OUTER_NAMESPACE, NamespaceContext.getNamespace(),
                            "entry failure must restore the exact outer namespace")
            );
        }
    }

    private QueryModelLoader throwingLoader(
            String expectedNamespace,
            AtomicReference<String> namespaceSeenInside,
            RuntimeException marker
    ) {
        QueryModelLoader loader = mock(QueryModelLoader.class);
        when(loader.getJdbcQueryModel(eq(MODEL_NAME), eq(expectedNamespace))).thenAnswer(invocation -> {
            namespaceSeenInside.set(NamespaceContext.getNamespace());
            throw marker;
        });
        return loader;
    }

    private QueryFacadeImpl facadeThatSkipsPhysicalQuery(
            String expectedNamespace,
            AtomicReference<String> namespaceSeenInside
    ) {
        QueryModelLoader loader = mock(QueryModelLoader.class);
        QueryModel queryModel = mock(QueryModel.class);
        when(loader.getJdbcQueryModel(eq(MODEL_NAME), eq(expectedNamespace))).thenAnswer(invocation -> {
            namespaceSeenInside.set(NamespaceContext.getNamespace());
            return queryModel;
        });
        return facadeThatSkipsPhysicalQuery(loader);
    }

    private QueryFacadeImpl facadeThatCapturesAndSkips(
            List<String> namespacesPassedToLoader,
            List<String> namespacesSeenInside
    ) {
        QueryModelLoader loader = mock(QueryModelLoader.class);
        QueryModel queryModel = mock(QueryModel.class);
        when(loader.getJdbcQueryModel(eq(MODEL_NAME), anyString())).thenAnswer(invocation -> {
            namespacesPassedToLoader.add(invocation.getArgument(1));
            namespacesSeenInside.add(NamespaceContext.getNamespace());
            return queryModel;
        });
        return facadeThatSkipsPhysicalQuery(loader);
    }

    private QueryFacadeImpl facadeThatSkipsPhysicalQuery(QueryModelLoader loader) {
        DataSetResultFilterManager filterManager = mock(DataSetResultFilterManager.class);
        doAnswer(invocation -> {
            ModelResultContext resultContext = invocation.getArgument(0);
            resultContext.setSkipQuery(true);
            return null;
        }).when(filterManager).beforeQuery(any(ModelResultContext.class));
        return facade(loader, filterManager);
    }

    private QueryFacadeImpl facade(QueryModelLoader loader, DataSetResultFilterManager filterManager) {
        QueryFacadeImpl facade = new QueryFacadeImpl();
        ReflectionTestUtils.setField(facade, "queryModelLoader", loader);
        ReflectionTestUtils.setField(facade, "systemBundlesContext", mock(SystemBundlesContext.class));
        ReflectionTestUtils.setField(facade, "dataSetResultFilterManager", filterManager);
        return facade;
    }

    private PagingRequest<DbQueryRequestDef> form() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(MODEL_NAME);
        PagingRequest<DbQueryRequestDef> form = new PagingRequest<>();
        form.setParam(request);
        return form;
    }

    private ModelResultContext context(String namespace) {
        ModelResultContext context = new ModelResultContext(form(), null);
        context.setNamespace(namespace);
        return context;
    }

    @FunctionalInterface
    private interface FacadeInvocation {
        Object invoke(QueryFacadeImpl facade, ModelResultContext context);
    }
}
