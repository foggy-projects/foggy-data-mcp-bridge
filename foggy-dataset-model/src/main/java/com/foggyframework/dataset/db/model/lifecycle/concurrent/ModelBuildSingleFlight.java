package com.foggyframework.dataset.db.model.lifecycle.concurrent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Caller-inline, per-{@link ModelBuildKey} single-flight coordinator.
 *
 * <p>The winner executes on its calling thread. This class owns no executor and never transfers a
 * candidate or namespace scope between threads. All terminal paths remove only the exact flight
 * instance they installed.</p>
 */
public final class ModelBuildSingleFlight {

    private final ConcurrentHashMap<ModelBuildKey, Flight<?>> inFlight = new ConcurrentHashMap<>();
    private final ThreadLocal<Deque<ModelBuildKey>> buildPath = new ThreadLocal<>();
    private final ModelBuildFlightObserver observer;

    public ModelBuildSingleFlight() {
        this(ModelBuildFlightObserver.NOOP);
    }

    public ModelBuildSingleFlight(ModelBuildFlightObserver observer) {
        this.observer = observer == null ? ModelBuildFlightObserver.NOOP : observer;
    }

    public <T> T execute(ModelBuildKey key, Supplier<T> builder) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(builder, "builder");
        rejectCycleAlreadyOnPath(key);

        // Missing binding identity must never become reusable coordination identity.
        if (!key.isShareable()) {
            return executeIsolated(key, builder);
        }

        Flight<T> candidate = new Flight<>(Thread.currentThread());
        Flight<?> existing = inFlight.putIfAbsent(key, candidate);
        if (existing != null) {
            rejectSelfWait(key, existing);
            int waiterCount = existing.waiterCount.incrementAndGet();
            observe(() -> observer.waiterJoined(key, waiterCount));
            return await(existing);
        }

        try (BuildPathScope ignored = enterBuildPath(key)) {
            observe(() -> observer.winnerStarted(key));
            try {
                T result = builder.get();
                candidate.result.complete(result);
                observe(() -> observer.flightCompleted(
                        key, ModelBuildFlightObserver.Completion.SUCCEEDED));
                return result;
            } catch (Throwable failure) {
                candidate.result.completeExceptionally(failure);
                observe(() -> observer.flightCompleted(
                        key, ModelBuildFlightObserver.Completion.FAILED));
                return rethrowExact(failure);
            }
        } finally {
            if (inFlight.remove(key, candidate)) {
                observe(() -> observer.flightRemoved(key));
            }
        }
    }

    public int inFlightCount() {
        return inFlight.size();
    }

    public boolean isInFlight(ModelBuildKey key) {
        return key != null && inFlight.containsKey(key);
    }

    /** Package-private deterministic diagnostic; production callers must not branch on it. */
    int currentThreadBuildDepth() {
        Deque<ModelBuildKey> path = buildPath.get();
        return path == null ? 0 : path.size();
    }

    /** Package-private deterministic diagnostic; production callers must not branch on it. */
    boolean hasCurrentThreadBuildState() {
        return buildPath.get() != null;
    }

    private <T> T executeIsolated(ModelBuildKey key, Supplier<T> builder) {
        try (BuildPathScope ignored = enterBuildPath(key)) {
            return builder.get();
        }
    }

    private void rejectCycleAlreadyOnPath(ModelBuildKey requested) {
        Deque<ModelBuildKey> path = buildPath.get();
        if (path != null && containsLogicalModel(path, requested)) {
            throw cycle(path, requested);
        }
    }

    private void rejectSelfWait(ModelBuildKey requested, Flight<?> existing) {
        Deque<ModelBuildKey> path = buildPath.get();
        if ((path != null && containsLogicalModel(path, requested))
                || existing.ownerThread == Thread.currentThread()) {
            throw cycle(path, requested);
        }
    }

    private BuildPathScope enterBuildPath(ModelBuildKey key) {
        Deque<ModelBuildKey> path = buildPath.get();
        if (path == null) {
            path = new ArrayDeque<>();
            buildPath.set(path);
        }
        if (containsLogicalModel(path, key)) {
            throw cycle(path, key);
        }
        path.addLast(key);
        return new BuildPathScope(key);
    }

    private ModelBuildCyclicDependencyException cycle(
            Deque<ModelBuildKey> currentPath,
            ModelBuildKey requested
    ) {
        List<ModelBuildKey> path = currentPath == null
                ? new ArrayList<>()
                : new ArrayList<>(currentPath);
        int first = -1;
        for (int index = 0; index < path.size(); index++) {
            if (path.get(index).sameLogicalModel(requested)) {
                first = index;
                break;
            }
        }
        List<ModelBuildKey> cycle = first < 0
                ? new ArrayList<>(List.of(requested))
                : new ArrayList<>(path.subList(first, path.size()));
        cycle.add(requested);
        return new ModelBuildCyclicDependencyException(cycle);
    }

    private boolean containsLogicalModel(Deque<ModelBuildKey> path, ModelBuildKey requested) {
        for (ModelBuildKey existing : path) {
            if (existing.sameLogicalModel(requested)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private <T> T await(Flight<?> flight) {
        try {
            return ((CompletableFuture<T>) flight.result).get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for model build", interrupted);
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            return rethrowExact(cause);
        }
    }

    private static <T> T rethrowExact(Throwable failure) {
        return ModelBuildSingleFlight.<RuntimeException, T>throwUnchecked(failure);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable, T> T throwUnchecked(Throwable failure) throws E {
        throw (E) failure;
    }

    private void observe(Runnable notification) {
        try {
            notification.run();
        } catch (Throwable ignored) {
            // Observability must not change build or publication semantics.
        }
    }

    private static final class Flight<T> {
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private final Thread ownerThread;
        private final AtomicInteger waiterCount = new AtomicInteger();

        private Flight(Thread ownerThread) {
            this.ownerThread = ownerThread;
        }
    }

    private final class BuildPathScope implements AutoCloseable {
        private final ModelBuildKey expected;
        private boolean closed;

        private BuildPathScope(ModelBuildKey expected) {
            this.expected = expected;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            Deque<ModelBuildKey> path = buildPath.get();
            ModelBuildKey actual = path == null ? null : path.pollLast();
            if (!expected.equals(actual)) {
                buildPath.remove();
                throw new IllegalStateException("model build path closed out of order");
            }
            if (path.isEmpty()) {
                buildPath.remove();
            }
        }
    }
}
