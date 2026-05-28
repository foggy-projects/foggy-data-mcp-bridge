package com.foggyframework.dataset.mcp.experience;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Service
public class ExperienceRecipeRegistryService {
    private final ExperienceRecipeRegistryStore store;
    private final PlatformTransactionManager transactionManager;
    private final List<ExperienceRecipeArtifactResolver> artifactResolvers;
    private final ExperienceRecipeArtifactSignatureVerifier artifactSignatureVerifier;
    private final ExperienceRecipeRegistryProperties properties;
    private final ExperienceRecipeArtifactUriPolicy artifactUriPolicy;
    private final ExperienceRecipeArtifactObjectMetadataPolicy artifactObjectMetadataPolicy;

    public ExperienceRecipeRegistryService(
            ExperienceRecipeRegistryStore store,
            @Autowired(required = false) PlatformTransactionManager transactionManager) {
        this(store, transactionManager, List.of(), null, new ExperienceRecipeRegistryProperties());
    }

    public ExperienceRecipeRegistryService(
            ExperienceRecipeRegistryStore store,
            PlatformTransactionManager transactionManager,
            ExperienceRecipeArtifactResolver artifactResolver,
            ExperienceRecipeRegistryProperties properties) {
        this(store, transactionManager, artifactResolver, null, properties);
    }

    public ExperienceRecipeRegistryService(
            ExperienceRecipeRegistryStore store,
            PlatformTransactionManager transactionManager,
            ExperienceRecipeArtifactResolver artifactResolver,
            ExperienceRecipeArtifactSignatureVerifier artifactSignatureVerifier,
            ExperienceRecipeRegistryProperties properties) {
        this(
                store,
                transactionManager,
                artifactResolver == null ? List.of() : List.of(artifactResolver),
                artifactSignatureVerifier,
                properties);
    }

    @Autowired
    public ExperienceRecipeRegistryService(
            ExperienceRecipeRegistryStore store,
            @Autowired(required = false) PlatformTransactionManager transactionManager,
            @Autowired(required = false) List<ExperienceRecipeArtifactResolver> artifactResolvers,
            @Autowired(required = false) ExperienceRecipeArtifactSignatureVerifier artifactSignatureVerifier,
            ExperienceRecipeRegistryProperties properties) {
        this.store = store;
        this.transactionManager = transactionManager;
        this.artifactResolvers = artifactResolvers == null
                ? List.of()
                : artifactResolvers.stream()
                        .filter(Objects::nonNull)
                        .toList();
        this.artifactSignatureVerifier = artifactSignatureVerifier;
        this.properties = properties == null ? new ExperienceRecipeRegistryProperties() : properties;
        this.artifactUriPolicy = new ExperienceRecipeArtifactUriPolicy(this.properties);
        this.artifactObjectMetadataPolicy = new ExperienceRecipeArtifactObjectMetadataPolicy(this.properties);
    }

    public ExperienceRecipeRegistryResponse mutate(ExperienceRecipeRegistryMutationRequest request) {
        validateMutationRequest(request);
        Optional<ExperienceRecipeRegistryEvent> replay =
                store.findEventByIdempotencyKey(request.getIdempotencyKey());
        if (replay.isPresent()) {
            return fromReplayEvent(replay.get());
        }
        try {
            return runInTransaction(status -> mutateNewRequest(request, status));
        } catch (DuplicateKeyException ex) {
            Optional<ExperienceRecipeRegistryEvent> conflict =
                    store.findEventByIdempotencyKey(request.getIdempotencyKey());
            if (conflict.isPresent()) {
                return fromReplayEvent(conflict.get());
            }
            throw ex;
        }
    }

    public ExperienceRecipeRegistryResponse searchDiscoverable(ExperienceRecipeSearchRequest request) {
        SearchGovernanceResult governance = applySearchGovernance(request, store.findDiscoverable());
        List<ExperienceRecipeRegistryEntry> rows = governance.dedupedRows().stream()
                .limit(resolveLimit(request))
                .toList();
        return ExperienceRecipeRegistryResponse.readOk(
                rows,
                governance.candidateCanonicalGroups(),
                governance.filteredCounts());
    }

