package com.foggyframework.dataset.model.lifecycle.catalog;

import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.lifecycle.port.CommittedSourceRevisionGuard;
import com.foggyframework.dataset.model.lifecycle.port.SourceRevisionProvider;
import com.foggyframework.dataset.model.lifecycle.port.StaleSourceRevisionException;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/** Per-namespace immutable catalog authority. */
public final class CatalogSnapshotStore implements SourceRevisionProvider {

    private final String bootEpoch;
    private final AtomicLong nextCatalogSequence = new AtomicLong();
    private final CommittedSourceRevisionGuard sourceRevisionGuard;
    private final Map<String, NamespaceEntry> entries = new ConcurrentHashMap<>();
    private final ThreadLocal<ActiveCandidate> activeCandidate = new ThreadLocal<>();

    public CatalogSnapshotStore() {
        this(UUID.randomUUID().toString(), null);
    }

    /** Uses an external committed-source guard without changing catalog ownership. */
    public CatalogSnapshotStore(CommittedSourceRevisionGuard sourceRevisionGuard) {
        this(UUID.randomUUID().toString(), sourceRevisionGuard);
    }

    CatalogSnapshotStore(String bootEpoch) {
        this(bootEpoch, null);
    }

    CatalogSnapshotStore(
            String bootEpoch,
            CommittedSourceRevisionGuard sourceRevisionGuard
    ) {
        if (bootEpoch == null || bootEpoch.isBlank()) {
            throw new IllegalArgumentException("bootEpoch must not be blank");
        }
        this.bootEpoch = bootEpoch;
        this.sourceRevisionGuard = sourceRevisionGuard == null
                ? new ProcessLocalSourceRevisionGuard(bootEpoch)
                : sourceRevisionGuard;
    }

    /** Plain read: never allocates or advances a generation. */
    public Optional<CatalogSnapshot> current(String namespace) {
        NamespaceEntry entry = entries.get(CatalogIdentity.canonicalNamespace(namespace));
        return Optional.ofNullable(entry == null ? null : entry.snapshot);
    }

    /** Read-path view that enforces stale-source admission independently of object retention. */
    public Optional<CatalogSnapshot> readCurrent(String namespace) {
        String canonical = CatalogIdentity.canonicalNamespace(namespace);
        NamespaceEntry entry = entries.get(canonical);
        if (entry == null || entry.admissionState == CatalogAdmissionState.ABSENT) {
            return Optional.empty();
        }
        if (entry.admissionState == CatalogAdmissionState.STALE_ADMISSION_BLOCKED) {
            throw new CatalogAdmissionBlockedException(canonical, entry.admissionDiagnostic);
        }
        CatalogSnapshot snapshot = entry.snapshot;
        if (snapshot == null) {
            throw new IllegalStateException(
                    "CATALOG_ADMISSION_STATE_INVALID: active catalog is absent");
        }
        return Optional.of(snapshot);
    }

    public CatalogAdmissionState admissionState(String namespace) {
        NamespaceEntry entry = entries.get(CatalogIdentity.canonicalNamespace(namespace));
        return entry == null ? CatalogAdmissionState.ABSENT : entry.admissionState;
    }

    public Optional<String> admissionDiagnostic(String namespace) {
        NamespaceEntry entry = entries.get(CatalogIdentity.canonicalNamespace(namespace));
        return Optional.ofNullable(entry == null ? null : entry.admissionDiagnostic);
    }

