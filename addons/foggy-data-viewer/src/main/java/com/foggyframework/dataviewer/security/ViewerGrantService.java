package com.foggyframework.dataviewer.security;

import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataviewer.service.ViewerLaunchLinkProvider;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataviewer.service.StableQueryFacadeRequestMapper;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** Reusable service-identity grants. Every session access follows its live grant and identity. */
public final class ViewerGrantService implements ViewerLaunchLinkProvider {
    private final ViewerGrantStore store;
    private final ViewerIdentityProvider identities;
    private final QueryCacheService queries;
    private final QueryFacade facade;
    private final DataViewerProperties.LinkAuthorizationProperties props;
    private final Clock clock;
    private static final SecureRandom RANDOM = new SecureRandom();

    public ViewerGrantService(ViewerGrantStore store, ViewerIdentityProvider identities, QueryCacheService queries,
                              QueryFacade facade, DataViewerProperties.LinkAuthorizationProperties props, Clock clock) {
        this.store=store; this.identities=identities; this.queries=queries; this.facade=facade; this.props=props; this.clock=clock;
        if (props.getLinkTtl() == null || props.getLinkTtl().isNegative()
                || props.getSessionTtl() == null || props.getSessionTtl().compareTo(Duration.ofSeconds(1)) < 0)
            throw new IllegalArgumentException("link-ttl must be zero (permanent) or positive; session-ttl must be at least 1s");
        if (!props.getCookieName().matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid viewer cookie name");
    }
    @Override public String createViewerUrl(CachedQueryContext q, String auth, String ignored) {
        return createViewerLink(q,auth,ignored).url();
    }
    @Override public ViewerLaunchLink createViewerLink(CachedQueryContext q, String auth, String ignored) {
        ViewerIdentityProvider.Identity identity = identities.verify(auth, q);
        if (identity == null || identity.credentialReference() == null || identity.scopeVersion() == null)
            throw denied();
        identities.revalidate(identity,q);
        // Validate the actual query with the original MCP data-plane identity, including fields/rows.
        PagingRequest<DbQueryRequestDef> request = new PagingRequest<>();
        request.setParam(q.toDbQueryRequestDef()); request.setStart(0); request.setLimit(1);
        facade.query(StableQueryFacadeRequestMapper.from(request,auth,q.getNamespace()));
        Instant now = clock.instant();
        Instant expiry = props.getLinkTtl().isZero() ? null : now.plus(props.getLinkTtl());
        // Pin before publishing. Query cache TTL must never shorten the authorization lifetime.
        if (!queries.extendExpiry(q.getQueryId(),q.getModel(),q.getNamespace(),now,expiry)) throw denied();
        q.setExpiresAt(expiry);
        String secret = random();
        store.saveGrant(new ViewerGrantStore.Grant(digest(secret),q.getQueryId(),q.getModel(),q.getNamespace(),identity,now,expiry,null));
        return new ViewerLaunchLink(java.net.URI.create(ignored).resolve("/data-viewer/open").toString() + "#" + secret,expiry);
    }
    public IssuedSession redeem(String secret) {
        ViewerGrantStore.Grant grant = activeGrant(digest(secret));
        String token = random(); Instant now = clock.instant(); Instant expiry = now.plus(props.getSessionTtl());
        store.cleanupSessions(now);
        store.saveSession(new ViewerGrantStore.Session(digest(token),grant.digest(),now,expiry));
        return new IssuedSession(token,grant.queryId(),viewerPath(grant),now,expiry);
    }
    public Access access(String token, String queryId) {
        if (token == null || token.isBlank()) throw denied();
        ViewerGrantStore.Session session = store.session(digest(token)).orElseThrow(ViewerGrantService::denied);
        if (!clock.instant().isBefore(session.expiresAt())) throw denied();
        ViewerGrantStore.Grant grant = activeGrant(session.grantDigest());
        if (!Objects.equals(grant.queryId(),queryId)) throw denied();
        return new Access(grant,query(grant),identities.runtimeAuthorization(grant.identity()),session);
    }
    public boolean revoke(String secret, String authorization) {
        ViewerGrantStore.Grant grant = store.grant(digest(secret)).orElseThrow(ViewerGrantService::denied);
        CachedQueryContext q = query(grant);
        var identity = identities.verify(authorization,q);
        if (!grant.identity().equals(identity)) throw denied();
        identities.revalidate(identity,q);
        return store.revoke(grant.digest(),clock.instant());
    }
    private ViewerGrantStore.Grant activeGrant(String digest) {
        ViewerGrantStore.Grant grant = store.grant(digest).orElseThrow(ViewerGrantService::denied);
        if (grant.revokedAt() != null || (grant.expiresAt() != null && !clock.instant().isBefore(grant.expiresAt()))) throw denied();
        identities.revalidate(grant.identity(),query(grant));
        return grant;
    }
    private CachedQueryContext query(ViewerGrantStore.Grant g) {
        CachedQueryContext q = queries.getQuery(g.queryId(),clock.instant()).orElseThrow(ViewerGrantService::denied);
        if (!Objects.equals(g.model(),q.getModel()) || !Objects.equals(g.namespace(),q.getNamespace())) throw denied();
        return q;
    }
    public String cookieName(String queryId) { return props.getCookieName()+"-"+queryId; }
    private String viewerPath(ViewerGrantStore.Grant g) { return "/data-viewer/view/"+g.model()+"/"+g.queryId(); }
    public static SecurityException denied() { return new SecurityException("Viewer authorization is unavailable or expired"); }
    public static String digest(String value) {
        if (value == null) return "";
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String random() { byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public record IssuedSession(String token,String queryId,String viewerUrl,Instant issuedAt,Instant expiresAt) { }
    public record Access(ViewerGrantStore.Grant grant,CachedQueryContext query,String authorization,ViewerGrantStore.Session session) { }
}