    private ExperienceRecipeRegistryResponse mutateNewRequest(
            ExperienceRecipeRegistryMutationRequest request,
            TransactionStatus transactionStatus) {
        Optional<ExperienceRecipeRegistryEntry> currentOpt = store.findByRegistryKey(request.getRegistryKey());
        ExperienceRecipeRegistryEntry current = currentOpt.map(ExperienceRecipeRegistryEntry::copy).orElse(null);
        assertExpectedState(request, current);

        return switch (request.getOperation()) {
            case CREATE_DRAFT_STUB -> createDraft(request, current);
            case PROMOTE_DRAFT_TO_CANDIDATE -> transition(
                    request, current, ExperienceRecipeStatus.DRAFT, ExperienceRecipeStatus.CANDIDATE, false,
                    ExperienceRecipeApiResult.UPDATED, transactionStatus);
            case PUBLISH_VALIDATED -> publishValidated(request, current, transactionStatus);
            case DEPRECATE_RECIPE -> transition(
                    request, current, ExperienceRecipeStatus.VALIDATED, ExperienceRecipeStatus.DEPRECATED, false,
                    ExperienceRecipeApiResult.UPDATED, transactionStatus);
            case REJECT_CANDIDATE -> transition(
                    request, current, ExperienceRecipeStatus.CANDIDATE, ExperienceRecipeStatus.REJECTED, false,
                    ExperienceRecipeApiResult.UPDATED, transactionStatus);
            case ROLLBACK_VALIDATED_TO_CANDIDATE -> transition(
                    request, current, ExperienceRecipeStatus.VALIDATED, ExperienceRecipeStatus.CANDIDATE, false,
                    ExperienceRecipeApiResult.UPDATED, transactionStatus);
            case SEARCH_DISCOVERABLE_RECIPES ->
                    throw new IllegalArgumentException("search_discoverable_recipes is a read-only operation");
        };
    }

    private ExperienceRecipeRegistryResponse createDraft(
            ExperienceRecipeRegistryMutationRequest request,
            ExperienceRecipeRegistryEntry current) {
        if (current != null) {
            throw new IllegalStateException("Experience recipe already exists: " + request.getRegistryKey());
        }
        ExperienceRecipeRegistryEntry draft = entryFromRequest(request);
        draft.setStatus(ExperienceRecipeStatus.DRAFT);
        draft.setActiveForDiscovery(false);
        draft.setCreatedAt(Instant.now());
        draft.setUpdatedAt(draft.getCreatedAt());
        saveWithVersionGuard(draft, null);
        store.appendEvent(eventFor(
                request,
                null,
                draft,
                ExperienceRecipeApiResult.CREATED,
                ExperienceRecipeFailureStage.NONE,
                request.getReason()));
        return ExperienceRecipeRegistryResponse.fromEntry(
                ExperienceRecipeApiResult.CREATED,
                draft,
                ExperienceRecipeFailureStage.NONE,
                request.getReason());
    }

    private ExperienceRecipeRegistryResponse publishValidated(
            ExperienceRecipeRegistryMutationRequest request,
            ExperienceRecipeRegistryEntry current,
            TransactionStatus transactionStatus) {
        requireCurrentStatus(current, ExperienceRecipeStatus.CANDIDATE, request.getRegistryKey());
        PublishGateDecision publishGate = evaluatePublishGate(request, current);
        if (!publishGate.allowed()) {
            String reason = firstNonBlank(request.getReason(), publishGate.reason());
            store.appendEvent(eventFor(
                    request,
                    current,
                    current,
                    ExperienceRecipeApiResult.BLOCKED,
                    ExperienceRecipeFailureStage.GATE_VALIDATION,
                    reason));
            return ExperienceRecipeRegistryResponse.fromEntry(
                    ExperienceRecipeApiResult.BLOCKED,
                    current,
                    ExperienceRecipeFailureStage.GATE_VALIDATION,
                    reason,
                    request.getGovernanceEvidence().getEvidenceArtifacts());
        }
        return transition(
                request,
                current,
                ExperienceRecipeStatus.CANDIDATE,
                ExperienceRecipeStatus.VALIDATED,
                true,
                ExperienceRecipeApiResult.UPDATED,
                transactionStatus);
    }

