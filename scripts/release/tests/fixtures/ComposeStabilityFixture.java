package io.foggytest.release;

import com.foggyframework.dataset.model.engine.compose.ComposeFeatureFlags;
import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionContext;
import com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionStep;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/** Loaded only by the isolated release smoke; never packaged in the candidate. */
@AutoConfiguration
public class ComposeStabilityFixture {
    static final AtomicInteger prepared = new AtomicInteger();
    static final AtomicInteger executed = new AtomicInteger();
    static final AtomicInteger masked = new AtomicInteger();
    static final Pattern FACT_READ = Pattern.compile("(?is)\\b(?:from|join)\\s+[\"`\\[]?compose_stability_fact(?:[\"`\\]]|\\b)");

    @Bean
    static BeanPostProcessor observeReleaseDataSources() {
        return new BeanPostProcessor() {
            public Object postProcessAfterInitialization(Object bean, String name) {
                return bean instanceof DataSource source && !(bean instanceof ObservedDataSource)
                        ? new ObservedDataSource(source) : bean;
            }
        };
    }

    @Bean QueryExecutionStep releaseExecutionPolicy() { return new ReleaseExecutionPolicy(); }

    public static class ReleaseExecutionPolicy implements QueryExecutionStep {
        public int order() { return 19001; }
        public boolean composeSqlOnlyIsSafe(QueryExecutionContext context) {
            return !Set.of("UnsafeMask", "UnsafeDeny").contains(context.getModelName());
        }
        public int prepareComposeSql(QueryExecutionContext context) {
            if ("PureDeny".equals(context.getModelName()) || "TerminalDeny".equals(context.getModelName()) && generated(context)) {
                throw new IllegalArgumentException("RELEASE_COMPOSE_POLICY_DENIED");
            }
            return CONTINUE;
        }
        public int beforeExecute(QueryExecutionContext context) {
            if (Set.of("UnsafeDeny", "PureDeny").contains(context.getModelName())) {
                throw new IllegalArgumentException("RELEASE_COMPOSE_POLICY_DENIED");
            }
            return CONTINUE;
        }
        @SuppressWarnings("unchecked") public int afterExecute(QueryExecutionContext context) {
            if ("UnsafeMask".equals(context.getModelName()) && context.getResult() != null) {
                masked.incrementAndGet();
                for (Object item : context.getResult().getItems()) {
                    if (item instanceof Map<?, ?> row && row.containsKey("amount")) ((Map<Object, Object>) row).put("amount", 0);
                }
            }
            return CONTINUE;
        }
        private static boolean generated(QueryExecutionContext context) {
            return context.getModelResultContext().getRequest().getParam().getColumns().stream()
                    .anyMatch(column -> column.startsWith("amount__"));
        }
    }

    @RestController
    public static class ProbeController {
        @PostMapping("/__release_compose_probe/reset")
        public Map<String, Object> reset() {
            prepared.set(0); executed.set(0); masked.set(0);
            return counters();
        }
        @GetMapping("/__release_compose_probe/counters")
        public Map<String, Object> counters() {
            return Map.of("prepared", prepared.get(), "executed", executed.get(), "maskCalls", masked.get(),
                    "g10", ComposeFeatureFlags.g10Enabled(), "engineOrigin",
                    QueryPlan.class.getProtectionDomain().getCodeSource().getLocation().toString());
        }
    }

    static final class ObservedDataSource extends DelegatingDataSource {
        ObservedDataSource(DataSource target) { super(target); }
        public Connection getConnection() throws SQLException { return observe(super.getConnection()); }
        public Connection getConnection(String user, String password) throws SQLException { return observe(super.getConnection(user, password)); }
    }

    static boolean businessSql(String sql) {
        String normalized = sql.stripLeading().toLowerCase(Locale.ROOT);
        return (normalized.startsWith("select") || normalized.startsWith("with")) && FACT_READ.matcher(sql).find();
    }

    public static void main(String[] arguments) {
        for (String sql : new String[]{"SELECT * FROM compose_stability_fact", "WITH x AS (SELECT * FROM \"compose_stability_fact\") SELECT * FROM x"}) {
            if (!businessSql(sql)) throw new AssertionError("Missed business SQL: " + sql);
        }
        for (String sql : new String[]{"PRAGMA table_info('compose_stability_fact')", "SHOW COLUMNS FROM compose_stability_fact",
                "SELECT * FROM pragma_table_info('compose_stability_fact')", "SELECT name FROM sqlite_master WHERE name='compose_stability_fact'", "SELECT 1"}) {
            if (businessSql(sql)) throw new AssertionError("Counted metadata SQL as business data: " + sql);
        }
        System.out.println("COUNTER_FILTER_PASSED");
    }

    static Connection observe(Connection connection) {
        return (Connection) Proxy.newProxyInstance(ComposeStabilityFixture.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
            try {
                Object result = method.invoke(connection, arguments);
                if (!(result instanceof Statement statement)) return result;
                String sql = arguments != null && arguments.length > 0 && arguments[0] instanceof String text ? text : "";
                if (method.getName().startsWith("prepare") && businessSql(sql)) prepared.incrementAndGet();
                return Proxy.newProxyInstance(ComposeStabilityFixture.class.getClassLoader(),
                        new Class<?>[]{statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class}, (p, operation, values) -> {
                    String actual = values != null && values.length > 0 && values[0] instanceof String text ? text : sql;
                    if (operation.getName().startsWith("execute") && businessSql(actual)) executed.incrementAndGet();
                    try { return operation.invoke(statement, values); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
            } catch (InvocationTargetException failure) { throw failure.getCause(); }
        });
    }
}
