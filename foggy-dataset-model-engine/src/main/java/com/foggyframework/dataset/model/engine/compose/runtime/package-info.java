/**
 * M7 Compose Query runtime — script execution infrastructure.
 *
 * <p>This package mirrors Python
 * {@code foggy.dataset_model.engine.compose.runtime} and provides:
 * <ul>
 *   <li>{@link com.foggyframework.dataset.model.engine.compose.runtime.ComposeRuntimeBundle}
 *       — immutable bag of ctx + planning/execution ports + dialect</li>
 *   <li>{@link com.foggyframework.dataset.model.engine.compose.runtime.ComposeRuntimeHolder}
 *       — {@code ThreadLocal<Deque>} for ambient bundle access</li>
 *   <li>{@link com.foggyframework.dataset.model.engine.compose.runtime.ContextBridge}
 *       — {@code ToolExecutionContext → ComposeQueryContext} bridge (embedded mode)</li>
 *   <li>{@link com.foggyframework.dataset.model.engine.compose.runtime.PlanExecution}
 *       — {@code QueryPlan → List<Map>} via planning + raw-SQL execution ports</li>
 *   <li>{@link com.foggyframework.dataset.model.engine.compose.runtime.ComposeScriptService}
 *       — host-facing validate/preview/execute facade over restricted fsscript</li>
 *   <li>{@link com.foggyframework.dataset.model.engine.compose.runtime.ScriptRuntime}
 *       — fsscript evaluator with sandboxed visible surface ({@code from}, {@code dsl})</li>
 * </ul>
 *
 * @since 8.2.0.beta
 */
package com.foggyframework.dataset.model.engine.compose.runtime;
