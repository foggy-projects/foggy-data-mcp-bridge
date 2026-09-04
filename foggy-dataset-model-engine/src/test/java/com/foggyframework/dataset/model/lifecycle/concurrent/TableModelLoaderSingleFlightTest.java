package com.foggyframework.dataset.model.lifecycle.concurrent;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.def.DbModelDef;
import com.foggyframework.dataset.model.impl.loader.JdbcTableModelLoaderImpl;
import com.foggyframework.dataset.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.model.lifecycle.port.StaleDatasourceBindingException;
import com.foggyframework.dataset.model.spi.NamedDataSourceResolver;
import com.foggyframework.dataset.model.spi.ProcessLocalDefaultDataSourceResolver;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.spi.TableModelLoader;
import com.foggyframework.dataset.model.test.JdbcModelTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.foggyframework.dataset.model.lifecycle.support.DeterministicConcurrencyTestSupport.await;
import static com.foggyframework.dataset.model.lifecycle.support.DeterministicConcurrencyTestSupport.cancelIncomplete;
import static com.foggyframework.dataset.model.lifecycle.support.DeterministicConcurrencyTestSupport.get;
import static com.foggyframework.dataset.model.lifecycle.support.DeterministicConcurrencyTestSupport.rendezvous;
import static com.foggyframework.dataset.model.lifecycle.support.DeterministicConcurrencyTestSupport.shutdownAndAssertTerminated;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(classes = JdbcModelTestApplication.class)
@ActiveProfiles("sqlite")
@Timeout(60)
class TableModelLoaderSingleFlightTest {

    private static final int CALLERS = 100;

    @Autowired
    private SystemBundlesContext systemBundlesContext;

    @Autowired
    private FileFsscriptLoader fileFsscriptLoader;

    @Autowired
    private JdbcTableModelLoaderImpl jdbcTableModelLoader;

    @Autowired
    private DataSource dataSource;

    @Test
    void oneHundredColdSameKeyCallersBuildAndPublishOnce() {
        CountDownLatch winnerEntered = new CountDownLatch(1);
        CountDownLatch allWaitersJoined = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        AtomicInteger builds = new AtomicInteger();
        TableModelLoader controlledLoader = controlled((fsscript, definition, bundle) -> {
            builds.incrementAndGet();
            winnerEntered.countDown();
            await(releaseWinner, "same-key TM winner release");
            return jdbcTableModelLoader.load(fsscript, definition, bundle);
        });
        ModelBuildSingleFlight singleFlight = new ModelBuildSingleFlight(
                new ModelBuildFlightObserver() {
                    @Override
                    public void waiterJoined(ModelBuildKey key, int waiterCount) {
                        if (waiterCount == CALLERS - 1) {
                            allWaitersJoined.countDown();
                        }
                    }
                });
        TableModelLoaderManagerImpl manager = manager(controlledLoader, singleFlight);

        Phaser simultaneousStart = new Phaser(CALLERS + 1);
        ExecutorService executor = Executors.newFixedThreadPool(CALLERS);
        List<Future<TableModel>> futures = new ArrayList<>();
        for (int index = 0; index < CALLERS; index++) {
            futures.add(executor.submit(() -> {
                rendezvous(simultaneousStart, "same-key TM caller start");
                return manager.load("DimChannelModel", null);
            }));
        }

        try {
            rendezvous(simultaneousStart, "same-key TM callers start");
            await(winnerEntered, "same-key TM winner entered");
            await(allWaitersJoined, "99 same-key TM waiters joined");
            releaseWinner.countDown();

            TableModel first = get(futures.get(0), "same-key TM first result");
            for (int index = 1; index < futures.size(); index++) {
                assertSame(first, get(futures.get(index), "same-key TM result " + index));
            }
            assertEquals(1, builds.get());
            assertSame(first, manager.getCatalogSnapshotStore().current("")
                    .orElseThrow().tableModels().get("DimChannelModel"));
            assertSame(first, manager.load("DimChannelModel", null));
            assertEquals(1, builds.get(), "cache hit must not start another build");
            assertEquals(0, singleFlight.inFlightCount());
        } finally {
            releaseWinner.countDown();
            cancelIncomplete(futures);
            shutdownAndAssertTerminated(executor, "same-key TM executor");
        }
    }

