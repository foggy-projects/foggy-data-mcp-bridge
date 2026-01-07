package com.foggyframework.dataset.mcp.storage;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地图表存储适配器
 * <p>
 * 将图表保存到本地目录，通过 HTTP Controller 提供访问。
 * 支持定期清理过期文件。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "foggy.chart.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalChartStorageAdapter implements ChartStorageAdapter {

    private final ChartStorageProperties properties;

    private Path storageDir;

    @PostConstruct
    public void init() {
        this.storageDir = Paths.get(properties.getLocal().getDirectory()).toAbsolutePath();
        try {
            Files.createDirectories(storageDir);
            log.info("Local chart storage initialized: {}", storageDir);
        } catch (IOException e) {
            log.error("Failed to create chart storage directory: {}", storageDir, e);
        }
    }

    @Override
    public String getType() {
        return "local";
    }

    @Override
    public String save(byte[] imageBytes, String format, String traceId) throws ChartStorageException {
        try {
            // 生成文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String shortTraceId = traceId != null && traceId.length() > 8 ? traceId.substring(0, 8) : traceId;
            String fileName = String.format("chart_%s_%s.%s", timestamp, shortTraceId, format);

            // 保存文件
            Path filePath = storageDir.resolve(fileName);
            Files.write(filePath, imageBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("Chart saved locally: {}, size: {} KB", fileName, imageBytes.length / 1024);

            // 返回访问 URL
            return buildAccessUrl(fileName);

        } catch (IOException e) {
            throw new ChartStorageException("Failed to save chart locally: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean delete(String url) {
        try {
            String fileName = extractFileName(url);
            if (fileName != null) {
                Path filePath = storageDir.resolve(fileName);
                return Files.deleteIfExists(filePath);
            }
        } catch (IOException e) {
            log.warn("Failed to delete chart: {}", url, e);
        }
        return false;
    }

    @Override
    public boolean isAvailable() {
        return Files.isDirectory(storageDir) && Files.isWritable(storageDir);
    }

    /**
     * 获取存储目录路径
     */
    public Path getStorageDir() {
        return storageDir;
    }

    /**
     * 定期清理过期文件
     * 默认每天凌晨 3 点执行，可通过配置修改
     */
    @Scheduled(cron = "${foggy.chart.storage.local.cleanup-cron:0 0 3 * * ?}")
    public void cleanupExpiredFiles() {
        if (storageDir == null || !Files.isDirectory(storageDir)) {
            return;
        }

        long retentionMillis = properties.getLocal().getRetention().toMillis();
        Instant cutoffTime = Instant.now().minusMillis(retentionMillis);

        AtomicLong deletedCount = new AtomicLong(0);
        AtomicLong freedSpace = new AtomicLong(0);

        try {
            Files.walkFileTree(storageDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        // 只清理图片文件
                        String fileName = file.getFileName().toString().toLowerCase();
                        if (fileName.startsWith("chart_") &&
                                (fileName.endsWith(".png") || fileName.endsWith(".svg") || fileName.endsWith(".jpg"))) {

                            if (attrs.lastModifiedTime().toInstant().isBefore(cutoffTime)) {
                                long size = attrs.size();
                                Files.delete(file);
                                deletedCount.incrementAndGet();
                                freedSpace.addAndGet(size);
                            }
                        }
                    } catch (IOException e) {
                        log.warn("Failed to delete expired file: {}", file, e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (deletedCount.get() > 0) {
                log.info("Chart cleanup completed: deleted {} files, freed {} KB",
                        deletedCount.get(), freedSpace.get() / 1024);
            }

        } catch (IOException e) {
            log.error("Failed to cleanup expired charts", e);
        }
    }

    /**
     * 构建访问 URL
     */
    private String buildAccessUrl(String fileName) {
        String urlPrefix = properties.getLocal().getUrlPrefix();
        if (urlPrefix != null && !urlPrefix.isEmpty()) {
            // 使用配置的 URL 前缀
            return urlPrefix.endsWith("/") ? urlPrefix + fileName : urlPrefix + "/" + fileName;
        }
        // 默认使用相对路径，由 Controller 提供访问
        return "/charts/" + fileName;
    }

    /**
     * 从 URL 提取文件名
     */
    private String extractFileName(String url) {
        if (url == null) return null;
        int lastSlash = url.lastIndexOf('/');
        return lastSlash >= 0 ? url.substring(lastSlash + 1) : url;
    }
}
