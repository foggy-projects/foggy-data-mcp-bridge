package com.foggyframework.dataset.model.candidate;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.member.SyntheticMemberQueryModelResolver;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.validation.DetachedModelValidationFactory;
import com.foggyframework.dataset.model.validation.DetachedModelValidationSession;
import com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** Production candidate-query adapter backed by detached TM/QM loaders. */
public final class DefaultCandidateQueryFactory implements CandidateQueryFactory {

    private static final List<String> SOURCE_SUFFIXES =
            List.of(".tm", ".qm", ".fsscript");
    private static final List<String> ISOLATION_DIAGNOSTICS = List.of(
            "REQUEST_LOCAL_CATALOG_PINNED",
            "SHARED_L1_CACHE_DISABLED",
            "SHARED_L2_CACHE_DISABLED",
            "PREAGGREGATION_DISABLED"
    );

    private final SystemBundlesContext liveBundlesContext;
    private final DetachedModelValidationFactory validationFactory;
    private final SemanticQueryServiceV3 semanticQueryService;
    private final CommittedSourceRevisionRegistry sourceRevisionRegistry;

    public DefaultCandidateQueryFactory(
            SystemBundlesContext liveBundlesContext,
            DetachedModelValidationFactory validationFactory,
            SemanticQueryServiceV3 semanticQueryService,
            CommittedSourceRevisionRegistry sourceRevisionRegistry
    ) {
        this.liveBundlesContext = Objects.requireNonNull(
                liveBundlesContext, "liveBundlesContext");
        this.validationFactory = Objects.requireNonNull(
                validationFactory, "validationFactory");
        this.semanticQueryService = Objects.requireNonNull(
                semanticQueryService, "semanticQueryService");
        this.sourceRevisionRegistry = Objects.requireNonNull(
                sourceRevisionRegistry, "sourceRevisionRegistry");
    }

    @Override
    public CandidateQuerySession open(CandidateQuerySource source) {
        CandidateSourceSnapshot snapshot = inspectSource(source, "open");
        requireLiveSourceBundle(source, "open");
        requireLiveSourceCurrent(source, "open");
        requireOverlayAllowed(source, snapshot, "open");

        DetachedModelValidationSession detached = validationFactory.open(
                source.sourceBundle(),
                canonicalNamespace(source.namespace()),
                snapshot.root().toString()
        );
        return new Session(source, snapshot, detached);
    }

    private final class Session implements CandidateQuerySession {

        private final CandidateQuerySource source;
        private final CandidateSourceSnapshot openedSnapshot;
        private final DetachedModelValidationSession detached;
        private final Map<String, CatalogResolution<QueryModel>> resolutions =
                new HashMap<>();
        private boolean closed;

        private Session(
                CandidateQuerySource source,
                CandidateSourceSnapshot openedSnapshot,
                DetachedModelValidationSession detached
        ) {
            this.source = source;
            this.openedSnapshot = openedSnapshot;
            this.detached = detached;
        }

        @Override
        public CandidateQueryIdentity identity() {
            CatalogIdentity catalogIdentity = resolutions.values().stream()
                    .findFirst()
                    .map(CatalogResolution::catalogIdentity)
                    .orElse(null);
            return new CandidateQueryIdentity(
                    canonicalNamespace(source.namespace()),
                    source.sourceBundle().trim(),
                    source.baseSourceRevision().trim(),
                    openedSnapshot.revision(),
                    catalogIdentity
            );
        }

        @Override
        public CandidateQueryResult validate(
                String model,
                SemanticQueryRequest request,
                SemanticRequestContext context
        ) {
            return invoke("validate", model, request, context);
        }

        @Override
        public CandidateQueryResult execute(
                String model,
                SemanticQueryRequest request,
                SemanticRequestContext context
        ) {
            return invoke("execute", model, request, context);
        }

        private CandidateQueryResult invoke(
                String phase,
                String model,
                SemanticQueryRequest request,
                SemanticRequestContext context
        ) {
            requireOpen(phase);
            requireSupportedRequest(model, request, phase);
            verifyCurrent(phase);
            CatalogResolution<QueryModel> resolution = resolution(model, phase);
            SemanticRequestContext baseContext = context == null
                    ? SemanticRequestContext.ofNamespace(source.namespace())
                    : context;
            if (!canonicalNamespace(source.namespace()).equals(
                    canonicalNamespace(baseContext.getNamespace()))) {
                throw failure(
                        CandidateQueryErrorCode.CANDIDATE_SOURCE_INVALID,
                        phase,
                        "Candidate request namespace does not match the source namespace",
                        model
                );
            }
            SemanticRequestContext candidateContext = baseContext
                    .withCandidateExecution(
                            resolution, detached.executionBundlesContext())
                    .withPermissionAction("validate".equals(phase)
                            ? PermissionAction.VALIDATE
                            : PermissionAction.EXECUTE);

            try {
                SemanticQueryResponse response = semanticQueryService.queryModel(
                        model, request, phase, candidateContext);
                verifyCurrent(phase);
                return new CandidateQueryResult(
                        response, identity(), phase, ISOLATION_DIAGNOSTICS);
            } catch (RuntimeException | Error failure) {
                try {
                    verifyCurrent(phase);
                } catch (RuntimeException stale) {
                    stale.addSuppressed(failure);
                    throw stale;
                }
                throw failure;
            }
        }

