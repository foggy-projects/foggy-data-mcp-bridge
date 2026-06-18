package com.foggyframework.dataset.db.model.cache.config;

import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheInvalidationBroadcaster;
import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheInvalidationListenerLifecycle;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationEvent;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class PivotOuterCacheRedisInvalidationCrossProcessWorker {

    private static WorkerState state;

    private PivotOuterCacheRedisInvalidationCrossProcessWorker() {
    }

    public static void main(String[] args) throws Exception {
        WorkerArguments arguments = WorkerArguments.parse(args);
        state = new WorkerState(arguments);
        if ("listener".equals(arguments.role())) {
            runListener(arguments);
            return;
        }
        if ("publisher".equals(arguments.role())) {
            runPublisher(arguments);
            return;
        }
        throw new IllegalArgumentException("Unsupported role: " + arguments.role());
    }

    private static void runListener(WorkerArguments arguments) throws Exception {
        try (ConfigurableApplicationContext context = startContext(arguments, true)) {
            waitForListenerRunning(context, Duration.ofSeconds(10L));
            writeProperties(arguments.readyFile(), Map.of(
                    "role", arguments.role(),
                    "nodeId", arguments.nodeId(),
                    "channel", arguments.channel(),
                    "listenerRunning", "true"));
            if (!state.evicted().await(15L, TimeUnit.SECONDS)) {
                writeProperties(arguments.errorFile(), Map.of(
                        "role", arguments.role(),
                        "error", "Timed out waiting for Redis invalidation event"));
                System.exit(2);
            }
        }
    }

    private static void runPublisher(WorkerArguments arguments) throws Exception {
        try (ConfigurableApplicationContext context = startContext(arguments, false)) {
            RedisPivotOuterCacheInvalidationBroadcaster broadcaster =
                    context.getBean(RedisPivotOuterCacheInvalidationBroadcaster.class);
            PivotOuterCacheInvalidationResult result = broadcaster.evict(
                    PivotOuterCacheInvalidationEvent.of(arguments.namespace(), arguments.model())
                            .withMetadata(arguments.eventId(), arguments.nodeId(), System.currentTimeMillis()));
            writeProperties(arguments.publishFile(), Map.of(
                    "role", arguments.role(),
                    "nodeId", arguments.nodeId(),
                    "channel", broadcaster.channel(),
                    "localNodeId", broadcaster.localNodeId(),
                    "success", Boolean.toString(result.success()),
                    "removed", Integer.toString(result.removed()),
                    "attemptedNodes", Integer.toString(result.attemptedNodes()),
                    "succeededNodes", Integer.toString(result.succeededNodes()),
                    "failedNodes", Integer.toString(result.failedNodes()),
                    "errors", String.join("|", result.errors())));
            if (!result.success()) {
                System.exit(3);
            }
        }
    }

    private static ConfigurableApplicationContext startContext(WorkerArguments arguments,
                                                               boolean listenerEnabled) {
        return new SpringApplicationBuilder(
                WorkerConfiguration.class,
                RedisAutoConfiguration.class,
                PivotOuterCacheRedisInvalidationAutoConfiguration.class)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .web(WebApplicationType.NONE)
                .properties(Map.of(
                        "spring.data.redis.host", arguments.host(),
                        "spring.data.redis.port", Integer.toString(arguments.port()),
                        "spring.data.redis.timeout", "2s",
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-enabled", "true",
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-listener-enabled",
                        Boolean.toString(listenerEnabled),
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-listener-auto-startup", "true",
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-listener-recovery-interval-millis", "100",
                        "foggy.dataset.pivot.outer-cache.redis.invalidation-channel", arguments.channel(),
                        "foggy.dataset.pivot.outer-cache.redis.node-id", arguments.nodeId()))
                .run();
    }

    private static void waitForListenerRunning(ConfigurableApplicationContext context,
                                               Duration timeout) throws InterruptedException {
        RedisPivotOuterCacheInvalidationListenerLifecycle lifecycle =
                context.getBean(RedisPivotOuterCacheInvalidationListenerLifecycle.class);
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (lifecycle.isRunning()) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new IllegalStateException("Redis invalidation listener did not start within " + timeout);
    }

    private static void writeProperties(Path path, Map<String, String> values) throws IOException {
        Files.createDirectories(path.getParent());
        Properties properties = new Properties();
        values.forEach(properties::setProperty);
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, "Pivot outer-cache Redis invalidation cross-process worker");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class WorkerConfiguration {

        @Bean
        SemanticQueryServiceV3 semanticQueryServiceV3() {
            return new RecordingSemanticQueryService(state);
        }
    }

    static final class RecordingSemanticQueryService implements SemanticQueryServiceV3 {

        private final WorkerState state;

        private RecordingSemanticQueryService(WorkerState state) {
            this.state = state;
        }

        @Override
        public SemanticQueryResponse queryModel(String model,
                                                SemanticQueryRequest request,
                                                String mode,
                                                SemanticRequestContext context) {
            throw new UnsupportedOperationException("queryModel is not used by this cross-process worker");
        }

        @Override
        public SemanticQueryResponse validateQuery(String model,
                                                   SemanticQueryRequest request,
                                                   SemanticRequestContext context) {
            throw new UnsupportedOperationException("validateQuery is not used by this cross-process worker");
        }

        @Override
        public SqlGenerationResult generateSql(String model,
                                               SemanticQueryRequest request,
                                               SemanticRequestContext context) {
            throw new UnsupportedOperationException("generateSql is not used by this cross-process worker");
        }

        @Override
        public List<Map<String, Object>> executeSql(String sql,
                                                    List<Object> params,
                                                    String routeModel) {
            throw new UnsupportedOperationException("executeSql is not used by this cross-process worker");
        }

        @Override
        public int evictPivotOuterCache(String namespace, String model) {
            WorkerArguments arguments = state.arguments();
            Map<String, String> values = new LinkedHashMap<>();
            values.put("role", arguments.role());
            values.put("nodeId", arguments.nodeId());
            values.put("namespace", namespace == null ? "<all>" : namespace);
            values.put("model", model == null ? "<all>" : model);
            values.put("thread", Thread.currentThread().getName());
            try {
                writeProperties(arguments.evictFile(), values);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to write evict result", e);
            }
            state.evicted().countDown();
            return "listener".equals(arguments.role()) ? 3 : 2;
        }
    }

    record WorkerState(WorkerArguments arguments, CountDownLatch evicted) {

        private WorkerState(WorkerArguments arguments) {
            this(arguments, new CountDownLatch(1));
        }
    }

    record WorkerArguments(String role,
                           String host,
                           int port,
                           String channel,
                           String nodeId,
                           String namespace,
                           String model,
                           String eventId,
                           Path resultDir) {

        static WorkerArguments parse(String[] args) {
            if (args.length != 9) {
                throw new IllegalArgumentException("Expected 9 arguments: role host port channel nodeId namespace "
                        + "model eventId resultDir");
            }
            return new WorkerArguments(
                    args[0],
                    args[1],
                    Integer.parseInt(args[2]),
                    args[3],
                    args[4],
                    args[5],
                    args[6],
                    args[7],
                    Path.of(args[8]));
        }

        Path readyFile() {
            return resultDir.resolve(role + "-ready.properties");
        }

        Path evictFile() {
            return resultDir.resolve(role + "-evict.properties");
        }

        Path publishFile() {
            return resultDir.resolve(role + "-publish.properties");
        }

        Path errorFile() {
            return resultDir.resolve(role + "-error.properties");
        }
    }
}
