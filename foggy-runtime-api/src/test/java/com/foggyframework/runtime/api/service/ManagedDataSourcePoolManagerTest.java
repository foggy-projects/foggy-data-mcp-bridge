package com.foggyframework.runtime.api.service;

import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService.RuntimeDatasourceRecord;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
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

class ManagedDataSourcePoolManagerTest {

    @Test
    void shouldCreatePoolLazilyOnResolveAndReuseLivePool() {
        TestContext context = newContext();
        RuntimeDatasourceRecord record = record("sales-h2", "jdbc:h2:mem:sales_reuse");

        DataSource first = context.manager.resolve(record);
        DataSource second = context.manager.resolve(record.withUpdatedAt("later"));

        assertThat(first).isSameAs(second);
        assertThat(context.factory.created).hasSize(1);
        assertThat(context.manager.state("sales-h2").orElseThrow().lifecycleStatus()).isEqualTo("live");
        assertThat(context.manager.state("sales-h2").orElseThrow().poolExists()).isTrue();
    }

    @Test
    void shouldCloseIdlePoolKeepSlotAndRecreateOnNextResolve() throws Exception {
        TestContext context = newContext();
        RuntimeDatasourceRecord record = record("sales-h2", "jdbc:h2:mem:sales_idle");
        DataSource dataSource = context.manager.resolve(record);
        FakeManagedDataSourcePool firstPool = context.factory.created.get(0);

        Connection connection = dataSource.getConnection();
        connection.close();
        connection.close();
        context.clock.advance(Duration.ofMinutes(16));
        context.manager.runIdleCleanup();

        ManagedDataSourcePoolManager.ManagedDataSourcePoolState closedState =
                context.manager.state("sales-h2").orElseThrow();
        assertThat(firstPool.closed).isTrue();
        assertThat(firstPool.closeCount).isEqualTo(1);
        assertThat(closedState.lifecycleStatus()).isEqualTo("idle-closed");
        assertThat(closedState.poolExists()).isFalse();
        assertThat(closedState.poolClosed()).isTrue();
        assertThat(closedState.activeConnections()).isZero();

        DataSource recreated = context.manager.resolve(record);

        assertThat(recreated).isSameAs(dataSource);
        assertThat(context.factory.created).hasSize(2);
        assertThat(context.factory.created.get(1)).isNotSameAs(firstPool);
        assertThat(context.manager.state("sales-h2").orElseThrow().lifecycleStatus()).isEqualTo("live");
    }

    @Test
    void shouldNotCloseActiveOrLongRunningBorrowedConnectionUntilReturnedAndIdle() throws Exception {
        TestContext context = newContext();
        RuntimeDatasourceRecord record = record("sales-h2", "jdbc:h2:mem:sales_active");
        DataSource dataSource = context.manager.resolve(record);
        FakeManagedDataSourcePool pool = context.factory.created.get(0);

        Connection connection = dataSource.getConnection();
        context.clock.advance(Duration.ofMinutes(30));
        context.manager.runIdleCleanup();

        assertThat(pool.closed).isFalse();
        assertThat(context.manager.state("sales-h2").orElseThrow().activeConnections()).isEqualTo(1);

        connection.close();
        context.manager.runIdleCleanup();
        assertThat(pool.closed).isFalse();

        context.clock.advance(Duration.ofMinutes(16));
        context.manager.runIdleCleanup();
        assertThat(pool.closed).isTrue();
        assertThat(context.manager.state("sales-h2").orElseThrow().lifecycleStatus()).isEqualTo("idle-closed");
    }

    @Test
    void shouldNotCloseIdlePoolWhenCleanupRunsDuringConnectionBorrow() throws Exception {
        TestContext context = newContext();
        RuntimeDatasourceRecord record = record("sales-h2", "jdbc:h2:mem:sales_borrow_cleanup");
        DataSource dataSource = context.manager.resolve(record);
        FakeManagedDataSourcePool pool = context.factory.created.get(0);
        context.clock.advance(Duration.ofMinutes(16));
        pool.beforeGetConnection = context.manager::runIdleCleanup;

        Connection connection = dataSource.getConnection();

        ManagedDataSourcePoolManager.ManagedDataSourcePoolState state =
                context.manager.state("sales-h2").orElseThrow();
        assertThat(pool.closed).isFalse();
        assertThat(state.lifecycleStatus()).isEqualTo("live");
        assertThat(state.poolExists()).isTrue();
        assertThat(state.activeConnections()).isEqualTo(1);

        connection.close();
    }