    @Test
    void distinctColdModelsRebaseDisjointPublicationsWithoutRebuild() {
        CountDownLatch bothBuildersEntered = new CountDownLatch(2);
        CountDownLatch releaseBuilders = new CountDownLatch(1);
        AtomicInteger builds = new AtomicInteger();
        TableModelLoader controlledLoader = controlled((fsscript, definition, bundle) -> {
            builds.incrementAndGet();
            bothBuildersEntered.countDown();
            await(releaseBuilders, "distinct TM builders release");
            return jdbcTableModelLoader.load(fsscript, definition, bundle);
        });
        ModelBuildSingleFlight singleFlight = new ModelBuildSingleFlight();
        TableModelLoaderManagerImpl manager = manager(controlledLoader, singleFlight);

        Phaser simultaneousStart = new Phaser(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<TableModel>> futures = List.of(
                executor.submit(() -> {
                    rendezvous(simultaneousStart, "DimChannelModel caller start");
                    return manager.load("DimChannelModel", null);
                }),
                executor.submit(() -> {
                    rendezvous(simultaneousStart, "DimCustomerModel caller start");
                    return manager.load("DimCustomerModel", null);
                }));

        try {
            rendezvous(simultaneousStart, "distinct TM callers start");
            await(bothBuildersEntered, "both distinct TM builders overlap");
            assertEquals(0, bothBuildersEntered.getCount());
            releaseBuilders.countDown();

            assertNotNull(get(futures.get(0), "DimChannelModel result"));
            assertNotNull(get(futures.get(1), "DimCustomerModel result"));
            assertEquals(2, builds.get(),
                    "disjoint cold additions must publish without rebuilding");
            assertEquals(2, manager.getCatalogSnapshotStore().current("")
                    .orElseThrow().tableModels().size());
            assertEquals(0, singleFlight.inFlightCount());
        } finally {
            releaseBuilders.countDown();
            cancelIncomplete(futures);
            shutdownAndAssertTerminated(executor, "distinct TM executor");
        }
    }

    @Test
    void winnerFailureIsSharedAndNextCallCanRetry() {
        CountDownLatch winnerEntered = new CountDownLatch(1);
        CountDownLatch allWaitersJoined = new CountDownLatch(1);
        CountDownLatch releaseFailure = new CountDownLatch(1);
        CountDownLatch firstFlightRemoved = new CountDownLatch(1);
        AtomicInteger builds = new AtomicInteger();
        IllegalStateException marker = new IllegalStateException("controlled TM build failure");
        TableModelLoader controlledLoader = controlled((fsscript, definition, bundle) -> {
            int invocation = builds.incrementAndGet();
            if (invocation == 1) {
                winnerEntered.countDown();
                await(releaseFailure, "failed TM winner release");
                throw marker;
            }
            return jdbcTableModelLoader.load(fsscript, definition, bundle);
        });
        ModelBuildSingleFlight singleFlight = new ModelBuildSingleFlight(
                new ModelBuildFlightObserver() {
                    @Override
                    public void waiterJoined(ModelBuildKey key, int waiterCount) {
                        if (waiterCount == CALLERS - 1) {
                            allWaitersJoined.countDown();
                        }
                    }

                    @Override
                    public void flightRemoved(ModelBuildKey key) {
                        firstFlightRemoved.countDown();
                    }
                });
        TableModelLoaderManagerImpl manager = manager(controlledLoader, singleFlight);

        Phaser simultaneousStart = new Phaser(CALLERS + 1);
        ExecutorService executor = Executors.newFixedThreadPool(CALLERS);
        List<Future<TableModel>> futures = new ArrayList<>();
        for (int index = 0; index < CALLERS; index++) {
            futures.add(executor.submit(() -> {
                rendezvous(simultaneousStart, "failed TM caller start");
                return manager.load("DimChannelModel", null);
            }));
        }

        try {
            rendezvous(simultaneousStart, "failed TM callers start");
            await(winnerEntered, "failed TM winner entered");
            await(allWaitersJoined, "99 failed TM waiters joined");
            releaseFailure.countDown();
            for (Future<TableModel> future : futures) {
                assertFutureCause(future, marker);
            }
            await(firstFlightRemoved, "failed TM flight removed");
            assertFalse(manager.getCatalogSnapshotStore().current("").isPresent());
            assertEquals(0, singleFlight.inFlightCount());

            TableModel retried = manager.load("DimChannelModel", null);
            assertNotNull(retried);
            assertEquals(2, builds.get());
            assertSame(retried, manager.getCatalogSnapshotStore().current("")
                    .orElseThrow().tableModels().get("DimChannelModel"));
            assertEquals(0, singleFlight.inFlightCount());
        } finally {
            releaseFailure.countDown();
            cancelIncomplete(futures);
            shutdownAndAssertTerminated(executor, "failed TM executor");
        }
    }

    @Test
    void bindingChangeAtFinalPublicationGuardDiscardsAndRetriesBuild() {
        AtomicInteger builds = new AtomicInteger();
        TableModelLoader controlledLoader = controlled((fsscript, definition, bundle) -> {
            builds.incrementAndGet();
            return jdbcTableModelLoader.load(fsscript, definition, bundle);
        });
        ModelBuildSingleFlight singleFlight = new ModelBuildSingleFlight();
        RotatingTrackedDefaultResolver resolver =
                new RotatingTrackedDefaultResolver(dataSource);
        TableModelLoaderManagerImpl manager = manager(
                controlledLoader, singleFlight, resolver);

        TableModel loaded = manager.load("DimChannelModel", null);

        assertNotNull(loaded);
        assertEquals(2, builds.get(), "the stale G1 build must be discarded");
        assertEquals(2, resolver.publicationGuards.get());
        assertEquals(resolver.currentIdentity(),
                manager.getCatalogSnapshotStore().current("")
                        .orElseThrow()
                        .provenance()
                        .get(CatalogModelKey.table("DimChannelModel"))
                        .datasourceBindings()
                        .get(resolver.currentIdentity().bindingKey()));
        assertFalse(manager.getCatalogSnapshotStore().current("")
                .orElseThrow()
                .provenance()
                .get(CatalogModelKey.table("DimChannelModel"))
                .datasourceBindings()
                .containsValue(resolver.firstIdentity));
        assertEquals(0, singleFlight.inFlightCount());
    }

    @Test
    void exhaustedStaleBuildReportsReasonGenerationsAndRefreshOverlap() {
        AtomicInteger builds = new AtomicInteger();
        TableModelLoader controlledLoader = controlled((fsscript, definition, bundle) -> {
            builds.incrementAndGet();
            return jdbcTableModelLoader.load(fsscript, definition, bundle);
        });
        AlwaysStaleTrackedDefaultResolver resolver =
                new AlwaysStaleTrackedDefaultResolver(dataSource);
        TableModelLoaderManagerImpl manager = manager(
                controlledLoader, new ModelBuildSingleFlight(), resolver);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> manager.load("DimChannelModel", null));

        assertEquals(3, builds.get());
        assertTrue(failure.getMessage().contains(
                "staleReason=DATASOURCE_BINDING_CHANGED"));
        assertTrue(failure.getMessage().contains("diagnostic-generation"));
        assertTrue(failure.getMessage().contains("concurrentRefresh=false"));
    }

