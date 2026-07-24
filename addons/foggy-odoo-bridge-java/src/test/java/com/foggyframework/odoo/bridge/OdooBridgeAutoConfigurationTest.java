package com.foggyframework.odoo.bridge;

import com.foggyframework.core.bundle.BundleDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class OdooBridgeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OdooBridgeAutoConfiguration.class));

    @Test
    void odooBridgeShouldBeEnabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("odoo-bundle");

            BundleDefinition bundle = context.getBean("odoo-bundle", BundleDefinition.class);
            assertThat(bundle.getName()).isEqualTo("odoo");
            assertThat(bundle.getNamespace()).isEqualTo("odoo");
            assertThat(bundle.getPackageName()).isEqualTo("com.foggyframework.odoo.bridge");
            assertThat(bundle.getDefinitionClass()).isEqualTo(OdooBridgeAutoConfiguration.class);
        });
    }

    @Test
    void odooBridgeShouldBeDisabledByProperty() {
        contextRunner.withPropertyValues("foggy.odoo.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("odoo-bundle");
                    assertThat(context).doesNotHaveBean(BundleDefinition.class);
                });
    }

    @Test
    void odooModelsAndQueryModelsShouldBePackaged() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        assertThat(resolver.getResources("classpath*:foggy/templates/odoo/model/*.tm"))
                .hasSize(17);
        assertThat(resolver.getResources("classpath*:foggy/templates/odoo/query/*.qm"))
                .hasSize(17);
        assertThat(resolver.getResource("classpath:foggy/templates/odoo/odoo17.fsscript").exists())
                .isTrue();
        assertThat(resolver.getResource("classpath:sql/refresh_closure_tables.sql").exists())
                .isTrue();
    }
}
