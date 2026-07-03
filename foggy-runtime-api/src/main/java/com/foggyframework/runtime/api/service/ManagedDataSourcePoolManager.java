package com.foggyframework.runtime.api.service;

import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService.RuntimeDatasourceRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class ManagedDataSourcePoolManager {

    private final FoggyRuntimeApiProperties properties;
    private final ManagedDataSourcePoolFactory poolFactory;
    private final Clock clock;
    private final Map<String, PoolSlot> slots = new LinkedHashMap<>();
    private final boolean autoStartScheduler;
    private ScheduledExecutorService scheduler;
    private boolean ownsScheduler;

    @Autowired
    public ManagedDataSourcePoolManager(
            FoggyRuntimeApiProperties properties,
            ManagedDataSourcePoolFactory poolFactory
    ) {
        this(properties, poolFactory, Clock.systemUTC(), null, true);
    }

    ManagedDataSourcePoolManager(
            FoggyRuntimeApiProperties properties,
            ManagedDataSourcePoolFactory poolFactory,
            Clock clock,
            ScheduledExecutorService scheduler,
            boolean autoStartScheduler
    ) {
        this.properties = properties;
        this.poolFactory = poolFactory;
        this.clock = clock;
        this.scheduler = scheduler;
        this.autoStartScheduler = autoStartScheduler;
    }

    @PostConstruct
    public synchronized void start() {
        FoggyRuntimeApiProperties.DatasourcePool poolProperties = poolProperties();
        if (!autoStartScheduler || !poolProperties.isCleanupEnabled() || poolProperties.getCleanupIntervalMinutes() <= 0) {
            return;
        }
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "foggy-runtime-datasource-pool-cleaner");
                thread.setDaemon(true);
                return thread;
            });
            ownsScheduler = true;
        }
        long interval = Math.max(1, poolProperties.getCleanupIntervalMinutes());
        scheduler.scheduleAtFixedRate(this::runIdleCleanupSafely, interval, interval, TimeUnit.MINUTES);
    }

    public DataSource resolve(RuntimeDatasourceRecord record) {
        Objects.requireNonNull(record, "record");
        if (!record.enabled()) {
            throw new IllegalArgumentException("Runtime-managed dataSource is disabled: " + record.name());
        }
        synchronized (this) {
            PoolSlot slot = slots.computeIfAbsent(record.name(), ignored -> new PoolSlot(record));
            slot.updateRecord(record);
            slot.ensurePool();
            return slot.dataSource();
        }
    }

    public synchronized void onRecordSaved(RuntimeDatasourceRecord previous, RuntimeDatasourceRecord saved) {
        Objects.requireNonNull(saved, "saved");
        PoolSlot slot = slots.get(saved.name());
        if (slot == null) {
            return;
        }
        slot.updateRecord(saved);
        if (!saved.enabled()) {
            slot.closePool("disabled");
        }
    }

    public synchronized void remove(String name) {
        PoolSlot slot = slots.remove(name);
        if (slot != null) {
            slot.closePool("removed");
        }
    }

    public synchronized void runIdleCleanup() {
        for (PoolSlot slot : slots.values()) {
            slot.closeIfIdle();
        }
    }

    public synchronized void closeAll() {
        for (PoolSlot slot : slots.values()) {
            slot.closePool("shutdown");
        }
    }

    @PreDestroy
    public synchronized void destroy() {
        closeAll();
        if (ownsScheduler && scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public synchronized Optional<ManagedDataSourcePoolState> state(String name) {
        PoolSlot slot = slots.get(name);
        if (slot == null) {
            return Optional.empty();
        }
        return Optional.of(slot.state());
    }

    public ManagedDataSourcePoolSettings settingsFor(RuntimeDatasourceRecord record) {
        FoggyRuntimeApiProperties.DatasourcePool poolProperties = poolProperties();
        int maximumPoolSize = Math.max(1, poolProperties.getMaximumPoolSize());
        int minimumIdle = Math.max(0, Math.min(poolProperties.getMinimumIdle(), maximumPoolSize));
        if (isSqlite(record.jdbcUrl())) {
            maximumPoolSize = 1;
            minimumIdle = 0;
        }
        return new ManagedDataSourcePoolSettings(
                Math.max(1, poolProperties.getIdlePoolCloseMinutes()),
                Math.max(1, poolProperties.getCleanupIntervalMinutes()),
                maximumPoolSize,
                minimumIdle,
                Math.max(1, poolProperties.getConnectionTimeoutMs()),
                Math.max(1, poolProperties.getIdleTimeoutMs()),
                Math.max(1, poolProperties.getMaxLifetimeMs()),
                driverClassNameFor(record.jdbcUrl())
        );
    }

    private void runIdleCleanupSafely() {
        try {
            runIdleCleanup();
        } catch (RuntimeException ignored) {
            // The next scheduled pass can retry; lifecycle state is kept in each slot.
        }
    }

    private FoggyRuntimeApiProperties.DatasourcePool poolProperties() {
        if (properties.getDatasourcePool() == null) {
            properties.setDatasourcePool(new FoggyRuntimeApiProperties.DatasourcePool());
        }
        return properties.getDatasourcePool();
    }

    private String resolvePassword(RuntimeDatasourceRecord record) {
        if (record.password() != null) {
            return record.password();
        }
        String ref = record.passwordRef();
        if (!StringUtils.hasText(ref)) {
            return null;
        }
        String resolved = resolvePasswordRef(ref);
        if (resolved == null) {
            throw new IllegalArgumentException("Runtime-managed dataSource passwordRef could not be resolved: " + ref);
        }
        return resolved;
    }

    private String resolvePasswordRef(String ref) {
        if (ref.startsWith("env:")) {
            return System.getenv(ref.substring("env:".length()));
        }
        if (ref.startsWith("system:")) {
            return System.getProperty(ref.substring("system:".length()));
        }
        if (ref.startsWith("sys:")) {
            return System.getProperty(ref.substring("sys:".length()));
        }
        String envValue = System.getenv(ref);
        if (envValue != null) {
            return envValue;
        }
        return System.getProperty(ref);
    }

    private String fingerprint(RuntimeDatasourceRecord record, ManagedDataSourcePoolSettings settings) {
        String credentialIdentity = StringUtils.hasText(record.passwordRef())
                ? "ref:" + sha256(record.passwordRef())
                : "password:" + sha256(record.password() == null ? "" : record.password());
        String source = String.join("|",
                nullToEmpty(record.type()).toLowerCase(Locale.ROOT),
                nullToEmpty(record.jdbcUrl()),
                nullToEmpty(record.username()),
                credentialIdentity,
                String.valueOf(settings.idlePoolCloseMinutes()),
                String.valueOf(settings.cleanupIntervalMinutes()),
                nullToEmpty(settings.driverClassName()),
                String.valueOf(settings.maximumPoolSize()),
                String.valueOf(settings.minimumIdle()),
                String.valueOf(settings.connectionTimeoutMs()),
                String.valueOf(settings.idleTimeoutMs()),
                String.valueOf(settings.maxLifetimeMs())
        );
        return sha256(source);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }

    private String driverClassNameFor(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl) || !jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:")) {
            throw new IllegalArgumentException("Runtime-managed dataSource requires jdbcUrl starting with jdbc:");
        }
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (lower.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        if (lower.startsWith("jdbc:sqlserver:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }
        if (lower.startsWith("jdbc:sqlite:")) {
            return "org.sqlite.JDBC";
        }
        if (lower.startsWith("jdbc:oracle:")) {
            return "oracle.jdbc.OracleDriver";
        }
        if (lower.startsWith("jdbc:h2:")) {
            return "org.h2.Driver";
        }
        if (lower.startsWith("jdbc:mariadb:")) {
            return "org.mariadb.jdbc.Driver";
        }
        return null;
    }

    private boolean isSqlite(String jdbcUrl) {
        return StringUtils.hasText(jdbcUrl) && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:sqlite:");
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    public record ManagedDataSourcePoolState(
            String name,
            String lifecycleStatus,
            boolean poolExists,
            boolean poolClosed,
            int activeConnections,
            String lastBorrowedAt,
            String lastReturnedAt,
            String lastCloseReason,
            String lastCloseError,
            ManagedDataSourcePoolSettings settings
    ) {
    }

    private final class PoolSlot {

        private RuntimeDatasourceRecord record;
        private String fingerprint;
        private ManagedDataSourcePool pool;
        private final ManagedRuntimeDataSource dataSource = new ManagedRuntimeDataSource(this);
        private final AtomicInteger activeConnections = new AtomicInteger();
        private Instant lastBorrowedAt;
        private Instant lastReturnedAt;
        private String lastCloseReason;
        private String lastCloseError;
        private boolean lastPoolClosed;

        private PoolSlot(RuntimeDatasourceRecord record) {
            this.record = record;
            this.fingerprint = fingerprint(record, settingsFor(record));
        }

        private DataSource dataSource() {
            return dataSource;
        }

        private synchronized void updateRecord(RuntimeDatasourceRecord nextRecord) {
            ManagedDataSourcePoolSettings nextSettings = settingsFor(nextRecord);
            String nextFingerprint = fingerprint(nextRecord, nextSettings);
            if (!nextFingerprint.equals(fingerprint)) {
                closePool("config-changed");
            }
            record = nextRecord;
            fingerprint = nextFingerprint;
        }

        private synchronized ManagedDataSourcePool ensurePool() {
            if (pool != null && !pool.isClosed()) {
                return pool;
            }
            String password = resolvePassword(record);
            pool = poolFactory.create(record, password, settingsFor(record));
            lastReturnedAt = clock.instant();
            lastCloseReason = null;
            lastCloseError = null;
            lastPoolClosed = false;
            return pool;
        }

        private synchronized Connection borrowConnection(String username, String password) throws SQLException {
            ManagedDataSourcePool currentPool = ensurePool();
            activeConnections.incrementAndGet();
            try {
                Connection connection = username != null || password != null
                        ? currentPool.getConnection(username, password)
                        : currentPool.getConnection();
                lastBorrowedAt = clock.instant();
                return wrapConnection(connection, this);
            } catch (SQLException | RuntimeException e) {
                connectionReturned();
                throw e;
            }
        }

        private synchronized void connectionReturned() {
            activeConnections.updateAndGet(value -> Math.max(0, value - 1));
            lastReturnedAt = clock.instant();
        }

        private synchronized void closeIfIdle() {
            if (pool == null || pool.isClosed() || activeConnectionCount() > 0 || lastReturnedAt == null) {
                return;
            }
            Duration idleDuration = Duration.between(lastReturnedAt, clock.instant());
            if (idleDuration.compareTo(Duration.ofMinutes(settingsFor(record).idlePoolCloseMinutes())) > 0) {
                closePool("idle-closed");
            }
        }

        private synchronized void closePool(String reason) {
            ManagedDataSourcePool currentPool = pool;
            pool = null;
            lastCloseReason = reason;
            if (currentPool == null || currentPool.isClosed()) {
                lastPoolClosed = currentPool != null && currentPool.isClosed() || lastPoolClosed;
                return;
            }
            try {
                currentPool.close();
                lastCloseError = null;
                lastPoolClosed = true;
            } catch (RuntimeException e) {
                lastCloseError = e.getMessage();
            }
        }

        private synchronized int activeConnectionCount() {
            int wrappedActive = activeConnections.get();
            int poolActive = pool != null && !pool.isClosed() ? pool.activeConnections() : 0;
            return Math.max(wrappedActive, Math.max(poolActive, 0));
        }

        private synchronized ManagedDataSourcePoolState state() {
            boolean exists = pool != null && !pool.isClosed();
            String status = exists ? "live" : lastCloseReason != null ? lastCloseReason : "not-created";
            return new ManagedDataSourcePoolState(
                    record.name(),
                    status,
                    exists,
                    pool != null ? pool.isClosed() : lastPoolClosed,
                    activeConnectionCount(),
                    lastBorrowedAt != null ? lastBorrowedAt.toString() : null,
                    lastReturnedAt != null ? lastReturnedAt.toString() : null,
                    lastCloseReason,
                    lastCloseError,
                    settingsFor(record)
            );
        }
    }

    private static Connection wrapConnection(Connection connection, PoolSlot slot) {
        InvocationHandler handler = new ConnectionCloseTrackingHandler(connection, slot);
        return (Connection) Proxy.newProxyInstance(
                connection.getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                handler
        );
    }

    private static final class ConnectionCloseTrackingHandler implements InvocationHandler {

        private final Connection target;
        private final PoolSlot slot;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ConnectionCloseTrackingHandler(Connection target, PoolSlot slot) {
            this.target = target;
            this.slot = slot;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("close".equals(methodName) && method.getParameterCount() == 0) {
                if (closed.compareAndSet(false, true)) {
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    } finally {
                        slot.connectionReturned();
                    }
                }
                return null;
            }
            if ("isClosed".equals(methodName) && method.getParameterCount() == 0 && closed.get()) {
                return true;
            }
            if ("equals".equals(methodName) && method.getParameterCount() == 1) {
                return proxy == args[0];
            }
            if ("hashCode".equals(methodName) && method.getParameterCount() == 0) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(methodName) && method.getParameterCount() == 0) {
                return "ManagedRuntimeConnection[" + target + "]";
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static final class ManagedRuntimeDataSource implements DataSource {

        private final PoolSlot slot;

        private ManagedRuntimeDataSource(PoolSlot slot) {
            this.slot = slot;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return slot.borrowConnection(null, null);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return slot.borrowConnection(username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return slot.ensurePool().getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            slot.ensurePool().setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            slot.ensurePool().setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return slot.ensurePool().getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return slot.ensurePool().getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return slot.ensurePool().unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return slot.ensurePool().isWrapperFor(iface);
        }
    }
}