    private TableModelLoaderManagerImpl manager(
            TableModelLoader loader,
            ModelBuildSingleFlight singleFlight
    ) {
        return manager(loader, singleFlight, new TrackedDefaultResolver(dataSource));
    }

    private TableModelLoaderManagerImpl manager(
            TableModelLoader loader,
            ModelBuildSingleFlight singleFlight,
            NamedDataSourceResolver resolver
    ) {
        TableModelLoaderManagerImpl manager = new TableModelLoaderManagerImpl(
                systemBundlesContext,
                fileFsscriptLoader,
                List.of(),
                List.of(loader),
                resolver);
        manager.setDataSource(dataSource);
        manager.setModelBuildSingleFlight(singleFlight);
        return manager;
    }

    private TableModelLoader controlled(ControlledBuild build) {
        return new TableModelLoader() {
            @Override
            public TableModel load(Fsscript fsscript, DbModelDef definition, Bundle bundle) {
                return build.load(fsscript, definition, bundle);
            }

            @Override
            public String getTypeName() {
                return "jdbc";
            }
        };
    }

    private void assertFutureCause(Future<TableModel> future, RuntimeException expected) {
        try {
            future.get(15, TimeUnit.SECONDS);
            fail("expected controlled TM build failure");
        } catch (ExecutionException failure) {
            assertSame(expected, failure.getCause());
        } catch (Exception failure) {
            throw new AssertionError("failed while awaiting TM failure", failure);
        }
    }

