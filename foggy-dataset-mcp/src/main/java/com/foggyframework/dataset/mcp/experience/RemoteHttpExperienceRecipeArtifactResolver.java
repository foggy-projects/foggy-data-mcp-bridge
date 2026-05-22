package com.foggyframework.dataset.mcp.experience;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(
        prefix = "foggy.mcp.experience-recipe.registry.remote-http",
        name = "enabled",
        havingValue = "true")
public class RemoteHttpExperienceRecipeArtifactResolver implements ExperienceRecipeArtifactResolver {
    private static final int DEFAULT_MAX_BYTES = 1024 * 1024;
    private static final long DEFAULT_CONNECT_TIMEOUT_MILLIS = 2000;
    private static final long DEFAULT_READ_TIMEOUT_MILLIS = 5000;
    private static final String HEADER_OBJECT_VERSION = "X-Foggy-Artifact-Object-Version";
    private static final String HEADER_OBJECT_ETAG = "X-Foggy-Artifact-Object-Etag";
    private static final String HEADER_ARTIFACT_HASH = "X-Foggy-Artifact-Hash";
    private static final String HEADER_ARTIFACT_SIZE = "X-Foggy-Artifact-Size";
    private static final String HEADER_METADATA_PREFIX = "x-foggy-artifact-metadata-";
    private static final Map<String, String> TRUSTED_METADATA_KEYS = Map.of(
            "namespace", "namespace",
            "tenant", "tenant",
            "owner", "owner",
            "registrykey", "registryKey",
            "canonicalrecipeid", "canonicalRecipeId",
            "version", "version",
            "artifacttype", "artifactType",
            "artifacthash", "artifactHash");

    private final Set<String> allowedHosts;
    private final int maxBytes;
    private final Duration readTimeout;
    private final RemoteHttpTransport transport;

    public RemoteHttpExperienceRecipeArtifactResolver(ExperienceRecipeRegistryProperties properties) {
        this(properties, new JavaNetRemoteHttpTransport(properties));
    }

    RemoteHttpExperienceRecipeArtifactResolver(
            ExperienceRecipeRegistryProperties properties,
            RemoteHttpTransport transport) {
        ExperienceRecipeRegistryProperties.RemoteHttpArtifactResolverProperties remote =
                properties == null
                        ? new ExperienceRecipeRegistryProperties.RemoteHttpArtifactResolverProperties()
                        : properties.getRemoteHttp();
        this.allowedHosts = normalizeHosts(remote.getAllowedHosts());
        this.maxBytes = remote.getMaxBytes() > 0 ? remote.getMaxBytes() : DEFAULT_MAX_BYTES;
        this.readTimeout = Duration.ofMillis(positiveOrDefault(
                remote.getReadTimeoutMillis(),
                DEFAULT_READ_TIMEOUT_MILLIS));
        this.transport = transport;
    }

    @Override
    public Optional<byte[]> resolve(ExperienceRecipeEvidenceArtifact artifact) {
        return resolveArtifact(artifact).map(ExperienceRecipeArtifactResolution::content);
    }

