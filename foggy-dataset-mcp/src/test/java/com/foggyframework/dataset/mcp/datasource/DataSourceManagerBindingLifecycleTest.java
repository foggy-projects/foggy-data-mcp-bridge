package com.foggyframework.dataset.mcp.datasource;

import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.port.BindingAdmissionState;
import com.foggyframework.dataset.db.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.db.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.db.model.lifecycle.port.RevokeMode;
import com.foggyframework.dataset.db.model.lifecycle.port.StaleDatasourceBindingException;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataSourceManagerBindingLifecycleTest {

    @Test
    void configurationToStringDoesNotExposePhysicalTargetOrCredentials() {
        DataSourceManager.DataSourceConfig config = config();
        DataSourceController.DataSourceConfigRequest request =
                new DataSourceController.DataSourceConfigRequest();
        request.setName("orders");
        request.setHost(config.getHost());
        request.setDatabase(config.getDatabase());
        request.setUsername(config.getUsername());
        request.setPassword(config.getPassword());
        request.setDriver(config.getDriver());

        assertThat(config.toString())
                .doesNotContain("db.internal", "analytics", "service", "secret");
        assertThat(request.toString())
                .doesNotContain("db.internal", "analytics", "service", "secret");
    }

    @Test
    void datasourceIdentityRejectsPhysicalTargetShapedNames() {
        TrackingFactory factory = new TrackingFactory();
        DataSourceManager manager = manager(
                mock(DataSourceConfigPersistence.class),
                factory,
                new ManualDrainScheduler());

        assertThatThrownBy(() -> manager.configure("jdbc:postgresql://db/orders", config()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("logical identifier");
        assertThat(factory.created).isEmpty();
        manager.close();
    }

    @Test
    void leaseDrainTimeoutRejectsOutOfContractValues() {
        DataSourceConfigPersistence persistence = mock(DataSourceConfigPersistence.class);
        TrackingFactory factory = new TrackingFactory();
        ManualDrainScheduler scheduler = new ManualDrainScheduler();

        assertThatThrownBy(() -> new DataSourceManager(
                persistence, factory, scheduler, 999L, "mcp-test-boot"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lease-drain-timeout-ms");
        assertThatThrownBy(() -> new DataSourceManager(
                persistence, factory, scheduler, 300_001L, "mcp-test-boot"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lease-drain-timeout-ms");
    }

    @Test
    void configureAlwaysCreatesFreshGenerationAndPinnedPhysicalHandle() throws Exception {
        DataSourceConfigPersistence persistence = mock(DataSourceConfigPersistence.class);
        TrackingFactory factory = new TrackingFactory();
        ManualDrainScheduler scheduler = new ManualDrainScheduler();
        DataSourceManager manager = manager(persistence, factory, scheduler);

        manager.configure(" orders ", config());
        ResolvedDatasourceBinding first = manager.resolveBinding("orders");
        TrackingDataSource firstPhysical = factory.created.get(0);

        manager.configure("orders", config());
        ResolvedDatasourceBinding second = manager.resolveBinding("orders");
        TrackingDataSource secondPhysical = factory.created.get(1);

        assertThat(first.identity()).isNotEqualTo(second.identity());
        assertThat(first.identity().generation().value()).isEqualTo("mcp-test-boot:1");
        assertThat(second.identity().generation().value()).isEqualTo("mcp-test-boot:2");
        assertThat(first.dataSource()).isNotSameAs(second.dataSource());
        assertThat(firstPhysical.closed).isTrue();
        assertThat(secondPhysical.closed).isFalse();
        assertRevoked(first.dataSource());
        assertThatThrownBy(() -> second.dataSource().getConnection("other", "credential"))
                .isInstanceOf(SQLFeatureNotSupportedException.class)
                .hasMessageContaining("does not allow credential override");

        try (Connection ignored = second.dataSource().getConnection()) {
            assertThat(secondPhysical.connections).hasSize(1);
        }
        manager.close();
    }

    @Test
    void overlappingPoolCreationAllocatesGenerationInCommitOrder() throws Exception {
        DataSourceConfigPersistence persistence = mock(DataSourceConfigPersistence.class);
        OverlappingFactory factory = new OverlappingFactory();
        ManualDrainScheduler scheduler = new ManualDrainScheduler();
        DataSourceManager manager = new DataSourceManager(
                persistence, factory, scheduler, 1_000L, "mcp-test-boot");
        DataSourceManager.DataSourceConfig firstConfig = config();
        firstConfig.setDatabase("first");
        DataSourceManager.DataSourceConfig secondConfig = config();
        secondConfig.setDatabase("second");
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> manager.configure("orders", firstConfig));
            assertThat(factory.firstCreationEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executor.submit(() -> manager.configure("orders", secondConfig));

            second.get(5, TimeUnit.SECONDS);
            ResolvedDatasourceBinding interim = manager.resolveBinding("orders");
            assertThat(interim.identity().generation().value()).isEqualTo("mcp-test-boot:1");

            factory.releaseFirstCreation.countDown();
            first.get(5, TimeUnit.SECONDS);

            ResolvedDatasourceBinding committedLast = manager.resolveBinding("orders");
            assertThat(committedLast.identity().generation().value()).isEqualTo("mcp-test-boot:2");
            assertThat(manager.getConfig("orders").getDatabase()).isEqualTo("first");
            assertRevoked(interim.dataSource());
            assertThat(factory.created.get(0).closed).isFalse();
            assertThat(factory.created.get(1).closed).isTrue();
        } finally {
            factory.releaseFirstCreation.countDown();
            executor.shutdownNow();
            manager.close();
        }
    }

    @Test
    void removeRejectsNewOldGenerationBorrowsWhileHeldLeaseDrains() throws Exception {
        DataSourceConfigPersistence persistence = mock(DataSourceConfigPersistence.class);
        TrackingFactory factory = new TrackingFactory();
        ManualDrainScheduler scheduler = new ManualDrainScheduler();
        DataSourceManager manager = manager(persistence, factory, scheduler);
        manager.configure("orders", config());
        ResolvedDatasourceBinding binding = manager.resolveBinding("orders");
        TrackingDataSource physical = factory.created.get(0);
        Connection held = binding.dataSource().getConnection();

        assertThat(manager.remove("orders")).isTrue();

        assertThat(manager.resolveBinding("orders")).isNull();
        assertThat(manager.admissionState(binding.dataSource()))
                .isEqualTo(BindingAdmissionState.RETIRING);
        assertThat(manager.activeLeases(binding.dataSource())).isEqualTo(1);
        assertThat(physical.closed).isFalse();
        assertThat(physical.connections.get(0).closed.get()).isFalse();
        assertRevoked(binding.dataSource());

        held.close();

        assertThat(manager.admissionState(binding.dataSource()))
                .isEqualTo(BindingAdmissionState.CLOSED);
        assertThat(physical.closed).isTrue();
        assertThat(physical.closeCalls.get()).isEqualTo(1);
        assertThat(physical.connections.get(0).closeCalls.get()).isEqualTo(1);
        assertThat(scheduler.tasks.get(0).cancelled.get()).isTrue();

        manager.configure("orders", config());
        assertThat(manager.getBindingIdentity("orders")).isNotEqualTo(binding.identity());
        assertThat(manager.getBindingIdentity("orders").generation().value())
                .isEqualTo("mcp-test-boot:2");
        manager.close();
    }

    @Test
    void controlledDrainDeadlineAndHardRevokeForceCloseHeldLease() throws Exception {
        DataSourceConfigPersistence persistence = mock(DataSourceConfigPersistence.class);
        TrackingFactory factory = new TrackingFactory();
        ManualDrainScheduler scheduler = new ManualDrainScheduler();
        DataSourceManager manager = manager(persistence, factory, scheduler);

        manager.configure("drain", config());
        ResolvedDatasourceBinding drainBinding = manager.resolveBinding("drain");
        Connection draining = drainBinding.dataSource().getConnection();
        TrackingDataSource drainPhysical = factory.created.get(0);
        manager.remove("drain");

        scheduler.runNext();

        assertThat(drainPhysical.connections.get(0).closed.get()).isTrue();
        assertThat(drainPhysical.closed).isTrue();
        assertThat(manager.admissionState(drainBinding.dataSource()))
                .isEqualTo(BindingAdmissionState.CLOSED);
        draining.close();

        manager.configure("hard", config());
        ResolvedDatasourceBinding hardBinding = manager.resolveBinding("hard");
        Connection hardLease = hardBinding.dataSource().getConnection();
        TrackingDataSource hardPhysical = factory.created.get(1);

        manager.remove("hard", RevokeMode.HARD);

        assertThat(hardPhysical.connections.get(0).closed.get()).isTrue();
        assertThat(hardPhysical.closed).isTrue();
        assertThat(manager.admissionState(hardBinding.dataSource()))
                .isEqualTo(BindingAdmissionState.CLOSED);
        hardLease.close();
        assertThat(hardPhysical.closeCalls.get()).isEqualTo(1);
        assertThat(hardPhysical.connections.get(0).closeCalls.get()).isEqualTo(1);
        manager.close();
    }

    @Test
    void managerShutdownHardClosesCurrentAndAlreadyRetiringGenerations() throws Exception {
        DataSourceConfigPersistence persistence = mock(DataSourceConfigPersistence.class);
        TrackingFactory factory = new TrackingFactory();
        DataSourceManager manager = manager(
                persistence, factory, new ManualDrainScheduler());
        manager.configure("orders", config());
        ResolvedDatasourceBinding oldBinding = manager.resolveBinding("orders");
        Connection heldOldLease = oldBinding.dataSource().getConnection();
        manager.configure("orders", config());
        ResolvedDatasourceBinding currentBinding = manager.resolveBinding("orders");

        assertThat(manager.admissionState(oldBinding.dataSource()))
                .isEqualTo(BindingAdmissionState.RETIRING);

        manager.close();

        assertThat(factory.created.get(0).connections.get(0).closed.get()).isTrue();
        assertThat(factory.created.get(0).closed).isTrue();
        assertThat(factory.created.get(1).closed).isTrue();
        assertThat(manager.admissionState(oldBinding.dataSource()))
                .isEqualTo(BindingAdmissionState.CLOSED);
        assertThat(manager.admissionState(currentBinding.dataSource()))
                .isEqualTo(BindingAdmissionState.CLOSED);
        heldOldLease.close();
        assertThat(factory.created.get(0).closeCalls.get()).isEqualTo(1);
        assertThat(factory.created.get(0).connections.get(0).closeCalls.get()).isEqualTo(1);
        assertThat(factory.created.get(1).closeCalls.get()).isEqualTo(1);
    }

    @Test
    void restoreAllocatesColdGenerationsInCanonicalNameOrderAndRejectsCollision() {
        DataSourceConfigPersistence persistence = mock(DataSourceConfigPersistence.class);
        Map<String, DataSourceManager.DataSourceConfig> restored = new LinkedHashMap<>();
        restored.put("zeta", config());
        restored.put("alpha", config());
        when(persistence.loadAll()).thenReturn(restored);
        TrackingFactory factory = new TrackingFactory();
        DataSourceManager manager = manager(persistence, factory, new ManualDrainScheduler());

        manager.init();

        assertThat(manager.getConfiguredNames()).containsExactly("alpha", "zeta");
        assertThat(manager.getBindingIdentity("alpha").generation().value())
                .isEqualTo("mcp-test-boot:1");
        assertThat(manager.getBindingIdentity("zeta").generation().value())
                .isEqualTo("mcp-test-boot:2");
        assertThat(factory.created).hasSize(2);
        manager.close();

        DataSourceConfigPersistence conflictingPersistence = mock(DataSourceConfigPersistence.class);
        Map<String, DataSourceManager.DataSourceConfig> conflicting = new LinkedHashMap<>();
        conflicting.put("alpha", config());
        conflicting.put(" alpha ", config());
        when(conflictingPersistence.loadAll()).thenReturn(conflicting);
        TrackingFactory conflictingFactory = new TrackingFactory();
        DataSourceManager conflictingManager = manager(
                conflictingPersistence, conflictingFactory, new ManualDrainScheduler());

        assertThatThrownBy(conflictingManager::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATASOURCE_CANONICAL_NAME_CONFLICT");
        assertThat(conflictingFactory.created).isEmpty();
        conflictingManager.close();
    }

    @Test
    void persistenceFailureLeavesCurrentAdmissionAndIdentityUntouched() throws Exception {
        DataSourceConfigPersistence persistence = mock(DataSourceConfigPersistence.class);
        TrackingFactory factory = new TrackingFactory();
        DataSourceManager manager = manager(persistence, factory, new ManualDrainScheduler());
        manager.configure("orders", config());
        ResolvedDatasourceBinding current = manager.resolveBinding("orders");
        doThrow(new IllegalStateException("disk failure"))
                .when(persistence).save("orders", config());

        assertThatThrownBy(() -> manager.configure("orders", config()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("disk failure");

        assertThat(manager.resolveBinding("orders").identity()).isEqualTo(current.identity());
        assertThat(factory.created.get(0).closed).isFalse();
        assertThat(factory.created.get(1).closed).isTrue();
        try (Connection ignored = current.dataSource().getConnection()) {
            assertThat(manager.activeLeases(current.dataSource())).isEqualTo(1);
        }
        manager.close();
    }

    @Test
    void deleteFailureLeavesCurrentBindingOpen() throws Exception {
        DataSourceConfigPersistence persistence = mock(DataSourceConfigPersistence.class);
        TrackingFactory factory = new TrackingFactory();
        DataSourceManager manager = manager(persistence, factory, new ManualDrainScheduler());
        manager.configure("orders", config());
        ResolvedDatasourceBinding current = manager.resolveBinding("orders");
        doThrow(new IllegalStateException("delete failure"))
                .when(persistence).delete("orders");

        assertThatThrownBy(() -> manager.remove("orders"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("delete failure");

        assertThat(manager.resolveBinding("orders").identity()).isEqualTo(current.identity());
        assertThat(manager.admissionState(current.dataSource())).isEqualTo(BindingAdmissionState.OPEN);
        assertThat(factory.created.get(0).closed).isFalse();
        try (Connection ignored = current.dataSource().getConnection()) {
            assertThat(manager.activeLeases(current.dataSource())).isEqualTo(1);
        }
        manager.close();
    }

    @Test
    void strongResolverAndLegacyResolverReturnTheSamePinnedHandle() {
        DataSourceConfigPersistence persistence = mock(DataSourceConfigPersistence.class);
        TrackingFactory factory = new TrackingFactory();
        DataSourceManager manager = manager(
                persistence, factory, new ManualDrainScheduler());
        manager.configure("orders", config());
        NamedDataSourceResolverImpl resolver = new NamedDataSourceResolverImpl(manager);

        ResolvedDatasourceBinding first = resolver.resolveBinding("orders");

        assertThat(first).isNotNull();
        assertThat(first.cacheable()).isTrue();
        assertThat(first.identity()).isEqualTo(manager.getBindingIdentity("orders"));
        assertThat(resolver.resolve("orders")).isSameAs(first.dataSource());
        assertThat(resolver.isConfigured("orders")).isTrue();
        assertThat(resolver.currentness(first.identity())).isEqualTo(BindingCurrentness.CURRENT);
        assertThat(factory.created.get(0).connections).isEmpty();

        manager.configure("orders", config());
        ResolvedDatasourceBinding second = resolver.resolveBinding("orders");

        assertThat(resolver.currentness(first.identity())).isEqualTo(BindingCurrentness.STALE);
        assertThat(resolver.currentness(second.identity())).isEqualTo(BindingCurrentness.CURRENT);
        assertThat(resolver.currentness(new DatasourceBindingIdentity(
                "other",
                "other-backend",
                new DatasourceBindingGeneration("other-generation"))))
                .isEqualTo(BindingCurrentness.UNKNOWN);
        assertThat(factory.created.get(1).connections).isEmpty();

        manager.remove("orders");
        assertThat(resolver.currentness(second.identity())).isEqualTo(BindingCurrentness.STALE);
        manager.close();
    }

    @Test
    void processLocalDefaultUsesTheTrackedDefaultNamedBinding() {
        DataSourceConfigPersistence persistence = mock(DataSourceConfigPersistence.class);
        TrackingFactory factory = new TrackingFactory();
        DataSourceManager manager = manager(
                persistence, factory, new ManualDrainScheduler());
        manager.configure("default", config());
        NamedDataSourceResolverImpl resolver = new NamedDataSourceResolverImpl(manager);

        ResolvedDatasourceBinding binding = resolver.resolveProcessLocalDefaultBinding();

        assertThat(binding).isNotNull();
        assertThat(binding.cacheable()).isTrue();
        assertThat(binding.identity()).isEqualTo(manager.getBindingIdentity("default"));
        assertThat(resolver.currentness(binding.identity())).isEqualTo(BindingCurrentness.CURRENT);
        manager.close();
    }

    @Test
    void publicationGuardBlocksRebindCommitUntilCallbackCompletes() throws Exception {
        DataSourceConfigPersistence persistence = mock(DataSourceConfigPersistence.class);
        RebindReadyFactory factory = new RebindReadyFactory();
        DataSourceManager manager = new DataSourceManager(
                persistence, factory, new ManualDrainScheduler(),
                1_000L, "mcp-test-boot");
        manager.configure("orders", config());
        NamedDataSourceResolverImpl resolver = new NamedDataSourceResolverImpl(manager);
        DatasourceBindingIdentity captured = manager.getBindingIdentity("orders");
        CountDownLatch publicationEntered = new CountDownLatch(1);
        CountDownLatch releasePublication = new CountDownLatch(1);
        AtomicBoolean publicationCompleted = new AtomicBoolean();
        AtomicBoolean rebindObservedCompletedPublication = new AtomicBoolean();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> publication = null;
        Future<?> rebind = null;

        try {
            publication = executor.submit(() -> resolver.publishIfCurrent(
                    List.of(captured),
                    () -> {
                        publicationEntered.countDown();
                        awaitLatch(releasePublication, "guarded publication release");
                        publicationCompleted.set(true);
                        return "published";
                    }));
            assertThat(publicationEntered.await(5, TimeUnit.SECONDS)).isTrue();

            rebind = executor.submit(() -> {
                manager.configure("orders", config());
                rebindObservedCompletedPublication.set(publicationCompleted.get());
            });
            assertThat(factory.rebindPhysicalReady.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(rebind).isNotDone();

            releasePublication.countDown();
            assertThat(publication.get(5, TimeUnit.SECONDS)).isEqualTo("published");
            rebind.get(5, TimeUnit.SECONDS);

            DatasourceBindingIdentity rebound = manager.getBindingIdentity("orders");
            assertThat(rebindObservedCompletedPublication).isTrue();
            assertThat(rebound).isNotEqualTo(captured);
            assertThat(resolver.currentness(captured)).isEqualTo(BindingCurrentness.STALE);
            assertThat(resolver.currentness(rebound)).isEqualTo(BindingCurrentness.CURRENT);
            assertThatThrownBy(() -> resolver.publishIfCurrent(
                    List.of(captured), () -> "must-not-publish"))
                    .isInstanceOf(StaleDatasourceBindingException.class)
                    .hasMessage("STALE_DATASOURCE_BINDING: orders");

            AtomicBoolean unknownPublicationRan = new AtomicBoolean();
            DatasourceBindingIdentity unknown = new DatasourceBindingIdentity(
                    "orders", "other-backend",
                    new DatasourceBindingGeneration("other-generation"));
            assertThatThrownBy(() -> resolver.publishIfCurrent(
                    List.of(unknown), () -> {
                        unknownPublicationRan.set(true);
                        return "must-not-publish";
                    }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("DATASOURCE_BINDING_CURRENTNESS_UNKNOWN: orders");
            assertThat(unknownPublicationRan).isFalse();
            assertThatThrownBy(() -> resolver.publishIfCurrent(
                    java.util.Collections.singletonList(null),
                    () -> "must-not-publish"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("DATASOURCE_BINDING_CURRENTNESS_UNKNOWN: null");
        } finally {
            releasePublication.countDown();
            if (publication != null && !publication.isDone()) {
                publication.cancel(true);
            }
            if (rebind != null && !rebind.isDone()) {
                rebind.cancel(true);
            }
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            manager.close();
        }
    }

    private static DataSourceManager manager(
            DataSourceConfigPersistence persistence,
            TrackingFactory factory,
            ManualDrainScheduler scheduler) {
        return new DataSourceManager(persistence, factory, scheduler, 1_000L, "mcp-test-boot");
    }

    private static DataSourceManager.DataSourceConfig config() {
        return DataSourceManager.DataSourceConfig.builder()
                .host("db.internal")
                .port(5432)
                .database("analytics")
                .username("service")
                .password("secret")
                .driver("postgresql")
                .build();
    }

    private static void awaitLatch(CountDownLatch latch, String label) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for " + label);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted waiting for " + label, e);
        }
    }

    private static void assertRevoked(DataSource dataSource) {
        assertThatThrownBy(dataSource::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("DATASOURCE_BINDING_REVOKED");
    }

    private static final class TrackingFactory implements DataSourceManager.ManagedDataSourceFactory {
        private final List<TrackingDataSource> created = new ArrayList<>();

        @Override
        public DataSource create(DataSourceManager.DataSourceConfig config) {
            TrackingDataSource dataSource = new TrackingDataSource();
            created.add(dataSource);
            return dataSource;
        }
    }

    private static final class RebindReadyFactory
            implements DataSourceManager.ManagedDataSourceFactory {
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch rebindPhysicalReady = new CountDownLatch(1);

        @Override
        public DataSource create(DataSourceManager.DataSourceConfig config) {
            TrackingDataSource dataSource = new TrackingDataSource();
            if (calls.incrementAndGet() == 2) {
                rebindPhysicalReady.countDown();
            }
            return dataSource;
        }
    }

    private static final class OverlappingFactory implements DataSourceManager.ManagedDataSourceFactory {
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch firstCreationEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFirstCreation = new CountDownLatch(1);
        private final List<TrackingDataSource> created =
                java.util.Collections.synchronizedList(new ArrayList<>());

        @Override
        public DataSource create(DataSourceManager.DataSourceConfig config) {
            int call = calls.incrementAndGet();
            TrackingDataSource dataSource = new TrackingDataSource();
            created.add(dataSource);
            if (call == 1) {
                firstCreationEntered.countDown();
                try {
                    if (!releaseFirstCreation.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("first pool creation was not released");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("first pool creation interrupted", e);
                }
            }
            return dataSource;
        }
    }

    private static final class ManualDrainScheduler implements DataSourceManager.DrainScheduler {
        private final List<ManualTask> tasks = new ArrayList<>();

        @Override
        public DataSourceManager.DrainTask schedule(Runnable task, long delayMillis) {
            assertThat(delayMillis).isEqualTo(1_000L);
            ManualTask manualTask = new ManualTask(task);
            tasks.add(manualTask);
            return manualTask::cancel;
        }

        void runNext() {
            tasks.stream()
                    .filter(task -> !task.cancelled.get() && !task.ran.get())
                    .findFirst()
                    .orElseThrow()
                    .run();
        }
    }

    private static final class ManualTask {
        private final Runnable action;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean ran = new AtomicBoolean();

        private ManualTask(Runnable action) {
            this.action = action;
        }

        void cancel() {
            cancelled.set(true);
        }

        void run() {
            if (!cancelled.get() && ran.compareAndSet(false, true)) {
                action.run();
            }
        }
    }

    private static final class TrackingDataSource implements DataSource, AutoCloseable {
        private final List<TrackingConnection> connections = new ArrayList<>();
        private final AtomicInteger closeCalls = new AtomicInteger();
        private boolean closed;
        private PrintWriter logWriter;
        private int loginTimeout;

        @Override
        public Connection getConnection() throws SQLException {
            if (closed) {
                throw new SQLException("physical pool closed");
            }
            TrackingConnection connection = new TrackingConnection();
            connections.add(connection);
            return connection.proxy;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            logWriter = out;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            loginTimeout = seconds;
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
            closed = true;
        }
    }

    private static final class TrackingConnection {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicInteger closeCalls = new AtomicInteger();
        private final Connection proxy = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (instance, method, arguments) -> switch (method.getName()) {
                    case "close", "abort" -> {
                        closeCalls.incrementAndGet();
                        closed.set(true);
                        yield null;
                    }
                    case "isClosed" -> closed.get();
                    case "isValid" -> !closed.get();
                    case "getCatalog" -> "analytics";
                    case "unwrap" -> {
                        Class<?> iface = (Class<?>) arguments[0];
                        if (iface.isInstance(instance)) {
                            yield instance;
                        }
                        throw new SQLException("not a wrapper");
                    }
                    case "isWrapperFor" -> ((Class<?>) arguments[0]).isInstance(instance);
                    case "toString" -> "TrackingConnection";
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == arguments[0];
                    default -> defaultValue(method.getReturnType());
                });

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == char.class) {
                return '\0';
            }
            if (type == byte.class) {
                return (byte) 0;
            }
            if (type == short.class) {
                return (short) 0;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            if (type == float.class) {
                return 0F;
            }
            return 0D;
        }
    }
}
