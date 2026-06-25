package com.foggyframework.runtime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.security.RuntimeApiAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = RuntimeApiAuthCodeGateTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "foggy.runtime-api.enabled=true",
                "foggy.runtime-api.auth-code=runtime-secret",
                "foggy.runtime-api.bundle-registry.path=target/runtime-api-auth-test-bundles-${random.uuid}.json",
                "foggy.runtime-api.datasource-registry.path=target/runtime-api-auth-test-datasources-${random.uuid}.json",
                "spring.autoconfigure.exclude=com.foggyframework.dataset.db.model.DbModelAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
        }
)
class RuntimeApiAuthCodeGateTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private SemanticModelCatalogService catalogService;

    @MockitoBean
    private SemanticServiceV3 semanticServiceV3;

    @MockitoBean
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @MockitoBean
    private SystemBundlesContext systemBundlesContext;

    @MockitoBean
    private QueryModelLoader queryModelLoader;

    @MockitoBean
    private TableModelLoaderManager tableModelLoaderManager;

    @MockitoBean
    private DataSource dataSource;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void shouldExposeAuthCodeEffectiveSecurityModeWithoutAuthHeader() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url("/api/v1/capabilities"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").path("securityMode").asText()).isEqualTo("auth-code");
    }

    @Test
    void shouldRejectProtectedMutationWithoutAuthCode() {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url("/api/v1/bundles"),
                Map.of("name", "runtime-auth-demo", "path", ".", "namespace", "dev"),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
        assertThat(response.getBody().path("error").path("code").asText()).isEqualTo("RUNTIME_AUTH_REQUIRED");
        verify(systemBundlesContext, never()).addExternalBundle(eq("runtime-auth-demo"), eq("dev"), eq("."), anyBoolean());
    }

    @Test
    void shouldRejectProtectedMutationWithWrongAuthCodeBeforeController() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(RuntimeApiAuthInterceptor.AUTH_CODE_HEADER, "wrong-secret");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                Map.of("name", "runtime-auth-demo", "path", ".", "namespace", "dev"),
                headers
        );

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url("/api/v1/bundles"),
                HttpMethod.POST,
                entity,
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
        assertThat(response.getBody().path("error").path("code").asText()).isEqualTo("RUNTIME_AUTH_REQUIRED");
        verify(systemBundlesContext, never()).addExternalBundle(eq("runtime-auth-demo"), eq("dev"), eq("."), anyBoolean());
    }

    @Test
    void shouldAllowProtectedMutationWithAuthCodeHeader() {
        when(systemBundlesContext.containBundle("runtime-auth-demo")).thenReturn(false);
        when(systemBundlesContext.addExternalBundle("runtime-auth-demo", "dev", ".", false)).thenReturn(true);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url("/api/v1/bundles"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("name", "runtime-auth-demo", "path", ".", "namespace", "dev"),
                        authHeaders(RuntimeApiAuthInterceptor.AUTH_CODE_HEADER, "runtime-secret")
                ),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        verify(systemBundlesContext).addExternalBundle("runtime-auth-demo", "dev", ".", false);
    }

    @Test
    void shouldAllowProtectedMutationWithBearerAuthCode() {
        when(systemBundlesContext.containBundle("runtime-bearer-demo")).thenReturn(false);
        when(systemBundlesContext.addExternalBundle("runtime-bearer-demo", "dev", ".", false)).thenReturn(true);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url("/api/v1/bundles"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("name", "runtime-bearer-demo", "path", ".", "namespace", "dev"),
                        authHeaders(HttpHeaders.AUTHORIZATION, "Bearer runtime-secret")
                ),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        verify(systemBundlesContext).addExternalBundle("runtime-bearer-demo", "dev", ".", false);
    }

    @Test
    void shouldFailClosedWhenAuthCodeModeHasNoCode() throws Exception {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.setSecurityMode("auth-code");
        RuntimeApiAuthInterceptor interceptor = new RuntimeApiAuthInterceptor(properties, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/bundles");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("RUNTIME_AUTH_CODE_NOT_CONFIGURED");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static HttpHeaders authHeaders(String headerName, String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(headerName, value);
        return headers;
    }

    @SpringBootApplication(scanBasePackages = "com.foggyframework.runtime.api")
    static class TestApplication {
    }
}