    @Test
    void shouldKeepPoolForSameFingerprintAndCloseForChangedFingerprint() {
        TestContext context = newContext();
        RuntimeDatasourceRecord original = record("sales-h2", "jdbc:h2:mem:sales_original");
        context.manager.resolve(original);
        FakeManagedDataSourcePool firstPool = context.factory.created.get(0);

        RuntimeDatasourceRecord sameConfig = new RuntimeDatasourceRecord(
                original.name(), original.type(), original.jdbcUrl(), original.username(), original.password(),
                original.passwordRef(), original.enabled(), original.createdAt(), "later");
        context.manager.onRecordSaved(original, sameConfig);

        assertThat(firstPool.closed).isFalse();
        assertThat(context.factory.created).hasSize(1);

        RuntimeDatasourceRecord changed = record("sales-h2", "jdbc:h2:mem:sales_changed");
        context.manager.onRecordSaved(sameConfig, changed);

        assertThat(firstPool.closed).isTrue();
        assertThat(context.manager.state("sales-h2").orElseThrow().lifecycleStatus()).isEqualTo("config-changed");
        assertThat(context.manager.state("sales-h2").orElseThrow().poolExists()).isFalse();

        context.manager.resolve(changed);
        assertThat(context.factory.created).hasSize(2);
    }

    @Test
    void shouldClosePoolOnRemoveAndShutdownExactlyOnce() {
        TestContext context = newContext();
        RuntimeDatasourceRecord first = record("first-h2", "jdbc:h2:mem:first");
        RuntimeDatasourceRecord second = record("second-h2", "jdbc:h2:mem:second");
        context.manager.resolve(first);
        context.manager.resolve(second);
        FakeManagedDataSourcePool firstPool = context.factory.created.get(0);
        FakeManagedDataSourcePool secondPool = context.factory.created.get(1);

        context.manager.remove("first-h2");
        context.manager.closeAll();
        context.manager.closeAll();

        assertThat(context.manager.state("first-h2")).isEmpty();
        assertThat(firstPool.closed).isTrue();
        assertThat(firstPool.closeCount).isEqualTo(1);
        assertThat(secondPool.closed).isTrue();
        assertThat(secondPool.closeCount).isEqualTo(1);
    }

    @Test
    void shouldNotResolveUnresolvedPasswordRefDuringSaveButFailOnResolve() {
        TestContext context = newContext();
        RuntimeDatasourceRecord record = new RuntimeDatasourceRecord(
                "missing-ref",
                "h2",
                "jdbc:h2:mem:missing_ref",
                "sa",
                null,
                "env:FOGGY_MISSING_PASSWORD_FOR_POOL_MANAGER_TEST",
                true,
                "now",
                "now"
        );

        context.manager.onRecordSaved(null, record);

        assertThat(context.factory.created).isEmpty();
        assertThatThrownBy(() -> context.manager.resolve(record))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("passwordRef could not be resolved");
    }

    @Test
    void shouldApplySQLiteSingleConnectionPoolSettings() {
        TestContext context = newContext();
        RuntimeDatasourceRecord record = record("sales-sqlite", "jdbc:sqlite:target/sales.db");

        ManagedDataSourcePoolSettings settings = context.manager.settingsFor(record);
        context.manager.resolve(record);

        assertThat(settings.maximumPoolSize()).isEqualTo(1);
        assertThat(settings.minimumIdle()).isZero();
        assertThat(context.factory.created.get(0).settings.maximumPoolSize()).isEqualTo(1);
        assertThat(context.factory.created.get(0).settings.minimumIdle()).isZero();
    }

