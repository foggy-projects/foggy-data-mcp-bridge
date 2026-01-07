package com.foggyframework.dataset.mcp.storage.cloud;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ComponentScan;

/**
 * 云存储自动配置
 * <p>
 * 引入此模块后，根据配置的 foggy.chart.storage.type 自动启用对应的云存储适配器。
 * </p>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "com.foggyframework.dataset.mcp.storage.ChartStorageAdapter")
@ComponentScan(basePackageClasses = CloudStorageAutoConfiguration.class)
public class CloudStorageAutoConfiguration {

    public CloudStorageAutoConfiguration() {
        log.info("Cloud storage adapters registered: aliyun-oss, huawei-obs, tencent-cos, aws-s3");
    }
}
