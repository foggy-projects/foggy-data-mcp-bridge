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

class ComposeKnownOptionsContractTest {
    private static final String BASE = "const base=dsl({model:'M',columns:['id','amount']});";

    static Stream<Arguments> invalidInputs() {
        List<Arguments> cases = new ArrayList<>();
        for (String entry : List.of("query", "model", "source", "base")) {
            String path=entry.equals("query")?"query":"dsl";
            String invocation=entry.equals("query")?"base.query({":entry.equals("base")?"dsl({model:'M',":"dsl({"+entry+":base,";
            for(String key:List.of("slice","groupBy","columns","orderBy","calculatedFields","having")) {
                for(String value:List.of("{}","'wrong'","1","false"))
                    cases.add(Arguments.of(BASE+"return {plans:"+invocation+key+":"+value+"})};","COMPOSE_PARAMETER_TYPE_INVALID",key,path+"."+key));
            }
            for(String key:List.of("limit","start","offset"))for(String value:List.of("'1'","true","1.9","4294967296","2147483648","-1","{}","[]"))
                cases.add(Arguments.of(BASE+"return {plans:"+invocation+"columns:['id'],"+key+":"+value+"})};","COMPOSE_PARAMETER_TYPE_INVALID",key,path+"."+key));
            for(String value:List.of("'true'","1","{}","[]"))
                cases.add(Arguments.of(BASE+"return {plans:"+invocation+"columns:['id'],distinct:"+value+"})};","COMPOSE_PARAMETER_TYPE_INVALID","distinct",path+".distinct"));
        }
        for(String selector:List.of("model","source"))cases.add(Arguments.of(BASE+"return {plans:base.query({columns:['id'],"+selector+":base})};","COMPOSE_PARAMETER_TYPE_INVALID",selector,"query."+selector));
        for(String args:List.of("","null","1","{},{}"))cases.add(Arguments.of(BASE+"return {plans:base.query("+args+")};","COMPOSE_PARAMETER_TYPE_INVALID","options","query"));
        for(String value:List.of("'true'","1","[]"))cases.add(Arguments.of(BASE+"return {plans:base.union(base,{all:"+value+"})};","COMPOSE_PARAMETER_TYPE_INVALID","all","union.all"));
        cases.add(Arguments.of("return {plans:dsl({model:'M',columns:['id'],calculatedFields:[{name:'x',expression:'id',windowOrderBy:'id'}]})};","COMPOSE_PARAMETER_TYPE_INVALID","windowOrderBy","dsl.calculatedFields[0].windowOrderBy"));
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
