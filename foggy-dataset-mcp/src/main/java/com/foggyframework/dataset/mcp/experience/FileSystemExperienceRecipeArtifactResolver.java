package com.foggyframework.dataset.mcp.experience;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "foggy.mcp.experience-recipe.registry", name = "artifact-root")
public class FileSystemExperienceRecipeArtifactResolver implements ExperienceRecipeArtifactResolver {
    private final Path artifactRoot;

    public FileSystemExperienceRecipeArtifactResolver(ExperienceRecipeRegistryProperties properties) {
        if (!hasText(properties.getArtifactRoot())) {
            throw new IllegalArgumentException("experience recipe artifact root cannot be blank");
        }
        this.artifactRoot = Path.of(properties.getArtifactRoot()).toAbsolutePath().normalize();
    }

    @Override
    public Optional<byte[]> resolve(ExperienceRecipeEvidenceArtifact artifact) {
        if (artifact == null || !artifact.hasValidArtifactUri()) {
            return Optional.empty();
        }
        URI uri = URI.create(artifact.getArtifactUri().trim());
        if (!"foggy".equalsIgnoreCase(uri.getScheme())) {
            return Optional.empty();
        }
        Path artifactPath = artifactPath(uri);
        if (!artifactPath.startsWith(artifactRoot) || !Files.isRegularFile(artifactPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(artifactPath));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private Path artifactPath(URI uri) {
        String authority = uri.getAuthority() == null ? "" : uri.getAuthority();
        String path = uri.getPath() == null ? "" : uri.getPath();
        String relativePath = authority + "/" + stripLeadingSlash(path);
        return artifactRoot.resolve(relativePath).normalize();
    }

    private static String stripLeadingSlash(String value) {
        String stripped = value;
        while (stripped.startsWith("/")) {
            stripped = stripped.substring(1);
        }
        return stripped;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
