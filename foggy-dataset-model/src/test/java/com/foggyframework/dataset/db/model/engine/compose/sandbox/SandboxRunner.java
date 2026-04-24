package com.foggyframework.dataset.db.model.engine.compose.sandbox;

import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.runtime.ScriptRuntime;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;

import static org.mockito.Mockito.mock;

/**
 * Runner for M9 Sandbox Tests.
 */
public class SandboxRunner {
    private final ComposeQueryContext ctx;
    private final SemanticQueryServiceV3 semanticService;

    private SandboxRunner(ComposeQueryContext ctx) {
        this.ctx = ctx;
        this.semanticService = mock(SemanticQueryServiceV3.class);
    }

    public static SandboxRunner forScript(ComposeQueryContext ctx) {
        return new SandboxRunner(ctx);
    }

    public void run(String script) {
        ScriptRuntime.runScript(script, ctx, semanticService, "mysql");
    }

    public String runToSql(String script) {
        // Evaluate the script which returns a QueryPlan, then call toSql() on it?
        // Wait, the test uses newRunner().runToSql("from(...)") ? No, the script in B03 is:
        // "from({model:'X', slice:[{field:'name', op:'=', value:\"a' UNION SELECT 1,2,3--\"}]})"
        // Wait, runToSql means evaluate the script and return the generated SQL.
        // It should probably just call runScript and then if it returns a QueryPlan, call toSql().
        ScriptRuntime.ScriptResult result = ScriptRuntime.runScript(script, ctx, semanticService, "mysql");
        if (result.value() instanceof com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan) {
            com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan plan =
                    (com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan) result.value();
            return plan.toSql(ctx, "mysql").getSql();
        }
        return String.valueOf(result.value());
    }
}
