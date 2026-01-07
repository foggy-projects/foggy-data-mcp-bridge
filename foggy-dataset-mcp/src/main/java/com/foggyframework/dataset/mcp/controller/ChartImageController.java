package com.foggyframework.dataset.mcp.controller;

import com.foggyframework.dataset.mcp.storage.LocalChartStorageAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 图表图片访问 Controller
 * <p>
 * 提供本地存储图表的 HTTP 访问。
 * 仅在使用本地存储时启用。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/charts")
@RequiredArgsConstructor
@ConditionalOnBean(LocalChartStorageAdapter.class)
public class ChartImageController {

    private final LocalChartStorageAdapter storageAdapter;

    /**
     * 获取图表图片
     *
     * @param fileName 文件名
     * @return 图片资源
     */
    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> getChartImage(@PathVariable String fileName) {
        try {
            // 安全检查：防止路径遍历攻击
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                log.warn("Invalid chart file name requested: {}", fileName);
                return ResponseEntity.badRequest().build();
            }

            // 只允许访问图片文件
            String lowerName = fileName.toLowerCase();
            if (!lowerName.endsWith(".png") && !lowerName.endsWith(".svg") && !lowerName.endsWith(".jpg")) {
                log.warn("Non-image file requested: {}", fileName);
                return ResponseEntity.badRequest().build();
            }

            Path filePath = storageAdapter.getStorageDir().resolve(fileName);

            if (!Files.exists(filePath)) {
                log.debug("Chart file not found: {}", fileName);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            // 确定 Content-Type
            String contentType = determineContentType(fileName);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                    .body(resource);

        } catch (Exception e) {
            log.error("Failed to serve chart image: {}", fileName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取存储统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStorageStats() {
        try {
            Path storageDir = storageAdapter.getStorageDir();

            long fileCount = 0;
            long totalSize = 0;

            if (Files.isDirectory(storageDir)) {
                try (var stream = Files.list(storageDir)) {
                    var files = stream
                            .filter(Files::isRegularFile)
                            .filter(p -> {
                                String name = p.getFileName().toString().toLowerCase();
                                return name.endsWith(".png") || name.endsWith(".svg") || name.endsWith(".jpg");
                            })
                            .toList();

                    fileCount = files.size();
                    for (Path file : files) {
                        totalSize += Files.size(file);
                    }
                }
            }

            return ResponseEntity.ok()
                    .body(java.util.Map.of(
                            "directory", storageDir.toString(),
                            "fileCount", fileCount,
                            "totalSizeKB", totalSize / 1024,
                            "available", storageAdapter.isAvailable()
                    ));

        } catch (Exception e) {
            log.error("Failed to get storage stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String determineContentType(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".png")) {
            return "image/png";
        } else if (lowerName.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }
}
