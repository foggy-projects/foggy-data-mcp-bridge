package com.foggyframework.runtime.api.service;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.candidate.CandidateContentRevision;
import com.foggyframework.dataset.model.candidate.CandidateQueryErrorCode;
import com.foggyframework.dataset.model.candidate.CandidateQueryException;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.validation.DetachedModelValidationFactory;
import com.foggyframework.dataset.model.validation.DetachedModelValidationSession;
import com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistry;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceCreateRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceDeleteRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceDiffResponse;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo.ReleaseImportEvidence;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceListResponse;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceQueryResponse;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceResource;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceResourcesResponse;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceSaveRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceState;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceStore.RevisionLease;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceStore.StoredWorkspace;
import com.foggyframework.runtime.api.service.RuntimeBundleInventoryService.WorkspaceSource;
import com.foggyframework.runtime.api.service.RuntimeCandidateQueryService.Phase;
import com.foggyframework.runtime.api.service.RuntimeCandidateQueryService.RuntimeCandidateQueryCommand;
import com.foggyframework.runtime.api.service.RuntimeCandidateQueryService.RuntimeCandidateQueryResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Runtime-local orchestration for immutable authoring workspace revisions. */
@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeAuthoringWorkspaceService {

    private final RuntimeAuthoringWorkspaceStore store;
    private final RuntimeBundleInventoryService inventory;
    private final SystemBundlesContext bundlesContext;
    private final ObjectProvider<CommittedSourceRevisionRegistry> sourceRegistryProvider;
    private final ObjectProvider<DetachedModelValidationFactory> validationFactoryProvider;
    private final ObjectProvider<RuntimeCandidateQueryService> candidateQueryProvider;

    public RuntimeAuthoringWorkspaceService(
            RuntimeAuthoringWorkspaceStore store,
            RuntimeBundleInventoryService inventory,
            SystemBundlesContext bundlesContext,
            ObjectProvider<CommittedSourceRevisionRegistry> sourceRegistryProvider,
            ObjectProvider<DetachedModelValidationFactory> validationFactoryProvider,
            ObjectProvider<RuntimeCandidateQueryService> candidateQueryProvider
    ) {
        this.store = store;
        this.inventory = inventory;
        this.bundlesContext = bundlesContext;
        this.sourceRegistryProvider = sourceRegistryProvider;
        this.validationFactoryProvider = validationFactoryProvider;
        this.candidateQueryProvider = candidateQueryProvider;
    }

    public AuthoringWorkspaceInfo create(
            String headerNamespace,
            AuthoringWorkspaceCreateRequest request
    ) {
        String phase = "workspaces.create";
        if (request == null || !StringUtils.hasText(request.sourceBundle())) {
            throw invalid(phase,
                    "A source Bundle is required.", null, false);
        }
        String namespace = resolveNamespace(
                headerNamespace, request.namespace(), phase);
        String bundleName = request.sourceBundle().trim();
        CommittedSourceRevisionRegistry sourceRegistry = sourceRegistry(phase);

        String revisionBefore = sourceRegistry.currentRevision(namespace);
        WorkspaceSource firstSource = inventory.requireWorkspaceSource(
                bundleName, namespace, phase);
        Map<String, byte[]> firstSnapshot = readSourceSnapshot(
                firstSource.path(), phase);
        WorkspaceSource secondSource = inventory.requireWorkspaceSource(
                bundleName, namespace, phase);
        Map<String, byte[]> snapshot = readSourceSnapshot(
                secondSource.path(), phase);
        String revisionAfter = sourceRegistry.currentRevision(namespace);
        if (!revisionBefore.equals(revisionAfter)
                || !firstSource.sourceIdentity().equals(
                secondSource.sourceIdentity())
                || !CandidateContentRevision.calculate(firstSnapshot).equals(
                CandidateContentRevision.calculate(snapshot))) {
            throw stale(phase,
                    "Bundle source changed while the workspace was being created.");
        }
        requireOverlayAllowed(bundleName, namespace, snapshot, phase);

        StoredWorkspace created = store.create(
                namespace, bundleName, revisionAfter,
                secondSource.sourceIdentity(), snapshot);
        try {
            if (!sourceIsCurrent(created)) {
                store.rollbackCreate(created.workspaceId());
                throw stale(phase,
                        "Bundle source changed while the workspace was being created.");
            }
            return store.toInfo(created);
        } catch (RuntimeAuthoringWorkspaceException failure) {
            try {
                store.rollbackCreate(created.workspaceId());
            } catch (RuntimeAuthoringWorkspaceException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } catch (RuntimeException failure) {
            try {
                store.rollbackCreate(created.workspaceId());
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    public AuthoringWorkspaceInfo importRelease(
            String headerNamespace,
            String bodyNamespace,
            String targetBundle,
            Map<String, byte[]> candidateSnapshot,
            ReleaseImportEvidence releaseImport
    ) {
        String phase = "workspaces.release.import";
        if (!StringUtils.hasText(targetBundle) || releaseImport == null) {
            throw invalid(phase,
                    "A target Bundle and release package are required.", null, false);
        }
        String namespace = resolveNamespace(
                headerNamespace, bodyNamespace, phase);
        String bundleName = targetBundle.trim();
        CommittedSourceRevisionRegistry sourceRegistry = sourceRegistry(phase);
        String revisionBefore = sourceRegistry.currentRevision(namespace);
        WorkspaceSource firstSource = inventory.requireWorkspaceSource(
                bundleName, namespace, phase);
        Map<String, byte[]> firstBase = readSourceSnapshot(
                firstSource.path(), phase);
        WorkspaceSource secondSource = inventory.requireWorkspaceSource(
                bundleName, namespace, phase);
        Map<String, byte[]> base = readSourceSnapshot(secondSource.path(), phase);
        String revisionAfter = sourceRegistry.currentRevision(namespace);
        if (!revisionBefore.equals(revisionAfter)
                || !firstSource.sourceIdentity().equals(
                secondSource.sourceIdentity())
                || !CandidateContentRevision.calculate(firstBase).equals(
                CandidateContentRevision.calculate(base))) {
            throw stale(phase,
                    "Target Bundle changed while the release was being imported.");
        }
        requireOverlayAllowed(bundleName, namespace, candidateSnapshot, phase);
        StoredWorkspace created = store.createImported(
                namespace, bundleName, revisionAfter,
                secondSource.sourceIdentity(), base, candidateSnapshot,
                releaseImport);
        try {
            if (!sourceIsCurrent(created)) {
                store.rollbackImportedCreate(created.workspaceId());
                throw stale(phase,
                        "Target Bundle changed while the release was being imported.");
            }
            return store.toInfo(created);
        } catch (RuntimeAuthoringWorkspaceException failure) {
            try {
                store.rollbackImportedCreate(created.workspaceId());
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } catch (RuntimeException failure) {
            try {
                store.rollbackImportedCreate(created.workspaceId());
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    public AuthoringWorkspaceListResponse list(
            String namespace,
            AuthoringWorkspaceState state,
            boolean includeDiscarded
    ) {
        List<AuthoringWorkspaceInfo> result = new ArrayList<>();
        for (StoredWorkspace record : store.list(
                namespace, null, includeDiscarded)) {
            StoredWorkspace current = record.state()
                    == AuthoringWorkspaceState.DISCARDED
                    ? record : refreshSourceState(record, false);
            if (state == null || current.state() == state) {
                result.add(store.toInfo(current));
            }
        }
        return new AuthoringWorkspaceListResponse(result, List.of());
    }

    public AuthoringWorkspaceInfo get(String workspaceId) {
        StoredWorkspace record = store.get(workspaceId);
        if (record.state() != AuthoringWorkspaceState.DISCARDED) {
            record = refreshSourceState(record, false);
        }
        return store.toInfo(record);
    }

    public AuthoringWorkspaceInfo discard(
            String workspaceId,
            String expectedRevision
    ) {
        StoredWorkspace record = store.discard(workspaceId, expectedRevision);
        return store.toInfo(record);
    }

    public AuthoringWorkspaceResourcesResponse listResources(
            String workspaceId,
            String candidateRevision
    ) {
        StoredWorkspace record = store.get(workspaceId);
        refreshSourceState(record, false);
        return new AuthoringWorkspaceResourcesResponse(
                workspaceId, candidateRevision,
                store.listResources(workspaceId, candidateRevision));
    }

    public AuthoringWorkspaceResource readResource(
            String workspaceId,
            String candidateRevision,
            String path
    ) {
        StoredWorkspace record = store.get(workspaceId);
        refreshSourceState(record, false);
        return store.readResource(workspaceId, candidateRevision, path);
    }

    public AuthoringWorkspaceInfo save(
            String workspaceId,
            AuthoringWorkspaceSaveRequest request
    ) {
        String phase = "workspaces.resources.save";
        if (request == null || request.files() == null
                || request.files().isEmpty()) {
            throw invalid(phase,
                    "At least one resource file is required.", null, false);
        }
        requireBatchSize(request.files().size(), phase);
        StoredWorkspace before = store.get(workspaceId);
        refreshSourceState(before, false);
        requireReleaseEditable(before, phase);
        Map<String, byte[]> desired = new TreeMap<>(store.snapshot(
                workspaceId, request.expectedCandidateRevision()));
        Set<String> paths = new HashSet<>();
        Set<String> folded = new HashSet<>();
        for (AuthoringWorkspaceSaveRequest.ResourceFile file : request.files()) {
            if (file == null || file.content() == null) {
                throw invalid(phase,
                        "Each resource requires path and content.", null, false);
            }
            String path = store.canonicalResourcePath(file.path(), phase);
            if (!paths.add(path)
                    || !folded.add(path.toLowerCase(Locale.ROOT))) {
                throw invalidPath(phase,
                        "Resource batch contains duplicate paths.");
            }
            desired.put(path, file.content().getBytes(StandardCharsets.UTF_8));
        }
        requireOverlayAllowed(before.sourceBundle(), before.namespace(),
                desired, phase);
        StoredWorkspace updated = store.replace(
                workspaceId, request.expectedCandidateRevision(), desired,
                () -> requireOverlayAllowed(
                        before.sourceBundle(), before.namespace(), desired, phase));
        updated = refreshSourceState(updated, false);
        return store.toInfo(updated);
    }

    public AuthoringWorkspaceInfo delete(
            String workspaceId,
            AuthoringWorkspaceDeleteRequest request
    ) {
        String phase = "workspaces.resources.delete";
        if (request == null || request.paths() == null
                || request.paths().isEmpty()) {
            throw invalid(phase,
                    "At least one resource path is required.", null, false);
        }
        requireBatchSize(request.paths().size(), phase);
        StoredWorkspace before = store.get(workspaceId);
        refreshSourceState(before, false);
        requireReleaseEditable(before, phase);
        Map<String, byte[]> desired = new TreeMap<>(store.snapshot(
                workspaceId, request.expectedCandidateRevision()));
        Set<String> paths = new HashSet<>();
        Set<String> folded = new HashSet<>();
        for (String value : request.paths()) {
            String path = store.canonicalResourcePath(value, phase);
            if (!paths.add(path)
                    || !folded.add(path.toLowerCase(Locale.ROOT))) {
                throw invalidPath(phase,
                        "Resource batch contains duplicate paths.");
            }
            if (!desired.containsKey(path)) {
                throw RuntimeAuthoringWorkspaceStore.failure(
                        "WORKSPACE_RESOURCE_NOT_FOUND", phase,
                        "Workspace resource was not found.", path, true);
            }
        }
        paths.forEach(desired::remove);
        requireOverlayAllowed(before.sourceBundle(), before.namespace(),
                desired, phase);
        StoredWorkspace updated = store.replace(
                workspaceId, request.expectedCandidateRevision(), desired,
                () -> requireOverlayAllowed(
                        before.sourceBundle(), before.namespace(), desired, phase));
        updated = refreshSourceState(updated, false);
        return store.toInfo(updated);
    }

    public AuthoringWorkspaceDiffResponse diff(
            String workspaceId,
            String candidateRevision,
            boolean includeContent
    ) {
        refreshSourceState(store.get(workspaceId), false);
        return store.diff(workspaceId, candidateRevision, includeContent);
    }

    public AuthoringWorkspaceInfo validate(
            String workspaceId,
            String candidateRevision
    ) {
        String phase = "workspaces.validate";
        StoredWorkspace initial = requireHeadCurrent(
                workspaceId, candidateRevision, phase);
        requireExecutableSource(initial, phase);
        DetachedModelValidationFactory factory =
                validationFactoryProvider.getIfAvailable();
        if (factory == null) {
            throw RuntimeAuthoringWorkspaceStore.failure(
                    "WORKSPACE_STATE_INVALID", phase,
                    "Detached model validation is unavailable.", null, false);
        }

        AuthoringWorkspaceInfo.ValidationEvidence evidence;
        try (RevisionLease lease = store.acquire(
                workspaceId, candidateRevision, phase)) {
            Map<String, byte[]> snapshot = store.snapshot(
                    workspaceId, candidateRevision);
            requireOverlayAllowed(initial.sourceBundle(), initial.namespace(),
                    snapshot, phase);
            evidence = validateSnapshot(factory, lease, snapshot);
            requireHeadCurrent(workspaceId, candidateRevision, phase);
            requireExecutableSource(initial, phase);
            requireOverlayAllowed(initial.sourceBundle(), initial.namespace(),
                    snapshot, phase);
        }

        StoredWorkspace updated = store.recordValidation(
                workspaceId, candidateRevision, evidence);
        StoredWorkspace current = requireHeadCurrent(
                workspaceId, candidateRevision, phase);
        requireExecutableSource(current, phase);
        if (!evidence.valid()) {
            throw RuntimeAuthoringWorkspaceStore.failure(
                    "WORKSPACE_VALIDATION_FAILED", phase,
                    "Workspace model validation failed.", null, true);
        }
        return store.toInfo(requireValidated(
                workspaceId, candidateRevision, phase));
    }

    public AuthoringWorkspaceQueryResponse query(
            String workspaceId,
            String model,
            String candidateRevision,
            SemanticQueryRequest request,
            String authorization,
            Phase queryPhase
    ) {
        String phase = queryPhase == Phase.VALIDATE
                ? "workspaces.query.validate"
                : "workspaces.query.execute";
        if (!StringUtils.hasText(model) || request == null
                || queryPhase == null) {
            throw invalid(phase,
                    "Candidate model, revision, request, and phase are required.",
                    null, false);
        }
        StoredWorkspace initial = requireValidated(
                workspaceId, candidateRevision, phase);
        requireExecutableSource(initial, phase);
        RuntimeCandidateQueryService candidateService =
                candidateQueryProvider.getIfAvailable();
        if (candidateService == null) {
            throw RuntimeAuthoringWorkspaceStore.failure(
                    "WORKSPACE_STATE_INVALID", phase,
                    "Candidate query is unavailable.", null, false);
        }

        try (RevisionLease lease = store.acquire(
                workspaceId, candidateRevision, phase)) {
            Map<String, byte[]> snapshot = store.snapshot(
                    workspaceId, candidateRevision);
            requireOverlayAllowed(initial.sourceBundle(), initial.namespace(),
                    snapshot, phase);
            RuntimeCandidateQueryResult result;
            try {
                result = candidateService.query(new RuntimeCandidateQueryCommand(
                        initial.sourceBundle(), initial.namespace(),
                        lease.path().toString(), initial.baseSourceRevision(),
                        model.trim(), request, authorization, queryPhase));
            } catch (CandidateQueryException failure) {
                if (failure.code() == CandidateQueryErrorCode.CANDIDATE_SOURCE_STALE) {
                    store.markStale(workspaceId,
                            "Workspace Namespace source revision changed.");
                }
                throw failure;
            }
            StoredWorkspace current = requireValidated(
                    workspaceId, candidateRevision, phase);
            requireExecutableSource(current, phase);
            requireOverlayAllowed(initial.sourceBundle(), initial.namespace(),
                    snapshot, phase);
            if (!candidateRevision.equals(result.candidateRevision())
                    || !initial.baseSourceRevision().equals(
                    result.baseSourceRevision())
                    || !initial.sourceBundle().equals(result.sourceBundle())
                    || !initial.namespace().equals(result.namespace())) {
                throw RuntimeAuthoringWorkspaceStore.failure(
                        "WORKSPACE_STORE_CORRUPT", phase,
                        "Candidate query returned an unexpected source identity.",
                        null, false);
            }
            requireValidated(workspaceId, candidateRevision, phase);
            return new AuthoringWorkspaceQueryResponse(
                    workspaceId, initial.sourceBundle(), initial.namespace(),
                    initial.baseBundleRevision(), initial.baseSourceRevision(),
                    candidateRevision, result.catalogIdentity(), result.phase(),
                    result.response(), result.diagnostics());
        }
    }

    private AuthoringWorkspaceInfo.ValidationEvidence validateSnapshot(
            DetachedModelValidationFactory factory,
            RevisionLease lease,
            Map<String, byte[]> snapshot
    ) {
        StoredWorkspace workspace = lease.workspace();
        List<AuthoringWorkspaceInfo.ValidationIssue> issues = new ArrayList<>();
        int valid = 0;
        int cascading = 0;
        List<String> scripts = pathsOfType(snapshot, ".fsscript");
        List<String> tableModels = pathsOfType(snapshot, ".tm");
        List<String> queryModels = pathsOfType(snapshot, ".qm");
        try (DetachedModelValidationSession session = factory.open(
                workspace.sourceBundle(), workspace.namespace(),
                lease.path().toString())) {
            ValidationCounts scriptCounts = validateResources(
                    session, lease.path(), scripts, "FSSCRIPT", false,
                    (resource, ignored) -> session.validateFsscript(resource),
                    issues);
            valid += scriptCounts.valid();
            ValidationCounts tableCounts = validateResources(
                    session, lease.path(), tableModels, "TM",
                    !issues.isEmpty(),
                    (resource, namespace) -> session.validateTableModel(
                            resource, namespace), issues);
            valid += tableCounts.valid();
            cascading += tableCounts.cascading();
            ValidationCounts queryCounts = validateResources(
                    session, lease.path(), queryModels, "QM",
                    !issues.isEmpty(),
                    (resource, ignored) -> session.validateQueryModel(resource),
                    issues);
            valid += queryCounts.valid();
            cascading += queryCounts.cascading();
        } catch (RuntimeException openFailure) {
            if (snapshot.isEmpty()) {
                issues.add(issue(null, "WORKSPACE",
                        "Workspace contains no model resources.", "VALIDATION"));
            } else {
                issues.add(issue(null, "WORKSPACE",
                        "Detached validation session failed.", "VALIDATION"));
            }
        }
        if (snapshot.isEmpty() && issues.isEmpty()) {
            issues.add(issue(null, "WORKSPACE",
                    "Workspace contains no model resources.", "VALIDATION"));
        }
        int invalid = snapshot.size() - valid;
        return new AuthoringWorkspaceInfo.ValidationEvidence(
                issues.isEmpty() && !snapshot.isEmpty(),
                workspace.candidateRevision(), workspace.baseBundleRevision(),
                workspace.baseSourceRevision(), Instant.now().toString(),
                snapshot.size(), valid, Math.max(0, invalid), cascading,
                issues);
    }

    private ValidationCounts validateResources(
            DetachedModelValidationSession session,
            Path root,
            List<String> paths,
            String type,
            boolean priorFailure,
            ResourceValidator validator,
            List<AuthoringWorkspaceInfo.ValidationIssue> issues
    ) {
        int valid = 0;
        int cascading = 0;
        for (String path : paths) {
            BundleResource resource = new BundleResource(
                    session.sourceBundle(),
                    new FileSystemResource(root.resolve(path)));
            try {
                validator.validate(resource,
                        session.sourceBundle().getDefinition().getNamespace());
                valid++;
            } catch (RuntimeException validationFailure) {
                boolean cascade = priorFailure || !issues.isEmpty();
                if (cascade) {
                    cascading++;
                }
                issues.add(issue(path, type,
                        type + " validation failed.",
                        cascade ? "CASCADING" : "VALIDATION"));
            }
        }
        return new ValidationCounts(valid, cascading);
    }

    private static AuthoringWorkspaceInfo.ValidationIssue issue(
            String path,
            String type,
            String message,
            String category
    ) {
        return new AuthoringWorkspaceInfo.ValidationIssue(
                path, type, "WORKSPACE_VALIDATION_FAILED", message, category);
    }

    private StoredWorkspace refreshSourceState(
            StoredWorkspace record,
            boolean failIfStale
    ) {
        if (record.state() == AuthoringWorkspaceState.DISCARDED
                || record.state() == AuthoringWorkspaceState.PUBLISHING
                || record.state() == AuthoringWorkspaceState.RECOVERY_REQUIRED
                || record.state() == AuthoringWorkspaceState.PUBLISHED
                || record.state() == AuthoringWorkspaceState.ROLLING_BACK
                || record.state() == AuthoringWorkspaceState.ROLLBACK_REQUIRED
                || record.state() == AuthoringWorkspaceState.ROLLED_BACK) {
            return record;
        }
        if (record.state() == AuthoringWorkspaceState.STALE) {
            if (failIfStale) {
                throw stale("workspaces.source",
                        "Workspace source is stale.");
            }
            return record;
        }
        if (!sourceIsCurrent(record)) {
            StoredWorkspace stale = store.markStale(
                    record.workspaceId(),
                    "Workspace source Bundle or Namespace revision changed.");
            if (failIfStale) {
                throw stale("workspaces.source",
                        "Workspace source is stale.");
            }
            return stale;
        }
        return record;
    }

    private void requireExecutableSource(StoredWorkspace record, String phase) {
        try {
            StoredWorkspace current = refreshSourceState(
                    store.get(record.workspaceId()), true);
            if (current.state() == AuthoringWorkspaceState.STALE) {
                throw stale(phase, "Workspace source is stale.");
            }
        } catch (RuntimeAuthoringWorkspaceException failure) {
            if ("WORKSPACE_STALE".equals(failure.code())) {
                throw RuntimeAuthoringWorkspaceStore.failure(
                        failure.code(), phase, failure.getMessage(),
                        null, false);
            }
            throw failure;
        }
    }

    private boolean sourceIsCurrent(StoredWorkspace record) {
        try {
            CommittedSourceRevisionRegistry sourceRegistry =
                    sourceRegistryProvider.getIfAvailable();
            if (sourceRegistry == null
                    || !record.baseSourceRevision().equals(
                    sourceRegistry.currentRevision(record.namespace()))) {
                return false;
            }
            WorkspaceSource source = inventory.requireWorkspaceSource(
                    record.sourceBundle(), record.namespace(),
                    "workspaces.source");
            if (!record.baseSourceIdentity().equals(source.sourceIdentity())) {
                return false;
            }
            Map<String, byte[]> snapshot = readSourceSnapshot(
                    source.path(), "workspaces.source");
            return record.baseBundleRevision().equals(
                    CandidateContentRevision.calculate(snapshot));
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private Map<String, byte[]> readSourceSnapshot(Path root, String phase) {
        Map<String, byte[]> resources = new TreeMap<>();
        Set<String> folded = new HashSet<>();
        long totalBytes = 0L;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw RuntimeAuthoringWorkspaceStore.failure(
                            "WORKSPACE_SOURCE_INELIGIBLE", phase,
                            "Source Bundle contains a symbolic link.",
                            null, false);
                }
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                Path relativePath = root.relativize(path);
                if (!CandidateContentRevision.isCandidateResource(relativePath)) {
                    continue;
                }
                String relative = relativePath.toString().replace('\\', '/');
                String canonical = store.canonicalResourcePath(relative, phase);
                if (!folded.add(canonical.toLowerCase(Locale.ROOT))) {
                    throw invalidPath(phase,
                            "Source Bundle contains case-colliding resource paths.");
                }
                long size = Files.size(path);
                if (size > store.limits().maxResourceBytes()) {
                    throw RuntimeAuthoringWorkspaceStore.failure(
                            "WORKSPACE_LIMIT_EXCEEDED", phase,
                            "Source Bundle resource exceeds the configured limit.",
                            canonical, false);
                }
                byte[] content = Files.readAllBytes(path);
                if (content.length > store.limits().maxResourceBytes()) {
                    throw RuntimeAuthoringWorkspaceStore.failure(
                            "WORKSPACE_LIMIT_EXCEEDED", phase,
                            "Source Bundle resource exceeds the configured limit.",
                            canonical, false);
                }
                totalBytes += content.length;
                if (totalBytes > store.limits().maxRevisionBytes()) {
                    throw RuntimeAuthoringWorkspaceStore.failure(
                            "WORKSPACE_LIMIT_EXCEEDED", phase,
                            "Source Bundle snapshot exceeds the configured limit.",
                            null, false);
                }
                resources.put(canonical, content);
                if (resources.size()
                        > store.limits().maxResourcesPerRevision()) {
                    throw RuntimeAuthoringWorkspaceStore.failure(
                            "WORKSPACE_LIMIT_EXCEEDED", phase,
                            "Source Bundle contains too many authoring resources.",
                            null, false);
                }
            }
            // Store validation enforces strict UTF-8, case folding, and quotas.
            return resources;
        } catch (RuntimeAuthoringWorkspaceException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            RuntimeAuthoringWorkspaceException error =
                    RuntimeAuthoringWorkspaceStore.failure(
                            "WORKSPACE_SOURCE_INELIGIBLE", phase,
                            "Source Bundle snapshot could not be read.",
                            null, false);
            error.addSuppressed(failure);
            throw error;
        }
    }

    private void requireOverlayAllowed(
            String selectedBundle,
            String namespace,
            Map<String, byte[]> candidate,
            String phase
    ) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String path : candidate.keySet()) {
            String filename = Path.of(path).getFileName().toString();
            if (names.putIfAbsent(
                    filename.toLowerCase(Locale.ROOT), filename) != null) {
                throw invalidPath(phase,
                        "Candidate contains duplicate resource filenames.");
            }
        }
        List<Bundle> liveBundles = bundlesContext.getBundleList();
        if (liveBundles == null) {
            liveBundles = List.of();
        }
        for (Bundle bundle : liveBundles) {
            if (bundle == null || bundle.getDefinition() == null
                    || selectedBundle.equals(bundle.getName())
                    || !canonicalNamespace(namespace).equals(canonicalNamespace(
                    bundle.getDefinition().getNamespace()))) {
                continue;
            }
            for (String filename : names.values()) {
                try {
                    if (bundle.findResources("**/" + filename).length > 0) {
                        throw RuntimeAuthoringWorkspaceStore.failure(
                                "WORKSPACE_OVERLAY_FORBIDDEN", phase,
                                "Candidate resource would shadow another Bundle.",
                                filename, false);
                    }
                } catch (RuntimeAuthoringWorkspaceException failure) {
                    throw failure;
                } catch (RuntimeException inspectionFailure) {
                    RuntimeAuthoringWorkspaceException failure =
                            RuntimeAuthoringWorkspaceStore.failure(
                                    "WORKSPACE_OVERLAY_FORBIDDEN", phase,
                                    "Candidate resource ownership could not be verified.",
                                    filename, false);
                    failure.addSuppressed(inspectionFailure);
                    throw failure;
                }
            }
        }
    }

    private StoredWorkspace requireValidated(
            String workspaceId,
            String candidateRevision,
            String phase
    ) {
        StoredWorkspace record = requireHeadCurrent(
                workspaceId, candidateRevision, phase);
        AuthoringWorkspaceInfo.ValidationEvidence evidence =
                record.lastValidation();
        if (record.state() != AuthoringWorkspaceState.VALIDATED
                || evidence == null || !evidence.valid()
                || !candidateRevision.equals(evidence.candidateRevision())
                || !record.baseBundleRevision().equals(
                evidence.baseBundleRevision())
                || !record.baseSourceRevision().equals(
                evidence.baseNamespaceSourceRevision())) {
            throw RuntimeAuthoringWorkspaceStore.failure(
                    "WORKSPACE_NOT_VALIDATED", phase,
                    "Exact workspace revision has not passed full validation.",
                    null, true);
        }
        return record;
    }

    PublicationCandidate preflightPublication(
            String workspaceId,
            String expectedCandidateRevision,
            String expectedBaseBundleRevision,
            String expectedBaseSourceRevision
    ) {
        String phase = "workspaces.publish.preflight";
        if (!StringUtils.hasText(expectedCandidateRevision)
                || !StringUtils.hasText(expectedBaseBundleRevision)
                || !StringUtils.hasText(expectedBaseSourceRevision)) {
            throw invalid(phase,
                    "Candidate and base revision identities are required.",
                    null, false);
        }
        StoredWorkspace record = requireValidated(
                workspaceId, expectedCandidateRevision.trim(), phase);
        if (!record.baseBundleRevision().equals(expectedBaseBundleRevision.trim())
                || !record.baseSourceRevision().equals(
                expectedBaseSourceRevision.trim())) {
            throw RuntimeAuthoringWorkspaceStore.failure(
                    "WORKSPACE_REVISION_CONFLICT", phase,
                    "Workspace base revision identity no longer matches the request.",
                    null, true);
        }
        WorkspaceSource source = inventory.requireWorkspaceSource(
                record.sourceBundle(), record.namespace(), phase);
        Map<String, byte[]> snapshot = store.snapshot(
                workspaceId, expectedCandidateRevision.trim());
        requireOverlayAllowed(record.sourceBundle(), record.namespace(),
                snapshot, phase);
        return new PublicationCandidate(record, source,
                Map.copyOf(snapshot));
    }

    public ReleaseCandidate releaseCandidate(
            String workspaceId,
            String expectedCandidateRevision
    ) {
        String phase = "workspaces.release.export";
        StoredWorkspace record = requireHeadCurrent(
                workspaceId, expectedCandidateRevision, phase);
        AuthoringWorkspaceInfo.ValidationEvidence evidence =
                record.lastValidation();
        if ((record.state() != AuthoringWorkspaceState.VALIDATED
                && record.state() != AuthoringWorkspaceState.PUBLISHED)
                || evidence == null || !evidence.valid()
                || !record.candidateRevision().equals(evidence.candidateRevision())
                || !record.baseBundleRevision().equals(evidence.baseBundleRevision())
                || !record.baseSourceRevision().equals(
                evidence.baseNamespaceSourceRevision())) {
            throw RuntimeAuthoringWorkspaceStore.failure(
                    "WORKSPACE_NOT_VALIDATED", phase,
                    "Exact workspace revision has not passed full validation.",
                    null, false);
        }
        Map<String, byte[]> snapshot = store.snapshot(
                workspaceId, expectedCandidateRevision);
        return new ReleaseCandidate(record, Map.copyOf(snapshot));
    }

    public StoredWorkspace requireImportedValidated(
            String workspaceId,
            String candidateRevision,
            String packageId,
            String phase
    ) {
        StoredWorkspace record = requireValidated(
                workspaceId, candidateRevision, phase);
        ReleaseImportEvidence release = record.releaseImport();
        if (release == null || !StringUtils.hasText(packageId)
                || !packageId.trim().equals(release.packageId())
                || !record.candidateRevision().equals(
                release.exportedCandidateRevision())) {
            throw RuntimeAuthoringWorkspaceStore.failure(
                    "WORKSPACE_RELEASE_PACKAGE_CONFLICT", phase,
                    "Release package identity does not match the workspace.",
                    null, false);
        }
        return record;
    }

    void assertPublicationBaseCurrent(StoredWorkspace record) {
        requireExecutableSource(record, "workspaces.publish.preflight");
    }

    void assertPublicationSourceCurrent(
            StoredWorkspace record,
            Map<String, byte[]> snapshot
    ) {
        String phase = "workspaces.publish.preflight";
        StoredWorkspace current = store.get(record.workspaceId());
        if (current.state() != AuthoringWorkspaceState.PUBLISHING
                || !current.candidateRevision().equals(
                record.candidateRevision())) {
            throw RuntimeAuthoringWorkspaceStore.failure(
                    "WORKSPACE_PUBLISH_CONFLICT", phase,
                    "Workspace publication is no longer current.", null, false);
        }
        if (!sourceIsCurrent(record)) {
            throw stale(phase,
                    "Workspace source changed before publication commit.");
        }
        requireOverlayAllowed(record.sourceBundle(), record.namespace(),
                snapshot, phase);
    }

    private StoredWorkspace requireHeadCurrent(
            String workspaceId,
            String candidateRevision,
            String phase
    ) {
        StoredWorkspace current = store.get(workspaceId);
        if (!StringUtils.hasText(candidateRevision)
                || !current.candidateRevision().equals(
                candidateRevision.trim())) {
            throw RuntimeAuthoringWorkspaceStore.failure(
                    "WORKSPACE_REVISION_CONFLICT", phase,
                    "Workspace candidate revision is no longer current.",
                    null, true);
        }
        if (current.state() == AuthoringWorkspaceState.DISCARDED) {
            throw RuntimeAuthoringWorkspaceStore.failure(
                    "WORKSPACE_STATE_INVALID", phase,
                    "Discarded workspace cannot perform this action.",
                    null, false);
        }
        return current;
    }

    private CommittedSourceRevisionRegistry sourceRegistry(String phase) {
        CommittedSourceRevisionRegistry registry =
                sourceRegistryProvider.getIfAvailable();
        if (registry == null) {
            throw RuntimeAuthoringWorkspaceStore.failure(
                    "WORKSPACE_SOURCE_INELIGIBLE", phase,
                    "Committed source revision tracking is unavailable.",
                    null, false);
        }
        return registry;
    }

    private void requireBatchSize(int count, String phase) {
        if (count > store.limits().maxBatchOperations()) {
            throw RuntimeAuthoringWorkspaceStore.failure(
                    "WORKSPACE_LIMIT_EXCEEDED", phase,
                    "Resource batch exceeds the configured operation limit.",
                    null, false);
        }
    }

    private static void requireReleaseEditable(
            StoredWorkspace record,
            String phase
    ) {
        if (record.releaseImport() != null) {
            throw RuntimeAuthoringWorkspaceStore.failure(
                    "WORKSPACE_RELEASE_IMMUTABLE", phase,
                    "Imported release package candidate is immutable.",
                    null, false);
        }
    }

    private static List<String> pathsOfType(
            Map<String, byte[]> snapshot,
            String suffix
    ) {
        return snapshot.keySet().stream()
                .filter(path -> path.endsWith(suffix))
                .sorted()
                .toList();
    }

    private static String resolveNamespace(
            String header,
            String body,
            String phase
    ) {
        String headerValue = canonicalNamespace(header);
        String bodyValue = canonicalNamespace(body);
        if (StringUtils.hasText(headerValue)
                && StringUtils.hasText(bodyValue)
                && !headerValue.equals(bodyValue)) {
            throw invalid(phase,
                    "X-NS and request Namespace do not match.", null, false);
        }
        String resolved = StringUtils.hasText(headerValue)
                ? headerValue : bodyValue;
        if (!StringUtils.hasText(resolved)) {
            throw invalid(phase,
                    "An explicit non-empty Namespace is required.", null, false);
        }
        return resolved;
    }

    private static String canonicalNamespace(String value) {
        return value == null ? "" : value.trim();
    }

    private static RuntimeAuthoringWorkspaceException invalid(
            String phase,
            String message,
            String path,
            boolean repairable
    ) {
        return RuntimeAuthoringWorkspaceStore.failure(
                "WORKSPACE_INVALID_REQUEST", phase, message, path, repairable);
    }

    private static RuntimeAuthoringWorkspaceException invalidPath(
            String phase,
            String message
    ) {
        return RuntimeAuthoringWorkspaceStore.failure(
                "WORKSPACE_RESOURCE_PATH_INVALID", phase,
                message, null, false);
    }

    private static RuntimeAuthoringWorkspaceException stale(
            String phase,
            String message
    ) {
        return RuntimeAuthoringWorkspaceStore.failure(
                "WORKSPACE_STALE", phase, message, null, false);
    }

    @FunctionalInterface
    private interface ResourceValidator {
        void validate(BundleResource resource, String namespace);
    }

    private record ValidationCounts(int valid, int cascading) {
    }

    record PublicationCandidate(
            StoredWorkspace workspace,
            WorkspaceSource source,
            Map<String, byte[]> snapshot
    ) {
    }

    public record ReleaseCandidate(
            StoredWorkspace workspace,
            Map<String, byte[]> snapshot
    ) {
    }
}