    private PublishGateDecision evaluatePublishGate(
            ExperienceRecipeRegistryMutationRequest request,
            ExperienceRecipeRegistryEntry current) {
        if (!"registry_admin".equals(normalizeRole(request.getActorRole()))
                || !request.getGovernanceEvidence().publishGatePassed()) {
            return PublishGateDecision.blocked("publish_validated blocked by registry governance gate");
        }
        ExperienceRecipeArtifactVerificationResult artifactVerification = verifyArtifactContents(
                request.getGovernanceEvidence().getEvidenceArtifacts(),
                signatureContextFor(request, current));
        if (!artifactVerification.verified()) {
            return PublishGateDecision.blocked(artifactVerification.reason());
        }
        return PublishGateDecision.passed();
    }

    private ExperienceRecipeArtifactVerificationResult verifyArtifactContents(
            List<ExperienceRecipeEvidenceArtifact> artifacts,
            ExperienceRecipeArtifactSignatureContext signatureContext) {
        boolean requireResolution = properties.isRequireArtifactResolution();
        boolean requireSignature = properties.isRequireArtifactSignatureVerification();
        boolean requireResolvedObjectMetadata = artifactObjectMetadataPolicy.requireResolvedObjectMetadata();
        boolean requiresResolver = requireResolution || requireSignature || requireResolvedObjectMetadata;
        if (!requireResolution
                && !requireSignature
                && !artifactUriPolicy.enabled()
                && !artifactObjectMetadataPolicy.enabled()) {
            return ExperienceRecipeArtifactVerificationResult.passed();
        }
        ExperienceRecipeArtifactVerificationResult uriPolicyResult =
                artifactUriPolicy.validate(artifacts, signatureContext);
        if (!uriPolicyResult.verified()) {
            return uriPolicyResult;
        }
        if (!requireResolvedObjectMetadata) {
            ExperienceRecipeArtifactVerificationResult metadataPolicyResult =
                    artifactObjectMetadataPolicy.validate(artifacts, signatureContext);
            if (!metadataPolicyResult.verified()) {
                return metadataPolicyResult;
            }
        }
        if (!requiresResolver) {
            return ExperienceRecipeArtifactVerificationResult.passed();
        }
        if (artifactResolvers.isEmpty()) {
            return ExperienceRecipeArtifactVerificationResult.failed(
                    "publish_validated blocked because evidence artifact resolver is not configured");
        }
        if (requireSignature && artifactSignatureVerifier == null) {
            return ExperienceRecipeArtifactVerificationResult.failed(
                    "publish_validated blocked because evidence artifact signature verifier is not configured");
        }
        for (ExperienceRecipeEvidenceArtifact artifact : artifacts) {
            if (artifact == null || !artifact.validForPublishGate()) {
                continue;
            }
            Optional<ExperienceRecipeArtifactResolution> resolution = resolveArtifact(artifact);
            if (resolution.isEmpty()) {
                return ExperienceRecipeArtifactVerificationResult.failed(
                        "publish_validated blocked because evidence artifact cannot be resolved: "
                                + artifact.getArtifactUri());
            }
            ExperienceRecipeArtifactResolution resolvedArtifact = resolution.get();
            if (requireResolvedObjectMetadata) {
                ExperienceRecipeArtifactVerificationResult metadataPolicyResult =
                        artifactObjectMetadataPolicy.validate(
                                List.of(resolvedArtifact.toTrustedObjectArtifact(artifact)),
                                signatureContext);
                if (!metadataPolicyResult.verified()) {
                    return metadataPolicyResult;
                }
            }
            byte[] artifactContent = resolvedArtifact.content();
            if (requireResolution) {
                String actualHash = ExperienceRecipeArtifactHash.sha256(artifactContent);
                if (!actualHash.equalsIgnoreCase(artifact.getArtifactHash().trim())) {
                    return ExperienceRecipeArtifactVerificationResult.failed(
                            "publish_validated blocked because evidence artifact hash mismatched: "
                                    + artifact.getArtifactUri());
                }
            }
            if (requireSignature) {
                ExperienceRecipeArtifactVerificationResult signatureVerification =
                        artifactSignatureVerifier.verify(artifact, artifactContent, signatureContext);
                if (signatureVerification == null || !signatureVerification.verified()) {
                    String reason = signatureVerification == null
                            ? "signature verifier returned no result"
                            : signatureVerification.reason();
                    return ExperienceRecipeArtifactVerificationResult.failed(
                            "publish_validated blocked because evidence artifact signature verification failed: "
                                    + firstNonBlank(reason, artifact.getArtifactUri()));
                }
            }
        }
        return ExperienceRecipeArtifactVerificationResult.passed();
    }

