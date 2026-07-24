package com.foggyframework.dataset.model.semantic.port;

/** Small model-side boundary for restricted Compose execution. */
@FunctionalInterface
public interface ComposeExecutionPort {

    ComposeExecutionResult execute(ComposeExecutionRequest request);
}