    @Test
    void shouldTrackBorrowReturnTimestampsAndMakeWrappedConnectionCloseIdempotent() throws Exception {
        TestContext context = newContext();
        RuntimeDatasourceRecord record = record("sales-h2", "jdbc:h2:mem:sales_timestamps");
        DataSource dataSource = context.manager.resolve(record);

        Connection connection = dataSource.getConnection();
        ManagedDataSourcePoolManager.ManagedDataSourcePoolState borrowed =
                context.manager.state("sales-h2").orElseThrow();
        assertThat(borrowed.lastBorrowedAt()).isEqualTo("2026-07-03T00:00:00Z");
        assertThat(borrowed.activeConnections()).isEqualTo(1);

        context.clock.advance(Duration.ofMinutes(2));
        connection.close();
        connection.close();

        ManagedDataSourcePoolManager.ManagedDataSourcePoolState returned =
                context.manager.state("sales-h2").orElseThrow();
        assertThat(returned.lastReturnedAt()).isEqualTo("2026-07-03T00:02:00Z");
        assertThat(returned.activeConnections()).isZero();
        assertThat(context.factory.created.get(0).activeConnections()).isZero();
    }

    @Test
    void shouldCreateOnlyOnePoolWhenResolveIsConcurrent() throws Exception {
        TestContext context = newContext();
        RuntimeDatasourceRecord record = record("sales-h2", "jdbc:h2:mem:sales_concurrent");
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<DataSource>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return context.manager.resolve(record);
            });
        }

        List<Future<DataSource>> futures = new ArrayList<>();
        for (Callable<DataSource> task : tasks) {
            futures.add(executor.submit(task));
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        DataSource first = futures.get(0).get(5, TimeUnit.SECONDS);
        for (Future<DataSource> future : futures) {
            assertThat(future.get(5, TimeUnit.SECONDS)).isSameAs(first);
        }
        executor.shutdownNow();

        assertThat(context.factory.created).hasSize(1);
    }

    @Test
    void shouldCreateOnlyOnePoolWhenBorrowIsConcurrentAfterIdleClose() throws Exception {
        TestContext context = newContext();
        RuntimeDatasourceRecord record = record("sales-h2", "jdbc:h2:mem:sales_concurrent_borrow");
        DataSource dataSource = context.manager.resolve(record);
        Connection connection = dataSource.getConnection();
        connection.close();
        context.clock.advance(Duration.ofMinutes(16));
        context.manager.runIdleCleanup();
        assertThat(context.manager.state("sales-h2").orElseThrow().lifecycleStatus()).isEqualTo("idle-closed");
        context.factory.createDelayMillis = 30;

        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch ready = new CountDownLatch(12);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                try (Connection borrowed = dataSource.getConnection()) {
                    return null;
                }
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<Void> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        assertThat(context.factory.created).hasSize(2);
        assertThat(context.manager.state("sales-h2").orElseThrow().poolExists()).isTrue();
        assertThat(context.manager.state("sales-h2").orElseThrow().activeConnections()).isZero();
    }

    @Test
    void shouldUseRealHikariPoolForH2ConnectionAndIdleCleanup() throws Exception {
        MutableClock clock = new MutableClock();
        FoggyRuntimeApiProperties properties = properties();
        ManagedDataSourcePoolManager manager = new ManagedDataSourcePoolManager(
                properties,
                new HikariManagedDataSourcePoolFactory(),
                clock,
                null,
                false
        );
        RuntimeDatasourceRecord record = record("real-h2", "jdbc:h2:mem:real_h2_pool;DB_CLOSE_DELAY=-1");
        DataSource dataSource = manager.resolve(record);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("create table probe(id int primary key)");
            statement.execute("insert into probe(id) values (1)");
        }

        clock.advance(Duration.ofMinutes(16));
        manager.runIdleCleanup();

        ManagedDataSourcePoolManager.ManagedDataSourcePoolState state = manager.state("real-h2").orElseThrow();
        assertThat(state.lifecycleStatus()).isEqualTo("idle-closed");
        assertThat(state.poolExists()).isFalse();
        manager.destroy();
    }

    private static TestContext newContext() {
        MutableClock clock = new MutableClock();
        FakeManagedDataSourcePoolFactory factory = new FakeManagedDataSourcePoolFactory();
        ManagedDataSourcePoolManager manager = new ManagedDataSourcePoolManager(
                properties(),
                factory,
                clock,
                null,
                false
        );
        return new TestContext(manager, factory, clock);
    }

    private static FoggyRuntimeApiProperties properties() {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        FoggyRuntimeApiProperties.DatasourcePool pool = properties.getDatasourcePool();
        pool.setIdlePoolCloseMinutes(15);
        pool.setCleanupIntervalMinutes(1);
        pool.setMaximumPoolSize(4);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeoutMs(10000);
        pool.setIdleTimeoutMs(120000);
        pool.setMaxLifetimeMs(1800000);
        return properties;
    }

    private static RuntimeDatasourceRecord record(String name, String jdbcUrl) {
        return new RuntimeDatasourceRecord(
                name,
                jdbcUrl.startsWith("jdbc:sqlite:") ? "sqlite" : "h2",
                jdbcUrl,
                jdbcUrl.startsWith("jdbc:sqlite:") ? null : "sa",
                null,
                null,
                true,
                "now",
                "now"
        );
    }

    private record TestContext(
            ManagedDataSourcePoolManager manager,
            FakeManagedDataSourcePoolFactory factory,
            MutableClock clock
    ) {
    }

    private static final class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-07-03T00:00:00Z");

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

    private static final class FakeManagedDataSourcePoolFactory implements ManagedDataSourcePoolFactory {

        private final List<FakeManagedDataSourcePool> created = Collections.synchronizedList(new ArrayList<>());
        private volatile long createDelayMillis;

        @Override
        public ManagedDataSourcePool create(
                RuntimeDatasourceRecord record,
                String password,
                ManagedDataSourcePoolSettings settings
        ) {
            if (createDelayMillis > 0) {
                try {
                    Thread.sleep(createDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while creating fake pool", e);
                }
            }
            FakeManagedDataSourcePool pool = new FakeManagedDataSourcePool(record, password, settings);
            created.add(pool);
            return pool;
        }
    }

    private static final class FakeManagedDataSourcePool implements ManagedDataSourcePool {

        private final RuntimeDatasourceRecord record;
        private final String password;
        private final ManagedDataSourcePoolSettings settings;
        private final AtomicInteger activeConnections = new AtomicInteger();
        private volatile Runnable beforeGetConnection;
        private boolean closed;
        private int closeCount;

        private FakeManagedDataSourcePool(
                RuntimeDatasourceRecord record,
                String password,
                ManagedDataSourcePoolSettings settings
        ) {
            this.record = record;
            this.password = password;
            this.settings = settings;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return getConnection(record.username(), password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Runnable callback = beforeGetConnection;
            if (callback != null) {
                callback.run();
            }
            if (closed) {
                throw new SQLException("pool is closed");
            }
            activeConnections.incrementAndGet();
            return fakeConnection(activeConnections);
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
            return activeConnections.get();
        }
    }

    private static Connection fakeConnection(AtomicInteger activeConnections) {
        InvocationHandler handler = new InvocationHandler() {
            private final AtomicBoolean closed = new AtomicBoolean();

            @Override
            public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                if ("close".equals(method.getName()) && method.getParameterCount() == 0) {
                    if (closed.compareAndSet(false, true)) {
                        activeConnections.decrementAndGet();
                    }
                    return null;
                }
                if ("isClosed".equals(method.getName()) && method.getParameterCount() == 0) {
                    return closed.get();
                }
                if ("equals".equals(method.getName()) && method.getParameterCount() == 1) {
                    return proxy == args[0];
                }
                if ("hashCode".equals(method.getName()) && method.getParameterCount() == 0) {
                    return System.identityHashCode(proxy);
                }
                if ("toString".equals(method.getName()) && method.getParameterCount() == 0) {
                    return "FakeConnection";
                }
                Class<?> returnType = method.getReturnType();
                if (!returnType.isPrimitive()) {
                    return null;
                }
                if (boolean.class.equals(returnType)) {
                    return false;
                }
                if (byte.class.equals(returnType)) {
                    return (byte) 0;
                }
                if (short.class.equals(returnType)) {
                    return (short) 0;
                }
                if (int.class.equals(returnType)) {
                    return 0;
                }
                if (long.class.equals(returnType)) {
                    return 0L;
                }
                if (float.class.equals(returnType)) {
                    return 0F;
                }
                if (double.class.equals(returnType)) {
                    return 0D;
                }
                if (char.class.equals(returnType)) {
                    return '\0';
                }
                return null;
            }
        };
        return (Connection) Proxy.newProxyInstance(
                ManagedDataSourcePoolManagerTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                handler
        );
    }
}