    public Set<String> knownNamespaces() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(new java.util.TreeSet<>(entries.keySet())));
    }

    public Optional<CatalogCandidate> currentCandidate(String namespace) {
        ActiveCandidate active = activeCandidate.get();
        String canonical = CatalogIdentity.canonicalNamespace(namespace);
        return active != null && canonical.equals(active.namespace)
                ? Optional.of(active.candidate)
                : Optional.empty();
    }

    /**
     * Captures the exact immutable base and committed source revision for a
     * detached build. Repeated cold captures are stable until either source or
     * catalog publication changes the namespace view.
     */
    public CatalogBuildView capture(String namespace) {
        String canonical = CatalogIdentity.canonicalNamespace(namespace);
        NamespaceEntry entry = namespaceEntry(canonical);
        // A double revision read is a non-blocking source/catalog seqlock. It
        // avoids taking the external source guard in reverse order with the
        // catalog publication lock while still returning one coherent view.
        for (int attempt = 1; attempt <= 16; attempt++) {
            SourceRevision before = sourceRevisionGuard.currentSourceRevision(canonical);
            CatalogSnapshot snapshot;
            long storeRevision;
            entry.publicationLock.lock();
            try {
                snapshot = entry.snapshot;
                storeRevision = entry.storeRevision;
            } finally {
                entry.publicationLock.unlock();
            }
            SourceRevision after = sourceRevisionGuard.currentSourceRevision(canonical);
            if (before.equals(after)) {
                return new CatalogBuildView(
                        canonical, snapshot, before, storeRevision);
            }
        }
        throw new IllegalStateException(
                "SOURCE_REVISION_CAPTURE_UNSTABLE: namespace='" + canonical + "'");
    }

    @Override
    public SourceRevision currentSourceRevision(String namespace) {
        String canonical = CatalogIdentity.canonicalNamespace(namespace);
        namespaceEntry(canonical);
        return sourceRevisionGuard.currentSourceRevision(canonical);
    }

    /**
     * Opens a request-local candidate. Nested work for the same namespace joins
     * the candidate, so a QM and all of its TMs publish exactly once.
     */
    public CandidateScope openCandidate(String namespace) {
        String canonical = CatalogIdentity.canonicalNamespace(namespace);
        ActiveCandidate active = activeCandidate.get();
        if (active != null) {
            return joinActiveCandidate(active, canonical, null);
        }

        return openCandidate(capture(canonical));
    }

    /** Opens a detached candidate against an explicitly captured build view. */
    public CandidateScope openCandidate(CatalogBuildView buildView) {
        CatalogBuildView captured = java.util.Objects.requireNonNull(buildView, "buildView");
        String canonical = captured.namespace();
        ActiveCandidate active = activeCandidate.get();
        if (active != null) {
            return joinActiveCandidate(active, canonical, captured);
        }

        NamespaceEntry entry = namespaceEntry(canonical);
        ActiveCandidate created = new ActiveCandidate(
                canonical,
                entry,
                captured,
                new CatalogCandidate(canonical, captured.sourceRevision(), captured.baseSnapshot())
        );
        activeCandidate.set(created);
        return new CandidateScope(created, true);
    }

    /** Compatibility/test invalidation only; production refresh must not call this. */
    public void clearNamespace(String namespace) {
        String canonical = CatalogIdentity.canonicalNamespace(namespace);
        ActiveCandidate active = activeCandidate.get();
        if (active != null && canonical.equals(active.namespace)) {
            throw new IllegalStateException("cannot clear an active catalog candidate");
        }
        NamespaceEntry entry = entries.get(canonical);
        if (entry == null) {
            return;
        }
        // Preserve the legacy process-local invalidation contract: a candidate
        // captured before clear observes a source change, not merely a base
        // object change. External source registries remain the sole owner of
        // their revisions, so storeRevision below is still the universal ABA
        // guard. Advance before taking publicationLock to keep the same
        // source-then-catalog lock order as publishIfCurrent.
        if (sourceRevisionGuard instanceof ProcessLocalSourceRevisionGuard processLocal) {
            processLocal.advance(canonical);
        }
        entry.publicationLock.lock();
        try {
            entry.snapshot = null;
            // Prevent null-base ABA: a detached cold candidate captured before
            // an intervening publish+clear must not become current again merely
            // because both base references are null.
            entry.storeRevision++;
            entry.admissionState = CatalogAdmissionState.ABSENT;
            entry.admissionDiagnostic = null;
        } finally {
            entry.publicationLock.unlock();
        }
    }

    /** Compatibility/test invalidation only. */
    public void clearAll() {
        if (activeCandidate.get() != null) {
            throw new IllegalStateException("cannot clear catalogs while a candidate is active");
        }
        for (String namespace : entries.keySet()) {
            clearNamespace(namespace);
        }
    }

    /**
     * Compatibility helper for the process-local guard. External source
     * registries own their revision mutation and expose it through the injected
     * {@link CommittedSourceRevisionGuard}.
     */
    public SourceRevision advanceSourceRevision(String namespace) {
        String canonical = CatalogIdentity.canonicalNamespace(namespace);
        namespaceEntry(canonical);
        if (!(sourceRevisionGuard instanceof ProcessLocalSourceRevisionGuard processLocal)) {
            throw new IllegalStateException(
                    "SOURCE_REVISION_MUTATION_OWNED_BY_EXTERNAL_GUARD");
        }
        return processLocal.advance(canonical);
    }

    /** Blocks new catalog reads while retaining the old object for diagnostics/recovery. */
    public void markStaleAdmissionBlocked(String namespace, String sanitizedDiagnostic) {
        String canonical = CatalogIdentity.canonicalNamespace(namespace);
        NamespaceEntry entry = namespaceEntry(canonical);
        entry.publicationLock.lock();
        try {
            entry.storeRevision++;
            entry.admissionState = CatalogAdmissionState.STALE_ADMISSION_BLOCKED;
            entry.admissionDiagnostic = sanitizedDiagnostic == null
                    || sanitizedDiagnostic.isBlank()
                    ? "catalog source scope is unknown"
                    : sanitizedDiagnostic;
        } finally {
            entry.publicationLock.unlock();
        }
    }

    /**
     * Applies a failure marker only while the exact catalog/admission view used
     * by the failed attempt is still current. A concurrent publish or newer
     * admission decision advances {@code storeRevision} and makes this a no-op.
     */
    public boolean markStaleAdmissionBlockedIfCurrent(
            CatalogBuildView expected,
            String sanitizedDiagnostic
    ) {
        CatalogBuildView buildView = java.util.Objects.requireNonNull(
                expected, "expected");
        NamespaceEntry entry = namespaceEntry(buildView.namespace());
        entry.publicationLock.lock();
        try {
            if (!matches(entry, buildView)) {
                return false;
            }
            if (entry.admissionState
                    == CatalogAdmissionState.STALE_ADMISSION_BLOCKED) {
                return true;
            }
            entry.storeRevision++;
            entry.admissionState = CatalogAdmissionState.STALE_ADMISSION_BLOCKED;
            entry.admissionDiagnostic = sanitizedDiagnostic == null
                    || sanitizedDiagnostic.isBlank()
                    ? "catalog source scope is unknown"
                    : sanitizedDiagnostic;
            return true;
        } finally {
            entry.publicationLock.unlock();
        }
    }

    /**
     * Records a failed known-scope refresh whose old snapshot and effective
     * datasource bindings remain admissible. The catalog identity is retained
     * and new reads may continue to use it until a later atomic rebuild wins.
     */
    public void markActiveOldPreserved(String namespace, String sanitizedDiagnostic) {
        String canonical = CatalogIdentity.canonicalNamespace(namespace);
        NamespaceEntry entry = namespaceEntry(canonical);
        entry.publicationLock.lock();
        try {
            if (entry.admissionState
                    == CatalogAdmissionState.STALE_ADMISSION_BLOCKED) {
                return;
            }
            if (entry.snapshot == null) {
                throw new IllegalStateException(
                        "CATALOG_ACTIVE_OLD_ABSENT: namespace='" + canonical + "'");
            }
            entry.storeRevision++;
            entry.admissionState = CatalogAdmissionState.ACTIVE_OLD_PRESERVED;
            entry.admissionDiagnostic = sanitizedDiagnostic == null
                    || sanitizedDiagnostic.isBlank()
                    ? "known-scope catalog refresh failed; active catalog preserved"
                    : sanitizedDiagnostic;
        } finally {
            entry.publicationLock.unlock();
        }
    }

    /**
     * Preserves the old catalog only if no concurrent publication or admission
     * transition has superseded the failed attempt's captured build view.
     */
    public boolean markActiveOldPreservedIfCurrent(
            CatalogBuildView expected,
            String sanitizedDiagnostic
    ) {
        CatalogBuildView buildView = java.util.Objects.requireNonNull(
                expected, "expected");
        NamespaceEntry entry = namespaceEntry(buildView.namespace());
        entry.publicationLock.lock();
        try {
            if (!matches(entry, buildView)) {
                return false;
            }
            if (entry.admissionState
                    == CatalogAdmissionState.STALE_ADMISSION_BLOCKED
                    || entry.admissionState
                    == CatalogAdmissionState.ACTIVE_OLD_PRESERVED) {
                return true;
            }
            if (entry.snapshot == null) {
                return false;
            }
            entry.storeRevision++;
            entry.admissionState = CatalogAdmissionState.ACTIVE_OLD_PRESERVED;
            entry.admissionDiagnostic = sanitizedDiagnostic == null
                    || sanitizedDiagnostic.isBlank()
                    ? "known-scope catalog refresh failed; active catalog preserved"
                    : sanitizedDiagnostic;
            return true;
        } finally {
            entry.publicationLock.unlock();
        }
    }

    /** Applies unknown-scope fail-closed admission to every materialized namespace. */
    public Set<String> markKnownNamespacesStaleAdmissionBlocked(String sanitizedDiagnostic) {
        Set<String> affected = knownNamespaces();
        affected.forEach(namespace ->
                markStaleAdmissionBlocked(namespace, sanitizedDiagnostic));
        return affected;
    }

    /**
     * Performs validation and immutable snapshot assembly without holding an
     * external datasource-binding publication guard. The preparation is
     * request-local and can only be published by its owning scope.
     */
    private PreparedPublication prepare(ActiveCandidate active) {
        CatalogCandidate candidate = active.candidate;
        candidate.validateBuildSucceeded();
        if (!candidate.hasObservableChanges()) {
            ensureBuildViewIsCurrent(active);
            candidate.seal();
            return PreparedPublication.noChanges();
        }

        // Reject an already-stale candidate before doing immutable snapshot
        // assembly. The final check below remains authoritative because another
        // detached publisher may win while this candidate freezes.
        ensureBuildViewIsCurrent(active);
        CatalogIdentity identity = new CatalogIdentity(
                active.namespace,
                new CatalogGeneration("catalog:" + bootEpoch + ":"
                        + nextCatalogSequence.incrementAndGet()),
                candidate.sourceRevision()
        );
        CatalogSnapshot snapshot = candidate.freeze(identity);
        candidate.seal();
        return PreparedPublication.changed(snapshot);
    }

    /** Final source/store currentness check and atomic catalog swap only. */
    private CatalogSnapshot publishPrepared(
            ActiveCandidate active,
            PreparedPublication prepared
    ) {
        return publishIfSourceCurrent(active, () -> {
            active.entry.publicationLock.lock();
            try {
                ensureLocalBuildViewIsCurrent(active);
                if (!prepared.observableChanges) {
                    // A no-op cannot prove a blocked source was rebuilt.
                    return active.entry.snapshot;
                }
                active.entry.snapshot = prepared.snapshot;
                active.entry.storeRevision++;
                activate(active.entry);
                return prepared.snapshot;
            } finally {
                active.entry.publicationLock.unlock();
            }
        });
    }

    /** Snapshot used by stale-build diagnostics to identify refresh overlap. */
    public RefreshObservation refreshObservation(String namespace) {
        NamespaceEntry entry = namespaceEntry(
                CatalogIdentity.canonicalNamespace(namespace));
        return new RefreshObservation(
                entry.refreshSequence.get(),
                entry.activeRefreshes.get() > 0);
    }

    /** Marks the exact lifetime of a coordinated explicit refresh attempt. */
    public RefreshActivity beginRefresh(String namespace) {
        NamespaceEntry entry = namespaceEntry(
                CatalogIdentity.canonicalNamespace(namespace));
        entry.activeRefreshes.incrementAndGet();
        entry.refreshSequence.incrementAndGet();
        return new RefreshActivity(entry);
    }

    /**
     * Publishes a lazy-load candidate, rebasing disjoint additive changes when
     * another lazy loader advanced the namespace generation first.
     */
    private CatalogSnapshot publishAdditive(ActiveCandidate active) {
        return publishIfSourceCurrent(active, () -> {
            active.entry.publicationLock.lock();
            try {
                CatalogCandidate publicationCandidate = active.candidate;
                if (!matches(active.entry, active.buildView)) {
                    publicationCandidate = active.candidate.rebaseAdditionsOnto(
                            active.entry.snapshot);
                    if (publicationCandidate == null) {
                        throw new StaleCatalogBuildException(
                                active.namespace,
                                StaleCatalogBuildException.Reason.BASE_CATALOG_CHANGED);
                    }
                }
                publicationCandidate.validateBuildSucceeded();
                if (!publicationCandidate.hasObservableChanges()) {
                    publicationCandidate.seal();
                    return active.entry.snapshot;
                }
                CatalogIdentity identity = new CatalogIdentity(
                        active.namespace,
                        new CatalogGeneration("catalog:" + bootEpoch + ":"
                                + nextCatalogSequence.incrementAndGet()),
                        publicationCandidate.sourceRevision());
                CatalogSnapshot snapshot = publicationCandidate.freeze(identity);
                publicationCandidate.seal();
                active.entry.snapshot = snapshot;
                active.entry.storeRevision++;
                activate(active.entry);
                return snapshot;
            } finally {
                active.entry.publicationLock.unlock();
            }
        });
    }

    private void ensureBuildViewIsCurrent(ActiveCandidate active) {
        SourceRevision current = sourceRevisionGuard.currentSourceRevision(active.namespace);
        if (!current.equals(active.buildView.sourceRevision())) {
            throw new StaleCatalogBuildException(
                    active.namespace,
                    StaleCatalogBuildException.Reason.SOURCE_REVISION_CHANGED);
        }
        active.entry.publicationLock.lock();
        try {
            ensureLocalBuildViewIsCurrent(active);
        } finally {
            active.entry.publicationLock.unlock();
        }
    }

    private CandidateScope joinActiveCandidate(
            ActiveCandidate active,
            String canonicalNamespace,
            CatalogBuildView requestedView
    ) {
        if (!canonicalNamespace.equals(active.namespace)) {
            throw new IllegalStateException("a catalog build cannot switch namespace from '"
                    + active.namespace + "' to '" + canonicalNamespace + "'");
        }
        if (requestedView != null
                && (requestedView.baseSnapshot() != active.buildView.baseSnapshot()
                || !requestedView.sourceRevision().equals(active.buildView.sourceRevision())
                || requestedView.storeRevision() != active.buildView.storeRevision())) {
            throw new IllegalStateException("nested catalog build view does not match its owner candidate");
        }
        active.depth++;
        return new CandidateScope(active, false);
    }

    private void ensureLocalBuildViewIsCurrent(ActiveCandidate active) {
        if (active.entry.snapshot != active.buildView.baseSnapshot()) {
            throw new StaleCatalogBuildException(
                    active.namespace,
                    StaleCatalogBuildException.Reason.BASE_CATALOG_CHANGED);
        }
        if (active.entry.storeRevision != active.buildView.storeRevision()) {
            throw new StaleCatalogBuildException(
                    active.namespace,
                    StaleCatalogBuildException.Reason.BASE_CATALOG_CHANGED);
        }
    }

    private boolean matches(NamespaceEntry entry, CatalogBuildView expected) {
        return entry.snapshot == expected.baseSnapshot()
                && entry.storeRevision == expected.storeRevision();
    }

    private <T> T publishIfSourceCurrent(
            ActiveCandidate active,
            java.util.function.Supplier<T> publication
    ) {
        try {
            return sourceRevisionGuard.publishIfCurrent(
                    active.namespace,
                    active.buildView.sourceRevision(),
                    publication);
        } catch (StaleSourceRevisionException stale) {
            throw new StaleCatalogBuildException(
                    active.namespace,
                    StaleCatalogBuildException.Reason.SOURCE_REVISION_CHANGED);
        }
    }

    private void activate(NamespaceEntry entry) {
        entry.admissionState = entry.snapshot == null
                ? CatalogAdmissionState.ABSENT
                : CatalogAdmissionState.ACTIVE;
        entry.admissionDiagnostic = null;
    }

    private NamespaceEntry namespaceEntry(String canonicalNamespace) {
        return entries.computeIfAbsent(canonicalNamespace, ignored -> new NamespaceEntry());
    }

    public final class CandidateScope implements AutoCloseable {

        private final ActiveCandidate active;
        private final boolean owner;
        private boolean closed;
        private CatalogSnapshot published;
        private PreparedPublication prepared;

        private CandidateScope(ActiveCandidate active, boolean owner) {
            this.active = active;
            this.owner = owner;
        }

        public CatalogCandidate candidate() {
            ensureOpen();
            ensureOwnerThread();
            return active.candidate;
        }

        public boolean isOwner() {
            return owner;
        }

        /**
         * Validates, freezes and seals the candidate before an external
         * datasource-binding guard is entered. Publication remains deferred
         * until {@link #commit()}, which repeats source and local currentness
         * checks immediately before the atomic store swap.
         */
        public void prepareCommit() {
            ensureOpen();
            ensureOwnerThread();
            if (!owner || published != null || prepared != null) {
                return;
            }
            if (active.depth != 1) {
                throw new IllegalStateException(
                        "cannot prepare publication while nested candidate scopes remain open");
            }
            prepared = prepare(active);
        }

        /** Only the outer owner performs publication; nested commits are a no-op. */
        public CatalogSnapshot commit() {
            ensureOpen();
            ensureOwnerThread();
            if (!owner) {
                return active.buildView.baseSnapshot();
            }
            if (active.depth != 1) {
                throw new IllegalStateException("cannot publish while nested candidate scopes remain open");
            }
            if (published == null) {
                prepareCommit();
                published = publishPrepared(active, prepared);
            }
            return published;
        }

        /**
         * Commits an additive lazy model load.  Unlike explicit refresh, a
         * disjoint concurrent lazy publication may be rebased atomically.
         */
        public CatalogSnapshot commitAdditive() {
            ensureOpen();
            ensureOwnerThread();
            if (!owner) {
                return active.buildView.baseSnapshot();
            }
            if (active.depth != 1) {
                throw new IllegalStateException(
                        "cannot publish while nested candidate scopes remain open");
            }
            if (prepared != null) {
                throw new IllegalStateException(
                        "cannot switch a prepared strict publication to additive mode");
            }
            if (published == null) {
                published = publishAdditive(active);
            }
            return published;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            ensureOwnerThread();
            closed = true;
            if (!owner) {
                active.depth--;
                return;
            }
            try {
                if (active.depth != 1) {
                    throw new IllegalStateException("catalog candidate scopes closed out of order");
                }
            } finally {
                try {
                    // A detached candidate must never remain mutable after its
                    // owner scope exits, including abandoned, failed and stale
                    // publication attempts. seal() is intentionally idempotent.
                    active.candidate.seal();
                } finally {
                    activeCandidate.remove();
                }
            }
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("catalog candidate scope is closed");
            }
        }

        private void ensureOwnerThread() {
            if (Thread.currentThread() != active.ownerThread) {
                throw new IllegalStateException(
                        "catalog candidate scope must be used by its owner thread");
            }
        }
    }

    public record RefreshObservation(long sequence, boolean inProgress) {
    }

    public final class RefreshActivity implements AutoCloseable {
        private final NamespaceEntry entry;
        private boolean closed;

        private RefreshActivity(NamespaceEntry entry) {
            this.entry = entry;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            int remaining = entry.activeRefreshes.decrementAndGet();
            if (remaining < 0) {
                throw new IllegalStateException(
                        "CATALOG_REFRESH_ACTIVITY_UNDERFLOW");
            }
            entry.refreshSequence.incrementAndGet();
        }
    }

    private static final class NamespaceEntry {
        private final ReentrantLock publicationLock = new ReentrantLock();
        private final AtomicLong refreshSequence = new AtomicLong();
        private final AtomicInteger activeRefreshes = new AtomicInteger();
        private volatile CatalogSnapshot snapshot;
        private volatile long storeRevision;
        private volatile CatalogAdmissionState admissionState = CatalogAdmissionState.ABSENT;
        private volatile String admissionDiagnostic;
    }

    private static final class PreparedPublication {
        private final boolean observableChanges;
        private final CatalogSnapshot snapshot;

        private PreparedPublication(
                boolean observableChanges,
                CatalogSnapshot snapshot
        ) {
            if (observableChanges != (snapshot != null)) {
                throw new IllegalArgumentException(
                        "changed catalog preparation requires exactly one snapshot");
            }
            this.observableChanges = observableChanges;
            this.snapshot = snapshot;
        }

        private static PreparedPublication noChanges() {
            return new PreparedPublication(false, null);
        }

        private static PreparedPublication changed(CatalogSnapshot snapshot) {
            return new PreparedPublication(
                    true, java.util.Objects.requireNonNull(snapshot, "snapshot"));
        }
    }

    /** Default single-process source authority used when no external registry is wired. */
    private static final class ProcessLocalSourceRevisionGuard
            implements CommittedSourceRevisionGuard {

        private final String bootEpoch;
        private final AtomicLong nextSequence = new AtomicLong();
        private final Map<String, RevisionSlot> revisions = new ConcurrentHashMap<>();

        private ProcessLocalSourceRevisionGuard(String bootEpoch) {
            this.bootEpoch = bootEpoch;
        }

        @Override
        public SourceRevision currentSourceRevision(String namespace) {
            String canonical = CatalogIdentity.canonicalNamespace(namespace);
            RevisionSlot slot = revisions.computeIfAbsent(
                    canonical, ignored -> new RevisionSlot(nextRevision()));
            synchronized (slot) {
                return slot.revision;
            }
        }

        @Override
        public <T> T publishIfCurrent(
                String namespace,
                SourceRevision expected,
                java.util.function.Supplier<T> publication
        ) {
            java.util.Objects.requireNonNull(expected, "expected");
            java.util.Objects.requireNonNull(publication, "publication");
            String canonical = CatalogIdentity.canonicalNamespace(namespace);
            RevisionSlot slot = revisions.computeIfAbsent(
                    canonical, ignored -> new RevisionSlot(nextRevision()));
            synchronized (slot) {
                if (!slot.revision.equals(expected)) {
                    throw new StaleSourceRevisionException(
                            canonical, expected, slot.revision);
                }
                return publication.get();
            }
        }

        private SourceRevision advance(String namespace) {
            String canonical = CatalogIdentity.canonicalNamespace(namespace);
            RevisionSlot slot = revisions.computeIfAbsent(
                    canonical, ignored -> new RevisionSlot(nextRevision()));
            synchronized (slot) {
                slot.revision = nextRevision();
                return slot.revision;
            }
        }

        private SourceRevision nextRevision() {
            return new SourceRevision("source:" + bootEpoch + ":"
                    + nextSequence.incrementAndGet());
        }

        private static final class RevisionSlot {
            private SourceRevision revision;

            private RevisionSlot(SourceRevision revision) {
                this.revision = revision;
            }
        }
    }

    private static final class ActiveCandidate {
        private final String namespace;
        private final NamespaceEntry entry;
        private final CatalogBuildView buildView;
        private final CatalogCandidate candidate;
        private final Thread ownerThread;
        private int depth = 1;

        private ActiveCandidate(
                String namespace,
                NamespaceEntry entry,
                CatalogBuildView buildView,
                CatalogCandidate candidate
        ) {
            this.namespace = namespace;
            this.entry = entry;
            this.buildView = buildView;
            this.candidate = candidate;
            this.ownerThread = Thread.currentThread();
        }
    }
}