    @FunctionalInterface
    private interface ControlledBuild {
        TableModel load(Fsscript fsscript, DbModelDef definition, Bundle bundle);
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

    private static final class RotatingTrackedDefaultResolver
            implements NamedDataSourceResolver, ProcessLocalDefaultDataSourceResolver {
        private final DataSource dataSource;
        private final DatasourceBindingIdentity firstIdentity = identity("generation-one");
        private final DatasourceBindingIdentity secondIdentity = identity("generation-two");
        private final AtomicReference<DatasourceBindingIdentity> current =
                new AtomicReference<>(firstIdentity);
        private final AtomicInteger publicationGuards = new AtomicInteger();

        private RotatingTrackedDefaultResolver(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public DataSource resolve(String name) {
            return null;
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
            if (publicationGuards.incrementAndGet() == 1) {
                current.set(secondIdentity);
                throw new StaleDatasourceBindingException(firstIdentity.bindingKey());
            }
            for (DatasourceBindingIdentity identity : identities) {
                if (!current.get().equals(identity)) {
                    throw new StaleDatasourceBindingException(identity.bindingKey());
                }
            }
            return publication.get();
        }

        @Override
        public boolean isConfigured(String name) {
            return false;
        }

        private DatasourceBindingIdentity currentIdentity() {
            return current.get();
        }

        private static DatasourceBindingIdentity identity(String generation) {
            return new DatasourceBindingIdentity(
                    "test:namespace-default",
                    "test:sqlite",
                    new DatasourceBindingGeneration(generation));
        }
    }

    private static final class AlwaysStaleTrackedDefaultResolver
            implements NamedDataSourceResolver, ProcessLocalDefaultDataSourceResolver {
        private final DataSource dataSource;
        private final DatasourceBindingIdentity identity = new DatasourceBindingIdentity(
                "test:diagnostic-default",
                "test:sqlite",
                new DatasourceBindingGeneration("diagnostic-generation"));

        private AlwaysStaleTrackedDefaultResolver(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public DataSource resolve(String name) {
            return null;
        }

        @Override
        public ResolvedDatasourceBinding resolveProcessLocalDefaultBinding() {
            return ResolvedDatasourceBinding.tracked(dataSource, identity);
        }

        @Override
        public BindingCurrentness currentness(DatasourceBindingIdentity expected) {
            return BindingCurrentness.STALE;
        }

        @Override
        public <T> T publishIfCurrent(
                Collection<DatasourceBindingIdentity> identities,
                Supplier<T> publication
        ) {
            throw new StaleDatasourceBindingException(identity.bindingKey());
        }

        @Override
        public boolean isConfigured(String name) {
            return false;
        }
    }
}
