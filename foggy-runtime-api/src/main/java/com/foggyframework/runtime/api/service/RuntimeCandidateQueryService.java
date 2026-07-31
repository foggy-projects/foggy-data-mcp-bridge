package com.foggyframework.runtime.api.service;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.dataset.model.candidate.CandidateQueryErrorCode;
import com.foggyframework.dataset.model.candidate.CandidateQueryException;
import com.foggyframework.dataset.model.candidate.CandidateQueryFactory;
import com.foggyframework.dataset.model.candidate.CandidateQueryResult;
import com.foggyframework.dataset.model.candidate.CandidateQuerySession;
import com.foggyframework.dataset.model.candidate.CandidateQuerySource;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService.RuntimeBundleRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runtime-internal orchestration for one managed Bundle candidate query.
 * This service deliberately has no controller or public route.
 */
@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
@ConditionalOnBean(CandidateQueryFactory.class)
public class RuntimeCandidateQueryService {

    private final RuntimeBundleRegistryService bundleRegistry;
    private final SystemBundlesContext liveBundlesContext;
    private final CandidateQueryFactory candidateQueryFactory;

    public RuntimeCandidateQueryService(
            RuntimeBundleRegistryService bundleRegistry,
            SystemBundlesContext liveBundlesContext,
            CandidateQueryFactory candidateQueryFactory
    ) {
        this.bundleRegistry = bundleRegistry;
        this.liveBundlesContext = liveBundlesContext;
        this.candidateQueryFactory = candidateQueryFactory;
    }

    public RuntimeCandidateQueryResult query(RuntimeCandidateQueryCommand command) {
        if (command == null) {
            throw invalid("Candidate query command is required", null);
        }
        if (command.phase() == null) {
            throw invalid("Candidate query phase is required", command.model());
        }
        RuntimeBundleRecord record = bundleRegistry.find(command.sourceBundle())
                .filter(RuntimeBundleRecord::enabled)
                .orElseThrow(() -> invalid(
                        "Candidate source must be an enabled Runtime-managed Bundle",
                        command.sourceBundle()));
        if (!canonicalNamespace(record.namespace()).equals(
                canonicalNamespace(command.namespace()))) {
            throw invalid(
                    "Candidate source Bundle does not belong to the target namespace",
                    command.sourceBundle());
        }
        requireManagedLiveBundle(record, command);

        CandidateQuerySource source = new CandidateQuerySource(
                record.name(),
                command.namespace(),
                command.candidatePath(),
                command.baseSourceRevision()
        );
        try (CandidateQuerySession session = candidateQueryFactory.open(source)) {
            SemanticRequestContext context = SemanticRequestContext.of(
                    command.namespace(), command.authorization());
            CandidateQueryResult result = switch (command.phase()) {
                case VALIDATE -> session.validate(
                        command.model(), command.request(), context);
                case EXECUTE -> session.execute(
                        command.model(), command.request(), context);
            };
            return new RuntimeCandidateQueryResult(
                    result.response(),
                    result.identity().sourceBundle(),
                    result.identity().namespace(),
                    result.identity().candidateRevision(),
                    result.identity().baseSourceRevision(),
                    result.identity().catalogIdentity(),
                    result.phase(),
                    result.diagnostics()
            );
        }
    }

    private void requireManagedLiveBundle(
            RuntimeBundleRecord record,
            RuntimeCandidateQueryCommand command
    ) {
        Bundle liveBundle;
        try {
            liveBundle = liveBundlesContext.getBundleByName(record.name(), false);
        } catch (RuntimeException lookupFailure) {
            CandidateQueryException failure = invalid(
                    "Candidate source is not an active Runtime-managed external Bundle",
                    command.sourceBundle());
            failure.addSuppressed(lookupFailure);
            throw failure;
        }
        if (!(liveBundle instanceof ExternalFileBundle externalBundle)
                || !(liveBundle.getDefinition()
                instanceof ExternalBundleDefinition externalDefinition)) {
            throw invalid(
                    "Candidate source is not an active Runtime-managed external Bundle",
                    command.sourceBundle());
        }

        String expectedName = canonicalName(command.sourceBundle());
        if (!expectedName.equals(canonicalName(record.name()))
                || !expectedName.equals(canonicalName(externalBundle.getName()))
                || !expectedName.equals(canonicalName(externalDefinition.getName()))) {
            throw invalid(
                    "Candidate source identity does not match the live external Bundle",
                    command.sourceBundle());
        }

        String expectedNamespace = canonicalNamespace(command.namespace());
        if (!expectedNamespace.equals(canonicalNamespace(record.namespace()))
                || !expectedNamespace.equals(canonicalNamespace(
                externalDefinition.getNamespace()))) {
            throw invalid(
                    "Candidate source namespace does not match the live external Bundle",
                    command.sourceBundle());
        }

        Path managedPath = realDirectory(record.path(), command.sourceBundle());
        if (!managedPath.equals(realDirectory(
                externalDefinition.getPath(), command.sourceBundle()))
                || !managedPath.equals(realDirectory(
                externalBundle.getBasePath(), command.sourceBundle()))
                || !managedPath.equals(realDirectory(
                externalBundle.getRootPath(), command.sourceBundle()))) {
            throw invalid(
                    "Candidate source path does not match the live external Bundle",
                    command.sourceBundle());
        }
    }

    private static Path realDirectory(String configuredPath, String resource) {
        try {
            if (configuredPath == null || configuredPath.isBlank()) {
                throw new IllegalArgumentException("blank candidate source path");
            }
            Path normalized = Path.of(configuredPath)
                    .toAbsolutePath()
                    .normalize();
            Path real = normalized.toRealPath();
            if (!normalized.equals(real)
                    || Files.isSymbolicLink(normalized)
                    || !Files.isDirectory(real)) {
                throw new IllegalArgumentException(
                        "candidate source path is not a direct real directory");
            }
            return real;
        } catch (IOException | RuntimeException pathFailure) {
            CandidateQueryException failure = invalid(
                    "Candidate source path is not a readable real directory",
                    resource);
            failure.addSuppressed(pathFailure);
            throw failure;
        }
    }

    private static CandidateQueryException invalid(String message, String resource) {
        return new CandidateQueryException(
                CandidateQueryErrorCode.CANDIDATE_SOURCE_INVALID,
                "open",
                message,
                resource
        );
    }

    private static String canonicalNamespace(String namespace) {
        return CatalogIdentity.canonicalNamespace(namespace);
    }

    private static String canonicalName(String name) {
        return name == null ? "" : name.trim();
    }

    public enum Phase {
        VALIDATE,
        EXECUTE
    }

    public record RuntimeCandidateQueryCommand(
            String sourceBundle,
            String namespace,
            String candidatePath,
            String baseSourceRevision,
            String model,
            SemanticQueryRequest request,
            String authorization,
            Phase phase
    ) {
    }

    public record RuntimeCandidateQueryResult(
            SemanticQueryResponse response,
            String sourceBundle,
            String namespace,
            String candidateRevision,
            String baseSourceRevision,
            CatalogIdentity catalogIdentity,
            String phase,
            List<String> diagnostics
    ) {
        public RuntimeCandidateQueryResult {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }
}