        private CatalogResolution<QueryModel> resolution(
                String model,
                String phase
        ) {
            CatalogResolution<QueryModel> cached = resolutions.get(model);
            if (cached != null) {
                return cached;
            }
            BundleResource sourceResource = detached.sourceBundle()
                    .findBundleResource(model + ".qm", false);
            if (sourceResource == null) {
                throw failure(
                        CandidateQueryErrorCode.CANDIDATE_MODEL_NOT_IN_SOURCE,
                        phase,
                        "Candidate query model is absent from the candidate source",
                        model
                );
            }
            CatalogResolution<QueryModel> detachedResolution =
                    detached.resolveQueryModel(model, source.namespace());
            if (!model.equals(detachedResolution.canonicalName())) {
                throw failure(
                        CandidateQueryErrorCode.CANDIDATE_MODEL_NOT_IN_SOURCE,
                        phase,
                        "Candidate query aliases are not supported",
                        model
                );
            }
            if (!(detachedResolution.model() instanceof JdbcQueryModelImpl)) {
                throw failure(
                        CandidateQueryErrorCode.CANDIDATE_MODE_UNSUPPORTED,
                        phase,
                        "Candidate query supports resolved JDBC query models only",
                        model
                );
            }
            CatalogIdentity identity = new CatalogIdentity(
                    source.namespace(),
                    new CatalogGeneration("candidate:" + openedSnapshot.revision()),
                    new SourceRevision(source.baseSourceRevision().trim())
            );
            CatalogResolution<QueryModel> candidateResolution =
                    new CatalogResolution<>(
                            detachedResolution.canonicalName(),
                            detachedResolution.model(),
                            identity,
                            detachedResolution.dependencyBindings(),
                            detachedResolution.bindingIdentityComplete()
                    );
            resolutions.put(model, candidateResolution);
            verifyCurrent(phase);
            return candidateResolution;
        }

        private void verifyCurrent(String phase) {
            requireLiveSourceCurrent(source, phase);
            CandidateSourceSnapshot current = inspectSource(source, phase);
            if (!openedSnapshot.revision().equals(current.revision())) {
                throw failure(
                        CandidateQueryErrorCode.CANDIDATE_CONTENT_STALE,
                        phase,
                        "Candidate source content changed during the active session",
                        null
                );
            }
            requireOverlayAllowed(source, current, phase);
        }

        private void requireOpen(String phase) {
            if (closed) {
                throw failure(
                        CandidateQueryErrorCode.CANDIDATE_SESSION_CLOSED,
                        phase,
                        "Candidate query session is closed",
                        null
                );
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            resolutions.clear();
            detached.close();
        }
    }

    private void requireLiveSourceBundle(
            CandidateQuerySource source,
            String phase
    ) {
        requireText(source.sourceBundle(), "sourceBundle", phase);
        requireText(source.baseSourceRevision(), "baseSourceRevision", phase);
        Bundle bundle = liveBundlesContext.getBundleByName(
                source.sourceBundle().trim(), false);
        if (bundle == null || bundle.getDefinition() == null
                || !canonicalNamespace(source.namespace()).equals(
                canonicalNamespace(bundle.getDefinition().getNamespace()))) {
            throw failure(
                    CandidateQueryErrorCode.CANDIDATE_SOURCE_INVALID,
                    phase,
                    "Candidate source Bundle is absent from the target namespace",
                    source.sourceBundle()
            );
        }
    }

    private void requireLiveSourceCurrent(
            CandidateQuerySource source,
            String phase
    ) {
        String expected = requireText(
                source.baseSourceRevision(), "baseSourceRevision", phase);
        String current = sourceRevisionRegistry.currentRevision(source.namespace());
        if (!expected.equals(current)) {
            throw failure(
                    CandidateQueryErrorCode.CANDIDATE_SOURCE_STALE,
                    phase,
                    "Candidate base source revision is stale",
                    null
            );
        }
    }

