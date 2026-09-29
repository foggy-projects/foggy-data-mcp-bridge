package com.foggyframework.mcp.launcher.demopreview;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Local opt-in login, one-time handoff, and model-permission callback for the synthetic demo. */
@RestController
@ConditionalOnProperty(prefix = "foggy.demo-preview", name = "enabled", havingValue = "true")
public class DemoPreviewController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final DemoPreviewSessionService sessions;
    private final DemoPreviewProperties properties;

    public DemoPreviewController(DemoPreviewSessionService sessions, DemoPreviewProperties properties) {
        this.sessions = sessions;
        this.properties = properties;
    }

    @GetMapping("/demo-preview/open")
    public ResponseEntity<String> loginPage() {
        byte[] nonceBytes = new byte[18];
        RANDOM.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        String html = """
                <!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>数据探查登录</title><style>body{font:16px system-ui;background:#f4f6fa;margin:0;display:grid;min-height:100vh;place-items:center}.card{background:#fff;padding:32px;border-radius:12px;width:min(360px,calc(100vw - 48px));box-shadow:0 12px 36px #15213a18}label{display:block;margin:16px 0 6px}input{box-sizing:border-box;width:100%;padding:10px;border:1px solid #ccd3df;border-radius:6px}button{margin-top:20px;width:100%;padding:11px;border:0;border-radius:6px;background:#3289f5;color:#fff;font-size:16px}small{color:#697386}#error{color:#c43e4d;min-height:1.3em}</style>
                <main class="card"><h1>打开数据探查</h1><p>请用获授权的演示用户登录。页面只访问独立构造的合成物流数据。</p>
                <form id="login"><label for="user">演示用户</label><input id="user" name="username" autocomplete="username" required>
                <label for="password">密码</label><input id="password" name="password" type="password" autocomplete="current-password" required>
                <button>验证并继续</button><p id="error" role="alert"></p></form><small>交接码两小时内可使用一次；登录后可探查两小时。</small></main>
                <script nonce="%s">const form=document.querySelector('#login');const error=document.querySelector('#error');const code=location.hash.slice(1);history.replaceState(null,'',location.pathname);form.addEventListener('submit',async event=>{event.preventDefault();if(!code){error.textContent='链接已失效，请从 Harness 重新打开';return}const body={code,username:form.username.value,password:form.password.value};try{const response=await fetch('/demo-preview/api/session/redeem',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body),credentials:'same-origin'});if(!response.ok){error.textContent='账号、密码或交接链接无效';return}const result=await response.json();location.replace(result.viewerUrl)}catch{error.textContent='登录服务暂时不可用，请重试'}});</script></html>
                """.replace("%s", nonce);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-"
                        + nonce + "'; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'")
                .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                .body(html);
    }

    @PostMapping("/demo-preview/api/session/redeem")
    public ResponseEntity<?> redeem(@RequestBody RedeemRequest body,
                                    @RequestHeader(value = "Origin", required = false) String origin,
                                    HttpServletRequest request) {
        if (!sameOrigin(origin, request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "origin rejected"));
        }
        try {
            DemoPreviewSessionService.RedeemedSession redeemed = sessions.redeem(
                    body.code(), body.username(), body.password());
            long maxAge = Math.max(1, properties.getAbsoluteSessionTtl().toSeconds());
            ResponseCookie cookie = ResponseCookie.from(properties.getCookieName(), redeemed.token())
                    .httpOnly(true)
                    .secure(properties.isSecureCookie())
                    .sameSite("Strict")
                    .path("/data-viewer")
                    .maxAge(maxAge)
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(Map.of("viewerUrl", redeemed.viewerUrl(), "session", redeemed.session()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/data-viewer/api/preview/session")
    public ResponseEntity<?> currentSession(HttpServletRequest request) {
        String token = cookie(request, properties.getCookieName());
        DemoPreviewSessionService.SessionView session = sessions.getSession(token);
        return session == null
                ? ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(Map.of("error", "session expired"))
                : ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(session);
    }

    @PostMapping("/data-viewer/api/preview/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                       @RequestHeader(value = "Origin", required = false) String origin) {
        if (!sameOrigin(origin, request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }
        sessions.revoke(cookie(request, properties.getCookieName()));
        ResponseCookie cookie = ResponseCookie.from(properties.getCookieName(), "")
                .httpOnly(true).secure(properties.isSecureCookie()).sameSite("Strict")
                .path("/data-viewer").maxAge(0).build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @PostMapping("/demo-preview/internal/model-permissions")
    public ResponseEntity<DemoPreviewSessionService.PermissionGrant> resolveModelPermissions(
            @RequestBody PermissionRequest permission,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletRequest request) {
        if (!isLoopback(request.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(DemoPreviewSessionService.PermissionGrant.denied(properties.getPolicyVersion()));
        }
        DemoPreviewSessionService.PermissionGrant grant = sessions.resolveAuthorization(
                authorization, permission.namespace(), permission.model(), permission.action());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(grant);
    }

    private boolean sameOrigin(String origin, HttpServletRequest request) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        String expected = request.getScheme() + "://" + request.getServerName();
        if ((request.getScheme().equals("http") && request.getServerPort() != 80)
                || (request.getScheme().equals("https") && request.getServerPort() != 443)) {
            expected += ":" + request.getServerPort();
        }
        return expected.equalsIgnoreCase(origin);
    }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private boolean isLoopback(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

    public record RedeemRequest(String code, String username, String password) { }
    public record PermissionRequest(String namespace, String model, String action) { }
}
