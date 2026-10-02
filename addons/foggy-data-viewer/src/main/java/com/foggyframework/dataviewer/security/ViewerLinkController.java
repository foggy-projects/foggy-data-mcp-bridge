package com.foggyframework.dataviewer.security;

import com.foggyframework.dataviewer.config.DataViewerProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.security.SecureRandom;
import java.util.*;

@RestController
public final class ViewerLinkController {
    private final ViewerGrantService grants;
    private final DataViewerProperties.LinkAuthorizationProperties props;
    public ViewerLinkController(ViewerGrantService grants,DataViewerProperties p) { this.grants=grants; props=p.getLinkAuthorization(); }
    @GetMapping("/data-viewer/open")
    public ResponseEntity<String> open() {
        byte[] bytes=new byte[18]; new SecureRandom().nextBytes(bytes);
        String nonce=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String html="""
            <!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>打开数据视图</title>
            <main><p id="status">正在打开数据视图…</p></main>
            <script nonce="%s">const secret=location.hash.slice(1);history.replaceState(null,'',location.pathname);
            (async()=>{try{const r=await fetch('/data-viewer/api/link/redeem',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json'},body:JSON.stringify({secret})});
            if(!r.ok)throw Error();const result=await r.json();location.replace(result.viewerUrl)}catch{document.querySelector('#status').textContent='链接已过期、被撤销或服务授权已失效，请从 Harness 重新打开。'}})();</script></html>
            """.formatted(nonce);
        return ResponseEntity.ok().header("Cache-Control","no-store").header("Referrer-Policy","no-referrer")
                .header("X-Content-Type-Options","nosniff")
                .header("Content-Security-Policy","default-src 'none'; script-src 'nonce-"+nonce+"'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'")
                .contentType(MediaType.TEXT_HTML).body(html);
    }
    @PostMapping("/data-viewer/api/link/redeem")
    public ResponseEntity<?> redeem(@RequestBody Secret body,HttpServletRequest r) {
        if (!sameOrigin(r)) return ResponseEntity.status(403).build();
        try {
            var issued=grants.redeem(body.secret());
            ResponseCookie cookie=ResponseCookie.from(grants.cookieName(issued.queryId()),issued.token())
                    .httpOnly(true).secure(props.isSecureCookie()).sameSite("Strict").path("/data-viewer")
                    .maxAge(props.getSessionTtl()).build();
            return ResponseEntity.ok().header("Set-Cookie",cookie.toString()).header("Cache-Control","no-store")
                    .body(Map.of("viewerUrl",issued.viewerUrl(),"issuedAt",issued.issuedAt(),"expiresAt",issued.expiresAt()));
        } catch (SecurityException e) { return ResponseEntity.status(401).header("Cache-Control","no-store").body(Map.of("error","Viewer link is unavailable")); }
    }
    /** Management operation uses the owning MCP credential, never a browser cookie alone. */
    @PostMapping("/data-viewer/api/link/revoke")
    public ResponseEntity<?> revoke(@RequestBody Secret body,@RequestHeader(value="Authorization",required=false) String auth) {
        try { grants.revoke(body.secret(),auth); return ResponseEntity.noContent().header("Cache-Control","no-store").build(); }
        catch (SecurityException e) { return ResponseEntity.status(403).header("Cache-Control","no-store").build(); }
    }
    private boolean sameOrigin(HttpServletRequest r) {
        String expected=r.getScheme()+"://"+r.getServerName();
        if (!(r.getScheme().equals("http") && r.getServerPort()==80) && !(r.getScheme().equals("https") && r.getServerPort()==443)) expected+=":"+r.getServerPort();
        return expected.equalsIgnoreCase(r.getHeader("Origin"));
    }
    public record Secret(String secret) { }
}
