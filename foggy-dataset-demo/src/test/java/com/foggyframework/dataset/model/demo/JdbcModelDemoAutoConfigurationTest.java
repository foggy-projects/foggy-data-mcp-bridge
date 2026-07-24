package com.foggyframework.dataset.model.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcModelDemoAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JdbcModelDemoAutoConfiguration.class));

    @Test
    void demoConfigurationShouldBeEnabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DemoSessionTokenService.class);
            assertThat(context).hasSingleBean(DemoAuthorizationService.class);
        });
    }

    @Test
    void demoConfigurationShouldBeDisabledByProperty() {
        contextRunner.withPropertyValues("foggy.demo.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DemoSessionTokenService.class);
                    assertThat(context).doesNotHaveBean(DemoAuthorizationService.class);
                });
    }

    @Test
    void sessionTokenShouldExposeStableDemoIdentity() {
        DemoSessionTokenService.SessionToken token =
                new DemoSessionTokenService().getSessionToken(new Object());

        assertThat(token.getUserId()).isEqualTo("user_001");
        assertThat(token.getUserName()).isEqualTo("张三");
        assertThat(token.getRole()).isEqualTo("MANAGER");
        assertThat(token.getStoreKey()).isEqualTo(1);
        assertThat(token.getTeamId()).isEqualTo("TEAM_001");
        assertThat(token.getRegionId()).isEqualTo("REGION_001");
    }

    @Test
    void authorizationContextShouldExposeStableDemoScope() {
        DemoAuthorizationService.UserContext user =
                new DemoAuthorizationService().getCurrentUserContext(new Object());

        assertThat(user.getUserId()).isEqualTo("user_001");
        assertThat(user.getUserName()).isEqualTo("张三");
        assertThat(user.getRole()).isEqualTo("MANAGER");
        assertThat(user.getStoreKey()).isEqualTo(1);
        assertThat(user.getTeamId()).isEqualTo("TEAM_001");
        assertThat(user.getRegionId()).isEqualTo("REGION_001");
        assertThat(user.getPermissions())
                .containsExactly("VIEW_SALES", "VIEW_CUSTOMER", "VIEW_STORE");
    }
}
