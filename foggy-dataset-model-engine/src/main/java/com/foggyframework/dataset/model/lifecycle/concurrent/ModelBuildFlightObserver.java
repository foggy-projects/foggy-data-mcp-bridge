package com.foggyframework.dataset.model.lifecycle.concurrent;

/**
 * Optional package-level observability seam for deterministic tests and bounded telemetry.
 * Implementations must not perform lifecycle mutations.
 */
public interface ModelBuildFlightObserver {

    ModelBuildFlightObserver NOOP = new ModelBuildFlightObserver() {
    };

    default void winnerStarted(ModelBuildKey key) {
    }

    default void waiterJoined(ModelBuildKey key, int waiterCount) {
    }

    default void flightCompleted(ModelBuildKey key, Completion completion) {
    }

    default void flightRemoved(ModelBuildKey key) {
    }

    enum Completion {
        SUCCEEDED,
        FAILED
    }
}
