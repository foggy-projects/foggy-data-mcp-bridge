package com.foggyframework.runtime.api.service;

import com.foggyframework.dataset.db.model.lifecycle.port.BindingAdmissionState;
import com.foggyframework.dataset.db.model.lifecycle.port.RevokeMode;
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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Owns Runtime-managed physical pools and generation-pinned logical handles.
 *
 * <p>A logical handle never changes its backend. Registry mutations publish a
 * fresh handle and retire the old one. A connection borrow is the lease
 * admission linearization point; closing the returned connection releases that
 * lease.</p>
 */
@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class ManagedDataSourcePoolManager {

    static final long MIN_LEASE_DRAIN_TIMEOUT_MS = 1_000L;
    static final long MAX_LEASE_DRAIN_TIMEOUT_MS = 300_000L;
    private static final String DRAIN_DEADLINE_SCHEDULE_FAILED = "drain-deadline-schedule-failed";

    private final FoggyRuntimeApiProperties properties;
    private final ManagedDataSourcePoolFactory poolFactory;
    private final Clock clock;
    private final Map<BackendKey, BackendSlot> backends = new LinkedHashMap<>();
    private final Map<BindingKey, BindingSlot> bindings = new LinkedHashMap<>();
    private final Map<String, BindingSlot> currentBindings = new LinkedHashMap<>();
    private final Map<String, String> lastCloseReasons = new LinkedHashMap<>();
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
        leaseDrainTimeoutMs();
        if (!autoStartScheduler) {
            return;
        }
        ensureScheduler();
        FoggyRuntimeApiProperties.DatasourcePool poolProperties = poolProperties();
        if (!poolProperties.isCleanupEnabled() || poolProperties.getCleanupIntervalMinutes() <= 0) {
            return;
        }
        long interval = Math.max(1, poolProperties.getCleanupIntervalMinutes());
        scheduler.scheduleAtFixedRate(this::runIdleCleanupSafely, interval, interval, TimeUnit.MINUTES);
    }

    /** Compatibility resolver for the named Runtime binding. */
    public DataSource resolve(RuntimeDatasourceRecord record) {
        return resolve(record, namedBindingKey(record.name()), effectiveRecordGeneration(record), true);
    }

    /** Resolve one immutable logical binding generation. */
    public synchronized DataSource resolve(
            RuntimeDatasourceRecord record,
            String logicalBindingKey,
            String bindingGeneration
    ) {
        return resolve(record, logicalBindingKey, bindingGeneration, false);
    }

    private synchronized DataSource resolve(
            RuntimeDatasourceRecord record,
            String logicalBindingKey,
            String bindingGeneration,
            boolean allowLegacyReplacement
    ) {
        Objects.requireNonNull(record, "record");
        if (!record.enabled()) {
            throw new IllegalArgumentException("Runtime-managed dataSource is disabled: " + record.name());
        }
        if (!StringUtils.hasText(logicalBindingKey)) {
            throw new IllegalArgumentException("Runtime-managed dataSource requires a logical binding key");
        }
        String effectiveBindingGeneration = StringUtils.hasText(bindingGeneration)
                ? bindingGeneration
                : effectiveLegacyGeneration(record, logicalBindingKey);
        BindingSlot current = currentBindings.get(logicalBindingKey);
        if (current != null && current.generation.equals(effectiveBindingGeneration)) {
            BackendSlot backend = backendFor(record);
            if (current.backend != backend || current.state != BindingAdmissionState.OPEN) {
                throw new IllegalStateException("DATASOURCE_BINDING_REVOKED: binding is no longer current ["
                        + logicalBindingKey + ", generation=" + effectiveBindingGeneration + "]");
            }
            current.backend.ensurePool();
            return current.dataSource;
        }
        if (current != null && !allowLegacyReplacement) {
            throw new IllegalStateException("DATASOURCE_BINDING_NOT_CURRENT: refusing to replace current binding ["
                    + logicalBindingKey + ", generation=" + effectiveBindingGeneration + "]");
        }
        BindingSlot known = bindings.get(new BindingKey(logicalBindingKey, effectiveBindingGeneration));
        if (known != null && (known.state != BindingAdmissionState.OPEN
                || currentBindings.get(logicalBindingKey) != known)) {
            throw new IllegalStateException("DATASOURCE_BINDING_REVOKED: binding generation cannot be reopened ["
                    + logicalBindingKey + ", generation=" + effectiveBindingGeneration + "]");
        }
        BackendSlot backend = backendFor(record);
        if (current != null) {
            retireBinding(current, RevokeMode.DRAIN, "generation-replaced");
        }
        BindingSlot created = new BindingSlot(logicalBindingKey, effectiveBindingGeneration, backend);
        bindings.put(created.key(), created);
        currentBindings.put(logicalBindingKey, created);
        lastCloseReasons.remove(record.name());
        backend.ensurePool();
        return created.dataSource;
    }

    public synchronized void onRecordSaved(RuntimeDatasourceRecord previous, RuntimeDatasourceRecord saved) {
        onRecordSaved(previous, saved, RevokeMode.DRAIN);
    }

    synchronized void onRecordSaved(
            RuntimeDatasourceRecord previous,
            RuntimeDatasourceRecord saved,
            RevokeMode mode
    ) {
        Objects.requireNonNull(saved, "saved");
        String newBackendGeneration = effectiveRecordGeneration(saved);
        boolean changed = previous == null
                || !effectiveRecordGeneration(previous).equals(newBackendGeneration)
                || previous.enabled() != saved.enabled();
        if (!changed) {
            return;
        }

        List<BindingSlot> affectedBindings = currentBindings.values().stream()
                .filter(binding -> binding.backend.record.name().equals(saved.name()))
                .filter(binding -> !binding.backend.generation.equals(newBackendGeneration) || !saved.enabled())
                .toList();
        retireBindings(affectedBindings, mode, saved.enabled() ? "config-changed" : "disabled");
        List<BackendSlot> affectedBackends = backends.values().stream()
                .filter(backend -> backend.record.name().equals(saved.name()))
                .filter(backend -> !backend.generation.equals(newBackendGeneration) || !saved.enabled())
                .toList();
        for (BackendSlot backend : affectedBackends) {
            retireBackend(backend, mode, saved.enabled() ? "config-changed" : "disabled");
        }

        String namedKey = namedBindingKey(saved.name());
        if (saved.enabled()) {
            BackendSlot backend = backendFor(saved);
            BindingSlot replacement = new BindingSlot(namedKey, effectiveRecordGeneration(saved), backend);
            bindings.put(replacement.key(), replacement);
            currentBindings.put(namedKey, replacement);
            if (previous != null) {
                lastCloseReasons.put(saved.name(), "config-changed");
            }
        } else {
            currentBindings.remove(namedKey);
            lastCloseReasons.put(saved.name(), "disabled");
        }
    }

    public synchronized void onNamespaceBindingChanged(String namespace) {
        onNamespaceBindingChanged(namespace, RevokeMode.DRAIN);
    }

    synchronized void onNamespaceBindingChanged(String namespace, RevokeMode mode) {
        String bindingKey = namespaceBindingKey(namespace);
        BindingSlot current = currentBindings.get(bindingKey);
        if (current != null) {
            retireBinding(current, mode, "namespace-rebound");
        }
    }

    public synchronized void remove(String name) {
        remove(name, RevokeMode.DRAIN);
    }

    synchronized void remove(String name, RevokeMode mode) {
        List<BindingSlot> affectedBindings = currentBindings.values().stream()
                .filter(binding -> binding.backend.record.name().equals(name))
                .toList();
        retireBindings(affectedBindings, mode, "removed");
        List<BackendSlot> affectedBackends = backends.values().stream()
                .filter(backend -> backend.record.name().equals(name))
                .toList();
        for (BackendSlot backend : affectedBackends) {
            retireBackend(backend, mode, "removed");
        }
        currentBindings.remove(namedBindingKey(name));
        lastCloseReasons.remove(name);
    }

    public synchronized void runIdleCleanup() {
        for (BackendSlot backend : backends.values()) {
            backend.closeIfIdle();
        }
    }

    public synchronized void closeAll() {
        for (BindingSlot binding : new ArrayList<>(currentBindings.values())) {
            retireBinding(binding, RevokeMode.HARD, "shutdown");
        }
        for (BackendSlot backend : backends.values()) {
            retireBackend(backend, RevokeMode.HARD, "shutdown");
        }
        currentBindings.clear();
    }

    @PreDestroy
    public synchronized void destroy() {
        closeAll();
        if (ownsScheduler && scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public synchronized Optional<ManagedDataSourcePoolState> state(String name) {
        BindingSlot binding = currentBindings.get(namedBindingKey(name));
        if (binding == null) {
            return Optional.empty();
        }
        BackendSlot backend = binding.backend;
        boolean exists = backend.pool != null && !backend.pool.isClosed();
        String status = exists
                ? "live"
                : lastCloseReasons.getOrDefault(name,
                backend.lastCloseReason != null ? backend.lastCloseReason : "not-created");
        return Optional.of(new ManagedDataSourcePoolState(
                name,
                status,
                exists,
                backend.pool != null ? backend.pool.isClosed() : backend.lastPoolClosed,
                backend.activeLeases,
                backend.lastBorrowedAt != null ? backend.lastBorrowedAt.toString() : null,
                backend.lastReturnedAt != null ? backend.lastReturnedAt.toString() : null,
                backend.lastCloseReason,
                backend.lastCloseError,
                settingsFor(backend.record)
        ));
    }

    synchronized Optional<BindingAdmissionState> admissionState(String logicalBindingKey, String generation) {
        BindingSlot binding = bindings.get(new BindingKey(logicalBindingKey, generation));
        return binding == null ? Optional.empty() : Optional.of(binding.state);
    }

    synchronized int activeLeases(String logicalBindingKey, String generation) {
        BindingSlot binding = bindings.get(new BindingKey(logicalBindingKey, generation));
        return binding == null ? 0 : binding.activeLeases;
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
            // The next scheduled pass can retry; lifecycle state is retained.
        }
    }

    private FoggyRuntimeApiProperties.DatasourcePool poolProperties() {
        if (properties.getDatasourcePool() == null) {
            properties.setDatasourcePool(new FoggyRuntimeApiProperties.DatasourcePool());
        }
        return properties.getDatasourcePool();
    }

    private long leaseDrainTimeoutMs() {
        long value = poolProperties().getLeaseDrainTimeoutMs();
        if (value < MIN_LEASE_DRAIN_TIMEOUT_MS || value > MAX_LEASE_DRAIN_TIMEOUT_MS) {
            throw new IllegalStateException(
                    "foggy.runtime-api.datasource-pool.lease-drain-timeout-ms must be between "
                            + MIN_LEASE_DRAIN_TIMEOUT_MS + " and " + MAX_LEASE_DRAIN_TIMEOUT_MS
            );
        }
        return value;
    }

    private void ensureScheduler() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "foggy-runtime-datasource-pool-cleaner");
            thread.setDaemon(true);
            return thread;
        });
        ownsScheduler = true;
    }

    private BackendSlot backendFor(RuntimeDatasourceRecord record) {
        String generation = effectiveRecordGeneration(record);
        BackendKey key = new BackendKey(record.name(), generation);
        BackendSlot backend = backends.get(key);
        if (backend != null) {
            String nextFingerprint = fingerprint(record, settingsFor(record));
            if (!backend.fingerprint.equals(nextFingerprint)) {
                throw new IllegalStateException(
                        "DATASOURCE_BINDING_NOT_CURRENT: one generation cannot change physical configuration"
                );
            }
            return backend;
        }
        backend = new BackendSlot(record, generation);
        backends.put(key, backend);
        return backend;
    }

    private void retireBindings(List<BindingSlot> affectedBindings, RevokeMode mode, String reason) {
        for (BindingSlot binding : affectedBindings) {
            beginRetirement(binding, reason);
        }
        for (BindingSlot binding : affectedBindings) {
            finishRetirement(binding, mode, reason);
        }
    }

    private void retireBinding(BindingSlot binding, RevokeMode mode, String reason) {
        beginRetirement(binding, reason);
        finishRetirement(binding, mode, reason);
    }

    private void beginRetirement(BindingSlot binding, String reason) {
        if (binding.state == BindingAdmissionState.CLOSED) {
            return;
        }
        currentBindings.remove(binding.logicalKey, binding);
        binding.lastCloseReason = reason;
        lastCloseReasons.put(binding.backend.record.name(), reason);
        if (binding.state == BindingAdmissionState.OPEN) {
            binding.state = BindingAdmissionState.RETIRING;
        }
    }

    private void finishRetirement(BindingSlot binding, RevokeMode mode, String reason) {
        if (binding.state == BindingAdmissionState.CLOSED) {
            return;
        }
        if (mode == RevokeMode.HARD) {
            revokeBinding(binding, reason);
            return;
        }
        if (binding.activeLeases == 0) {
            closeBinding(binding);
            return;
        }
        scheduleBindingDeadline(binding);
    }

    private void scheduleBindingDeadline(BindingSlot binding) {
        if (binding.deadlineTask != null) {
            return;
        }
        if (scheduler == null) {
            if (autoStartScheduler) {
                ensureScheduler();
            } else {
                revokeBinding(binding, "drain-deadline-unavailable");
                return;
            }
        }
        long timeoutMs = leaseDrainTimeoutMs();
        binding.drainDeadline = clock.instant().plusMillis(timeoutMs);
        try {
            ScheduledFuture<?> deadlineTask = scheduler.schedule(
                    () -> revokeAtDeadline(binding),
                    timeoutMs,
                    TimeUnit.MILLISECONDS
            );
            if (deadlineTask == null) {
                binding.drainDeadline = null;
                revokeBinding(binding, DRAIN_DEADLINE_SCHEDULE_FAILED);
                return;
            }
            binding.deadlineTask = deadlineTask;
        } catch (RuntimeException scheduleFailure) {
            binding.drainDeadline = null;
            revokeBinding(binding, DRAIN_DEADLINE_SCHEDULE_FAILED);
        }
    }

    private synchronized void revokeAtDeadline(BindingSlot binding) {
        if (binding.state == BindingAdmissionState.RETIRING) {
            revokeBinding(binding, "drain-timeout");
        }
    }

    private void revokeBinding(BindingSlot binding, String reason) {
        if (binding.state == BindingAdmissionState.CLOSED) {
            return;
        }
        binding.state = BindingAdmissionState.REVOKED;
        binding.lastCloseReason = reason;
        List<LeaseToken> active = new ArrayList<>(binding.leases);
        for (LeaseToken lease : active) {
            lease.revoke();
        }
        closeBinding(binding);
    }

    private void closeBinding(BindingSlot binding) {
        if (binding.deadlineTask != null) {
            binding.deadlineTask.cancel(false);
            binding.deadlineTask = null;
        }
        binding.state = BindingAdmissionState.CLOSED;
        currentBindings.remove(binding.logicalKey, binding);
    }

    private void retireBackend(BackendSlot backend, RevokeMode mode, String reason) {
        if (backend.state == BindingAdmissionState.CLOSED) {
            return;
        }
        backend.lastCloseReason = reason;
        if (mode == RevokeMode.HARD) {
            backend.state = BindingAdmissionState.REVOKED;
            for (LeaseToken lease : new ArrayList<>(backend.leases)) {
                lease.revoke();
            }
            backend.closePool(reason);
            backend.state = BindingAdmissionState.CLOSED;
            return;
        }
        if (backend.state == BindingAdmissionState.OPEN) {
            backend.state = BindingAdmissionState.RETIRING;
        }
        if (backend.activeLeases == 0) {
            backend.closePool(reason);
            backend.state = BindingAdmissionState.CLOSED;
        }
    }

    private LeaseToken admit(BindingSlot binding) throws SQLException {
        synchronized (this) {
            if (binding.state != BindingAdmissionState.OPEN
                    || currentBindings.get(binding.logicalKey) != binding
                    || binding.backend.state != BindingAdmissionState.OPEN) {
                throw revoked(binding.logicalKey, binding.generation);
            }
            LeaseToken lease = new LeaseToken(binding, binding.backend);
            binding.activeLeases++;
            binding.leases.add(lease);
            binding.lastBorrowedAt = clock.instant();
            binding.backend.activeLeases++;
            binding.backend.leases.add(lease);
            binding.backend.lastBorrowedAt = clock.instant();
            return lease;
        }
    }

    private Connection borrow(BindingSlot binding, String username, String password) throws SQLException {
        LeaseToken lease = admit(binding);
        Connection target = null;
        try {
            ManagedDataSourcePool pool = binding.backend.ensurePool();
            target = username != null || password != null
                    ? pool.getConnection(username, password)
                    : pool.getConnection();
            ConnectionCloseTrackingHandler handler = new ConnectionCloseTrackingHandler(target, lease);
            synchronized (this) {
                if (lease.revoked) {
                    target.close();
                    release(lease);
                    throw revoked(binding.logicalKey, binding.generation);
                }
                lease.handler = handler;
            }
            return handler.proxy();
        } catch (SQLException | RuntimeException e) {
            if (target != null) {
                try {
                    target.close();
                } catch (SQLException closeFailure) {
                    e.addSuppressed(closeFailure);
                }
            }
            release(lease);
            throw e;
        }
    }

    private synchronized void release(LeaseToken lease) {
        if (lease.released) {
            return;
        }
        lease.released = true;
        BindingSlot binding = lease.binding;
        BackendSlot backend = lease.backend;
        binding.leases.remove(lease);
        binding.activeLeases = Math.max(0, binding.activeLeases - 1);
        binding.lastReturnedAt = clock.instant();
        backend.leases.remove(lease);
        backend.activeLeases = Math.max(0, backend.activeLeases - 1);
        backend.lastReturnedAt = clock.instant();
        if (binding.state == BindingAdmissionState.RETIRING && binding.activeLeases == 0) {
            closeBinding(binding);
        }
        if (backend.state == BindingAdmissionState.RETIRING && backend.activeLeases == 0) {
            backend.closePool(backend.lastCloseReason != null ? backend.lastCloseReason : "retired");
            backend.state = BindingAdmissionState.CLOSED;
        }
    }

    private synchronized ManagedDataSourcePool metadataPool(BindingSlot binding) throws SQLException {
        if (binding.state != BindingAdmissionState.OPEN || currentBindings.get(binding.logicalKey) != binding) {
            throw revoked(binding.logicalKey, binding.generation);
        }
        return binding.backend.ensurePool();
    }

    private SQLException revoked(String bindingKey, String generation) {
        return new SQLException("DATASOURCE_BINDING_REVOKED: binding is no longer current ["
                + bindingKey + ", generation=" + generation + "]");
    }

    private String resolvePassword(RuntimeDatasourceRecord record) {
        if (record.password() != null) {
            return record.password();
        }
        String ref = record.passwordRef();
        if (!StringUtils.hasText(ref)) {
            return null;
        }
        ref = ref.trim();
        String validationError = validatePasswordRef(ref);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }
        String resolved = resolvePasswordRef(ref);
        if (resolved == null) {
            throw new IllegalArgumentException("Runtime-managed dataSource passwordRef could not be resolved: " + ref);
        }
        return resolved;
    }

    public static String validatePasswordRef(String ref) {
        if (!StringUtils.hasText(ref)) {
            return null;
        }
        String normalized = ref.trim();
        if (normalized.startsWith("env:")) {
            return validatePasswordRefTarget(normalized, "env:");
        }
        if (normalized.startsWith("system:")) {
            return validatePasswordRefTarget(normalized, "system:");
        }
        if (normalized.startsWith("sys:")) {
            return validatePasswordRefTarget(normalized, "sys:");
        }
        int schemeSeparator = normalized.indexOf(':');
        if (schemeSeparator > 0) {
            String scheme = normalized.substring(0, schemeSeparator);
            return "Unsupported passwordRef scheme: " + scheme
                    + ". Supported schemes are env:, system:, sys:, or a bare environment/system property name.";
        }
        return null;
    }

    private static String validatePasswordRefTarget(String ref, String prefix) {
        if (ref.length() == prefix.length() || !StringUtils.hasText(ref.substring(prefix.length()))) {
            return "passwordRef " + prefix + " requires a non-empty key.";
        }
        return null;
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
        return envValue != null ? envValue : System.getProperty(ref);
    }

    private String effectiveRecordGeneration(RuntimeDatasourceRecord record) {
        return StringUtils.hasText(record.bindingGeneration())
                ? record.bindingGeneration()
                : "legacy:" + fingerprint(record, settingsFor(record));
    }

    private String effectiveLegacyGeneration(RuntimeDatasourceRecord record, String logicalBindingKey) {
        return "legacy:" + sha256(logicalBindingKey + "|" + fingerprint(record, settingsFor(record)));
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

    private static String namedBindingKey(String name) {
        return "runtime:named:" + name;
    }

    private static String namespaceBindingKey(String namespace) {
        return "runtime:namespace-default:" + namespace;
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

    private record BackendKey(String name, String generation) {
    }

    private record BindingKey(String logicalKey, String generation) {
    }

    private final class BackendSlot {
        private final RuntimeDatasourceRecord record;
        private final String generation;
        private final String fingerprint;
        private final Set<LeaseToken> leases = new LinkedHashSet<>();
        private BindingAdmissionState state = BindingAdmissionState.OPEN;
        private ManagedDataSourcePool pool;
        private int activeLeases;
        private Instant lastBorrowedAt;
        private Instant lastReturnedAt;
        private String lastCloseReason;
        private String lastCloseError;
        private boolean lastPoolClosed;

        private BackendSlot(RuntimeDatasourceRecord record, String generation) {
            this.record = record;
            this.generation = generation;
            this.fingerprint = fingerprint(record, settingsFor(record));
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

        private synchronized void closeIfIdle() {
            if (state != BindingAdmissionState.OPEN || pool == null || pool.isClosed()
                    || activeLeases > 0 || lastReturnedAt == null) {
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
    }

    private final class BindingSlot {
        private final String logicalKey;
        private final String generation;
        private final BackendSlot backend;
        private final ManagedRuntimeDataSource dataSource = new ManagedRuntimeDataSource(this);
        private final Set<LeaseToken> leases = new LinkedHashSet<>();
        private BindingAdmissionState state = BindingAdmissionState.OPEN;
        private int activeLeases;
        private Instant lastBorrowedAt;
        private Instant lastReturnedAt;
        private Instant drainDeadline;
        private ScheduledFuture<?> deadlineTask;
        private String lastCloseReason;

        private BindingSlot(String logicalKey, String generation, BackendSlot backend) {
            this.logicalKey = logicalKey;
            this.generation = generation;
            this.backend = backend;
        }

        private BindingKey key() {
            return new BindingKey(logicalKey, generation);
        }
    }

    private final class LeaseToken {
        private final BindingSlot binding;
        private final BackendSlot backend;
        private boolean revoked;
        private boolean released;
        private ConnectionCloseTrackingHandler handler;

        private LeaseToken(BindingSlot binding, BackendSlot backend) {
            this.binding = binding;
            this.backend = backend;
        }

        private void revoke() {
            revoked = true;
            ConnectionCloseTrackingHandler current = handler;
            if (current != null) {
                current.forceClose();
            }
        }
    }

    private final class ConnectionCloseTrackingHandler implements InvocationHandler {
        private final Connection target;
        private final LeaseToken lease;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ConnectionCloseTrackingHandler(Connection target, LeaseToken lease) {
            this.target = target;
            this.lease = lease;
        }

        private Connection proxy() {
            return (Connection) Proxy.newProxyInstance(
                    ManagedDataSourcePoolManager.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    this
            );
        }

        private void forceClose() {
            if (closed.compareAndSet(false, true)) {
                try {
                    target.close();
                } catch (SQLException ignored) {
                    // Admission is already revoked; cleanup failure is diagnostic only.
                } finally {
                    release(lease);
                }
            }
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
                        release(lease);
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
                return "ManagedRuntimeConnection[opaque]";
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private final class ManagedRuntimeDataSource implements DataSource {
        private final BindingSlot binding;

        private ManagedRuntimeDataSource(BindingSlot binding) {
            this.binding = binding;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return borrow(binding, null, null);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return borrow(binding, username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return metadataPool(binding).getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            metadataPool(binding).setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            metadataPool(binding).setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return metadataPool(binding).getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            try {
                return metadataPool(binding).getParentLogger();
            } catch (SQLException e) {
                SQLFeatureNotSupportedException wrapped = new SQLFeatureNotSupportedException(e.getMessage());
                wrapped.initCause(e);
                throw wrapped;
            }
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Managed Runtime datasource does not expose its physical pool delegate");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }

        @Override
        public String toString() {
            return "ManagedRuntimeDataSource[binding=" + binding.logicalKey
                    + ", generation=" + binding.generation + "]";
        }
    }
}
