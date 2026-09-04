package com.foggyframework.dataset.model.lifecycle.refresh;

import com.foggyframework.dataset.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogAdmissionState;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogBuildView;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogCandidate;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.model.lifecycle.catalog.StaleCatalogBuildException;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.model.lifecycle.port.DatasourceBindingResolver;
import com.foggyframework.dataset.model.lifecycle.port.StaleDatasourceBindingException;
import com.foggyframework.dataset.model.spi.NamespaceContext;
import com.foggyframework.dataset.model.spi.NamespaceScope;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Synchronous per-namespace refresh authority. It owns coordination and the
 * sole commit; callers own scheduling and detached model construction.
 */
public final class CatalogRefreshCoordinator {

    private static final int MAX_STABLE_DISCOVERY_ATTEMPTS = 3;

    private final CatalogSnapshotStore snapshotStore;
    private final DatasourceBindingResolver bindingResolver;
    private final TableModelLoaderManagerImpl tableModelLoader;
    private final QueryModelLoaderImpl queryModelLoader;
    private final ConcurrentMap<String, NamespaceMutex> namespaceLocks =
            new ConcurrentHashMap<>();

    /** Core-only constructor for callers that supply an exact plan and callback. */
    public CatalogRefreshCoordinator(CatalogSnapshotStore snapshotStore) {
        this(snapshotStore, null, null, null);
    }

    /** Core-only constructor with an atomic datasource publication guard. */
    public CatalogRefreshCoordinator(
            CatalogSnapshotStore snapshotStore,
            DatasourceBindingResolver bindingResolver
    ) {
        this(snapshotStore, bindingResolver, null, null);
    }

    /**
     * Compatibility adapter for the current TM/QM loaders. Their refresh
     * methods stage only; this coordinator still performs the single commit.
     */
    public CatalogRefreshCoordinator(
            CatalogSnapshotStore snapshotStore,
            TableModelLoaderManagerImpl tableModelLoader,
            QueryModelLoaderImpl queryModelLoader
    ) {
        this(
                snapshotStore,
                Objects.requireNonNull(tableModelLoader, "tableModelLoader")
                        .getNamedDataSourceResolver(),
                tableModelLoader,
                Objects.requireNonNull(queryModelLoader, "queryModelLoader"));
    }

