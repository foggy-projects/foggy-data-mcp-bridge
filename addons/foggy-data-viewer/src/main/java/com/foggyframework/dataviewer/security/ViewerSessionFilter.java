package com.foggyframework.dataviewer.security;

import com.fasterxml.jackson.databind.*;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Restricts every viewer API to one grant's namespace, model, fields and immutable base query. */
public final class ViewerSessionFilter extends OncePerRequestFilter {
    public static final String ACCESS = ViewerSessionFilter.class.getName()+".access";
    private final ViewerGrantService grants;
    private final ObjectMapper mapper;
    public ViewerSessionFilter(ViewerGrantService grants,ObjectMapper mapper) { this.grants=grants; this.mapper=mapper; }
    public static ViewerGrantService.Access currentAccess() {
        var a=RequestContextHolder.getRequestAttributes();
        return a instanceof ServletRequestAttributes s ? (ViewerGrantService.Access)s.getRequest().getAttribute(ACCESS) : null;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest r) {
        String p=r.getRequestURI();
        return !p.startsWith("/data-viewer/api/") || p.equals("/data-viewer/api/link/redeem")
                || p.equals("/data-viewer/api/link/revoke") || p.equals("/data-viewer/api/query/create");
    }
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        response.setHeader("Cache-Control","no-store"); response.setHeader("Vary","Cookie");
        response.setHeader("Referrer-Policy","no-referrer");
        ViewerGrantService.Access access;
        byte[] body;
        try {
            String origin=r.getHeader("Origin");
            String expected=r.getScheme()+"://"+r.getServerName()+((r.getServerPort()==80 || r.getServerPort()==443)?"":":"+r.getServerPort());
            if (origin!=null && !expected.equalsIgnoreCase(origin)) throw ViewerGrantService.denied();
            String[] parts=r.getRequestURI().split("/");
            String queryId=r.getHeader("X-Foggy-View");
            if (parts.length >= 7 && parts[3].equals("query")) {
                if (queryId != null && !queryId.equals(parts[5])) throw ViewerGrantService.denied();
                queryId=parts[5];
            }
            if (queryId == null || !queryId.matches("[a-f0-9]{32}")) throw ViewerGrantService.denied();
            String token=null;
            if (r.getCookies()!=null) for (Cookie c:r.getCookies()) if (grants.cookieName(queryId).equals(c.getName())) token=c.getValue();
            access=grants.access(token,queryId);
            CachedQueryContext q=access.query();
            checkNamespace(r.getHeader("X-NS"),q); checkNamespace(r.getParameter("namespace"),q);
            String p=r.getRequestURI(); String root="/data-viewer/api/";
            String queryRoot=root+"query/"+q.getModel()+"/"+q.getQueryId();
            boolean allowed=(r.getMethod().equals("GET") && (p.equals(queryRoot+"/meta")
                    || p.startsWith(queryRoot+"/filter-options/") || p.equals(root+"schema/"+q.getModel())
                    || p.equals(root+"frontend-meta/"+q.getModel())))
                    || (r.getMethod().equals("POST") && p.equals(queryRoot+"/data"));
            if (!allowed) throw ViewerGrantService.denied();
            if (p.contains("/filter-options/") && !q.getColumns().contains(java.net.URLDecoder.decode(parts[parts.length-1],StandardCharsets.UTF_8))) throw ViewerGrantService.denied();
            body=r.getInputStream().readNBytes(1_048_577);
            if (body.length>1_048_576) throw ViewerGrantService.denied();
            if (body.length>0) checkBody(mapper.readTree(body),q);
        } catch (SecurityException|IllegalArgumentException e) {
            response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"视图授权已失效或请求超出授权范围，请重新打开有效链接\"}"); return;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            response.sendError(400,"Invalid viewer request"); return;
        }
        r.setAttribute(ACCESS,access);
        HttpServletRequest wrapped=new HttpServletRequestWrapper(r) {
            @Override public String getHeader(String name) {
                if (name.equalsIgnoreCase("Authorization")) return access.authorization();
                if (name.equalsIgnoreCase("X-NS")) return access.query().getNamespace();
                // Service grants confer no caller-controlled personal identity.
                if (name.toLowerCase(Locale.ROOT).matches("x-(user-id|dept-id|tenant-id|roles|permission-tags)")) return null;
                return super.getHeader(name);
            }
            @Override public Enumeration<String> getHeaders(String name) {
                String value=getHeader(name); return value==null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(value));
            }
            @Override public ServletInputStream getInputStream() {
                ByteArrayInputStream stream=new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    public int read() { return stream.read(); }
                    public boolean isFinished() { return stream.available()==0; }
                    public boolean isReady() { return true; }
                    public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8)); }
        };
        chain.doFilter(wrapped,response);
    }
    private void checkNamespace(String ns,CachedQueryContext q) {
        if (ns!=null && !ns.isBlank() && !Objects.equals(ns,q.getNamespace())) throw ViewerGrantService.denied();
    }
    private void checkBody(JsonNode b,CachedQueryContext q) {
        if (!b.isObject()) throw ViewerGrantService.denied();
        Set<String> keys=Set.of("start","limit","columns","slice","having","orderBy","groupBy","namespace","returnTotal","extData");
        b.fieldNames().forEachRemaining(k->{ if (!keys.contains(k)) throw ViewerGrantService.denied(); });
        checkNamespace(b.path("namespace").isTextual()?b.get("namespace").asText():null,q);
        if (b.has("namespace") && !b.get("namespace").isNull() && !b.get("namespace").isTextual()) throw ViewerGrantService.denied();
        if (b.hasNonNull("extData") && !b.get("extData").equals(mapper.valueToTree(q.getExtData()))) throw ViewerGrantService.denied();
        if (b.hasNonNull("groupBy") && b.get("groupBy").size()>0 && !b.get("groupBy").equals(mapper.valueToTree(q.getGroupBy()))) throw ViewerGrantService.denied();
        Set<String> fields=new HashSet<>(q.getColumns());
        for (String key:List.of("columns","slice","having","orderBy")) if (b.hasNonNull(key)) {
            if (!b.get(key).isArray()) throw ViewerGrantService.denied();
            checkFields(b.get(key),fields);
        }
        if (b.has("limit") && (!b.get("limit").canConvertToInt() || b.get("limit").asInt()<1 || b.get("limit").asInt()>10000)) throw ViewerGrantService.denied();
        if (b.has("start") && (!b.get("start").canConvertToInt() || b.get("start").asInt()<0)) throw ViewerGrantService.denied();
    }
    private void checkFields(JsonNode n,Set<String> fields) {
        if (n.isTextual()) { if (!fields.contains(n.asText())) throw ViewerGrantService.denied(); }
        else if (n.isArray()) for (JsonNode c:n) checkFields(c,fields);
        else if (n.isObject()) {
            if (n.hasNonNull("$expr")) throw ViewerGrantService.denied();
            if (n.has("value") && n.get("value").has("$field") && !fields.contains(n.get("value").get("$field").asText())) throw ViewerGrantService.denied();
            if (n.has("field") && (!n.get("field").isTextual() || !fields.contains(n.get("field").asText()))) throw ViewerGrantService.denied();
            // Nested boolean slices keep their detail/having position; never rewrite their semantics.
            for (String k:List.of("$and","$or","and","or","children")) if (n.has(k)) checkFields(n.get(k),fields);
        }
    }
}
