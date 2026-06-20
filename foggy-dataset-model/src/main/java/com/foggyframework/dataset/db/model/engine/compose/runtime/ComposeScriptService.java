package com.foggyframework.dataset.db.model.engine.compose.runtime;

import com.foggyframework.dataset.db.model.engine.compose.capability.CapabilityPolicy;
import com.foggyframework.dataset.db.model.engine.compose.capability.CapabilityRegistry;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;

import java.util.List;
import java.util.Objects;

/**
 * Stable host-facing service boundary for restricted Compose/FSScript execution.
 *
 * <p>P14 keeps Runtime API / CLI integration above this class so Java native
 * REST, Runtime API routes, and future standalone fsscript bridge code do not
 * depend directly on {@link ScriptRuntime} overload details.</p>
 */
public final class ComposeScriptService {

    private ComposeScriptService() { /* utility */ }

    public enum Mode {
        VALIDATE,
        PREVIEW,
        EXECUTE
    }

    public static ComposeScriptResult validate(
            String script,
            ComposeQueryContext ctx,
            SemanticQueryServiceV3 semanticService,
            String dialect) {
        return run(ComposeScriptRequest.builder()
                .mode(Mode.VALIDATE)
                .script(script)
                .ctx(ctx)
                .semanticService(semanticService)
                .dialect(dialect)
                .build());
    }

    public static ComposeScriptResult preview(
            String script,
            ComposeQueryContext ctx,
            SemanticQueryServiceV3 semanticService,
            String dialect) {
        return run(ComposeScriptRequest.builder()
                .mode(Mode.PREVIEW)
                .script(script)
                .ctx(ctx)
                .semanticService(semanticService)
                .dialect(dialect)
                .build());
    }

    public static ComposeScriptResult execute(
            String script,
            ComposeQueryContext ctx,
            SemanticQueryServiceV3 semanticService,
            String dialect) {
        return run(ComposeScriptRequest.builder()
                .mode(Mode.EXECUTE)
                .script(script)
                .ctx(ctx)
                .semanticService(semanticService)
                .dialect(dialect)
                .build());
    }

    public static ComposeScriptResult run(ComposeScriptRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String script = request.script();
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("script must not be blank");
        }
        Mode mode = request.mode() == null ? Mode.EXECUTE : request.mode();
        boolean previewMode = mode != Mode.EXECUTE;

        ScriptRuntime.ScriptResult scriptResult = ScriptRuntime.runScript(
                script,
                request.ctx(),
                request.semanticService(),
                request.dialect(),
                previewMode,
                request.capabilityRegistry(),
                request.capabilityPolicy(),
                request.suspensionManager(),
                request.normalizePlan());
        return ComposeScriptResult.builder()
                .mode(mode)
                .valid(true)
                .executed(mode == Mode.EXECUTE)
                .scriptResult(scriptResult)
                .build();
    }

    public static final class ComposeScriptRequest {
        private final Mode mode;
        private final String script;
        private final ComposeQueryContext ctx;
        private final SemanticQueryServiceV3 semanticService;
        private final String dialect;
        private final CapabilityRegistry capabilityRegistry;
        private final CapabilityPolicy capabilityPolicy;
        private final SuspensionManager suspensionManager;
        private final boolean normalizePlan;

        private ComposeScriptRequest(Builder b) {
            this.mode = b.mode == null ? Mode.EXECUTE : b.mode;
            this.script = b.script;
            this.ctx = b.ctx;
            this.semanticService = b.semanticService;
            this.dialect = b.dialect;
            this.capabilityRegistry = b.capabilityRegistry;
            this.capabilityPolicy = b.capabilityPolicy;
            this.suspensionManager = b.suspensionManager;
            this.normalizePlan = b.normalizePlan;
        }

        public Mode mode() { return mode; }
        public String script() { return script; }
        public ComposeQueryContext ctx() { return ctx; }
        public SemanticQueryServiceV3 semanticService() { return semanticService; }
        public String dialect() { return dialect; }
        public CapabilityRegistry capabilityRegistry() { return capabilityRegistry; }
        public CapabilityPolicy capabilityPolicy() { return capabilityPolicy; }
        public SuspensionManager suspensionManager() { return suspensionManager; }
        public boolean normalizePlan() { return normalizePlan; }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private Mode mode;
            private String script;
            private ComposeQueryContext ctx;
            private SemanticQueryServiceV3 semanticService;
            private String dialect;
            private CapabilityRegistry capabilityRegistry;
            private CapabilityPolicy capabilityPolicy;
            private SuspensionManager suspensionManager;
            private boolean normalizePlan;

            public Builder mode(Mode v) { this.mode = v; return this; }
            public Builder script(String v) { this.script = v; return this; }
            public Builder ctx(ComposeQueryContext v) { this.ctx = v; return this; }
            public Builder semanticService(SemanticQueryServiceV3 v) { this.semanticService = v; return this; }
            public Builder dialect(String v) { this.dialect = v; return this; }
            public Builder capabilityRegistry(CapabilityRegistry v) { this.capabilityRegistry = v; return this; }
            public Builder capabilityPolicy(CapabilityPolicy v) { this.capabilityPolicy = v; return this; }
            public Builder suspensionManager(SuspensionManager v) { this.suspensionManager = v; return this; }
            public Builder normalizePlan(boolean v) { this.normalizePlan = v; return this; }

            public ComposeScriptRequest build() { return new ComposeScriptRequest(this); }
        }
    }

    public static final class ComposeScriptResult {
        private final Mode mode;
        private final boolean valid;
        private final boolean executed;
        private final ScriptRuntime.ScriptResult scriptResult;

        private ComposeScriptResult(Builder b) {
            this.mode = b.mode == null ? Mode.EXECUTE : b.mode;
            this.valid = b.valid;
            this.executed = b.executed;
            this.scriptResult = Objects.requireNonNull(b.scriptResult,
                    "ComposeScriptResult.scriptResult is required");
        }

        public Mode mode() { return mode; }
        public boolean valid() { return valid; }
        public boolean executed() { return executed; }
        public ScriptRuntime.ScriptResult scriptResult() { return scriptResult; }
        public Object value() { return scriptResult.value(); }
        public String sql() { return scriptResult.sql(); }
        public List<Object> params() { return scriptResult.params(); }
        public List<String> warnings() { return scriptResult.warnings(); }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private Mode mode;
            private boolean valid;
            private boolean executed;
            private ScriptRuntime.ScriptResult scriptResult;

            public Builder mode(Mode v) { this.mode = v; return this; }
            public Builder valid(boolean v) { this.valid = v; return this; }
            public Builder executed(boolean v) { this.executed = v; return this; }
            public Builder scriptResult(ScriptRuntime.ScriptResult v) { this.scriptResult = v; return this; }

            public ComposeScriptResult build() { return new ComposeScriptResult(this); }
        }
    }
}