    private Optional<ExperienceRecipeArtifactResolution> resolveArtifact(ExperienceRecipeEvidenceArtifact artifact) {
        for (ExperienceRecipeArtifactResolver resolver : artifactResolvers) {
            Optional<ExperienceRecipeArtifactResolution> resolution = resolver.resolveArtifact(artifact);
            if (resolution.isPresent()) {
                return resolution;
            }
        }
        return Optional.empty();
    }

    private static ExperienceRecipeArtifactSignatureContext signatureContextFor(
            ExperienceRecipeRegistryMutationRequest request,
            ExperienceRecipeRegistryEntry current) {
        ExperienceRecipeRegistryEntry effective = current == null
                ? entryFromRequest(request)
                : current.copy();
        applyMutableRequestFields(effective, request);
        return new ExperienceRecipeArtifactSignatureContext(
                effective.getNamespaceScope(),
                effective.getTenantScope(),
                effective.getRegistryKey(),
                effective.getCanonicalRecipeId(),
                effective.getRecipeVersion(),
                effective.getOwnerRole());
    }

    private ExperienceRecipeRegistryResponse transition(
            ExperienceRecipeRegistryMutationRequest request,
            ExperienceRecipeRegistryEntry current,
            ExperienceRecipeStatus requiredStatus,
            ExperienceRecipeStatus targetStatus,
            boolean targetActiveForDiscovery,
            ExperienceRecipeApiResult apiResult,
            TransactionStatus transactionStatus) {
        requireCurrentStatus(current, requiredStatus, request.getRegistryKey());
        ExperienceRecipeRegistryEntry target = current.copy();
        applyMutableRequestFields(target, request);
        target.setStatus(targetStatus);
        target.setActiveForDiscovery(targetActiveForDiscovery);

        if (request.getSimulateFailureStage() == ExperienceRecipeFailureStage.REGISTRY_EVENT_APPEND) {
            if (transactionStatus != null) {
                saveWithVersionGuard(target, current);
                transactionStatus.setRollbackOnly();
            }
            return ExperienceRecipeRegistryResponse.fromEntry(
                    ExperienceRecipeApiResult.ROLLED_BACK,
                    current,
                    ExperienceRecipeFailureStage.REGISTRY_EVENT_APPEND,
                    "registry/event transaction rolled back",
                    request.getGovernanceEvidence().getEvidenceArtifacts());
        }

        saveWithVersionGuard(target, current);
        store.appendEvent(eventFor(
                request,
                current,
                target,
                apiResult,
                ExperienceRecipeFailureStage.NONE,
                request.getReason()));
        return ExperienceRecipeRegistryResponse.fromEntry(
                apiResult,
                target,
                ExperienceRecipeFailureStage.NONE,
                request.getReason(),
                request.getGovernanceEvidence().getEvidenceArtifacts());
    }

    private void saveWithVersionGuard(
            ExperienceRecipeRegistryEntry target,
            ExperienceRecipeRegistryEntry current) {
        Long expectedRecordVersion = current == null ? null : current.getRecordVersion();
        if (!store.saveWithVersionCheck(target, expectedRecordVersion)) {
            throw new IllegalStateException(
                    "Experience recipe record version changed: " + target.getRegistryKey());
        }
    }

