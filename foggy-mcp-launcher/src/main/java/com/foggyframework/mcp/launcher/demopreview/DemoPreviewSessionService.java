package com.foggyframework.mcp.launcher.demopreview;

import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.security.ViewerIdentityProvider;
import com.foggyframework.dataviewer.security.ViewerGrantService;
import com.foggyframework.dataviewer.service.QueryCacheService;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Live demo MCP credential registry and engine-permission adapter. No personal browser login.
 * Incoming MCP headers are fingerprinted and discarded. Internal references carry a process HMAC.
 */
public final class DemoPreviewSessionService implements ViewerIdentityProvider {
    private static final String SCHEME="FoggyMcpReference ";
    private static final Set<String> ACTIONS=Set.of("DISCOVER","DESCRIBE","VALIDATE","EXECUTE","MEMBER_QUERY");
    private final DemoPreviewProperties properties;
    private final byte[] key=new byte[32];
    public DemoPreviewSessionService(DemoPreviewProperties properties,QueryCacheService ignored) {
        this.properties=properties; new SecureRandom().nextBytes(key);
    }
    @Override public Identity verify(String authorization,CachedQueryContext query) {
        if (authorization==null || authorization.isBlank()) throw ViewerGrantService.denied();
        return current(fingerprint(authorization),query);
    }
    @Override public void revalidate(Identity identity,CachedQueryContext query) {
        if (!identity.equals(current(identity.credentialReference(),query))) throw ViewerGrantService.denied();
    }
    @Override public String runtimeAuthorization(Identity identity) {
        return SCHEME+identity.credentialReference()+"."+sign(identity.credentialReference());
    }
    private Identity current(String ref,CachedQueryContext query) {
        if (!properties.getNamespace().equals(query.getNamespace()) || !properties.getModel().equals(query.getModel())) throw ViewerGrantService.denied();
        String scope=properties.getCredentialPrincipals().get(ref);
        DemoPreviewProperties.DemoUser service=scope==null?null:properties.getUsers().get(scope);
        if (service==null || service.getStationCodes()==null || service.getStationCodes().isEmpty()) throw ViewerGrantService.denied();
        String version=fingerprint(properties.getPolicyVersion()+"|"+scope+"|"+properties.getNamespace()+"|"+properties.getModel()+"|"+new TreeSet<>(service.getStationCodes()));
        return new Identity(ref,version);
    }
    public PermissionGrant resolveAuthorization(String authorization,String namespace,String model,String action) {
        if (!ACTIONS.contains(action)) return PermissionGrant.denied(properties.getPolicyVersion());
        try {
            String ref;
            if (authorization!=null && authorization.startsWith(SCHEME)) {
                String[] parts=authorization.substring(SCHEME.length()).split("\\.");
                if (parts.length!=2 || !MessageDigest.isEqual(sign(parts[0]).getBytes(StandardCharsets.UTF_8),parts[1].getBytes(StandardCharsets.UTF_8))) throw ViewerGrantService.denied();
                ref=parts[0];
            } else {
                if (authorization==null || authorization.isBlank()) throw ViewerGrantService.denied();
                ref=fingerprint(authorization);
            }
            CachedQueryContext query=CachedQueryContext.builder().model(model).namespace(namespace).build();
            current(ref,query);
            var scope=properties.getUsers().get(properties.getCredentialPrincipals().get(ref));
            return new PermissionGrant(true,List.copyOf(scope.getStationCodes()),properties.getPolicyVersion(),"mcp-service-policy");
        } catch (SecurityException e) { return PermissionGrant.denied(properties.getPolicyVersion()); }
    }
    private String sign(String ref) {
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(ref.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public static String fingerprint(String value) { return ViewerGrantService.digest(value); }
    public record PermissionGrant(boolean allow,List<String> stationCodes,String policyVersion,String decisionId) {
        static PermissionGrant denied(String version) { return new PermissionGrant(false,List.of(),version,null); }
    }
}
