package com.foggyframework.runtime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionPort;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.spi.TableModelLoaderManager;
import com.foggyframework.dataset.model.validation.DetachedModelValidationFactory;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.config.RuntimeApiAuthScope;
import com.foggyframework.runtime.api.security.RuntimeApiAuthInterceptor;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
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
                "foggy.runtime-api.authoring-workspaces.path=target/runtime-api-auth-test-workspaces-${random.uuid}",
                "spring.autoconfigure.exclude=com.foggyframework.dataset.model.DbModelAutoConfiguration,"
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
    private ComposeExecutionPort composeExecutionPort;

    @MockitoBean
    private DetachedModelValidationFactory detachedModelValidationFactory;

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
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url(RuntimeApiRoutes.Full.CAPABILITIES), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").path("securityMode").asText()).isEqualTo("auth-code");
    }

    @Test
    void shouldAllowAccessCheckOnlyWithValidAuthCodeAndDisableCaching() {
        ResponseEntity<JsonNode> rejected = restTemplate.getForEntity(
                url(RuntimeApiRoutes.Full.ACCESS_CHECK),
                JsonNode.class
        );

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rejected.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(rejected.getBody()).isNotNull();
        assertThat(rejected.getBody().path("error").path("code").asText())
                .isEqualTo("RUNTIME_AUTH_REQUIRED");

        ResponseEntity<JsonNode> accepted = restTemplate.exchange(
                url(RuntimeApiRoutes.Full.ACCESS_CHECK),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(RuntimeApiAuthInterceptor.AUTH_CODE_HEADER, "runtime-secret")),
                JsonNode.class
        );

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(accepted.getBody()).isNotNull();
        assertThat(accepted.getBody().path("data").path("authenticated").asBoolean()).isTrue();
        assertThat(accepted.getBody().path("data").path("authScope").asText()).isEqualTo("mutations");
        assertThat(accepted.getBody().toString()).doesNotContain("runtime-secret");
    }

    @Test
    void shouldRejectProtectedMutationWithoutAuthCode() {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url(RuntimeApiRoutes.Full.BUNDLES),
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
    void shouldExposeWorkspaceRoutesOnlyAfterManagementAuthentication() {
        ResponseEntity<JsonNode> rejected = restTemplate.getForEntity(
                url(RuntimeApiRoutes.Full.AUTHORING_WORKSPACES),
                JsonNode.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<JsonNode> listed = restTemplate.exchange(
                url(RuntimeApiRoutes.Full.AUTHORING_WORKSPACES),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(
                        RuntimeApiAuthInterceptor.AUTH_CODE_HEADER,
                        "runtime-secret")),
                JsonNode.class);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody()).isNotNull();
        assertThat(listed.getBody().path("success").asBoolean()).isTrue();
        assertThat(listed.getBody().path("data").path("workspaces"))
                .isEmpty();

        ResponseEntity<JsonNode> invalidCreate = restTemplate.exchange(
                url(RuntimeApiRoutes.Full.AUTHORING_WORKSPACES),
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), authHeaders(
                        RuntimeApiAuthInterceptor.AUTH_CODE_HEADER,
                        "runtime-secret")),
                JsonNode.class);
        assertThat(invalidCreate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(invalidCreate.getBody().path("success").asBoolean())
                .isFalse();
        assertThat(invalidCreate.getBody().path("error").path("code").asText())
                .isEqualTo("WORKSPACE_INVALID_REQUEST");
    }

    @Test
    void shouldRejectDatasourceTestWithoutAuthCode() {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url(route(RuntimeApiRoutes.Full.DATASOURCE_TEST, "name", "demo")),
                Map.of(),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
        assertThat(response.getBody().path("error").path("code").asText()).isEqualTo("RUNTIME_AUTH_REQUIRED");
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
                url(RuntimeApiRoutes.Full.BUNDLES),
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
                url(RuntimeApiRoutes.Full.BUNDLES),
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
    void shouldNotTreatBusinessAuthorizationAsManagementAuthCode() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url(RuntimeApiRoutes.Full.BUNDLES),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("name", "runtime-bearer-demo", "path", ".", "namespace", "dev"),
                        authHeaders(HttpHeaders.AUTHORIZATION, "Bearer runtime-secret")
                ),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
        assertThat(response.getBody().path("error").path("code").asText()).isEqualTo("RUNTIME_AUTH_REQUIRED");
        verify(systemBundlesContext, never())
                .addExternalBundle(eq("runtime-bearer-demo"), eq("dev"), eq("."), anyBoolean());
    }

    @Test
    void shouldFailClosedWhenAuthCodeModeHasNoCode() throws Exception {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.setSecurityMode("auth-code");
        RuntimeApiAuthInterceptor interceptor = new RuntimeApiAuthInterceptor(
                properties,
                new RuntimeApiResponseFactory(properties),
                new ObjectMapper()
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", RuntimeApiRoutes.Full.BUNDLES);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("RUNTIME_AUTH_CODE_NOT_CONFIGURED");
    }

    @Test
    void shouldRequireAuthCodeForRuntimeManagementOperationInventory() throws Exception {
        assertRejectedByInterceptor("POST", RuntimeApiRoutes.Full.BUNDLES);
        assertRejectedByInterceptor("PUT", route(RuntimeApiRoutes.Full.BUNDLE_BY_NAME, "name", "demo"));
        assertRejectedByInterceptor("DELETE", route(RuntimeApiRoutes.Full.BUNDLE_BY_NAME, "name", "demo"));
        assertRejectedByInterceptor("POST", RuntimeApiRoutes.Full.DATASOURCES);
        assertRejectedByInterceptor("PUT", route(RuntimeApiRoutes.Full.DATASOURCE_BY_NAME, "name", "demo"));
        assertRejectedByInterceptor("DELETE", route(RuntimeApiRoutes.Full.DATASOURCE_BY_NAME, "name", "demo"));
        assertRejectedByInterceptor("POST", route(RuntimeApiRoutes.Full.DATASOURCE_TEST, "name", "demo"));
        assertRejectedByInterceptor("PUT", route(RuntimeApiRoutes.Full.NAMESPACE_DATASOURCE, "namespace", "dev"));
        assertRejectedByInterceptor("POST", RuntimeApiRoutes.Full.RESOURCES_SAVE);
        assertRejectedByInterceptor("POST", RuntimeApiRoutes.Full.MODELS_VALIDATE);
        assertRejectedByInterceptor("POST", RuntimeApiRoutes.Full.MODELS_REFRESH);
        assertRejectedByInterceptor("POST", RuntimeApiRoutes.Full.FSSCRIPT_EXECUTE);
        assertRejectedByInterceptor("POST", RuntimeApiRoutes.Full.LEGACY_BUNDLE_ADD);
        assertRejectedByInterceptor("DELETE", route(RuntimeApiRoutes.Full.LEGACY_BUNDLE_REMOVE, "bundleName", "demo"));
        assertWorkspaceOperationsRejectedByInterceptor(
                authInterceptor("runtime-secret"));
    }

    @Test
    void shouldFailClosedForEveryWorkspaceRouteEvenWhenGlobalAuthIsDisabled()
            throws Exception {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        RuntimeApiAuthInterceptor interceptor = new RuntimeApiAuthInterceptor(
                properties,
                new RuntimeApiResponseFactory(properties),
                new ObjectMapper());
        String path = RuntimeApiRoutes.Full.AUTHORING_WORKSPACES;
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer business-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        JsonNode body = new ObjectMapper().readTree(
                response.getContentAsString());
        assertThat(body.path("error").path("code").asText())
                .isEqualTo("RUNTIME_AUTH_CODE_NOT_CONFIGURED");
        assertThat(body.toString()).doesNotContain("business-token");
    }

    @Test
    void shouldRequireManagementHeaderForAllWorkspaceMethodsAndIgnoreAuthorization()
            throws Exception {
        RuntimeApiAuthInterceptor interceptor = authInterceptor("runtime-secret");
        assertWorkspaceOperationsRejectedByInterceptor(interceptor);

        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", route(RuntimeApiRoutes.Full.AUTHORING_QUERY_EXECUTE,
                "workspaceId", "workspace-1")
                .replace("{model}", "Order"));
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer runtime-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object()))
                .isFalse();
        assertThat(response.getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void shouldLeaveReadAndExecutionEndpointsOutsideManagementAuthGate() throws Exception {
        assertAllowedByInterceptor("GET", RuntimeApiRoutes.Full.CAPABILITIES);
        assertAllowedByInterceptor("GET", RuntimeApiRoutes.Full.BUNDLES);
        assertAllowedByInterceptor("GET", RuntimeApiRoutes.Full.DATASOURCES);
        assertAllowedByInterceptor("GET", RuntimeApiRoutes.Full.DATASOURCES_DIAGNOSTICS);
        assertAllowedByInterceptor("GET", route(RuntimeApiRoutes.Full.NAMESPACE_DATASOURCE, "namespace", "dev"));
        assertAllowedByInterceptor("GET", RuntimeApiRoutes.Full.MODELS);
        assertAllowedByInterceptor("POST", route(RuntimeApiRoutes.Full.MODEL_DESCRIBE, "model", "Order"));
        assertAllowedByInterceptor("POST", RuntimeApiRoutes.Full.RESOURCES_EXPORT);
        assertAllowedByInterceptor("POST", route(RuntimeApiRoutes.Full.QUERY_VALIDATE, "model", "Order"));
        assertAllowedByInterceptor("POST", route(RuntimeApiRoutes.Full.QUERY_EXECUTE, "model", "Order"));
        assertAllowedByInterceptor("POST", RuntimeApiRoutes.Full.TABLES_LIST);
        assertAllowedByInterceptor("POST", RuntimeApiRoutes.Full.TABLES_INSPECT);
        assertAllowedByInterceptor("POST", RuntimeApiRoutes.Full.SQL_QUERY);
        assertAllowedByInterceptor("POST", RuntimeApiRoutes.Full.COMPOSE_VALIDATE);
        assertAllowedByInterceptor("POST", RuntimeApiRoutes.Full.COMPOSE_PREVIEW);
        assertAllowedByInterceptor("POST", RuntimeApiRoutes.Full.COMPOSE_EXECUTE);
    }

    @Test
    void shouldRequireAuthCodeForEveryV1OperationInManagementAllScope() throws Exception {
        String[][] operations = {
                {"GET", RuntimeApiRoutes.Full.CAPABILITIES},
                {"GET", RuntimeApiRoutes.Full.ACCESS_CHECK},
                {"GET", RuntimeApiRoutes.Full.BUNDLES},
                {"POST", RuntimeApiRoutes.Full.BUNDLES},
                {"PUT", route(RuntimeApiRoutes.Full.BUNDLE_BY_NAME, "name", "demo")},
                {"DELETE", route(RuntimeApiRoutes.Full.BUNDLE_BY_NAME, "name", "demo")},
                {"GET", RuntimeApiRoutes.Full.DATASOURCES},
                {"GET", RuntimeApiRoutes.Full.DATASOURCES_DIAGNOSTICS},
                {"POST", RuntimeApiRoutes.Full.DATASOURCES},
                {"PUT", route(RuntimeApiRoutes.Full.DATASOURCE_BY_NAME, "name", "demo")},
                {"DELETE", route(RuntimeApiRoutes.Full.DATASOURCE_BY_NAME, "name", "demo")},
                {"POST", route(RuntimeApiRoutes.Full.DATASOURCE_TEST, "name", "demo")},
                {"GET", route(RuntimeApiRoutes.Full.NAMESPACE_DATASOURCE, "namespace", "dev")},
                {"PUT", route(RuntimeApiRoutes.Full.NAMESPACE_DATASOURCE, "namespace", "dev")},
                {"POST", RuntimeApiRoutes.Full.RESOURCES_EXPORT},
                {"POST", RuntimeApiRoutes.Full.RESOURCES_SAVE},
                {"GET", RuntimeApiRoutes.Full.MODELS},
                {"POST", route(RuntimeApiRoutes.Full.MODEL_DESCRIBE, "model", "Order")},
                {"POST", RuntimeApiRoutes.Full.MODELS_VALIDATE},
                {"POST", RuntimeApiRoutes.Full.MODELS_REFRESH},
                {"POST", route(RuntimeApiRoutes.Full.QUERY_VALIDATE, "model", "Order")},
                {"POST", route(RuntimeApiRoutes.Full.QUERY_EXECUTE, "model", "Order")},
                {"POST", RuntimeApiRoutes.Full.TABLES_LIST},
                {"POST", RuntimeApiRoutes.Full.TABLES_INSPECT},
                {"POST", RuntimeApiRoutes.Full.SQL_QUERY},
                {"POST", RuntimeApiRoutes.Full.COMPOSE_VALIDATE},
                {"POST", RuntimeApiRoutes.Full.COMPOSE_PREVIEW},
                {"POST", RuntimeApiRoutes.Full.COMPOSE_EXECUTE},
                {"POST", RuntimeApiRoutes.Full.FSSCRIPT_EXECUTE}
        };

        RuntimeApiAuthInterceptor interceptor = authInterceptor(
                "runtime-secret",
                RuntimeApiAuthScope.MANAGEMENT_ALL
        );
        for (String[] operation : operations) {
            assertRejectedByInterceptor(interceptor, operation[0], operation[1]);
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static String route(String template, String variable, String value) {
        return template.replace("{" + variable + "}", value);
    }

    private static HttpHeaders authHeaders(String headerName, String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(headerName, value);
        return headers;
    }

    private static void assertRejectedByInterceptor(String method, String path) throws Exception {
        assertRejectedByInterceptor(authInterceptor("runtime-secret"), method, path);
    }

    private static void assertRejectedByInterceptor(
            RuntimeApiAuthInterceptor interceptor,
            String method,
            String path
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).as(method + " " + path).isFalse();
        assertThat(response.getStatus()).as(method + " " + path).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.path("error").path("code").asText()).as(method + " " + path).isEqualTo("RUNTIME_AUTH_REQUIRED");
    }

    private static void assertAllowedByInterceptor(String method, String path) throws Exception {
        RuntimeApiAuthInterceptor interceptor = authInterceptor("runtime-secret");
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).as(method + " " + path).isTrue();
        assertThat(response.getStatus()).as(method + " " + path).isEqualTo(200);
    }

    private static void assertWorkspaceOperationsRejectedByInterceptor(
            RuntimeApiAuthInterceptor interceptor
    ) throws Exception {
        String workspace = route(RuntimeApiRoutes.Full.AUTHORING_WORKSPACE,
                "workspaceId", "workspace-1");
        String resources = route(RuntimeApiRoutes.Full.AUTHORING_RESOURCES,
                "workspaceId", "workspace-1");
        String content = route(
                RuntimeApiRoutes.Full.AUTHORING_RESOURCE_CONTENT,
                "workspaceId", "workspace-1");
        String save = route(RuntimeApiRoutes.Full.AUTHORING_RESOURCES_SAVE,
                "workspaceId", "workspace-1");
        String delete = route(RuntimeApiRoutes.Full.AUTHORING_RESOURCES_DELETE,
                "workspaceId", "workspace-1");
        String diff = route(RuntimeApiRoutes.Full.AUTHORING_DIFF,
                "workspaceId", "workspace-1");
        String validate = route(RuntimeApiRoutes.Full.AUTHORING_VALIDATE,
                "workspaceId", "workspace-1");
        String queryValidate = route(
                RuntimeApiRoutes.Full.AUTHORING_QUERY_VALIDATE,
                "workspaceId", "workspace-1").replace("{model}", "Order");
        String queryExecute = route(
                RuntimeApiRoutes.Full.AUTHORING_QUERY_EXECUTE,
                "workspaceId", "workspace-1").replace("{model}", "Order");
        for (String[] operation : new String[][]{
                {"POST", RuntimeApiRoutes.Full.AUTHORING_WORKSPACES},
                {"GET", RuntimeApiRoutes.Full.AUTHORING_WORKSPACES},
                {"GET", workspace}, {"DELETE", workspace},
                {"GET", resources}, {"GET", content},
                {"POST", save}, {"POST", delete}, {"POST", diff},
                {"POST", validate}, {"POST", queryValidate},
                {"POST", queryExecute}
        }) {
            assertRejectedByInterceptor(
                    interceptor, operation[0], operation[1]);
        }
    }

    private static RuntimeApiAuthInterceptor authInterceptor(String authCode) {
        return authInterceptor(authCode, RuntimeApiAuthScope.MUTATIONS);
    }

    private static RuntimeApiAuthInterceptor authInterceptor(
            String authCode,
            RuntimeApiAuthScope authScope
    ) {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.setAuthCode(authCode);
        properties.setAuthScope(authScope);
        return new RuntimeApiAuthInterceptor(
                properties,
                new RuntimeApiResponseFactory(properties),
                new ObjectMapper()
        );
    }

    @SpringBootApplication(scanBasePackages = "com.foggyframework.runtime.api")
    static class TestApplication {
    }
}