    private CatalogRefreshCoordinator(
            CatalogSnapshotStore snapshotStore,
            DatasourceBindingResolver bindingResolver,
            TableModelLoaderManagerImpl tableModelLoader,
            QueryModelLoaderImpl queryModelLoader
    ) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.bindingResolver = bindingResolver;
        this.tableModelLoader = tableModelLoader;
        this.queryModelLoader = queryModelLoader;
    }

    /** Discovers and stages through the configured current TM/QM loaders. */
    public CatalogRefreshResult refresh(CatalogRefreshRequest request) {
        return refreshWithConfiguredDiscovery(
                request, this::stageWithConfiguredLoaders, true);
    }

    /**
     * Uses configured source discovery but delegates detached construction to
     * a caller callback. No executor or thread is owned by this coordinator.
     */
    public CatalogRefreshResult refresh(
            CatalogRefreshRequest request,
            CatalogRefreshCallback callback
    ) {
        return refreshWithConfiguredDiscovery(request, callback, false);
    }

    private CatalogRefreshResult refreshWithConfiguredDiscovery(
            CatalogRefreshRequest request,
            CatalogRefreshCallback callback,
            boolean retrySourceStale
    ) {
        CatalogRefreshRequest validated = Objects.requireNonNull(request, "request");
        Objects.requireNonNull(callback, "callback");
        if (queryModelLoader == null) {
            throw new IllegalStateException(
                    "CATALOG_REFRESH_DISCOVERY_CALLBACK_REQUIRED");
        }

        NamespaceMutex mutex = acquireNamespace(validated.namespace());
        try (CatalogSnapshotStore.RefreshActivity ignored =
                     snapshotStore.beginRefresh(validated.namespace())) {
            int attempts = retrySourceStale
                    ? MAX_STABLE_DISCOVERY_ATTEMPTS
                    : 1;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                CatalogBuildView attemptView;
                CatalogAdmissionState admissionBefore =
                        snapshotStore.admissionState(validated.namespace());
                try {
                    attemptView = snapshotStore.capture(validated.namespace());
                } catch (Throwable failure) {
                    if (failure instanceof Error error) {
                        throw error;
                    }
                    CatalogRefreshException refreshFailure = refreshFailure(
                            validated,
                            snapshotStore.current(validated.namespace())
                                    .map(CatalogSnapshot::identity)
                                    .orElse(null),
                            failure);
                    if (retrySourceStale
                            && "SOURCE_REVISION_STALE".equals(
                            refreshFailure.code())
                            && attempt < attempts) {
                        continue;
                    }
                    throw refreshFailure;
                }
                try {
                    StableDiscovery stable = captureStableDiscovery(
                            validated.namespace(), attemptView);
                    CatalogRefreshPlan plan = plan(validated, stable.names());
                    return refreshLocked(
                            validated,
                            plan,
                            stable.buildView(),
                            callback,
                            admissionBefore);
                } catch (CatalogRefreshException failure) {
                    if (!retrySourceStale
                            || !"SOURCE_REVISION_STALE".equals(failure.code())
                            || attempt == attempts) {
                        throw failure;
                    }
                } catch (Throwable failure) {
                    handleFailedRefresh(
                            validated, attemptView, admissionBefore, failure);
                    if (failure instanceof Error error) {
                        throw error;
                    }
                    CatalogRefreshException refreshFailure = refreshFailure(
                            validated, identity(attemptView), failure);
                    if (retrySourceStale
                            && "SOURCE_REVISION_STALE".equals(
                            refreshFailure.code())
                            && attempt < attempts) {
                        continue;
                    }
                    throw refreshFailure;
                }
            }
            throw new IllegalStateException(
                    "SOURCE_REVISION_REFRESH_RETRY_EXHAUSTED");
        } finally {
            releaseNamespace(validated.namespace(), mutex);
        }
    }

    /**
     * Generic exact-plan boundary used by source adapters that own discovery.
     * The plan is applied only to a detached candidate and is source-guarded
     * again at final publication.
     */
    public CatalogRefreshResult refresh(
            CatalogRefreshRequest request,
            CatalogRefreshPlan plan,
            CatalogRefreshCallback callback
    ) {
        CatalogRefreshRequest validated = Objects.requireNonNull(request, "request");
        CatalogRefreshPlan exactPlan = Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(callback, "callback");
        validatePlanMatchesRequest(validated, exactPlan);

        NamespaceMutex mutex = acquireNamespace(validated.namespace());
        try (CatalogSnapshotStore.RefreshActivity ignored =
                     snapshotStore.beginRefresh(validated.namespace())) {
            CatalogAdmissionState admissionBefore =
                    snapshotStore.admissionState(validated.namespace());
            CatalogBuildView buildView = snapshotStore.capture(validated.namespace());
            return refreshLocked(
                    validated, exactPlan, buildView, callback, admissionBefore);
        } finally {
            releaseNamespace(validated.namespace(), mutex);
        }
    }

    private CatalogRefreshResult refreshLocked(
            CatalogRefreshRequest request,
            CatalogRefreshPlan plan,
            CatalogBuildView buildView,
            CatalogRefreshCallback callback,
            CatalogAdmissionState admissionBefore
    ) {
        long started = System.nanoTime();
        CatalogIdentity beforeIdentity = buildView.baseSnapshot() == null
                ? null
                : buildView.baseSnapshot().identity();
        Set<CatalogModelKey> beforeKeys = modelKeys(buildView.baseSnapshot());

        try (NamespaceScope ignored = NamespaceContext.open(request.namespace());
             CatalogSnapshotStore.CandidateScope scope =
                     snapshotStore.openCandidate(buildView)) {
            if (!scope.isOwner()) {
                throw new IllegalStateException(
                        "CATALOG_REFRESH_NESTED_OWNER_REQUIRED");
            }
            CatalogCandidate candidate = scope.candidate();
            Set<CatalogModelKey> invalidated = candidate.applyRefreshPlan(plan);
            CatalogRefreshBuildContext context = new CatalogRefreshBuildContext(
                    buildView, candidate, plan, invalidated);
            try {
                callback.build(context);
            } catch (Throwable failure) {
                candidate.fail("catalog refresh detached build failed");
                if (failure instanceof Error error) {
                    throw error;
                }
                if (failure instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException(
                        "CATALOG_REFRESH_BUILD_FAILED", failure);
            }

            Map<String, DatasourceBindingIdentity> effectiveBindings =
                    candidate.effectiveDatasourceBindings();
            CatalogSnapshot published = publish(
                    scope, effectiveBindings.values());
            if (published == null) {
                throw new IllegalStateException(
                        "CATALOG_REFRESH_PUBLISHED_SNAPSHOT_ABSENT");
            }

            Set<CatalogModelKey> afterKeys = modelKeys(published);
            Set<CatalogModelKey> refreshed = refreshedModels(
                    request.scope(), beforeKeys, afterKeys, invalidated);
            Set<CatalogModelKey> preserved = preservedModels(
                    beforeKeys, afterKeys, invalidated);
            List<DatasourceBindingIdentity> affected = affectedBindings(
                    published, refreshed);
            return new CatalogRefreshResult(
                    request.namespace(),
                    request.scope(),
                    beforeIdentity,
                    published.identity(),
                    published.identity().sourceRevision(),
                    refreshed,
                    preserved,
                    affected,
                    elapsedMillis(started),
                    snapshotStore.admissionState(request.namespace()),
                    List.of());
        } catch (Throwable failure) {
            handleFailedRefresh(
                    request, buildView, admissionBefore, failure);
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure instanceof CatalogRefreshException refreshFailure) {
                throw refreshFailure;
            }
            throw refreshFailure(request, beforeIdentity, failure);
        }
    }

    private CatalogSnapshot publish(
            CatalogSnapshotStore.CandidateScope scope,
            Collection<DatasourceBindingIdentity> effectiveBindings
    ) {
        if (!effectiveBindings.isEmpty() && bindingResolver == null) {
            throw new IllegalStateException(
                    "DATASOURCE_BINDING_PUBLICATION_GUARD_UNAVAILABLE");
        }
        // Snapshot sorting, alias freezing and dependency validation are all
        // detached work. Keep them outside the registry mutation monitor; the
        // guarded commit below retains binding/source/store currentness and
        // performs only the final atomic catalog swap.
        scope.prepareCommit();
        if (effectiveBindings.isEmpty()) {
            return scope.commit();
        }
        return bindingResolver.publishIfCurrent(effectiveBindings, scope::commit);
    }

    private void stageWithConfiguredLoaders(CatalogRefreshBuildContext context) {
        if (tableModelLoader == null || queryModelLoader == null) {
            throw new IllegalStateException("CATALOG_REFRESH_BUILD_CALLBACK_REQUIRED");
        }
        CatalogCandidate candidate = context.candidate();
        CatalogRefreshPlan plan = context.plan();
        TreeSet<CatalogModelKey> toBuild = new TreeSet<>();
        if (plan.scope() == CatalogRefreshScope.NAMESPACE) {
            plan.discoveredQueryModelNames().stream()
                    .map(CatalogModelKey::query)
                    .forEach(toBuild::add);
        } else {
            toBuild.addAll(context.invalidatedModels());
        }

        for (CatalogModelKey key : toBuild) {
            switch (key.kind()) {
                case TABLE -> tableModelLoader.stageForRefresh(
                        key.canonicalName(),
                        candidate.namespace(),
                        context.buildView(),
                        candidate);
                case QUERY -> {
                    if (plan.discoveredQueryModelNames()
                            .contains(key.canonicalName())) {
                        queryModelLoader.stageForRefresh(
                                key.canonicalName(),
                                candidate.namespace(),
                                plan.discoveredQueryModelNames(),
                                context.buildView(),
                                candidate);
                    }
                }
                case SYNTHETIC_QUERY -> stageSyntheticIfSourceSurvives(
                        key, context);
            }
        }
    }

    private void stageSyntheticIfSourceSurvives(
            CatalogModelKey key,
            CatalogRefreshBuildContext context
    ) {
        int separator = key.canonicalName().indexOf('#');
        if (separator <= 0) {
            return;
        }
        String sourceName = key.canonicalName().substring(0, separator);
        if (context.candidate().findQueryModel(sourceName) == null) {
            return;
        }
        TreeSet<String> discovery = new TreeSet<>(
                context.plan().discoveredQueryModelNames());
        discovery.add(key.canonicalName());
        queryModelLoader.stageForRefresh(
                key.canonicalName(),
                context.candidate().namespace(),
                discovery,
                context.buildView(),
                context.candidate());
    }

    private StableDiscovery captureStableDiscovery(
            String namespace,
            CatalogBuildView initialView
    ) {
        CatalogBuildView before = Objects.requireNonNull(
                initialView, "initialView");
        for (int attempt = 1; attempt <= MAX_STABLE_DISCOVERY_ATTEMPTS; attempt++) {
            Set<String> names = queryModelLoader.discoverQueryModelNames(namespace);
            CatalogBuildView after = snapshotStore.capture(namespace);
            if (before.sourceRevision().equals(after.sourceRevision())) {
                return new StableDiscovery(names, after);
            }
            before = after;
        }
        throw new StaleCatalogBuildException(
                namespace,
                StaleCatalogBuildException.Reason.SOURCE_REVISION_CHANGED);
    }

    private void handleFailedRefresh(
            CatalogRefreshRequest request,
            CatalogBuildView buildView,
            CatalogAdmissionState admissionBefore,
            Throwable failure
    ) {
        if (buildView == null || buildView.baseSnapshot() == null) {
            return;
        }
        CatalogSnapshot current = snapshotStore.current(request.namespace())
                .orElse(null);
        if (current == null || current != buildView.baseSnapshot()) {
            return;
        }
        String code = failureCode(failure);
        String diagnostic = code + ": detached refresh published no candidate";
        CatalogAdmissionState admissionNow =
                snapshotStore.admissionState(request.namespace());
        if (admissionBefore == CatalogAdmissionState.STALE_ADMISSION_BLOCKED
                || admissionNow == CatalogAdmissionState.STALE_ADMISSION_BLOCKED
                || !bindingsRemainAdmissible(current)) {
            snapshotStore.markStaleAdmissionBlockedIfCurrent(
                    buildView, diagnostic);
        } else {
            snapshotStore.markActiveOldPreservedIfCurrent(
                    buildView, diagnostic);
        }
    }

    private CatalogIdentity identity(CatalogBuildView buildView) {
        return buildView.baseSnapshot() == null
                ? null
                : buildView.baseSnapshot().identity();
    }

    private CatalogRefreshException refreshFailure(
            CatalogRefreshRequest request,
            CatalogIdentity beforeIdentity,
            Throwable failure
    ) {
        String code = failureCode(failure);
        CatalogRefreshDiagnostic diagnostic = new CatalogRefreshDiagnostic(
                code,
                request.targets().isEmpty()
                        ? request.namespace()
                        : request.targets().toString(),
                "detached catalog refresh published no candidate");
        return new CatalogRefreshException(
                code,
                request,
                beforeIdentity,
                snapshotStore.admissionState(request.namespace()),
                List.of(diagnostic),
                failure);
    }

    private boolean bindingsRemainAdmissible(CatalogSnapshot snapshot) {
        TreeMap<String, DatasourceBindingIdentity> bindings = new TreeMap<>();
        for (ModelProvenance model : snapshot.provenance().values()) {
            // A legacy/default datasource may be executable without exposing a
            // reusable generation identity. That is an untracked compatibility
            // binding, not evidence that a known binding was revoked. Preserve
            // the old catalog after a source/model refresh failure; cache reuse
            // remains fail-closed through bindingIdentityComplete=false.
            for (Map.Entry<String, DatasourceBindingIdentity> entry
                    : model.datasourceBindings().entrySet()) {
                DatasourceBindingIdentity previous = bindings.putIfAbsent(
                        entry.getKey(), entry.getValue());
                if (previous != null && !previous.equals(entry.getValue())) {
                    return false;
                }
            }
        }
        if (bindings.isEmpty()) {
            return true;
        }
        if (bindingResolver == null) {
            return false;
        }
        return bindings.values().stream().allMatch(identity ->
                bindingResolver.currentness(identity) == BindingCurrentness.CURRENT);
    }

    private CatalogRefreshPlan plan(
            CatalogRefreshRequest request,
            Collection<String> discovery
    ) {
        return request.scope() == CatalogRefreshScope.NAMESPACE
                ? CatalogRefreshPlan.namespace(discovery)
                : CatalogRefreshPlan.models(request.targets(), discovery);
    }

    private void validatePlanMatchesRequest(
            CatalogRefreshRequest request,
            CatalogRefreshPlan plan
    ) {
        if (request.scope() != plan.scope()
                || !request.targets().equals(plan.targets())) {
            throw new IllegalArgumentException(
                    "refresh plan does not match request scope/targets");
        }
    }

    private NamespaceMutex acquireNamespace(String namespace) {
        NamespaceMutex mutex = namespaceLocks.compute(namespace, (ignored, current) -> {
            NamespaceMutex selected = current == null
                    ? new NamespaceMutex()
                    : current;
            selected.references++;
            return selected;
        });
        mutex.lock.lock();
        return mutex;
    }

    private void releaseNamespace(String namespace, NamespaceMutex mutex) {
        mutex.lock.unlock();
        namespaceLocks.compute(namespace, (ignored, current) -> {
            if (current != mutex) {
                return current;
            }
            current.references--;
            if (current.references < 0) {
                throw new IllegalStateException(
                        "CATALOG_REFRESH_LOCK_REFERENCE_UNDERFLOW");
            }
            return current.references == 0 ? null : current;
        });
    }

    private Set<CatalogModelKey> modelKeys(CatalogSnapshot snapshot) {
        return snapshot == null
                ? Set.of()
                : immutableModelKeys(snapshot.provenance().keySet());
    }

    private Set<CatalogModelKey> refreshedModels(
            CatalogRefreshScope scope,
            Set<CatalogModelKey> before,
            Set<CatalogModelKey> after,
            Set<CatalogModelKey> invalidated
    ) {
        TreeSet<CatalogModelKey> refreshed = new TreeSet<>();
        if (scope == CatalogRefreshScope.NAMESPACE) {
            refreshed.addAll(after);
        } else {
            refreshed.addAll(after);
            refreshed.retainAll(invalidated);
            TreeSet<CatalogModelKey> added = new TreeSet<>(after);
            added.removeAll(before);
            refreshed.addAll(added);
        }
        return immutableModelKeys(refreshed);
    }

    private Set<CatalogModelKey> preservedModels(
            Set<CatalogModelKey> before,
            Set<CatalogModelKey> after,
            Set<CatalogModelKey> invalidated
    ) {
        TreeSet<CatalogModelKey> preserved = new TreeSet<>(before);
        preserved.retainAll(after);
        preserved.removeAll(invalidated);
        return immutableModelKeys(preserved);
    }

    private List<DatasourceBindingIdentity> affectedBindings(
            CatalogSnapshot snapshot,
            Set<CatalogModelKey> refreshedModels
    ) {
        TreeSet<DatasourceBindingIdentity> bindings = new TreeSet<>();
        for (CatalogModelKey key : refreshedModels) {
            ModelProvenance model = snapshot.provenance().get(key);
            if (model != null) {
                bindings.addAll(model.datasourceBindings().values());
            }
        }
        return List.copyOf(bindings);
    }

    private Set<CatalogModelKey> immutableModelKeys(
            Collection<CatalogModelKey> source
    ) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(
                new TreeSet<>(source == null ? Set.of() : source)));
    }

    private long elapsedMillis(long started) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - started));
    }

    private String failureCode(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof StaleDatasourceBindingException) {
                return "DATASOURCE_BINDING_NOT_CURRENT";
            }
            if (current instanceof StaleCatalogBuildException stale) {
                return stale.reason()
                        == StaleCatalogBuildException.Reason.SOURCE_REVISION_CHANGED
                        ? "SOURCE_REVISION_STALE"
                        : "CATALOG_GENERATION_STALE";
            }
            current = current.getCause();
        }
        return "CATALOG_REFRESH_FAILED";
    }

    private record StableDiscovery(
            Set<String> names,
            CatalogBuildView buildView
    ) {

        private StableDiscovery {
            names = Collections.unmodifiableSet(new LinkedHashSet<>(
                    new TreeSet<>(Objects.requireNonNull(names, "names"))));
            Objects.requireNonNull(buildView, "buildView");
        }
    }

    private static final class NamespaceMutex {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }
}
