package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.candidate.CandidateContentRevision;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService.RuntimeBundleRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Ownership-bearing immutable artifacts and durable publication attempts. */
@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimePublishedBundleArtifactStore {

    private static final int VERSION = 1;
    private static final String OWNER_FILE = ".foggy-published-owner.json";
    private static final String ARTIFACT_MARKER = ".artifact.json";
    private static final String WRITE_OWNER_SUFFIX = ".owner.json";
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern STORE_ID = Pattern.compile("[A-Za-z0-9_-]{22,64}");
    private static final Pattern ATTEMPT_ID = Pattern.compile(
            UUID_PATTERN);
    private static final Pattern ARTIFACT_STAGING = Pattern.compile(
            "\\.staging-" + UUID_PATTERN);
    private static final Pattern ARTIFACT_STAGING_OWNER = Pattern.compile(
            "(\\.staging-" + UUID_PATTERN + ")\\.owner\\.json");
    private static final Pattern ATTEMPT_TEMPORARY = Pattern.compile(
            "(" + UUID_PATTERN + ")\\.json\\.tmp-" + UUID_PATTERN);
    private static final Pattern ATTEMPT_TEMPORARY_OWNER = Pattern.compile(
            "((" + UUID_PATTERN + ")\\.json\\.tmp-" + UUID_PATTERN
                    + ")\\.owner\\.json");
    private static final Set<String> ATTEMPT_STATUSES = Set.of(
            "PUBLISHING", "SOURCE_APPLIED", "PUBLISHED", "RECOVERED",
            "RECOVERY_REQUIRED", "FAILED");
    private static final Set<String> ROLLBACK_STATUSES = Set.of(
            "ROLLING_BACK", "ROLLED_BACK", "ROLLBACK_REQUIRED",
            "FORWARD_RECOVERED");

    private final FoggyRuntimeApiProperties properties;
    private final ObjectMapper objectMapper;
    private final RuntimeAuthoringStorePathPolicy pathPolicy;
    private final RuntimeBundleRegistryService bundleRegistry;
    private final SecureRandom secureRandom = new SecureRandom();
    private Path root;
    private String storeId;

    public RuntimePublishedBundleArtifactStore(
            FoggyRuntimeApiProperties properties,
            ObjectMapper objectMapper,
            RuntimeAuthoringStorePathPolicy pathPolicy,
            RuntimeBundleRegistryService bundleRegistry
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.pathPolicy = pathPolicy;
        this.bundleRegistry = bundleRegistry;
    }

    public synchronized String newAttemptId() {
        return UUID.randomUUID().toString();
    }

    public synchronized Path prepareArtifact(
            String attemptId,
            String workspaceId,
            String namespace,
            String bundle,
            String candidateRevision,
            Map<String, byte[]> snapshot
    ) {
        requireAttemptId(attemptId);
        ensureRoot();
        Map<String, byte[]> canonical = new TreeMap<>(snapshot == null ? Map.of() : snapshot);
        if (!candidateRevision.equals(CandidateContentRevision.calculate(canonical))) {
            throw failure("WORKSPACE_ARTIFACT_CORRUPT", "workspaces.publish.artifact",
                    "Candidate artifact does not match the requested revision.");
        }
        validateSnapshotPaths(canonical);
        Path target = artifactsRoot().resolve(attemptId);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            verifyArtifact(target, attemptId, workspaceId, namespace, bundle,
                    candidateRevision);
            return target;
        }
        Path staging = artifactsRoot().resolve(".staging-" + UUID.randomUUID());
        Path writeOwner = staging.resolveSibling(
                staging.getFileName() + WRITE_OWNER_SUFFIX);
        try {
            writeJson(writeOwner, new ArtifactWriteOwner(
                    VERSION, storeId, staging.getFileName().toString(),
                    attemptId, workspaceId, namespace, bundle,
                    candidateRevision));
            Files.createDirectory(staging);
            for (Map.Entry<String, byte[]> entry : canonical.entrySet()) {
                Path relative = Path.of(entry.getKey());
                Path file = staging.resolve(relative).normalize();
                Files.createDirectories(file.getParent());
                Files.write(file, entry.getValue());
            }
            writeJson(staging.resolve(ARTIFACT_MARKER), new ArtifactManifest(
                    VERSION, storeId, attemptId, workspaceId, namespace, bundle,
                    candidateRevision, Instant.now().toString()));
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw failure("WORKSPACE_ARTIFACT_STORE_FAILURE",
                        "workspaces.publish.artifact",
                        "Published artifact filesystem does not support atomic commit.");
            }
            verifyArtifact(target, attemptId, workspaceId, namespace, bundle,
                    candidateRevision);
            Files.delete(writeOwner);
            return target;
        } catch (RuntimeAuthoringWorkspaceException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw failure("WORKSPACE_ARTIFACT_STORE_FAILURE",
                    "workspaces.publish.artifact",
                    "Published artifact could not be committed.");
        }
    }

    public synchronized PublicationAttempt begin(PublicationAttempt attempt) {
        ensureRoot();
        validateAttempt(attempt);
        Path target = attemptPath(attempt.attemptId());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw failure("WORKSPACE_PUBLISH_CONFLICT", "workspaces.publish.preflight",
                    "Publication attempt already exists.");
        }
        writeAttemptAtomically(target, attempt);
        return attempt;
    }

    public synchronized PublicationAttempt update(PublicationAttempt attempt) {
        ensureRoot();
        validateAttempt(attempt);
        if (!Files.isRegularFile(attemptPath(attempt.attemptId()),
                LinkOption.NOFOLLOW_LINKS)) {
            throw failure("WORKSPACE_RECOVERY_REQUIRED", "workspaces.publish.commit",
                    "Durable publication attempt is missing.");
        }
        writeAttemptAtomically(attemptPath(attempt.attemptId()), attempt);
        return attempt;
    }

    public synchronized PublicationAttempt get(String attemptId) {
        requireAttemptId(attemptId);
        ensureRoot();
        Path path = attemptPath(attemptId);
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(path)) {
                throw failure("WORKSPACE_RECOVERY_CONFLICT", "workspaces.publish.recovery",
                        "Publication attempt was not found.");
            }
            PublicationAttempt attempt = objectMapper.readValue(
                    path.toFile(), PublicationAttempt.class);
            validateAttempt(attempt);
            return attempt;
        } catch (RuntimeAuthoringWorkspaceException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw failure("WORKSPACE_ARTIFACT_CORRUPT", "workspaces.publish.recovery",
                    "Publication attempt is corrupt.");
        }
    }

    public synchronized Path artifactPath(PublicationAttempt attempt) {
        ensureRoot();
        Path path = artifactsRoot().resolve(attempt.attemptId());
        verifyArtifact(path, attempt.attemptId(), attempt.workspaceId(),
                attempt.namespace(), attempt.bundle(), attempt.candidateRevision());
        return path;
    }

    /**
     * Verifies that an immutable registry source is an owned, completed
     * publication artifact before it can become the recovery base of another
     * publication attempt.
     */
    public synchronized Path verifyPublishedSource(RuntimeBundleRecord record) {
        try {
            ensureRoot();
            if (record == null || !record.enabled() || record.watch()
                    || !record.immutablePublication()
                    || !StringUtils.hasText(record.artifactRevision())
                    || !StringUtils.hasText(record.path())) {
                throw new IOException("published registry identity is invalid");
            }
            Path configured = Path.of(record.path()).toAbsolutePath().normalize();
            Path ownedArtifacts = artifactsRoot().toAbsolutePath().normalize();
            if (!ownedArtifacts.equals(configured.getParent())) {
                throw new IOException("published source is outside the owned artifact root");
            }
            String attemptId = configured.getFileName().toString();
            requireAttemptId(attemptId);
            PublicationAttempt attempt = get(attemptId);
            if (!"PUBLISHED".equals(attempt.status())
                    || !record.name().equals(attempt.bundle())
                    || !canonicalNamespace(record.namespace()).equals(
                    canonicalNamespace(attempt.namespace()))
                    || !record.artifactRevision().equals(
                    attempt.candidateRevision())) {
                throw new IOException("published source attempt identity mismatch");
            }
            Path verified = artifactPath(attempt).toAbsolutePath().normalize();
            if (!configured.equals(verified)) {
                throw new IOException("published source path mismatch");
            }
            return verified;
        } catch (IOException | RuntimeException failure) {
            throw failure("WORKSPACE_ARTIFACT_CORRUPT",
                    "workspaces.publish.preflight",
                    "Published Bundle ownership or content is corrupt.");
        }
    }

    private void verifyArtifact(
            Path artifact,
            String attemptId,
            String workspaceId,
            String namespace,
            String bundle,
            String revision
    ) {
        try {
            if (!Files.isDirectory(artifact, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(artifact)) {
                throw new IOException("artifact directory is invalid");
            }
            Path marker = artifact.resolve(ARTIFACT_MARKER);
            ArtifactManifest manifest = objectMapper.readValue(
                    marker.toFile(), ArtifactManifest.class);
            if (manifest == null || manifest.version() != VERSION
                    || !storeId.equals(manifest.storeId())
                    || !attemptId.equals(manifest.attemptId())
                    || !workspaceId.equals(manifest.workspaceId())
                    || !namespace.equals(manifest.namespace())
                    || !bundle.equals(manifest.bundle())
                    || !revision.equals(manifest.candidateRevision())) {
                throw new IOException("artifact ownership mismatch");
            }
            Map<String, byte[]> snapshot = new TreeMap<>();
            try (Stream<Path> paths = Files.walk(artifact)) {
                for (Path path : paths.toList()) {
                    if (Files.isSymbolicLink(path)) {
                        throw new IOException("artifact contains a symlink");
                    }
                    if (!Files.isRegularFile(path)) {
                        continue;
                    }
                    Path relative = artifact.relativize(path);
                    if (ARTIFACT_MARKER.equals(relative.toString())) {
                        continue;
                    }
                    if (!CandidateContentRevision.isCandidateResource(relative)) {
                        throw new IOException("artifact contains an unknown file");
                    }
                    snapshot.put(relative.toString().replace('\\', '/'),
                            Files.readAllBytes(path));
                }
            }
            if (!revision.equals(CandidateContentRevision.calculate(snapshot))) {
                throw new IOException("artifact revision mismatch");
            }
        } catch (RuntimeAuthoringWorkspaceException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw failure("WORKSPACE_ARTIFACT_CORRUPT", "workspaces.publish.artifact",
                    "Published artifact ownership or content is corrupt.");
        }
    }

    private void ensureRoot() {
        if (root != null) {
            recoverInterruptedWrites(root);
            validateRoot(root);
            return;
        }
        if (pathPolicy != null) {
            pathPolicy.assertStoreDisjoint(bundleRegistry == null
                    ? List.of() : bundleRegistry.listRecords());
        }
        FoggyRuntimeApiProperties.AuthoringWorkspaces configured =
                properties.getAuthoringWorkspaces();
        String value = configured == null
                ? null : configured.getPublishedBundlesPath();
        Path candidate;
        try {
            candidate = Path.of(StringUtils.hasText(value)
                            ? value : ".foggy-runtime/published-bundles")
                    .toAbsolutePath().normalize();
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(candidate);
            }
            if (Files.isSymbolicLink(candidate) || !Files.isDirectory(candidate)
                    || !candidate.equals(candidate.toRealPath())) {
                throw new IOException("published root is not a real directory");
            }
            Path owner = candidate.resolve(OWNER_FILE);
            if (!Files.exists(owner, LinkOption.NOFOLLOW_LINKS)) {
                try (Stream<Path> children = Files.list(candidate)) {
                    if (children.findAny().isPresent()) {
                        throw failure("WORKSPACE_ARTIFACT_STORE_FAILURE",
                                "workspaces.publish.artifact",
                                "Published artifact root is non-empty and unowned.");
                    }
                }
                storeId = newStoreId();
                writeRootOwnerAtomically(owner, new RootOwner(VERSION, storeId));
                Files.createDirectory(candidate.resolve("artifacts"));
                Files.createDirectory(candidate.resolve("attempts"));
            } else {
                RootOwner rootOwner = objectMapper.readValue(
                        owner.toFile(), RootOwner.class);
                if (rootOwner == null || rootOwner.version() != VERSION
                        || !STORE_ID.matcher(rootOwner.storeId()).matches()) {
                    throw new IOException("published root ownership mismatch");
                }
                storeId = rootOwner.storeId();
            }
            root = candidate;
            recoverInterruptedWrites(candidate);
            validateRoot(candidate);
        } catch (RuntimeAuthoringWorkspaceException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw failure("WORKSPACE_ARTIFACT_STORE_FAILURE",
                    "workspaces.publish.artifact",
                    "Published artifact root could not be initialized.");
        }
    }

    private void validateRoot(Path candidate) {
        try {
            if (Files.isSymbolicLink(candidate)
                    || !Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("invalid published root");
            }
            List<String> names = new ArrayList<>();
            try (Stream<Path> children = Files.list(candidate)) {
                for (Path child : children.toList()) {
                    if (Files.isSymbolicLink(child)) {
                        throw new IOException("published root contains symlink");
                    }
                    names.add(child.getFileName().toString());
                }
            }
            if (!names.stream().allMatch(name -> OWNER_FILE.equals(name)
                    || "artifacts".equals(name) || "attempts".equals(name))
                    || !Files.isDirectory(candidate.resolve("artifacts"), LinkOption.NOFOLLOW_LINKS)
                    || !Files.isDirectory(candidate.resolve("attempts"), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("published root contains foreign entries");
            }
            validateArtifactEntries(candidate.resolve("artifacts"));
            validateAttemptEntries(candidate.resolve("attempts"));
        } catch (IOException | RuntimeException failure) {
            throw failure("WORKSPACE_ARTIFACT_CORRUPT", "workspaces.publish.artifact",
                    "Published artifact root contains unowned or corrupt data.");
        }
    }

    private void validateArtifactEntries(Path artifacts) throws IOException {
        try (Stream<Path> entries = Files.list(artifacts)) {
            for (Path artifact : entries.toList()) {
                String attemptId = artifact.getFileName().toString();
                if (!ATTEMPT_ID.matcher(attemptId).matches()
                        || Files.isSymbolicLink(artifact)
                        || !Files.isDirectory(artifact, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("published artifact root contains an unknown entry");
                }
                ArtifactManifest manifest = objectMapper.readValue(
                        artifact.resolve(ARTIFACT_MARKER).toFile(),
                        ArtifactManifest.class);
                if (manifest == null || !attemptId.equals(manifest.attemptId())) {
                    throw new IOException("published artifact identity mismatch");
                }
                verifyArtifact(artifact, manifest.attemptId(),
                        manifest.workspaceId(), manifest.namespace(),
                        manifest.bundle(), manifest.candidateRevision());
            }
        }
    }

    private void validateAttemptEntries(Path attempts) throws IOException {
        try (Stream<Path> entries = Files.list(attempts)) {
            for (Path path : entries.toList()) {
                String name = path.getFileName().toString();
                if (Files.isSymbolicLink(path)
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || !name.endsWith(".json")) {
                    throw new IOException("publication attempt root contains an unknown entry");
                }
                String attemptId = name.substring(0, name.length() - 5);
                if (!ATTEMPT_ID.matcher(attemptId).matches()) {
                    throw new IOException("publication attempt filename is invalid");
                }
                PublicationAttempt attempt = objectMapper.readValue(
                        path.toFile(), PublicationAttempt.class);
                validateAttempt(attempt);
                if (!attemptId.equals(attempt.attemptId())) {
                    throw new IOException("publication attempt identity mismatch");
                }
            }
        }
    }

    private Path artifactsRoot() {
        return root.resolve("artifacts");
    }

    private Path attemptPath(String attemptId) {
        return root.resolve("attempts").resolve(attemptId + ".json");
    }

    private void writeAttemptAtomically(Path target, PublicationAttempt value) {
        Path temporary = target.resolveSibling(target.getFileName()
                + ".tmp-" + UUID.randomUUID());
        Path writeOwner = temporary.resolveSibling(
                temporary.getFileName() + WRITE_OWNER_SUFFIX);
        try {
            writeJson(writeOwner, new AttemptWriteOwner(
                    VERSION, storeId, temporary.getFileName().toString(),
                    target.getFileName().toString(), value.attemptId()));
            writeJson(temporary, value);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            Files.delete(writeOwner);
        } catch (AtomicMoveNotSupportedException unsupported) {
            throw failure("WORKSPACE_ARTIFACT_STORE_FAILURE",
                    "workspaces.publish.commit",
                    "Published artifact filesystem does not support atomic metadata commit.");
        } catch (IOException | RuntimeException failure) {
            throw failure("WORKSPACE_ARTIFACT_STORE_FAILURE",
                    "workspaces.publish.commit",
                    "Publication metadata could not be committed.");
        }
    }

    private void writeRootOwnerAtomically(Path target, RootOwner value) {
        Path temporary = target.resolveSibling(target.getFileName()
                + ".tmp-" + UUID.randomUUID());
        try {
            writeJson(temporary, value);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            throw failure("WORKSPACE_ARTIFACT_STORE_FAILURE",
                    "workspaces.publish.artifact",
                    "Published artifact filesystem does not support atomic ownership commit.");
        } catch (IOException | RuntimeException failure) {
            throw failure("WORKSPACE_ARTIFACT_STORE_FAILURE",
                    "workspaces.publish.artifact",
                    "Published artifact ownership could not be committed.");
        }
    }

    private void recoverInterruptedWrites(Path candidate) {
        try {
            if (Files.isSymbolicLink(candidate)
                    || !Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("invalid published root");
            }
            List<String> rootNames = new ArrayList<>();
            try (Stream<Path> children = Files.list(candidate)) {
                for (Path child : children.toList()) {
                    if (Files.isSymbolicLink(child)) {
                        throw new IOException("published root contains symlink");
                    }
                    rootNames.add(child.getFileName().toString());
                }
            }
            if (!rootNames.stream().allMatch(name -> OWNER_FILE.equals(name)
                    || "artifacts".equals(name) || "attempts".equals(name))) {
                throw new IOException("published root contains foreign entries");
            }
            Path artifacts = candidate.resolve("artifacts");
            Path attempts = candidate.resolve("attempts");
            if (!plainDirectory(artifacts) || !plainDirectory(attempts)) {
                throw new IOException("published store roots are unsafe");
            }
            List<InterruptedWrite> recoveries = new ArrayList<>();
            preflightArtifactRecoveries(artifacts, recoveries);
            preflightAttemptRecoveries(attempts, recoveries);
            for (InterruptedWrite recovery : recoveries) {
                if (recovery.object() != null) {
                    if (recovery.directory()) {
                        deleteOwnedDirectory(recovery.object(), recovery.parent());
                    } else {
                        requireRegularFile(recovery.object());
                        Files.delete(recovery.object());
                    }
                }
                requireRegularFile(recovery.marker());
                Files.delete(recovery.marker());
            }
        } catch (IOException | RuntimeException failure) {
            throw failure("WORKSPACE_ARTIFACT_CORRUPT",
                    "workspaces.publish.artifact",
                    "Published artifact recovery could not safely complete.");
        }
    }

    private void preflightArtifactRecoveries(
            Path artifacts,
            List<InterruptedWrite> recoveries
    ) throws IOException {
        Map<String, Path> staging = new TreeMap<>();
        Map<String, Path> owners = new TreeMap<>();
        try (Stream<Path> entries = Files.list(artifacts)) {
            for (Path entry : entries.toList()) {
                String name = entry.getFileName().toString();
                if (Files.isSymbolicLink(entry)) {
                    throw new IOException("artifact root contains symlink");
                }
                if (ATTEMPT_ID.matcher(name).matches()) {
                    ArtifactManifest manifest = objectMapper.readValue(
                            entry.resolve(ARTIFACT_MARKER).toFile(),
                            ArtifactManifest.class);
                    if (manifest == null || !name.equals(manifest.attemptId())) {
                        throw new IOException("artifact identity mismatch");
                    }
                    verifyArtifact(entry, manifest.attemptId(),
                            manifest.workspaceId(), manifest.namespace(),
                            manifest.bundle(), manifest.candidateRevision());
                    continue;
                }
                var ownerMatch = ARTIFACT_STAGING_OWNER.matcher(name);
                if (ownerMatch.matches()) {
                    requireRegularFile(entry);
                    owners.put(ownerMatch.group(1), entry);
                    continue;
                }
                if (ARTIFACT_STAGING.matcher(name).matches()
                        && plainDirectory(entry)) {
                    staging.put(name, entry);
                    continue;
                }
                throw new IOException("artifact root contains unknown entry");
            }
        }
        if (!owners.keySet().containsAll(staging.keySet())) {
            throw new IOException("artifact staging is unowned");
        }
        for (Map.Entry<String, Path> entry : owners.entrySet()) {
            ArtifactWriteOwner owner = objectMapper.readValue(
                    entry.getValue().toFile(), ArtifactWriteOwner.class);
            String stagingName = entry.getKey();
            if (owner == null || owner.version() != VERSION
                    || !storeId.equals(owner.storeId())
                    || !stagingName.equals(owner.stagingName())
                    || !ATTEMPT_ID.matcher(nullToEmpty(owner.attemptId())).matches()
                    || !StringUtils.hasText(owner.workspaceId())
                    || !StringUtils.hasText(owner.namespace())
                    || !StringUtils.hasText(owner.bundle())
                    || !owner.candidateRevision().matches(
                    "sha256:[0-9a-f]{64}")) {
                throw new IOException("artifact staging owner mismatch");
            }
            Path partial = staging.get(stagingName);
            Path committed = artifacts.resolve(owner.attemptId());
            if (Files.exists(committed, LinkOption.NOFOLLOW_LINKS)) {
                verifyArtifact(committed, owner.attemptId(), owner.workspaceId(),
                        owner.namespace(), owner.bundle(), owner.candidateRevision());
            }
            if (partial != null) {
                validateInterruptedArtifact(partial, owner);
                recoveries.add(new InterruptedWrite(
                        artifacts, partial, entry.getValue(), true));
            } else if (Files.exists(committed, LinkOption.NOFOLLOW_LINKS)) {
                recoveries.add(new InterruptedWrite(
                        artifacts, null, entry.getValue(), false));
            } else {
                recoveries.add(new InterruptedWrite(
                        artifacts, null, entry.getValue(), false));
            }
        }
    }

    private void preflightAttemptRecoveries(
            Path attempts,
            List<InterruptedWrite> recoveries
    ) throws IOException {
        Map<String, Path> temporaries = new TreeMap<>();
        Map<String, Path> owners = new TreeMap<>();
        try (Stream<Path> entries = Files.list(attempts)) {
            for (Path entry : entries.toList()) {
                String name = entry.getFileName().toString();
                if (Files.isSymbolicLink(entry)) {
                    throw new IOException("attempt root contains symlink");
                }
                if (name.endsWith(".json")
                        && ATTEMPT_ID.matcher(name.substring(0,
                        name.length() - 5)).matches()) {
                    validateAttemptFile(entry,
                            name.substring(0, name.length() - 5));
                    continue;
                }
                var ownerMatch = ATTEMPT_TEMPORARY_OWNER.matcher(name);
                if (ownerMatch.matches()) {
                    requireRegularFile(entry);
                    owners.put(ownerMatch.group(1), entry);
                    continue;
                }
                if (ATTEMPT_TEMPORARY.matcher(name).matches()) {
                    requireRegularFile(entry);
                    temporaries.put(name, entry);
                    continue;
                }
                throw new IOException("attempt root contains unknown entry");
            }
        }
        if (!owners.keySet().containsAll(temporaries.keySet())) {
            throw new IOException("attempt temporary is unowned");
        }
        for (Map.Entry<String, Path> entry : owners.entrySet()) {
            AttemptWriteOwner owner = objectMapper.readValue(
                    entry.getValue().toFile(), AttemptWriteOwner.class);
            String temporaryName = entry.getKey();
            var temporaryMatch = ATTEMPT_TEMPORARY.matcher(temporaryName);
            if (owner == null || owner.version() != VERSION
                    || !storeId.equals(owner.storeId())
                    || !temporaryName.equals(owner.temporaryName())
                    || !temporaryMatch.matches()
                    || !owner.attemptId().equals(temporaryMatch.group(1))
                    || !(owner.attemptId() + ".json").equals(owner.finalName())) {
                throw new IOException("attempt temporary owner mismatch");
            }
            Path temporary = temporaries.get(temporaryName);
            Path committed = attempts.resolve(owner.finalName());
            if (temporary != null) {
                validateAttemptFile(temporary, owner.attemptId());
            }
            if (Files.exists(committed, LinkOption.NOFOLLOW_LINKS)) {
                validateAttemptFile(committed, owner.attemptId());
            }
            recoveries.add(new InterruptedWrite(
                    attempts, temporary, entry.getValue(), false));
        }
    }

    private void validateInterruptedArtifact(
            Path staging,
            ArtifactWriteOwner owner
    ) throws IOException {
        if (!plainDirectory(staging)) {
            throw new IOException("artifact staging is unsafe");
        }
        try (Stream<Path> paths = Files.walk(staging)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("artifact staging contains symlink");
                }
                if (path.equals(staging)
                        || Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                requireRegularFile(path);
                Path relative = staging.relativize(path);
                if (ARTIFACT_MARKER.equals(relative.toString())) {
                    ArtifactManifest manifest = objectMapper.readValue(
                            path.toFile(), ArtifactManifest.class);
                    if (manifest == null || manifest.version() != VERSION
                            || !storeId.equals(manifest.storeId())
                            || !owner.attemptId().equals(manifest.attemptId())
                            || !owner.workspaceId().equals(manifest.workspaceId())
                            || !owner.namespace().equals(manifest.namespace())
                            || !owner.bundle().equals(manifest.bundle())
                            || !owner.candidateRevision().equals(
                            manifest.candidateRevision())) {
                        throw new IOException("artifact staging manifest mismatch");
                    }
                } else if (!CandidateContentRevision.isCandidateResource(relative)) {
                    throw new IOException("artifact staging contains foreign data");
                }
            }
        }
    }

    private void validateAttemptFile(Path path, String attemptId)
            throws IOException {
        requireRegularFile(path);
        PublicationAttempt attempt = objectMapper.readValue(
                path.toFile(), PublicationAttempt.class);
        validateAttempt(attempt);
        if (!attemptId.equals(attempt.attemptId())) {
            throw new IOException("attempt identity mismatch");
        }
    }

    private static void deleteOwnedDirectory(Path directory, Path parent)
            throws IOException {
        if (!plainDirectory(directory)
                || !parent.equals(directory.getParent())) {
            throw new IOException("owned directory identity changed");
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> ordered = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path path : ordered) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("owned directory contains symlink");
                }
                Files.delete(path);
            }
        }
    }

    private static boolean plainDirectory(Path path) {
        return path != null && !Files.isSymbolicLink(path)
                && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static void requireRegularFile(Path path) throws IOException {
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("unsafe file");
        }
    }

    private static void validateSnapshotPaths(Map<String, byte[]> snapshot) {
        for (String name : snapshot.keySet()) {
            Path relative = Path.of(name);
            if (!CandidateContentRevision.isCandidateResource(relative)
                    || relative.isAbsolute() || name.contains("\\")
                    || relative.normalize().startsWith("..")) {
                throw failure("WORKSPACE_ARTIFACT_CORRUPT",
                        "workspaces.publish.artifact",
                        "Candidate artifact contains an invalid resource path.");
            }
        }
    }

    private void writeJson(Path target, Object value) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), value);
    }

    private void validateAttempt(PublicationAttempt attempt) {
        if (attempt == null || attempt.version() != VERSION
                || !ATTEMPT_ID.matcher(nullToEmpty(attempt.attemptId())).matches()
                || !StringUtils.hasText(attempt.workspaceId())
                || !StringUtils.hasText(attempt.namespace())
                || !StringUtils.hasText(attempt.bundle())
                || !StringUtils.hasText(attempt.candidateRevision())
                || !StringUtils.hasText(attempt.baseBundleRevision())
                || !StringUtils.hasText(attempt.baseSourceRevision())
                || !StringUtils.hasText(attempt.previousPath())
                || !ATTEMPT_STATUSES.contains(attempt.status())
                || !StringUtils.hasText(attempt.startedAt())) {
            throw failure("WORKSPACE_ARTIFACT_CORRUPT", "workspaces.publish.commit",
                    "Publication attempt metadata is invalid.");
        }
        RollbackAttempt rollback = attempt.rollback();
        if (rollback == null) {
            return;
        }
        try {
            if (!"PUBLISHED".equals(attempt.status())
                    || !ROLLBACK_STATUSES.contains(rollback.status())
                    || !StringUtils.hasText(rollback.startedAt())) {
                throw new IllegalArgumentException("invalid rollback metadata");
            }
            Instant.parse(rollback.startedAt());
            if (StringUtils.hasText(rollback.completedAt())) {
                Instant.parse(rollback.completedAt());
            }
            if ("ROLLED_BACK".equals(rollback.status())
                    && (!StringUtils.hasText(rollback.rolledBackSourceRevision())
                    || !StringUtils.hasText(
                    rollback.rolledBackCatalogGeneration())
                    || !StringUtils.hasText(rollback.completedAt()))) {
                throw new IllegalArgumentException("incomplete rollback evidence");
            }
            if ("FORWARD_RECOVERED".equals(rollback.status())
                    && (!StringUtils.hasText(
                    rollback.forwardRecoveredSourceRevision())
                    || !StringUtils.hasText(
                    rollback.forwardRecoveredCatalogGeneration())
                    || !StringUtils.hasText(rollback.completedAt()))) {
                throw new IllegalArgumentException(
                        "incomplete forward recovery evidence");
            }
        } catch (RuntimeException invalid) {
            throw failure("WORKSPACE_ARTIFACT_CORRUPT",
                    "workspaces.rollback.commit",
                    "Publication rollback metadata is invalid.");
        }
    }

    private static void requireAttemptId(String attemptId) {
        if (!ATTEMPT_ID.matcher(nullToEmpty(attemptId)).matches()) {
            throw failure("WORKSPACE_INVALID_REQUEST", "workspaces.publish.preflight",
                    "Publication attempt identity is invalid.");
        }
    }

    private String newStoreId() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static RuntimeAuthoringWorkspaceException failure(
            String code,
            String phase,
            String message
    ) {
        return new RuntimeAuthoringWorkspaceException(
                code, phase, message, null, false);
    }

    private static String canonicalNamespace(String namespace) {
        return namespace == null ? "" : namespace.trim();
    }

    public record PublicationAttempt(
            int version,
            String attemptId,
            String workspaceId,
            String namespace,
            String bundle,
            String candidateRevision,
            String baseBundleRevision,
            String baseSourceRevision,
            String previousPath,
            boolean previousWatch,
            boolean previousEnabled,
            String previousCreatedAt,
            String previousUpdatedAt,
            boolean previousImmutablePublication,
            String previousArtifactRevision,
            String status,
            String publishedSourceRevision,
            String beforeCatalogGeneration,
            String afterCatalogGeneration,
            String recoveredCatalogGeneration,
            String startedAt,
            String completedAt,
            List<String> diagnostics,
            RollbackAttempt rollback
    ) {
        public PublicationAttempt {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        /** Compatibility constructor retaining the pre-rollback attempt surface. */
        public PublicationAttempt(
                int version,
                String attemptId,
                String workspaceId,
                String namespace,
                String bundle,
                String candidateRevision,
                String baseBundleRevision,
                String baseSourceRevision,
                String previousPath,
                boolean previousWatch,
                boolean previousEnabled,
                String previousCreatedAt,
                String previousUpdatedAt,
                boolean previousImmutablePublication,
                String previousArtifactRevision,
                String status,
                String publishedSourceRevision,
                String beforeCatalogGeneration,
                String afterCatalogGeneration,
                String recoveredCatalogGeneration,
                String startedAt,
                String completedAt,
                List<String> diagnostics
        ) {
            this(version, attemptId, workspaceId, namespace, bundle,
                    candidateRevision, baseBundleRevision, baseSourceRevision,
                    previousPath, previousWatch, previousEnabled,
                    previousCreatedAt, previousUpdatedAt,
                    previousImmutablePublication, previousArtifactRevision,
                    status, publishedSourceRevision, beforeCatalogGeneration,
                    afterCatalogGeneration, recoveredCatalogGeneration,
                    startedAt, completedAt, diagnostics, null);
        }

        public PublicationAttempt withStatus(
                String nextStatus,
                String sourceRevision,
                String beforeGeneration,
                String afterGeneration,
                String recoveredGeneration,
                String completed,
                List<String> nextDiagnostics
        ) {
            return new PublicationAttempt(version, attemptId, workspaceId,
                    namespace, bundle, candidateRevision, baseBundleRevision,
                    baseSourceRevision, previousPath, previousWatch,
                    previousEnabled, previousCreatedAt, previousUpdatedAt,
                    previousImmutablePublication, previousArtifactRevision,
                    nextStatus, sourceRevision,
                    beforeGeneration, afterGeneration, recoveredGeneration,
                    startedAt, completed, nextDiagnostics, rollback);
        }

        public PublicationAttempt withRollback(RollbackAttempt nextRollback) {
            return new PublicationAttempt(version, attemptId, workspaceId,
                    namespace, bundle, candidateRevision, baseBundleRevision,
                    baseSourceRevision, previousPath, previousWatch,
                    previousEnabled, previousCreatedAt, previousUpdatedAt,
                    previousImmutablePublication, previousArtifactRevision,
                    status, publishedSourceRevision, beforeCatalogGeneration,
                    afterCatalogGeneration, recoveredCatalogGeneration,
                    startedAt, completedAt, diagnostics, nextRollback);
        }
    }

    public record RollbackAttempt(
            String status,
            String startedAt,
            String rolledBackSourceRevision,
            String rolledBackCatalogGeneration,
            String completedAt,
            String forwardRecoveredSourceRevision,
            String forwardRecoveredCatalogGeneration,
            List<String> diagnostics
    ) {
        public RollbackAttempt {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }

    private record RootOwner(int version, String storeId) {
    }

    private record ArtifactWriteOwner(
            int version,
            String storeId,
            String stagingName,
            String attemptId,
            String workspaceId,
            String namespace,
            String bundle,
            String candidateRevision
    ) {
    }

    private record AttemptWriteOwner(
            int version,
            String storeId,
            String temporaryName,
            String finalName,
            String attemptId
    ) {
    }

    private record InterruptedWrite(
            Path parent,
            Path object,
            Path marker,
            boolean directory
    ) {
    }

    private record ArtifactManifest(
            int version,
            String storeId,
            String attemptId,
            String workspaceId,
            String namespace,
            String bundle,
            String candidateRevision,
            String createdAt
    ) {
    }
}
