package com.foggyframework.dataset.mcp.storage.cloud;

import com.foggyframework.dataset.mcp.storage.ChartStorageException;
import com.foggyframework.dataset.mcp.storage.ChartStorageProperties;
import com.obs.services.ObsClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * 华为云 OBS 存储适配器
 *
 * <p>配置示例：
 * <pre>
 * foggy:
 *   chart:
 *     storage:
 *       type: huawei-obs
 *       huawei-obs:
 *         endpoint: obs.cn-east-3.myhuaweicloud.com
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
@ConditionalOnProperty(name = "foggy.chart.storage.type", havingValue = "huawei-obs")
@ConditionalOnClass(ObsClient.class)
public class HuaweiObsStorageAdapter extends AbstractCloudStorageAdapter {

    private final ChartStorageProperties properties;
    private final ObsClient obsClient;

    public HuaweiObsStorageAdapter(ChartStorageProperties properties) {
        this.properties = properties;

        ChartStorageProperties.HuaweiObs config = properties.getHuaweiObs();
        this.obsClient = new ObsClient(
                config.getAccessKeyId(),
                config.getAccessKeySecret(),
                config.getEndpoint()
        );

        log.info("Huawei OBS client initialized: endpoint={}, bucket={}",
                config.getEndpoint(), config.getBucketName());
    }

    @PreDestroy
    public void destroy() throws Exception {
        if (obsClient != null) {
            obsClient.close();
        }
    }

    @Override
    public String getType() {
        return "huawei-obs";
    }

    @Override
    public String save(byte[] imageBytes, String format, String traceId) throws ChartStorageException {
        ChartStorageProperties.HuaweiObs config = properties.getHuaweiObs();

        String objectKey = generateObjectKey(format, traceId, config.getPathPrefix());

        try {
            obsClient.putObject(config.getBucketName(), objectKey, new ByteArrayInputStream(imageBytes));

            String url = buildAccessUrl(
                    config.getCustomDomain(),
                    config.getEndpoint(),
                    config.getBucketName(),
                    objectKey
            );

            log.info("Chart uploaded to Huawei OBS: {}", url);
            return url;

        } catch (Exception e) {
            throw new ChartStorageException("Failed to upload to Huawei OBS: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean delete(String url) {
        ChartStorageProperties.HuaweiObs config = properties.getHuaweiObs();
        String objectKey = extractObjectKey(url, config.getPathPrefix());

        if (objectKey == null) {
            return false;
        }

        try {
            obsClient.deleteObject(config.getBucketName(), objectKey);
            return true;
        } catch (Exception e) {
            log.warn("Failed to delete from Huawei OBS: {}", url, e);
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            ChartStorageProperties.HuaweiObs config = properties.getHuaweiObs();
            return obsClient.headBucket(config.getBucketName());
        } catch (Exception e) {
            log.warn("Failed to check Huawei OBS availability", e);
            return false;
        }
    }
}
