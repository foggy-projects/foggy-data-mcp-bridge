package com.foggyframework.analytics.console.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.console.catalog.AnalyticsConsoleCatalogException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/** Append-only, fsync-backed single-process Ask recovery journal. */
public final class FileAnalyticsConsoleAskRecoveryRepository
        implements AnalyticsConsoleAskRecoveryRepository {

    private final Path path;
    private final ObjectMapper json;
    private final ReentrantLock lock = new ReentrantLock();

    public FileAnalyticsConsoleAskRecoveryRepository(Path path, ObjectMapper json) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.json = Objects.requireNonNull(json, "json").copy().findAndRegisterModules();
        try {
            Path parent = this.path.getParent();
            if (parent == null) throw new IOException("journal requires a parent directory");
            Files.createDirectories(parent);
            if (Files.isSymbolicLink(this.path)) throw new IOException("journal is a symlink");
            if (!Files.exists(this.path, LinkOption.NOFOLLOW_LINKS)) {
                Files.createFile(this.path);
            }
        } catch (IOException failure) {
            throw unavailable("Ask recovery journal could not be initialized", failure);
        }
    }

    @Override
    public void record(Entry entry) {
        lock.lock();
        try {
            byte[] line = (json.writeValueAsString(Objects.requireNonNull(entry, "entry"))
                    + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(path,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(line);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
        } catch (IOException failure) {
            throw unavailable("Ask recovery journal could not be persisted", failure);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Entry> unresolved() {
        lock.lock();
        try {
            Map<String, Entry> latest = new LinkedHashMap<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    Entry entry = json.readValue(line, Entry.class);
                    latest.put(entry.operationId(), entry);
                }
            }
            return latest.values().stream()
                    .filter(entry -> entry.state() != State.CATALOGED)
                    .toList();
        } catch (IOException | RuntimeException failure) {
            throw unavailable("Ask recovery journal could not be read", failure);
        } finally {
            lock.unlock();
        }
    }

    private static AnalyticsConsoleCatalogException unavailable(
            String message, Throwable cause) {
        return new AnalyticsConsoleCatalogException(
                "ANALYTICS_CONSOLE_ASK_RECOVERY_UNAVAILABLE", message, cause);
    }
}
