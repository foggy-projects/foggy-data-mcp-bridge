package com.foggyframework.dataset.mcp.storage.cloud;

import com.foggyframework.dataset.mcp.storage.ChartStorageAdapter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 云存储适配器抽象基类
 * <p>
 * 提供云存储的通用功能，具体平台实现只需关注上传逻辑。
 * </p>
 */
@Slf4j
public abstract class AbstractCloudStorageAdapter implements ChartStorageAdapter {

    /**
     * 生成对象存储路径
     *
     * @param format   图片格式
     * @param traceId  追踪 ID
     * @param prefix   路径前缀
     * @return 对象路径（不含 bucket）
     */
    protected String generateObjectKey(String format, String traceId, String prefix) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd/HHmmss"));
        String shortTraceId = traceId != null && traceId.length() > 8 ? traceId.substring(0, 8) : traceId;
        String fileName = String.format("chart_%s.%s", shortTraceId, format);

        String normalizedPrefix = prefix != null ? prefix : "";
        if (!normalizedPrefix.isEmpty() && !normalizedPrefix.endsWith("/")) {
            normalizedPrefix = normalizedPrefix + "/";
        }

        return normalizedPrefix + timestamp + "/" + fileName;
    }

    /**
     * 构建访问 URL
     *
     * @param customDomain 自定义域名（可选）
     * @param endpoint     服务端点
     * @param bucketName   存储桶名称
     * @param objectKey    对象路径
     * @return 访问 URL
     */
    protected String buildAccessUrl(String customDomain, String endpoint, String bucketName, String objectKey) {
        if (customDomain != null && !customDomain.isEmpty()) {
            // 使用自定义域名（CDN）
            String domain = customDomain.endsWith("/") ? customDomain.substring(0, customDomain.length() - 1) : customDomain;
            return domain + "/" + objectKey;
        }

        // 使用默认域名
        return String.format("https://%s.%s/%s", bucketName, endpoint, objectKey);
    }

    /**
     * 从 URL 提取对象 Key
     */
    protected String extractObjectKey(String url, String pathPrefix) {
        if (url == null) return null;

        try {
            java.net.URL parsedUrl = new java.net.URL(url);
            String path = parsedUrl.getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            return path;
        } catch (Exception e) {
            log.warn("Failed to parse URL: {}", url);
            return null;
        }
    }
}
