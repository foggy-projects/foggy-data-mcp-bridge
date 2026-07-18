package com.foggyframework.dataset.mcp.storage.cloud;

import com.foggyframework.dataset.mcp.storage.ChartStorageException;
import com.foggyframework.dataset.mcp.storage.ChartStorageProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/**
 * AWS S3 存储适配器
 *
 * <p>配置示例：
 * <pre>
 * foggy:
 *   chart:
 *     storage:
 *       type: aws-s3
 *       aws-s3:
 *         region: us-east-1
 *         access-key-id: ${AWS_S3_ACCESS_KEY_ID}
 *         secret-access-key: ${AWS_S3_SECRET_ACCESS_KEY}
 *         bucket-name: your-bucket-name
 *         path-prefix: charts/
 *         custom-domain: https://cdn.example.com
 * </pre>
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "foggy.chart.storage.type", havingValue = "aws-s3")
@ConditionalOnClass(S3Client.class)
public class AwsS3StorageAdapter extends AbstractCloudStorageAdapter {

    private final ChartStorageProperties properties;
    private final S3Client s3Client;

    public AwsS3StorageAdapter(ChartStorageProperties properties) {
        this.properties = properties;

        ChartStorageProperties.AwsS3 config = properties.getAwsS3();

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                config.getAccessKeyId(),
                config.getSecretAccessKey()
        );

        this.s3Client = S3Client.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();

        log.info("AWS S3 client initialized: region={}, bucket={}",
                config.getRegion(), config.getBucketName());
    }

    @PreDestroy
    public void destroy() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

    @Override
    public String getType() {
        return "aws-s3";
    }

    @Override
    public String save(byte[] imageBytes, String format, String traceId) throws ChartStorageException {
        ChartStorageProperties.AwsS3 config = properties.getAwsS3();

        String objectKey = generateObjectKey(format, traceId, config.getPathPrefix());

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(objectKey)
                    .contentType("image/" + format)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(imageBytes));

            String url = buildS3AccessUrl(config, objectKey);

            log.info("Chart uploaded to AWS S3: {}", url);
            return url;

        } catch (Exception e) {
            throw new ChartStorageException("Failed to upload to AWS S3: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean delete(String url) {
        ChartStorageProperties.AwsS3 config = properties.getAwsS3();
        String objectKey = extractObjectKey(url, config.getPathPrefix());

        if (objectKey == null) {
            return false;
        }

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
            return true;
        } catch (Exception e) {
            log.warn("Failed to delete from AWS S3: {}", url, e);
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            ChartStorageProperties.AwsS3 config = properties.getAwsS3();
            HeadBucketRequest headRequest = HeadBucketRequest.builder()
                    .bucket(config.getBucketName())
                    .build();
            s3Client.headBucket(headRequest);
            return true;
        } catch (Exception e) {
            log.warn("Failed to check AWS S3 availability", e);
            return false;
        }
    }

    private String buildS3AccessUrl(ChartStorageProperties.AwsS3 config, String objectKey) {
        if (config.getCustomDomain() != null && !config.getCustomDomain().isEmpty()) {
            String domain = config.getCustomDomain().endsWith("/")
                    ? config.getCustomDomain().substring(0, config.getCustomDomain().length() - 1)
                    : config.getCustomDomain();
            return domain + "/" + objectKey;
        }

        // S3 默认域名格式：bucket.s3.region.amazonaws.com
        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                config.getBucketName(), config.getRegion(), objectKey);
    }
}
