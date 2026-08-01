package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.candidate.CandidateContentRevision;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.ArtifactLifecycleInventory;
import com.foggyframework.runtime.api.dto.ArtifactLifecycleInventory.InventoryObject;
import com.foggyframework.runtime.api.dto.ArtifactLifecycleInventory.RootHealth;
import com.foggyframework.runtime.api.dto.ArtifactLifecycleInventory.Summary;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo.PublicationEvidence;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceState;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceStore.LifecycleRevisionLease;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceStore.StoredWorkspace;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService.RuntimeBundleRecord;
import com.foggyframework.runtime.api.service.RuntimePublishedBundleArtifactStore.PublicationAttempt;
import com.foggyframework.runtime.api.service.RuntimePublishedBundleArtifactStore.RollbackAttempt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Collects redacted lifecycle facts without initializing or mutating either store. */
@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeArtifactLifecycleInventoryService {

    static final String MUST_RETAIN = "MUST_RETAIN";
    static final String CANDIDATE = "PROVABLY_UNREACHABLE_CANDIDATE";
    static final String UNKNOWN_PRESERVE = "UNKNOWN_PRESERVE";

    private static final String PHASE = "runtime.artifacts.lifecycle.inventory";
    private static final String WORKSPACE = "WORKSPACE";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String LIVE_REGISTRY = "LIVE_REGISTRY";
    private static final String WORKSPACE_REGISTRY = "workspaces.json";
    private static final String WORKSPACE_MARKER = ".workspace-owner.json";
    private static final String PUBLISHED_OWNER = ".foggy-published-owner.json";
    private static final String ARTIFACT_MARKER = ".artifact.json";
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern UUID_NAME = Pattern.compile(UUID_PATTERN);
    private static final Pattern STORE_ID = Pattern.compile("[A-Za-z0-9_-]{22,64}");
    private static final Pattern REVISION_NAME = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern WORKSPACE_ID = Pattern.compile("[A-Za-z0-9_-]{22,64}");
    private static final Pattern STAGING_NAME = Pattern.compile("\\.staging-" + UUID_PATTERN);
    private static final Pattern STAGING_OWNER = Pattern.compile(
            "(\\.staging-" + UUID_PATTERN + ")\\.owner\\.json");
    private static final Pattern WORKSPACE_REGISTRY_TEMP = Pattern.compile(
            Pattern.quote(WORKSPACE_REGISTRY) + "\\.tmp-" + UUID_PATTERN);
    private static final Pattern WORKSPACE_MARKER_TEMP = Pattern.compile(
            Pattern.quote(WORKSPACE_MARKER) + "\\.tmp-" + UUID_PATTERN);
    private static final Pattern MIGRATION_TEMP = Pattern.compile(
            "\\.migration-v2\\.json\\.tmp-" + UUID_PATTERN);
    private static final Pattern ATTEMPT_FILE = Pattern.compile("(" + UUID_PATTERN + ")\\.json");
    private static final Pattern ATTEMPT_TEMP = Pattern.compile(
            "(" + UUID_PATTERN + ")\\.json\\.tmp-" + UUID_PATTERN);
    private static final Pattern ATTEMPT_TEMP_OWNER = Pattern.compile(
            "((" + UUID_PATTERN + ")\\.json\\.tmp-" + UUID_PATTERN
                    + ")\\.owner\\.json");
    private static final Set<String> PUBLICATION_STATUSES = Set.of(
            "PUBLISHING", "SOURCE_APPLIED", "PUBLISHED", "RECOVERED",
            "RECOVERY_REQUIRED", "FAILED");
    private static final Set<String> ROLLBACK_STATUSES = Set.of(
            "ROLLING_BACK", "ROLLED_BACK", "ROLLBACK_REQUIRED",
            "FORWARD_RECOVERED");

    private final FoggyRuntimeApiProperties properties;
    private final ObjectMapper objectMapper;
    private final RuntimeAuthoringPublicationLock publicationLock;
    private final RuntimeAuthoringWorkspaceStore workspaceStore;
    private final RuntimeBundleRegistryService bundleRegistry;

    public RuntimeArtifactLifecycleInventoryService(
            FoggyRuntimeApiProperties properties,
            ObjectMapper objectMapper,
            RuntimeAuthoringPublicationLock publicationLock,
            RuntimeAuthoringWorkspaceStore workspaceStore,
            RuntimeBundleRegistryService bundleRegistry
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.publicationLock = publicationLock;
        this.workspaceStore = workspaceStore;
        this.bundleRegistry = bundleRegistry;
    }

    public ArtifactLifecycleInventory inventory() {
        try (RuntimeAuthoringPublicationLock.Guard ignored = publicationLock.acquire()) {
            return workspaceStore.withLifecycleInventorySnapshot(
                    this::inventoryLocked);
        } catch (RuntimeAuthoringWorkspaceException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw failure(failure);
        }
    }

    private ArtifactLifecycleInventory inventoryLocked(
            Set<LifecycleRevisionLease> leases
    ) {
        Builder result = new Builder();
        Path workspaceRoot = configuredRoot(true);
        Path publishedRoot = configuredRoot(false);
        WorkspaceFacts workspaces = scanWorkspaceRoot(
                workspaceRoot, leases, result);
        PublishedFacts published = scanPublishedRoot(publishedRoot, result);
        scanLiveRegistry(publishedRoot, published, result);
        classifyArtifacts(workspaces, published, result);
        return result.build(Instant.now().toString());
    }

    private WorkspaceFacts scanWorkspaceRoot(
            Path root,
            Set<LifecycleRevisionLease> leases,
            Builder result
    ) {
        RootScan scan = result.root(WORKSPACE);
        WorkspaceFacts facts = new WorkspaceFacts();
        if (!prepareReadableRoot(root, scan)) {
            facts.referenceGraphComplete = !Files.exists(
                    root, LinkOption.NOFOLLOW_LINKS);
            return facts;
        }
        Path registry = root.resolve(WORKSPACE_REGISTRY);
        if (!Files.exists(registry, LinkOption.NOFOLLOW_LINKS)) {
            if (children(root).isEmpty()) {
                scan.notInitialized();
            } else {
                facts.referenceGraphComplete = false;
                scan.block("WORKSPACE_ROOT_UNOWNED");
                addUnknownChildren(root, WORKSPACE, result,
                        "WORKSPACE_ROOT_UNOWNED");
            }
            scan.finish(root, result);
            return facts;
        }
        JsonNode registryJson;
        try {
            requireRegularFile(registry);
            registryJson = objectMapper.readTree(registry.toFile());
        } catch (IOException | RuntimeException failure) {
            facts.referenceGraphComplete = false;
            result.addBlocked(WORKSPACE, "WORKSPACE_REGISTRY", "workspace-registry",
                    safeSize(registry), "WORKSPACE_REGISTRY_CORRUPT", scan);
            scan.finish(root, result);
            return facts;
        }
        int version = registryJson.path("version").asInt(-1);
        String storeId = text(registryJson, "storeId");
        if (version != 2 || !STORE_ID.matcher(nullToEmpty(storeId)).matches()) {
            facts.referenceGraphComplete = false;
            result.addBlocked(WORKSPACE, "WORKSPACE_REGISTRY", "workspace-registry",
                    safeSize(registry), version == 1
                            ? "WORKSPACE_REGISTRY_LEGACY"
                            : "WORKSPACE_REGISTRY_CORRUPT", scan);
            scan.finish(root, result);
            return facts;
        }
        result.add(new Draft(WORKSPACE, "WORKSPACE_REGISTRY", "workspace-registry",
                "OWNED", safeSize(registry), MUST_RETAIN));
        JsonNode records = registryJson.path("workspaces");
        if (!records.isArray()) {
            facts.referenceGraphComplete = false;
            scan.block("WORKSPACE_REGISTRY_CORRUPT");
        } else {
            for (JsonNode node : records) {
                readWorkspaceRecord(node, facts, result, scan);
            }
        }
        Set<LeaseKey> activeLeases = new HashSet<>();
        for (LifecycleRevisionLease lease : leases) {
            activeLeases.add(new LeaseKey(
                    lease.workspaceId(), lease.revision()));
        }
        for (Path child : children(root)) {
            String name = child.getFileName().toString();
            if (WORKSPACE_REGISTRY.equals(name)) {
                continue;
            }
            if (WORKSPACE_REGISTRY_TEMP.matcher(name).matches()) {
                scanOwnedJsonTemporary(child, storeId, WORKSPACE,
                        "WORKSPACE_REGISTRY_TEMPORARY",
                        facts.referenceGraphComplete, result, scan);
                continue;
            }
            if (".migration-v2.json".equals(name)
                    || MIGRATION_TEMP.matcher(name).matches()) {
                scanOwnedJsonTemporary(child, storeId, WORKSPACE,
                        "WORKSPACE_MIGRATION_METADATA",
                        facts.referenceGraphComplete, result, scan);
                continue;
            }
            if (WORKSPACE_ID.matcher(name).matches()) {
                scanWorkspaceDirectory(child, storeId, facts.records.get(name),
                        activeLeases, facts.referenceGraphComplete, result, scan);
                continue;
            }
            result.addBlocked(WORKSPACE, "UNKNOWN_ENTRY", "workspace-root-entry",
                    safeTreeBytes(child), "WORKSPACE_UNKNOWN_ENTRY", scan);
            facts.referenceGraphComplete = false;
        }
        if (!facts.referenceGraphComplete) {
            result.downgradeCandidates(WORKSPACE,
                    "WORKSPACE_REFERENCE_GRAPH_INCOMPLETE", scan);
        }
        scan.finish(root, result);
        return facts;
    }

    private void readWorkspaceRecord(
            JsonNode node,
            WorkspaceFacts facts,
            Builder result,
            RootScan scan
    ) {
        try {
            StoredWorkspace record = objectMapper.treeToValue(
                    node, StoredWorkspace.class);
            if (record == null
                    || !WORKSPACE_ID.matcher(nullToEmpty(record.workspaceId())).matches()
                    || record.state() == null
                    || !validRevision(record.baseBundleRevision())
                    || !validRevision(record.candidateRevision())
                    || facts.records.put(record.workspaceId(), record) != null) {
                throw new IllegalArgumentException("invalid workspace record");
            }
            String type = record.state() == AuthoringWorkspaceState.DISCARDED
                    ? "WORKSPACE_TOMBSTONE" : "WORKSPACE_RECORD";
            Draft draft = new Draft(WORKSPACE, type,
                    "workspace:" + record.workspaceId(), record.state().name(),
                    0L, MUST_RETAIN);
            PublicationEvidence publication = record.lastPublication();
            if (publication != null && StringUtils.hasText(publication.attemptId())) {
                draft.references.add("attempt:" + publication.attemptId());
            }
            result.add(draft);
            if (record.state() != AuthoringWorkspaceState.DISCARDED) {
                facts.revisionReferences.computeIfAbsent(
                        record.baseBundleRevision(), ignored -> new TreeSet<>())
                        .add("workspace:" + record.workspaceId() + ":base");
                facts.revisionReferences.computeIfAbsent(
                        record.candidateRevision(), ignored -> new TreeSet<>())
                        .add("workspace:" + record.workspaceId() + ":candidate");
            }
        } catch (IOException | RuntimeException failure) {
            facts.referenceGraphComplete = false;
            result.addBlocked(WORKSPACE, "WORKSPACE_RECORD", "workspace-record",
                    0L, "WORKSPACE_RECORD_CORRUPT", scan);
        }
    }

    private void scanWorkspaceDirectory(
            Path workspace,
            String storeId,
            StoredWorkspace record,
            Set<LeaseKey> activeLeases,
            boolean referenceGraphComplete,
            Builder result,
            RootScan scan
    ) {
        String workspaceId = workspace.getFileName().toString();
        if (!plainDirectory(workspace)) {
            result.addBlocked(WORKSPACE, "WORKSPACE_DIRECTORY",
                    "workspace:" + workspaceId, safeTreeBytes(workspace),
                    "WORKSPACE_DIRECTORY_UNSAFE", scan);
            return;
        }
        Path marker = workspace.resolve(WORKSPACE_MARKER);
        if (!matchingOwner(marker, storeId, "workspaceId", workspaceId)) {
            result.addBlocked(WORKSPACE, "WORKSPACE_DIRECTORY",
                    "workspace:" + workspaceId, safeTreeBytes(workspace),
                    "WORKSPACE_OWNER_MISMATCH", scan);
            return;
        }
        boolean active = record != null
                && record.state() != AuthoringWorkspaceState.DISCARDED;
        result.add(new Draft(WORKSPACE, "WORKSPACE_MARKER",
                "workspace:" + workspaceId + ":marker",
                active ? "ACTIVE" : "ORPHANED", safeSize(marker),
                active ? MUST_RETAIN
                        : referenceGraphComplete ? CANDIDATE : UNKNOWN_PRESERVE));
        boolean revisionsFound = false;
        for (Path child : children(workspace)) {
            String name = child.getFileName().toString();
            if (WORKSPACE_MARKER.equals(name)) {
                continue;
            }
            if (WORKSPACE_MARKER_TEMP.matcher(name).matches()) {
                if (matchingOwner(child, storeId, "workspaceId", workspaceId)) {
                    Draft temporary = new Draft(
                            WORKSPACE, "WORKSPACE_MARKER_TEMPORARY",
                            "workspace:" + workspaceId + ":marker-temporary",
                            "INTERRUPTED", safeSize(child),
                            referenceGraphComplete ? CANDIDATE : UNKNOWN_PRESERVE);
                    if (!referenceGraphComplete) {
                        temporary.blockedReason =
                                "WORKSPACE_REFERENCE_GRAPH_INCOMPLETE";
                        scan.block(temporary.blockedReason);
                    }
                    result.add(temporary);
                } else {
                    result.addBlocked(WORKSPACE, "WORKSPACE_MARKER_TEMPORARY",
                            "workspace:" + workspaceId + ":marker-temporary",
                            safeSize(child), "WORKSPACE_TEMPORARY_OWNER_MISMATCH", scan);
                }
                continue;
            }
            if ("revisions".equals(name)) {
                revisionsFound = true;
                scanWorkspaceRevisions(child, record, activeLeases,
                        referenceGraphComplete, result, scan);
                continue;
            }
            if (STAGING_NAME.matcher(name).matches()) {
                String identity = "workspace:" + workspaceId + ":staging:" + name;
                if (safeTree(child)) {
                    Draft staging = new Draft(
                            WORKSPACE, "WORKSPACE_STAGING", identity,
                            "INTERRUPTED", safeTreeBytes(child),
                            referenceGraphComplete ? CANDIDATE : UNKNOWN_PRESERVE);
                    if (!referenceGraphComplete) {
                        staging.blockedReason =
                                "WORKSPACE_REFERENCE_GRAPH_INCOMPLETE";
                        scan.block(staging.blockedReason);
                    }
                    result.add(staging);
                } else {
                    result.addBlocked(WORKSPACE, "WORKSPACE_STAGING", identity,
                            safeTreeBytes(child), "WORKSPACE_STAGING_UNSAFE", scan);
                }
                continue;
            }
            result.addBlocked(WORKSPACE, "UNKNOWN_ENTRY",
                    "workspace:" + workspaceId + ":entry", safeTreeBytes(child),
                    "WORKSPACE_UNKNOWN_ENTRY", scan);
        }
        if (active && !revisionsFound) {
            scan.block("WORKSPACE_REVISION_ROOT_MISSING");
        }
    }

    private void scanWorkspaceRevisions(
            Path revisions,
            StoredWorkspace record,
            Set<LeaseKey> activeLeases,
            boolean referenceGraphComplete,
            Builder result,
            RootScan scan
    ) {
        if (!plainDirectory(revisions)) {
            result.addBlocked(WORKSPACE, "WORKSPACE_REVISION_ROOT",
                    "workspace-revisions", safeTreeBytes(revisions),
                    "WORKSPACE_REVISION_ROOT_UNSAFE", scan);
            return;
        }
        String workspaceId = revisions.getParent().getFileName().toString();
        for (Path revisionPath : children(revisions)) {
            String name = revisionPath.getFileName().toString();
            String revision = "sha256:" + name;
            String identity = "workspace:" + workspaceId + ":revision:" + revision;
            if (!REVISION_NAME.matcher(name).matches()
                    || !validSnapshotDirectory(revisionPath, revision)) {
                result.addBlocked(WORKSPACE, "WORKSPACE_REVISION", identity,
                        safeTreeBytes(revisionPath), "WORKSPACE_REVISION_CORRUPT", scan);
                continue;
            }
            Set<String> references = new TreeSet<>();
            if (record != null
                    && record.state() != AuthoringWorkspaceState.DISCARDED) {
                if (revision.equals(record.baseBundleRevision())) {
                    references.add("workspace:" + workspaceId + ":base");
                }
                if (revision.equals(record.candidateRevision())) {
                    references.add("workspace:" + workspaceId + ":candidate");
                }
            }
            if (activeLeases.contains(new LeaseKey(workspaceId, revision))) {
                references.add("workspace:" + workspaceId + ":active-lease");
            }
            Draft draft = new Draft(WORKSPACE, "WORKSPACE_REVISION", identity,
                    references.isEmpty() ? "OBSOLETE" : "REFERENCED",
                    safeTreeBytes(revisionPath),
                    references.isEmpty()
                            ? referenceGraphComplete ? CANDIDATE : UNKNOWN_PRESERVE
                            : MUST_RETAIN);
            draft.references.addAll(references);
            if (references.isEmpty() && !referenceGraphComplete) {
                draft.blockedReason = "WORKSPACE_REFERENCE_GRAPH_INCOMPLETE";
                scan.block(draft.blockedReason);
            }
            result.add(draft);
        }
    }

    private PublishedFacts scanPublishedRoot(Path root, Builder result) {
        RootScan scan = result.root(PUBLISHED);
        PublishedFacts facts = new PublishedFacts();
        if (!prepareReadableRoot(root, scan)) {
            facts.referenceGraphComplete = !Files.exists(
                    root, LinkOption.NOFOLLOW_LINKS);
            return facts;
        }
        Path owner = root.resolve(PUBLISHED_OWNER);
        if (!Files.exists(owner, LinkOption.NOFOLLOW_LINKS)) {
            if (children(root).isEmpty()) {
                scan.notInitialized();
            } else {
                facts.referenceGraphComplete = false;
                scan.block("PUBLISHED_ROOT_UNOWNED");
                addUnknownChildren(root, PUBLISHED, result,
                        "PUBLISHED_ROOT_UNOWNED");
            }
            scan.finish(root, result);
            return facts;
        }
        String storeId = ownerIdentity(owner);
        if (storeId == null) {
            facts.referenceGraphComplete = false;
            result.addBlocked(PUBLISHED, "PUBLISHED_ROOT_OWNER",
                    "published-root-owner", safeSize(owner),
                    "PUBLISHED_ROOT_OWNER_CORRUPT", scan);
            scan.finish(root, result);
            return facts;
        }
        result.add(new Draft(PUBLISHED, "PUBLISHED_ROOT_OWNER",
                "published-root-owner", "OWNED", safeSize(owner), MUST_RETAIN));
        Path attempts = root.resolve("attempts");
        Path artifacts = root.resolve("artifacts");
        scanAttemptDirectory(attempts, storeId, facts, result, scan);
        scanArtifactDirectory(artifacts, storeId, facts, result, scan);
        for (Path child : children(root)) {
            String name = child.getFileName().toString();
            if (PUBLISHED_OWNER.equals(name) || "attempts".equals(name)
                    || "artifacts".equals(name)) {
                continue;
            }
            result.addBlocked(PUBLISHED, "UNKNOWN_ENTRY", "published-root-entry",
                    safeTreeBytes(child), "PUBLISHED_UNKNOWN_ENTRY", scan);
            facts.referenceGraphComplete = false;
        }
        for (Map.Entry<String, AttemptFact> entry : facts.attempts.entrySet()) {
            Draft attempt = entry.getValue().draft;
            PublicationAttempt metadata = entry.getValue().attempt;
            String artifactIdentity = "artifact:" + entry.getKey();
            attempt.references.add(artifactIdentity);
            ArtifactFact candidate = facts.artifacts.get(entry.getKey());
            if (candidate == null) {
                attempt.blockedReason = "PUBLISHED_ARTIFACT_MISSING";
                scan.block("PUBLISHED_ARTIFACT_MISSING");
            } else if (!candidate.revision.equals(metadata.candidateRevision())) {
                attempt.blockedReason = "PUBLISHED_ARTIFACT_IDENTITY_MISMATCH";
                scan.block("PUBLISHED_ARTIFACT_IDENTITY_MISMATCH");
            }
            String previousId = artifactIdFromPath(
                    metadata.previousPath(), root);
            if (previousId != null) {
                attempt.references.add("artifact:" + previousId);
                facts.artifactReferences.computeIfAbsent(
                        previousId, ignored -> new TreeSet<>())
                        .add("attempt:" + entry.getKey() + ":previous");
                ArtifactFact previous = facts.artifacts.get(previousId);
                if (metadata.previousImmutablePublication()
                        && (previous == null || !previous.revision.equals(
                        metadata.previousArtifactRevision()))) {
                    if (!StringUtils.hasText(attempt.blockedReason)) {
                        attempt.blockedReason =
                                "PUBLICATION_PREVIOUS_ARTIFACT_IDENTITY_INVALID";
                    }
                    scan.block("PUBLICATION_PREVIOUS_ARTIFACT_IDENTITY_INVALID");
                }
            } else if (metadata.previousImmutablePublication()) {
                facts.referenceGraphComplete = false;
                if (!StringUtils.hasText(attempt.blockedReason)) {
                    attempt.blockedReason =
                            "PUBLICATION_PREVIOUS_ARTIFACT_IDENTITY_INVALID";
                }
                scan.block("PUBLICATION_PREVIOUS_ARTIFACT_IDENTITY_INVALID");
                facts.revisionReferences.computeIfAbsent(
                        metadata.previousArtifactRevision(),
                        ignored -> new TreeSet<>())
                        .add("attempt:" + entry.getKey() + ":previous-by-revision");
            }
            facts.artifactReferences.computeIfAbsent(
                    entry.getKey(), ignored -> new TreeSet<>())
                    .add("attempt:" + entry.getKey() + ":candidate");
        }
        scan.finish(root, result);
        return facts;
    }

    private void scanAttemptDirectory(
            Path attempts,
            String storeId,
            PublishedFacts facts,
            Builder result,
            RootScan scan
    ) {
        if (!plainDirectory(attempts)) {
            facts.referenceGraphComplete = false;
            scan.block("PUBLISHED_ATTEMPT_ROOT_MISSING_OR_UNSAFE");
            return;
        }
        for (Path path : children(attempts)) {
            String name = path.getFileName().toString();
            var finalMatch = ATTEMPT_FILE.matcher(name);
            if (finalMatch.matches()) {
                String attemptId = finalMatch.group(1);
                try {
                    requireRegularFile(path);
                    PublicationAttempt attempt = objectMapper.readValue(
                            path.toFile(), PublicationAttempt.class);
                    validateAttempt(attemptId, attempt);
                    Draft draft = new Draft(PUBLISHED, "PUBLICATION_ATTEMPT",
                            "attempt:" + attemptId, attemptStatus(attempt),
                            safeSize(path), MUST_RETAIN);
                    result.add(draft);
                    facts.attempts.put(attemptId, new AttemptFact(attempt, draft));
                } catch (IOException | RuntimeException failure) {
                    facts.referenceGraphComplete = false;
                    facts.blockedAttemptIds.add(attemptId);
                    result.addBlocked(PUBLISHED, "PUBLICATION_ATTEMPT",
                            "attempt:" + attemptId, safeSize(path),
                            "PUBLICATION_ATTEMPT_CORRUPT", scan);
                }
                continue;
            }
            var temporaryMatch = ATTEMPT_TEMP.matcher(name);
            if (temporaryMatch.matches()) {
                facts.referenceGraphComplete = false;
                facts.blockedAttemptIds.add(temporaryMatch.group(1));
                Path owner = path.resolveSibling(name + ".owner.json");
                if (validAttemptWriteOwner(owner, storeId, name,
                        temporaryMatch.group(1))) {
                    result.addBlocked(PUBLISHED,
                            "PUBLICATION_METADATA_RECOVERY_PENDING",
                            "attempt:" + temporaryMatch.group(1) + ":temporary",
                            safeSize(path) + safeSize(owner),
                            "PUBLICATION_METADATA_RECOVERY_PENDING", scan);
                } else {
                    result.addBlocked(PUBLISHED, "PUBLICATION_METADATA_TEMPORARY",
                            "attempt:" + temporaryMatch.group(1) + ":temporary",
                            safeSize(path), "PUBLICATION_METADATA_TEMPORARY", scan);
                }
                continue;
            }
            var ownerMatch = ATTEMPT_TEMP_OWNER.matcher(name);
            if (ownerMatch.matches()) {
                Path temporary = path.resolveSibling(ownerMatch.group(1));
                if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                facts.referenceGraphComplete = false;
                facts.blockedAttemptIds.add(ownerMatch.group(2));
                if (validAttemptWriteOwner(path, storeId, ownerMatch.group(1),
                        ownerMatch.group(2))) {
                    result.addBlocked(PUBLISHED,
                            "PUBLICATION_METADATA_RECOVERY_PENDING",
                            "attempt:" + ownerMatch.group(2) + ":temporary",
                            safeSize(path),
                            "PUBLICATION_METADATA_RECOVERY_PENDING", scan);
                } else {
                    result.addBlocked(PUBLISHED, "PUBLICATION_METADATA_OWNER",
                            "attempt:" + ownerMatch.group(2) + ":owner",
                            safeSize(path),
                            "PUBLICATION_METADATA_OWNER_MISMATCH", scan);
                }
                continue;
            }
            result.addBlocked(PUBLISHED, "UNKNOWN_ENTRY", "attempt-entry",
                    safeTreeBytes(path), "PUBLICATION_ATTEMPT_UNKNOWN_ENTRY", scan);
            facts.referenceGraphComplete = false;
        }
    }

    private void scanArtifactDirectory(
            Path artifacts,
            String storeId,
            PublishedFacts facts,
            Builder result,
            RootScan scan
    ) {
        if (!plainDirectory(artifacts)) {
            scan.block("PUBLISHED_ARTIFACT_ROOT_MISSING_OR_UNSAFE");
            return;
        }
        for (Path artifact : children(artifacts)) {
            String name = artifact.getFileName().toString();
            if (STAGING_NAME.matcher(name).matches()) {
                Path owner = artifact.resolveSibling(name + ".owner.json");
                if (validArtifactWriteOwner(owner, storeId, name)) {
                    result.addBlocked(PUBLISHED,
                            "PUBLISHED_ARTIFACT_RECOVERY_PENDING",
                            "artifact-staging:" + name,
                            safeTreeBytes(artifact) + safeSize(owner),
                            "PUBLISHED_ARTIFACT_RECOVERY_PENDING", scan);
                } else {
                    result.addBlocked(PUBLISHED, "PUBLISHED_ARTIFACT_STAGING",
                            "artifact-staging:" + name, safeTreeBytes(artifact),
                            "PUBLISHED_ARTIFACT_STAGING_UNOWNED", scan);
                }
                facts.referenceGraphComplete = false;
                continue;
            }
            var ownerMatch = STAGING_OWNER.matcher(name);
            if (ownerMatch.matches()) {
                Path staging = artifact.resolveSibling(ownerMatch.group(1));
                if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (validArtifactWriteOwner(artifact, storeId,
                        ownerMatch.group(1))) {
                    result.addBlocked(PUBLISHED,
                            "PUBLISHED_ARTIFACT_RECOVERY_PENDING",
                            "artifact-staging:" + ownerMatch.group(1),
                            safeSize(artifact),
                            "PUBLISHED_ARTIFACT_RECOVERY_PENDING", scan);
                } else {
                    result.addBlocked(PUBLISHED, "PUBLISHED_ARTIFACT_OWNER",
                            "artifact-staging-owner", safeSize(artifact),
                            "PUBLISHED_ARTIFACT_OWNER_MISMATCH", scan);
                }
                facts.referenceGraphComplete = false;
                continue;
            }
            if (!UUID_NAME.matcher(name).matches()) {
                result.addBlocked(PUBLISHED, "UNKNOWN_ENTRY", "artifact-entry",
                        safeTreeBytes(artifact), "PUBLISHED_ARTIFACT_UNKNOWN_ENTRY", scan);
                continue;
            }
            ArtifactFact fact = readArtifact(artifact, storeId, name);
            if (fact == null) {
                result.addBlocked(PUBLISHED, "PUBLISHED_ARTIFACT",
                        "artifact:" + name, safeTreeBytes(artifact),
                        "PUBLISHED_ARTIFACT_CORRUPT", scan);
                continue;
            }
            Draft draft = new Draft(PUBLISHED, "PUBLISHED_ARTIFACT",
                    "artifact:" + name, "VERIFIED", safeTreeBytes(artifact),
                    CANDIDATE);
            result.add(draft);
            facts.artifacts.put(name, new ArtifactFact(
                    name, fact.revision, draft));
        }
    }

    private ArtifactFact readArtifact(
            Path artifact,
            String storeId,
            String attemptId
    ) {
        try {
            if (!plainDirectory(artifact)) {
                return null;
            }
            Path marker = artifact.resolve(ARTIFACT_MARKER);
            requireRegularFile(marker);
            JsonNode manifest = objectMapper.readTree(marker.toFile());
            String revision = text(manifest, "candidateRevision");
            if (manifest.path("version").asInt(-1) != 1
                    || !storeId.equals(text(manifest, "storeId"))
                    || !attemptId.equals(text(manifest, "attemptId"))
                    || !StringUtils.hasText(text(manifest, "workspaceId"))
                    || !StringUtils.hasText(text(manifest, "namespace"))
                    || !StringUtils.hasText(text(manifest, "bundle"))
                    || !validRevision(revision)
                    || !validSnapshotDirectory(artifact, revision, ARTIFACT_MARKER)) {
                return null;
            }
            return new ArtifactFact(attemptId, revision, null);
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }

    private boolean validArtifactWriteOwner(
            Path marker,
            String storeId,
            String stagingName
    ) {
        try {
            requireRegularFile(marker);
            JsonNode value = objectMapper.readTree(marker.toFile());
            return value.path("version").asInt(-1) == 1
                    && storeId.equals(text(value, "storeId"))
                    && stagingName.equals(text(value, "stagingName"))
                    && UUID_NAME.matcher(nullToEmpty(
                    text(value, "attemptId"))).matches()
                    && StringUtils.hasText(text(value, "workspaceId"))
                    && StringUtils.hasText(text(value, "namespace"))
                    && StringUtils.hasText(text(value, "bundle"))
                    && validRevision(text(value, "candidateRevision"));
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private boolean validAttemptWriteOwner(
            Path marker,
            String storeId,
            String temporaryName,
            String attemptId
    ) {
        try {
            requireRegularFile(marker);
            JsonNode value = objectMapper.readTree(marker.toFile());
            return value.path("version").asInt(-1) == 1
                    && storeId.equals(text(value, "storeId"))
                    && temporaryName.equals(text(value, "temporaryName"))
                    && (attemptId + ".json").equals(text(value, "finalName"))
                    && attemptId.equals(text(value, "attemptId"));
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private void scanLiveRegistry(
            Path publishedRoot,
            PublishedFacts published,
            Builder result
    ) {
        List<RuntimeBundleRecord> records;
        try {
            records = bundleRegistry.listRecords();
        } catch (RuntimeException failure) {
            published.referenceGraphComplete = false;
            result.addBlocked(LIVE_REGISTRY, "BUNDLE_REGISTRY",
                    "bundle-registry", 0L, "LIVE_REGISTRY_UNREADABLE", null);
            return;
        }
        records.stream()
                .sorted(Comparator.comparing(RuntimeBundleRecord::namespace,
                                Comparator.nullsFirst(String::compareTo))
                        .thenComparing(RuntimeBundleRecord::name,
                                Comparator.nullsFirst(String::compareTo)))
                .forEach(record -> {
                    String identity = "bundle:" + nullToEmpty(record.namespace())
                            + ":" + nullToEmpty(record.name());
                    Draft draft = new Draft(LIVE_REGISTRY, "BUNDLE_RECORD", identity,
                            record.enabled() ? "ENABLED" : "DISABLED", 0L,
                            MUST_RETAIN);
                    if (record.immutablePublication()) {
                        String artifactId = artifactIdFromPath(
                                record.path(), publishedRoot);
                        boolean revisionValid = validRevision(
                                record.artifactRevision());
                        if (artifactId == null || !revisionValid) {
                            draft.blockedReason = "LIVE_PUBLISHED_SOURCE_IDENTITY_INVALID";
                            result.block("LIVE_PUBLISHED_SOURCE_IDENTITY_INVALID");
                            published.referenceGraphComplete = false;
                        }
                        if (artifactId != null) {
                            draft.references.add("artifact:" + artifactId);
                            published.artifactReferences.computeIfAbsent(
                                    artifactId, ignored -> new TreeSet<>())
                                    .add(identity + ":current");
                        } else if (revisionValid) {
                            published.revisionReferences.computeIfAbsent(
                                    record.artifactRevision(),
                                    ignored -> new TreeSet<>())
                                    .add(identity + ":current-by-revision");
                        }
                    }
                    result.add(draft);
                });
    }

    private void classifyArtifacts(
            WorkspaceFacts workspaces,
            PublishedFacts published,
            Builder result
    ) {
        for (ArtifactFact artifact : published.artifacts.values()) {
            Set<String> references = published.artifactReferences
                    .computeIfAbsent(artifact.attemptId, ignored -> new TreeSet<>());
            references.addAll(workspaces.revisionReferences.getOrDefault(
                    artifact.revision, Set.of()));
            references.addAll(published.revisionReferences.getOrDefault(
                    artifact.revision, Set.of()));
            if (!references.isEmpty()) {
                artifact.draft.referenceClass = MUST_RETAIN;
                artifact.draft.references.addAll(references);
            } else if (!workspaces.referenceGraphComplete
                    || !published.referenceGraphComplete
                    || published.blockedAttemptIds.contains(artifact.attemptId)) {
                artifact.draft.referenceClass = UNKNOWN_PRESERVE;
                artifact.draft.blockedReason =
                        "ARTIFACT_REFERENCE_GRAPH_INCOMPLETE";
                result.block("ARTIFACT_REFERENCE_GRAPH_INCOMPLETE");
            }
        }
    }

    private void scanOwnedJsonTemporary(
            Path path,
            String storeId,
            String store,
            String type,
            boolean referenceGraphComplete,
            Builder result,
            RootScan scan
    ) {
        try {
            requireRegularFile(path);
            JsonNode node = objectMapper.readTree(path.toFile());
            if (!storeId.equals(text(node, "storeId"))) {
                throw new IOException("owner mismatch");
            }
            Draft temporary = new Draft(store, type, type.toLowerCase(),
                    "INTERRUPTED", safeSize(path),
                    referenceGraphComplete ? CANDIDATE : UNKNOWN_PRESERVE);
            if (!referenceGraphComplete) {
                temporary.blockedReason =
                        "WORKSPACE_REFERENCE_GRAPH_INCOMPLETE";
                scan.block(temporary.blockedReason);
            }
            result.add(temporary);
        } catch (IOException | RuntimeException failure) {
            result.addBlocked(store, type, type.toLowerCase(), safeSize(path),
                    "WORKSPACE_TEMPORARY_OWNER_MISMATCH", scan);
        }
    }

    private boolean prepareReadableRoot(Path root, RootScan scan) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            scan.notInitialized();
            scan.finishMissing();
            return false;
        }
        try {
            if (Files.isSymbolicLink(root)
                    || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                    || !root.equals(root.toRealPath())) {
                scan.block(scan.store + "_ROOT_UNSAFE");
                scan.finishMissing();
                return false;
            }
            if (!Files.isReadable(root)) {
                scan.block(scan.store + "_ROOT_UNREADABLE");
                scan.finishMissing();
                return false;
            }
            return true;
        } catch (IOException | RuntimeException failure) {
            scan.block(scan.store + "_ROOT_UNREADABLE");
            scan.finishMissing();
            return false;
        }
    }

    private Path configuredRoot(boolean workspace) {
        FoggyRuntimeApiProperties.AuthoringWorkspaces configured =
                properties.getAuthoringWorkspaces();
        String value = configured == null ? null : workspace
                ? configured.getPath() : configured.getPublishedBundlesPath();
        String fallback = workspace
                ? ".foggy-runtime/authoring-workspaces"
                : ".foggy-runtime/published-bundles";
        try {
            return Path.of(StringUtils.hasText(value) ? value : fallback)
                    .toAbsolutePath().normalize();
        } catch (RuntimeException invalid) {
            throw failure(invalid);
        }
    }

    private String ownerIdentity(Path owner) {
        try {
            requireRegularFile(owner);
            JsonNode value = objectMapper.readTree(owner.toFile());
            String storeId = text(value, "storeId");
            if (value.path("version").asInt(-1) != 1
                    || !STORE_ID.matcher(nullToEmpty(storeId)).matches()) {
                return null;
            }
            return storeId;
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }

    private boolean matchingOwner(
            Path marker,
            String storeId,
            String identityField,
            String identity
    ) {
        try {
            requireRegularFile(marker);
            JsonNode value = objectMapper.readTree(marker.toFile());
            return value.path("version").asInt(-1) == 1
                    && storeId.equals(text(value, "storeId"))
                    && identity.equals(text(value, identityField));
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private boolean validSnapshotDirectory(Path root, String revision) {
        return validSnapshotDirectory(root, revision, null);
    }

    private boolean validSnapshotDirectory(
            Path root,
            String revision,
            String ignoredFile
    ) {
        if (!plainDirectory(root)) {
            return false;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            Map<String, byte[]> resources = new TreeMap<>();
            for (Path path : paths.toList()) {
                if (path.equals(root) || Files.isDirectory(
                        path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (Files.isSymbolicLink(path)
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    return false;
                }
                String relative = root.relativize(path).toString()
                        .replace('\\', '/');
                if (ignoredFile != null && ignoredFile.equals(relative)) {
                    continue;
                }
                if (!CandidateContentRevision.isCandidateResource(Path.of(relative))) {
                    return false;
                }
                resources.put(relative, Files.readAllBytes(path));
            }
            return revision.equals(CandidateContentRevision.calculate(resources));
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private void validateAttempt(String id, PublicationAttempt attempt) {
        if (attempt == null || attempt.version() != 1
                || !id.equals(attempt.attemptId())
                || !StringUtils.hasText(attempt.workspaceId())
                || !StringUtils.hasText(attempt.namespace())
                || !StringUtils.hasText(attempt.bundle())
                || !validRevision(attempt.candidateRevision())
                || !validRevision(attempt.baseBundleRevision())
                || !StringUtils.hasText(attempt.baseSourceRevision())
                || !StringUtils.hasText(attempt.previousPath())
                || !StringUtils.hasText(attempt.startedAt())
                || (attempt.previousImmutablePublication()
                && !validRevision(attempt.previousArtifactRevision()))
                || !PUBLICATION_STATUSES.contains(attempt.status())) {
            throw new IllegalArgumentException("invalid publication attempt");
        }
        Instant.parse(attempt.startedAt());
        if (StringUtils.hasText(attempt.completedAt())) {
            Instant.parse(attempt.completedAt());
        }
        RollbackAttempt rollback = attempt.rollback();
        if (rollback == null) {
            return;
        }
        if (!"PUBLISHED".equals(attempt.status())
                || !ROLLBACK_STATUSES.contains(rollback.status())
                || !StringUtils.hasText(rollback.startedAt())) {
            throw new IllegalArgumentException("invalid rollback attempt");
        }
        Instant.parse(rollback.startedAt());
        if (StringUtils.hasText(rollback.completedAt())) {
            Instant.parse(rollback.completedAt());
        }
        if ("ROLLED_BACK".equals(rollback.status())
                && (!StringUtils.hasText(rollback.rolledBackSourceRevision())
                || !StringUtils.hasText(rollback.rolledBackCatalogGeneration())
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
    }

    private static String attemptStatus(PublicationAttempt attempt) {
        return attempt.rollback() == null
                ? attempt.status()
                : attempt.status() + "/" + attempt.rollback().status();
    }

    private static String artifactIdFromPath(String value, Path root) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            Path path = Path.of(value).toAbsolutePath().normalize();
            Path artifacts = root.resolve("artifacts").toAbsolutePath().normalize();
            if (!artifacts.equals(path.getParent())) {
                return null;
            }
            String id = path.getFileName().toString();
            return UUID_NAME.matcher(id).matches() ? id : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static boolean validRevision(String revision) {
        return revision != null && revision.matches("sha256:[0-9a-f]{64}");
    }

    private static boolean plainDirectory(Path path) {
        return path != null && !Files.isSymbolicLink(path)
                && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                && Files.isReadable(path);
    }

    private static void requireRegularFile(Path path) throws IOException {
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("unsafe file");
        }
    }

    private static List<Path> children(Path root) {
        if (!plainDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(root)) {
            return paths.sorted(Comparator.comparing(
                    path -> path.getFileName().toString())).toList();
        } catch (IOException | RuntimeException failure) {
            return List.of();
        }
    }

    private static boolean safeTree(Path root) {
        if (!plainDirectory(root)) {
            return false;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.allMatch(path -> !Files.isSymbolicLink(path)
                    && (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)));
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private static long safeTreeBytes(Path root) {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return 0L;
        }
        if (Files.isSymbolicLink(root)) {
            return 0L;
        }
        if (Files.isRegularFile(root, LinkOption.NOFOLLOW_LINKS)) {
            return safeSize(root);
        }
        try (Stream<Path> paths = Files.walk(root)) {
            long total = 0L;
            for (Path path : paths.toList()) {
                if (!Files.isSymbolicLink(path)
                        && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    total = Math.addExact(total, Files.size(path));
                }
            }
            return total;
        } catch (IOException | RuntimeException failure) {
            return 0L;
        }
    }

    private static long safeSize(Path path) {
        try {
            return path != null && !Files.isSymbolicLink(path)
                    && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    ? Files.size(path) : 0L;
        } catch (IOException | RuntimeException failure) {
            return 0L;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void addUnknownChildren(
            Path root,
            String store,
            Builder result,
            String reason
    ) {
        for (Path child : children(root)) {
            result.addBlocked(store, "UNKNOWN_ENTRY", store.toLowerCase() + "-entry",
                    safeTreeBytes(child), reason, null);
        }
    }

    private static RuntimeAuthoringWorkspaceException failure(Throwable cause) {
        RuntimeAuthoringWorkspaceException failure =
                new RuntimeAuthoringWorkspaceException(
                        "ARTIFACT_LIFECYCLE_INVENTORY_FAILED", PHASE,
                        "Artifact lifecycle inventory could not be collected.",
                        null, false);
        if (cause != null) {
            failure.addSuppressed(cause);
        }
        return failure;
    }

    private static final class Builder {
        private final List<Draft> objects = new ArrayList<>();
        private final List<RootScan> roots = new ArrayList<>();
        private final Set<String> blockedReasons = new TreeSet<>();

        RootScan root(String store) {
            RootScan root = new RootScan(store, objects.size());
            roots.add(root);
            return root;
        }

        void add(Draft draft) {
            objects.add(draft);
            if (StringUtils.hasText(draft.blockedReason)) {
                block(draft.blockedReason);
            }
        }

        void addBlocked(
                String store,
                String type,
                String identity,
                long bytes,
                String reason,
                RootScan root
        ) {
            Draft draft = new Draft(store, type, identity, "BLOCKED", bytes,
                    UNKNOWN_PRESERVE);
            draft.blockedReason = reason;
            add(draft);
            if (root != null) {
                root.block(reason);
            }
        }

        void block(String reason) {
            if (StringUtils.hasText(reason)) {
                blockedReasons.add(reason);
            }
        }

        void downgradeCandidates(
                String store,
                String reason,
                RootScan root
        ) {
            boolean downgraded = false;
            for (Draft draft : objects) {
                if (store.equals(draft.store)
                        && CANDIDATE.equals(draft.referenceClass)) {
                    draft.referenceClass = UNKNOWN_PRESERVE;
                    draft.blockedReason = reason;
                    downgraded = true;
                }
            }
            if (downgraded) {
                root.block(reason);
            }
        }

        ArtifactLifecycleInventory build(String capturedAt) {
            roots.stream().flatMap(root -> root.reasons.stream())
                    .forEach(this::block);
            List<InventoryObject> finalObjects = objects.stream()
                    .sorted(Comparator.comparing((Draft value) -> value.store)
                            .thenComparing(value -> value.type)
                            .thenComparing(value -> value.identity))
                    .map(Draft::toDto)
                    .toList();
            long totalBytes = roots.stream().mapToLong(root -> root.bytes).sum();
            long mustRetain = finalObjects.stream()
                    .filter(value -> MUST_RETAIN.equals(value.referenceClass())).count();
            long candidates = finalObjects.stream()
                    .filter(value -> CANDIDATE.equals(value.referenceClass())).count();
            long unknown = finalObjects.stream()
                    .filter(value -> UNKNOWN_PRESERVE.equals(value.referenceClass())).count();
            long blocked = finalObjects.stream()
                    .filter(value -> StringUtils.hasText(value.blockedReason())).count();
            String health;
            if (!blockedReasons.isEmpty()
                    || roots.stream().anyMatch(
                    root -> "BLOCKED".equals(root.health))) {
                health = "BLOCKED";
            } else if (roots.stream().allMatch(root -> "NOT_INITIALIZED".equals(root.health))) {
                health = "NOT_INITIALIZED";
            } else if (roots.stream().anyMatch(root -> "NOT_INITIALIZED".equals(root.health))) {
                health = "PARTIAL";
            } else {
                health = "HEALTHY";
            }
            return new ArtifactLifecycleInventory(
                    capturedAt, health,
                    roots.stream().map(RootScan::toDto).toList(),
                    new Summary(finalObjects.size(), totalBytes, mustRetain,
                            candidates, unknown, blocked),
                    finalObjects, List.copyOf(blockedReasons));
        }
    }

    private static final class RootScan {
        private final String store;
        private final int objectStart;
        private final Set<String> reasons = new TreeSet<>();
        private String health = "HEALTHY";
        private long objectCount;
        private long bytes;

        private RootScan(String store, int objectStart) {
            this.store = store;
            this.objectStart = objectStart;
        }

        void block(String reason) {
            health = "BLOCKED";
            reasons.add(reason);
        }

        void notInitialized() {
            if (!"BLOCKED".equals(health)) {
                health = "NOT_INITIALIZED";
            }
        }

        void finish(Path root, Builder result) {
            objectCount = result.objects.size() - objectStart;
            bytes = safeTreeBytes(root);
            reasons.forEach(result::block);
        }

        void finishMissing() {
            objectCount = 0L;
            bytes = 0L;
        }

        RootHealth toDto() {
            return new RootHealth(store, health, objectCount, bytes,
                    List.copyOf(reasons));
        }
    }

    private static final class Draft {
        private final String store;
        private final String type;
        private final String identity;
        private final String status;
        private final long bytes;
        private final Set<String> references = new TreeSet<>();
        private String referenceClass;
        private String blockedReason;

        private Draft(
                String store,
                String type,
                String identity,
                String status,
                long bytes,
                String referenceClass
        ) {
            this.store = store;
            this.type = type;
            this.identity = identity;
            this.status = status;
            this.bytes = bytes;
            this.referenceClass = referenceClass;
        }

        InventoryObject toDto() {
            return new InventoryObject(store, type, identity, status, bytes,
                    referenceClass, List.copyOf(references), blockedReason);
        }
    }

    private static final class WorkspaceFacts {
        private final Map<String, StoredWorkspace> records = new LinkedHashMap<>();
        private final Map<String, Set<String>> revisionReferences = new HashMap<>();
        private boolean referenceGraphComplete = true;
    }

    private static final class PublishedFacts {
        private final Map<String, AttemptFact> attempts = new LinkedHashMap<>();
        private final Map<String, ArtifactFact> artifacts = new LinkedHashMap<>();
        private final Map<String, Set<String>> artifactReferences = new HashMap<>();
        private final Map<String, Set<String>> revisionReferences = new HashMap<>();
        private final Set<String> blockedAttemptIds = new HashSet<>();
        private boolean referenceGraphComplete = true;

    }

    private record AttemptFact(PublicationAttempt attempt, Draft draft) {
    }

    private static final class ArtifactFact {
        private final String attemptId;
        private final String revision;
        private final Draft draft;

        private ArtifactFact(String attemptId, String revision, Draft draft) {
            this.attemptId = attemptId;
            this.revision = revision;
            this.draft = draft;
        }
    }

    private record LeaseKey(String workspaceId, String revision) {
    }
}
