package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.candidate.CandidateContentRevision;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceDiffResponse;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo.PublicationEvidence;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo.ReleaseImportEvidence;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo.RollbackEvidence;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceLimits;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceResource;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeAuthoringWorkspaceStore {

    private static final int SCHEMA_VERSION = 2;
    private static final int LEGACY_SCHEMA_VERSION = 1;
    private static final int WORKSPACE_MARKER_VERSION = 1;
    private static final String REGISTRY_FILE = "workspaces.json";
    private static final String WORKSPACE_MARKER_FILE = ".workspace-owner.json";
    private static final String MIGRATION_FILE = ".migration-v2.json";
    private static final Pattern WORKSPACE_ID =
            Pattern.compile("[A-Za-z0-9_-]{22,64}");
    private static final Pattern STORE_ID =
            Pattern.compile("[A-Za-z0-9_-]{22,64}");
    private static final Pattern REVISION_DIRECTORY =
            Pattern.compile("[0-9a-f]{64}");
    private static final Pattern STAGING_DIRECTORY = Pattern.compile(
            "\\.staging-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern REGISTRY_TEMP_FILE = Pattern.compile(
            Pattern.quote(REGISTRY_FILE) + "\\.tmp-" + UUID_PATTERN);
    private static final Pattern MIGRATION_TEMP_FILE = Pattern.compile(
            Pattern.quote(MIGRATION_FILE) + "\\.tmp-" + UUID_PATTERN);
    private static final Pattern WORKSPACE_MARKER_TEMP_FILE = Pattern.compile(
            Pattern.quote(WORKSPACE_MARKER_FILE) + "\\.tmp-" + UUID_PATTERN);

    private final FoggyRuntimeApiProperties properties;
    private final ObjectMapper objectMapper;
    private final RuntimeAuthoringStorePathPolicy pathPolicy;
    private final RuntimeBundleRegistryService bundleRegistry;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, StoredWorkspace> workspaces = new LinkedHashMap<>();
    private final Map<RevisionKey, Integer> leases = new HashMap<>();
    private String storeId;
    private boolean loaded;

    public RuntimeAuthoringWorkspaceStore(
            FoggyRuntimeApiProperties properties,
            ObjectMapper objectMapper
    ) {
        this(properties, objectMapper, null, null);
    }

    @Autowired
    public RuntimeAuthoringWorkspaceStore(
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

    public synchronized StoredWorkspace create(
            String namespace,
            String sourceBundle,
            String baseSourceRevision,
            String baseSourceIdentity,
            Map<String, byte[]> resources
    ) {
        loadIfNeeded();
        long active = workspaces.values().stream()
                .filter(record -> !isTerminal(record.state()))
                .count();
        if (active >= limits().maxActiveWorkspaces()) {
            throw failure("WORKSPACE_LIMIT_EXCEEDED", "workspaces.create",
                    "Active workspace limit has been reached.", null, false);
        }
        Map<String, byte[]> snapshot = validateSnapshot(resources, "workspaces.create");
        String revision = CandidateContentRevision.calculate(snapshot);
        String workspaceId = newWorkspaceId();
        String now = Instant.now().toString();
        StoredWorkspace record = new StoredWorkspace(
                workspaceId,
                namespace,
                sourceBundle,
                "runtime-managed",
                baseSourceIdentity,
                revision,
                baseSourceRevision,
                revision,
                AuthoringWorkspaceState.DRAFT,
                now,
                now,
                null,
                null
        );
        validateStoredRecord(record);
        Path workspaceRoot = workspacePath(workspaceId);
        boolean created = false;
        try {
            Files.createDirectory(workspaceRoot);
            created = true;
            writeWorkspaceMarker(workspaceRoot, workspaceId);
            Files.createDirectory(workspaceRoot.resolve("revisions"));
            stageRevision(record, snapshot);
            Map<String, StoredWorkspace> next = new LinkedHashMap<>(workspaces);
            next.put(workspaceId, record);
            persist(next);
            workspaces.clear();
            workspaces.putAll(next);
            return record;
        } catch (RuntimeAuthoringWorkspaceException e) {
            if (created) {
                cleanupWorkspaceAfterFailedCreate(workspaceRoot, workspaceId);
            }
            throw e;
        } catch (IOException | RuntimeException e) {
            if (created) {
                cleanupWorkspaceAfterFailedCreate(workspaceRoot, workspaceId);
            }
            throw storeFailure("workspaces.create", e);
        }
    }

    public synchronized StoredWorkspace createImported(
            String namespace,
            String sourceBundle,
            String baseSourceRevision,
            String baseSourceIdentity,
            Map<String, byte[]> baseResources,
            Map<String, byte[]> candidateResources,
            ReleaseImportEvidence releaseImport
    ) {
        loadIfNeeded();
        long active = workspaces.values().stream()
                .filter(record -> !isTerminal(record.state()))
                .count();
        if (active >= limits().maxActiveWorkspaces()) {
            throw failure("WORKSPACE_LIMIT_EXCEEDED", "workspaces.release.import",
                    "Active workspace limit has been reached.", null, false);
        }
        Map<String, byte[]> base = validateSnapshot(
                baseResources, "workspaces.release.import");
        Map<String, byte[]> candidate = validateSnapshot(
                candidateResources, "workspaces.release.import");
        String baseRevision = CandidateContentRevision.calculate(base);
        String candidateRevision = CandidateContentRevision.calculate(candidate);
        if (releaseImport == null
                || !candidateRevision.equals(releaseImport.exportedCandidateRevision())) {
            throw failure("WORKSPACE_RELEASE_PACKAGE_INVALID",
                    "workspaces.release.import",
                    "Release import provenance does not match candidate content.",
                    null, false);
        }
        String workspaceId = newWorkspaceId();
        String now = Instant.now().toString();
        StoredWorkspace record = new StoredWorkspace(
                workspaceId, namespace, sourceBundle, "runtime-managed",
                baseSourceIdentity, baseRevision, baseSourceRevision,
                candidateRevision, AuthoringWorkspaceState.DRAFT,
                now, now, null, null, releaseImport, null);
        validateStoredRecord(record);
        Path workspaceRoot = workspacePath(workspaceId);
        boolean created = false;
        try {
            Files.createDirectory(workspaceRoot);
            created = true;
            writeWorkspaceMarker(workspaceRoot, workspaceId);
            Files.createDirectory(workspaceRoot.resolve("revisions"));
            stageRevision(record.withCandidateRevision(baseRevision), base);
            stageRevision(record, candidate);
            Map<String, StoredWorkspace> next = new LinkedHashMap<>(workspaces);
            next.put(workspaceId, record);
            persist(next);
            workspaces.clear();
            workspaces.putAll(next);
            return record;
        } catch (RuntimeAuthoringWorkspaceException failure) {
            if (created) {
                cleanupWorkspaceAfterFailedCreate(workspaceRoot, workspaceId);
            }
            throw failure;
        } catch (IOException | RuntimeException failure) {
            if (created) {
                cleanupWorkspaceAfterFailedCreate(workspaceRoot, workspaceId);
            }
            throw storeFailure("workspaces.release.import", failure);
        }
    }

    public synchronized List<StoredWorkspace> list(
            String namespace,
            AuthoringWorkspaceState state,
            boolean includeDiscarded
    ) {
        loadIfNeeded();
        String canonicalNamespace = canonicalNamespace(namespace);
        return workspaces.values().stream()
                .filter(record -> includeDiscarded
                        || record.state() != AuthoringWorkspaceState.DISCARDED)
                .filter(record -> !StringUtils.hasText(namespace)
                        || canonicalNamespace.equals(record.namespace()))
                .filter(record -> state == null || state == record.state())
                .sorted(Comparator.comparing(StoredWorkspace::createdAt)
                        .thenComparing(StoredWorkspace::workspaceId))
                .toList();
    }

    public synchronized StoredWorkspace get(String workspaceId) {
        loadIfNeeded();
        StoredWorkspace record = workspaces.get(validWorkspaceId(workspaceId));
        if (record == null) {
            throw failure("WORKSPACE_NOT_FOUND", "workspaces.get",
                    "Authoring workspace was not found.", null, false);
        }
        return record;
    }

    /** Holds the store monitor while read-only lifecycle diagnostics inspect disk state. */
    synchronized <T> T withLifecycleInventorySnapshot(
            Function<Set<LifecycleRevisionLease>, T> reader
    ) {
        Set<LifecycleRevisionLease> snapshot = leases.keySet().stream()
                .map(key -> new LifecycleRevisionLease(
                        key.workspaceId(), key.revision()))
                .collect(Collectors.toUnmodifiableSet());
        return reader.apply(snapshot);
    }

    public synchronized Map<String, byte[]> snapshot(
            String workspaceId,
            String expectedRevision
    ) {
        StoredWorkspace record = requireCurrent(
                workspaceId, expectedRevision, "workspaces.resources");
        requireReadable(record, "workspaces.resources");
        return readRevision(record, record.candidateRevision());
    }

    public synchronized StoredWorkspace replace(
            String workspaceId,
            String expectedRevision,
            Map<String, byte[]> desiredResources
    ) {
        return replace(workspaceId, expectedRevision, desiredResources,
                () -> { });
    }

    public synchronized StoredWorkspace replace(
            String workspaceId,
            String expectedRevision,
            Map<String, byte[]> desiredResources,
            Runnable commitGuard
    ) {
        StoredWorkspace current = requireCurrent(
                workspaceId, expectedRevision, "workspaces.resources.save");
        requireMutable(current, "workspaces.resources.save");
        if (current.releaseImport() != null) {
            throw failure("WORKSPACE_RELEASE_IMMUTABLE",
                    "workspaces.resources.save",
                    "Imported release package candidate is immutable.", null, false);
        }
        Map<String, byte[]> snapshot = validateSnapshot(
                desiredResources, "workspaces.resources.save");
        String revision = CandidateContentRevision.calculate(snapshot);
        if (revision.equals(current.candidateRevision())) {
            if (commitGuard != null) {
                commitGuard.run();
            }
            return current;
        }
        Path revisionPath = null;
        boolean newRevision = false;
        try {
            revisionPath = revisionPath(current.workspaceId(), revision);
            newRevision = !Files.exists(revisionPath);
            stageRevision(current.withCandidateRevision(revision), snapshot);
            if (commitGuard != null) {
                commitGuard.run();
            }
            AuthoringWorkspaceState state = current.state() == AuthoringWorkspaceState.STALE
                    ? AuthoringWorkspaceState.STALE
                    : AuthoringWorkspaceState.DRAFT;
            StoredWorkspace updated = new StoredWorkspace(
                    current.workspaceId(), current.namespace(), current.sourceBundle(),
                    current.sourceKind(), current.baseSourceIdentity(),
                    current.baseBundleRevision(), current.baseSourceRevision(),
                    revision, state, current.createdAt(), Instant.now().toString(),
                    null, null, current.releaseImport(), current.staleReason());
            replaceRecord(updated);
            cleanupRevision(updated, current.candidateRevision());
            return updated;
        } catch (RuntimeAuthoringWorkspaceException e) {
            if (newRevision && revisionPath != null) {
                deleteOwnedTreeQuietly(revisionPath, current.workspaceId());
            }
            throw e;
        } catch (IOException | RuntimeException e) {
            if (newRevision && revisionPath != null) {
                deleteOwnedTreeQuietly(revisionPath, current.workspaceId());
            }
            throw storeFailure("workspaces.resources.save", e);
        }
    }

    public synchronized List<AuthoringWorkspaceResource> listResources(
            String workspaceId,
            String expectedRevision
    ) {
        Map<String, byte[]> snapshot = snapshot(workspaceId, expectedRevision);
        List<AuthoringWorkspaceResource> resources = new ArrayList<>();
        snapshot.forEach((path, bytes) -> resources.add(resource(path, bytes, false)));
        return List.copyOf(resources);
    }

    public synchronized AuthoringWorkspaceResource readResource(
            String workspaceId,
            String expectedRevision,
            String path
    ) {
        String canonical = canonicalResourcePath(path, "workspaces.resources.read");
        Map<String, byte[]> snapshot = snapshot(workspaceId, expectedRevision);
        byte[] content = snapshot.get(canonical);
        if (content == null) {
            throw failure("WORKSPACE_RESOURCE_NOT_FOUND", "workspaces.resources.read",
                    "Workspace resource was not found.", canonical, true);
        }
        return resource(canonical, content, true);
    }

    public synchronized AuthoringWorkspaceDiffResponse diff(
            String workspaceId,
            String expectedRevision,
            boolean includeContent
    ) {
        StoredWorkspace record = requireCurrent(
                workspaceId, expectedRevision, "workspaces.diff");
        requireReadable(record, "workspaces.diff");
        Map<String, byte[]> base = readRevision(record, record.baseBundleRevision());
        Map<String, byte[]> candidate = readRevision(record, record.candidateRevision());
        Set<String> paths = new HashSet<>(base.keySet());
        paths.addAll(candidate.keySet());
        List<AuthoringWorkspaceDiffResponse.ResourceChange> changes = new ArrayList<>();
        paths.stream().sorted().forEach(path -> {
            byte[] before = base.get(path);
            byte[] after = candidate.get(path);
            if (before != null && after != null && MessageDigest.isEqual(before, after)) {
                return;
            }
            String changeType = before == null ? "ADDED" : after == null ? "DELETED" : "MODIFIED";
            changes.add(new AuthoringWorkspaceDiffResponse.ResourceChange(
                    path,
                    resourceType(path),
                    changeType,
                    before == null ? null : sha256(before),
                    after == null ? null : sha256(after),
                    includeContent && before != null ? decodeUtf8(before) : null,
                    includeContent && after != null ? decodeUtf8(after) : null
            ));
        });
        return new AuthoringWorkspaceDiffResponse(
                record.workspaceId(), record.baseBundleRevision(),
                record.candidateRevision(), changes);
    }

    public synchronized RevisionLease acquire(
            String workspaceId,
            String expectedRevision,
            String phase
    ) {
        StoredWorkspace record = requireCurrent(workspaceId, expectedRevision, phase);
        requireExecutable(record, phase);
        Path path = revisionPath(record.workspaceId(), record.candidateRevision());
        verifyRevision(record, record.candidateRevision());
        RevisionKey key = new RevisionKey(record.workspaceId(), record.candidateRevision());
        leases.merge(key, 1, Integer::sum);
        return new RevisionLease(this, record, path, key);
    }

    public synchronized StoredWorkspace recordValidation(
            String workspaceId,
            String expectedRevision,
            AuthoringWorkspaceInfo.ValidationEvidence evidence
    ) {
        StoredWorkspace current = requireCurrent(
                workspaceId, expectedRevision, "workspaces.validate");
        requireExecutable(current, "workspaces.validate");
        AuthoringWorkspaceState state = current.state() == AuthoringWorkspaceState.STALE
                ? AuthoringWorkspaceState.STALE
                : evidence.valid() ? AuthoringWorkspaceState.VALIDATED
                : AuthoringWorkspaceState.DRAFT;
        StoredWorkspace updated = new StoredWorkspace(
                current.workspaceId(), current.namespace(), current.sourceBundle(),
                current.sourceKind(), current.baseSourceIdentity(),
                current.baseBundleRevision(), current.baseSourceRevision(),
                current.candidateRevision(), state, current.createdAt(),
                Instant.now().toString(), evidence, current.lastPublication(),
                current.releaseImport(), current.staleReason());
        replaceRecord(updated);
        return updated;
    }

    public synchronized StoredWorkspace markStale(String workspaceId, String reason) {
        StoredWorkspace current = get(workspaceId);
        if (current.state() == AuthoringWorkspaceState.DISCARDED
                || current.state() == AuthoringWorkspaceState.PUBLISHED
                || current.state() == AuthoringWorkspaceState.PUBLISHING
                || current.state() == AuthoringWorkspaceState.RECOVERY_REQUIRED
                || current.state() == AuthoringWorkspaceState.ROLLING_BACK
                || current.state() == AuthoringWorkspaceState.ROLLBACK_REQUIRED
                || current.state() == AuthoringWorkspaceState.ROLLED_BACK
                || current.state() == AuthoringWorkspaceState.STALE) {
            return current;
        }
        StoredWorkspace updated = new StoredWorkspace(
                current.workspaceId(), current.namespace(), current.sourceBundle(),
                current.sourceKind(), current.baseSourceIdentity(),
                current.baseBundleRevision(), current.baseSourceRevision(),
                current.candidateRevision(), AuthoringWorkspaceState.STALE,
                current.createdAt(), Instant.now().toString(),
                current.lastValidation(), current.lastPublication(),
                current.releaseImport(), sanitizeReason(reason));
        replaceRecord(updated);
        return updated;
    }

    public synchronized StoredWorkspace discard(
            String workspaceId,
            String expectedRevision
    ) {
        StoredWorkspace current = requireCurrent(
                workspaceId, expectedRevision, "workspaces.discard");
        requireMutable(current, "workspaces.discard");
        StoredWorkspace updated = new StoredWorkspace(
                current.workspaceId(), current.namespace(), current.sourceBundle(),
                current.sourceKind(), current.baseSourceIdentity(),
                current.baseBundleRevision(), current.baseSourceRevision(),
                current.candidateRevision(), AuthoringWorkspaceState.DISCARDED,
                current.createdAt(), Instant.now().toString(),
                current.lastValidation(), current.lastPublication(),
                current.releaseImport(), current.staleReason());
        replaceRecord(updated);
        cleanupRevision(updated, updated.baseBundleRevision());
        cleanupRevision(updated, updated.candidateRevision());
        return updated;
    }

    /** Roll back a just-created workspace when the source drifts during create. */
    synchronized void rollbackCreate(String workspaceId) {
        loadIfNeeded();
        String id = validWorkspaceId(workspaceId);
        StoredWorkspace current = workspaces.get(id);
        if (current == null) {
            return;
        }
        if (current.state() != AuthoringWorkspaceState.DRAFT
                || !current.baseBundleRevision().equals(
                current.candidateRevision())
                || current.lastValidation() != null) {
            throw failure("WORKSPACE_STATE_INVALID", "workspaces.create",
                    "Workspace can no longer be rolled back as a new draft.",
                    null, false);
        }
        Map<String, StoredWorkspace> next = new LinkedHashMap<>(workspaces);
        next.remove(id);
        persist(next);
        workspaces.clear();
        workspaces.putAll(next);
        deleteOwnedTreeQuietly(workspacePath(id), id);
    }

    synchronized void rollbackImportedCreate(String workspaceId) {
        loadIfNeeded();
        String id = validWorkspaceId(workspaceId);
        StoredWorkspace current = workspaces.get(id);
        if (current == null) {
            return;
        }
        if (current.state() != AuthoringWorkspaceState.DRAFT
                || current.releaseImport() == null
                || current.lastValidation() != null
                || current.lastPublication() != null) {
            throw failure("WORKSPACE_STATE_INVALID", "workspaces.release.import",
                    "Imported workspace can no longer be rolled back.", null, false);
        }
        Map<String, StoredWorkspace> next = new LinkedHashMap<>(workspaces);
        next.remove(id);
        persist(next);
        workspaces.clear();
        workspaces.putAll(next);
        deleteOwnedTreeQuietly(workspacePath(id), id);
    }

    public AuthoringWorkspaceLimits limits() {
        FoggyRuntimeApiProperties.AuthoringWorkspaces configured = configuration();
        return new AuthoringWorkspaceLimits(
                positive(configured.getMaxActiveWorkspaces(), "max-active-workspaces"),
                positive(configured.getMaxResourcesPerRevision(), "max-resources-per-revision"),
                positive(configured.getMaxResourceBytes(), "max-resource-bytes"),
                positive(configured.getMaxRevisionBytes(), "max-revision-bytes"),
                positive(configured.getMaxBatchOperations(), "max-batch-operations"),
                positive(configured.getMaxPathBytes(), "max-path-bytes")
        );
    }

    public AuthoringWorkspaceInfo toInfo(StoredWorkspace record) {
        List<String> diagnostics = StringUtils.hasText(record.staleReason())
                ? List.of(record.staleReason()) : List.of();
        return new AuthoringWorkspaceInfo(
                record.workspaceId(), record.namespace(), record.sourceBundle(),
                record.sourceKind(), record.baseBundleRevision(),
                record.baseSourceRevision(), record.candidateRevision(),
                record.state(), record.createdAt(), record.updatedAt(),
                record.lastValidation(), record.lastPublication(),
                record.releaseImport(), diagnostics);
    }

    public synchronized StoredWorkspace beginPublication(
            String workspaceId,
            String expectedRevision,
            PublicationEvidence evidence
    ) {
        StoredWorkspace current = requireCurrent(
                workspaceId, expectedRevision, "workspaces.publish.preflight");
        if (current.state() != AuthoringWorkspaceState.VALIDATED
                || evidence == null || current.lastPublication() != null) {
            throw failure("WORKSPACE_NOT_VALIDATED", "workspaces.publish.preflight",
                    "Exact workspace revision is not available for publication.",
                    null, false);
        }
        StoredWorkspace updated = current.withPublication(
                AuthoringWorkspaceState.PUBLISHING, evidence, null);
        replaceRecord(updated);
        return updated;
    }

    public synchronized StoredWorkspace markPublished(
            String workspaceId,
            String attemptId,
            PublicationEvidence evidence
    ) {
        return transitionPublication(workspaceId, attemptId,
                AuthoringWorkspaceState.PUBLISHED, evidence, null);
    }

    public synchronized StoredWorkspace beginRollback(
            String workspaceId,
            String attemptId,
            PublicationEvidence evidence
    ) {
        StoredWorkspace current = get(workspaceId);
        PublicationEvidence previous = current.lastPublication();
        if (current.state() != AuthoringWorkspaceState.PUBLISHED
                || current.releaseImport() == null
                || previous == null || !StringUtils.hasText(attemptId)
                || !attemptId.equals(previous.attemptId())
                || evidence == null || !attemptId.equals(evidence.attemptId())
                || evidence.rollback() == null
                || !"ROLLING_BACK".equals(evidence.rollback().status())) {
            throw failure("WORKSPACE_ROLLBACK_CONFLICT",
                    "workspaces.rollback.preflight",
                    "Published release is no longer eligible for rollback.",
                    null, false);
        }
        StoredWorkspace updated = current.withPublication(
                AuthoringWorkspaceState.ROLLING_BACK, evidence, null);
        replaceRecord(updated);
        return updated;
    }

    public synchronized StoredWorkspace markRolledBack(
            String workspaceId,
            String attemptId,
            PublicationEvidence evidence
    ) {
        return transitionRollback(workspaceId, attemptId,
                AuthoringWorkspaceState.ROLLED_BACK, evidence, null);
    }

    public synchronized StoredWorkspace markRollbackRequired(
            String workspaceId,
            String attemptId,
            PublicationEvidence evidence,
            String reason
    ) {
        return transitionRollback(workspaceId, attemptId,
                AuthoringWorkspaceState.ROLLBACK_REQUIRED, evidence, reason);
    }

    public synchronized StoredWorkspace markForwardRecovered(
            String workspaceId,
            String attemptId,
            PublicationEvidence evidence,
            String reason
    ) {
        return transitionRollback(workspaceId, attemptId,
                AuthoringWorkspaceState.PUBLISHED, evidence, reason);
    }

    private StoredWorkspace transitionRollback(
            String workspaceId,
            String attemptId,
            AuthoringWorkspaceState nextState,
            PublicationEvidence evidence,
            String reason
    ) {
        StoredWorkspace current = get(workspaceId);
        PublicationEvidence previous = current.lastPublication();
        if ((current.state() != AuthoringWorkspaceState.ROLLING_BACK
                && current.state() != AuthoringWorkspaceState.ROLLBACK_REQUIRED)
                || previous == null || !StringUtils.hasText(attemptId)
                || !attemptId.equals(previous.attemptId())
                || evidence == null || !attemptId.equals(evidence.attemptId())
                || evidence.rollback() == null) {
            throw failure("WORKSPACE_ROLLBACK_CONFLICT",
                    "workspaces.rollback.commit",
                    "Rollback attempt is no longer current.", null, false);
        }
        StoredWorkspace updated = current.withPublication(
                nextState, evidence, sanitizeReason(reason));
        replaceRecord(updated);
        return updated;
    }

    public synchronized StoredWorkspace markRecoveryRequired(
            String workspaceId,
            String attemptId,
            PublicationEvidence evidence,
            String reason
    ) {
        return transitionPublication(workspaceId, attemptId,
                AuthoringWorkspaceState.RECOVERY_REQUIRED, evidence, reason);
    }

    public synchronized StoredWorkspace markRecovered(
            String workspaceId,
            String attemptId,
            PublicationEvidence evidence,
            String reason
    ) {
        return transitionPublication(workspaceId, attemptId,
                AuthoringWorkspaceState.STALE, evidence, reason);
    }

    private StoredWorkspace transitionPublication(
            String workspaceId,
            String attemptId,
            AuthoringWorkspaceState nextState,
            PublicationEvidence evidence,
            String reason
    ) {
        StoredWorkspace current = get(workspaceId);
        PublicationEvidence previous = current.lastPublication();
        if (previous == null || !StringUtils.hasText(attemptId)
                || !attemptId.equals(previous.attemptId())
                || evidence == null || !attemptId.equals(evidence.attemptId())) {
            throw failure("WORKSPACE_PUBLISH_CONFLICT", "workspaces.publish.commit",
                    "Publication attempt is no longer current.", null, false);
        }
        if (current.state() != AuthoringWorkspaceState.PUBLISHING
                && current.state() != AuthoringWorkspaceState.RECOVERY_REQUIRED) {
            throw failure("WORKSPACE_STATE_INVALID", "workspaces.publish.commit",
                    "Workspace cannot transition publication state.", null, false);
        }
        StoredWorkspace updated = current.withPublication(
                nextState, evidence, sanitizeReason(reason));
        replaceRecord(updated);
        return updated;
    }

    private StoredWorkspace requireCurrent(
            String workspaceId,
            String expectedRevision,
            String phase
    ) {
        StoredWorkspace record = get(workspaceId);
        if (!StringUtils.hasText(expectedRevision)
                || !record.candidateRevision().equals(expectedRevision.trim())) {
            throw failure("WORKSPACE_REVISION_CONFLICT", phase,
                    "Workspace candidate revision is no longer current.", null, true);
        }
        return record;
    }

    private static void requireReadable(StoredWorkspace record, String phase) {
        if (record.state() == AuthoringWorkspaceState.DISCARDED) {
            throw failure("WORKSPACE_STATE_INVALID", phase,
                    "Discarded workspace cannot perform this action.", null, false);
        }
    }

    private static void requireMutable(StoredWorkspace record, String phase) {
        if (record.state() != AuthoringWorkspaceState.DRAFT
                && record.state() != AuthoringWorkspaceState.VALIDATED
                && record.state() != AuthoringWorkspaceState.STALE) {
            throw failure("WORKSPACE_STATE_INVALID", phase,
                    "Workspace state does not allow source mutation.", null, false);
        }
    }

    private static void requireExecutable(StoredWorkspace record, String phase) {
        requireMutable(record, phase);
    }

    private void replaceRecord(StoredWorkspace updated) {
        validateStoredRecord(updated);
        Map<String, StoredWorkspace> next = new LinkedHashMap<>(workspaces);
        next.put(updated.workspaceId(), updated);
        persist(next);
        workspaces.clear();
        workspaces.putAll(next);
    }

    private Path stageRevision(
            StoredWorkspace record,
            Map<String, byte[]> snapshot
    ) throws IOException {
        Path workspace = workspacePath(record.workspaceId());
        requireWorkspaceOwnership(workspace, record.workspaceId());
        Path revisions = workspace.resolve("revisions");
        requirePlainDirectory(revisions,
                "Workspace revision root is missing or unsafe.");
        assertNoSymlinkComponents(secureRoot(), revisions);
        Path target = revisionPath(record.workspaceId(), record.candidateRevision());
        if (Files.exists(target)) {
            Map<String, byte[]> existing = readSnapshotDirectory(
                    target, record.candidateRevision(), "workspaces.store");
            if (!snapshotsEqual(existing, snapshot)) {
                throw corrupt("Existing revision content does not match its identity.", null);
            }
            return target;
        }
        Path staging = workspace
                .resolve(".staging-" + UUID.randomUUID());
        try {
            Files.createDirectory(staging);
            writeSnapshot(staging, snapshot);
            String stagedRevision = CandidateContentRevision.calculate(snapshot);
            if (!record.candidateRevision().equals(stagedRevision)) {
                throw corrupt("Staged revision identity mismatch.", null);
            }
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw storeFailure("workspaces.store", unsupported);
            }
            return target;
        } finally {
            deleteOwnedTreeQuietly(staging, record.workspaceId());
        }
    }

    private void writeSnapshot(Path root, Map<String, byte[]> snapshot) throws IOException {
        for (Map.Entry<String, byte[]> entry : snapshot.entrySet()) {
            Path target = root.resolve(entry.getKey()).normalize();
            if (!target.startsWith(root)) {
                throw failure("WORKSPACE_RESOURCE_PATH_INVALID", "workspaces.store",
                        "Workspace resource path escapes revision root.", entry.getKey(), false);
            }
            Files.createDirectories(target.getParent());
            assertNoSymlinkComponents(root, target.getParent());
            Files.write(target, entry.getValue());
        }
    }

    private Map<String, byte[]> readRevision(StoredWorkspace record, String revision) {
        if (record.state() == AuthoringWorkspaceState.DISCARDED) {
            throw failure("WORKSPACE_STATE_INVALID", "workspaces.store",
                    "Discarded workspace has no readable revisions.", null, false);
        }
        return readSnapshotDirectory(
                revisionPath(record.workspaceId(), revision), revision,
                "workspaces.store");
    }

    private Map<String, byte[]> readSnapshotDirectory(
            Path root,
            String expectedRevision,
            String phase
    ) {
        try {
            assertNoSymlinkComponents(secureRoot(), root);
            if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
                throw corrupt("Workspace revision directory is missing or unsafe.", null);
            }
            Map<String, byte[]> resources = new TreeMap<>();
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.toList()) {
                    if (path.equals(root)) {
                        continue;
                    }
                    if (Files.isSymbolicLink(path)) {
                        throw corrupt("Workspace revision contains a symbolic link.", null);
                    }
                    if (Files.isDirectory(path)) {
                        continue;
                    }
                    if (!Files.isRegularFile(path)) {
                        throw corrupt(
                                "Workspace revision contains an unsupported entry.",
                                null);
                    }
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    String canonical;
                    try {
                        canonical = canonicalResourcePath(relative, phase);
                    } catch (RuntimeAuthoringWorkspaceException invalid) {
                        throw corrupt("Workspace revision contains an invalid resource path.", relative);
                    }
                    resources.put(canonical, Files.readAllBytes(path));
                }
            }
            Map<String, byte[]> validated;
            try {
                validated = validateSnapshot(resources, phase);
            } catch (RuntimeAuthoringWorkspaceException invalid) {
                throw corrupt("Workspace revision violates configured limits.", null);
            }
            if (!expectedRevision.equals(CandidateContentRevision.calculate(validated))) {
                throw corrupt("Workspace revision hash mismatch.", null);
            }
            return validated;
        } catch (RuntimeAuthoringWorkspaceException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw corrupt("Workspace revision could not be read.", null, e);
        }
    }

    private void verifyRevision(StoredWorkspace record, String revision) {
        readRevision(record, revision);
    }

    private Map<String, byte[]> validateSnapshot(
            Map<String, byte[]> resources,
            String phase
    ) {
        Map<String, byte[]> ordered = new TreeMap<>();
        Set<String> folded = new HashSet<>();
        long total = 0L;
        if (resources != null) {
            for (Map.Entry<String, byte[]> entry : resources.entrySet()) {
                String path = canonicalResourcePath(entry.getKey(), phase);
                byte[] content = entry.getValue();
                if (content == null) {
                    throw failure("WORKSPACE_INVALID_REQUEST", phase,
                            "Workspace resource content is required.", path, false);
                }
                if (!folded.add(path.toLowerCase(Locale.ROOT))) {
                    throw failure("WORKSPACE_RESOURCE_PATH_INVALID", phase,
                            "Workspace resource paths collide by case.", path, false);
                }
                if (content.length > limits().maxResourceBytes()) {
                    throw failure("WORKSPACE_LIMIT_EXCEEDED", phase,
                            "Workspace resource exceeds the configured size limit.", path, false);
                }
                decodeUtf8(content, phase, path);
                total += content.length;
                if (total > limits().maxRevisionBytes()) {
                    throw failure("WORKSPACE_LIMIT_EXCEEDED", phase,
                            "Workspace revision exceeds the configured size limit.", null, false);
                }
                ordered.put(path, content.clone());
            }
        }
        if (ordered.size() > limits().maxResourcesPerRevision()) {
            throw failure("WORKSPACE_LIMIT_EXCEEDED", phase,
                    "Workspace revision contains too many resources.", null, false);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(ordered));
    }

    public String canonicalResourcePath(String value, String phase) {
        if (!StringUtils.hasText(value) || value.startsWith("/")
                || value.endsWith("/") || value.contains("\\")
                || value.contains("//")) {
            throw invalidPath(phase, value);
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > limits().maxPathBytes()) {
            throw failure("WORKSPACE_LIMIT_EXCEEDED", phase,
                    "Workspace resource path exceeds the configured limit.", null, false);
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character <= 0x1f || character == 0x7f) {
                throw invalidPath(phase, null);
            }
        }
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw invalidPath(phase, value);
            }
        }
        Path parsed;
        try {
            parsed = Path.of(value);
        } catch (RuntimeException invalid) {
            throw invalidPath(phase, null);
        }
        if (parsed.isAbsolute() || !parsed.normalize().toString()
                .replace('\\', '/').equals(value)) {
            throw invalidPath(phase, value);
        }
        if (!CandidateContentRevision.isCandidateResource(parsed)) {
            throw failure("WORKSPACE_RESOURCE_TYPE_UNSUPPORTED", phase,
                    "Only .tm, .qm, and .fsscript resources are supported.", value, false);
        }
        return value;
    }

    private void loadIfNeeded() {
        if (loaded) {
            return;
        }
        Path root = prepareRootForLoad();
        Path registry = root.resolve(REGISTRY_FILE);
        Map<String, StoredWorkspace> loadedRecords = new LinkedHashMap<>();
        try {
            if (!Files.exists(registry, LinkOption.NOFOLLOW_LINKS)) {
                if (!isDirectoryEmpty(root)) {
                    throw storeFailure("workspaces.store", null);
                }
                storeId = newStoreId();
                persistAtRoot(root, Map.of());
                workspaces.clear();
                loaded = true;
                return;
            }
            if (Files.isSymbolicLink(registry) || !Files.isRegularFile(registry)) {
                throw corrupt("Workspace registry path is unsafe.", null);
            }
            RegistryFile file = objectMapper.readValue(
                    registry.toFile(), RegistryFile.class);
            if (file == null || (file.version() != SCHEMA_VERSION
                    && file.version() != LEGACY_SCHEMA_VERSION)) {
                throw corrupt("Workspace registry schema is unsupported.", null);
            }
            for (StoredWorkspace record : file.workspaces()) {
                validateStoredRecord(record);
                if (loadedRecords.put(record.workspaceId(), record) != null) {
                    throw corrupt("Workspace registry contains a duplicate identity.", null);
                }
            }
            if (file.version() == LEGACY_SCHEMA_VERSION) {
                migrateLegacyStore(root, registry, loadedRecords);
            } else {
                requireStoreId(file.storeId());
                storeId = file.storeId();
                verifyOwnedRecords(loadedRecords);
            }
            boolean reconciled = reconcileInterruptedPublications(loadedRecords);
            if (reconciled) {
                persistAtRoot(root, loadedRecords);
            }
            cleanupOrphans(root, loadedRecords);
            workspaces.clear();
            workspaces.putAll(loadedRecords);
            loaded = true;
        } catch (RuntimeAuthoringWorkspaceException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw corrupt("Workspace registry could not be loaded.", null, e);
        }
    }

    private void cleanupOrphans(
            Path root,
            Map<String, StoredWorkspace> records
    ) throws IOException {
        List<CleanupTarget> cleanupTargets = new ArrayList<>();
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.toList()) {
                String childName = child.getFileName().toString();
                if (childName.equals(REGISTRY_FILE)) {
                    continue;
                }
                if (childName.equals(MIGRATION_FILE)) {
                    MigrationFile migration = readMigrationFile(child);
                    if (!storeId.equals(migration.storeId())) {
                        throw corrupt("Workspace migration ownership mismatch.", null);
                    }
                    cleanupTargets.add(new CleanupTarget(
                            child, null, CleanupType.FILE));
                    continue;
                }
                if (REGISTRY_TEMP_FILE.matcher(childName).matches()) {
                    requireTemporaryRegistryOwnership(child);
                    cleanupTargets.add(new CleanupTarget(
                            child, null, CleanupType.FILE));
                    continue;
                }
                if (MIGRATION_TEMP_FILE.matcher(childName).matches()) {
                    MigrationFile migration = readMigrationFile(child);
                    if (!storeId.equals(migration.storeId())) {
                        throw corrupt("Workspace migration staging ownership mismatch.", null);
                    }
                    cleanupTargets.add(new CleanupTarget(
                            child, null, CleanupType.FILE));
                    continue;
                }
                if (!WORKSPACE_ID.matcher(childName).matches()) {
                    throw corrupt("Workspace store contains an unknown root entry.", null);
                }
                requireWorkspaceOwnership(child, childName);
                StoredWorkspace record = records.get(childName);
                if (record == null
                        || record.state() == AuthoringWorkspaceState.DISCARDED) {
                    validateWorkspaceLayout(child, null, cleanupTargets, false);
                    cleanupTargets.add(new CleanupTarget(
                            child, childName, CleanupType.TREE));
                    continue;
                }
                validateWorkspaceLayout(child, record, cleanupTargets, true);
            }
        }
        for (CleanupTarget target : cleanupTargets) {
            if (target.type() == CleanupType.FILE) {
                deleteOwnedFile(target);
            } else {
                deleteOwnedTree(target.path(), target.workspaceId());
            }
        }
    }

    private void deleteOwnedFile(CleanupTarget target) throws IOException {
        String name = target.path().getFileName().toString();
        if (target.workspaceId() != null) {
            Path workspace = secureRoot().resolve(target.workspaceId());
            requireWorkspaceOwnership(workspace, target.workspaceId());
            if (!WORKSPACE_MARKER_TEMP_FILE.matcher(name).matches()) {
                throw new IOException("unsafe owned workspace file cleanup target");
            }
            readWorkspaceMarker(target.path(), target.workspaceId());
        } else if (REGISTRY_TEMP_FILE.matcher(name).matches()) {
            requireTemporaryRegistryOwnership(target.path());
        } else if (MIGRATION_FILE.equals(name)
                || MIGRATION_TEMP_FILE.matcher(name).matches()) {
            MigrationFile migration = readMigrationFile(target.path());
            if (!storeId.equals(migration.storeId())) {
                throw new IOException("unsafe owned migration cleanup target");
            }
        } else {
            throw new IOException("unsafe owned root file cleanup target");
        }
        Files.deleteIfExists(target.path());
    }

    private void migrateLegacyStore(
            Path root,
            Path registry,
            Map<String, StoredWorkspace> records
    ) throws IOException {
        String registryDigest = sha256(Files.readAllBytes(registry));
        Path migrationPath = root.resolve(MIGRATION_FILE);
        MigrationFile migration = null;
        if (Files.exists(migrationPath, LinkOption.NOFOLLOW_LINKS)) {
            migration = readMigrationFile(migrationPath);
            if (!registryDigest.equals(migration.registrySha256())) {
                throw corrupt("Workspace migration source changed.", null);
            }
            storeId = migration.storeId();
        }
        validateLegacyLayout(root, records, migration != null);
        if (migration == null) {
            storeId = newStoreId();
            migration = new MigrationFile(
                    WORKSPACE_MARKER_VERSION, storeId, registryDigest);
            writeJsonAtomically(migrationPath, migration);
        }
        for (StoredWorkspace record : records.values()) {
            Path workspace = root.resolve(record.workspaceId());
            if (!Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            Path marker = workspace.resolve(WORKSPACE_MARKER_FILE);
            if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
                requireWorkspaceOwnership(workspace, record.workspaceId());
            } else {
                writeWorkspaceMarker(workspace, record.workspaceId());
            }
        }
        persistAtRoot(root, records);
        verifyOwnedRecords(records);
    }

    private void validateLegacyLayout(
            Path root,
            Map<String, StoredWorkspace> records,
            boolean migrationInProgress
    ) throws IOException {
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.toList()) {
                String name = child.getFileName().toString();
                if (REGISTRY_FILE.equals(name)) {
                    continue;
                }
                if (MIGRATION_FILE.equals(name) && migrationInProgress) {
                    continue;
                }
                StoredWorkspace record = records.get(name);
                if (record == null || !WORKSPACE_ID.matcher(name).matches()) {
                    throw corrupt("Legacy workspace store contains an unknown entry.", null);
                }
                validateLegacyWorkspace(child, record, migrationInProgress);
            }
        }
        for (StoredWorkspace record : records.values()) {
            Path workspace = root.resolve(record.workspaceId());
            if (record.state() != AuthoringWorkspaceState.DISCARDED
                    && !Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) {
                throw corrupt("Legacy workspace directory is missing.", null);
            }
        }
    }

    private void validateLegacyWorkspace(
            Path workspace,
            StoredWorkspace record,
            boolean migrationInProgress
    ) throws IOException {
        requirePlainDirectory(workspace,
                "Legacy workspace directory is unsafe.");
        Path revisions = null;
        try (Stream<Path> children = Files.list(workspace)) {
            for (Path child : children.toList()) {
                String name = child.getFileName().toString();
                if (WORKSPACE_MARKER_FILE.equals(name)) {
                    if (!migrationInProgress) {
                        throw corrupt("Legacy workspace contains unknown ownership metadata.", null);
                    }
                    requireWorkspaceOwnership(workspace, record.workspaceId());
                } else if (WORKSPACE_MARKER_TEMP_FILE.matcher(name).matches()) {
                    if (!migrationInProgress) {
                        throw corrupt("Legacy workspace contains unknown marker staging.", null);
                    }
                    readWorkspaceMarker(child, record.workspaceId());
                } else if ("revisions".equals(name)) {
                    requirePlainDirectory(child,
                            "Legacy revision root is unsafe.");
                    revisions = child;
                } else if (STAGING_DIRECTORY.matcher(name).matches()) {
                    assertSafeTree(child);
                } else {
                    throw corrupt("Legacy workspace contains an unknown entry.", null);
                }
            }
        }
        if (record.state() != AuthoringWorkspaceState.DISCARDED
                && revisions == null) {
            throw corrupt("Legacy workspace revision root is missing.", null);
        }
        if (revisions != null) {
            Set<String> found = new HashSet<>();
            try (Stream<Path> revisionPaths = Files.list(revisions)) {
                for (Path revision : revisionPaths.toList()) {
                    String name = revision.getFileName().toString();
                    if (!REVISION_DIRECTORY.matcher(name).matches()) {
                        throw corrupt("Legacy workspace has an unknown revision entry.", null);
                    }
                    readSnapshotDirectory(revision, "sha256:" + name,
                            "workspaces.store");
                    found.add(name);
                }
            }
            if (record.state() != AuthoringWorkspaceState.DISCARDED
                    && (!found.contains(revisionName(record.baseBundleRevision()))
                    || !found.contains(revisionName(record.candidateRevision())))) {
                throw corrupt("Legacy workspace revision is missing.", null);
            }
        }
    }

    private void verifyOwnedRecords(Map<String, StoredWorkspace> records) {
        Path root = secureRoot();
        for (StoredWorkspace record : records.values()) {
            Path workspace = root.resolve(record.workspaceId());
            if (record.state() == AuthoringWorkspaceState.DISCARDED
                    && !Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            requireWorkspaceOwnership(workspace, record.workspaceId());
            if (record.state() != AuthoringWorkspaceState.DISCARDED) {
                verifyRevision(record, record.baseBundleRevision());
                verifyRevision(record, record.candidateRevision());
            }
        }
    }

    private void validateWorkspaceLayout(
            Path workspace,
            StoredWorkspace record,
            List<CleanupTarget> cleanupTargets,
            boolean collectCleanup
    ) throws IOException {
        Path revisions = null;
        try (Stream<Path> children = Files.list(workspace)) {
            for (Path child : children.toList()) {
                String name = child.getFileName().toString();
                if (WORKSPACE_MARKER_FILE.equals(name)) {
                    continue;
                }
                if (WORKSPACE_MARKER_TEMP_FILE.matcher(name).matches()) {
                    readWorkspaceMarker(child,
                            workspace.getFileName().toString());
                    if (collectCleanup) {
                        cleanupTargets.add(new CleanupTarget(
                                child, workspace.getFileName().toString(),
                                CleanupType.FILE));
                    }
                    continue;
                }
                if ("revisions".equals(name)) {
                    requirePlainDirectory(child,
                            "Workspace revision root is unsafe.");
                    revisions = child;
                    continue;
                }
                if (STAGING_DIRECTORY.matcher(name).matches()) {
                    assertSafeTree(child);
                    if (collectCleanup) {
                        cleanupTargets.add(new CleanupTarget(
                                child, workspace.getFileName().toString(),
                                CleanupType.TREE));
                    }
                    continue;
                }
                throw corrupt("Workspace contains an unknown entry.", null);
            }
        }
        if (record != null && revisions == null) {
            throw corrupt("Workspace revision root is missing.", null);
        }
        if (revisions == null) {
            return;
        }
        try (Stream<Path> revisionPaths = Files.list(revisions)) {
            for (Path revision : revisionPaths.toList()) {
                String name = revision.getFileName().toString();
                if (!REVISION_DIRECTORY.matcher(name).matches()) {
                    throw corrupt("Workspace has an unknown revision entry.", null);
                }
                boolean retained = record != null
                        && (name.equals(revisionName(record.baseBundleRevision()))
                        || name.equals(revisionName(record.candidateRevision())));
                if (!retained) {
                    readSnapshotDirectory(revision, "sha256:" + name,
                            "workspaces.store");
                    if (collectCleanup) {
                        cleanupTargets.add(new CleanupTarget(
                                revision, workspace.getFileName().toString(),
                                CleanupType.TREE));
                    }
                }
            }
        }
    }

    private void requireWorkspaceOwnership(Path workspace, String workspaceId) {
        requirePlainDirectory(workspace, "Workspace directory is unsafe.");
        Path markerPath = workspace.resolve(WORKSPACE_MARKER_FILE);
        readWorkspaceMarker(markerPath, workspaceId);
    }

    private WorkspaceMarker readWorkspaceMarker(
            Path markerPath,
            String workspaceId
    ) {
        try {
            if (Files.isSymbolicLink(markerPath)
                    || !Files.isRegularFile(markerPath)) {
                throw corrupt("Workspace ownership metadata is missing or unsafe.", null);
            }
            WorkspaceMarker marker = objectMapper.readValue(
                    markerPath.toFile(), WorkspaceMarker.class);
            if (marker == null || marker.version() != WORKSPACE_MARKER_VERSION
                    || !storeId.equals(marker.storeId())
                    || !workspaceId.equals(marker.workspaceId())) {
                throw corrupt("Workspace ownership metadata does not match.", null);
            }
            return marker;
        } catch (RuntimeAuthoringWorkspaceException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw corrupt("Workspace ownership metadata could not be read.", null,
                    failure);
        }
    }

    private void requireTemporaryRegistryOwnership(Path path) {
        try {
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) {
                throw corrupt("Workspace registry staging path is unsafe.", null);
            }
            RegistryFile staged = objectMapper.readValue(
                    path.toFile(), RegistryFile.class);
            if (staged == null || staged.version() != SCHEMA_VERSION
                    || !storeId.equals(staged.storeId())) {
                throw corrupt("Workspace registry staging ownership mismatch.", null);
            }
        } catch (RuntimeAuthoringWorkspaceException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw corrupt("Workspace registry staging could not be read.", null,
                    failure);
        }
    }

    private void writeWorkspaceMarker(Path workspace, String workspaceId) {
        requireStoreId(storeId);
        writeJsonAtomically(workspace.resolve(WORKSPACE_MARKER_FILE),
                new WorkspaceMarker(
                        WORKSPACE_MARKER_VERSION, storeId, workspaceId));
    }

    private MigrationFile readMigrationFile(Path path) {
        try {
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) {
                throw corrupt("Workspace migration metadata is unsafe.", null);
            }
            MigrationFile migration = objectMapper.readValue(
                    path.toFile(), MigrationFile.class);
            if (migration == null
                    || migration.version() != WORKSPACE_MARKER_VERSION
                    || !STORE_ID.matcher(nullToEmpty(migration.storeId())).matches()
                    || !nullToEmpty(migration.registrySha256())
                    .matches("[0-9a-f]{64}")) {
                throw corrupt("Workspace migration metadata is invalid.", null);
            }
            return migration;
        } catch (RuntimeAuthoringWorkspaceException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw corrupt("Workspace migration metadata could not be read.", null,
                    failure);
        }
    }

    private static void requirePlainDirectory(Path path, String message) {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path)) {
            throw corrupt(message, null);
        }
    }

    private static void assertSafeTree(Path path) {
        try (Stream<Path> entries = Files.walk(path)) {
            for (Path entry : entries.toList()) {
                if (Files.isSymbolicLink(entry)
                        || (!Files.isDirectory(entry)
                        && !Files.isRegularFile(entry))) {
                    throw corrupt("Owned cleanup target contains an unsafe entry.", null);
                }
            }
        } catch (RuntimeAuthoringWorkspaceException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw corrupt("Owned cleanup target could not be inspected.", null,
                    failure);
        }
    }

    private static void validateStoredRecord(StoredWorkspace record) {
        if (record == null || record.workspaceId() == null
                || !WORKSPACE_ID.matcher(record.workspaceId()).matches()
                || !StringUtils.hasText(record.namespace())
                || !StringUtils.hasText(record.sourceBundle())
                || !"runtime-managed".equals(record.sourceKind())
                || record.state() == null
                || !StringUtils.hasText(record.baseSourceIdentity())
                || !record.baseSourceIdentity().matches("sha256:[0-9a-f]{64}")
                || !StringUtils.hasText(record.baseSourceRevision())) {
            throw corrupt("Workspace registry contains invalid metadata.", null);
        }
        revisionName(record.baseBundleRevision());
        revisionName(record.candidateRevision());
        try {
            Instant.parse(record.createdAt());
            Instant.parse(record.updatedAt());
        } catch (RuntimeException invalidTime) {
            throw corrupt("Workspace registry contains invalid timestamps.", null,
                    invalidTime);
        }
        AuthoringWorkspaceInfo.ValidationEvidence evidence =
                record.lastValidation();
        if (evidence != null) {
            if (!record.candidateRevision().equals(evidence.candidateRevision())
                    || !record.baseBundleRevision().equals(
                    evidence.baseBundleRevision())
                    || !record.baseSourceRevision().equals(
                    evidence.baseNamespaceSourceRevision())
                    || evidence.totalFiles() < 0 || evidence.validFiles() < 0
                    || evidence.invalidFiles() < 0
                    || evidence.cascadingErrors() < 0
                    || evidence.validFiles() + evidence.invalidFiles()
                    != evidence.totalFiles()) {
                throw corrupt("Workspace registry contains invalid validation evidence.",
                        null);
            }
            try {
                Instant.parse(evidence.validatedAt());
            } catch (RuntimeException invalidTime) {
                throw corrupt("Workspace validation evidence has an invalid timestamp.",
                        null, invalidTime);
            }
            if (evidence.cascadingErrors() > evidence.invalidFiles()
                    || (evidence.valid()
                    && (evidence.totalFiles() == 0
                    || evidence.validFiles() != evidence.totalFiles()
                    || evidence.invalidFiles() != 0
                    || !evidence.issues().isEmpty()))
                    || (!evidence.valid() && evidence.issues().isEmpty())
                    || (evidence.valid()
                    && record.state() != AuthoringWorkspaceState.VALIDATED
                    && record.state() != AuthoringWorkspaceState.STALE
                    && record.state() != AuthoringWorkspaceState.PUBLISHING
                    && record.state() != AuthoringWorkspaceState.RECOVERY_REQUIRED
                    && record.state() != AuthoringWorkspaceState.PUBLISHED
                    && record.state() != AuthoringWorkspaceState.ROLLING_BACK
                    && record.state() != AuthoringWorkspaceState.ROLLBACK_REQUIRED
                    && record.state() != AuthoringWorkspaceState.ROLLED_BACK
                    && record.state() != AuthoringWorkspaceState.DISCARDED)
                    || (!evidence.valid()
                    && record.state() == AuthoringWorkspaceState.VALIDATED)) {
                throw corrupt(
                        "Workspace registry contains inconsistent validation state.",
                        null);
            }
        } else if (record.state() == AuthoringWorkspaceState.VALIDATED
                || record.state() == AuthoringWorkspaceState.PUBLISHING
                || record.state() == AuthoringWorkspaceState.RECOVERY_REQUIRED
                || record.state() == AuthoringWorkspaceState.PUBLISHED
                || record.state() == AuthoringWorkspaceState.ROLLING_BACK
                || record.state() == AuthoringWorkspaceState.ROLLBACK_REQUIRED
                || record.state() == AuthoringWorkspaceState.ROLLED_BACK) {
            throw corrupt(
                    "Validated publication workspace is missing validation evidence.", null);
        }
        validatePublicationEvidence(record);
        validateReleaseImportEvidence(record);
    }

    private static void validateReleaseImportEvidence(StoredWorkspace record) {
        ReleaseImportEvidence release = record.releaseImport();
        if (release == null) {
            return;
        }
        try {
            if (!StringUtils.hasText(release.packageId())
                    || !release.packageId().matches("sha256:[0-9a-f]{64}")
                    || !"foggy-authoring-release/v1".equals(release.formatVersion())
                    || !StringUtils.hasText(release.sourceRuntimeApiVersion())
                    || !StringUtils.hasText(release.sourceNamespace())
                    || !StringUtils.hasText(release.sourceBundle())
                    || !record.candidateRevision().equals(
                    release.exportedCandidateRevision())) {
                throw new IllegalArgumentException("invalid release import evidence");
            }
            Instant.parse(release.importedAt());
        } catch (RuntimeException invalid) {
            throw corrupt("Workspace registry contains invalid release import evidence.",
                    null, invalid);
        }
    }

    private static void validatePublicationEvidence(StoredWorkspace record) {
        PublicationEvidence publication = record.lastPublication();
        boolean publicationState = record.state() == AuthoringWorkspaceState.PUBLISHING
                || record.state() == AuthoringWorkspaceState.RECOVERY_REQUIRED
                || record.state() == AuthoringWorkspaceState.PUBLISHED
                || record.state() == AuthoringWorkspaceState.ROLLING_BACK
                || record.state() == AuthoringWorkspaceState.ROLLBACK_REQUIRED
                || record.state() == AuthoringWorkspaceState.ROLLED_BACK;
        if (publication == null) {
            if (publicationState) {
                throw corrupt("Publication workspace is missing evidence.", null);
            }
            return;
        }
        try {
            if (publication.attemptId() == null
                    || !publication.attemptId().matches(UUID_PATTERN)) {
                throw new IllegalArgumentException("invalid publication attempt id");
            }
            UUID.fromString(publication.attemptId());
            Instant.parse(publication.startedAt());
            if (StringUtils.hasText(publication.completedAt())) {
                Instant.parse(publication.completedAt());
            }
        } catch (RuntimeException invalid) {
            throw corrupt("Publication evidence contains an invalid identity or timestamp.",
                    null, invalid);
        }
        if (!record.candidateRevision().equals(publication.candidateRevision())
                || !record.baseBundleRevision().equals(
                publication.baseBundleRevision())
                || !record.baseSourceRevision().equals(
                publication.baseNamespaceSourceRevision())
                || !record.candidateRevision().equals(
                publication.appliedBundleRevision())
                || !publicationStatusMatches(record.state(), publication.status())) {
            throw corrupt("Workspace registry contains inconsistent publication evidence.",
                    null);
        }
        validateRollbackEvidence(record, publication.rollback());
    }

    private static void validateRollbackEvidence(
            StoredWorkspace record,
            RollbackEvidence rollback
    ) {
        boolean rollbackState = record.state() == AuthoringWorkspaceState.ROLLING_BACK
                || record.state() == AuthoringWorkspaceState.ROLLBACK_REQUIRED
                || record.state() == AuthoringWorkspaceState.ROLLED_BACK;
        if (rollback == null) {
            if (rollbackState) {
                throw corrupt("Rollback workspace is missing evidence.", null);
            }
            return;
        }
        if (record.releaseImport() == null || !StringUtils.hasText(rollback.status())
                || !StringUtils.hasText(rollback.startedAt())) {
            throw corrupt("Workspace rollback evidence is incomplete.", null);
        }
        try {
            Instant.parse(rollback.startedAt());
            if (StringUtils.hasText(rollback.completedAt())) {
                Instant.parse(rollback.completedAt());
            }
        } catch (RuntimeException invalid) {
            throw corrupt("Workspace rollback evidence has an invalid timestamp.",
                    null, invalid);
        }
        String expected = switch (record.state()) {
            case ROLLING_BACK -> "ROLLING_BACK";
            case ROLLBACK_REQUIRED -> "ROLLBACK_REQUIRED";
            case ROLLED_BACK -> "ROLLED_BACK";
            case PUBLISHED -> "FORWARD_RECOVERED";
            default -> null;
        };
        if (expected == null || !expected.equals(rollback.status())) {
            throw corrupt("Workspace rollback state is inconsistent.", null);
        }
        if (record.state() == AuthoringWorkspaceState.ROLLED_BACK
                && (!StringUtils.hasText(
                rollback.rolledBackNamespaceSourceRevision())
                || !StringUtils.hasText(rollback.rolledBackCatalogGeneration())
                || !StringUtils.hasText(rollback.completedAt()))) {
            throw corrupt("Completed rollback evidence is incomplete.", null);
        }
        if (record.state() == AuthoringWorkspaceState.PUBLISHED
                && (!StringUtils.hasText(
                rollback.forwardRecoveredNamespaceSourceRevision())
                || !StringUtils.hasText(
                rollback.forwardRecoveredCatalogGeneration())
                || !StringUtils.hasText(rollback.completedAt()))) {
            throw corrupt("Forward recovery evidence is incomplete.", null);
        }
    }

    private static boolean publicationStatusMatches(
            AuthoringWorkspaceState state,
            String status
    ) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        return switch (state) {
            case PUBLISHING -> "PUBLISHING".equals(status);
            case RECOVERY_REQUIRED -> "RECOVERY_REQUIRED".equals(status);
            case PUBLISHED, ROLLING_BACK, ROLLBACK_REQUIRED, ROLLED_BACK ->
                    "PUBLISHED".equals(status);
            case STALE -> "RECOVERED".equals(status);
            default -> false;
        };
    }

    private static boolean reconcileInterruptedPublications(
            Map<String, StoredWorkspace> records
    ) {
        boolean changed = false;
        for (Map.Entry<String, StoredWorkspace> entry
                : new ArrayList<>(records.entrySet())) {
            StoredWorkspace record = entry.getValue();
            if (record.state() == AuthoringWorkspaceState.ROLLING_BACK) {
                PublicationEvidence previous = record.lastPublication();
                RollbackEvidence rollback = previous.rollback();
                List<String> rollbackDiagnostics = new ArrayList<>(
                        rollback.diagnostics());
                rollbackDiagnostics.add(
                        "Runtime restarted before rollback completion was proven.");
                RollbackEvidence required = new RollbackEvidence(
                        "ROLLBACK_REQUIRED", rollback.startedAt(),
                        rollback.rolledBackNamespaceSourceRevision(),
                        rollback.rolledBackCatalogGeneration(),
                        rollback.completedAt(),
                        rollback.forwardRecoveredNamespaceSourceRevision(),
                        rollback.forwardRecoveredCatalogGeneration(),
                        rollbackDiagnostics);
                PublicationEvidence reconciled = withRollback(previous, required);
                StoredWorkspace updated = record.withPublication(
                        AuthoringWorkspaceState.ROLLBACK_REQUIRED, reconciled,
                        "Rollback was interrupted; explicit forward recovery is required.");
                validateStoredRecord(updated);
                records.put(entry.getKey(), updated);
                changed = true;
                continue;
            }
            if (record.state() != AuthoringWorkspaceState.PUBLISHING) {
                continue;
            }
            PublicationEvidence previous = record.lastPublication();
            List<String> diagnostics = new ArrayList<>(previous.diagnostics());
            diagnostics.add("Runtime restarted before publication completion was proven.");
            PublicationEvidence reconciled = new PublicationEvidence(
                    previous.attemptId(), "RECOVERY_REQUIRED",
                    previous.candidateRevision(), previous.baseBundleRevision(),
                    previous.appliedBundleRevision(),
                    previous.baseNamespaceSourceRevision(),
                    previous.publishedNamespaceSourceRevision(),
                    previous.beforeCatalogGeneration(),
                    previous.afterCatalogGeneration(),
                    previous.recoveredCatalogGeneration(),
                    previous.startedAt(), previous.completedAt(), diagnostics);
            StoredWorkspace updated = record.withPublication(
                    AuthoringWorkspaceState.RECOVERY_REQUIRED, reconciled,
                    "Publication was interrupted; explicit recovery is required.");
            validateStoredRecord(updated);
            records.put(entry.getKey(), updated);
            changed = true;
        }
        return changed;
    }

    private static PublicationEvidence withRollback(
            PublicationEvidence publication,
            RollbackEvidence rollback
    ) {
        return new PublicationEvidence(
                publication.attemptId(), publication.status(),
                publication.candidateRevision(), publication.baseBundleRevision(),
                publication.appliedBundleRevision(),
                publication.baseNamespaceSourceRevision(),
                publication.publishedNamespaceSourceRevision(),
                publication.beforeCatalogGeneration(),
                publication.afterCatalogGeneration(),
                publication.recoveredCatalogGeneration(),
                publication.startedAt(), publication.completedAt(),
                publication.diagnostics(), rollback);
    }

    private static boolean isTerminal(AuthoringWorkspaceState state) {
        return state == AuthoringWorkspaceState.DISCARDED
                || state == AuthoringWorkspaceState.PUBLISHED
                || state == AuthoringWorkspaceState.ROLLED_BACK;
    }

    private void persist(Map<String, StoredWorkspace> records) {
        Path root = secureRoot();
        requireStoreId(storeId);
        persistAtRoot(root, records);
    }

    private void persistAtRoot(
            Path root,
            Map<String, StoredWorkspace> records
    ) {
        writeJsonAtomically(root.resolve(REGISTRY_FILE),
                new RegistryFile(SCHEMA_VERSION, storeId,
                        new ArrayList<>(records.values())));
    }

    private void writeJsonAtomically(Path target, Object value) {
        Path temporary = target.resolveSibling(
                target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    temporary.toFile(), value);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw storeFailure("workspaces.store", unsupported);
            }
        } catch (RuntimeAuthoringWorkspaceException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw storeFailure("workspaces.store", e);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // A same-root orphan is removed during the next store load.
            }
        }
    }

    private Path secureRoot() {
        assertStoreDisjoint();
        Path root = configuredRoot();
        try {
            assertNoSymlinkComponents(root.getRoot(), root);
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root)
                    || !root.equals(root.toRealPath())) {
                throw storeFailure("workspaces.store", null);
            }
            return root;
        } catch (RuntimeAuthoringWorkspaceException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw storeFailure("workspaces.store", e);
        }
    }

    private Path prepareRootForLoad() {
        assertStoreDisjoint();
        Path root = configuredRoot();
        try {
            assertNoSymlinkComponents(root.getRoot(), root);
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(root);
            }
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root)
                    || !root.equals(root.toRealPath())) {
                throw storeFailure("workspaces.store", null);
            }
            return root;
        } catch (RuntimeAuthoringWorkspaceException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw storeFailure("workspaces.store", failure);
        }
    }

    private Path configuredRoot() {
        if (pathPolicy != null) {
            return pathPolicy.configuredStoreRoot();
        }
        String configured = configuration().getPath();
        try {
            return Path.of(StringUtils.hasText(configured)
                            ? configured : ".foggy-runtime/authoring-workspaces")
                    .toAbsolutePath().normalize();
        } catch (RuntimeException invalid) {
            throw storeFailure("workspaces.store", invalid);
        }
    }

    private void assertStoreDisjoint() {
        if (pathPolicy == null) {
            return;
        }
        try {
            pathPolicy.assertStoreDisjoint(bundleRegistry == null
                    ? List.of() : bundleRegistry.listRecords());
        } catch (RuntimeAuthoringStorePathPolicy.PathConflictException conflict) {
            throw storeFailure("workspaces.store", conflict);
        }
    }

    private static boolean isDirectoryEmpty(Path root) throws IOException {
        try (Stream<Path> children = Files.list(root)) {
            return children.findAny().isEmpty();
        }
    }

    private static void assertNoSymlinkComponents(Path base, Path target) throws IOException {
        Path cursor = base;
        if (cursor == null) {
            cursor = target.getRoot();
        }
        Path relative = cursor.relativize(target);
        for (Path segment : relative) {
            cursor = cursor.resolve(segment);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                    && Files.isSymbolicLink(cursor)) {
                throw new IOException("symbolic link in workspace path");
            }
        }
    }

    private Path workspacePath(String workspaceId) {
        Path root = secureRoot();
        Path path = root.resolve(validWorkspaceId(workspaceId)).normalize();
        if (!path.startsWith(root)) {
            throw storeFailure("workspaces.store", null);
        }
        return path;
    }

    private Path revisionPath(String workspaceId, String revision) {
        return workspacePath(workspaceId).resolve("revisions").resolve(revisionName(revision));
    }

    private static String revisionName(String revision) {
        if (revision == null || !revision.matches("sha256:[0-9a-f]{64}")) {
            throw corrupt("Workspace revision identity is invalid.", null);
        }
        return revision.substring("sha256:".length());
    }

    private String validWorkspaceId(String workspaceId) {
        String normalized = StringUtils.hasText(workspaceId) ? workspaceId.trim() : "";
        if (!WORKSPACE_ID.matcher(normalized).matches()) {
            throw failure("WORKSPACE_NOT_FOUND", "workspaces.get",
                    "Authoring workspace was not found.", null, false);
        }
        return normalized;
    }

    private String newWorkspaceId() {
        byte[] bytes = new byte[24];
        String id;
        do {
            secureRandom.nextBytes(bytes);
            id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (workspaces.containsKey(id));
        return id;
    }

    private String newStoreId() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void requireStoreId(String value) {
        if (!STORE_ID.matcher(nullToEmpty(value)).matches()) {
            throw corrupt("Workspace store ownership identity is invalid.", null);
        }
    }

    private void release(RevisionKey key) {
        synchronized (this) {
            Integer current = leases.get(key);
            if (current == null || current <= 1) {
                leases.remove(key);
                StoredWorkspace record = workspaces.get(key.workspaceId());
                if (record != null) {
                    cleanupRevision(record, key.revision());
                }
            } else {
                leases.put(key, current - 1);
            }
        }
    }

    private void cleanupRevision(StoredWorkspace record, String revision) {
        if (revision == null || leases.containsKey(
                new RevisionKey(record.workspaceId(), revision))) {
            return;
        }
        boolean retained = record.state() != AuthoringWorkspaceState.DISCARDED
                && (revision.equals(record.baseBundleRevision())
                || revision.equals(record.candidateRevision()));
        if (!retained) {
            deleteOwnedTreeQuietly(
                    revisionPath(record.workspaceId(), revision),
                    record.workspaceId());
        }
    }

    private void cleanupWorkspaceAfterFailedCreate(
            Path workspace,
            String workspaceId
    ) {
        try {
            Path marker = workspace.resolve(WORKSPACE_MARKER_FILE);
            if (Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(marker)) {
                deleteOwnedTree(workspace, workspaceId);
            } else if (Files.isDirectory(workspace)
                    && isDirectoryEmpty(workspace)) {
                Files.deleteIfExists(workspace);
            }
        } catch (IOException | RuntimeException ignored) {
            // An unproven tree is intentionally preserved for fail-closed recovery.
        }
    }

    private void deleteOwnedTreeQuietly(Path path, String workspaceId) {
        try {
            deleteOwnedTree(path, workspaceId);
        } catch (IOException | RuntimeException ignored) {
            // Inert orphan cleanup is retried on the next store load.
        }
    }

    private void deleteOwnedTree(Path path, String workspaceId) throws IOException {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Path root = secureRoot();
        if (!WORKSPACE_ID.matcher(nullToEmpty(workspaceId)).matches()) {
            throw new IOException("invalid owned workspace identity");
        }
        Path workspace = root.resolve(workspaceId).normalize();
        requireWorkspaceOwnership(workspace, workspaceId);
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.equals(workspace)
                && !isInternalCleanupTarget(workspace, normalized)) {
            throw new IOException("unsafe workspace cleanup target");
        }
        assertSafeTree(normalized);
        try (Stream<Path> paths = Files.walk(normalized)) {
            for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static boolean isInternalCleanupTarget(
            Path workspace,
            Path target
    ) {
        if (!target.startsWith(workspace)) {
            return false;
        }
        Path relative = workspace.relativize(target);
        if (relative.getNameCount() == 1) {
            return STAGING_DIRECTORY.matcher(
                    relative.getFileName().toString()).matches();
        }
        return relative.getNameCount() == 2
                && "revisions".equals(relative.getName(0).toString())
                && REVISION_DIRECTORY.matcher(
                relative.getName(1).toString()).matches();
    }

    private static boolean snapshotsEqual(
            Map<String, byte[]> first,
            Map<String, byte[]> second
    ) {
        if (!first.keySet().equals(second.keySet())) {
            return false;
        }
        for (String key : first.keySet()) {
            if (!MessageDigest.isEqual(first.get(key), second.get(key))) {
                return false;
            }
        }
        return true;
    }

    private static AuthoringWorkspaceResource resource(
            String path,
            byte[] content,
            boolean includeContent
    ) {
        return new AuthoringWorkspaceResource(
                path, resourceType(path), content.length, sha256(content),
                includeContent ? decodeUtf8(content) : null);
    }

    private static String resourceType(String path) {
        if (path.endsWith(".tm")) {
            return "TM";
        }
        if (path.endsWith(".qm")) {
            return "QM";
        }
        return "FSSCRIPT";
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String decodeUtf8(byte[] value) {
        return decodeUtf8(value, "workspaces.resources", null);
    }

    private static String decodeUtf8(
            byte[] value,
            String phase,
            String path
    ) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(value)).toString();
        } catch (CharacterCodingException invalid) {
            throw failure("WORKSPACE_INVALID_REQUEST", phase,
                    "Workspace resources must contain strict UTF-8 text.", path, false);
        }
    }

    private FoggyRuntimeApiProperties.AuthoringWorkspaces configuration() {
        return properties.getAuthoringWorkspaces() == null
                ? new FoggyRuntimeApiProperties.AuthoringWorkspaces()
                : properties.getAuthoringWorkspaces();
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalStateException(name + " must be positive");
        }
        return value;
    }

    private static long positive(long value, String name) {
        if (value <= 0L) {
            throw new IllegalStateException(name + " must be positive");
        }
        return value;
    }

    private static String canonicalNamespace(String namespace) {
        return namespace == null ? "" : namespace.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sanitizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "Workspace source is stale.";
        }
        String singleLine = reason.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() <= 240 ? singleLine : singleLine.substring(0, 240);
    }

    private static RuntimeAuthoringWorkspaceException invalidPath(
            String phase,
            String path
    ) {
        return failure("WORKSPACE_RESOURCE_PATH_INVALID", phase,
                "Workspace resource path is invalid.", path, false);
    }

    private static RuntimeAuthoringWorkspaceException corrupt(
            String message,
            String path
    ) {
        return corrupt(message, path, null);
    }

    private static RuntimeAuthoringWorkspaceException corrupt(
            String message,
            String path,
            Throwable cause
    ) {
        RuntimeAuthoringWorkspaceException failure = failure(
                "WORKSPACE_STORE_CORRUPT", "workspaces.store",
                message, path, false);
        if (cause != null) {
            failure.addSuppressed(cause);
        }
        return failure;
    }

    private static RuntimeAuthoringWorkspaceException storeFailure(
            String phase,
            Throwable cause
    ) {
        RuntimeAuthoringWorkspaceException failure = failure(
                "WORKSPACE_STORE_FAILURE", phase,
                "Authoring workspace store operation failed.", null, false);
        if (cause != null) {
            failure.addSuppressed(cause);
        }
        return failure;
    }

    static RuntimeAuthoringWorkspaceException failure(
            String code,
            String phase,
            String message,
            String path,
            boolean safeToAutoRepair
    ) {
        return new RuntimeAuthoringWorkspaceException(
                code, phase, message, path, safeToAutoRepair);
    }

    public record StoredWorkspace(
            String workspaceId,
            String namespace,
            String sourceBundle,
            String sourceKind,
            String baseSourceIdentity,
            String baseBundleRevision,
            String baseSourceRevision,
            String candidateRevision,
            AuthoringWorkspaceState state,
            String createdAt,
            String updatedAt,
            AuthoringWorkspaceInfo.ValidationEvidence lastValidation,
            PublicationEvidence lastPublication,
            ReleaseImportEvidence releaseImport,
            String staleReason
    ) {
        /** Compatibility constructor for records created before publication support. */
        public StoredWorkspace(
                String workspaceId,
                String namespace,
                String sourceBundle,
                String sourceKind,
                String baseSourceIdentity,
                String baseBundleRevision,
                String baseSourceRevision,
                String candidateRevision,
                AuthoringWorkspaceState state,
                String createdAt,
                String updatedAt,
                AuthoringWorkspaceInfo.ValidationEvidence lastValidation,
                PublicationEvidence lastPublication,
                String staleReason
        ) {
            this(workspaceId, namespace, sourceBundle, sourceKind,
                    baseSourceIdentity, baseBundleRevision, baseSourceRevision,
                    candidateRevision, state, createdAt, updatedAt,
                    lastValidation, lastPublication, null, staleReason);
        }

        /** Compatibility constructor for records created before publication support. */
        public StoredWorkspace(
                String workspaceId,
                String namespace,
                String sourceBundle,
                String sourceKind,
                String baseSourceIdentity,
                String baseBundleRevision,
                String baseSourceRevision,
                String candidateRevision,
                AuthoringWorkspaceState state,
                String createdAt,
                String updatedAt,
                AuthoringWorkspaceInfo.ValidationEvidence lastValidation,
                String staleReason
        ) {
            this(workspaceId, namespace, sourceBundle, sourceKind,
                    baseSourceIdentity, baseBundleRevision, baseSourceRevision,
                    candidateRevision, state, createdAt, updatedAt,
                    lastValidation, null, null, staleReason);
        }

        StoredWorkspace withCandidateRevision(String revision) {
            return new StoredWorkspace(
                    workspaceId, namespace, sourceBundle, sourceKind,
                    baseSourceIdentity, baseBundleRevision, baseSourceRevision,
                    revision, state, createdAt, updatedAt, lastValidation,
                    lastPublication, releaseImport, staleReason);
        }

        StoredWorkspace withPublication(
                AuthoringWorkspaceState nextState,
                PublicationEvidence publication,
                String reason
        ) {
            return new StoredWorkspace(
                    workspaceId, namespace, sourceBundle, sourceKind,
                    baseSourceIdentity, baseBundleRevision, baseSourceRevision,
                    candidateRevision, nextState, createdAt,
                    Instant.now().toString(), lastValidation, publication,
                    releaseImport, reason);
        }
    }

    public static final class RevisionLease implements AutoCloseable {
        private final RuntimeAuthoringWorkspaceStore store;
        private final StoredWorkspace workspace;
        private final Path path;
        private final RevisionKey key;
        private boolean closed;

        private RevisionLease(
                RuntimeAuthoringWorkspaceStore store,
                StoredWorkspace workspace,
                Path path,
                RevisionKey key
        ) {
            this.store = store;
            this.workspace = workspace;
            this.path = path;
            this.key = key;
        }

        public StoredWorkspace workspace() {
            return workspace;
        }

        public Path path() {
            return path;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                store.release(key);
            }
        }
    }

    private record RevisionKey(String workspaceId, String revision) {
    }

    record LifecycleRevisionLease(String workspaceId, String revision) {
    }

    private enum CleanupType {
        FILE,
        TREE
    }

    private record CleanupTarget(
            Path path,
            String workspaceId,
            CleanupType type
    ) {
    }

    private record WorkspaceMarker(
            int version,
            String storeId,
            String workspaceId
    ) {
        public WorkspaceMarker() {
            this(WORKSPACE_MARKER_VERSION, null, null);
        }
    }

    private record MigrationFile(
            int version,
            String storeId,
            String registrySha256
    ) {
        public MigrationFile() {
            this(WORKSPACE_MARKER_VERSION, null, null);
        }
    }

    private record RegistryFile(
            int version,
            String storeId,
            List<StoredWorkspace> workspaces
    ) {
        private RegistryFile {
            workspaces = workspaces == null ? List.of() : List.copyOf(workspaces);
        }

        public RegistryFile() {
            this(SCHEMA_VERSION, null, List.of());
        }
    }
}
