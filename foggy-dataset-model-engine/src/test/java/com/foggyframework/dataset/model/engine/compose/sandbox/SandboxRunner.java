package com.foggyframework.dataset.model.engine.compose.sandbox;

import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.runtime.ScriptRuntime;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runner for M9 Sandbox Tests.
 *
 * <p>The mocked {@link SemanticQueryServiceV3} returns a stub
 * {@link SqlGenerationResult} so happy-path sandbox tests (e.g. A-10, B-05,
 * B-06, C-06, C-07) reach the PerBaseCompiler without tripping a default
 * Mockito {@code null} return — the focus of M9 tests is sandbox-layer
 * behaviour, not actual SQL generation.</p>
 */
public class SandboxRunner {
    private final ComposeQueryContext ctx;
    private final SemanticQueryServiceV3 semanticService;

    private SandboxRunner(ComposeQueryContext ctx) {
        this.ctx = ctx;
        this.semanticService = stubbedSemanticService();
    }

    /** Build a {@link SemanticQueryServiceV3} mock that returns a stub
     *  {@link SqlGenerationResult} from {@code generateSql} so callers reach
     *  PerBaseCompiler without tripping a default Mockito {@code null} return.
     *  Exposed for sandbox tests that build the mock inline (e.g. C-06, C-07). */
    public static SemanticQueryServiceV3 stubbedSemanticService() {
        SemanticQueryServiceV3 svc = mock(SemanticQueryServiceV3.class);
        when(svc.generateSql(anyString(), any(), any()))
                .thenReturn(new SqlGenerationResult("SELECT 1", List.of(), null));
        return svc;
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
        if (result.value() instanceof com.foggyframework.dataset.model.engine.compose.plan.QueryPlan) {
            com.foggyframework.dataset.model.engine.compose.plan.QueryPlan plan =
                    (com.foggyframework.dataset.model.engine.compose.plan.QueryPlan) result.value();
            return plan.toSql(ctx, "mysql").getSql();
        }
        return String.valueOf(result.value());
    }
}
