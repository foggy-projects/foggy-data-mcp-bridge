package com.foggyframework.dataset.model.cache.provider;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.cache.config.QueryCacheAutoConfiguration;
import com.foggyframework.dataset.model.cache.config.QueryCacheProperties;
import com.foggyframework.dataset.model.cache.fingerprint.QueryFingerprintBuilder;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogCandidate;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Real Redis restart probe. The owning JUnit JVM starts two child JVMs in
 * sequence so process-local catalog boot identity cannot be confused with two
 * objects living in one process.
 */
public class RedisCrossJvmCacheIT {

    private static final String PROBE_PREFIX = "V933_PROBE ";
    private static final String NAMESPACE = "tenant-cache-cross-jvm";
    private static final String CANONICAL_MODEL = "OrderModel";
    private static final String MODEL_ALIAS = "O";
    private static final String AUTHORIZATION = "Bearer cross-jvm-contract-token";
    private static final String SQL = "SELECT id FROM orders WHERE id = ?";
    private static final List<Integer> PARAMS = List.of(42);

    @Test
    void processRestartUsesAColdCatalogEpochAgainstSharedRedis() throws Exception {
        String host = requiredProperty("v933.redis.host");
        String port = requiredProperty("v933.redis.port");
        String keyPrefix = requiredProperty("v933.redis.key-prefix");

        ProbeRun writer = launchProbe("write", host, port, keyPrefix);
        ProbeRun reader = launchProbe(
                "restart",
                host,
                port,
                keyPrefix,
                writer.value("l1_key"),
                writer.value("l2_key"));

        assertNotEquals(writer.pid(), reader.pid(), "the probes must be separate OS processes");
        assertEquals(writer.pid(), Long.parseLong(writer.value("pid")));
        assertEquals(reader.pid(), Long.parseLong(reader.value("pid")));

        assertEquals("true", writer.value("l1_hit_after_write"));
        assertEquals("true", writer.value("l2_hit_after_write"));
        assertEquals("true", reader.value("previous_l1_hit"));
        assertEquals("true", reader.value("previous_l2_hit"));
        assertEquals("true", reader.value("current_l1_miss"));
        assertEquals("true", reader.value("current_l2_miss"));
        assertEquals("true", reader.value("current_l1_hit_after_write"));
        assertEquals("true", reader.value("current_l2_hit_after_write"));

        assertNotEquals(
                writer.value("catalog_generation"),
                reader.value("catalog_generation"));
        assertNotEquals(
                writer.value("source_revision"),
                reader.value("source_revision"));
        assertNotEquals(writer.value("l1_key"), reader.value("l1_key"));
        assertNotEquals(writer.value("l2_key"), reader.value("l2_key"));
        assertEquals(writer.value("binding_key"), reader.value("binding_key"));
        assertEquals(writer.value("binding_backend"), reader.value("binding_backend"));
        assertEquals(writer.value("binding_generation"),
                reader.value("binding_generation"));
        assertEquals("4", reader.value("key_count"));

        assertHashedCacheKey(writer.value("l1_key"), keyPrefix + "l1:", MODEL_ALIAS);
        assertHashedCacheKey(writer.value("l2_key"), keyPrefix + "l2:", CANONICAL_MODEL);
        assertHashedCacheKey(reader.value("l1_key"), keyPrefix + "l1:", MODEL_ALIAS);
        assertHashedCacheKey(reader.value("l2_key"), keyPrefix + "l2:", CANONICAL_MODEL);
    }

