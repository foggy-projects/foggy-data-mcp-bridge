package com.foggyframework.mcp.launcher.demopreview;

import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataviewer.service.ViewerLaunchLinkProvider;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local demo-only handoff and session store. Secret values are random opaque
 * strings held by the browser in an HttpOnly cookie; only their SHA-256
 * fingerprints are kept in this in-memory store.
 */
public final class DemoPreviewSessionService implements ViewerLaunchLinkProvider {

    public static final String RUNTIME_AUTH_SCHEME = "FoggyPreview ";
    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "DISCOVER", "DESCRIBE", "VALIDATE", "EXECUTE", "MEMBER_QUERY");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DemoPreviewProperties properties;
    private final QueryCacheService queryCacheService;
    private final Clock clock;
    private final Map<String, Launch> launches = new ConcurrentHashMap<>();
    private final Map<String, PreviewSession> sessions = new ConcurrentHashMap<>();

    public DemoPreviewSessionService(DemoPreviewProperties properties, QueryCacheService queryCacheService) {
        this(properties, queryCacheService, Clock.systemUTC());
    }

    DemoPreviewSessionService(DemoPreviewProperties properties, QueryCacheService queryCacheService, Clock clock) {
        this.properties = properties;
        this.queryCacheService = queryCacheService;
        this.clock = clock;
        validateProperties(properties);
    }

    @Override
    public String createViewerUrl(CachedQueryContext query, String authorization, String defaultViewerUrl) {
        return createViewerLink(query, authorization, defaultViewerUrl).url();
    }

    @Override
    public ViewerLaunchLink createViewerLink(CachedQueryContext query, String authorization,
                                             String defaultViewerUrl) {
        if (query == null || !properties.getModel().equals(query.getModel())
                || !properties.getNamespace().equals(query.getNamespace())) {
            throw new IllegalArgumentException("Demo preview only allows its configured QM and namespace");
        }
        Principal principal = resolveMcpCredential(authorization);
        if (principal == null) {
            throw new IllegalStateException("No authenticated demo user is mapped to this MCP credential");
        }
        String viewerPath = URI.create(defaultViewerUrl).getRawPath();
        if (viewerPath == null || !viewerPath.startsWith("/data-viewer/view/")) {
            throw new IllegalArgumentException("Demo preview redirect must target a DataViewer query page");
        }
        String code = randomSecret();
        Instant now = clock.instant();
        Instant launchExpiry = now.plus(properties.getLaunchTtl());
        if (query.getExpiresAt() != null && query.getExpiresAt().isBefore(launchExpiry)) {
            launchExpiry = query.getExpiresAt();
        }
        launches.put(fingerprint(code), new Launch(
                fingerprint(authorization), principal.id(), query.getModel(), query.getNamespace(),
                query.getQueryId(), viewerPath, launchExpiry, 0));
        return new ViewerLaunchLink(stripTrailingSlash(properties.getPublicOpenUrl()) + "#" + code,
                launchExpiry);
    }

    public synchronized RedeemedSession redeem(String code, String username, String password) {
        Instant now = clock.instant();
        String codeFingerprint = fingerprint(code);
        Launch launch = launches.get(codeFingerprint);
        if (launch == null || !now.isBefore(launch.expiresAt()) || launch.failedAttempts() >= 3) {
            launches.remove(codeFingerprint);
            throw new IllegalArgumentException("交接码已失效，请从 Harness 重新打开");
        }
        String mappedPrincipal = properties.getCredentialPrincipals().get(launch.mcpCredentialId());
        DemoPreviewProperties.DemoUser user = username == null ? null : properties.getUsers().get(username);
        boolean valid = user != null
                && user.getPassword() != null
                && !user.getPassword().isBlank()
                && constantTimeEquals(user.getPassword(), password)
                && username.equals(launch.principalId())
                && username.equals(mappedPrincipal);
        if (!valid) {
            launches.put(codeFingerprint, launch.withFailedAttempts(launch.failedAttempts() + 1));
            throw new IllegalArgumentException("账号、密码或交接权限无效");
        }

        if (!queryCacheService.extendExpiry(launch.queryId(), launch.model(), launch.namespace(), now,
                now.plus(properties.getAbsoluteSessionTtl()))) {
            launches.remove(codeFingerprint);
            throw new IllegalArgumentException("查询已失效，请从 Harness 重新打开");
        }

        launches.remove(codeFingerprint, launch);
        String sessionToken = randomSecret();
        Instant absoluteExpiry = now.plus(properties.getAbsoluteSessionTtl());
        PreviewSession session = new PreviewSession(
                fingerprint(sessionToken), username, displayName(user, username), launch.mcpCredentialId(),
                launch.model(), launch.namespace(), launch.queryId(), absoluteExpiry, now);
        sessions.put(session.tokenFingerprint(), session);
        return new RedeemedSession(sessionToken, toView(session, user), launch.viewerUrl());
    }

    public synchronized SessionView getSession(String cookieToken) {
        PreviewSession session = findActiveSession(cookieToken, true);
        if (session == null) {
            return null;
        }
        return toView(session, properties.getUsers().get(session.principalId()));
    }

    public synchronized boolean revoke(String cookieToken) {
        return cookieToken != null && sessions.remove(fingerprint(cookieToken)) != null;
    }

    public synchronized boolean allowsViewerRequest(String cookieToken, String method, String requestUri,
                                                     String requestedNamespace) {
        PreviewSession session = findActiveSession(cookieToken, true);
        if (session == null || !isCredentialStillMapped(session)
                || !Set.of("GET", "POST").contains(method)) {
            return false;
        }
        if (requestedNamespace != null && !requestedNamespace.isBlank()
                && !requestedNamespace.equals(session.namespace())) {
            return false;
        }

        String[] parts = requestUri.split("/");
        if (parts.length < 5 || !parts[1].equals("data-viewer") || !parts[2].equals("api")) {
            return false;
        }
        if (parts[3].equals("query") && parts.length >= 7) {
            boolean supportedRoute = (parts.length == 7 && parts[6].equals("meta") && method.equals("GET"))
                    || (parts.length == 7 && parts[6].equals("data") && method.equals("POST"))
                    || (parts.length == 8 && parts[6].equals("filter-options") && method.equals("GET"));
            return supportedRoute && session.model().equals(parts[4]) && session.queryId().equals(parts[5]);
        }
        return (parts.length == 5 && method.equals("GET")
                && (parts[3].equals("schema") || parts[3].equals("frontend-meta"))
                && session.model().equals(parts[4]));
    }

    public synchronized PermissionGrant resolveAuthorization(String authorization, String namespace,
                                                              String model, String action) {
        if (!ALLOWED_ACTIONS.contains(action) || !properties.getNamespace().equals(namespace)
                || !properties.getModel().equals(model)) {
            return PermissionGrant.denied(properties.getPolicyVersion());
        }

        Principal principal;
        String trimmed = authorization == null ? "" : authorization.trim();
        if (trimmed.startsWith(RUNTIME_AUTH_SCHEME)) {
            String token = trimmed.substring(RUNTIME_AUTH_SCHEME.length());
            PreviewSession session = findActiveSession(token, true);
            if (session == null || !isCredentialStillMapped(session)
                    || !session.namespace().equals(namespace) || !session.model().equals(model)) {
                return PermissionGrant.denied(properties.getPolicyVersion());
            }
            principal = principal(session.principalId());
        } else {
            principal = resolveMcpCredential(authorization);
        }
        if (principal == null || principal.stationCodes().isEmpty()) {
            return PermissionGrant.denied(properties.getPolicyVersion());
        }
        return new PermissionGrant(true, principal.stationCodes(), properties.getPolicyVersion(),
                "demo-" + fingerprint(trimmed).substring(0, 12));
    }

    public String toRuntimeAuthorization(String cookieToken) {
        return RUNTIME_AUTH_SCHEME + cookieToken;
    }

    public boolean hasActiveLaunch(String code) {
        Launch launch = launches.get(fingerprint(code));
        return launch != null && clock.instant().isBefore(launch.expiresAt());
    }

    private PreviewSession findActiveSession(String token, boolean touch) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String tokenFingerprint = fingerprint(token);
        PreviewSession session = sessions.get(tokenFingerprint);
        Instant now = clock.instant();
        if (session == null || !now.isBefore(session.absoluteExpiresAt())
                || !now.isBefore(session.lastAccessAt().plus(properties.getIdleSessionTtl()))) {
            sessions.remove(tokenFingerprint);
            return null;
        }
        if (touch) {
            session = session.withLastAccessAt(now);
            sessions.put(tokenFingerprint, session);
        }
        return session;
    }

    private Principal resolveMcpCredential(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String id = fingerprint(authorization);
        return principal(properties.getCredentialPrincipals().get(id));
    }

    private boolean isCredentialStillMapped(PreviewSession session) {
        if (session.principalId().equals(properties.getCredentialPrincipals().get(session.mcpCredentialId()))) {
            return true;
        }
        sessions.remove(session.tokenFingerprint());
        return false;
    }

    private Principal principal(String principalId) {
        if (principalId == null) {
            return null;
        }
        DemoPreviewProperties.DemoUser user = properties.getUsers().get(principalId);
        if (user == null || user.getStationCodes() == null || user.getStationCodes().isEmpty()) {
            return null;
        }
        return new Principal(principalId, displayName(user, principalId), List.copyOf(user.getStationCodes()));
    }

    private SessionView toView(PreviewSession session, DemoPreviewProperties.DemoUser user) {
        List<String> stations = user == null || user.getStationCodes() == null
                ? List.of() : List.copyOf(user.getStationCodes());
        return new SessionView(session.principalId(), session.displayName(), session.namespace(), session.model(),
                session.queryId(), stations, session.absoluteExpiresAt(), session.lastAccessAt());
    }

    private static String displayName(DemoPreviewProperties.DemoUser user, String fallback) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? fallback : user.getDisplayName();
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    public static String fingerprint(String value) {
        if (value == null) {
            return "";
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private static void validateProperties(DemoPreviewProperties props) {
        Duration max = Duration.ofHours(2);
        if (props.getAbsoluteSessionTtl().isNegative() || props.getAbsoluteSessionTtl().isZero()
                || props.getAbsoluteSessionTtl().compareTo(max) > 0) {
            throw new IllegalArgumentException("demo preview absolute session TTL must be between 0 and 2 hours");
        }
        if (props.getIdleSessionTtl().isNegative() || props.getIdleSessionTtl().isZero()
                || props.getIdleSessionTtl().compareTo(props.getAbsoluteSessionTtl()) > 0) {
            throw new IllegalArgumentException("demo preview idle TTL must not exceed the absolute TTL");
        }
        if (props.getLaunchTtl().isNegative() || props.getLaunchTtl().isZero()
                || props.getLaunchTtl().compareTo(max) > 0) {
            throw new IllegalArgumentException("demo preview launch code TTL must be at most 2 hours");
        }
        if (props.getNamespace() == null || props.getNamespace().isBlank()
                || props.getModel() == null || props.getModel().isBlank()) {
            throw new IllegalArgumentException("demo preview must be bound to a namespace and model");
        }
    }

    private record Launch(String mcpCredentialId, String principalId, String model, String namespace,
                          String queryId, String viewerUrl, Instant expiresAt, int failedAttempts) {
        private Launch withFailedAttempts(int attempts) {
            return new Launch(mcpCredentialId, principalId, model, namespace, queryId, viewerUrl,
                    expiresAt, attempts);
        }
    }

    private record PreviewSession(String tokenFingerprint, String principalId, String displayName,
                                  String mcpCredentialId, String model, String namespace, String queryId,
                                  Instant absoluteExpiresAt, Instant lastAccessAt) {
        private PreviewSession withLastAccessAt(Instant instant) {
            return new PreviewSession(tokenFingerprint, principalId, displayName, mcpCredentialId, model,
                    namespace, queryId, absoluteExpiresAt, instant);
        }
    }

    private record Principal(String id, String displayName, List<String> stationCodes) { }

    public record PermissionGrant(boolean allow, List<String> stationCodes, String policyVersion,
                                  String decisionId) {
        static PermissionGrant denied(String policyVersion) {
            return new PermissionGrant(false, List.of(), policyVersion, null);
        }
    }

    public record SessionView(String principalId, String displayName, String namespace, String model,
                              String queryId, List<String> stationCodes, Instant expiresAt,
                              Instant lastAccessAt) { }

    public record RedeemedSession(String token, SessionView session, String viewerUrl) { }
}
