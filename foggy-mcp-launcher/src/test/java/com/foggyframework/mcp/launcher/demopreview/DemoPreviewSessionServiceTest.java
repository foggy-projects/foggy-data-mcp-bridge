package com.foggyframework.mcp.launcher.demopreview;

import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.service.QueryCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DemoPreviewSessionServiceTest {

    private static final String MCP_AUTH = "Bearer demo-user-north";
    private static final String NAMESPACE = "harness_demo_logistics";
    private static final String MODEL = "DemoWaybillQueryModel";

    private DemoPreviewProperties properties;
    private MutableClock clock;
    private DemoPreviewSessionService service;
    private QueryCacheService queryCacheService;

    @BeforeEach
    void setUp() {
        properties = new DemoPreviewProperties();
        properties.setNamespace(NAMESPACE);
        properties.setModel(MODEL);
        properties.setPublicOpenUrl("http://127.0.0.1:18172/demo-preview/open");
        properties.setCredentialPrincipals(java.util.Map.of(
                DemoPreviewSessionService.fingerprint(MCP_AUTH), "north"));
        DemoPreviewProperties.DemoUser north = new DemoPreviewProperties.DemoUser();
        north.setDisplayName("北区运营");
        north.setPassword("north-local-password");
        north.setStationCodes(List.of("HZ-01", "NB-01"));
        DemoPreviewProperties.DemoUser south = new DemoPreviewProperties.DemoUser();
        south.setDisplayName("南区运营");
        south.setPassword("south-local-password");
        south.setStationCodes(List.of("SZ-01", "HF-01"));
        properties.setUsers(java.util.Map.of("north", north, "south", south));
        clock = new MutableClock(Instant.parse("2026-09-27T08:00:00Z"));
        queryCacheService = mock(QueryCacheService.class);
        when(queryCacheService.extendExpiry(eq("query-123"), eq(MODEL), eq(NAMESPACE), any(), any()))
                .thenReturn(true);
        service = new DemoPreviewSessionService(properties, queryCacheService, clock);
    }

    @Test
    void launchCodeIsFragmentOnlyOneTimeAndBoundToAuthenticatedUserAndQuery() {
        String url = service.createViewerUrl(query(), MCP_AUTH,
                "http://localhost:18172/data-viewer/view/" + MODEL + "/query-123");
        assertTrue(url.startsWith("http://127.0.0.1:18172/demo-preview/open#"));
        String code = url.substring(url.indexOf('#') + 1);

        assertThrows(IllegalArgumentException.class,
                () -> service.redeem(code, "south", "south-local-password"));
        DemoPreviewSessionService.RedeemedSession redeemed = service.redeem(code, "north", "north-local-password");
        assertEquals("/data-viewer/view/DemoWaybillQueryModel/query-123", redeemed.viewerUrl());
        assertFalse(url.contains(MCP_AUTH));
        assertThrows(IllegalArgumentException.class,
                () -> service.redeem(code, "north", "north-local-password"));

        String runtimeAuthorization = service.toRuntimeAuthorization(redeemed.token());
        DemoPreviewSessionService.PermissionGrant grant = service.resolveAuthorization(
                runtimeAuthorization, NAMESPACE, MODEL, "EXECUTE");
        assertTrue(grant.allow());
        assertEquals(List.of("HZ-01", "NB-01"), grant.stationCodes());
        assertFalse(service.resolveAuthorization(runtimeAuthorization, "another-ns", MODEL, "EXECUTE").allow());
        assertFalse(service.resolveAuthorization(runtimeAuthorization, NAMESPACE, MODEL, "DELETE").allow());

        assertTrue(service.allowsViewerRequest(redeemed.token(), "GET",
                "/data-viewer/api/query/" + MODEL + "/query-123/meta", NAMESPACE));
        assertFalse(service.allowsViewerRequest(redeemed.token(), "GET",
                "/data-viewer/api/query/" + MODEL + "/another-query/meta", NAMESPACE));
        assertFalse(service.allowsViewerRequest(redeemed.token(), "POST",
                "/data-viewer/api/query/" + MODEL + "/query-123/data", "another-ns"));
    }

    @Test
    void mcpPermissionAndBrowserSessionResolveToTheSameUserScope() {
        DemoPreviewSessionService.PermissionGrant mcpGrant = service.resolveAuthorization(
                MCP_AUTH, NAMESPACE, MODEL, "EXECUTE");
        String url = service.createViewerUrl(query(), MCP_AUTH,
                "http://127.0.0.1:18172/data-viewer/view/" + MODEL + "/query-123");
        String code = url.substring(url.indexOf('#') + 1);
        var redeemed = service.redeem(code, "north", "north-local-password");
        DemoPreviewSessionService.PermissionGrant viewerGrant = service.resolveAuthorization(
                service.toRuntimeAuthorization(redeemed.token()), NAMESPACE, MODEL, "EXECUTE");

        assertEquals(mcpGrant.stationCodes(), viewerGrant.stationCodes());
        assertEquals(List.of("HZ-01", "NB-01"), viewerGrant.stationCodes());
    }

    @Test
    void remappingMcpCredentialImmediatelyRevokesItsPreviewSession() {
        String url = service.createViewerUrl(query(), MCP_AUTH,
                "http://127.0.0.1:18172/data-viewer/view/" + MODEL + "/query-123");
        var redeemed = service.redeem(url.substring(url.indexOf('#') + 1),
                "north", "north-local-password");

        properties.setCredentialPrincipals(java.util.Map.of(
                DemoPreviewSessionService.fingerprint(MCP_AUTH), "south"));

        assertFalse(service.allowsViewerRequest(redeemed.token(), "GET",
                "/data-viewer/api/query/" + MODEL + "/query-123/meta", NAMESPACE));
        assertFalse(service.resolveAuthorization(service.toRuntimeAuthorization(redeemed.token()),
                NAMESPACE, MODEL, "EXECUTE").allow());
    }

    @Test
    void launchAndSessionExpireAndSessionCanBeRevoked() {
        String url = service.createViewerUrl(query(), MCP_AUTH,
                "http://127.0.0.1:18172/data-viewer/view/" + MODEL + "/query-123");
        String expiredCode = url.substring(url.indexOf('#') + 1);
        clock.advance(Duration.ofMinutes(121));
        assertThrows(IllegalArgumentException.class,
                () -> service.redeem(expiredCode, "north", "north-local-password"));

        clock.advance(Duration.ofMinutes(-121));
        String secondUrl = service.createViewerUrl(query(), MCP_AUTH,
                "http://127.0.0.1:18172/data-viewer/view/" + MODEL + "/query-123");
        var redeemed = service.redeem(secondUrl.substring(secondUrl.indexOf('#') + 1),
                "north", "north-local-password");
        assertNotNull(service.getSession(redeemed.token()));
        clock.advance(Duration.ofMinutes(16));
        assertNotNull(service.getSession(redeemed.token()));
        clock.advance(Duration.ofMinutes(105));
        assertNull(service.getSession(redeemed.token()));

        var second = service.redeem(
                service.createViewerUrl(query(), MCP_AUTH,
                        "http://127.0.0.1:18172/data-viewer/view/" + MODEL + "/query-123")
                        .substring("http://127.0.0.1:18172/demo-preview/open#".length()),
                "north", "north-local-password");
        assertTrue(service.revoke(second.token()));
        assertNull(service.getSession(second.token()));
    }

    @Test
    void lateRedemptionRenewsQueryForTwoHoursOfBrowsing() {
        var link = service.createViewerLink(query(), MCP_AUTH,
                "http://127.0.0.1:18172/data-viewer/view/" + MODEL + "/query-123");
        String url = link.url();
        assertEquals(clock.instant().plus(Duration.ofHours(2)), link.expiresAt());
        clock.advance(Duration.ofMinutes(90));
        Instant redeemedAt = clock.instant();
        var redeemed = service.redeem(url.substring(url.indexOf('#') + 1),
                "north", "north-local-password");

        verify(queryCacheService).extendExpiry("query-123", MODEL, NAMESPACE,
                redeemedAt, redeemedAt.plus(Duration.ofHours(2)));
        clock.advance(Duration.ofMinutes(110));
        assertNotNull(service.getSession(redeemed.token()));
        clock.advance(Duration.ofMinutes(11));
        assertNull(service.getSession(redeemed.token()));
    }

    @Test
    void openingAfterFiveMinutesStillStartsAFullTwoHourSession() {
        var link = service.createViewerLink(query(), MCP_AUTH,
                "http://127.0.0.1:18172/data-viewer/view/" + MODEL + "/query-123");
        clock.advance(Duration.ofMinutes(5));
        Instant openedAt = clock.instant();
        var redeemed = service.redeem(link.url().substring(link.url().indexOf('#') + 1),
                "north", "north-local-password");

        assertEquals(openedAt.plus(Duration.ofHours(2)), redeemed.session().expiresAt());
        verify(queryCacheService).extendExpiry("query-123", MODEL, NAMESPACE,
                openedAt, openedAt.plus(Duration.ofHours(2)));
        clock.advance(Duration.ofMinutes(119));
        assertNotNull(service.getSession(redeemed.token()));
        clock.advance(Duration.ofMinutes(2));
        assertNull(service.getSession(redeemed.token()));
    }

    @Test
    void expiredQueryCannotBeRedeemedIntoAnUnusableSession() {
        String url = service.createViewerUrl(query(), MCP_AUTH,
                "http://127.0.0.1:18172/data-viewer/view/" + MODEL + "/query-123");
        when(queryCacheService.extendExpiry(eq("query-123"), eq(MODEL), eq(NAMESPACE), any(), any()))
                .thenReturn(false);

        String code = url.substring(url.indexOf('#') + 1);
        assertThrows(IllegalArgumentException.class,
                () -> service.redeem(code, "north", "north-local-password"));
        assertFalse(service.hasActiveLaunch(code));
    }

    @Test
    void launchCannotOutliveItsInitialQueryContext() {
        CachedQueryContext shortLivedQuery = query();
        shortLivedQuery.setExpiresAt(clock.instant().plus(Duration.ofMinutes(30)));
        String url = service.createViewerUrl(shortLivedQuery, MCP_AUTH,
                "http://127.0.0.1:18172/data-viewer/view/" + MODEL + "/query-123");
        clock.advance(Duration.ofMinutes(31));

        assertThrows(IllegalArgumentException.class,
                () -> service.redeem(url.substring(url.indexOf('#') + 1),
                        "north", "north-local-password"));
    }

    @Test
    void rejectsRawUnknownCredentialsAndInvalidSessionLifetime() {
        assertFalse(service.resolveAuthorization("Bearer unknown", NAMESPACE, MODEL, "EXECUTE").allow());
        assertThrows(IllegalStateException.class, () -> service.createViewerUrl(query(), null,
                "http://127.0.0.1:18172/data-viewer/view/" + MODEL + "/query-123"));

        properties.setAbsoluteSessionTtl(Duration.ofHours(3));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoPreviewSessionService(properties, queryCacheService, clock));
    }

    private CachedQueryContext query() {
        return CachedQueryContext.builder().queryId("query-123").model(MODEL).namespace(NAMESPACE).build();
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        private void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
