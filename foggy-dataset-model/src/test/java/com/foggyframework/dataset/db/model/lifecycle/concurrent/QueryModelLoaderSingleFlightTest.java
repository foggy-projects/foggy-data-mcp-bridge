package com.foggyframework.dataset.db.model.lifecycle.concurrent;

import com.foggyframework.dataset.db.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.db.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.db.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.db.model.lifecycle.port.StaleDatasourceBindingException;
import com.foggyframework.dataset.db.model.semantic.member.SyntheticMemberQueryModelFactory;
import com.foggyframework.dataset.db.model.spi.NamedDataSourceResolver;
import com.foggyframework.dataset.db.model.spi.ProcessLocalDefaultDataSourceResolver;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelBuilder;
import com.foggyframework.dataset.db.model.test.JdbcModelTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.foggyframework.dataset.db.model.lifecycle.support.DeterministicConcurrencyTestSupport.await;
import static com.foggyframework.dataset.db.model.lifecycle.support.DeterministicConcurrencyTestSupport.cancelIncomplete;
import static com.foggyframework.dataset.db.model.lifecycle.support.DeterministicConcurrencyTestSupport.get;
import static com.foggyframework.dataset.db.model.lifecycle.support.DeterministicConcurrencyTestSupport.rendezvous;
import static com.foggyframework.dataset.db.model.lifecycle.support.DeterministicConcurrencyTestSupport.shutdownAndAssertTerminated;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

@SpringBootTest(classes = JdbcModelTestApplication.class)
@ActiveProfiles("sqlite")
@Timeout(60)
class QueryModelLoaderSingleFlightTest {

    private static final int CALLERS = 100;

    @Autowired
    private QueryModelLoaderImpl queryModelLoader;

    @Autowired
    private TableModelLoaderManagerImpl tableModelLoaderManager;

    @Autowired
    private SyntheticMemberQueryModelFactory syntheticFactory;

    @Autowired
    private DataSource dataSource;

    private NamedDataSourceResolver originalResolver;
    private ModelBuildSingleFlight originalTableFlight;
    private ModelBuildSingleFlight originalQueryFlight;
    private List<QueryModelBuilder> originalBuilders;
    private SyntheticMemberQueryModelFactory originalSyntheticFactory;

    @BeforeEach
    void installTrackedDefaultBinding() {
        originalResolver = tableModelLoaderManager.getNamedDataSourceResolver();
        originalTableFlight = tableModelLoaderManager.getModelBuildSingleFlight();
        originalQueryFlight = queryModelLoader.getModelBuildSingleFlight();
        originalBuilders = queryModelLoader.getQueryModelBuilders();
        originalSyntheticFactory = queryModelLoader.getSyntheticMemberQueryModelFactory();
        queryModelLoader.clearAll();
        tableModelLoaderManager.setNamedDataSourceResolver(
                new TrackedDefaultResolver(dataSource));
    }

    @AfterEach
    void restoreLoaderCollaborators() {
        queryModelLoader.clearAll();
        tableModelLoaderManager.setNamedDataSourceResolver(originalResolver);
        tableModelLoaderManager.setModelBuildSingleFlight(originalTableFlight);
        queryModelLoader.setModelBuildSingleFlight(originalQueryFlight);
        queryModelLoader.setQueryModelBuilders(originalBuilders);
        queryModelLoader.setSyntheticMemberQueryModelFactory(originalSyntheticFactory);
    }

    @Test
    void canonicalAndAliasCallersShareOneQueryBuildAndPublication() {
        CountDownLatch winnerEntered = new CountDownLatch(1);
        CountDownLatch allWaitersJoined = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        AtomicInteger queryBuilds = new AtomicInteger();
        ModelBuildSingleFlight flight = flightFor(
                "DimChannelQueryModel", allWaitersJoined);
        tableModelLoaderManager.setModelBuildSingleFlight(flight);
        queryModelLoader.setModelBuildSingleFlight(flight);

        QueryModelBuilder delegate = originalBuilders.get(0);
        QueryModelBuilder controlled = (definition, fsscript) -> {
            queryBuilds.incrementAndGet();
            winnerEntered.countDown();
            await(releaseWinner, "query-model winner release");
            return delegate.build(definition, fsscript);
        };
        ArrayList<QueryModelBuilder> builders = new ArrayList<>(originalBuilders);
        builders.set(0, controlled);
        queryModelLoader.setQueryModelBuilders(List.copyOf(builders));

        Phaser simultaneousStart = new Phaser(CALLERS + 1);
        ExecutorService executor = Executors.newFixedThreadPool(CALLERS);
        List<Future<QueryModel>> futures = new ArrayList<>();
        for (int index = 0; index < CALLERS; index++) {
            String requested = index % 2 == 0
                    ? "DimChannelQueryModel"
                    : "DC";
            futures.add(executor.submit(() -> {
                rendezvous(simultaneousStart, "QM caller start");
                return queryModelLoader.getJdbcQueryModel(requested, null);
            }));
        }

        try {
            rendezvous(simultaneousStart, "QM callers start");
            await(winnerEntered, "QM winner entered");
            await(allWaitersJoined, "99 QM waiters joined");
            releaseWinner.countDown();
            QueryModel first = get(futures.get(0), "QM first result");
            for (int index = 1; index < futures.size(); index++) {
                assertSame(first, get(futures.get(index), "QM result " + index));
            }
            assertEquals(1, queryBuilds.get());
            assertSame(first, queryModelLoader.getCatalogSnapshotStore().current("")
                    .orElseThrow().resolveQueryModel("DimChannelQueryModel").orElseThrow());
            assertEquals(0, flight.inFlightCount());
        } finally {
            releaseWinner.countDown();
            cancelIncomplete(futures);
            shutdownAndAssertTerminated(executor, "QM single-flight executor");
        }
    }

