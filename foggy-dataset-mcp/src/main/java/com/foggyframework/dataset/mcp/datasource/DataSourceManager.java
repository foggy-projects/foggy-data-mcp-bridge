package com.foggyframework.dataset.mcp.datasource;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.port.BindingAdmissionState;
import com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.model.lifecycle.port.RevokeMode;
import com.foggyframework.dataset.model.lifecycle.port.StaleDatasourceBindingException;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.model.lifecycle.refresh.DatasourceCatalogConvergence;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.function.Supplier;

/**
 * Dynamic named datasource manager with generation-pinned admission.
 *
 * <p>Every successful configure creates a fresh physical pool and an opaque binding generation.
 * A handle returned for one generation never switches to another physical target. Mutation first
 * commits credential persistence and then atomically changes admission: the old generation stops
 * accepting new connections while connections acquired before the boundary may drain for a
 * bounded period.</p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@Service
public class DataSourceManager {

    static final long DEFAULT_LEASE_DRAIN_TIMEOUT_MS = 60_000L;
    static final long MIN_LEASE_DRAIN_TIMEOUT_MS = 1_000L;
    static final long MAX_LEASE_DRAIN_TIMEOUT_MS = 300_000L;
    private static final String BACKEND_ID = "mcp-named";
    private static final String REVOKED_ERROR_CODE = "DATASOURCE_BINDING_REVOKED";
    private static final Pattern LOGICAL_NAME_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}_][\\p{L}\\p{N}._-]{0,127}");

    private final DataSourceConfigPersistence persistence;
    private final ManagedDataSourceFactory dataSourceFactory;
    private final DrainScheduler drainScheduler;
    private final long leaseDrainTimeoutMs;
    private final String bootEpoch;
    private final AtomicLong nextGeneration = new AtomicLong();
    private final Map<String, BindingSlot> currentSlots = new ConcurrentHashMap<>();
    private final Set<BindingSlot> liveSlots = ConcurrentHashMap.newKeySet();
    private final Object mutationMonitor = new Object();
    private volatile DatasourceCatalogConvergence catalogConvergence;
    private volatile ObjectProvider<CatalogSnapshotStore> catalogSnapshotStoreProvider;
    private volatile ObjectProvider<CatalogRefreshCoordinator> catalogRefreshCoordinatorProvider;
    private boolean closed;

    /**
     * Compatibility constructor for direct users and older tests.
     */
    public DataSourceManager(DataSourceConfigPersistence persistence) {
        this(persistence, DEFAULT_LEASE_DRAIN_TIMEOUT_MS);
    }

    /**
     * Spring constructor. MCP intentionally consumes the Runtime-owned timeout property so both
     * adapters follow the same bounded drain contract.
     */
    @Autowired
    public DataSourceManager(
            DataSourceConfigPersistence persistence,
            @Value("${foggy.runtime-api.datasource-pool.lease-drain-timeout-ms:60000}")
            long leaseDrainTimeoutMs) {
        this(persistence,
                DataSourceManager::createFreshPhysicalDataSource,
                productionDrainScheduler(leaseDrainTimeoutMs),
                leaseDrainTimeoutMs,
                UUID.randomUUID().toString());
    }

    /**
     * Deterministic construction seam for lifecycle tests. Package-private by design.
     */
    DataSourceManager(
            DataSourceConfigPersistence persistence,
            ManagedDataSourceFactory dataSourceFactory,
            DrainScheduler drainScheduler,
            long leaseDrainTimeoutMs,
            String bootEpoch) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.dataSourceFactory = Objects.requireNonNull(dataSourceFactory, "dataSourceFactory");
        this.drainScheduler = Objects.requireNonNull(drainScheduler, "drainScheduler");
        this.leaseDrainTimeoutMs = validateLeaseDrainTimeout(leaseDrainTimeoutMs);
        RX.hasText(bootEpoch, "Datasource binding boot epoch cannot be empty");
        this.bootEpoch = bootEpoch.trim();
    }

    /** Providers avoid a startup cycle through the model named datasource resolver. */
    @Autowired
    void configureCatalogConvergenceProviders(
            ObjectProvider<CatalogSnapshotStore> snapshotStoreProvider,
            ObjectProvider<CatalogRefreshCoordinator> refreshCoordinatorProvider
    ) {
        this.catalogSnapshotStoreProvider = snapshotStoreProvider;
        this.catalogRefreshCoordinatorProvider = refreshCoordinatorProvider;
    }

    /** Deterministic direct wiring seam retained for non-Spring callers and tests. */
    void configureCatalogConvergence(
            CatalogSnapshotStore snapshotStore,
            CatalogRefreshCoordinator refreshCoordinator
    ) {
        this.catalogConvergence = new DatasourceCatalogConvergence(
                snapshotStore, refreshCoordinator);
    }

    /**
     * Restore persisted configurations in canonical-name order. A canonical-name collision is a
     * startup error and is detected before any pool is published.
     */
    @PostConstruct
    public void init() {
        Map<String, DataSourceConfig> savedConfigs = persistence.loadAll();
        TreeMap<String, RestoredConfig> ordered = new TreeMap<>();
        for (Map.Entry<String, DataSourceConfig> entry : savedConfigs.entrySet()) {
            String canonicalName = canonicalName(entry.getKey());
            RestoredConfig previous = ordered.putIfAbsent(
                    canonicalName,
                    new RestoredConfig(copyConfig(entry.getValue())));
            if (previous != null) {
                throw new IllegalStateException(
                        "DATASOURCE_CANONICAL_NAME_CONFLICT: duplicate canonical datasource name '"
                                + canonicalName + "'");
            }
        }

        synchronized (mutationMonitor) {
            if (closed) {
                throw new IllegalStateException("Datasource manager is closed");
            }
            if (!currentSlots.isEmpty()) {
                throw new IllegalStateException("Datasource manager has already been initialized");
            }
            for (Map.Entry<String, RestoredConfig> entry : ordered.entrySet()) {
                String canonicalName = entry.getKey();
                try {
                    validateConfig(entry.getValue().config());
                    BindingSlot slot = createSlotWithFreshPhysical(
                            canonicalName, entry.getValue().config());
                    currentSlots.put(canonicalName, slot);
                    log.info("Restored datasource binding: name={}", canonicalName);
                } catch (RuntimeException e) {
                    // Preserve the legacy behavior for an independently invalid credential file.
                    // Canonical conflicts remain a fail-closed startup error above.
                    log.error("Failed to restore datasource binding: name={}, reason={}",
                            canonicalName, e.getClass().getSimpleName());
                }
            }
        }
    }

    /**
     * Configure with the ordinary bounded-drain retirement policy.
     */
    public void configure(String name, DataSourceConfig config) {
        configure(name, config, RevokeMode.DRAIN);
    }

    /**
     * Configure a fresh binding generation. Equivalent physical configuration deliberately does
     * not reuse the prior generation or physical pool.
     */
    public void configure(String name, DataSourceConfig config, RevokeMode revokeMode) {
        String canonicalName = canonicalName(name);
        validateConfig(config);
        RevokeMode effectiveMode = Objects.requireNonNull(revokeMode, "revokeMode");
        DataSourceConfig immutableConfig = copyConfig(config);
        DataSource candidatePhysical = createPhysical(immutableConfig);
        BindingSlot candidate = null;
        Set<String> catalogsToRefresh;
        boolean mutationCommitted = false;

        try {
            synchronized (mutationMonitor) {
                if (closed) {
                    throw new IllegalStateException("Datasource manager is closed");
                }
                // Allocate sequence while mutations are serialized, so committed generations
                // cannot appear in reverse sequence order when pool creation overlaps.
                candidate = createSlot(canonicalName, immutableConfig, candidatePhysical);
                // Persistence is part of the mutation precondition. A failed write leaves current
                // admission untouched and the unpublished candidate is closed below.
                persistence.save(canonicalName, immutableConfig);
                BindingSlot previous = currentSlots.get(canonicalName);
                if (previous != null) {
                    previous.retire(effectiveMode);
                }
                currentSlots.put(canonicalName, candidate);
                mutationCommitted = true;
                catalogsToRefresh = blockAffectedCatalogs(canonicalName);
            }
        } catch (RuntimeException | Error e) {
            if (!mutationCommitted) {
                if (candidate == null) {
                    closePhysical(candidatePhysical);
                } else {
                    candidate.retire(RevokeMode.HARD);
                }
            }
            throw e;
        }

        log.info("Datasource binding configured: name={}, generation={}",
                canonicalName, candidate.identity().generation().value());
        refreshCatalogs(catalogsToRefresh);
    }

    /**
     * Resolve a generation-pinned binding for model lifecycle consumers.
     */
    public ResolvedDatasourceBinding resolveBinding(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String canonicalName = canonicalName(name);
        synchronized (mutationMonitor) {
            if (closed) {
                return null;
            }
            BindingSlot slot = currentSlots.get(canonicalName);
            if (slot == null || slot.admissionState() != BindingAdmissionState.OPEN) {
                return null;
            }
            return new ResolvedDatasourceBinding(slot.handle(), slot.identity(), true);
        }
    }

    /**
     * Legacy raw-DataSource lookup. The returned object is still generation pinned.
     */
    public DataSource getDataSource(String name) {
        ResolvedDatasourceBinding binding = resolveBinding(name);
        return binding == null ? null : binding.dataSource();
    }

    public DatasourceBindingIdentity getBindingIdentity(String name) {
        ResolvedDatasourceBinding binding = resolveBinding(name);
        return binding == null ? null : binding.identity();
    }

    /**
     * Checks only the current logical slot identity. No physical connection is
     * opened and no host, database or credential field participates.
     */
    public BindingCurrentness currentness(DatasourceBindingIdentity identity) {
        synchronized (mutationMonitor) {
            return currentnessUnderMutationLock(identity);
        }
    }

    /**
     * Runs a catalog publication in the same critical section as configure/remove admission
     * commits. This closes the check-then-publish race for identities issued by this manager.
     */
    public <T> T publishIfCurrent(
            Collection<DatasourceBindingIdentity> identities,
            Supplier<T> publication
    ) {
        Objects.requireNonNull(identities, "identities");
        Objects.requireNonNull(publication, "publication");
        synchronized (mutationMonitor) {
            for (DatasourceBindingIdentity identity : identities) {
                BindingCurrentness currentness = currentnessUnderMutationLock(identity);
                if (currentness == BindingCurrentness.STALE) {
                    throw new StaleDatasourceBindingException(identity.bindingKey());
                }
                if (currentness != BindingCurrentness.CURRENT) {
                    throw new IllegalStateException(
                            "DATASOURCE_BINDING_CURRENTNESS_UNKNOWN: "
                                    + (identity == null ? "null" : identity.bindingKey()));
                }
            }
            return publication.get();
        }
    }

    private BindingCurrentness currentnessUnderMutationLock(
            DatasourceBindingIdentity identity
    ) {
        if (identity == null
                || !BACKEND_ID.equals(identity.backendId())
                || !LOGICAL_NAME_PATTERN.matcher(identity.bindingKey()).matches()) {
            return BindingCurrentness.UNKNOWN;
        }
        BindingSlot slot = currentSlots.get(identity.bindingKey());
        if (closed || slot == null || slot.admissionState() != BindingAdmissionState.OPEN) {
            return BindingCurrentness.STALE;
        }
        return slot.identity().equals(identity)
                ? BindingCurrentness.CURRENT
                : BindingCurrentness.STALE;
    }

    public DataSourceConfig getConfig(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        synchronized (mutationMonitor) {
            BindingSlot slot = currentSlots.get(canonicalName(name));
            return slot == null || slot.admissionState() != BindingAdmissionState.OPEN
                    ? null
                    : copyConfig(slot.config());
        }
    }

    public ConnectionTestResult testConnection(String name) {
        DataSource dataSource = getDataSource(name);
        if (dataSource == null) {
            return ConnectionTestResult.failure("Data source not configured: " + name);
        }

        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(5)) {
                return ConnectionTestResult.success(canonicalName(name), conn.getCatalog());
            }
            return ConnectionTestResult.failure("Connection validation failed");
        } catch (SQLException e) {
            log.warn("Datasource connection test failed: name={}", canonicalName(name));
            return ConnectionTestResult.failure(sanitizedSqlMessage(e));
        }
    }

    /**
     * Test an unpublished configuration and always close its temporary physical pool.
     */
    public ConnectionTestResult testConnection(DataSourceConfig config) {
        validateConfig(config);
        DataSource dataSource = null;
        try {
            dataSource = dataSourceFactory.create(copyConfig(config));
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(5)) {
                    return ConnectionTestResult.success("(test)", config.getDatabase());
                }
                return ConnectionTestResult.failure("Connection validation failed");
            }
        } catch (Exception e) {
            log.warn("Unpublished datasource connection test failed");
            return ConnectionTestResult.failure(sanitizedMessage(e));
        } finally {
            closePhysical(dataSource);
        }
    }

    public boolean isConfigured(String name) {
        return resolveBinding(name) != null;
    }

    public boolean remove(String name) {
        return remove(name, RevokeMode.DRAIN);
    }

    /**
     * Remove only after the credential file deletion succeeds. The deletion and admission change
     * are serialized with configure, so memory and persistence cannot commit in opposite order.
     */
    public boolean remove(String name, RevokeMode revokeMode) {
        String canonicalName = canonicalName(name);
        RevokeMode effectiveMode = Objects.requireNonNull(revokeMode, "revokeMode");
        Set<String> catalogsToRefresh;
        synchronized (mutationMonitor) {
            if (closed) {
                return false;
            }
            BindingSlot current = currentSlots.get(canonicalName);
            if (current == null) {
                return false;
            }
            persistence.delete(canonicalName);
            current.retire(effectiveMode);
            currentSlots.remove(canonicalName, current);
            catalogsToRefresh = blockAffectedCatalogs(canonicalName);
        }
        log.info("Datasource binding removed: name={}", canonicalName);
        refreshCatalogs(catalogsToRefresh);
        return true;
    }

    public Set<String> getConfiguredNames() {
        synchronized (mutationMonitor) {
            TreeSet<String> names = new TreeSet<>();
            currentSlots.forEach((name, slot) -> {
                if (slot.admissionState() == BindingAdmissionState.OPEN) {
                    names.add(name);
                }
            });
            return names;
        }
    }

    @PreDestroy
    public void close() {
        List<BindingSlot> slots;
        synchronized (mutationMonitor) {
            if (closed) {
                return;
            }
            closed = true;
            // Include already-retiring generations. They are no longer current, but may still
            // own in-flight connections whose scheduled deadline must not be orphaned on shutdown.
            slots = new ArrayList<>(liveSlots);
            currentSlots.clear();
        }
        slots.forEach(slot -> slot.retire(RevokeMode.HARD));
        drainScheduler.close();
    }

    BindingAdmissionState admissionState(DataSource handle) {
        if (handle instanceof GenerationPinnedDataSource pinned) {
            return pinned.slot.admissionState();
        }
        return null;
    }

    int activeLeases(DataSource handle) {
        if (handle instanceof GenerationPinnedDataSource pinned) {
            return pinned.slot.activeLeaseCount();
        }
        return 0;
    }

    private BindingSlot createSlotWithFreshPhysical(
            String canonicalName,
            DataSourceConfig config) {
        DataSource physical = createPhysical(config);
        try {
            return createSlot(canonicalName, config, physical);
        } catch (RuntimeException | Error e) {
            closePhysical(physical);
            throw e;
        }
    }

    private DataSource createPhysical(DataSourceConfig config) {
        DataSource physical;
        try {
            physical = dataSourceFactory.create(config);
        } catch (RuntimeException e) {
            log.warn("Datasource physical pool creation failed: reason={}",
                    e.getClass().getSimpleName());
            throw new IllegalStateException("DATASOURCE_POOL_CREATE_FAILED");
        }
        if (physical == null) {
            throw new IllegalStateException("DATASOURCE_POOL_CREATE_FAILED");
        }
        return physical;
    }

    private BindingSlot createSlot(
            String canonicalName,
            DataSourceConfig config,
            DataSource physical) {
        long sequence = nextGeneration.incrementAndGet();
        DatasourceBindingGeneration generation =
                new DatasourceBindingGeneration(bootEpoch + ":" + sequence);
        DatasourceBindingIdentity identity = new DatasourceBindingIdentity(
                canonicalName,
                BACKEND_ID,
                generation);
        BindingSlot slot = new BindingSlot(canonicalName, config, physical, identity);
        liveSlots.add(slot);
        return slot;
    }

    private static long validateLeaseDrainTimeout(long value) {
        if (value < MIN_LEASE_DRAIN_TIMEOUT_MS || value > MAX_LEASE_DRAIN_TIMEOUT_MS) {
            throw new IllegalArgumentException(
                    "lease-drain-timeout-ms must be between "
                            + MIN_LEASE_DRAIN_TIMEOUT_MS + " and "
                            + MAX_LEASE_DRAIN_TIMEOUT_MS + " milliseconds");
        }
        return value;
    }

    private static DrainScheduler productionDrainScheduler(long leaseDrainTimeoutMs) {
        validateLeaseDrainTimeout(leaseDrainTimeoutMs);
        return new ExecutorDrainScheduler();
    }

    private static String canonicalName(String name) {
        RX.hasText(name, "Data source name cannot be empty");
        String canonical = name.trim();
        if (!LOGICAL_NAME_PATTERN.matcher(canonical).matches()) {
            throw new IllegalArgumentException(
                    "Data source name must be a logical identifier of at most 128 characters");
        }
        return canonical;
    }

    private Set<String> blockAffectedCatalogs(String canonicalBindingKey) {
        DatasourceCatalogConvergence convergence = catalogConvergence();
        return convergence == null
                ? Set.of()
                : convergence.blockAffectedNamespaces(
                        Set.of(), Set.of(canonicalBindingKey));
    }

    private void refreshCatalogs(Collection<String> namespaces) {
        DatasourceCatalogConvergence convergence = catalogConvergence();
        if (convergence != null && namespaces != null && !namespaces.isEmpty()) {
            convergence.refresh(namespaces);
        }
    }

    private DatasourceCatalogConvergence catalogConvergence() {
        DatasourceCatalogConvergence current = catalogConvergence;
        if (current != null) {
            return current;
        }
        ObjectProvider<CatalogSnapshotStore> storeProvider = catalogSnapshotStoreProvider;
        ObjectProvider<CatalogRefreshCoordinator> coordinatorProvider =
                catalogRefreshCoordinatorProvider;
        if (storeProvider == null || coordinatorProvider == null) {
            return null;
        }
        CatalogSnapshotStore store = storeProvider.getIfAvailable();
        CatalogRefreshCoordinator coordinator = coordinatorProvider.getIfAvailable();
        if (store == null || coordinator == null) {
            return null;
        }
        DatasourceCatalogConvergence resolved =
                new DatasourceCatalogConvergence(store, coordinator);
        catalogConvergence = resolved;
        return resolved;
    }

    private static void validateConfig(DataSourceConfig config) {
        RX.notNull(config, "Data source config cannot be null");
        RX.hasText(config.getHost(), "Host cannot be empty");
        RX.hasText(config.getDatabase(), "Database cannot be empty");
        RX.hasText(config.getUsername(), "Username cannot be empty");
        RX.notNull(config.getPort(), "Port cannot be null");
    }

    private static DataSourceConfig copyConfig(DataSourceConfig source) {
        RX.notNull(source, "Data source config cannot be null");
        return DataSourceConfig.builder()
                .host(source.getHost())
                .port(source.getPort())
                .database(source.getDatabase())
                .username(source.getUsername())
                .password(source.getPassword())
                .driver(source.getDriver())
                .build();
    }

    private static DataSource createFreshPhysicalDataSource(DataSourceConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(buildJdbcUrl(config));
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setPoolName("FoggyMcpBinding-" + UUID.randomUUID());
        // Preserve the legacy pool defaults while avoiding cross-generation reuse. Every
        // binding still owns a fresh pool.
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30_000L);
        hikariConfig.setIdleTimeout(600_000L);
        hikariConfig.setMaxLifetime(1_800_000L);
        hikariConfig.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(hikariConfig);
    }

    private static String buildJdbcUrl(DataSourceConfig config) {
        String driver = config.getDriver();
        if (driver == null || driver.isBlank()) {
            driver = "postgresql";
        }
        return switch (driver.toLowerCase(Locale.ROOT)) {
            case "postgresql", "postgres" ->
                    String.format("jdbc:postgresql://%s:%d/%s",
                            config.getHost(), config.getPort(), config.getDatabase());
            case "mysql" ->
                    String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8",
                            config.getHost(), config.getPort(), config.getDatabase());
            case "sqlserver" ->
                    String.format("jdbc:sqlserver://%s:%d;databaseName=%s",
                            config.getHost(), config.getPort(), config.getDatabase());
            case "sqlite" -> String.format("jdbc:sqlite:%s", config.getDatabase());
            default -> throw new IllegalArgumentException("Unsupported driver: " + driver);
        };
    }

    private static String sanitizedSqlMessage(SQLException exception) {
        return REVOKED_ERROR_CODE.equals(exception.getSQLState())
                ? REVOKED_ERROR_CODE
                : "Datasource connection test failed";
    }

    private static String sanitizedMessage(Exception exception) {
        return exception instanceof SQLException sqlException
                ? sanitizedSqlMessage(sqlException)
                : "Datasource connection test failed";
    }

    private static void closePhysical(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn("Failed to close datasource pool cleanly: reason={}",
                        e.getClass().getSimpleName());
            }
        }
    }

    @FunctionalInterface
    interface ManagedDataSourceFactory {
        DataSource create(DataSourceConfig config);
    }

    interface DrainScheduler extends AutoCloseable {
        DrainTask schedule(Runnable task, long delayMillis);

        @Override
        default void close() {
        }
    }

    @FunctionalInterface
    interface DrainTask {
        void cancel();
    }

    private static final class ExecutorDrainScheduler implements DrainScheduler {
        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "foggy-mcp-datasource-drain");
            thread.setDaemon(true);
            return thread;
        });

        @Override
        public DrainTask schedule(Runnable task, long delayMillis) {
            var future = executor.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
            return () -> future.cancel(false);
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }

    private final class BindingSlot {
        private final String canonicalName;
        private final DataSourceConfig config;
        private final DataSource physical;
        private final DatasourceBindingIdentity identity;
        private final DataSource handle;
        private final Set<ConnectionLease> activeLeases =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private BindingAdmissionState state = BindingAdmissionState.OPEN;
        private DrainTask drainTask;
        private boolean physicalCloseStarted;

        private BindingSlot(
                String canonicalName,
                DataSourceConfig config,
                DataSource physical,
                DatasourceBindingIdentity identity) {
            this.canonicalName = canonicalName;
            this.config = copyConfig(config);
            this.physical = physical;
            this.identity = identity;
            this.handle = new GenerationPinnedDataSource(this);
        }

        DataSourceConfig config() {
            return config;
        }

        DatasourceBindingIdentity identity() {
            return identity;
        }

        DataSource handle() {
            return handle;
        }

        synchronized BindingAdmissionState admissionState() {
            return state;
        }

        synchronized int activeLeaseCount() {
            return activeLeases.size();
        }

        Connection acquire(ConnectionSupplier supplier) throws SQLException {
            synchronized (this) {
                if (state != BindingAdmissionState.OPEN) {
                    throw revokedException(canonicalName, identity.generation());
                }
                Connection delegate = supplier.get();
                ConnectionLease lease = new ConnectionLease(this, delegate);
                activeLeases.add(lease);
                return lease.proxy();
            }
        }

        void retire(RevokeMode mode) {
            List<ConnectionLease> toForceClose = List.of();
            boolean closePool = false;
            synchronized (this) {
                if (state == BindingAdmissionState.CLOSED
                        || state == BindingAdmissionState.REVOKED) {
                    return;
                }
                if (mode == RevokeMode.HARD) {
                    state = BindingAdmissionState.REVOKED;
                    toForceClose = new ArrayList<>(activeLeases);
                    closePool = beginPhysicalClose();
                } else {
                    if (state != BindingAdmissionState.OPEN) {
                        return;
                    }
                    state = BindingAdmissionState.RETIRING;
                    if (activeLeases.isEmpty()) {
                        state = BindingAdmissionState.CLOSED;
                        closePool = beginPhysicalClose();
                    } else {
                        try {
                            drainTask = drainScheduler.schedule(this::expireDrain, leaseDrainTimeoutMs);
                        } catch (RuntimeException schedulingFailure) {
                            state = BindingAdmissionState.REVOKED;
                            toForceClose = new ArrayList<>(activeLeases);
                            closePool = beginPhysicalClose();
                            log.warn("Datasource drain scheduling failed; binding revoked: name={}",
                                    canonicalName);
                        }
                    }
                }
            }
            toForceClose.forEach(ConnectionLease::forceClose);
            if (closePool) {
                closePhysical(physical);
            }
            finishRevocation();
            forgetClosedSlot();
        }

        private void expireDrain() {
            List<ConnectionLease> toForceClose;
            boolean closePool;
            synchronized (this) {
                if (state != BindingAdmissionState.RETIRING) {
                    return;
                }
                state = BindingAdmissionState.REVOKED;
                toForceClose = new ArrayList<>(activeLeases);
                closePool = beginPhysicalClose();
            }
            toForceClose.forEach(ConnectionLease::forceClose);
            if (closePool) {
                closePhysical(physical);
            }
            finishRevocation();
            forgetClosedSlot();
        }

        private void release(ConnectionLease lease) {
            boolean closePool = false;
            synchronized (this) {
                activeLeases.remove(lease);
                if (activeLeases.isEmpty() && state == BindingAdmissionState.RETIRING) {
                    cancelDrainTask();
                    state = BindingAdmissionState.CLOSED;
                    closePool = beginPhysicalClose();
                }
            }
            if (closePool) {
                closePhysical(physical);
            }
            forgetClosedSlot();
        }

        private synchronized boolean beginPhysicalClose() {
            if (physicalCloseStarted) {
                return false;
            }
            physicalCloseStarted = true;
            cancelDrainTask();
            return true;
        }

        private synchronized void finishRevocation() {
            if (state == BindingAdmissionState.REVOKED) {
                state = BindingAdmissionState.CLOSED;
            }
        }

        private void forgetClosedSlot() {
            if (admissionState() == BindingAdmissionState.CLOSED) {
                liveSlots.remove(this);
            }
        }

        private void cancelDrainTask() {
            if (drainTask != null) {
                try {
                    drainTask.cancel();
                } catch (RuntimeException e) {
                    log.warn("Failed to cancel datasource drain task: reason={}",
                            e.getClass().getSimpleName());
                } finally {
                    drainTask = null;
                }
            }
        }
    }

    private static final class ConnectionLease {
        private final BindingSlot slot;
        private final Connection delegate;
        private final AtomicBoolean released = new AtomicBoolean();
        private final Connection proxy;

        private ConnectionLease(BindingSlot slot, Connection delegate) {
            this.slot = slot;
            this.delegate = delegate;
            this.proxy = (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    this::invoke);
        }

        Connection proxy() {
            return proxy;
        }

        void forceClose() {
            Exception failure = releaseDelegate(Connection::close);
            if (failure instanceof SQLException sqlException) {
                log.debug("Failed to force-close datasource lease cleanly: sqlState={}",
                        sqlException.getSQLState());
            } else if (failure != null) {
                log.debug("Failed to force-close datasource lease cleanly: reason={}",
                        failure.getClass().getSimpleName());
            }
        }

        private Object invoke(Object proxyObject, Method method, Object[] arguments) throws Throwable {
            String methodName = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return switch (methodName) {
                    case "toString" -> "GenerationPinnedConnection[" + slot.canonicalName + "]";
                    case "hashCode" -> System.identityHashCode(proxyObject);
                    case "equals" -> proxyObject == arguments[0];
                    default -> method.invoke(this, arguments);
                };
            }
            if ("close".equals(methodName) && method.getParameterCount() == 0) {
                Exception failure = releaseDelegate(Connection::close);
                if (failure != null) {
                    throw failure;
                }
                return null;
            }
            if ("abort".equals(methodName)) {
                Exception failure = releaseDelegate(
                        connection -> connection.abort((java.util.concurrent.Executor) arguments[0]));
                if (failure != null) {
                    throw failure;
                }
                return null;
            }
            if ("unwrap".equals(methodName)) {
                Class<?> target = (Class<?>) arguments[0];
                if (target.isInstance(proxyObject)) {
                    return proxyObject;
                }
                throw new SQLException("Generation-pinned connection does not expose its delegate");
            }
            if ("isWrapperFor".equals(methodName)) {
                return ((Class<?>) arguments[0]).isInstance(proxyObject);
            }
            try {
                return method.invoke(delegate, arguments);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private Exception releaseDelegate(ConnectionCloser closer) {
            if (!released.compareAndSet(false, true)) {
                return null;
            }
            Exception failure = null;
            try {
                closer.close(delegate);
            } catch (Exception e) {
                failure = e;
            } finally {
                slot.release(this);
            }
            return failure;
        }
    }

    private final class GenerationPinnedDataSource implements DataSource {
        private final BindingSlot slot;

        private GenerationPinnedDataSource(BindingSlot slot) {
            this.slot = slot;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return slot.acquire(slot.physical::getConnection);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLFeatureNotSupportedException(
                    "Generation-pinned datasource does not allow credential override");
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return slot.physical.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            slot.physical.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            slot.physical.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return slot.physical.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return slot.physical.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Generation-pinned datasource does not expose its physical pool");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }

        @Override
        public String toString() {
            return "GenerationPinnedDataSource[" + slot.canonicalName + "]";
        }
    }

    @FunctionalInterface
    private interface ConnectionSupplier {
        Connection get() throws SQLException;
    }

    @FunctionalInterface
    private interface ConnectionCloser {
        void close(Connection connection) throws SQLException;
    }

    private static SQLException revokedException(
            String canonicalName,
            DatasourceBindingGeneration generation) {
        return new SQLException(
                REVOKED_ERROR_CODE + ": datasource binding is no longer open: name="
                        + canonicalName + ", generation=" + generation.value(),
                REVOKED_ERROR_CODE);
    }

    private record RestoredConfig(DataSourceConfig config) {
    }

    /**
     * Mutable request/persistence DTO retained for API compatibility. Published slots always keep
     * a defensive copy.
     */
    @Data
    public static class DataSourceConfig {
        private String host;
        private Integer port = 5432;
        private String database;
        private String username;
        private String password;
        private String driver;

        public static DataSourceConfigBuilder builder() {
            return new DataSourceConfigBuilder();
        }

        @Override
        public String toString() {
            return "DataSourceConfig{credentials=redacted}";
        }

        public static class DataSourceConfigBuilder {
            private String host;
            private Integer port = 5432;
            private String database;
            private String username;
            private String password;
            private String driver;

            public DataSourceConfigBuilder host(String host) {
                this.host = host;
                return this;
            }

            public DataSourceConfigBuilder port(Integer port) {
                this.port = port;
                return this;
            }

            public DataSourceConfigBuilder database(String database) {
                this.database = database;
                return this;
            }

            public DataSourceConfigBuilder username(String username) {
                this.username = username;
                return this;
            }

            public DataSourceConfigBuilder password(String password) {
                this.password = password;
                return this;
            }

            public DataSourceConfigBuilder driver(String driver) {
                this.driver = driver;
                return this;
            }

            public DataSourceConfig build() {
                DataSourceConfig config = new DataSourceConfig();
                config.setHost(host);
                config.setPort(port);
                config.setDatabase(database);
                config.setUsername(username);
                config.setPassword(password);
                config.setDriver(driver);
                return config;
            }
        }
    }

    @Data
    public static class ConnectionTestResult {
        private boolean success;
        private String message;
        private String dataSourceName;
        private String database;

        public static ConnectionTestResult success(String name, String database) {
            ConnectionTestResult result = new ConnectionTestResult();
            result.setSuccess(true);
            result.setMessage("Connection successful");
            result.setDataSourceName(name);
            result.setDatabase(database);
            return result;
        }

        public static ConnectionTestResult failure(String message) {
            ConnectionTestResult result = new ConnectionTestResult();
            result.setSuccess(false);
            result.setMessage(message);
            return result;
        }
    }
}
