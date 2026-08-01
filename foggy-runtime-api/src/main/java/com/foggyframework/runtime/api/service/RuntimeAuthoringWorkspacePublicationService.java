package com.foggyframework.runtime.api.service;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistry;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo.PublicationEvidence;
import com.foggyframework.runtime.api.dto.AuthoringWorkspacePublishRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceRecoverRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceState;
import com.foggyframework.runtime.api.dto.ModelRefreshRequest;
import com.foggyframework.runtime.api.dto.ModelRefreshResponse;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceService.PublicationCandidate;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceStore.StoredWorkspace;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService.RuntimeBundleRecord;
import com.foggyframework.runtime.api.service.RuntimePublishedBundleArtifactStore.PublicationAttempt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Coordinates durable, recoverable publication of one exact workspace revision. */
@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeAuthoringWorkspacePublicationService {

    private final RuntimeAuthoringWorkspaceStore workspaceStore;
    private final RuntimeAuthoringWorkspaceService workspaceService;
    private final RuntimePublishedBundleArtifactStore artifactStore;
    private final RuntimeBundleRegistryService bundleRegistry;
    private final SystemBundlesContext bundlesContext;
    private final RuntimeModelOperations modelOperations;
    private final RuntimeAuthoringPublicationLock publicationLock;
    private final ObjectProvider<CommittedSourceRevisionRegistry> sourceRegistryProvider;
    private final ObjectProvider<CatalogSnapshotStore> catalogStoreProvider;

    public RuntimeAuthoringWorkspacePublicationService(
            RuntimeAuthoringWorkspaceStore workspaceStore,
            RuntimeAuthoringWorkspaceService workspaceService,
            RuntimePublishedBundleArtifactStore artifactStore,
            RuntimeBundleRegistryService bundleRegistry,
            SystemBundlesContext bundlesContext,
            RuntimeModelOperations modelOperations,
            RuntimeAuthoringPublicationLock publicationLock,
            ObjectProvider<CommittedSourceRevisionRegistry> sourceRegistryProvider,
            ObjectProvider<CatalogSnapshotStore> catalogStoreProvider
    ) {
        this.workspaceStore = workspaceStore;
        this.workspaceService = workspaceService;
        this.artifactStore = artifactStore;
        this.bundleRegistry = bundleRegistry;
        this.bundlesContext = bundlesContext;
        this.modelOperations = modelOperations;
        this.publicationLock = publicationLock;
        this.sourceRegistryProvider = sourceRegistryProvider;
        this.catalogStoreProvider = catalogStoreProvider;
    }

    public AuthoringWorkspaceInfo publish(
            String workspaceId,
            AuthoringWorkspacePublishRequest request
    ) {
        try (RuntimeAuthoringPublicationLock.Guard ignored = publicationLock.acquire()) {
            if (request == null) {
                throw failure("WORKSPACE_INVALID_REQUEST",
                        "workspaces.publish.preflight",
                        "Publication revision identities are required.", true);
            }
            PublicationCandidate candidate = workspaceService.preflightPublication(
                    workspaceId, request.expectedCandidateRevision(),
                    request.expectedBaseBundleRevision(),
                    request.expectedBaseNamespaceSourceRevision());
            StoredWorkspace workspace = candidate.workspace();
            RuntimeBundleRecord baseRecord = candidate.source().record();
            requireEligibleBaseRecord(workspace, baseRecord);
            if (baseRecord.immutablePublication()) {
                artifactStore.verifyPublishedSource(baseRecord);
            }
            workspaceService.assertPublicationBaseCurrent(workspace);

            String attemptId = artifactStore.newAttemptId();
            Path artifact = artifactStore.prepareArtifact(
                    attemptId, workspace.workspaceId(), workspace.namespace(),
                    workspace.sourceBundle(), workspace.candidateRevision(),
                    candidate.snapshot());
            String startedAt = Instant.now().toString();
            PublicationAttempt attempt = new PublicationAttempt(
                    1, attemptId, workspace.workspaceId(), workspace.namespace(),
                    workspace.sourceBundle(), workspace.candidateRevision(),
                    workspace.baseBundleRevision(), workspace.baseSourceRevision(),
                    baseRecord.path(), baseRecord.watch(), baseRecord.enabled(),
                    baseRecord.createdAt(), baseRecord.updatedAt(),
                    baseRecord.immutablePublication(), baseRecord.artifactRevision(),
                    "PUBLISHING", null, catalogGeneration(workspace.namespace()),
                    null, null, startedAt, null, List.of());
            artifactStore.begin(attempt);

            PublicationEvidence publishing = evidence(attempt, "PUBLISHING",
                    null, null, null, null, null, List.of());
            try {
                workspaceStore.beginPublication(workspace.workspaceId(),
                        workspace.candidateRevision(), publishing);
            } catch (RuntimeException beginFailure) {
                bestEffortAttempt(attempt.withStatus(
                        "FAILED", null, attempt.beforeCatalogGeneration(),
                        null, null, Instant.now().toString(),
                        List.of("Workspace publication state could not be committed.")));
                throw failure("WORKSPACE_PUBLISH_FAILED",
                        "workspaces.publish.commit",
                        "Publication intent could not be committed.", false,
                        beginFailure);
            }

            boolean sourceSwitched = false;
            try {
                workspaceService.assertPublicationSourceCurrent(
                        workspace, candidate.snapshot());
                sourceSwitched = bundlesContext.replaceExternalBundle(
                        workspace.sourceBundle(), workspace.namespace(),
                        artifact.toString(), false);
                if (!sourceSwitched) {
                    throw failure("WORKSPACE_PUBLISH_FAILED",
                            "workspaces.publish.source",
                            "Published Bundle source could not be activated.", false);
                }
                String publishedSourceRevision = sourceRegistry(
                        "workspaces.publish.source")
                        .currentRevision(workspace.namespace());
                RuntimeBundleRecord published = baseRecord.withPublication(
                        artifact.toString(), workspace.candidateRevision());
                bundleRegistry.save(published);
                attempt = attempt.withStatus(
                        "SOURCE_APPLIED", publishedSourceRevision,
                        attempt.beforeCatalogGeneration(), null, null,
                        null, List.of());
                artifactStore.update(attempt);

                ModelRefreshResponse refresh = fullRefresh(workspace.namespace());
                requireRefreshCurrent(workspace.namespace(), refresh,
                        publishedSourceRevision, "workspaces.publish.refresh");
                String completedAt = Instant.now().toString();
                attempt = attempt.withStatus(
                        "PUBLISHED", publishedSourceRevision,
                        valueOr(attempt.beforeCatalogGeneration(),
                                refresh.beforeCatalogGeneration()),
                        refresh.afterCatalogGeneration(), null, completedAt,
                        List.of());
                artifactStore.update(attempt);
                PublicationEvidence publishedEvidence = evidence(
                        attempt, "PUBLISHED", publishedSourceRevision,
                        attempt.beforeCatalogGeneration(),
                        refresh.afterCatalogGeneration(), null,
                        completedAt, List.of());
                StoredWorkspace publishedWorkspace = workspaceStore.markPublished(
                        workspace.workspaceId(), attemptId, publishedEvidence);
                return workspaceStore.toInfo(publishedWorkspace);
            } catch (RuntimeException publishFailure) {
                return failAfterPublicationStarted(
                        workspace, attempt, artifact, baseRecord,
                        sourceSwitched, publishFailure);
            }
        }
    }

    public AuthoringWorkspaceInfo recover(
            String workspaceId,
            AuthoringWorkspaceRecoverRequest request
    ) {
        try (RuntimeAuthoringPublicationLock.Guard ignored = publicationLock.acquire()) {
            String phase = "workspaces.publish.recovery";
            if (request == null
                    || !StringUtils.hasText(request.expectedCandidateRevision())
                    || !StringUtils.hasText(request.publicationAttemptId())) {
                throw failure("WORKSPACE_INVALID_REQUEST", phase,
                        "Candidate revision and publication attempt are required.", true);
            }
            StoredWorkspace workspace = workspaceStore.get(workspaceId);
            if (!workspace.candidateRevision().equals(
                    request.expectedCandidateRevision().trim())) {
                throw failure("WORKSPACE_REVISION_CONFLICT", phase,
                        "Workspace candidate revision is no longer current.", true);
            }
            PublicationEvidence evidence = workspace.lastPublication();
            if (evidence == null || !request.publicationAttemptId().trim()
                    .equals(evidence.attemptId())) {
                throw failure("WORKSPACE_RECOVERY_CONFLICT", phase,
                        "Publication attempt is no longer current.", false);
            }
            PublicationAttempt attempt = artifactStore.get(evidence.attemptId());
            requireAttemptMatches(workspace, attempt);
            artifactStore.artifactPath(attempt);

            if (workspace.state() == AuthoringWorkspaceState.STALE
                    && "RECOVERED".equals(evidence.status())) {
                return workspaceStore.toInfo(workspace);
            }
            if (workspace.state() != AuthoringWorkspaceState.RECOVERY_REQUIRED) {
                throw failure("WORKSPACE_STATE_INVALID", phase,
                        "Workspace does not require publication recovery.", false);
            }
            RuntimeBundleRecord baseRecord = baseRecord(attempt);
            if ("RECOVERED".equals(attempt.status())
                    && baseIsCurrent(attempt, baseRecord)
                    && catalogIsCurrent(attempt.namespace())) {
                StoredWorkspace recovered = workspaceStore.markRecovered(
                        workspace.workspaceId(), attempt.attemptId(),
                        evidence(attempt, "RECOVERED",
                                attempt.publishedSourceRevision(),
                                attempt.beforeCatalogGeneration(),
                                attempt.afterCatalogGeneration(),
                                attempt.recoveredCatalogGeneration(),
                                attempt.completedAt(), attempt.diagnostics()),
                        "Publication recovery restored the base Bundle revision.");
                return workspaceStore.toInfo(recovered);
            }
            try {
                PublicationAttempt recovered = restoreBase(
                        attempt, baseRecord, true);
                StoredWorkspace stored = workspaceStore.markRecovered(
                        workspace.workspaceId(), attempt.attemptId(),
                        evidence(recovered, "RECOVERED",
                                recovered.publishedSourceRevision(),
                                recovered.beforeCatalogGeneration(),
                                recovered.afterCatalogGeneration(),
                                recovered.recoveredCatalogGeneration(),
                                recovered.completedAt(), recovered.diagnostics()),
                        "Publication recovery restored the base Bundle revision.");
                return workspaceStore.toInfo(stored);
            } catch (RuntimeException recoveryFailure) {
                recordRecoveryRequired(workspace, attempt,
                        "Explicit publication recovery could not prove base convergence.");
                if (recoveryFailure instanceof RuntimeAuthoringWorkspaceException typed
                        && "WORKSPACE_RECOVERY_CONFLICT".equals(typed.code())) {
                    throw typed;
                }
                throw failure("WORKSPACE_RECOVERY_FAILED", phase,
                        "Publication recovery failed; live state was preserved.",
                        false, recoveryFailure);
            }
        }
    }

    private AuthoringWorkspaceInfo failAfterPublicationStarted(
            StoredWorkspace workspace,
            PublicationAttempt attempt,
            Path artifact,
            RuntimeBundleRecord baseRecord,
            boolean sourceSwitched,
            RuntimeException publishFailure
    ) {
        try {
            PublicationAttempt recovered = restoreBase(
                    attempt, baseRecord, sourceSwitched);
            workspaceStore.markRecovered(
                    workspace.workspaceId(), attempt.attemptId(),
                    evidence(recovered, "RECOVERED",
                            recovered.publishedSourceRevision(),
                            recovered.beforeCatalogGeneration(),
                            recovered.afterCatalogGeneration(),
                            recovered.recoveredCatalogGeneration(),
                            recovered.completedAt(), recovered.diagnostics()),
                    "Publication failed and the base Bundle revision was restored.");
            throw failure("WORKSPACE_PUBLISH_FAILED",
                    phaseOf(publishFailure),
                    "Publication failed; the previous live Bundle was restored.",
                    false, publishFailure);
        } catch (RuntimeAuthoringWorkspaceException recoveredFailure) {
            if ("WORKSPACE_PUBLISH_FAILED".equals(recoveredFailure.code())) {
                throw recoveredFailure;
            }
            recordRecoveryRequired(workspace, attempt,
                    "Automatic publication recovery could not prove base convergence.");
            throw failure("WORKSPACE_RECOVERY_REQUIRED",
                    "workspaces.publish.recovery",
                    "Publication failed and requires explicit recovery.",
                    false, recoveredFailure);
        } catch (RuntimeException recoveryFailure) {
            recordRecoveryRequired(workspace, attempt,
                    "Automatic publication recovery could not prove base convergence.");
            throw failure("WORKSPACE_RECOVERY_REQUIRED",
                    "workspaces.publish.recovery",
                    "Publication failed and requires explicit recovery.",
                    false, recoveryFailure);
        }
    }

    private PublicationAttempt restoreBase(
            PublicationAttempt attempt,
            RuntimeBundleRecord baseRecord,
            boolean refreshRequired
    ) {
        SourcePosition position = sourcePosition(attempt, baseRecord);
        if (position.live() == Position.CANDIDATE) {
            if (!bundlesContext.replaceExternalBundle(
                    attempt.bundle(), attempt.namespace(),
                    baseRecord.path(), baseRecord.watch())) {
                throw failure("WORKSPACE_RECOVERY_FAILED",
                        "workspaces.publish.recovery",
                        "Previous Bundle source could not be restored.", false);
            }
            refreshRequired = true;
        }
        if (position.registry() == Position.CANDIDATE) {
            bundleRegistry.restoreExact(baseRecord);
        }
        if (refreshRequired || !catalogIsCurrent(attempt.namespace())) {
            ModelRefreshResponse refresh = fullRefresh(attempt.namespace());
            String currentSource = sourceRegistry("workspaces.publish.recovery")
                    .currentRevision(attempt.namespace());
            requireRefreshCurrent(attempt.namespace(), refresh, currentSource,
                    "workspaces.publish.recovery");
            if (!baseIsCurrent(attempt, baseRecord)) {
                throw failure("WORKSPACE_RECOVERY_CONFLICT",
                        "workspaces.publish.recovery",
                        "Bundle source changed while recovery was completing.", false);
            }
            PublicationAttempt recovered = attempt.withStatus(
                    "RECOVERED", attempt.publishedSourceRevision(),
                    valueOr(attempt.beforeCatalogGeneration(),
                            refresh.beforeCatalogGeneration()),
                    attempt.afterCatalogGeneration(),
                    refresh.afterCatalogGeneration(), Instant.now().toString(),
                    List.of("Previous live Bundle source and catalog were restored."));
            artifactStore.update(recovered);
            return recovered;
        }
        PublicationAttempt recovered = attempt.withStatus(
                "RECOVERED", attempt.publishedSourceRevision(),
                attempt.beforeCatalogGeneration(), attempt.afterCatalogGeneration(),
                attempt.recoveredCatalogGeneration(), Instant.now().toString(),
                List.of("Previous live Bundle source and catalog were already current."));
        artifactStore.update(recovered);
        return recovered;
    }

    private SourcePosition sourcePosition(
            PublicationAttempt attempt,
            RuntimeBundleRecord baseRecord
    ) {
        RuntimeBundleRecord current = bundleRegistry.find(attempt.bundle())
                .orElseThrow(() -> failure("WORKSPACE_RECOVERY_CONFLICT",
                        "workspaces.publish.recovery",
                        "Runtime Bundle registry no longer contains the publication target.",
                        false));
        Position registryPosition = classifyRecord(current, attempt, baseRecord);
        BundleDefinition definition = bundlesContext.getBundleDefinitionByName(
                attempt.bundle());
        if (!(definition instanceof ExternalBundleDefinition external)
                || !canonicalNamespace(attempt.namespace()).equals(
                canonicalNamespace(external.getNamespace()))) {
            throw failure("WORKSPACE_RECOVERY_CONFLICT",
                    "workspaces.publish.recovery",
                    "Live Bundle identity no longer matches the publication attempt.", false);
        }
        Position livePosition;
        if (samePath(external.getPath(), baseRecord.path())
                && external.isWatch() == baseRecord.watch()) {
            livePosition = Position.BASE;
        } else if (samePath(external.getPath(),
                artifactStore.artifactPath(attempt).toString())
                && !external.isWatch()) {
            livePosition = Position.CANDIDATE;
        } else {
            throw failure("WORKSPACE_RECOVERY_CONFLICT",
                    "workspaces.publish.recovery",
                    "Live Bundle source drifted outside the failed publication attempt.",
                    false);
        }
        return new SourcePosition(registryPosition, livePosition);
    }

    private Position classifyRecord(
            RuntimeBundleRecord current,
            PublicationAttempt attempt,
            RuntimeBundleRecord baseRecord
    ) {
        if (sameRecord(current, baseRecord)) {
            return Position.BASE;
        }
        Path artifact = artifactStore.artifactPath(attempt);
        if (current.enabled() && !current.watch()
                && current.immutablePublication()
                && attempt.candidateRevision().equals(current.artifactRevision())
                && attempt.namespace().equals(current.namespace())
                && samePath(current.path(), artifact.toString())) {
            return Position.CANDIDATE;
        }
        throw failure("WORKSPACE_RECOVERY_CONFLICT",
                "workspaces.publish.recovery",
                "Runtime Bundle registry drifted outside the failed publication attempt.",
                false);
    }

    private boolean baseIsCurrent(
            PublicationAttempt attempt,
            RuntimeBundleRecord baseRecord
    ) {
        try {
            SourcePosition position = sourcePosition(attempt, baseRecord);
            return position.registry() == Position.BASE
                    && position.live() == Position.BASE;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean catalogIsCurrent(String namespace) {
        CatalogSnapshotStore store = catalogStoreProvider.getIfAvailable();
        CommittedSourceRevisionRegistry source = sourceRegistryProvider.getIfAvailable();
        if (store == null || source == null) {
            return false;
        }
        return store.current(namespace)
                .map(snapshot -> source.currentRevision(namespace).equals(
                        snapshot.identity().sourceRevision().value()))
                .orElse(false);
    }

    private String catalogGeneration(String namespace) {
        CatalogSnapshotStore store = catalogStoreProvider.getIfAvailable();
        if (store == null) {
            return null;
        }
        return store.current(namespace)
                .map(snapshot -> snapshot.identity().generation().value())
                .orElse(null);
    }

    private ModelRefreshResponse fullRefresh(String namespace) {
        return modelOperations.refreshModels(
                new ModelRefreshRequest(namespace, List.of()), namespace);
    }

    private void requireRefreshCurrent(
            String namespace,
            ModelRefreshResponse refresh,
            String expectedSourceRevision,
            String phase
    ) {
        if (refresh == null
                || !namespace.equals(canonicalNamespace(refresh.namespace()))
                || !"namespace".equals(refresh.scope())
                || !StringUtils.hasText(refresh.afterCatalogGeneration())
                || !expectedSourceRevision.equals(refresh.sourceRevision())
                || !expectedSourceRevision.equals(sourceRegistry(phase)
                .currentRevision(namespace))) {
            throw failure("WORKSPACE_PUBLISH_FAILED", phase,
                    "Full Namespace catalog refresh did not converge to the expected source.",
                    false);
        }
    }

    private void recordRecoveryRequired(
            StoredWorkspace workspace,
            PublicationAttempt attempt,
            String diagnostic
    ) {
        List<String> diagnostics = new ArrayList<>(attempt.diagnostics());
        diagnostics.add(diagnostic);
        PublicationAttempt required = attempt.withStatus(
                "RECOVERY_REQUIRED", attempt.publishedSourceRevision(),
                attempt.beforeCatalogGeneration(), attempt.afterCatalogGeneration(),
                attempt.recoveredCatalogGeneration(), null, diagnostics);
        bestEffortAttempt(required);
        try {
            workspaceStore.markRecoveryRequired(
                    workspace.workspaceId(), attempt.attemptId(),
                    evidence(required, "RECOVERY_REQUIRED",
                            required.publishedSourceRevision(),
                            required.beforeCatalogGeneration(),
                            required.afterCatalogGeneration(),
                            required.recoveredCatalogGeneration(), null,
                            diagnostics), diagnostic);
        } catch (RuntimeException ignored) {
            // Restart reconciliation converts durable PUBLISHING evidence.
        }
    }

    private void bestEffortAttempt(PublicationAttempt attempt) {
        try {
            artifactStore.update(attempt);
        } catch (RuntimeException ignored) {
            // The last durable attempt remains authoritative for explicit recovery.
        }
    }

    private static RuntimeBundleRecord baseRecord(PublicationAttempt attempt) {
        return new RuntimeBundleRecord(
                attempt.bundle(), attempt.namespace(), attempt.previousPath(),
                attempt.previousWatch(), attempt.previousEnabled(),
                attempt.previousCreatedAt(), attempt.previousUpdatedAt(),
                attempt.previousImmutablePublication(),
                attempt.previousArtifactRevision());
    }

    private static void requireEligibleBaseRecord(
            StoredWorkspace workspace,
            RuntimeBundleRecord record
    ) {
        if (record == null || !record.enabled()
                || !workspace.sourceBundle().equals(record.name())
                || !workspace.namespace().equals(canonicalNamespace(record.namespace()))
                || (record.immutablePublication()
                && (record.watch()
                || !workspace.baseBundleRevision().equals(record.artifactRevision())))
                || (!record.immutablePublication()
                && StringUtils.hasText(record.artifactRevision()))) {
            throw failure("WORKSPACE_SOURCE_INELIGIBLE",
                    "workspaces.publish.preflight",
                    "Publication target is not a current Runtime-managed Bundle.", false);
        }
    }

    private static void requireAttemptMatches(
            StoredWorkspace workspace,
            PublicationAttempt attempt
    ) {
        if (!workspace.workspaceId().equals(attempt.workspaceId())
                || !workspace.namespace().equals(attempt.namespace())
                || !workspace.sourceBundle().equals(attempt.bundle())
                || !workspace.candidateRevision().equals(
                attempt.candidateRevision())
                || !workspace.baseBundleRevision().equals(
                attempt.baseBundleRevision())
                || !workspace.baseSourceRevision().equals(
                attempt.baseSourceRevision())) {
            throw failure("WORKSPACE_RECOVERY_CONFLICT",
                    "workspaces.publish.recovery",
                    "Publication attempt does not match the workspace identity.", false);
        }
    }

    private static boolean sameRecord(
            RuntimeBundleRecord first,
            RuntimeBundleRecord second
    ) {
        return Objects.equals(first.name(), second.name())
                && Objects.equals(canonicalNamespace(first.namespace()),
                canonicalNamespace(second.namespace()))
                && samePath(first.path(), second.path())
                && first.watch() == second.watch()
                && first.enabled() == second.enabled()
                && first.immutablePublication() == second.immutablePublication()
                && Objects.equals(first.artifactRevision(), second.artifactRevision())
                && Objects.equals(first.createdAt(), second.createdAt())
                && Objects.equals(first.updatedAt(), second.updatedAt());
    }

    private static boolean samePath(String first, String second) {
        try {
            return StringUtils.hasText(first) && StringUtils.hasText(second)
                    && Path.of(first).toAbsolutePath().normalize().equals(
                    Path.of(second).toAbsolutePath().normalize());
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private CommittedSourceRevisionRegistry sourceRegistry(String phase) {
        CommittedSourceRevisionRegistry registry =
                sourceRegistryProvider.getIfAvailable();
        if (registry == null) {
            throw failure("WORKSPACE_SOURCE_INELIGIBLE", phase,
                    "Committed source revision tracking is unavailable.", false);
        }
        return registry;
    }

    private static PublicationEvidence evidence(
            PublicationAttempt attempt,
            String status,
            String sourceRevision,
            String beforeGeneration,
            String afterGeneration,
            String recoveredGeneration,
            String completedAt,
            List<String> diagnostics
    ) {
        return new PublicationEvidence(
                attempt.attemptId(), status, attempt.candidateRevision(),
                attempt.baseBundleRevision(), attempt.candidateRevision(),
                attempt.baseSourceRevision(), sourceRevision,
                beforeGeneration, afterGeneration, recoveredGeneration,
                attempt.startedAt(), completedAt, diagnostics);
    }

    private static String phaseOf(RuntimeException failure) {
        return failure instanceof RuntimeAuthoringWorkspaceException typed
                ? typed.phase() : "workspaces.publish.commit";
    }

    private static String valueOr(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private static String canonicalNamespace(String namespace) {
        return namespace == null ? "" : namespace.trim();
    }

    private static RuntimeAuthoringWorkspaceException failure(
            String code,
            String phase,
            String message,
            boolean safeToAutoRepair
    ) {
        return RuntimeAuthoringWorkspaceStore.failure(
                code, phase, message, null, safeToAutoRepair);
    }

    private static RuntimeAuthoringWorkspaceException failure(
            String code,
            String phase,
            String message,
            boolean safeToAutoRepair,
            Throwable cause
    ) {
        RuntimeAuthoringWorkspaceException failure = failure(
                code, phase, message, safeToAutoRepair);
        if (cause != null) {
            failure.addSuppressed(cause);
        }
        return failure;
    }

    private enum Position {
        BASE,
        CANDIDATE
    }

    private record SourcePosition(Position registry, Position live) {
    }
}