    @Test
    void syntheticCallersShareOneFactoryBuildAndPublication() {
        QueryModel source = queryModelLoader.getJdbcQueryModel(
                "FactSalesNestedDimQueryModel", null);
        CountDownLatch winnerEntered = new CountDownLatch(1);
        CountDownLatch allWaitersJoined = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        AtomicInteger factoryBuilds = new AtomicInteger();
        String syntheticName = "FactSalesNestedDimQueryModel#product";
        ModelBuildSingleFlight flight = flightFor(syntheticName, allWaitersJoined);
        tableModelLoaderManager.setModelBuildSingleFlight(flight);
        queryModelLoader.setModelBuildSingleFlight(flight);

        SyntheticMemberQueryModelFactory controlledFactory = spy(syntheticFactory);
        doAnswer(invocation -> {
            factoryBuilds.incrementAndGet();
            winnerEntered.countDown();
            await(releaseWinner, "synthetic QM winner release");
            return invocation.callRealMethod();
        }).when(controlledFactory).build(
                org.mockito.ArgumentMatchers.any(QueryModel.class),
                org.mockito.ArgumentMatchers.any());
        queryModelLoader.setSyntheticMemberQueryModelFactory(controlledFactory);

        Phaser simultaneousStart = new Phaser(CALLERS + 1);
        ExecutorService executor = Executors.newFixedThreadPool(CALLERS);
        List<Future<QueryModel>> futures = new ArrayList<>();
        for (int index = 0; index < CALLERS; index++) {
            futures.add(executor.submit(() -> {
                rendezvous(simultaneousStart, "synthetic QM caller start");
                return queryModelLoader.getJdbcQueryModel(syntheticName, null);
            }));
        }

        try {
            rendezvous(simultaneousStart, "synthetic QM callers start");
            await(winnerEntered, "synthetic QM winner entered");
            await(allWaitersJoined, "99 synthetic QM waiters joined");
            releaseWinner.countDown();
            QueryModel first = get(futures.get(0), "synthetic QM first result");
            for (int index = 1; index < futures.size(); index++) {
                assertSame(first, get(futures.get(index),
                        "synthetic QM result " + index));
            }
            assertEquals(1, factoryBuilds.get());
            assertSame(source, queryModelLoader.getCatalogSnapshotStore().current("")
                    .orElseThrow().resolveQueryModel("FactSalesNestedDimQueryModel")
                    .orElseThrow());
            assertSame(first, queryModelLoader.getCatalogSnapshotStore().current("")
                    .orElseThrow().resolveQueryModel(syntheticName).orElseThrow());
            assertEquals(0, flight.inFlightCount());
        } finally {
            releaseWinner.countDown();
            cancelIncomplete(futures);
            shutdownAndAssertTerminated(executor, "synthetic QM single-flight executor");
        }
    }

    @Test
    void mixedCatalogMustRecheckKnownBindingAtFinalQueryPublication() {
        CatalogSnapshotStore store = queryModelLoader.getCatalogSnapshotStore();
        tableModelLoaderManager.setNamedDataSourceResolver(
                new UntrackedDefaultResolver(dataSource));
        tableModelLoaderManager.load("FactSalesModel");
        RotateAfterNestedGuardResolver resolver =
                new RotateAfterNestedGuardResolver(dataSource);
        tableModelLoaderManager.setNamedDataSourceResolver(resolver);

        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> queryModelLoader.getJdbcQueryModel(
                        "SalesReturnJoinQueryModel", null));

