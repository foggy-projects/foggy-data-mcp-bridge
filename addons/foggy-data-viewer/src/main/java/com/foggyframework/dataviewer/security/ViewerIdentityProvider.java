package com.foggyframework.dataviewer.security;

import com.foggyframework.dataviewer.domain.CachedQueryContext;

/** Host integration with the existing MCP credential registry/vault and engine authorization.
 * Never put the incoming credential in Identity. References must remain resolvable after restart.
 * Revalidation must reject disabled credentials and changed/revoked permissions, without caching
 * an old decision. runtimeAuthorization is server-only and still goes through QueryFacade.
 */
public interface ViewerIdentityProvider {
    Identity verify(String authorization, CachedQueryContext query);
    void revalidate(Identity identity, CachedQueryContext query);
    String runtimeAuthorization(Identity identity);

    record Identity(String credentialReference, String scopeVersion) { }
}
