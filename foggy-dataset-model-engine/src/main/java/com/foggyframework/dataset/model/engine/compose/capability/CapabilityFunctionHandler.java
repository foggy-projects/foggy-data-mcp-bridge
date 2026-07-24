package com.foggyframework.dataset.model.engine.compose.capability;

import java.util.Map;

/**
 * Functional interface for {@code pure_runtime} function handlers.
 *
 * <p>A handler receives keyword arguments (keyed by argument name)
 * and must return a safe value (primitive, String, Map, or List).</p>
 *
 * <p>Handlers do NOT receive {@code ComposeQueryContext}, principal,
 * authority resolver, semantic service, or {@code ApplicationContext}.</p>
 *
 * @since 8.4.0
 */
@FunctionalInterface
public interface CapabilityFunctionHandler {

    /**
     * Execute a pure_runtime function call.
     *
     * @param args argument name → value mapping
     * @return a safe return value
     */
    Object handle(Map<String, Object> args);
}
