package com.foggyframework.dataset.model.engine.compose.runtime;

import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.semantic.domain.*;
import com.foggyframework.dataset.model.semantic.port.*;
import com.foggyframework.dataset.model.semantic.service.DefaultComposeExecutionPort;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ComposeInputContractTest {
    private static final String BASE = "const base=dsl({model:'M',columns:['id','amount']});";

    static Stream<Arguments> invalidInputs() {
        List<Arguments> cases = new ArrayList<>();
        for (String value : List.of("1", "true", "[]", "{}", "null", "''", "'  '")) {
            cases.add(Arguments.of("return {plans:dsl({model:" + value + ",columns:['id']})};",
                    "MODEL_TYPE_INVALID", "model", "dsl.model"));
        }
        cases.add(Arguments.of("return {plans:dsl({columns:['id']})};", "MODEL_TYPE_INVALID", "model", "dsl.model"));
        cases.add(Arguments.of("return {plans:dsl({source:'M',columns:['id']})};", "SOURCE_TYPE_INVALID", "source", "dsl.source"));
        for (String model : List.of("'M'", "base")) {
            cases.add(Arguments.of(BASE + "return {plans:dsl({model:" + model + ",source:base,columns:['id']})};",
                    "COMPOSE_SOURCE_CONFLICT", "model", "dsl.model"));
        }
        for (String args : List.of("", "[]", "1", "null", "{},{}")) {
            cases.add(Arguments.of("return {plans:dsl(" + args + ")};", "COMPOSE_PARAMETER_TYPE_INVALID", "options", "dsl"));
        }
        for (String entry : List.of("query", "model", "source")) {
            String prefix = "query".equals(entry) ? "base.query({" : "dsl({" + entry + ":base,";
            String path = "query".equals(entry) ? "query" : "dsl";
            for (String parameter : List.of("calculatedFields", "having")) {
                for (String value : List.of("{}", "'wrong'", "42", "false")) {
                    cases.add(Arguments.of(BASE + "return {plans:" + prefix + "columns:['id']," + parameter + ":" + value + "})};",
                            "COMPOSE_PARAMETER_TYPE_INVALID", parameter, path + "." + parameter));
                }
                // Capability errors must win over parsing a definition or reporting its selected alias as missing.
                for (String columns : List.of("['id']", "['id','new_alias']")) {
                    cases.add(Arguments.of(BASE + "return {plans:" + prefix + "columns:" + columns + "," + parameter + ":[{name:'new_alias',expression:'hidden * 2'}]})};",
                            "DERIVED_PARAMETER_UNSUPPORTED", parameter, path + "." + parameter));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest @MethodSource("invalidInputs")
    void invalidInputsAreStructuredBeforeAuthorityOrDataCalls(String script, String code, String field, String path) {
        AtomicInteger resolutions = new AtomicInteger();
        PlanningService service = new PlanningService();
        var port = port(service, resolutions);
        for (ComposeOperation mode : ComposeOperation.values()) {
            ComposeExecutionException error = assertThrows(ComposeExecutionException.class,
                    () -> port.execute(request(mode, script)));
            assertEquals(ComposeExecutionException.Kind.SCHEMA, error.kind());
            assertEquals(code, error.code());
            assertEquals(field, error.field());
            assertEquals(path, error.details().get("path"));
            assertEquals("plan-build", error.enginePhase());
            assertEquals(false, error.details().get("safeToAutoRepair"));
            assertFalse(error.getMessage().contains("ClassCastException"));
            assertFalse(error.getMessage().contains("hidden"));
            assertNotNull(error.details().get("suggestedNextAction"));
        }
        assertEquals(0, resolutions.get());
        assertEquals(0, service.generated);
        assertEquals(0, service.executed);
    }

    static Stream<Arguments> noOpInputs() {
        return Stream.of("query", "model", "source").flatMap(entry ->
                Stream.of("null", "[]").map(value -> Arguments.of(entry, value)));
    }

    @ParameterizedTest @MethodSource("noOpInputs")
    void emptyDerivedOptionsRemainNoOps(String entry, String value) {
        PlanningService service = new PlanningService();
        var port = port(service, new AtomicInteger());
        String invocation = "query".equals(entry) ? "base.query({" : "dsl({" + entry + ":base,";
        String script = BASE + "return {plans:" + invocation + "columns:['id','amount * 2 as doubled'],calculatedFields:"
                + value + ",having:" + value + "})};";
        for (ComposeOperation mode : List.of(ComposeOperation.VALIDATE, ComposeOperation.PREVIEW)) {
            var result = port.execute(request(mode, script));
            assertTrue(result.valid());
            assertFalse(result.executed());
        }
        assertTrue(service.generated > 0);
        assertEquals(0, service.executed);
    }

    private static DefaultComposeExecutionPort port(PlanningService service, AtomicInteger resolutions) {
        var beans = new StaticListableBeanFactory();
        beans.addBean("authority", (AuthorityResolver) request -> {
            resolutions.incrementAndGet();
            Map<String, ModelBinding> bindings = new LinkedHashMap<>();
            for (String name : request.modelNames()) bindings.put(name, ModelBinding.builder().build());
            return AuthorityResolution.builder().bindings(bindings).build();
        });
        return new DefaultComposeExecutionPort(service, beans.getBeanProvider(AuthorityResolver.class), "sqlite");
    }

    @ParameterizedTest @ValueSource(strings = {"model:base,source:null", "model:null,source:base", "model:'M',source:null"})
    void nullSourceSelectorsRemainCompatible(String selectors) {
        PlanningService service = new PlanningService();
        var port = port(service, new AtomicInteger());
        assertTrue(port.execute(request(ComposeOperation.VALIDATE,
                BASE + "return {plans:dsl({" + selectors + ",columns:['id','amount']})};")).valid());
        assertEquals(0, service.executed);
    }

    private static ComposeExecutionRequest request(ComposeOperation mode, String script) {
        return new ComposeExecutionRequest(mode, script, "test", "trace", Map.of(),
                new ComposeCaller("test", "test", List.of(), null, null, null), "sqlite");
    }

    private static final class PlanningService implements SemanticQueryServiceV3 {
        int generated, executed;
        public SqlGenerationResult generateSql(String model, SemanticQueryRequest request, SemanticRequestContext context) {
            generated++;
            return new SqlGenerationResult("SELECT 1 AS id, 10 AS amount", List.of(), null);
        }
        public SemanticQueryResponse queryModel(String m, SemanticQueryRequest r, String mode, SemanticRequestContext c) {
            throw new AssertionError("Unexpected model data query");
        }
        public SemanticQueryResponse validateQuery(String m, SemanticQueryRequest r, SemanticRequestContext c) {
            throw new AssertionError("Unexpected model shortcut");
        }
        public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
            executed++;
            throw new AssertionError("Unexpected SQL execution");
        }
    }
}