    private ExperienceRecipeRegistryEvent eventFor(
            ExperienceRecipeRegistryMutationRequest request,
            ExperienceRecipeRegistryEntry from,
            ExperienceRecipeRegistryEntry responseEntry,
            ExperienceRecipeApiResult apiResult,
            ExperienceRecipeFailureStage failureStage,
            String reason) {
        ExperienceRecipeRegistryEvent event = new ExperienceRecipeRegistryEvent();
        event.setRegistryKey(request.getRegistryKey());
        event.setIdempotencyKey(request.getIdempotencyKey());
        event.setOperation(request.getOperation());
        event.setActorRole(request.getActorRole());
        event.setApiResult(apiResult);
        event.setFailureStage(failureStage);
        event.setFromStatus(from == null ? ExperienceRecipeStatus.NONE : from.getStatus());
        event.setFromActiveForDiscovery(from != null && from.isActiveForDiscovery());
        event.setToStatus(responseEntry == null ? ExperienceRecipeStatus.NONE : responseEntry.getStatus());
        event.setToActiveForDiscovery(responseEntry != null && responseEntry.isActiveForDiscovery());
        event.setResponseStatus(responseEntry == null ? ExperienceRecipeStatus.NONE : responseEntry.getStatus());
        event.setResponseActiveForDiscovery(responseEntry != null && responseEntry.isActiveForDiscovery());
        event.setResponseDiscoverable(responseEntry != null && responseEntry.discoverable());
        event.setEvidenceArtifacts(request.getGovernanceEvidence().getEvidenceArtifacts());
        event.setReason(reason);
        return event;
    }

    private ExperienceRecipeRegistryResponse fromReplayEvent(ExperienceRecipeRegistryEvent event) {
        ExperienceRecipeRegistryEntry replayEntry = new ExperienceRecipeRegistryEntry();
        replayEntry.setRegistryKey(event.getRegistryKey());
        replayEntry.setStatus(event.getResponseStatus());
        replayEntry.setActiveForDiscovery(event.isResponseActiveForDiscovery());
        return ExperienceRecipeRegistryResponse.fromEntry(
                ExperienceRecipeApiResult.IDEMPOTENT_REPLAY,
                replayEntry,
                ExperienceRecipeFailureStage.IDEMPOTENCY_REPLAY,
                "idempotent replay returns prior response",
                event.getEvidenceArtifacts());
    }

    private <T> T runInTransaction(Function<TransactionStatus, T> action) {
        if (transactionManager == null) {
            synchronized (this) {
                return action.apply(null);
            }
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(action::apply);
    }

    private static ExperienceRecipeRegistryEntry entryFromRequest(ExperienceRecipeRegistryMutationRequest request) {
        ExperienceRecipeRegistryEntry entry = new ExperienceRecipeRegistryEntry();
        entry.setRegistryKey(request.getRegistryKey());
        applyMutableRequestFields(entry, request);
        return entry;
    }

    private static void applyMutableRequestFields(
            ExperienceRecipeRegistryEntry entry,
            ExperienceRecipeRegistryMutationRequest request) {
        if (hasText(request.getRecipeId())) {
            entry.setRecipeId(request.getRecipeId());
        }
        if (hasText(request.getRecipeVersion())) {
            entry.setRecipeVersion(request.getRecipeVersion());
        }
        if (hasText(request.getCanonicalRecipeId())) {
            entry.setCanonicalRecipeId(request.getCanonicalRecipeId());
        }
        if (hasText(request.getTitle())) {
            entry.setTitle(request.getTitle());
        }
        if (hasText(request.getBusinessType())) {
            entry.setBusinessType(request.getBusinessType());
        }
        if (hasText(request.getRoute())) {
            entry.setRoute(request.getRoute());
        }
        if (hasText(request.getNamespaceScope())) {
            entry.setNamespaceScope(request.getNamespaceScope());
        }
        if (hasText(request.getTenantScope())) {
            entry.setTenantScope(request.getTenantScope());
        }
        if (hasText(request.getPermissionTags())) {
            entry.setPermissionTags(request.getPermissionTags());
        }
        if (hasText(request.getOwnerRole())) {
            entry.setOwnerRole(request.getOwnerRole());
        } else if (!hasText(entry.getOwnerRole()) && hasText(request.getActorRole())) {
            entry.setOwnerRole(request.getActorRole());
        }
    }

    private static void validateMutationRequest(ExperienceRecipeRegistryMutationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Experience recipe registry request cannot be null");
        }
        if (request.getOperation() == null) {
            throw new IllegalArgumentException("Experience recipe registry operation cannot be null");
        }
        if (request.getOperation() == ExperienceRecipeRegistryOperation.SEARCH_DISCOVERABLE_RECIPES) {
            throw new IllegalArgumentException("search_discoverable_recipes must use searchDiscoverable()");
        }
        requireText(request.getRegistryKey(), "registryKey");
        requireText(request.getIdempotencyKey(), "idempotencyKey");
        requireText(request.getActorRole(), "actorRole");
        if (request.getExpectedRecordVersion() != null && request.getExpectedRecordVersion() < 0) {
            throw new IllegalArgumentException("expectedRecordVersion cannot be negative");
        }
    }

