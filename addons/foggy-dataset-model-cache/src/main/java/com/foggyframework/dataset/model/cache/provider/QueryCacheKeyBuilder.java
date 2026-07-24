package com.foggyframework.dataset.model.cache.provider;

import com.foggyframework.dataset.model.cache.config.QueryCacheProperties;
import com.foggyframework.dataset.model.cache.fingerprint.QueryFingerprint;
import com.foggyframework.dataset.model.cache.fingerprint.QueryFingerprintBuilder;
import com.foggyframework.dataset.model.cache.fingerprint.SecurityPolicyFingerprint;
import com.foggyframework.dataset.model.cache.fingerprint.StableCanonicalEncoder;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds fail-closed L1/L2 keys with the complete runtime isolation scope.
 */
final class QueryCacheKeyBuilder {

    private static final String KEY_VERSION = "v3";

    private final QueryFingerprintBuilder fingerprintBuilder;
    private final QueryCacheProperties properties;

    QueryCacheKeyBuilder(QueryFingerprintBuilder fingerprintBuilder, QueryCacheProperties properties) {
        this.fingerprintBuilder = fingerprintBuilder;
        this.properties = properties;
    }

    String contextModelName(ModelResultContext context) {
        if (context == null || context.getRequest() == null || context.getRequest().getParam() == null) {
            return null;
        }
        String modelName = context.getRequest().getParam().getQueryModel();
        return isBlank(modelName) ? null : modelName.trim();
    }