    /** Child-process entry point used only by the owning test above. */
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException(
                    "probe requires mode, host, port and keyPrefix");
        }
        String mode = args[0];
        String host = args[1];
        int port = Integer.parseInt(args[2]);
        String keyPrefix = args[3];
        if ("write".equals(mode)) {
            runWriter(host, port, keyPrefix);
            return;
        }
        if ("restart".equals(mode) && args.length == 6) {
            runRestartReader(host, port, keyPrefix, args[4], args[5]);
            return;
        }
        throw new IllegalArgumentException("unsupported probe mode: " + mode);
    }

    private static void runWriter(String host, int port, String keyPrefix) {
        CatalogResolution<QueryModel> resolution = freshCatalogResolution();
        CatalogIdentity identity = resolution.catalogIdentity();
        ModelResultContext context = trackedContext(resolution);
        QueryCacheKeyBuilder keyBuilder = new QueryCacheKeyBuilder(
                new QueryFingerprintBuilder(), cacheProperties(keyPrefix));
        String l1Key = requireKey(keyBuilder.buildL1CacheKey(context, AUTHORIZATION));
        String l2Key = requireKey(keyBuilder.buildL2CacheKey(
                CANONICAL_MODEL, SQL, PARAMS, context));

        withProductionRedis(host, port, keyPrefix, (provider, redisTemplate) -> {
            PagingResultImpl<?> result = result("writer");
            provider.writeL1Cache(context, AUTHORIZATION, result);
            provider.writeL2Cache(CANONICAL_MODEL, SQL, PARAMS, result, context);
            PagingResultImpl<?> l1Cached = provider.checkL1Cache(context, AUTHORIZATION);
            PagingResultImpl<?> l2Cached = provider.checkL2Cache(
                    CANONICAL_MODEL, SQL, PARAMS, context);
            assertCachedResult(l1Cached, "writer");
            assertCachedResult(l2Cached, "writer");

            emit("mode", "write");
            emit("pid", Long.toString(ProcessHandle.current().pid()));
            emit("catalog_generation", identity.generation().value());
            emit("source_revision", identity.sourceRevision().value());
            emitBinding(context);
            emit("l1_key", l1Key);
            emit("l2_key", l2Key);
            emit("l1_hit_after_write", Boolean.toString(l1Cached != null));
            emit("l2_hit_after_write", Boolean.toString(l2Cached != null));
        });
    }

    private static void runRestartReader(
            String host,
            int port,
            String keyPrefix,
            String previousL1Key,
            String previousL2Key
    ) {
        assertHashedCacheKey(previousL1Key, keyPrefix + "l1:", MODEL_ALIAS);
        assertHashedCacheKey(previousL2Key, keyPrefix + "l2:", CANONICAL_MODEL);
        CatalogResolution<QueryModel> currentResolution = freshCatalogResolution();
        CatalogIdentity currentIdentity = currentResolution.catalogIdentity();
        ModelResultContext currentContext = trackedContext(currentResolution);
        QueryCacheKeyBuilder keyBuilder = new QueryCacheKeyBuilder(
                new QueryFingerprintBuilder(), cacheProperties(keyPrefix));
        String l1Key = requireKey(keyBuilder.buildL1CacheKey(currentContext, AUTHORIZATION));
        String l2Key = requireKey(keyBuilder.buildL2CacheKey(
                CANONICAL_MODEL, SQL, PARAMS, currentContext));

        withProductionRedis(host, port, keyPrefix, (provider, redisTemplate) -> {
            Object previousL1 = redisTemplate.opsForValue().get(previousL1Key);
            Object previousL2 = redisTemplate.opsForValue().get(previousL2Key);
            assertTrue(previousL1 instanceof PagingResultImpl<?>);
            assertTrue(previousL2 instanceof PagingResultImpl<?>);
            assertCachedResult((PagingResultImpl<?>) previousL1, "writer");
            assertCachedResult((PagingResultImpl<?>) previousL2, "writer");
            boolean previousL1Hit = previousL1 != null;
            boolean previousL2Hit = previousL2 != null;
            boolean currentL1Miss = provider.checkL1Cache(
                    currentContext, AUTHORIZATION) == null;
            boolean currentL2Miss = provider.checkL2Cache(
                    CANONICAL_MODEL, SQL, PARAMS, currentContext) == null;

            PagingResultImpl<?> result = result("restart");
            provider.writeL1Cache(currentContext, AUTHORIZATION, result);
            provider.writeL2Cache(CANONICAL_MODEL, SQL, PARAMS, result, currentContext);
            PagingResultImpl<?> currentL1 = provider.checkL1Cache(
                    currentContext, AUTHORIZATION);
            PagingResultImpl<?> currentL2 = provider.checkL2Cache(
                    CANONICAL_MODEL, SQL, PARAMS, currentContext);
            assertCachedResult(currentL1, "restart");
            assertCachedResult(currentL2, "restart");
            boolean currentL1Hit = currentL1 != null;
            boolean currentL2Hit = currentL2 != null;
            Set<String> keys = redisTemplate.keys(keyPrefix + "*");

            emit("mode", "restart");
            emit("pid", Long.toString(ProcessHandle.current().pid()));
            emit("catalog_generation", currentIdentity.generation().value());
            emit("source_revision", currentIdentity.sourceRevision().value());
            emitBinding(currentContext);
            emit("l1_key", l1Key);
            emit("l2_key", l2Key);
            emit("previous_l1_hit", Boolean.toString(previousL1Hit));
            emit("previous_l2_hit", Boolean.toString(previousL2Hit));
            emit("current_l1_miss", Boolean.toString(currentL1Miss));
            emit("current_l2_miss", Boolean.toString(currentL2Miss));
            emit("current_l1_hit_after_write", Boolean.toString(currentL1Hit));
            emit("current_l2_hit_after_write", Boolean.toString(currentL2Hit));
            emit("key_count", Integer.toString(keys == null ? 0 : keys.size()));
        });
    }

    private static CatalogResolution<QueryModel> freshCatalogResolution() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate(NAMESPACE)) {
            CatalogCandidate candidate = scope.candidate();
            candidate.resetForNamespaceRefresh(List.of(CANONICAL_MODEL));
            String alias = candidate.aliasFor(CANONICAL_MODEL);
            assertEquals(MODEL_ALIAS, alias);
            JdbcQueryModel model = jdbcQueryModel(alias);
            DatasourceBindingIdentity binding = bindingIdentity();
            candidate.putQueryModel(
                    CANONICAL_MODEL,
                    model,
                    new ModelProvenance(
                            CANONICAL_MODEL,
                            ModelProvenance.ModelKind.QUERY,
                            candidate.sourceRevision(),
                            Set.of(),
                            Map.of(binding.bindingKey(), binding),
                            true,
                            List.of()));
            CatalogSnapshot snapshot = scope.commit();
            String canonical = snapshot.canonicalQueryModelName(alias);
            QueryModel resolved = snapshot.resolveQueryModel(alias).orElseThrow();
            ModelProvenance provenance = snapshot.queryModelProvenance(canonical)
                    .orElseThrow();
            return new CatalogResolution<>(
                    canonical,
                    resolved,
                    snapshot.identity(),
                    provenance.datasourceBindings(),
                    provenance.bindingIdentityComplete());
        }
    }

    private static ModelResultContext trackedContext(
            CatalogResolution<QueryModel> resolution) {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel(resolution.model().getShortAlias());
        request.setColumns(List.of("id"));

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(request);
        pagingRequest.setPage(1);
        pagingRequest.setPageSize(20);

        ModelResultContext context = new ModelResultContext(pagingRequest, null);
        context.setNamespace(NAMESPACE);
        context.setSecurityContext(ModelResultContext.SecurityContext.builder()
                .authorization(AUTHORIZATION)
                .userId("cross-jvm-user")
                .tenantId("cross-jvm-tenant")
                .roles(List.of("reader"))
                .build());

        context.pinCatalogResolution(resolution, NAMESPACE);
        return context;
    }

    private static DatasourceBindingIdentity bindingIdentity() {
        return new DatasourceBindingIdentity(
                "primary",
                "runtime-registry",
                new DatasourceBindingGeneration("binding:persisted:1"));
    }

    private static JdbcQueryModel jdbcQueryModel(String alias) {
        return (JdbcQueryModel) Proxy.newProxyInstance(
                RedisCrossJvmCacheIT.class.getClassLoader(),
                new Class<?>[]{JdbcQueryModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> CANONICAL_MODEL;
                    case "getShortAlias" -> alias;
                    case "toString" -> "JdbcQueryModel[" + CANONICAL_MODEL + "]";
                    case "hashCode" -> 31;
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }

    private static QueryCacheProperties cacheProperties(String keyPrefix) {
        QueryCacheProperties properties = new QueryCacheProperties();
        properties.setKeyPrefix(keyPrefix);
        properties.setDefaultTtl(Duration.ofMinutes(5));
        properties.setCacheEmptyResult(true);
        properties.getRedis().setTtlJitter(false);
        return properties;
    }

    private static PagingResultImpl<?> result(String marker) {
        return PagingResultImpl.of(List.of(Map.of("marker", marker)), 1);
    }

    @SuppressWarnings("unchecked")
    private static void withProductionRedis(
            String host,
            int port,
            String keyPrefix,
            RedisWork work
    ) {
        AtomicBoolean executed = new AtomicBoolean();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        QueryCacheAutoConfiguration.class))
                .withBean(RedisConnectionFactory.class, () ->
                        new LettuceConnectionFactory(
                                new RedisStandaloneConfiguration(host, port)))
                .withPropertyValues(
                        "foggy.query-cache.enabled=true",
                        "foggy.query-cache.type=redis",
                        "foggy.query-cache.key-prefix=" + keyPrefix,
                        "foggy.query-cache.default-ttl=5m",
                        "foggy.query-cache.cache-empty-result=true",
                        "foggy.query-cache.redis.ttl-jitter=false")
                .run(applicationContext -> {
                    if (applicationContext.getStartupFailure() != null) {
                        throw new AssertionError(
                                "production query-cache auto-configuration failed",
                                applicationContext.getStartupFailure());
                    }
                    assertEquals(1, applicationContext
                            .getBeansOfType(RedisQueryCacheProvider.class).size());
                    RedisQueryCacheProvider provider = applicationContext.getBean(
                            RedisQueryCacheProvider.class);
                    RedisTemplate<String, Object> template =
                            (RedisTemplate<String, Object>) applicationContext.getBean(
                                    "foggyQueryCacheRedisTemplate",
                                    RedisTemplate.class);
                    assertTrue(template.getKeySerializer()
                            instanceof StringRedisSerializer);
                    assertTrue(template.getValueSerializer()
                            instanceof GenericJackson2JsonRedisSerializer);
                    work.execute(provider, template);
                    executed.set(true);
                });
        assertTrue(executed.get(), "production Redis callback must execute");
    }

    private static void assertCachedResult(
            PagingResultImpl<?> result,
            String expectedMarker
    ) {
        assertNotNull(result);
        assertEquals(1L, result.getTotal());
        assertEquals(0, result.getStart());
        assertEquals(1, result.getLimit());
        assertNotNull(result.getItems());
        assertEquals(1, result.getItems().size());
        assertTrue(result.getItems().get(0) instanceof Map<?, ?>);
        Map<?, ?> row = (Map<?, ?>) result.getItems().get(0);
        assertEquals(Set.of("marker"), row.keySet());
        assertEquals(expectedMarker, row.get("marker"));
    }

    private static void emitBinding(ModelResultContext context) {
        assertTrue(context.isBindingIdentityComplete());
        assertEquals(1, context.getDatasourceBindingIdentities().size());
        DatasourceBindingIdentity binding = context.getDatasourceBindingIdentities()
                .get("primary");
        assertNotNull(binding);
        emit("binding_key", binding.bindingKey());
        emit("binding_backend", binding.backendId());
        emit("binding_generation", binding.generation().value());
    }

    private ProbeRun launchProbe(String... probeArguments) throws Exception {
        String classPath = System.getProperty("surefire.test.class.path");
        if (classPath == null || classPath.isBlank()) {
            classPath = System.getProperty("java.class.path");
        }
        assertNotNull(classPath);
        assertFalse(classPath.isBlank());

        Path outputFile = Files.createTempFile("v933-redis-cross-jvm-", ".log");
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        addOptionalCoverageAgent(command, probeArguments[0]);
        command.add("-Djava.net.preferIPv4Stack=true");
        command.add("-cp");
        command.add(classPath);
        command.add(RedisCrossJvmCacheIT.class.getName());
        command.addAll(List.of(probeArguments));

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile())
                    .start();
            long pid = process.pid();
            boolean completed = process.waitFor(45, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                String output = Files.readString(outputFile);
                fail("child probe timed out, pid=" + pid + " output=" + output);
            }

            String output = Files.readString(outputFile);
            System.out.println("--- child probe pid=" + pid + " ---");
            System.out.print(output);
            assertEquals(0, process.exitValue(),
                    "child probe failed, pid=" + pid + " output=" + output);
            return new ProbeRun(pid, parseProbeOutput(output));
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    private static void addOptionalCoverageAgent(List<String> command, String mode) {
        String agentJar = System.getenv("V934_JACOCO_CHILD_AGENT_JAR");
        String execFile = System.getenv("V934_JACOCO_CHILD_EXEC_FILE");
        String sessionPrefix = System.getenv("V934_JACOCO_CHILD_SESSION_PREFIX");
        boolean configured = agentJar != null || execFile != null || sessionPrefix != null;
        if (!configured) {
            return;
        }
        if (agentJar == null || execFile == null || sessionPrefix == null) {
            throw new IllegalStateException("partial v934 child coverage configuration");
        }
        Path agentPath = Path.of(agentJar);
        Path execPath = Path.of(execFile);
        if (!agentPath.isAbsolute() || !Files.isRegularFile(agentPath)
                || !execPath.isAbsolute()
                || !sessionPrefix.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalStateException("invalid v934 child coverage configuration");
        }
        command.add("-javaagent:" + agentPath
                + "=destfile=" + execPath
                + ",append=true,sessionid=" + sessionPrefix + "-child-" + mode
                + ",output=file,dumponexit=true");
    }

    private static Map<String, String> parseProbeOutput(String output) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        output.lines()
                .filter(line -> line.startsWith(PROBE_PREFIX))
                .map(line -> line.substring(PROBE_PREFIX.length()))
                .forEach(line -> {
                    int separator = line.indexOf('=');
                    if (separator <= 0) {
                        throw new IllegalArgumentException("invalid probe output: " + line);
                    }
                    String previous = values.put(
                            line.substring(0, separator),
                            line.substring(separator + 1));
                    if (previous != null) {
                        throw new IllegalArgumentException(
                                "duplicate probe output key: " + line.substring(0, separator));
                    }
                });
        assertFalse(values.isEmpty(), "child probe produced no contract output");
        return Map.copyOf(values);
    }

    private static void assertHashedCacheKey(
            String key,
            String layerPrefix,
            String modelSegment
    ) {
        assertNotNull(key);
        String expectedPrefix = layerPrefix + modelSegment + ":";
        assertTrue(key.startsWith(expectedPrefix));
        String digest = key.substring(expectedPrefix.length());
        assertTrue(digest.matches("[0-9a-f]{64}"), "cache key must end in a SHA-256 digest");
        assertFalse(key.contains("@"), "cache key must not contain an object-address form");
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("required system property is missing: " + name);
        }
        return value.trim();
    }

    private static String requireKey(String key) {
        return Objects.requireNonNull(key, "strong lifecycle identity must produce a cache key");
    }

    private static void emit(String key, String value) {
        System.out.println(PROBE_PREFIX + key + "=" + value);
    }

    private record ProbeRun(long pid, Map<String, String> values) {
        private String value(String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("missing probe output: " + key);
            }
            return value;
        }
    }

    @FunctionalInterface
    private interface RedisWork {
        void execute(
                RedisQueryCacheProvider provider,
                RedisTemplate<String, Object> redisTemplate);
    }
}