    private void requireOverlayAllowed(
            CandidateQuerySource source,
            CandidateSourceSnapshot snapshot,
            String phase
    ) {
        Set<String> modelNames = new HashSet<>();
        for (Path file : snapshot.files()) {
            String filename = file.getFileName().toString();
            if (!(filename.endsWith(".tm") || filename.endsWith(".qm"))) {
                continue;
            }
            if (!modelNames.add(filename)) {
                throw failure(
                        CandidateQueryErrorCode.CANDIDATE_SOURCE_INVALID,
                        phase,
                        "Candidate source contains duplicate model filenames",
                        filename
                );
            }
            BundleResource liveResource;
            try {
                liveResource = liveBundlesContext.findResourceByName(
                        filename, source.namespace(), false);
            } catch (RuntimeException ambiguous) {
                CandidateQueryException failure = failure(
                        CandidateQueryErrorCode.CANDIDATE_OVERLAY_FORBIDDEN,
                        phase,
                        "Candidate model ownership is ambiguous in the live namespace",
                        filename
                );
                failure.addSuppressed(ambiguous);
                throw failure;
            }
            if (liveResource != null
                    && (liveResource.getBundle() == null
                    || !source.sourceBundle().trim().equals(
                    liveResource.getBundle().getName()))) {
                throw failure(
                        CandidateQueryErrorCode.CANDIDATE_OVERLAY_FORBIDDEN,
                        phase,
                        "Candidate model would shadow a resource owned by another Bundle",
                        filename
                );
            }
        }
    }

    private CandidateSourceSnapshot inspectSource(
            CandidateQuerySource source,
            String phase
    ) {
        if (source == null) {
            throw failure(
                    CandidateQueryErrorCode.CANDIDATE_SOURCE_INVALID,
                    phase,
                    "Candidate source is required",
                    null
            );
        }
        String configuredPath = requireText(source.path(), "path", phase);
        Path root = Path.of(configuredPath).toAbsolutePath().normalize();
        try {
            Path realRoot = root.toRealPath();
            if (!root.equals(realRoot) || Files.isSymbolicLink(root)
                    || !Files.isDirectory(root)) {
                throw invalidSource(phase,
                        "Candidate source must be a non-symlink directory", null);
            }
            List<Path> files = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.toList()) {
                    if (Files.isSymbolicLink(path)) {
                        throw invalidSource(
                                phase,
                                "Candidate source must not contain symbolic links",
                                safeRelative(root, path)
                        );
                    }
                    if (Files.isRegularFile(path) && isCandidateSource(path)) {
                        files.add(path);
                    }
                }
            }
            files.sort(Comparator.comparing(path -> safeRelative(root, path)));
            if (files.isEmpty()) {
                throw invalidSource(
                        phase,
                        "Candidate source contains no TM, QM, or FSScript resources",
                        null
                );
            }
            return new CandidateSourceSnapshot(
                    root, List.copyOf(files), contentRevision(root, files));
        } catch (CandidateQueryException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            CandidateQueryException invalid = invalidSource(
                    phase, "Candidate source cannot be read", null);
            invalid.addSuppressed(failure);
            throw invalid;
        }
    }

    private static String contentRevision(Path root, List<Path> files)
            throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        byte[] buffer = new byte[8192];
        for (Path file : files) {
            byte[] relative = safeRelative(root, file)
                    .getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES)
                    .putInt(relative.length).array());
            digest.update(relative);
            long size = Files.size(file);
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(size).array());
            try (InputStream input = Files.newInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static boolean isCandidateSource(Path path) {
        String filename = path.getFileName().toString();
        return SOURCE_SUFFIXES.stream().anyMatch(filename::endsWith);
    }

    private static void requireSupportedRequest(
            String model,
            SemanticQueryRequest request,
            String phase
    ) {
        if (model == null || model.isBlank() || request == null) {
            throw failure(
                    CandidateQueryErrorCode.CANDIDATE_SOURCE_INVALID,
                    phase,
                    "Candidate model and semantic request are required",
                    model
            );
        }
        boolean unsupported = model.contains(
                SyntheticMemberQueryModelResolver.MODEL_SEPARATOR)
                || request.isPivotMode()
                || request.getSemanticSql() != null
                || request.getMemoryGridPlan() != null
                || request.getGridSql() != null
                || request.getMemoryGridBindings() != null
                || request.getExecutablePlan() != null
                || request.getTimeWindow() != null
                || request.getRoute() != null
                || request.getStatus() != null;
        if (unsupported) {
            throw failure(
                    CandidateQueryErrorCode.CANDIDATE_MODE_UNSUPPORTED,
                    phase,
                    "Candidate query supports ordinary JDBC semantic requests only",
                    model
            );
        }
    }

    private static String requireText(
            String value,
            String label,
            String phase
    ) {
        if (value == null || value.isBlank()) {
            throw failure(
                    CandidateQueryErrorCode.CANDIDATE_SOURCE_INVALID,
                    phase,
                    "Candidate " + label + " is required",
                    null
            );
        }
        return value.trim();
    }

    private static String canonicalNamespace(String namespace) {
        return CatalogIdentity.canonicalNamespace(namespace);
    }

    private static String safeRelative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static CandidateQueryException invalidSource(
            String phase,
            String message,
            String resource
    ) {
        return failure(
                CandidateQueryErrorCode.CANDIDATE_SOURCE_INVALID,
                phase,
                message,
                resource
        );
    }

    private static CandidateQueryException failure(
            CandidateQueryErrorCode code,
            String phase,
            String message,
            String resource
    ) {
        return new CandidateQueryException(code, phase, message, resource);
    }

    private record CandidateSourceSnapshot(
            Path root,
            List<Path> files,
            String revision
    ) {
    }
}
