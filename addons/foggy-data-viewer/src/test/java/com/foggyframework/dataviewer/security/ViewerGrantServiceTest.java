package com.foggyframework.dataviewer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.service.*;
import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.*;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.Cookie;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ViewerGrantServiceTest {
    @TempDir Path dir;
    ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    MutableClock clock=new MutableClock();
    DataViewerProperties props=new DataViewerProperties();
    QueryFacade facade=mock(QueryFacade.class);
    boolean enabled=true;
    String version="v1";
    ViewerIdentityProvider identities=new ViewerIdentityProvider() {
        public Identity verify(String auth,CachedQueryContext q) {
            if (!"Bearer synthetic-test-only".equals(auth) || !enabled || !"demo".equals(q.getNamespace()) || !"Waybills".equals(q.getModel())) throw ViewerGrantService.denied();
            return new Identity("vault-reference",version);
        }
        public void revalidate(Identity identity,CachedQueryContext q) {
            if (!enabled || !new Identity("vault-reference",version).equals(identity)) throw ViewerGrantService.denied();
        }
        public String runtimeAuthorization(Identity identity) { return "Runtime service reference"; }
    };
    QueryCacheService queries;
    ViewerGrantService service;
    CachedQueryContext query;
    @BeforeEach void setUp() {
        queries=new QueryCacheService(new SqliteQueryContextStore(dir.resolve("contexts.sqlite"),mapper,1000),props);
        service=reopen();
        var request=new QueryCacheService.OpenInViewerRequest(); request.setModel("Waybills"); request.setNamespace("demo");
        request.setColumns(List.of("station","tickets")); request.setSlice(List.of(new SliceRequestDef("businessDate","=","2026-09-26")));
        request.setHaving(List.of(new SliceRequestDef("tickets",">=",1)));
        query=queries.cacheQuery(request,null);
    }
    ViewerGrantService reopen() {
        return new ViewerGrantService(new SqliteViewerGrantStore(dir.resolve("auth.sqlite"),mapper),identities,queries,facade,props.getLinkAuthorization(),clock);
    }
    String link() { return service.createViewerLink(query,"Bearer synthetic-test-only","http://localhost:18172/data-viewer/view/Waybills/"+query.getQueryId()).url().split("#")[1]; }
    @Test void permanentReusableLinkAnd24HourSessionSurviveRestartAndCacheCleanup() throws Exception {
        String secret=link(); var first=service.redeem(secret); var second=service.redeem(secret);
        assertNotEquals(first.token(),second.token()); assertEquals(Duration.ofHours(24),Duration.between(first.issuedAt(),first.expiresAt()));
        clock.advance(Duration.ofHours(23)); service=reopen();
        var restored=service.access(first.token(),query.getQueryId()); assertNull(restored.query().getExpiresAt());
        assertEquals("businessDate",restored.query().getSlice().get(0).getField()); assertEquals("tickets",restored.query().getHaving().get(0).getField());
        clock.advance(Duration.ofHours(1)); assertThrows(SecurityException.class,()->service.access(first.token(),query.getQueryId()));
        assertNotNull(service.redeem(secret)); clock.advance(Duration.ofDays(365));
        queries=new QueryCacheService(new SqliteQueryContextStore(dir.resolve("contexts.sqlite"),mapper,1000),props);
        service=reopen(); assertNotNull(service.redeem(secret));
        String database=Files.readString(dir.resolve("auth.sqlite"),java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(database.contains(secret)); assertFalse(database.contains(first.token())); assertFalse(database.contains("synthetic-test-only"));
    }
    @Test void finiteLinkStopsAllSessionsAndPersistsAfterRestart() {
        props.getLinkAuthorization().setLinkTtl(Duration.ofMinutes(5)); service=reopen();
        String secret=link(); var session=service.redeem(secret); clock.advance(Duration.ofMinutes(5)); service=reopen();
        assertThrows(SecurityException.class,()->service.access(session.token(),query.getQueryId()));
        assertThrows(SecurityException.class,()->service.redeem(secret));
    }
    @Test void customSessionRenewsFromValidLinkWithoutIdleTimeout() {
        props.getLinkAuthorization().setSessionTtl(Duration.ofHours(48)); service=reopen();
        String secret=link(); var session=service.redeem(secret); clock.advance(Duration.ofHours(47));
        assertNotNull(service.access(session.token(),query.getQueryId())); clock.advance(Duration.ofHours(1));
        assertThrows(SecurityException.class,()->service.access(session.token(),query.getQueryId())); assertNotNull(service.redeem(secret));
    }
    @Test void revocationRequiresMcpOwnerAndStopsExistingSessionsAfterRestart() {
        String secret=link(); var session=service.redeem(secret);
        assertThrows(SecurityException.class,()->service.revoke(secret,"another credential"));
        assertTrue(service.revoke(secret,"Bearer synthetic-test-only")); service=reopen();
        assertThrows(SecurityException.class,()->service.access(session.token(),query.getQueryId())); assertThrows(SecurityException.class,()->service.redeem(secret));
    }
    @Test void invalidIdentityAndPermissionChangeStopLinksAndSessions() {
        String secret=link(); var session=service.redeem(secret); enabled=false;
        assertThrows(SecurityException.class,()->service.access(session.token(),query.getQueryId())); assertThrows(SecurityException.class,()->service.redeem(secret));
        enabled=true; version="v2";
        assertThrows(SecurityException.class,()->service.access(session.token(),query.getQueryId())); assertThrows(SecurityException.class,()->service.redeem(secret));
    }
    @Test void rejectsCrossNamespaceModelAndQueriesDeniedByEngine() {
        query.setNamespace("other"); assertThrows(SecurityException.class,this::link); query.setNamespace("demo");
        query.setModel("other"); assertThrows(SecurityException.class,this::link); query.setModel("Waybills");
        doThrow(new SecurityException("engine denied fields or rows")).when(facade).query(any()); assertThrows(SecurityException.class,this::link);
    }
    @Test void invalidDurationsRejected() {
        props.getLinkAuthorization().setLinkTtl(Duration.ofSeconds(-1)); assertThrows(IllegalArgumentException.class,this::reopen);
        props.getLinkAuthorization().setLinkTtl(Duration.ZERO); props.getLinkAuthorization().setSessionTtl(Duration.ZERO);
        assertThrows(IllegalArgumentException.class,this::reopen);
    }
    @Test void filterBindsCookieAndRejectsNamespaceModelFieldsExpressionsAndParameters() throws Exception {
        String secret=link(); var session=service.redeem(secret); var filter=new ViewerSessionFilter(service,mapper);
        String root="/data-viewer/api/query/Waybills/"+query.getQueryId();
        for (String body:List.of("{\"namespace\":\"other\"}","{\"slice\":[{\"field\":\"secretField\",\"op\":\"=\",\"value\":1}]}",
                "{\"slice\":[{\"$expr\":\"secretField > 0\"}]}","{\"extData\":{\"tenant\":\"other\"}}",
                "{\"groupBy\":[{\"field\":\"station\"}]}","{\"having\":[{\"$and\":[{\"field\":\"secretField\"}]}]}")) {
            var r=request(root+"/data",session,body); var response=new MockHttpServletResponse();
            filter.doFilter(r,response,(a,b)->fail("must not reach controller")); assertEquals(401,response.getStatus());
        }
        var response=new MockHttpServletResponse();
        filter.doFilter(request(root+"/data",session,"{\"having\":[{\"field\":\"tickets\",\"op\":\"[]\",\"value\":[1,2]}]}"),response,(a,b)->{
            assertEquals("Runtime service reference",((jakarta.servlet.http.HttpServletRequest)a).getHeader("Authorization"));
        }); assertEquals(200,response.getStatus());
        response=new MockHttpServletResponse(); filter.doFilter(request(root.replace("Waybills","Other")+"/data",session,"{}"),response,(a,b)->fail()); assertEquals(401,response.getStatus());
        var r=request(root+"/data",session,"{}"); r.setCookies(new Cookie(service.cookieName("b".repeat(32)),session.token()));
        response=new MockHttpServletResponse(); filter.doFilter(r,response,(a,b)->fail()); assertEquals(401,response.getStatus());
        assertThrows(SecurityException.class,()->service.access(session.token(),"b".repeat(32)));
    }
    @Test void openPageHasNoLoginAndRedeemSetsOnlyHttpOnlySessionCookie() {
        String secret=link(); var controller=new ViewerLinkController(service,props);
        String page=controller.open().getBody(); assertFalse(page.contains("password")); assertFalse(page.contains("username"));
        var r=new MockHttpServletRequest(); r.setServerName("localhost"); r.setServerPort(18172); r.addHeader("Origin","http://localhost:18172");
        var result=controller.redeem(new ViewerLinkController.Secret(secret),r);
        assertEquals(200,result.getStatusCode().value()); String cookie=result.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("Secure")); assertTrue(cookie.contains("SameSite=Strict")); assertTrue(cookie.contains("Max-Age=86400"));
        assertFalse(result.getBody().toString().contains("synthetic-test-only")); assertFalse(result.getBody().toString().contains(secret));
        r.removeHeader("Origin"); assertEquals(403,controller.redeem(new ViewerLinkController.Secret(secret),r).getStatusCode().value());
    }
    MockHttpServletRequest request(String uri,ViewerGrantService.IssuedSession s,String body) {
        var r=new MockHttpServletRequest("POST",uri); r.setCookies(new Cookie(service.cookieName(query.getQueryId()),s.token()));
        r.setContentType("application/json"); r.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); return r;
    }
    static class MutableClock extends Clock {
        Instant now=Instant.now(); void advance(Duration d) { now=now.plus(d); }
        public ZoneId getZone() { return ZoneOffset.UTC; } public Clock withZone(ZoneId z) { return this; } public Instant instant() { return now; }
    }
}
