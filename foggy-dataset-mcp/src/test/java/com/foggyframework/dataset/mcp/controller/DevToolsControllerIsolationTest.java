package com.foggyframework.dataset.mcp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevToolsControllerIsolationTest {

    private final DataSource primaryDataSource = mock(DataSource.class);

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withBean(DataSource.class, () -> primaryDataSource)
            .withUserConfiguration(DevToolsControllerConfiguration.class);

    @Test
    void shouldBeDisabledByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(DevToolsController.class));
    }

    @Test
    void shouldNotExposeDevRouteByDefault() {
        contextRunner.run(context -> MockMvcBuilders.webAppContextSetup(context)
                .build()
                .perform(get("/dev/tables"))
                .andExpect(status().isNotFound()));
    }

    @Test
    void shouldRequireExplicitEnablement() {
        contextRunner
                .withPropertyValues("foggy.dev-tools.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DevToolsController.class));
    }

    @Test
    void shouldRejectUnknownNamedDataSourceWithoutFallingBackToPrimary() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean("missing", DataSource.class))
                .thenThrow(new org.springframework.beans.factory.NoSuchBeanDefinitionException("missing"));
        DevToolsController controller = new DevToolsController(primaryDataSource, applicationContext);

        var response = controller.listTables(null, "missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "DataSource not found: missing");
        verify(applicationContext).getBean("missing", DataSource.class);
        verifyNoInteractions(primaryDataSource);
    }

    @Test
    void shouldRejectBeanWithWrongTypeWithoutFallingBackToPrimary() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean("not-a-datasource", DataSource.class))
                .thenThrow(new org.springframework.beans.factory.BeanNotOfRequiredTypeException(
                        "not-a-datasource", DataSource.class, String.class));
        DevToolsController controller = new DevToolsController(primaryDataSource, applicationContext);

        var response = controller.listTables(null, "not-a-datasource");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry(
                "message", "DataSource not found: not-a-datasource");
        verify(applicationContext).getBean("not-a-datasource", DataSource.class);
        verifyNoInteractions(primaryDataSource);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(DevToolsController.class)
    static class DevToolsControllerConfiguration {
    }
}