    private static void assertExpectedState(
            ExperienceRecipeRegistryMutationRequest request,
            ExperienceRecipeRegistryEntry current) {
        ExperienceRecipeStatus actualStatus = current == null ? ExperienceRecipeStatus.NONE : current.getStatus();
        if (request.getExpectedFromStatus() != null && request.getExpectedFromStatus() != actualStatus) {
            throw new IllegalStateException("Expected registry status "
                    + request.getExpectedFromStatus().wireValue() + " but was " + actualStatus.wireValue());
        }
        if (request.getExpectedFromActiveForDiscovery() != null) {
            boolean actualActive = current != null && current.isActiveForDiscovery();
            if (request.getExpectedFromActiveForDiscovery() != actualActive) {
                throw new IllegalStateException("Expected activeForDiscovery "
                        + request.getExpectedFromActiveForDiscovery() + " but was " + actualActive);
            }
        }
        if (request.getExpectedRecordVersion() != null) {
            long actualRecordVersion = current == null || current.getRecordVersion() == null
                    ? 0L
                    : current.getRecordVersion();
            if (request.getExpectedRecordVersion() != actualRecordVersion) {
                throw new IllegalStateException("Expected recordVersion "
                        + request.getExpectedRecordVersion() + " but was " + actualRecordVersion);
            }
        }
    }

    private static void requireCurrentStatus(
            ExperienceRecipeRegistryEntry current,
            ExperienceRecipeStatus requiredStatus,
            String registryKey) {
        if (current == null) {
            throw new IllegalStateException("Experience recipe does not exist: " + registryKey);
        }
        if (current.getStatus() != requiredStatus) {
            throw new IllegalStateException("Experience recipe " + registryKey + " must be "
                    + requiredStatus.wireValue() + " but was " + current.getStatus().wireValue());
        }
    }

    private static SearchGovernanceResult applySearchGovernance(
            ExperienceRecipeSearchRequest request,
            List<ExperienceRecipeRegistryEntry> discoverableRows) {
        Map<String, Integer> filteredCounts = new LinkedHashMap<>();
        List<ExperienceRecipeRegistryEntry> filtered = new ArrayList<>();
        for (ExperienceRecipeRegistryEntry entry : discoverableRows) {
            String mismatch = mismatchReason(request, entry);
            if (mismatch == null) {
                filtered.add(entry);
            } else {
                filteredCounts.merge(mismatch, 1, Integer::sum);
            }
        }
        Map<String, ExperienceRecipeRegistryEntry> byCanonical = new LinkedHashMap<>();
        filtered.stream()
                .sorted(representativeComparator())
                .forEach(entry -> byCanonical.putIfAbsent(canonicalGroup(entry), entry));
        List<ExperienceRecipeRegistryEntry> dedupedRows = new ArrayList<>(byCanonical.values());
        dedupedRows.sort(Comparator.comparing(ExperienceRecipeRegistryEntry::getRegistryKey));
        return new SearchGovernanceResult(
                dedupedRows,
                List.copyOf(byCanonical.keySet()),
                filteredCounts);
    }

