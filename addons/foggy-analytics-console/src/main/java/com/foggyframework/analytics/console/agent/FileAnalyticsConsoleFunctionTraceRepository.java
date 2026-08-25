package com.foggyframework.analytics.console.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/** Atomic single-process file store for raw Console-owned Function callback payloads. */
public final class FileAnalyticsConsoleFunctionTraceRepository
        implements AnalyticsConsoleFunctionTraceRepository {

    private final Path root;
    private final ObjectMapper json;
    private final ReentrantLock lock = new ReentrantLock();

    public FileAnalyticsConsoleFunctionTraceRepository(Path root, ObjectMapper json) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.json = Objects.requireNonNull(json, "json").copy().findAndRegisterModules();
        initialize();
    }

    @Override
    public void save(FunctionTrace trace) {
        Objects.requireNonNull(trace, "trace");
        lock.lock();
        try {
            Path turnDirectory = turnDirectory(trace.conversationId(), trace.askInvocationRef());
            ensureDirectory(turnDirectory);
            Path target = turnDirectory.resolve(digest(trace.functionInvocationId()) + ".json");
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                FunctionTrace existing = read(target);
                if (!existing.equals(trace)) {
                    throw failure(
                            "ANALYTICS_CONSOLE_FUNCTION_TRACE_CONFLICT",
                            "Analytics Function trace changed for an existing invocation",
                            null);
                }
                return;
            }
            write(target, trace);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<FunctionTrace> findByTurn(String conversationId, String askInvocationRef) {
        Path directory = turnDirectory(
                required(conversationId, "conversationId"),
                required(askInvocationRef, "askInvocationRef"));
        lock.lock();
        try {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                return List.of();
            }
            requireSafeDirectory(directory);
            try (var files = Files.list(directory)) {
                return files
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .map(this::read)
                        .filter(trace -> trace.conversationId().equals(conversationId))
                        .filter(trace -> trace.askInvocationRef().equals(askInvocationRef))
                        .toList();
            } catch (IOException error) {
                throw failure(
                        "ANALYTICS_CONSOLE_FUNCTION_TRACE_UNAVAILABLE",
                        "Analytics Function traces could not be listed",
                        error);
            }
        } finally {
            lock.unlock();
        }
    }

    private void initialize() {
        lock.lock();
        try {
            ensureDirectory(root);
        } finally {
            lock.unlock();
        }
    }

    private void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            requireSafeDirectory(directory);
        } catch (IOException error) {
            throw failure(
                    "ANALYTICS_CONSOLE_FUNCTION_TRACE_UNAVAILABLE",
                    "Analytics Function trace directory could not be initialized",
                    error);
        }
    }

    private static void requireSafeDirectory(Path directory) {
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw failure(
                    "ANALYTICS_CONSOLE_FUNCTION_TRACE_UNSAFE",
                    "Analytics Function trace path must be a regular directory",
                    null);
        }
    }

    private FunctionTrace read(Path path) {
        try {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw failure(
                        "ANALYTICS_CONSOLE_FUNCTION_TRACE_UNSAFE",
                        "Analytics Function trace must be a regular file",
                        null);
            }
            return json.readValue(Files.readAllBytes(path), FunctionTrace.class);
        } catch (AnalyticsConsoleCatalogException known) {
            throw known;
        } catch (IOException | RuntimeException error) {
            throw failure(
                    "ANALYTICS_CONSOLE_FUNCTION_TRACE_INVALID",
                    "Analytics Function trace could not be read",
                    error);
        }
    }

    private void write(Path target, FunctionTrace trace) {
        Path temporary = target.resolveSibling(
                "." + target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(trace);
            Files.write(
                    temporary,
                    bytes,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // A uniquely owned temporary trace is safe to leave for diagnosis.
            }
            throw failure(
                    "ANALYTICS_CONSOLE_FUNCTION_TRACE_UNAVAILABLE",
                    "Analytics Function trace could not be persisted",
                    error);
        }
    }

    private Path turnDirectory(String conversationId, String askInvocationRef) {
        return root.resolve(digest(conversationId)).resolve(digest(askInvocationRef));
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }

    private static AnalyticsConsoleCatalogException failure(
            String code,
            String message,
            Throwable cause) {
        return cause == null
                ? new AnalyticsConsoleCatalogException(code, message)
                : new AnalyticsConsoleCatalogException(code, message, cause);
    }
}
