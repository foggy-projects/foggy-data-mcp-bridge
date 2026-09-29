package com.foggyframework.mcp.launcher.demopreview;

import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.service.QueryCacheService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DemoPreviewAuthorizationFilterTest {

    private static final String AUTH = "Bearer demo-mcp-key";
    private static final String MODEL = "DemoWaybillQueryModel";
    private static final String NAMESPACE = "harness_demo_logistics";

    private DemoPreviewProperties properties;
    private DemoPreviewSessionService sessions;
    private DemoPreviewAuthorizationFilter filter;
    private String sessionToken;

    @BeforeEach
    void setUp() {
        properties = new DemoPreviewProperties();
        properties.setModel(MODEL);
        properties.setNamespace(NAMESPACE);
        properties.setCredentialPrincipals(Map.of(DemoPreviewSessionService.fingerprint(AUTH), "coordinator"));
        DemoPreviewProperties.DemoUser user = new DemoPreviewProperties.DemoUser();
        user.setPassword("local-password");
        user.setStationCodes(List.of("HZ-01", "SZ-01", "NB-01", "HF-01"));
        properties.setUsers(Map.of("coordinator", user));
        QueryCacheService queryCacheService = mock(QueryCacheService.class);
        when(queryCacheService.extendExpiry(any(), any(), any(), any(), any())).thenReturn(true);
        sessions = new DemoPreviewSessionService(properties, queryCacheService);
        filter = new DemoPreviewAuthorizationFilter(sessions, properties);
        String viewerUrl = sessions.createViewerUrl(
                CachedQueryContext.builder().queryId("query-1").model(MODEL).namespace(NAMESPACE).build(),
                AUTH, "http://127.0.0.1:18172/data-viewer/view/" + MODEL + "/query-1");
        sessionToken = sessions.redeem(viewerUrl.substring(viewerUrl.indexOf('#') + 1),
                "coordinator", "local-password").token();
    }

    @Test
    void viewerApiRejectsRequestsWithoutSessionEvenWhenCallerSendsMcpCredential() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/data-viewer/api/query/" + MODEL + "/query-1/meta");
        request.addHeader("Authorization", AUTH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> authorizationSeenByDownstream = new AtomicReference<>();

        filter.doFilter(request, response, (wrapped, ignored) ->
                authorizationSeenByDownstream.set(((jakarta.servlet.http.HttpServletRequest) wrapped)
                        .getHeader("Authorization")));

        assertEquals(401, response.getStatus());
        assertNull(authorizationSeenByDownstream.get());
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test
    void validSessionBecomesScopedRuntimeIdentityAndOverridesNamespaceHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/data-viewer/api/query/" + MODEL + "/query-1/meta");
        request.setCookies(new Cookie(properties.getCookieName(), sessionToken));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> authorizationSeenByDownstream = new AtomicReference<>();
        AtomicReference<String> namespaceSeenByDownstream = new AtomicReference<>();

        filter.doFilter(request, response, (wrapped, ignored) -> {
            var scopedRequest = (jakarta.servlet.http.HttpServletRequest) wrapped;
            authorizationSeenByDownstream.set(scopedRequest.getHeader("Authorization"));
            namespaceSeenByDownstream.set(scopedRequest.getHeader("X-NS"));
        });

        assertEquals(DemoPreviewSessionService.RUNTIME_AUTH_SCHEME + sessionToken,
                authorizationSeenByDownstream.get());
        assertEquals(NAMESPACE, namespaceSeenByDownstream.get());
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test
    void validSessionRejectsCallerSelectedDifferentNamespace() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/data-viewer/api/query/" + MODEL + "/query-1/meta");
        request.setCookies(new Cookie(properties.getCookieName(), sessionToken));
        request.addHeader("X-NS", "another-namespace");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                fail("a mismatched namespace must not reach the DataViewer controller"));

        assertEquals(401, response.getStatus());
    }
}
