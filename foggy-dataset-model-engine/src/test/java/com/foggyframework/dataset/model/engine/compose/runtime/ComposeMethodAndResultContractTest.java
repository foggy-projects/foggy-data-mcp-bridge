package com.foggyframework.dataset.model.engine.compose.runtime;

import com.foggyframework.dataset.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.model.engine.compose.plan.PlanColumnRef;
import com.foggyframework.dataset.model.engine.compose.plan.WindowColumn;
import com.foggyframework.dataset.model.engine.compose.plan.WindowColumnBuilder;
import com.foggyframework.dataset.model.engine.compose.schema.ComposeParameterException;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.semantic.domain.*;
import com.foggyframework.dataset.model.semantic.port.*;
import com.foggyframework.dataset.model.semantic.service.DefaultComposeExecutionPort;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ComposeMethodAndResultContractTest {
    private static final String BASE = "const p=dsl({model:'M',columns:['id','amount']});";

    static Stream<Arguments> invalidMethods() {
        List<Arguments> values = new ArrayList<>();
        for (String function : List.of("lag", "lead")) {
            for (String value : List.of("4294967296", "2147483648", "1.9", "-1", "'2'", "true", "null", "{}", "[]")) {
                values.add(Arguments.of("p.amount." + function + "(" + value + ")", "COMPOSE_PARAMETER_TYPE_INVALID", function + ".offset"));
            }
            values.add(Arguments.of("p.amount." + function + "(1,2)", "COMPOSE_ARGUMENT_COUNT_INVALID", function));
        }
        for (String method : List.of("execute", "toSql")) {
            values.add(Arguments.of("p." + method + "({limit:0})", "COMPOSE_ARGUMENT_COUNT_INVALID", method));
            values.add(Arguments.of("p." + method + "(p,p)", "COMPOSE_ARGUMENT_COUNT_INVALID", method));
        }
        for (String method : List.of("limit", "offset", "where"))
            values.add(Arguments.of("p." + method + "(0,1)", "COMPOSE_ARGUMENT_COUNT_INVALID", method));
        for (String method : List.of("leftJoin", "innerJoin", "rightJoin", "fullJoin")) {
            for (String args : List.of("", "p,p"))
                values.add(Arguments.of("p." + method + "(" + args + ")", "COMPOSE_ARGUMENT_COUNT_INVALID", method));
            values.add(Arguments.of("p." + method + "('M')", "COMPOSE_PARAMETER_TYPE_INVALID", method));
        }
        for (String method : List.of("sum", "count", "avg", "min", "max"))
            values.add(Arguments.of("p.amount." + method + "(p.id)", "COMPOSE_ARGUMENT_COUNT_INVALID", method));
        for (String expr : List.of("p.amount", "p.amount.sum()", "p.amount.lag().over()"))
            values.add(Arguments.of(expr + ".as('x','caption','extra')", "COMPOSE_ARGUMENT_COUNT_INVALID", "as"));
        for (String expr : List.of("p.amount.lag()", "p.amount.sum()"))
            values.add(Arguments.of(expr + ".over({}, {})", "COMPOSE_ARGUMENT_COUNT_INVALID", "over"));
        for (String expr : List.of("Query.from('M','other')", "Query.col('amount','other')"))
            values.add(Arguments.of(expr, "COMPOSE_ARGUMENT_COUNT_INVALID", expr.startsWith("Query.from") ? "Query.from" : "Query.col"));
        values.add(Arguments.of("subquery(p,'id','extra')", "COMPOSE_ARGUMENT_COUNT_INVALID", "subquery"));
        values.add(Arguments.of("subquery('M')", "COMPOSE_PARAMETER_TYPE_INVALID", "subquery.plan"));
        return values.stream();
    }

    @ParameterizedTest @MethodSource("invalidMethods")
    void invalidMethodsRejectBeforePlanningAndData(String expression, String code, String path) {
        Fixture fixture = new Fixture();
        for (ComposeOperation mode : ComposeOperation.values()) {
            ComposeExecutionException error = assertThrows(ComposeExecutionException.class,
                    () -> fixture.run(mode, BASE + "const result=" + expression + ";return {ok:true};"));
            assertEquals(code, error.code(), expression);
            assertEquals(path, error.details().get("path"), expression);
            assertEquals(false, error.details().get("safeToAutoRepair"));
            assertNotNull(error.details().get("suggestedNextAction"));
        }
        assertEquals(0, fixture.authority.get());
        assertEquals(0, fixture.service.generated);
        assertEquals(0, fixture.service.executed);
    }

    static Stream<Arguments> timeWindowInputs() {
        return Stream.of("dsl", "query", "source").flatMap(entry -> Stream.of(
                Arguments.of(entry, "{field:'id',comparison:'mtd'}", "COMPOSE_PARAMETER_UNSUPPORTED"),
                Arguments.of(entry, "'mtd'", "COMPOSE_PARAMETER_TYPE_INVALID"),
                Arguments.of(entry, "[]", "COMPOSE_PARAMETER_TYPE_INVALID")));
    }

    @ParameterizedTest @MethodSource("timeWindowInputs")
    void knownUnsupportedWindowIsNotIgnored(String entry, String value, String code) {
        Fixture fixture = new Fixture();
        String invocation = entry.equals("query") ? "p.query({" : "dsl({" + (entry.equals("source") ? "source:p," : "model:'M',");
        for (ComposeOperation mode : ComposeOperation.values()) {
            ComposeExecutionException error = assertThrows(ComposeExecutionException.class,
                    () -> fixture.run(mode, BASE + "return {plans:" + invocation + "columns:['id'],timeWindow:" + value + "})};"));
            assertEquals(code, error.code());
            assertEquals((entry.equals("query") ? "query" : "dsl") + ".timeWindow", error.details().get("path"));
        }
        assertEquals(0, fixture.authority.get());
        assertEquals(0, fixture.service.generated);
        assertEquals(0, fixture.service.executed);
    }

    static Stream<Arguments> escapedPlans() {
        return Stream.of(
                Arguments.of("return [p];", "result[0]"),
                Arguments.of("return {value:{plan:p}};", "result.value.plan"),
                Arguments.of("return {plans:{nested:{bad:p}}};", "result.plans.nested.bad"),
                // ArrayExp flattens nested array literals; retain a map boundary to
                // exercise a genuinely unsupported nested result value instead.
                Arguments.of("return {plans:[{nested:[p]}]};", "result.plans[0].nested[0]"),
                Arguments.of("return {plans:{one:p,invalid:{plan:p}}};", "result.plans.invalid.plan"),
                Arguments.of("return {plans:p,extra:{bad:p}};", "result.extra.bad"),
                Arguments.of("const shared={one:p};return {plans:shared,extra:shared};", "result.extra.one"));
    }

    static Stream<Arguments> escapedDescriptors() {
        return Stream.of(
                Arguments.of("return {value:p.id};", "result.value"),
                Arguments.of("return {plans:p.id};", "result.plans"),
                Arguments.of("return p.innerJoin(p);", "result"),
                Arguments.of("return {value:p.amount.sum()};", "result.value"),
                Arguments.of("return {plans:[p.amount.lag()]};", "result.plans[0]"),
                Arguments.of("return {value:p.amount.lag().over()};", "result.value"),
                Arguments.of("return {value:p.amount.as('value')};", "result.value"),
                Arguments.of("return {value:subquery(p,'id')};", "result.value"),
                Arguments.of("return Query;", "result"));
    }

    @ParameterizedTest @MethodSource("escapedDescriptors")
    void unevaluatedDescriptorsAreRejectedInsteadOfReachingSerialization(String returned, String path) {
        Fixture fixture = new Fixture();
        for (ComposeOperation mode : ComposeOperation.values()) {
            ComposeExecutionException error = assertThrows(ComposeExecutionException.class,
                    () -> fixture.run(mode, BASE + returned));
            assertEquals("COMPOSE_RESULT_SHAPE_UNSUPPORTED", error.code());
            assertEquals(path, error.details().get("path"));
        }
        assertEquals(0, fixture.authority.get());
        assertEquals(0, fixture.service.generated);
        assertEquals(0, fixture.service.executed);
    }

    @ParameterizedTest @MethodSource("escapedPlans")
    void unsupportedResultPlansFailBeforeAutomaticExecution(String returned, String path) {
        Fixture fixture = new Fixture();
        for (ComposeOperation mode : ComposeOperation.values()) {
            ComposeExecutionException error = assertThrows(ComposeExecutionException.class,
                    () -> fixture.run(mode, BASE + returned));
            assertEquals("COMPOSE_RESULT_SHAPE_UNSUPPORTED", error.code());
            assertEquals(path, error.details().get("path"));
            assertEquals("result", error.field());
        }
        assertEquals(0, fixture.authority.get());
        assertEquals(0, fixture.service.generated);
        assertEquals(0, fixture.service.executed);
    }

    @Test
    void supportedShapesLiteralsAndEmptyWindowRemainCompatibleAndReadonly() {
        for (String returned : List.of("return p;", "return {plans:p};", "return {plans:{one:p}};", "return {plans:[p]};",
                "return {plans:{one:p,literal:{items:[1,2]}}};")) {
            Fixture fixture = new Fixture();
            for (ComposeOperation mode : List.of(ComposeOperation.VALIDATE, ComposeOperation.PREVIEW)) {
                assertTrue(fixture.run(mode, BASE + returned).valid());
            }
            assertEquals(0, fixture.service.executed);
            assertTrue(fixture.service.generated > 0);
            assertTrue(fixture.run(ComposeOperation.EXECUTE, BASE + returned).executed());
            assertEquals(1, fixture.service.executed);
        }
        for (String empty : List.of("null", "{}")) {
            Fixture fixture = new Fixture();
            assertTrue(fixture.run(ComposeOperation.VALIDATE,
                    "return {plans:dsl({model:'M',columns:['id'],timeWindow:" + empty + ",vendorExtension:{enabled:true}})};").valid());
            assertEquals(0, fixture.service.executed);
        }
        Fixture literals = new Fixture();
        assertTrue(literals.run(ComposeOperation.EXECUTE, "return {value:[{a:1},{b:[2,3]}],plans:{literal:{x:1}}};").valid());
        assertEquals(0, literals.service.executed);
        Fixture variadic = new Fixture();
        assertTrue(variadic.run(ComposeOperation.PREVIEW,
                BASE + "return {plans:p.select('id','amount').orderBy('id','-amount')};").valid());
        assertTrue(variadic.run(ComposeOperation.PREVIEW,
                BASE + "return {plans:p.where().limit().offset()};").valid());
        assertEquals(0, variadic.service.executed);
    }

    @Test
    void legalWindowOffsetsRetainMagnitudeAndNoArgumentDefault() {
        PlanColumnRef ref = new PlanColumnRef(BaseModelPlan.builder().model("M").build(), "amount");
        for (String method : List.of("lag", "lead")) {
            for (Number number : List.of(0, 1, 2, Integer.MAX_VALUE, new BigInteger("2147483647"))) {
                WindowColumnBuilder builder = (WindowColumnBuilder) ref.invoke(null, method, new Object[]{number});
                assertEquals(List.of(number.intValue()), builder.over(null).args());
            }
            WindowColumn defaultColumn = ((WindowColumnBuilder) ref.invoke(null, method, null)).over(null);
            assertEquals(List.of(1), defaultColumn.args());
            for (Number invalid : List.of(Double.NaN, Double.POSITIVE_INFINITY, new BigInteger("4294967296"))) {
                assertThrows(ComposeParameterException.class, () -> ref.invoke(null, method, new Object[]{invalid}));
            }
        }
        Fixture fixture = new Fixture();
        var response = fixture.run(ComposeOperation.PREVIEW,
                BASE + "return {plans:p.select(p.amount.lag(2).over({orderBy:['id']}).as('prior'))};");
        var value = (Map<?, ?>) response.value();
        assertTrue(((ComposedSql) value.get("plans")).getSql().contains(", 2) OVER"));
        assertEquals(0, fixture.service.executed);
    }

    private static final class Fixture {
        final AtomicInteger authority = new AtomicInteger();
        final PlanningService service = new PlanningService();
        final DefaultComposeExecutionPort port;
        Fixture() {
            var beans = new StaticListableBeanFactory();
            beans.addBean("authority", (AuthorityResolver) request -> {
                authority.incrementAndGet();
                Map<String, ModelBinding> bindings = new LinkedHashMap<>();
                for (String model : request.modelNames()) bindings.put(model, ModelBinding.builder().build());
                return AuthorityResolution.builder().bindings(bindings).build();
            });
            port = new DefaultComposeExecutionPort(service, beans.getBeanProvider(AuthorityResolver.class), "sqlite");
        }
        ComposeExecutionResult run(ComposeOperation mode, String script) {
            return port.execute(new ComposeExecutionRequest(mode, script, "test", "trace", Map.of(),
                    new ComposeCaller("test", "test", List.of(), null, null, null), "sqlite"));
        }
    }

    private static final class PlanningService implements SemanticQueryServiceV3 {
        int generated, executed;
        public SqlGenerationResult generateSql(String model, SemanticQueryRequest request, SemanticRequestContext context) {
            generated++;
            return new SqlGenerationResult("SELECT 1 AS id, 10 AS amount", List.of(), null);
        }
        public SemanticQueryResponse queryModel(String m, SemanticQueryRequest r, String mode, SemanticRequestContext c) {
            throw new AssertionError("Unexpected model query");
        }
        public SemanticQueryResponse validateQuery(String m, SemanticQueryRequest r, SemanticRequestContext c) {
            throw new AssertionError("Unexpected model validation shortcut");
        }
        public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
            executed++;
            return List.of(Map.of("id", 1, "amount", 10));
        }
    }
}
