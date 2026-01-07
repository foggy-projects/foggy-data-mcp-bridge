package com.foggyframework.dataset.mcp.storage.cloud;

import com.foggyframework.dataset.mcp.storage.ChartStorageException;
import com.foggyframework.dataset.mcp.storage.ChartStorageProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * 腾讯云 COS 存储适配器
 *
 * <p>配置示例：
 * <pre>
 * foggy:
 *   chart:
 *     storage:
 *       type: tencent-cos
 *       tencent-cos:
 *         region: ap-guangzhou
 *         secret-id: your-secret-id
 *         secret-key: your-secret-key
 *         bucket-name: your-bucket-1250000000
 *         path-prefix: charts/
 *         custom-domain: https://cdn.example.com
 * </pre>
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "foggy.chart.storage.type", havingValue = "tencent-cos")
@ConditionalOnClass(COSClient.class)
public class TencentCosStorageAdapter extends AbstractCloudStorageAdapter {

    private final ChartStorageProperties properties;
    private final COSClient cosClient;

    public TencentCosStorageAdapter(ChartStorageProperties properties) {
        this.properties = properties;

        ChartStorageProperties.TencentCos config = properties.getTencentCos();
        COSCredentials credentials = new BasicCOSCredentials(config.getSecretId(), config.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(config.getRegion()));

        this.cosClient = new COSClient(credentials, clientConfig);

        log.info("Tencent COS client initialized: region={}, bucket={}",
                config.getRegion(), config.getBucketName());
    }

    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
        }
    }

    @Override
    public String getType() {
        return "tencent-cos";
    }

    @Override
    public String save(byte[] imageBytes, String format, String traceId) throws ChartStorageException {
        ChartStorageProperties.TencentCos config = properties.getTencentCos();

        String objectKey = generateObjectKey(format, traceId, config.getPathPrefix());

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(imageBytes.length);
            metadata.setContentType("image/" + format);

            PutObjectRequest putRequest = new PutObjectRequest(
                    config.getBucketName(),
                    objectKey,
                    new ByteArrayInputStream(imageBytes),
                    metadata
            );
            cosClient.putObject(putRequest);

            String url = buildCosAccessUrl(config, objectKey);

            log.info("Chart uploaded to Tencent COS: {}", url);
            return url;

        } catch (Exception e) {
            throw new ChartStorageException("Failed to upload to Tencent COS: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean delete(String url) {
        ChartStorageProperties.TencentCos config = properties.getTencentCos();
        String objectKey = extractObjectKey(url, config.getPathPrefix());

        if (objectKey == null) {
            return false;
        }

        try {
            cosClient.deleteObject(config.getBucketName(), objectKey);
            return true;
        } catch (Exception e) {
            log.warn("Failed to delete from Tencent COS: {}", url, e);
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            ChartStorageProperties.TencentCos config = properties.getTencentCos();
            return cosClient.doesBucketExist(config.getBucketName());
        } catch (Exception e) {
            log.warn("Failed to check Tencent COS availability", e);
            return false;
        }
    }

    private String buildCosAccessUrl(ChartStorageProperties.TencentCos config, String objectKey) {
        if (config.getCustomDomain() != null && !config.getCustomDomain().isEmpty()) {
            String domain = config.getCustomDomain().endsWith("/")
                    ? config.getCustomDomain().substring(0, config.getCustomDomain().length() - 1)
                    : config.getCustomDomain();
            return domain + "/" + objectKey;
        }

        // COS 默认域名格式：bucket-appid.cos.region.myqcloud.com
        return String.format("https://%s.cos.%s.myqcloud.com/%s",
                config.getBucketName(), config.getRegion(), objectKey);
    }
}
