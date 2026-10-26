package com.foggyframework.dataset.model.semantic;

import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.plugins.query_execution.PhysicalColumnPermissionStep;
import com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionContext;
import com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionPhase;
import com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionStep;
import com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionStepExecutor;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** The native time-window entry must obey the same pure governance as Compose scripts. */
class TimeWindowExecutionGovernanceTest extends EcommerceTestSupport {
    private static final String MODEL = "FactSalesQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticService;

    @Test
    void normalResultHookCannotBeBypassedThroughAnyTimeWindowEntry() {
        AtomicInteger after = new AtomicInteger();
        QueryExecutionStep mask = new QueryExecutionStep() {
            public int order() { return 19001; }

            @SuppressWarnings("unchecked")
            public int afterExecute(QueryExecutionContext context) {
                after.incrementAndGet();
                for (Object item : context.getResult().getItems()) {
                    if (item instanceof Map<?, ?> row && row.containsKey("salesAmount")) {
                        ((Map<String, Object>) row).put("salesAmount", 0);
                    }
                }
                return CONTINUE;
            }
        };
        withExtension(mask, counters -> {
            var ordinary = windowRequest();
            ordinary.setTimeWindow(null);
            ordinary.setColumns(List.of("salesDate$year", "salesDate$month", "salesAmount"));
            var control = semanticService.queryModel(MODEL, ordinary, "execute", SemanticRequestContext.empty());
            assertFalse(control.getItems().isEmpty());
            assertTrue(control.getItems().stream().allMatch(row -> ((Number) row.get("salesAmount")).intValue() == 0));
            assertEquals(1, after.get());
            after.set(0);

            assertAllWindowEntriesDenied(counters, "COMPOSE_EXECUTION_GOVERNANCE_UNPROVEN");
            assertEquals(0, after.get(), "time-window planning must not run normal result hooks");
        });
    }

    @Test
    void originalGeneratedOutputsMustBeIncludedInTheSafetyDecision() {
        AtomicInteger terminal = new AtomicInteger();
        QueryExecutionStep conditional = new QueryExecutionStep() {
            public int order() { return 19002; }

            public boolean composeSqlOnlyIsSafe(QueryExecutionContext context) {
                if (requestsPrior(context)) {
                    terminal.incrementAndGet();
                    assertTrue(context.getSql().contains("salesAmount__prior"), context.getSql());
                    return false;
                }
                return true;
            }
        };
        withExtension(conditional, counters -> assertAllWindowEntriesDenied(
                counters, "COMPOSE_EXECUTION_GOVERNANCE_UNPROVEN"));
        assertEquals(4, terminal.get(), "each public entry must also prove the original request");
    }

    @Test
    void originalRequestPureAuthorizationCannotBeSkippedAfterSafeLeaves() {
        AtomicInteger terminal = new AtomicInteger();
        QueryExecutionStep conditional = new QueryExecutionStep() {
            public int order() { return 19003; }

            public boolean composeSqlOnlyIsSafe(QueryExecutionContext context) { return true; }

            public int prepareComposeSql(QueryExecutionContext context) {
                if (requestsPrior(context)) {
                    terminal.incrementAndGet();
                    assertTrue(context.getSql().contains("salesAmount__prior"), context.getSql());
                    throw new IllegalArgumentException("TIMEWINDOW_TERMINAL_POLICY_DENIED");
                }
                return CONTINUE;
            }
        };
        withExtension(conditional, counters -> assertAllWindowEntriesDenied(
                counters, "TIMEWINDOW_TERMINAL_POLICY_DENIED"));
        assertEquals(4, terminal.get());
    }

