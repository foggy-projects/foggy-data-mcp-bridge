package com.foggyframework.dataset.db.model.engine.compose.runtime;

import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.semantic.port.ComposeSemanticPlanningPort;
import com.foggyframework.dataset.db.model.semantic.port.ComposeSqlExecutionPort;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;

import java.util.Objects;

/**
 * Immutable bag of runtime dependencies threaded through a Compose Query
 * script execution via {@link ComposeRuntimeHolder}.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code ctx} — the {@link ComposeQueryContext} carrying principal,
 *       authority resolver, namespace, traceId</li>
 *   <li>{@code planningPort} — governed per-model SQL planning</li>
 *   <li>{@code executionPort} — compiled raw-SQL execution</li>
 *   <li>{@code dialect} — SQL dialect (default {@code "mysql"})</li>
 *   <li>{@code normalizePlan} — optional compile pre-normalization
 *       (default {@code false})</li>
 * </ul>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code ComposeRuntimeBundle} from
 * {@code foggy.dataset_model.engine.compose.runtime}.</p>
 *
 * @since 8.2.0.beta
 */
public final class ComposeRuntimeBundle {

    private final ComposeQueryContext ctx;
    /** Compatibility reference retained for existing callers. */
    private final SemanticQueryServiceV3 semanticService;
    private final ComposeSemanticPlanningPort planningPort;
    private final ComposeSqlExecutionPort executionPort;
    private final String dialect;
    private final boolean normalizePlan;

    private ComposeRuntimeBundle(Builder b) {
        this.ctx = Objects.requireNonNull(b.ctx,
                "ComposeRuntimeBundle.ctx is required");
        this.semanticService = b.semanticService;
        this.planningPort = Objects.requireNonNull(
                b.planningPort != null ? b.planningPort : b.semanticService,
                "ComposeRuntimeBundle.semanticService is required");
        this.executionPort = Objects.requireNonNull(
                b.executionPort != null ? b.executionPort : b.semanticService,
                "ComposeRuntimeBundle.semanticService is required");
        this.dialect = b.dialect == null ? "mysql" : b.dialect;
        this.normalizePlan = b.normalizePlan;
    }

    public ComposeQueryContext ctx() { return ctx; }
    public SemanticQueryServiceV3 semanticService() { return semanticService; }
    public ComposeSemanticPlanningPort planningPort() { return planningPort; }
    public ComposeSqlExecutionPort executionPort() { return executionPort; }
    public String dialect() { return dialect; }
    public boolean normalizePlan() { return normalizePlan; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private ComposeQueryContext ctx;
        private SemanticQueryServiceV3 semanticService;
        private ComposeSemanticPlanningPort planningPort;
        private ComposeSqlExecutionPort executionPort;
        private String dialect;
        private boolean normalizePlan;

        public Builder ctx(ComposeQueryContext v) { this.ctx = v; return this; }
        public Builder semanticService(SemanticQueryServiceV3 v) {
            this.semanticService = v;
            this.planningPort = v;
            this.executionPort = v;
            return this;
        }
        public Builder planningPort(ComposeSemanticPlanningPort v) { this.planningPort = v; return this; }
        public Builder executionPort(ComposeSqlExecutionPort v) { this.executionPort = v; return this; }
        public Builder dialect(String v) { this.dialect = v; return this; }
        public Builder normalizePlan(boolean v) { this.normalizePlan = v; return this; }

        public ComposeRuntimeBundle build() { return new ComposeRuntimeBundle(this); }
    }

    @Override
    public String toString() {
        return "ComposeRuntimeBundle{ctx=" + ctx
                + ", planningPort=" + planningPort.getClass().getSimpleName()
                + ", executionPort=" + executionPort.getClass().getSimpleName()
                + ", dialect=" + dialect
                + ", normalizePlan=" + normalizePlan + '}';
    }
}