    private static String mismatchReason(ExperienceRecipeSearchRequest request, ExperienceRecipeRegistryEntry entry) {
        if (request == null) {
            return null;
        }
        if (hasText(request.getRegistryKey()) && !request.getRegistryKey().equals(entry.getRegistryKey())) {
            return "registry_key_mismatch";
        }
        if (hasText(request.getBusinessType()) && !request.getBusinessType().equals(entry.getBusinessType())) {
            return "query_filter";
        }
        if (hasText(request.getRoute()) && !request.getRoute().equals(entry.getRoute())) {
            return "query_filter";
        }
        if (!scopeAllows(entry.getNamespaceScope(), request.getNamespace())) {
            return "namespace_mismatch";
        }
        if (!scopeAllows(entry.getTenantScope(), request.getTenantId())) {
            return "tenant_mismatch";
        }
        if (!permissionAllows(entry.getPermissionTags(), request.getPermissionTags())) {
            return "permission_mismatch";
        }
        if (!ownerAllows(entry.getOwnerRole(), request.getOwnerRoles())) {
            return "owner_mismatch";
        }
        return null;
    }

    private static Comparator<ExperienceRecipeRegistryEntry> representativeComparator() {
        return Comparator
                .comparing((ExperienceRecipeRegistryEntry entry) -> nullSafeLong(entry.getRecordVersion()))
                .reversed()
                .thenComparing(ExperienceRecipeRegistryEntry::getRecipeVersion, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ExperienceRecipeRegistryEntry::getRegistryKey);
    }

    private static String canonicalGroup(ExperienceRecipeRegistryEntry entry) {
        return hasText(entry.getCanonicalRecipeId()) ? entry.getCanonicalRecipeId() : entry.getRegistryKey();
    }

    private static boolean scopeAllows(String rawScope, String requestedScope) {
        Set<String> scopes = normalizedSet(rawScope);
        if (scopes.isEmpty() || scopes.contains("*")) {
            return true;
        }
        return hasText(requestedScope) && scopes.contains(normalizeValue(requestedScope));
    }

    private static boolean permissionAllows(String requiredPermissionTags, Set<String> requesterPermissionTags) {
        Set<String> required = normalizedSet(requiredPermissionTags);
        if (required.isEmpty()) {
            return true;
        }
        Set<String> requester = normalizeSet(requesterPermissionTags);
        return requester.containsAll(required);
    }

    private static boolean ownerAllows(String ownerRole, Set<String> allowedOwnerRoles) {
        Set<String> allowed = normalizeSet(allowedOwnerRoles);
        if (allowed.isEmpty() || !hasText(ownerRole)) {
            return true;
        }
        return allowed.contains(normalizeValue(ownerRole));
    }

    private static Set<String> normalizedSet(String raw) {
        if (!hasText(raw)) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(ExperienceRecipeRegistryService::normalizeValue)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<String> normalizeSet(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        return raw.stream()
                .map(ExperienceRecipeRegistryService::normalizeValue)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static long nullSafeLong(Long value) {
        return value == null ? 0L : value;
    }

    private static String normalizeValue(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static long resolveLimit(ExperienceRecipeSearchRequest request) {
        if (request == null || request.getLimit() == null || request.getLimit() <= 0) {
            return 20;
        }
        return Math.min(request.getLimit(), 100);
    }

    private static void requireText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalizeRole(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String first, String fallback) {
        return hasText(first) ? first : fallback;
    }

    private record SearchGovernanceResult(
            List<ExperienceRecipeRegistryEntry> dedupedRows,
            List<String> candidateCanonicalGroups,
            Map<String, Integer> filteredCounts) {
    }

    private record PublishGateDecision(boolean allowed, String reason) {
        static PublishGateDecision passed() {
            return new PublishGateDecision(true, null);
        }

        static PublishGateDecision blocked(String reason) {
            return new PublishGateDecision(false, reason);
        }
    }
}