    @Test
    void safeTimeWindowUsesOnlyPurePreparationAndPreservesOrdinaryHooks() {
        AtomicInteger terminal = new AtomicInteger();
        AtomicInteger ordinaryBefore = new AtomicInteger();
        QueryExecutionStep safe = new QueryExecutionStep() {
            public int order() { return 19004; }

            public boolean composeSqlOnlyIsSafe(QueryExecutionContext context) { return true; }

            public int prepareComposeSql(QueryExecutionContext context) {
                if (requestsPrior(context)) terminal.incrementAndGet();
                return CONTINUE;
            }

            public int beforeExecute(QueryExecutionPhase phase, QueryExecutionContext context) {
                assertEquals(QueryExecutionPhase.NORMAL_QUERY, phase,
                        "SQL-only planning must never run the ordinary managed-relation hook");
                ordinaryBefore.incrementAndGet();
                return CONTINUE;
            }

            public boolean supports(QueryExecutionPhase phase, QueryExecutionContext context) { return true; }
        };
        withExtension(safe, counters -> {
            semanticService.validateQuery(MODEL, windowRequest(), SemanticRequestContext.empty());
            semanticService.generateSql(MODEL, windowRequest(), SemanticRequestContext.empty());
            var sql = semanticService.generateComposeSql(MODEL, windowRequest(), SemanticRequestContext.empty());
            assertTrue(sql.sql().contains("salesAmount__prior"), sql.sql());
            assertEquals(0, counters.prepared.get());
            assertEquals(0, counters.executed.get());
            assertEquals(3, terminal.get());

            var response = semanticService.queryModel(MODEL, windowRequest(), "execute", SemanticRequestContext.empty());
            assertNotNull(response.getItems());
            assertTrue(counters.executed.get() > 0);
            assertEquals(4, terminal.get());
            assertEquals(0, ordinaryBefore.get());

            var ordinary = windowRequest();
            ordinary.setTimeWindow(null);
            ordinary.setColumns(List.of("salesDate$year", "salesDate$month", "salesAmount"));
            semanticService.queryModel(MODEL, ordinary, "execute", SemanticRequestContext.empty());
            assertEquals(1, ordinaryBefore.get());
        });
    }

    private void assertAllWindowEntriesDenied(Counters counters, String expected) {
        List<Runnable> calls = List.of(
                () -> semanticService.validateQuery(MODEL, windowRequest(), SemanticRequestContext.empty()),
                () -> semanticService.generateSql(MODEL, windowRequest(), SemanticRequestContext.empty()),
                () -> semanticService.generateComposeSql(MODEL, windowRequest(), SemanticRequestContext.empty()),
                () -> semanticService.queryModel(MODEL, windowRequest(), "execute", SemanticRequestContext.empty()));
        for (Runnable call : calls) {
            counters.reset();
            Throwable failure = assertThrows(RuntimeException.class, call::run);
            assertTrue(contains(failure, expected), failure.toString());
            assertEquals(0, counters.prepared.get(), "rejected plan must not prepare business SQL");
            assertEquals(0, counters.executed.get(), "rejected plan must not execute business SQL");
        }
    }

    private void withExtension(QueryExecutionStep step, Consumer<Counters> action) {
        JdbcQueryModelImpl model = (JdbcQueryModelImpl) getQueryModel(MODEL);
        var originalExecutor = model.getQueryExecutionStepExecutor();
        var originalDataSource = model.getDataSource();
        Counters counters = new Counters();
        model.setQueryExecutionStepExecutor(new QueryExecutionStepExecutor(List.of(step, new PhysicalColumnPermissionStep())));
        model.setDataSource(new DelegatingDataSource(originalDataSource) {
            public Connection getConnection() throws SQLException { return counters.observe(super.getConnection()); }
        });
        try {
            action.accept(counters);
        } finally {
            model.setQueryExecutionStepExecutor(originalExecutor);
            model.setDataSource(originalDataSource);
        }
    }

    private static boolean requestsPrior(QueryExecutionContext context) {
        return context.getModelResultContext().getRequest().getParam().getColumns()
                .stream().anyMatch(column -> column.equals("salesAmount__prior"));
    }

    private static SemanticQueryRequest windowRequest() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("salesDate$year", "salesDate$month", "salesAmount", "salesAmount__prior"));
        request.setGroupBy(List.of("salesDate$year", "salesDate$month").stream()
                .map(field -> new SemanticQueryRequest.GroupByItem(field, null)).toList());
        request.setTimeWindow(Map.of("field", "salesDate$id", "grain", "month", "comparison", "mom",
                "value", List.of("2024-02-01", "2024-03-01"), "targetMetrics", List.of("salesAmount")));
        return request;
    }

    private static boolean contains(Throwable failure, String message) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(message)) return true;
        }
        return false;
    }

    private static final class Counters {
        final AtomicInteger prepared = new AtomicInteger();
        final AtomicInteger executed = new AtomicInteger();

        void reset() { prepared.set(0); executed.set(0); }

        Connection observe(Connection connection) {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (p, method, args) -> {
                try {
                    Object result = method.invoke(connection, args);
                    if (!(result instanceof Statement statement)) return result;
                    String sql = args != null && args.length > 0 && args[0] instanceof String text ? text : "";
                    if (business(sql)) prepared.incrementAndGet();
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class}, (q, operation, values) -> {
                        String actual = values != null && values.length > 0 && values[0] instanceof String text ? text : sql;
                        if (operation.getName().startsWith("execute") && business(actual)) executed.incrementAndGet();
                        try { return operation.invoke(statement, values); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
                } catch (InvocationTargetException failure) { throw failure.getCause(); }
            });
        }

        private static boolean business(String sql) { return sql.toLowerCase(Locale.ROOT).contains("fact_sales"); }
    }
}
