package com.foggyframework.dataset.db.model.cache.config;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PivotOuterCacheRedisInvalidationCrossProcessLiveTest {

    private static final String WORKER_CLASS =
            PivotOuterCacheRedisInvalidationCrossProcessWorker.class.getName();

    @TempDir
    Path resultDir;

    private String channel;
    private String namespace;
    private String model;
    private String eventId;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(Boolean.getBoolean("foggy.redis.live.enabled"),
                "Set -Dfoggy.redis.live.enabled=true to run live Redis tests");
        assertLiveRedisAvailable();
        String suffix = UUID.randomUUID().toString();
        channel = "foggy:live:pivot:cross-process:" + suffix;
        namespace = "ns-cross-process-" + suffix;
        model = "ModelCrossProcess";
        eventId = "evt-cross-process-" + suffix;
    }

    @Test
    @DisplayName("live Redis Pub/Sub invalidation crosses independent JVM processes")
    void liveRedisInvalidationCrossesIndependentJvmProcesses() throws Exception {
        Process listener = startWorker("listener", "node-b");
        try {
            waitForFile(resultDir.resolve("listener-ready.properties"), listener,
                    Duration.ofSeconds(15L), "listener readiness");

            Process publisher = startWorker("publisher", "node-a");
            assertProcessExit(publisher, Duration.ofSeconds(15L), "publisher");
            assertProcessExit(listener, Duration.ofSeconds(20L), "listener");

            Properties publisherResult = readProperties(resultDir.resolve("publisher-publish.properties"));
            assertEquals("true", publisherResult.getProperty("success"));
            assertEquals("2", publisherResult.getProperty("removed"));
            assertEquals("2", publisherResult.getProperty("attemptedNodes"));
            assertEquals("2", publisherResult.getProperty("succeededNodes"));
            assertEquals("0", publisherResult.getProperty("failedNodes"));

            Properties sourceEviction = readProperties(resultDir.resolve("publisher-evict.properties"));
            assertEquals("node-a", sourceEviction.getProperty("nodeId"));
            assertEquals(namespace, sourceEviction.getProperty("namespace"));
            assertEquals(model, sourceEviction.getProperty("model"));

            Properties remoteEviction = readProperties(resultDir.resolve("listener-evict.properties"));
            assertEquals("node-b", remoteEviction.getProperty("nodeId"));
            assertEquals(namespace, remoteEviction.getProperty("namespace"));
            assertEquals(model, remoteEviction.getProperty("model"));
        } finally {
            stopProcess(listener);
        }
    }

    private Process startWorker(String role, String nodeId) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Dspring.main.banner-mode=off");
        command.add("-Dlogging.level.root=WARN");
        command.add("-cp");
        command.add(testClasspath());
        command.add(WORKER_CLASS);
        command.add(role);
        command.add(redisHost());
        command.add(Integer.toString(redisPort()));
        command.add(channel);
        command.add(nodeId);
        command.add(namespace);
        command.add(model);
        command.add(eventId);
        command.add(resultDir.toAbsolutePath().toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(resultDir.resolve(role + ".log").toFile());
        return processBuilder.start();
    }

    private void waitForFile(Path path,
                             Process process,
                             Duration timeout,
                             String description) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(path)) {
                return;
            }
            if (!process.isAlive()) {
                fail(description + " process exited early with code " + process.exitValue()
                        + "\n" + processLog(path));
            }
            Thread.sleep(50L);
        }
        fail("Timed out waiting for " + description + "\n" + processLog(path));
    }

    private void assertProcessExit(Process process, Duration timeout, String role) throws Exception {
        assertTrue(process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS),
                () -> "Timed out waiting for " + role + "\n" + safeProcessLog(resultDir.resolve(role + ".log")));
        assertEquals(0, process.exitValue(),
                () -> role + " process failed\n" + safeProcessLog(resultDir.resolve(role + ".log")));
    }

    private void stopProcess(Process process) throws InterruptedException {
        if (!process.isAlive()) {
            return;
        }
        process.destroy();
        if (!process.waitFor(2L, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(2L, TimeUnit.SECONDS);
        }
    }

    private Properties readProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    private String processLog(Path relatedPath) throws IOException {
        String fileName = relatedPath.getFileName().toString();
        String logName;
        if (fileName.endsWith(".log")) {
            logName = fileName;
        } else {
            String role = fileName.contains("-") ? fileName.substring(0, fileName.indexOf('-')) : fileName;
            logName = role.replace(".properties", "") + ".log";
        }
        Path log = resultDir.resolve(logName);
        if (!Files.exists(log)) {
            return "";
        }
        return Files.readString(log);
    }

    private String safeProcessLog(Path relatedPath) {
        try {
            return processLog(relatedPath);
        } catch (IOException e) {
            return "Unable to read process log: " + e.getMessage();
        }
    }

    private void assertLiveRedisAvailable() {
        LettuceConnectionFactory connectionFactory = connectionFactory(redisHost(), redisPort());
        RedisConnection connection = connectionFactory.getConnection();
        try {
            assertEquals("PONG", connection.ping());
        } finally {
            connection.close();
            connectionFactory.destroy();
        }
    }

    private LettuceConnectionFactory connectionFactory(String host, int port) {
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(host, port);
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2L))
                .shutdownTimeout(Duration.ZERO)
                .build();
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(redisConfiguration, clientConfiguration);
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    private String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private String testClasspath() {
        String surefireClasspath = System.getProperty("surefire.test.class.path");
        if (surefireClasspath != null && !surefireClasspath.isBlank()) {
            return surefireClasspath;
        }
        return System.getProperty("java.class.path");
    }

    private String redisHost() {
        return System.getProperty("foggy.redis.live.host", "127.0.0.1");
    }

    private int redisPort() {
        return Integer.parseInt(System.getProperty("foggy.redis.live.port", "16379"));
    }
}
