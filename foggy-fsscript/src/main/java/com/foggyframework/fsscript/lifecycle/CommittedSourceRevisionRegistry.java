package com.foggyframework.fsscript.lifecycle;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/** Linearization boundary shared by source mutations and catalog publication. */
public final class CommittedSourceRevisionRegistry {

    private final String bootEpoch;
    private final AtomicLong nextSequence = new AtomicLong();
    private final ReentrantReadWriteLock commitLock = new ReentrantReadWriteLock();
    private final Map<String, Long> namespaceSequences = new LinkedHashMap<>();
    private long globalSequence;

    public CommittedSourceRevisionRegistry() {
        this(UUID.randomUUID().toString());
    }

    CommittedSourceRevisionRegistry(String bootEpoch) {
        if (bootEpoch == null || bootEpoch.isBlank()) {
            throw new IllegalArgumentException("bootEpoch must not be blank");
        }
        this.bootEpoch = bootEpoch;
    }

    /** Plain read; never allocates or advances a revision. */
    public String currentRevision(String namespace) {
        String canonical = canonicalNamespace(namespace);
        commitLock.readLock().lock();
        try {
            return revisionValue(canonical);
        } finally {
            commitLock.readLock().unlock();
        }
    }

    /** Runs the mutation and advances its committed revision under one write lock. */
    public <T> MutationCommit<T> commit(Supplier<ScopedMutation<T>> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        commitLock.writeLock().lock();
        try {
            ScopedMutation<T> outcome = Objects.requireNonNull(
                    mutation.get(), "source mutation outcome");
            Set<String> namespaces = canonicalNamespaces(outcome.affectedNamespaces());
            if (outcome.scopeKnown() && namespaces.isEmpty()) {
                throw new IllegalArgumentException(
                        "known source mutation scope must contain a namespace");
            }
            if (outcome.scopeKnown()) {
                for (String namespace : namespaces) {
                    namespaceSequences.put(namespace, nextSequence.incrementAndGet());
                }
            } else {
                globalSequence = nextSequence.incrementAndGet();
            }
            Map<String, String> revisions = new LinkedHashMap<>();
            for (String namespace : namespaces) {
                revisions.put(namespace, revisionValue(namespace));
            }
            return new MutationCommit<>(
                    outcome.value(),
                    outcome.scopeKnown(),
                    namespaces,
                    Map.copyOf(revisions),
                    globalSequence);
        } finally {
            commitLock.writeLock().unlock();
        }
    }

    public <T> MutationCommit<T> commitKnown(
            Collection<String> affectedNamespaces,
            Supplier<T> mutation
    ) {
        Set<String> canonical = canonicalNamespaces(affectedNamespaces);
        return commit(() -> ScopedMutation.known(mutation.get(), canonical));
    }

    public <T> MutationCommit<T> commitUnknown(Supplier<T> mutation) {
        return commit(() -> ScopedMutation.unknown(mutation.get()));
    }

    /** Final currentness check and publication execute under the source read lock. */
    public <T> T publishIfCurrent(
            String namespace,
            String expectedRevision,
            Supplier<T> publication
    ) {
        Objects.requireNonNull(expectedRevision, "expectedRevision");
        Objects.requireNonNull(publication, "publication");
        String canonical = canonicalNamespace(namespace);
        commitLock.readLock().lock();
        try {
            if (!expectedRevision.equals(revisionValue(canonical))) {
                throw new CommittedSourceRevisionChangedException(canonical);
            }
            return publication.get();
        } finally {
            commitLock.readLock().unlock();
        }
    }

    private String revisionValue(String canonicalNamespace) {
        long namespaceSequence = namespaceSequences.getOrDefault(canonicalNamespace, 0L);
        return "source:" + bootEpoch + ":g" + globalSequence + ":n" + namespaceSequence;
    }

    private static Set<String> canonicalNamespaces(Collection<String> namespaces) {
        TreeSet<String> canonical = new TreeSet<>();
        if (namespaces != null) {
            for (String namespace : namespaces) {
                canonical.add(canonicalNamespace(namespace));
            }
        }
        return Set.copyOf(new LinkedHashSet<>(canonical));
    }

    private static String canonicalNamespace(String namespace) {
        return namespace == null || namespace.trim().isEmpty() ? "" : namespace.trim();
    }

    public record ScopedMutation<T>(
            T value,
            boolean scopeKnown,
            Set<String> affectedNamespaces
    ) {
        public ScopedMutation {
            affectedNamespaces = affectedNamespaces == null
                    ? Set.of()
                    : Set.copyOf(affectedNamespaces);
        }

        public static <T> ScopedMutation<T> known(T value, Collection<String> namespaces) {
            return new ScopedMutation<>(value, true,
                    namespaces == null ? Set.of() : Set.copyOf(namespaces));
        }

        public static <T> ScopedMutation<T> unknown(T value) {
            return new ScopedMutation<>(value, false, Set.of());
        }
    }

    public record MutationCommit<T>(
            T value,
            boolean scopeKnown,
            Set<String> affectedNamespaces,
            Map<String, String> committedRevisions,
            long globalSequence
    ) {
        public MutationCommit {
            affectedNamespaces = Set.copyOf(affectedNamespaces);
            committedRevisions = Map.copyOf(committedRevisions);
        }

        public String revisionFor(String namespace) {
            return committedRevisions.get(canonicalNamespace(namespace));
        }
    }

    public static final class CommittedSourceRevisionChangedException
            extends IllegalStateException {
        public CommittedSourceRevisionChangedException(String namespace) {
            super("SOURCE_REVISION_STALE: committed source changed for namespace '"
                    + namespace + "'");
        }
    }
}
