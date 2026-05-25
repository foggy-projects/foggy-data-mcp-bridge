package com.foggyframework.dataviewer.service.listpreset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.ListPresetDef;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 文件系统自定义列表存储。
 */
@Slf4j
public class FileSystemListPresetStore implements ListPresetStore {

    private static final String DEFAULT_SEGMENT = "_default";

    private final Path baseDir;
    private final ObjectMapper objectMapper;

    public FileSystemListPresetStore(DataViewerProperties properties, ObjectMapper objectMapper) {
        this.baseDir = Path.of(properties.getListPreset().getFileBaseDir()).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    @Override
    public ListPresetDef save(ListPresetDef preset) {
        Path presetFile = presetFile(preset.getOwnerId(), preset.getModel(), preset.getBusinessKey(), preset.getId());
        writeJson(presetFile, preset);
        if (Boolean.TRUE.equals(preset.getIsDefault())) {
            writeJson(scopeDir(preset.getOwnerId(), preset.getModel(), preset.getBusinessKey()).resolve("default.json"), preset.getId());
        }
        return preset;
    }

    @Override
    public List<ListPresetDef> list(String userId, String model, String businessKey) {
        Path presetsDir = scopeDir(userId, model, businessKey).resolve("presets");
        if (!Files.isDirectory(presetsDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(presetsDir)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readPreset)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(
                            preset -> Optional.ofNullable(preset.getUpdatedAt()).orElse(Instant.EPOCH),
                            Comparator.reverseOrder()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list list presets", e);
        }
    }

    @Override
    public Optional<ListPresetDef> findById(String userId, String presetId) {
        Path userDir = baseDir.resolve(sanitize(userId));
        if (!Files.isDirectory(userDir)) {
            return Optional.empty();
        }
        String fileName = sanitize(presetId) + ".json";
        try (Stream<Path> files = Files.find(userDir, 6, (path, attrs) ->
                attrs.isRegularFile() && path.getFileName().toString().equals(fileName))) {
            return files
                    .findFirst()
                    .flatMap(this::readPreset)
                    .filter(preset -> userId.equals(preset.getOwnerId()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to find list preset", e);
        }
    }

    @Override
    public Optional<ListPresetDef> findDefault(String userId, String model, String businessKey) {
        return list(userId, model, businessKey).stream()
                .filter(preset -> Boolean.TRUE.equals(preset.getIsDefault()))
                .findFirst();
    }

    @Override
    public void clearDefault(String userId, String model, String businessKey) {
        for (ListPresetDef preset : list(userId, model, businessKey)) {
            if (Boolean.TRUE.equals(preset.getIsDefault())) {
                preset.setIsDefault(false);
                save(preset);
            }
        }
        try {
            Files.deleteIfExists(scopeDir(userId, model, businessKey).resolve("default.json"));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete default list preset marker", e);
        }
    }

    @Override
    public void delete(ListPresetDef preset) {
        Path presetFile = presetFile(preset.getOwnerId(), preset.getModel(), preset.getBusinessKey(), preset.getId());
        try {
            Files.deleteIfExists(presetFile);
            if (Boolean.TRUE.equals(preset.getIsDefault())) {
                Files.deleteIfExists(scopeDir(preset.getOwnerId(), preset.getModel(), preset.getBusinessKey())
                        .resolve("default.json"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete list preset", e);
        }
    }

    private Path presetFile(String userId, String model, String businessKey, String presetId) {
        return scopeDir(userId, model, businessKey).resolve("presets").resolve(sanitize(presetId) + ".json");
    }

    private Path scopeDir(String userId, String model, String businessKey) {
        Path path = baseDir
                .resolve(sanitize(userId))
                .resolve(sanitize(model))
                .resolve(sanitizeBusinessKey(businessKey))
                .normalize();
        if (!path.startsWith(baseDir)) {
            throw new IllegalArgumentException("Invalid list preset path");
        }
        return path;
    }

    private Optional<ListPresetDef> readPreset(Path file) {
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), ListPresetDef.class));
        } catch (IOException e) {
            log.warn("Ignoring invalid list preset file {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private void writeJson(Path file, Object value) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
            objectMapper.writeValue(temp.toFile(), value);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write list preset file", e);
        }
    }

    private String sanitizeBusinessKey(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_SEGMENT;
        }
        return sanitize(value);
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_SEGMENT;
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
