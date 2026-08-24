package com.foggyframework.analytics.console.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.console.model.AnalyticsConsoleAsset;
import com.foggyframework.analytics.console.model.AnalyticsConsoleCatalogState;
import com.foggyframework.analytics.console.model.AnalyticsConsoleFolder;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

/** Atomic single-process JSON catalog for Console-owned product metadata. */
public final class FileAnalyticsConsoleCatalogRepository
        implements AnalyticsConsoleCatalogRepository {

    private final Path catalogPath;
    private final ObjectMapper json;
    private final ReentrantLock lock = new ReentrantLock();

    public FileAnalyticsConsoleCatalogRepository(Path catalogPath, ObjectMapper json) {
        this.catalogPath = Objects.requireNonNull(catalogPath, "catalogPath")
                .toAbsolutePath().normalize();
        this.json = Objects.requireNonNull(json, "json").copy().findAndRegisterModules();
        initialize();
    }

    @Override
    public AnalyticsConsoleCatalogState read() {
        lock.lock();
        try {
            return readUnderLock();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public AnalyticsConsoleCatalogState update(
            UnaryOperator<AnalyticsConsoleCatalogState> change) {
        Objects.requireNonNull(change, "change");
        lock.lock();
        try {
            AnalyticsConsoleCatalogState current = readUnderLock();
            AnalyticsConsoleCatalogState proposed = Objects.requireNonNull(
                    change.apply(current), "catalog update");
            AnalyticsConsoleCatalogState next = new AnalyticsConsoleCatalogState(
                    current.revision() + 1,
                    proposed.folders(),
                    proposed.assets(),
                    proposed.conversations());
            validate(next);
            writeUnderLock(next);
            return next;
        } finally {
            lock.unlock();
        }
    }

    private void initialize() {
        lock.lock();
        try {
            Path parent = catalogPath.getParent();
            if (parent == null) {
                throw failure("ANALYTICS_CONSOLE_CATALOG_INVALID",
                        "Analytics Console catalog requires a parent directory", null);
            }
            Files.createDirectories(parent);
            if (Files.isSymbolicLink(catalogPath)) {
                throw failure("ANALYTICS_CONSOLE_CATALOG_UNSAFE",
                        "Analytics Console catalog cannot be a symbolic link", null);
            }
            if (!Files.exists(catalogPath, LinkOption.NOFOLLOW_LINKS)) {
                writeUnderLock(AnalyticsConsoleCatalogState.empty());
            } else {
                validate(readUnderLock());
            }
        } catch (IOException failure) {
            throw failure("ANALYTICS_CONSOLE_CATALOG_UNAVAILABLE",
                    "Analytics Console catalog could not be initialized", failure);
        } finally {
            lock.unlock();
        }
    }

    private AnalyticsConsoleCatalogState readUnderLock() {
        try {
            if (Files.isSymbolicLink(catalogPath)
                    || !Files.isRegularFile(catalogPath, LinkOption.NOFOLLOW_LINKS)) {
                throw failure("ANALYTICS_CONSOLE_CATALOG_UNSAFE",
                        "Analytics Console catalog must be a regular file", null);
            }
            AnalyticsConsoleCatalogState state = json.readValue(
                    Files.readAllBytes(catalogPath), AnalyticsConsoleCatalogState.class);
            validate(state);
            return state;
        } catch (AnalyticsConsoleCatalogException known) {
            throw known;
        } catch (IOException | RuntimeException failure) {
            throw failure("ANALYTICS_CONSOLE_CATALOG_INVALID",
                    "Analytics Console catalog could not be read", failure);
        }
    }

    private void writeUnderLock(AnalyticsConsoleCatalogState state) {
        Path temporary = catalogPath.resolveSibling(
                "." + catalogPath.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(state);
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, catalogPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, catalogPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The uniquely owned temporary file is safe to leave for operator diagnosis.
            }
            throw failure("ANALYTICS_CONSOLE_CATALOG_UNAVAILABLE",
                    "Analytics Console catalog could not be persisted", failure);
        }
    }

    private static void validate(AnalyticsConsoleCatalogState state) {
        Objects.requireNonNull(state, "state");
        Set<String> folders = new HashSet<>();
        for (AnalyticsConsoleFolder folder : state.folders()) {
            if (!folders.add(folder.folderId())) {
                throw failure("ANALYTICS_CONSOLE_CATALOG_INVALID",
                        "Analytics Console folder identity is duplicated", null);
            }
        }
        Set<String> assets = new HashSet<>();
        for (AnalyticsConsoleAsset asset : state.assets()) {
            if (!assets.add(asset.assetId())) {
                throw failure("ANALYTICS_CONSOLE_CATALOG_INVALID",
                        "Analytics Console asset identity is duplicated", null);
            }
            if (asset.folderId() != null && !folders.contains(asset.folderId())) {
                throw failure("ANALYTICS_CONSOLE_CATALOG_INVALID",
                        "Analytics Console asset references a missing folder", null);
            }
        }
        Set<String> conversations = new HashSet<>();
        for (var conversation : state.conversations()) {
            if (!conversations.add(conversation.conversationId())
                    || state.assets().stream().noneMatch(asset ->
                            asset.assetId().equals(conversation.assetId()))) {
                throw failure("ANALYTICS_CONSOLE_CATALOG_INVALID",
                        "Analytics Console conversation binding is invalid", null);
            }
        }
    }

    private static AnalyticsConsoleCatalogException failure(
            String code, String message, Throwable cause) {
        return cause == null
                ? new AnalyticsConsoleCatalogException(code, message)
                : new AnalyticsConsoleCatalogException(code, message, cause);
    }
}
