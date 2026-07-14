package com.foggyframework.dataset.mcp.storage.cloud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CloudStorageAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CloudStorageAutoConfiguration.class));

    @Test
    void noProviderIsCreatedWhenStorageTypeIsNotConfigured() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AliyunOssStorageAdapter.class);
            assertThat(context).doesNotHaveBean(HuaweiObsStorageAdapter.class);
            assertThat(context).doesNotHaveBean(TencentCosStorageAdapter.class);
            assertThat(context).doesNotHaveBean(AwsS3StorageAdapter.class);
        });
    }

    @Test
    void missingAliyunSdkSkipsSelectedProviderSafely() {
        assertMissingSdkSkipsProvider("aliyun-oss", "com.aliyun.oss", AliyunOssStorageAdapter.class);
    }

    @Test
    void missingHuaweiSdkSkipsSelectedProviderSafely() {
        assertMissingSdkSkipsProvider("huawei-obs", "com.obs.services", HuaweiObsStorageAdapter.class);
    }

    @Test
    void missingTencentSdkSkipsSelectedProviderSafely() {
        assertMissingSdkSkipsProvider("tencent-cos", "com.qcloud.cos", TencentCosStorageAdapter.class);
    }

    @Test
    void missingAwsSdkSkipsSelectedProviderSafely() {
        assertMissingSdkSkipsProvider("aws-s3", "software.amazon.awssdk", AwsS3StorageAdapter.class);
    }

    private void assertMissingSdkSkipsProvider(String type,
                                               String filteredPackage,
                                               Class<?> providerType) {
        contextRunner
                .withClassLoader(new FilteredClassLoader(filteredPackage))
                .withPropertyValues("foggy.chart.storage.type=" + type)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(providerType);
                });
    }
}
