package com.foggyframework.dataset.db.model.semantic.port;

import com.foggyframework.dataset.db.model.engine.compose.ComposedDataSetResult;
import com.foggyframework.dataset.db.model.engine.compose.DataSetResult;
import com.foggyframework.dataset.db.model.engine.compose.DslQueryFunction;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.compose.compilation.ComposeSqlCompiler;
import com.foggyframework.dataset.db.model.engine.compose.compilation.RelationCompileOptions;
import com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeRuntimeBundle;
import com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeScriptService;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComposeSemanticPlanningPortBoundaryTest {

    @Test
    void legacySemanticServiceProvidesNarrowComposePlanningPort() {
        assertTrue(ComposeSemanticPlanningPort.class.isAssignableFrom(SemanticQueryServiceV3.class));
    }

    @Test
    void composePlannerCoreStoresOnlyTheNarrowPlanningPort() throws Exception {
        Class<?> planner = Class.forName(
                "com.foggyframework.dataset.db.model.engine.compose.compilation.ComposePlanner");
        Class<?> compileState = Arrays.stream(planner.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("CompileState"))
                .findFirst()
                .orElseThrow();
        Field service = compileState.getDeclaredField("planningPort");

        assertEquals(ComposeSemanticPlanningPort.class, service.getType());
    }

    @Test
    void composeCompilerRuntimeAndLegacyBridgeStoreOnlyNarrowPlanningPorts() throws Exception {
        assertFieldType(ComposeSqlCompiler.CompileOptions.class,
                "planningPort", ComposeSemanticPlanningPort.class);
        assertFieldType(RelationCompileOptions.class,
                "planningPort", ComposeSemanticPlanningPort.class);
        assertFieldType(ComposeRuntimeBundle.class,
                "planningPort", ComposeSemanticPlanningPort.class);
        assertFieldType(ComposeScriptService.ComposeScriptRequest.class,
                "planningPort", ComposeSemanticPlanningPort.class);
        assertFieldType(ComposedDataSetResult.class,
                "planningPort", ComposeSemanticPlanningPort.class);
        assertFieldType(DataSetResult.ComposeContext.class,
                "planningPort", ComposeSemanticPlanningPort.class);
        assertFieldType(DslQueryFunction.class,
                "planningPort", ComposeSemanticPlanningPort.class);
        assertFieldType(DslQueryFunction.class,
                "queryExecutionPort", SemanticQueryExecutionPort.class);
    }

    @Test
    void directPlanningPortFieldResolverFailsClosedByDefault() {
        ComposeSemanticPlanningPort port = new ComposeSemanticPlanningPort() {
            @Override
            public ComposeSqlGeneration generateComposeSql(
                    String model, SemanticQueryRequest request, SemanticRequestContext context) {
                return new ComposeSqlGeneration("select 1", List.of(), List.of(), Map.of());
            }

            @Override
            public Optional<String> resolveFieldSqlExpression(
                    String model, String field, String namespace) {
                return Optional.empty();
            }
        };

        assertTrue(port.supportsFieldSqlResolution());
    }

    @Test
    void legacyBridgePreservesCteStagesDiagnosticsAndNullParams() {
        SemanticQueryServiceV3 service = mock(SemanticQueryServiceV3.class, CALLS_REAL_METHODS);
        SemanticQueryRequest request = new SemanticQueryRequest();
        SemanticRequestContext context = SemanticRequestContext.empty();
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("trace", null);
        SqlGenerationResult generated = new SqlGenerationResult(
                "select * from stage1 where deleted_at is ?",
                Arrays.asList((Object) null),
                null,
                List.of(new SqlGenerationResult.CteStage(
                        "stage1", "select ? as id", List.of(7))),
                diagnostics);
        when(service.generateSql("SalesQM", request, context)).thenReturn(generated);

        ComposeSqlGeneration bridged = service.generateComposeSql("SalesQM", request, context);

        assertEquals(generated.getSql(), bridged.sql());
        assertEquals(generated.getParams(), bridged.params());
        assertEquals("stage1", bridged.cteStages().get(0).alias());
        assertTrue(bridged.diagnostics().containsKey("trace"));
        assertFalse(service.supportsFieldSqlResolution());
    }

    @Test
    void legacyAdapterDoesNotDependOnDefaultMethodDispatch() {
        SemanticQueryServiceV3 service = mock(SemanticQueryServiceV3.class);
        when(service.generateSql(anyString(), any(), any()))
                .thenReturn(new SqlGenerationResult("select 1", List.of(), null));

        ComposeSqlGeneration generated = SemanticQueryServiceV3.composePlanningPort(service)
                .generateComposeSql(
                        "SalesQM", new SemanticQueryRequest(), SemanticRequestContext.empty());

        assertEquals("select 1", generated.sql());
    }

    private static void assertFieldType(
            Class<?> owner, String fieldName, Class<?> expectedType) throws Exception {
        assertEquals(expectedType, owner.getDeclaredField(fieldName).getType(),
                owner.getName() + "#" + fieldName);
    }
}