    @Override
    public Optional<ExperienceRecipeArtifactResolution> resolveArtifact(ExperienceRecipeEvidenceArtifact artifact) {
        if (artifact == null || !artifact.hasValidArtifactUri()) {
            return Optional.empty();
        }
        URI uri = URI.create(artifact.getArtifactUri().trim());
        if (!allowed(uri)) {
            return Optional.empty();
        }
        try {
            RemoteHttpArtifactResponse response = transport.get(uri, readTimeout);
            byte[] body = response.body() == null ? new byte[0] : response.body();
            if (response.statusCode() != 200 || body.length > maxBytes) {
                return Optional.empty();
            }
            if (!trustedResponseFactsMatch(artifact, body, response.headers())) {
                return Optional.empty();
            }
            return Optional.of(ExperienceRecipeArtifactResolution.of(
                    body,
                    headerValue(response.headers(), HEADER_OBJECT_VERSION).orElse(null),
                    headerValue(response.headers(), HEADER_OBJECT_ETAG).orElse(null),
                    trustedObjectMetadata(response.headers())));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    private boolean allowed(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || allowedHosts.isEmpty()) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        String hostWithPort = uri.getPort() > 0
                ? normalizedHost + ":" + uri.getPort()
                : normalizedHost;
        return allowedHosts.contains(normalizedHost) || allowedHosts.contains(hostWithPort);
    }

    private static boolean trustedResponseFactsMatch(
            ExperienceRecipeEvidenceArtifact artifact,
            byte[] body,
            Map<String, List<String>> headers) {
        Optional<String> responseHash = headerValue(headers, HEADER_ARTIFACT_HASH);
        if (responseHash.isPresent()) {
            String actualHash = ExperienceRecipeArtifactHash.sha256(body);
            if (!actualHash.equalsIgnoreCase(responseHash.get())) {
                return false;
            }
            if (artifact != null
                    && artifact.hasValidArtifactHash()
                    && !artifact.getArtifactHash().trim().equalsIgnoreCase(responseHash.get())) {
                return false;
            }
        }
        Optional<String> responseSize = headerValue(headers, HEADER_ARTIFACT_SIZE);
        if (responseSize.isPresent()) {
            try {
                if (Long.parseLong(responseSize.get()) != body.length) {
                    return false;
                }
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> trustedObjectMetadata(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        headers.forEach((headerName, values) -> {
            if (headerName == null) {
                return;
            }
            String trimmedHeaderName = headerName.trim();
            String normalizedHeaderName = trimmedHeaderName.toLowerCase(Locale.ROOT);
            if (!normalizedHeaderName.startsWith(HEADER_METADATA_PREFIX)) {
                return;
            }
            String metadataKey = TRUSTED_METADATA_KEYS.get(normalizeMetadataHeaderKey(
                    trimmedHeaderName.substring(HEADER_METADATA_PREFIX.length())));
            if (metadataKey == null) {
                return;
            }
            firstHeaderValue(values).ifPresent(value -> metadata.put(metadataKey, value));
        });
        return Map.copyOf(metadata);
    }

    private static Optional<String> headerValue(Map<String, List<String>> headers, String name) {
        if (headers == null || headers.isEmpty() || name == null) {
            return Optional.empty();
        }
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .map(RemoteHttpExperienceRecipeArtifactResolver::firstHeaderValue)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private static Optional<String> firstHeaderValue(Collection<String> values) {
        if (values == null) {
            return Optional.empty();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .findFirst();
    }

    private static String normalizeMetadataHeaderKey(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("-", "")
                .replace("_", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalizeHosts(Set<String> hosts) {
        if (hosts == null) {
            return Set.of();
        }
        return hosts.stream()
                .filter(host -> host != null && !host.isBlank())
                .map(host -> host.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static long positiveOrDefault(long value, long fallback) {
        return value > 0 ? value : fallback;
    }

    interface RemoteHttpTransport {
        RemoteHttpArtifactResponse get(URI uri, Duration timeout) throws IOException, InterruptedException;
    }

    record RemoteHttpArtifactResponse(int statusCode, byte[] body, Map<String, List<String>> headers) {
        RemoteHttpArtifactResponse(int statusCode, byte[] body) {
            this(statusCode, body, Map.of());
        }
    }

    private static final class JavaNetRemoteHttpTransport implements RemoteHttpTransport {
        private final HttpClient httpClient;

        private JavaNetRemoteHttpTransport(ExperienceRecipeRegistryProperties properties) {
            ExperienceRecipeRegistryProperties.RemoteHttpArtifactResolverProperties remote =
                    properties == null
                            ? new ExperienceRecipeRegistryProperties.RemoteHttpArtifactResolverProperties()
                            : properties.getRemoteHttp();
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(positiveOrDefault(
                            remote.getConnectTimeoutMillis(),
                            DEFAULT_CONNECT_TIMEOUT_MILLIS)))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
        }

        @Override
        public RemoteHttpArtifactResponse get(URI uri, Duration timeout)
                throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Accept", "application/octet-stream")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray());
            return new RemoteHttpArtifactResponse(
                    response.statusCode(),
                    response.body(),
                    response.headers().map());
        }
    }
}
