package com.foggyframework.runtime.api.service;

import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.dataset.db.model.lifecycle.port.BindingAdmissionState;
import com.foggyframework.dataset.db.model.lifecycle.port.RevokeMode;
import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService.RuntimeDatasourceRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeDatasourceBindingLifecycleTest {

    private final List<TestContext> contexts = new ArrayList<>();

    @AfterEach
    void closeContexts() {
        for (TestContext context : contexts) {
            context.manager.destroy();
            context.scheduler.shutdownNow();
            context.factory.closeAll();
        }
    }

    @Test
    void changedGenerationPublishesDistinctHandleAndOldHandleCannotReopen() throws Exception {
        TestContext context = context();
        RuntimeDatasourceRecord original = record("sales", "physical-A", "epoch:1");
        RuntimeDatasourceRecord changed = record("sales", "physical-B", "epoch:2");
        DataSource originalHandle = context.manager.resolve(original);

        context.manager.onRecordSaved(original, changed);
        DataSource changedHandle = context.manager.resolve(changed);

        assertThat(changedHandle).isNotSameAs(originalHandle);
        assertThatThrownBy(originalHandle::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("DATASOURCE_BINDING_REVOKED");
        try (Connection connection = changedHandle.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo("physical-B");
        }
    }

    @Test
    void staleTrackedResolveCannotReplaceTheCurrentGeneration() {
        TestContext context = context();
        RuntimeDatasourceRecord original = record("sales", "physical-A", "epoch:1");
        RuntimeDatasourceRecord changed = record("sales", "physical-B", "epoch:2");
        context.manager.resolve(original);
        context.manager.onRecordSaved(original, changed);
        DataSource current = context.manager.resolve(changed);

        assertThatThrownBy(() -> context.manager.resolve(
                original,
                "runtime:named:sales",
                original.bindingGeneration()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATASOURCE_BINDING_NOT_CURRENT");
        assertThat(context.manager.resolve(
                changed,
                "runtime:named:sales",
                changed.bindingGeneration()
        )).isSameAs(current);
    }

    @Test
    void retiredTrackedGenerationCannotReopenWhenNoCurrentHandleExists() {
        TestContext context = context();
        RuntimeDatasourceRecord record = record("sales", "physical-A", "epoch:1");
        String namespaceKey = "runtime:namespace-default:tenant-a";
        context.manager.resolve(record, namespaceKey, "epoch:2");
        context.manager.onNamespaceBindingChanged("tenant-a");

        assertThatThrownBy(() -> context.manager.resolve(record, namespaceKey, "epoch:2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATASOURCE_BINDING_REVOKED");
    }

    @Test
    void drainRejectsNewBorrowButLetsHeldLeaseFinishThenClosesOldPoolOnce() throws Exception {
        TestContext context = context();
        RuntimeDatasourceRecord original = record("sales", "physical-A", "epoch:1");
        RuntimeDatasourceRecord changed = record("sales", "physical-B", "epoch:2");
        DataSource oldHandle = context.manager.resolve(original);
        Connection held = oldHandle.getConnection();
        TrackingPool oldPool = context.factory.created.get(0);

        context.manager.onRecordSaved(original, changed);

        assertThat(context.manager.admissionState("runtime:named:sales", "epoch:1"))
                .contains(BindingAdmissionState.RETIRING);
        assertThat(context.manager.activeLeases("runtime:named:sales", "epoch:1")).isEqualTo(1);
        assertThatThrownBy(oldHandle::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("DATASOURCE_BINDING_REVOKED");
        assertThat(held.getCatalog()).isEqualTo("physical-A");
        assertThat(oldPool.closed).isFalse();

        held.close();
        held.close();

        assertThat(context.manager.admissionState("runtime:named:sales", "epoch:1"))
                .contains(BindingAdmissionState.CLOSED);
        assertThat(oldPool.closed).isTrue();
        assertThat(oldPool.closeCount).isEqualTo(1);
    }

    @Test
    void controlledDeadlineForceClosesHeldLeaseWithoutRealWaiting() throws Exception {
        TestContext context = context();
        RuntimeDatasourceRecord original = record("sales", "physical-A", "epoch:1");
        RuntimeDatasourceRecord changed = record("sales", "physical-B", "epoch:2");
        DataSource oldHandle = context.manager.resolve(original);
        Connection held = oldHandle.getConnection();

        context.manager.onRecordSaved(original, changed);
        context.clock.advance(Duration.ofSeconds(1));
        context.scheduler.runNext();

        assertThat(held.isClosed()).isTrue();
        assertThat(context.manager.activeLeases("runtime:named:sales", "epoch:1")).isZero();
        assertThat(context.manager.admissionState("runtime:named:sales", "epoch:1"))
                .contains(BindingAdmissionState.CLOSED);
        assertThat(context.factory.created.get(0).closeCount).isEqualTo(1);
    }

    @Test
    void rejectedDrainDeadlineRevokesEveryAffectedBindingWithoutFailingTheCommit() throws Exception {
        TestContext context = context();
        RuntimeDatasourceRecord original = record("sales", "physical-A", "epoch:1");
        RuntimeDatasourceRecord changed = record("sales", "physical-B", "epoch:2");
        String tenantAKey = "runtime:namespace-default:tenant-a";
        String tenantBKey = "runtime:namespace-default:tenant-b";
        DataSource named = context.manager.resolve(original);
        DataSource tenantA = context.manager.resolve(original, tenantAKey, "tenant-a:1");
        DataSource tenantB = context.manager.resolve(original, tenantBKey, "tenant-b:1");
        Connection namedLease = named.getConnection();
        Connection tenantALease = tenantA.getConnection();
        Connection tenantBLease = tenantB.getConnection();
        context.scheduler.rejectNextSchedule();

        assertThatCode(() -> context.manager.onRecordSaved(original, changed))
                .doesNotThrowAnyException();

        assertThat(namedLease.isClosed()).isTrue();
        assertThat(tenantALease.isClosed()).isFalse();
        assertThat(tenantBLease.isClosed()).isFalse();
        assertThat(context.manager.admissionState("runtime:named:sales", "epoch:1"))
                .contains(BindingAdmissionState.CLOSED);
        assertThat(context.manager.admissionState(tenantAKey, "tenant-a:1"))
                .contains(BindingAdmissionState.RETIRING);
        assertThat(context.manager.admissionState(tenantBKey, "tenant-b:1"))
                .contains(BindingAdmissionState.RETIRING);
        assertThatThrownBy(named::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("DATASOURCE_BINDING_REVOKED");
        assertThatThrownBy(tenantA::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("DATASOURCE_BINDING_REVOKED");
        assertThatThrownBy(tenantB::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("DATASOURCE_BINDING_REVOKED");

        tenantALease.close();
        tenantBLease.close();
        assertThat(context.manager.admissionState(tenantAKey, "tenant-a:1"))
                .contains(BindingAdmissionState.CLOSED);
        assertThat(context.manager.admissionState(tenantBKey, "tenant-b:1"))
                .contains(BindingAdmissionState.CLOSED);
    }

    @Test
    void hardRevokeImmediatelyClosesHeldLease() throws Exception {
        TestContext context = context();
        RuntimeDatasourceRecord original = record("sales", "physical-A", "epoch:1");
        RuntimeDatasourceRecord changed = record("sales", "physical-B", "epoch:2");
        DataSource oldHandle = context.manager.resolve(original);
        Connection held = oldHandle.getConnection();

        context.manager.onRecordSaved(original, changed, RevokeMode.HARD);

        assertThat(held.isClosed()).isTrue();
        assertThat(context.manager.activeLeases("runtime:named:sales", "epoch:1")).isZero();
        assertThatThrownBy(oldHandle::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("DATASOURCE_BINDING_REVOKED");
    }

    @Test
    void borrowThatLinearizesBeforeCommitStaysOnOldBackendAndCannotAffectNewCount() throws Exception {
        TestContext context = context();
        RuntimeDatasourceRecord original = record("sales", "physical-A", "epoch:1");
        RuntimeDatasourceRecord changed = record("sales", "physical-B", "epoch:2");
        DataSource oldHandle = context.manager.resolve(original);
        TrackingPool oldPool = context.factory.created.get(0);
        CountDownLatch enteredPoolBorrow = new CountDownLatch(1);
        CountDownLatch releasePoolBorrow = new CountDownLatch(1);
        oldPool.beforeBorrow = () -> {
            enteredPoolBorrow.countDown();
            await(releasePoolBorrow);
        };
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        Future<Connection> future = executor.submit((Callable<Connection>) oldHandle::getConnection);

        assertThat(enteredPoolBorrow.await(5, TimeUnit.SECONDS)).isTrue();
        context.manager.onRecordSaved(original, changed);
        DataSource newHandle = context.manager.resolve(changed);
        releasePoolBorrow.countDown();
        Connection oldConnection = future.get(5, TimeUnit.SECONDS);

        assertThat(oldConnection.getCatalog()).isEqualTo("physical-A");
        try (Connection newConnection = newHandle.getConnection()) {
            assertThat(newConnection.getCatalog()).isEqualTo("physical-B");
            assertThat(context.manager.activeLeases("runtime:named:sales", "epoch:2")).isEqualTo(1);
        }
        oldConnection.close();
        assertThat(context.manager.activeLeases("runtime:named:sales", "epoch:1")).isZero();
        assertThat(context.manager.activeLeases("runtime:named:sales", "epoch:2")).isZero();
        executor.shutdownNow();
    }

    @Test
    void namespaceRebindRetiresOnlyNamespaceHandleAndKeepsNamedBackendUsable() throws Exception {
        TestContext context = context();
        RuntimeDatasourceRecord record = record("sales", "physical-A", "epoch:1");
        DataSource named = context.manager.resolve(record);
        DataSource oldNamespace = context.manager.resolve(
                record,
                "runtime:namespace-default:tenant-a",
                "epoch:2"
        );

        context.manager.onNamespaceBindingChanged("tenant-a");
        DataSource newNamespace = context.manager.resolve(
                record,
                "runtime:namespace-default:tenant-a",
                "epoch:3"
        );

        assertThat(newNamespace).isNotSameAs(oldNamespace);
        assertThatThrownBy(oldNamespace::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("DATASOURCE_BINDING_REVOKED");
        try (Connection namedConnection = named.getConnection();
             Connection namespaceConnection = newNamespace.getConnection()) {
            assertThat(namedConnection.getCatalog()).isEqualTo("physical-A");
            assertThat(namespaceConnection.getCatalog()).isEqualTo("physical-A");
        }
        assertThat(context.factory.created).hasSize(1);
        assertThat(context.factory.created.get(0).closed).isFalse();
    }

    @Test
    void leaseDrainTimeoutOutsideFrozenRangeFailsStartup() {
        FoggyRuntimeApiProperties below = properties();
        below.getDatasourcePool().setLeaseDrainTimeoutMs(999);
        ManagedDataSourcePoolManager belowManager = new ManagedDataSourcePoolManager(
                below, new TrackingPoolFactory(), Clock.systemUTC(), new ManualScheduler(), false);
        assertThatThrownBy(belowManager::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease-drain-timeout-ms");

        FoggyRuntimeApiProperties above = properties();
        above.getDatasourcePool().setLeaseDrainTimeoutMs(300_001);
        ManagedDataSourcePoolManager aboveManager = new ManagedDataSourcePoolManager(
                above, new TrackingPoolFactory(), Clock.systemUTC(), new ManualScheduler(), false);
        assertThatThrownBy(aboveManager::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease-drain-timeout-ms");
    }

    @Test
    void realH2QueryPinsOldAndNewPhysicalResultsAcrossRebind() throws Exception {
        String oldUrl = "jdbc:h2:mem:runtime_binding_old;DB_CLOSE_DELAY=-1";
        String newUrl = "jdbc:h2:mem:runtime_binding_new;DB_CLOSE_DELAY=-1";
        createSentinel(oldUrl, "OLD");
        createSentinel(newUrl, "NEW");
        MutableClock clock = new MutableClock();
        ManualScheduler scheduler = new ManualScheduler();
        ManagedDataSourcePoolManager manager = new ManagedDataSourcePoolManager(
                properties(), new HikariManagedDataSourcePoolFactory(), clock, scheduler, false);
        TestContext cleanup = new TestContext(manager, new TrackingPoolFactory(), clock, scheduler);
        contexts.add(cleanup);
        RuntimeDatasourceRecord oldRecord = h2Record("sales", oldUrl, "epoch:1");
        RuntimeDatasourceRecord newRecord = h2Record("sales", newUrl, "epoch:2");
        DataSource oldHandle = manager.resolve(oldRecord);
        Connection heldOld = oldHandle.getConnection();

        manager.onRecordSaved(oldRecord, newRecord);
        DataSource newHandle = manager.resolve(newRecord);

        assertThat(readSentinel(heldOld)).isEqualTo("OLD");
        try (Connection newConnection = newHandle.getConnection()) {
            assertThat(readSentinel(newConnection)).isEqualTo("NEW");
        }
        assertThatThrownBy(oldHandle::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("DATASOURCE_BINDING_REVOKED");
        heldOld.close();
    }

    private TestContext context() {
        MutableClock clock = new MutableClock();
        ManualScheduler scheduler = new ManualScheduler();
        TrackingPoolFactory factory = new TrackingPoolFactory();
        ManagedDataSourcePoolManager manager = new ManagedDataSourcePoolManager(
                properties(), factory, clock, scheduler, false);
        TestContext context = new TestContext(manager, factory, clock, scheduler);
        contexts.add(context);
        return context;
    }

    private static FoggyRuntimeApiProperties properties() {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getDatasourcePool().setLeaseDrainTimeoutMs(1_000);
        properties.getDatasourcePool().setIdlePoolCloseMinutes(15);
        properties.getDatasourcePool().setCleanupIntervalMinutes(1);
        return properties;
    }

    private static RuntimeDatasourceRecord record(String name, String physical, String generation) {
        return new RuntimeDatasourceRecord(
                name, "h2", "jdbc:stub:" + physical, "sa", null, null,
                true, "now", "now", generation);
    }

    private static RuntimeDatasourceRecord h2Record(String name, String url, String generation) {
        return new RuntimeDatasourceRecord(
                name, "h2", url, "sa", null, null,
                true, "now", "now", generation);
    }

    private static void createSentinel(String url, String value) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("create table binding_sentinel(marker varchar(16) not null)");
            statement.execute("insert into binding_sentinel(marker) values ('" + value + "')");
        }
    }

    private static String readSentinel(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select marker from binding_sentinel")) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("bounded test barrier timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while awaiting test barrier", e);
        }
    }

    private record TestContext(
            ManagedDataSourcePoolManager manager,
            TrackingPoolFactory factory,
            MutableClock clock,
            ManualScheduler scheduler
    ) {
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-07-13T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }

    private static final class ManualScheduler extends ScheduledThreadPoolExecutor {
        private final List<ManualFuture<?>> scheduled = new ArrayList<>();
        private int rejectedSchedules;

        private ManualScheduler() {
            super(1);
        }

        @Override
        public synchronized ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            if (rejectedSchedules > 0) {
                rejectedSchedules--;
                throw new RejectedExecutionException("synthetic first deadline rejection");
            }
            ManualFuture<Void> future = new ManualFuture<>(command, unit.toMillis(delay));
            scheduled.add(future);
            return future;
        }

        private synchronized void rejectNextSchedule() {
            rejectedSchedules++;
        }

        private synchronized void runNext() {
            ManualFuture<?> future = scheduled.stream()
                    .filter(value -> !value.isCancelled() && !value.isDone())
                    .findFirst()
                    .orElseThrow();
            future.run();
        }
    }

    private static final class ManualFuture<V> extends FutureTask<V> implements ScheduledFuture<V> {
        private final long delayMs;

        @SuppressWarnings("unchecked")
        private ManualFuture(Runnable command, long delayMs) {
            super(command, null);
            this.delayMs = delayMs;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(delayMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS));
        }
    }

    private static final class TrackingPoolFactory implements ManagedDataSourcePoolFactory {
        private final List<TrackingPool> created = new ArrayList<>();

        @Override
        public synchronized ManagedDataSourcePool create(
                RuntimeDatasourceRecord record,
                String password,
                ManagedDataSourcePoolSettings settings
        ) {
            TrackingPool pool = new TrackingPool(record.jdbcUrl().substring("jdbc:stub:".length()));
            created.add(pool);
            return pool;
        }

        private synchronized void closeAll() {
            created.forEach(TrackingPool::close);
        }
    }

    private static final class TrackingPool implements ManagedDataSourcePool {
        private final String physical;
        private final AtomicInteger active = new AtomicInteger();
        private volatile Runnable beforeBorrow;
        private boolean closed;
        private int closeCount;

        private TrackingPool(String physical) {
            this.physical = physical;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Runnable callback = beforeBorrow;
            if (callback != null) {
                callback.run();
            }
            if (closed) {
                throw new SQLException("pool is closed");
            }
            active.incrementAndGet();
            return connection(physical, active);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
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
            if (!closed) {
                closed = true;
                closeCount++;
            }
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public int activeConnections() {
            return active.get();
        }
    }

    private static Connection connection(String physical, AtomicInteger active) {
        AtomicBoolean closed = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(
                RuntimeDatasourceBindingLifecycleTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "close" -> {
                        if (closed.compareAndSet(false, true)) {
                            active.decrementAndGet();
                        }
                        yield null;
                    }
                    case "isClosed" -> closed.get();
                    case "getCatalog" -> physical;
                    case "isWrapperFor" -> false;
                    case "unwrap" -> throw new SQLException("not a wrapper");
                    case "toString" -> "OpaqueTrackingConnection";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(
                            "Unexpected connection method: " + method.getName());
                }
        );
    }
}