        assertTrue(hasCause(failure, StaleDatasourceBindingException.class));
        assertEquals(2, resolver.publicationGuards.get());
        CatalogSnapshot after = store.current("").orElseThrow();
        assertTrue(after.tableModels().containsKey("FactSalesModel"));
        assertTrue(after.tableModels().containsKey("FactReturnModel"));
        assertFalse(after.queryModels().containsKey("SalesReturnJoinQueryModel"));
    }

    private boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ModelBuildSingleFlight flightFor(
            String canonicalName,
            CountDownLatch allWaitersJoined
    ) {
        return new ModelBuildSingleFlight(new ModelBuildFlightObserver() {
            @Override
            public void waiterJoined(ModelBuildKey key, int waiterCount) {
                if (key.canonicalModelName().equals(canonicalName)
                        && waiterCount == CALLERS - 1) {
                    allWaitersJoined.countDown();
                }
            }
        });
    }

    private static final class TrackedDefaultResolver
            implements NamedDataSourceResolver, ProcessLocalDefaultDataSourceResolver {
        private final DataSource dataSource;
        private final DatasourceBindingIdentity identity = new DatasourceBindingIdentity(
                "test:namespace-default",
                "test:sqlite",
                new DatasourceBindingGeneration("test-binding-generation-1"));

        private TrackedDefaultResolver(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public DataSource resolve(String name) {
            return null;
        }

        @Override
        public ResolvedDatasourceBinding resolveDefaultBinding(String namespace) {
            return namespace == null || namespace.isBlank()
                    ? ResolvedDatasourceBinding.tracked(dataSource, identity)
                    : null;
        }

        @Override
        public ResolvedDatasourceBinding resolveProcessLocalDefaultBinding() {
            return ResolvedDatasourceBinding.tracked(dataSource, identity);
        }

        @Override
        public BindingCurrentness currentness(DatasourceBindingIdentity expected) {
            return identity.equals(expected)
                    ? BindingCurrentness.CURRENT
                    : BindingCurrentness.STALE;
        }

        @Override
        public <T> T publishIfCurrent(
                Collection<DatasourceBindingIdentity> identities,
                Supplier<T> publication
        ) {
            if (!identities.stream().allMatch(identity::equals)) {
                throw new IllegalStateException("stale test datasource binding");
            }
            return publication.get();
        }

        @Override
        public boolean isConfigured(String name) {
            return false;
        }
    }

    private static final class RotateAfterNestedGuardResolver
            implements NamedDataSourceResolver, ProcessLocalDefaultDataSourceResolver {

        private final DataSource dataSource;
        private final DatasourceBindingIdentity first = identity("generation-one");
        private final DatasourceBindingIdentity second = identity("generation-two");
        private final AtomicReference<DatasourceBindingIdentity> current =
                new AtomicReference<>(first);
        private final AtomicInteger publicationGuards = new AtomicInteger();

        private RotateAfterNestedGuardResolver(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public DataSource resolve(String name) {
            return null;
        }

        @Override
        public ResolvedDatasourceBinding resolveDefaultBinding(String namespace) {
            return namespace == null || namespace.isBlank()
                    ? ResolvedDatasourceBinding.tracked(dataSource, current.get())
                    : null;
        }

        @Override
        public ResolvedDatasourceBinding resolveProcessLocalDefaultBinding() {
            return ResolvedDatasourceBinding.tracked(dataSource, current.get());
        }

        @Override
        public BindingCurrentness currentness(DatasourceBindingIdentity expected) {
            return current.get().equals(expected)
                    ? BindingCurrentness.CURRENT
                    : BindingCurrentness.STALE;
        }

        @Override
        public synchronized <T> T publishIfCurrent(
                Collection<DatasourceBindingIdentity> identities,
                Supplier<T> publication
        ) {
            DatasourceBindingIdentity expected = current.get();
            if (!identities.stream().allMatch(expected::equals)) {
                throw new StaleDatasourceBindingException(
                        identities.iterator().next().bindingKey());
            }
            int invocation = publicationGuards.incrementAndGet();
            if (invocation == 2) {
                current.set(second);
                throw new StaleDatasourceBindingException(
                        identities.iterator().next().bindingKey());
            }
            return publication.get();
        }

        @Override
        public boolean isConfigured(String name) {
            return false;
        }

        private static DatasourceBindingIdentity identity(String generation) {
            return new DatasourceBindingIdentity(
                    "test:namespace-default",
                    "test:sqlite",
                    new DatasourceBindingGeneration(generation));
        }
    }

    private static final class UntrackedDefaultResolver
            implements NamedDataSourceResolver, ProcessLocalDefaultDataSourceResolver {

        private final DataSource dataSource;

        private UntrackedDefaultResolver(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public DataSource resolve(String name) {
            return null;
        }

        @Override
        public ResolvedDatasourceBinding resolveDefaultBinding(String namespace) {
            return namespace == null || namespace.isBlank()
                    ? ResolvedDatasourceBinding.untracked(dataSource)
                    : null;
        }

        @Override
        public ResolvedDatasourceBinding resolveProcessLocalDefaultBinding() {
            return ResolvedDatasourceBinding.untracked(dataSource);
        }

        @Override
        public boolean isConfigured(String name) {
            return false;
        }
    }
}
