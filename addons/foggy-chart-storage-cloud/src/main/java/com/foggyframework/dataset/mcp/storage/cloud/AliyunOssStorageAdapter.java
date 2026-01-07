package com.foggyframework.dataset.mcp.storage.cloud;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.foggyframework.dataset.mcp.storage.ChartStorageException;
import com.foggyframework.dataset.mcp.storage.ChartStorageProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * 阿里云 OSS 存储适配器
 *
 * <p>配置示例：
 * <pre>
 * foggy:
 *   chart:
 *     storage:
 *       type: aliyun-oss
 *       aliyun-oss:
 *         endpoint: oss-cn-hangzhou.aliyuncs.com
 *         access-key-id: your-access-key-id
 *         access-key-secret: your-access-key-secret
 *         bucket-name: your-bucket-name
 *         path-prefix: charts/
 *         custom-domain: https://cdn.example.com
 * </pre>
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "foggy.chart.storage.type", havingValue = "aliyun-oss")
@ConditionalOnClass(OSS.class)
public class AliyunOssStorageAdapter extends AbstractCloudStorageAdapter {

    private final ChartStorageProperties properties;
    private final OSS ossClient;

    public AliyunOssStorageAdapter(ChartStorageProperties properties) {
        this.properties = properties;

        ChartStorageProperties.AliyunOss config = properties.getAliyunOss();
        this.ossClient = new OSSClientBuilder().build(
                config.getEndpoint(),
                config.getAccessKeyId(),
                config.getAccessKeySecret()
        );

        log.info("Aliyun OSS client initialized: endpoint={}, bucket={}",
                config.getEndpoint(), config.getBucketName());
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    @Override
    public String getType() {
        return "aliyun-oss";
    }

    @Override
    public String save(byte[] imageBytes, String format, String traceId) throws ChartStorageException {
        ChartStorageProperties.AliyunOss config = properties.getAliyunOss();

        String objectKey = generateObjectKey(format, traceId, config.getPathPrefix());

        try {
            ossClient.putObject(config.getBucketName(), objectKey, new ByteArrayInputStream(imageBytes));

            String url = buildAccessUrl(
                    config.getCustomDomain(),
                    config.getEndpoint(),
                    config.getBucketName(),
                    objectKey
            );

            log.info("Chart uploaded to Aliyun OSS: {}", url);
            return url;

        } catch (Exception e) {
            throw new ChartStorageException("Failed to upload to Aliyun OSS: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean delete(String url) {
        ChartStorageProperties.AliyunOss config = properties.getAliyunOss();
        String objectKey = extractObjectKey(url, config.getPathPrefix());

        if (objectKey == null) {
            return false;
        }

        try {
            ossClient.deleteObject(config.getBucketName(), objectKey);
            return true;
        } catch (Exception e) {
            log.warn("Failed to delete from Aliyun OSS: {}", url, e);
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            ChartStorageProperties.AliyunOss config = properties.getAliyunOss();
            return ossClient.doesBucketExist(config.getBucketName());
        } catch (Exception e) {
            log.warn("Failed to check Aliyun OSS availability", e);
            return false;
        }
    }
}
