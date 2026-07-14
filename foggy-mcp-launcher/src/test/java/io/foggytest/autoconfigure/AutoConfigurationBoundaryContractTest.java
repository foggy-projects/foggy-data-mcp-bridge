package io.foggytest.autoconfigure;

import com.foggyframework.dataset.db.model.DbModelAutoConfiguration;
import com.foggyframework.dataset.mcp.DatasetMcpAutoConfiguration;
import com.foggyframework.mcp.launcher.McpLauncherApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import static org.assertj.core.api.Assertions.assertThat;

class AutoConfigurationBoundaryContractTest {

    @Test
    void launcherDoesNotScanTheFoggyFrameworkRootPackage() {
        SpringBootApplication annotation = McpLauncherApplication.class
                .getAnnotation(SpringBootApplication.class);

        assertThat(annotation.scanBasePackages()).isEmpty();
        assertThat(annotation.scanBasePackageClasses()).isEmpty();
    }

    @Test
    void modelAutoConfigurationDoesNotUseClasspathWideComponentScanning() {
        assertThat(DbModelAutoConfiguration.class.getAnnotation(ComponentScan.class)).isNull();
    }

    @Test
    void mcpAutoConfigurationDoesNotUseClasspathWideComponentScanning() {
        assertThat(DatasetMcpAutoConfiguration.class.getAnnotation(ComponentScan.class)).isNull();
    }
}