    String buildL1CacheKey(ModelResultContext context, String authorization) {
        if (isBlank(authorization)) {
            return null;
        }
        try {
            QueryFingerprint fingerprint = fingerprintBuilder.build(context);
            if (fingerprint == null || !fingerprint.isCacheable()) {
                return null;
            }
            Optional<CacheScope> scope = resolveScope(
                    context,
                    fingerprint.getModelName(),
                    fingerprint.getSecurityPolicyHash(),
                    CacheLayer.L1);
            Optional<String> encodedAuthorization = StableCanonicalEncoder.encode(authorization);
            String fingerprintKey = fingerprint.toCacheKey();
            if (scope.isEmpty() || encodedAuthorization.isEmpty() || fingerprintKey == null) {
                return null;
            }

            String payload = StableCanonicalEncoder.segment("version", KEY_VERSION)
                    + StableCanonicalEncoder.segment("scope", scope.get().canonical())
                    + StableCanonicalEncoder.segment(
                            "authorizationHash",
                            StableCanonicalEncoder.sha256(encodedAuthorization.get()))
                    + StableCanonicalEncoder.segment("fingerprint", fingerprintKey);
            return properties.getKeyPrefix() + "l1:" + fingerprint.getModelName() + ":"
                    + StableCanonicalEncoder.sha256(payload);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    String buildL2CacheKey(String modelName,
                           String sql,
                           List<?> params,
                           ModelResultContext context) {
        if (isBlank(modelName) || isBlank(sql)) {
            return null;
        }
        try {
            Optional<SecurityPolicyFingerprint> policy = SecurityPolicyFingerprint.from(context);
            if (policy.isEmpty()) {
                return null;
            }
            Optional<CacheScope> scope = resolveScope(
                    context, modelName, policy.get().combinedHash(), CacheLayer.L2);
            Optional<String> encodedParams = StableCanonicalEncoder.encode(params);
            if (scope.isEmpty() || encodedParams.isEmpty()) {
                return null;
            }

            String payload = StableCanonicalEncoder.segment("version", KEY_VERSION)
                    + StableCanonicalEncoder.segment("scope", scope.get().canonical())
                    + StableCanonicalEncoder.segment("sql", sql)
                    + StableCanonicalEncoder.segment("params", encodedParams.get());
            return properties.getKeyPrefix() + "l2:" + modelName + ":"
                    + StableCanonicalEncoder.sha256(payload);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Optional<CacheScope> resolveScope(ModelResultContext context,
                                              String expectedModelName,
                                              String securityPolicyHash,
                                              CacheLayer layer) {
        String requestedModelName = contextModelName(context);
        if (requestedModelName == null || isBlank(expectedModelName) || isBlank(securityPolicyHash)) {
            return Optional.empty();
        }

        CatalogIdentity catalogIdentity = context.getCatalogIdentity();
        String canonicalModelName = context.getCanonicalModelName();
        QueryModel queryModel = context.getQueryModel();
        if (catalogIdentity == null
                || isBlank(canonicalModelName)
                || queryModel == null
                || isBlank(queryModel.getName())
                || !canonicalModelName.equals(queryModel.getName())) {
            return Optional.empty();
        }

        String canonicalNamespace = CatalogIdentity.canonicalNamespace(context.getNamespace());
        if (!canonicalNamespace.equals(catalogIdentity.namespace())
                || catalogIdentity.generation() == null
                || isBlank(catalogIdentity.generation().value())
                || catalogIdentity.sourceRevision() == null
                || isBlank(catalogIdentity.sourceRevision().value())
                || !context.isBindingIdentityComplete()) {
            return Optional.empty();
        }

        if (layer == CacheLayer.L1) {
            if (!requestedModelName.equals(expectedModelName)) {
                return Optional.empty();
            }
        } else if (!canonicalModelName.equals(expectedModelName)) {
            return Optional.empty();
        }

        Optional<String> bindingIdentity = bindingIdentity(context, queryModel);
        if (bindingIdentity.isEmpty()) {
            return Optional.empty();
        }

        String canonical = StableCanonicalEncoder.segment("namespace", canonicalNamespace)
                + StableCanonicalEncoder.segment("requestedModel", requestedModelName)
                + StableCanonicalEncoder.segment("canonicalModel", canonicalModelName)
                + StableCanonicalEncoder.segment(
                        "catalogGeneration", catalogIdentity.generation().value())
                + StableCanonicalEncoder.segment(
                        "sourceRevision", catalogIdentity.sourceRevision().value())
                + StableCanonicalEncoder.segment("bindingIdentities", bindingIdentity.get())
                + StableCanonicalEncoder.segment("securityPolicy", securityPolicyHash);
        return Optional.of(new CacheScope(canonical));
    }

    private Optional<String> bindingIdentity(ModelResultContext context, QueryModel queryModel) {
        Map<String, DatasourceBindingIdentity> identities = context.getDatasourceBindingIdentities();
        if (identities == null || (queryModel instanceof JdbcQueryModel && identities.isEmpty())) {
            return Optional.empty();
        }

        List<Map.Entry<String, DatasourceBindingIdentity>> entries =
                new ArrayList<>(identities.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey, Comparator.nullsFirst(String::compareTo)));

        StringBuilder canonical = new StringBuilder(
                StableCanonicalEncoder.segment("bindingCount", Integer.toString(entries.size())));
        for (Map.Entry<String, DatasourceBindingIdentity> entry : entries) {
            String bindingKey = entry.getKey();
            DatasourceBindingIdentity identity = entry.getValue();
            if (isBlank(bindingKey)
                    || identity == null
                    || !bindingKey.equals(identity.bindingKey())
                    || isBlank(identity.backendId())
                    || identity.generation() == null
                    || isBlank(identity.generation().value())) {
                return Optional.empty();
            }
            canonical.append(StableCanonicalEncoder.segment("bindingKey", bindingKey))
                    .append(StableCanonicalEncoder.segment("backendId", identity.backendId()))
                    .append(StableCanonicalEncoder.segment(
                            "bindingGeneration", identity.generation().value()));
        }
        if (entries.isEmpty()) {
            canonical.append(StableCanonicalEncoder.segment("datasourceFree", "true"));
        }
        return Optional.of(canonical.toString());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CacheScope(String canonical) {
    }

    private enum CacheLayer {
        L1,
        L2
    }
}
