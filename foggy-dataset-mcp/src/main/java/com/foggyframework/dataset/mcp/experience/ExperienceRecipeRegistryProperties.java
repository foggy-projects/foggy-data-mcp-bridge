package com.foggyframework.dataset.mcp.experience;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "foggy.mcp.experience-recipe.registry")
public class ExperienceRecipeRegistryProperties {
    private String store = "memory";
    private boolean autoInitSchema;
    private boolean requireArtifactResolution;
    private boolean requireArtifactSignatureVerification;
    private String artifactRoot;
    private ArtifactUriPolicyProperties artifactUriPolicy = new ArtifactUriPolicyProperties();
    private ArtifactObjectMetadataPolicyProperties artifactObjectMetadataPolicy =
            new ArtifactObjectMetadataPolicyProperties();
    private RemoteHttpArtifactResolverProperties remoteHttp = new RemoteHttpArtifactResolverProperties();
    private List<ArtifactTrustKeyProperties> artifactTrustKeys = new ArrayList<>();

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public boolean isAutoInitSchema() {
        return autoInitSchema;
    }

    public void setAutoInitSchema(boolean autoInitSchema) {
        this.autoInitSchema = autoInitSchema;
    }

    public boolean isRequireArtifactResolution() {
        return requireArtifactResolution;
    }

    public void setRequireArtifactResolution(boolean requireArtifactResolution) {
        this.requireArtifactResolution = requireArtifactResolution;
    }

    public boolean isRequireArtifactSignatureVerification() {
        return requireArtifactSignatureVerification;
    }

    public void setRequireArtifactSignatureVerification(boolean requireArtifactSignatureVerification) {
        this.requireArtifactSignatureVerification = requireArtifactSignatureVerification;
    }

    public String getArtifactRoot() {
        return artifactRoot;
    }

    public void setArtifactRoot(String artifactRoot) {
        this.artifactRoot = artifactRoot;
    }

    public ArtifactUriPolicyProperties getArtifactUriPolicy() {
        return artifactUriPolicy;
    }

    public void setArtifactUriPolicy(ArtifactUriPolicyProperties artifactUriPolicy) {
        this.artifactUriPolicy = artifactUriPolicy == null ? new ArtifactUriPolicyProperties() : artifactUriPolicy;
    }

    public ArtifactObjectMetadataPolicyProperties getArtifactObjectMetadataPolicy() {
        return artifactObjectMetadataPolicy;
    }

    public void setArtifactObjectMetadataPolicy(
            ArtifactObjectMetadataPolicyProperties artifactObjectMetadataPolicy) {
        this.artifactObjectMetadataPolicy = artifactObjectMetadataPolicy == null
                ? new ArtifactObjectMetadataPolicyProperties()
                : artifactObjectMetadataPolicy;
    }

    public RemoteHttpArtifactResolverProperties getRemoteHttp() {
        return remoteHttp;
    }

    public void setRemoteHttp(RemoteHttpArtifactResolverProperties remoteHttp) {
        this.remoteHttp = remoteHttp == null ? new RemoteHttpArtifactResolverProperties() : remoteHttp;
    }

    public List<ArtifactTrustKeyProperties> getArtifactTrustKeys() {
        return artifactTrustKeys;
    }

    public void setArtifactTrustKeys(List<ArtifactTrustKeyProperties> artifactTrustKeys) {
        this.artifactTrustKeys = artifactTrustKeys == null ? new ArrayList<>() : artifactTrustKeys;
    }

    public static class ArtifactTrustKeyProperties {
        private String keyId;
        private String algorithm = "ed25519";
        private String publicKey;
        private Set<String> purposes = Set.of(ExperienceRecipeArtifactSignaturePayload.PURPOSE);
        private Set<String> tenantIds = Set.of();
        private Set<String> ownerIds = Set.of();
        private Set<String> artifactTypes = Set.of();
        private Set<String> signedBySubjects = Set.of();
        private Instant validFrom;
        private Instant validTo;
        private String status = "enabled";
        private Instant revokedAt;

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        public Set<String> getPurposes() {
            return purposes;
        }

        public void setPurposes(Set<String> purposes) {
            this.purposes = purposes == null ? Set.of() : purposes;
        }

        public Set<String> getTenantIds() {
            return tenantIds;
        }

        public void setTenantIds(Set<String> tenantIds) {
            this.tenantIds = tenantIds == null ? Set.of() : tenantIds;
        }

        public Set<String> getOwnerIds() {
            return ownerIds;
        }

        public void setOwnerIds(Set<String> ownerIds) {
            this.ownerIds = ownerIds == null ? Set.of() : ownerIds;
        }

        public Set<String> getArtifactTypes() {
            return artifactTypes;
        }

        public void setArtifactTypes(Set<String> artifactTypes) {
            this.artifactTypes = artifactTypes == null ? Set.of() : artifactTypes;
        }

        public Set<String> getSignedBySubjects() {
            return signedBySubjects;
        }

        public void setSignedBySubjects(Set<String> signedBySubjects) {
            this.signedBySubjects = signedBySubjects == null ? Set.of() : signedBySubjects;
        }

        public Instant getValidFrom() {
            return validFrom;
        }

        public void setValidFrom(Instant validFrom) {
            this.validFrom = validFrom;
        }

        public Instant getValidTo() {
            return validTo;
        }

        public void setValidTo(Instant validTo) {
            this.validTo = validTo;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Instant getRevokedAt() {
            return revokedAt;
        }

        public void setRevokedAt(Instant revokedAt) {
            this.revokedAt = revokedAt;
        }
    }

    public static class RemoteHttpArtifactResolverProperties {
        private boolean enabled;
        private Set<String> allowedHosts = Set.of();
        private int maxBytes = 1024 * 1024;
        private long connectTimeoutMillis = 2000;
        private long readTimeoutMillis = 5000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Set<String> getAllowedHosts() {
            return allowedHosts;
        }

        public void setAllowedHosts(Set<String> allowedHosts) {
            this.allowedHosts = allowedHosts == null ? Set.of() : allowedHosts;
        }

        public int getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        public long getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(long connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public long getReadTimeoutMillis() {
            return readTimeoutMillis;
        }

        public void setReadTimeoutMillis(long readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
        }
    }

    public static class ArtifactUriPolicyProperties {
        private boolean enabled;
        private List<String> allowedUriPrefixes = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAllowedUriPrefixes() {
            return allowedUriPrefixes;
        }

        public void setAllowedUriPrefixes(List<String> allowedUriPrefixes) {
            this.allowedUriPrefixes = allowedUriPrefixes == null ? new ArrayList<>() : allowedUriPrefixes;
        }
    }

    public static class ArtifactObjectMetadataPolicyProperties {
        private boolean enabled;
        private boolean requireResolvedObjectMetadata;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequireResolvedObjectMetadata() {
            return requireResolvedObjectMetadata;
        }

        public void setRequireResolvedObjectMetadata(boolean requireResolvedObjectMetadata) {
            this.requireResolvedObjectMetadata = requireResolvedObjectMetadata;
        }
    }
}
