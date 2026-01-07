package com.foggyframework.dataset.mcp.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 图表存储配置属性
 */
@Data
@ConfigurationProperties(prefix = "foggy.chart.storage")
public class ChartStorageProperties {

    /**
     * 存储类型：local, aliyun-oss, tencent-cos, huawei-obs, aws-s3
     */
    private String type = "local";

    /**
     * 本地存储配置
     */
    private Local local = new Local();

    /**
     * 阿里云 OSS 配置
     */
    private AliyunOss aliyunOss = new AliyunOss();

    /**
     * 腾讯云 COS 配置
     */
    private TencentCos tencentCos = new TencentCos();

    /**
     * 华为云 OBS 配置
     */
    private HuaweiObs huaweiObs = new HuaweiObs();

    /**
     * AWS S3 配置
     */
    private AwsS3 awsS3 = new AwsS3();

    @Data
    public static class Local {
        /**
         * 本地存储目录
         */
        private String directory = "./chart-images";

        /**
         * 文件保留时间，默认 14 天
         */
        private Duration retention = Duration.ofDays(14);

        /**
         * 清理任务 cron 表达式，默认每天凌晨 3 点执行
         */
        private String cleanupCron = "0 0 3 * * ?";

        /**
         * 访问 URL 前缀（如果不设置，会自动根据服务地址生成）
         */
        private String urlPrefix;
    }

    @Data
    public static class AliyunOss {
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String bucketName;
        /**
         * 存储路径前缀
         */
        private String pathPrefix = "charts/";
        /**
         * 自定义域名（CDN 加速域名）
         */
        private String customDomain;
    }

    @Data
    public static class TencentCos {
        private String region;
        private String secretId;
        private String secretKey;
        private String bucketName;
        private String pathPrefix = "charts/";
        private String customDomain;
    }

    @Data
    public static class HuaweiObs {
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String bucketName;
        private String pathPrefix = "charts/";
        private String customDomain;
    }

    @Data
    public static class AwsS3 {
        private String region;
        private String accessKeyId;
        private String secretAccessKey;
        private String bucketName;
        private String pathPrefix = "charts/";
        private String customDomain;
    }
}
