package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsBundleDependencyState;
import com.foggyframework.analytics.definition.api.AnalyticsBundleLifecycle;
import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.api.AnalyticsBundleSourceState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.BUNDLE_IDENTITY_MISMATCH;
import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.BUNDLE_NOT_REGISTERED;
import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.BUNDLE_UNAVAILABLE;
import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.DEPENDENCY_STALE;
import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.DIGEST_MISMATCH;
import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.IMMUTABLE_BUNDLE;
import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.INVALID_BUNDLE;
import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.RECOVERY_FAILED;
import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.REVISION_CONFLICT;
import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.UNSAFE_PATH;

/**
 * Independent filesystem store for Analytics Bundles.
 *
 * <p>The store uses trusted registrations, an Analytics-only path allowlist,
 * optimistic revision checks, a cross-process publication lock and an external
 * rollback journal. It has no dependency on model-engine resources or Spring.</p>
 */
public final class FileSystemAnalyticsBundleStore implements AnalyticsBundleStore {

    private static final int JOURNAL_MAGIC = 0x46414231;
    private static final int JOURNAL_VERSION = 1;
    private static final Map<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

    private final Map<AnalyticsBundleRef, AnalyticsBundleRegistration> registrations;
    private final AnalyticsBundleDependencyStateResolver dependencyStateResolver;
    private final AnalyticsBundleManifestJsonCodec manifestCodec;
    private final AnalyticsBundleRevisionCalculator revisionCalculator;
    private final AnalyticsBundleStructureValidator structureValidator;
    private final AnalyticsArtifactPathPolicy pathPolicy;
    private final AnalyticsAtomicFileWriter atomicFileWriter;

    public FileSystemAnalyticsBundleStore(Collection<AnalyticsBundleRegistration> registrations) {
        this(registrations, AnalyticsBundleDependencyStateResolver.failClosed());
    }

    public FileSystemAnalyticsBundleStore(
            Collection<AnalyticsBundleRegistration> registrations,
            AnalyticsBundleDependencyStateResolver dependencyStateResolver) {
        this(
                registrations,
                dependencyStateResolver,
                new AnalyticsBundleManifestJsonCodec(),
                new AnalyticsBundleStructureValidator(),
                new AnalyticsArtifactPathPolicy(),
                new DefaultAnalyticsAtomicFileWriter());
    }

    FileSystemAnalyticsBundleStore(
            Collection<AnalyticsBundleRegistration> registrations,
            AnalyticsBundleDependencyStateResolver dependencyStateResolver,
            AnalyticsBundleManifestJsonCodec manifestCodec,
            AnalyticsBundleStructureValidator structureValidator,
            AnalyticsArtifactPathPolicy pathPolicy,
            AnalyticsAtomicFileWriter atomicFileWriter) {
        this.registrations = immutableRegistrations(registrations);
        this.dependencyStateResolver = Objects.requireNonNull(
                dependencyStateResolver,
                "dependencyStateResolver");
        this.manifestCodec = Objects.requireNonNull(manifestCodec, "manifestCodec");
        this.revisionCalculator = new AnalyticsBundleRevisionCalculator(manifestCodec);
        this.structureValidator = Objects.requireNonNull(structureValidator, "structureValidator");
        this.pathPolicy = Objects.requireNonNull(pathPolicy, "pathPolicy");
        this.atomicFileWriter = Objects.requireNonNull(atomicFileWriter, "atomicFileWriter");
    }

    @Override
    public ResolvedAnalyticsBundle resolve(AnalyticsBundleRef bundleRef) {
        AnalyticsBundleRegistration registration = registration(bundleRef);
        if (registration.sourceState() == AnalyticsBundleSourceState.RUNTIME_OWNED) {
            return withControlLock(registration, () -> {
                recoverRequired(registration);
                return resolveInternal(registration);
            });
        }
        return callUnchecked(registration, () -> resolveInternal(registration));
    }

    @Override
    public byte[] readArtifact(
            AnalyticsBundleRef bundleRef,
            AnalyticsBundleRevision expectedRevision,
            String relativePath) {
        Objects.requireNonNull(expectedRevision, "expectedRevision");
        AnalyticsBundleRegistration registration = registration(bundleRef);
        StoreOperation<byte[]> read = () -> {
            if (registration.sourceState() == AnalyticsBundleSourceState.RUNTIME_OWNED) {
                recoverRequired(registration);
            }
            ResolvedAnalyticsBundle resolved = resolveInternal(registration);
            requireExpectedRevision(resolved, expectedRevision);
            Path target = pathPolicy.resolve(registration.root(), relativePath);
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new AnalyticsBundleStoreException(
                        BUNDLE_UNAVAILABLE,
                        "Analytics artifact does not exist: " + relativePath);
            }
            return Files.readAllBytes(target);
        };
        return registration.sourceState() == AnalyticsBundleSourceState.RUNTIME_OWNED
                ? withControlLock(registration, read)
                : callUnchecked(registration, read);
    }

    @Override
    public AnalyticsDefinitionSnapshot readDefinitionSnapshot(
            AnalyticsBundleRef bundleRef,
            AnalyticsBundleRevision expectedRevision) {
        Objects.requireNonNull(expectedRevision, "expectedRevision");
        AnalyticsBundleRegistration registration = registration(bundleRef);
        StoreOperation<AnalyticsDefinitionSnapshot> read = () -> {
            if (registration.sourceState() == AnalyticsBundleSourceState.RUNTIME_OWNED) {
                recoverRequired(registration);
            }
            ResolvedAnalyticsBundle resolved = resolveInternal(registration);
            requireExpectedRevision(resolved, expectedRevision);
            Map<String, byte[]> artifacts = new LinkedHashMap<>();
            try (var paths = Files.walk(registration.root())) {
                for (Path path : paths
                        .filter(candidate -> Files.isRegularFile(
                                candidate,
                                LinkOption.NOFOLLOW_LINKS))
                        .sorted()
                        .toList()) {
                    String relativePath = portable(registration.root(), path);
                    if (pathPolicy.isDefinitionArtifact(relativePath)) {
                        artifacts.put(relativePath, Files.readAllBytes(path));
                    }
                }
            }
            return new AnalyticsDefinitionSnapshot(resolved, artifacts);
        };
        return registration.sourceState() == AnalyticsBundleSourceState.RUNTIME_OWNED
                ? withControlLock(registration, read)
                : callUnchecked(registration, read);
    }

    @Override
    public ResolvedAnalyticsBundle saveArtifact(
            AnalyticsBundleRef bundleRef,
            AnalyticsBundleRevision expectedRevision,
            String relativePath,
            byte[] content) {
        Objects.requireNonNull(expectedRevision, "expectedRevision");
        Objects.requireNonNull(content, "content");
        AnalyticsBundleRegistration registration = registration(bundleRef);
        if (registration.sourceState() != AnalyticsBundleSourceState.RUNTIME_OWNED) {
            throw new AnalyticsBundleStoreException(
                    IMMUTABLE_BUNDLE,
                    "Analytics Bundle is read-only in state " + registration.sourceState());
        }
        byte[] safeContent = content.clone();
        return withControlLock(registration, () -> saveUnderLock(
                registration,
                expectedRevision,
                relativePath,
                safeContent));
    }

    private ResolvedAnalyticsBundle saveUnderLock(
            AnalyticsBundleRegistration registration,
            AnalyticsBundleRevision expectedRevision,
            String relativePath,
            byte[] content) throws IOException {
        recoverRequired(registration);
        ResolvedAnalyticsBundle current = resolveInternal(registration);
        requireExpectedRevision(current, expectedRevision);
        Path target = pathPolicy.resolve(registration.root(), relativePath);
        String canonicalPath = portable(registration.root(), target);
        try {
            structureValidator.validateArtifact(canonicalPath, content);
        } catch (IllegalArgumentException invalid) {
            throw new AnalyticsBundleStoreException(
                    INVALID_BUNDLE,
                    "Analytics artifact validation failed: " + canonicalPath,
                    invalid);
        }

        Files.createDirectories(target.getParent());
        target = pathPolicy.resolve(registration.root(), canonicalPath);
        Path manifestPath = registration.root().resolve(
                AnalyticsBundleRevisionCalculator.MANIFEST_FILE);
        byte[] previousManifest = Files.readAllBytes(manifestPath);
        byte[] previousArtifact = Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                ? Files.readAllBytes(target)
                : null;
        Path journalPath = journalPath(registration);
        writeJournal(journalPath, new WriteJournal(
                canonicalPath,
                previousManifest,
                previousArtifact));

        try {
            atomicFileWriter.write(target, content);
            AnalyticsBundleRevision nextRevision = revisionCalculator.calculate(registration.root());
            byte[] nextManifest = manifestCodec.withBundleRevision(
                    previousManifest,
                    nextRevision);
            atomicFileWriter.write(manifestPath, nextManifest);
            ResolvedAnalyticsBundle resolved = resolveInternal(registration);
            Files.deleteIfExists(journalPath);
            return resolved;
        } catch (IOException failure) {
            recoverAfterFailure(registration, failure);
            throw failure;
        } catch (RuntimeException failure) {
            recoverAfterFailure(registration, failure);
            throw failure;
        }
    }

    private ResolvedAnalyticsBundle resolveInternal(
            AnalyticsBundleRegistration registration) throws IOException {
        AnalyticsBundleManifest manifest = validateStoredBundle(registration);
        AnalyticsBundleDependencyState dependencyState;
        try {
            dependencyState = dependencyStateResolver.resolve(manifest);
        } catch (AnalyticsBundleStoreException known) {
            throw known;
        } catch (RuntimeException failure) {
            throw new AnalyticsBundleStoreException(
                    BUNDLE_UNAVAILABLE,
                    "Analytics model dependency resolution failed",
                    failure);
        }
        if (dependencyState == null || dependencyState == AnalyticsBundleDependencyState.STALE) {
            throw new AnalyticsBundleStoreException(
                    DEPENDENCY_STALE,
                    "Analytics Bundle has missing or stale model dependencies: "
                            + registration.bundleRef().value());
        }
        return new ResolvedAnalyticsBundle(
                manifest,
                new AnalyticsBundleLifecycle(registration.sourceState(), dependencyState));
    }

    private AnalyticsBundleManifest validateStoredBundle(
            AnalyticsBundleRegistration registration) throws IOException {
        AnalyticsBundleRevision calculated;
        try {
            calculated = revisionCalculator.calculate(registration.root());
        } catch (AnalyticsBundleRevisionCalculator.UnsafeBundlePathException unsafe) {
            throw new AnalyticsBundleStoreException(
                    UNSAFE_PATH,
                    "Analytics Bundle contains an unsafe path",
                    unsafe);
        } catch (IllegalArgumentException invalid) {
            throw new AnalyticsBundleStoreException(
                    INVALID_BUNDLE,
                    "Analytics Bundle content is invalid",
                    invalid);
        }
        Path manifestPath = registration.root().resolve(
                AnalyticsBundleRevisionCalculator.MANIFEST_FILE);
        if (Files.isSymbolicLink(manifestPath)
                || !Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new AnalyticsBundleStoreException(
                    INVALID_BUNDLE,
                    "Analytics Bundle requires a real root manifest.json");
        }
        AnalyticsBundleManifest manifest;
        try {
            manifest = manifestCodec.read(Files.readAllBytes(manifestPath));
            structureValidator.validate(registration.root(), manifest);
        } catch (IllegalArgumentException invalid) {
            throw new AnalyticsBundleStoreException(
                    INVALID_BUNDLE,
                    "Analytics Bundle validation failed",
                    invalid);
        }
        if (!registration.bundleRef().equals(manifest.bundleRef())) {
            throw new AnalyticsBundleStoreException(
                    BUNDLE_IDENTITY_MISMATCH,
                    "Registered bundleRef does not match manifest bundleRef");
        }
        if (!calculated.equals(manifest.bundleRevision())) {
            throw new AnalyticsBundleStoreException(
                    DIGEST_MISMATCH,
                    "Declared bundleRevision does not match canonical bundle content");
        }
        return manifest;
    }

    private void recoverRequired(AnalyticsBundleRegistration registration) throws IOException {
        Path journal = journalPath(registration);
        if (!Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            recover(registration, journal);
        } catch (IOException | RuntimeException failure) {
            throw new AnalyticsBundleStoreException(
                    RECOVERY_FAILED,
                    "Analytics Bundle write recovery failed",
                    failure);
        }
    }

    private void recoverAfterFailure(
            AnalyticsBundleRegistration registration,
            Throwable originalFailure) throws IOException {
        try {
            recoverRequired(registration);
        } catch (RuntimeException recoveryFailure) {
            recoveryFailure.addSuppressed(originalFailure);
            throw recoveryFailure;
        }
    }

    private void recover(
            AnalyticsBundleRegistration registration,
            Path journalPath) throws IOException {
        if (Files.isSymbolicLink(journalPath)
                || !Files.isRegularFile(journalPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Analytics write journal is not a real file");
        }
        WriteJournal journal = readJournal(journalPath);
        Path target = pathPolicy.resolve(registration.root(), journal.relativePath());
        if (journal.previousArtifact() == null) {
            Files.deleteIfExists(target);
        } else {
            Files.createDirectories(target.getParent());
            target = pathPolicy.resolve(registration.root(), journal.relativePath());
            atomicFileWriter.write(target, journal.previousArtifact());
        }
        Path manifestPath = registration.root().resolve(
                AnalyticsBundleRevisionCalculator.MANIFEST_FILE);
        atomicFileWriter.write(manifestPath, journal.previousManifest());
        validateStoredBundle(registration);
        Files.deleteIfExists(journalPath);
    }

    private void writeJournal(Path journalPath, WriteJournal journal) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(JOURNAL_MAGIC);
            output.writeInt(JOURNAL_VERSION);
            writeBytes(output, journal.relativePath().getBytes(StandardCharsets.UTF_8));
            writeBytes(output, journal.previousManifest());
            if (journal.previousArtifact() == null) {
                output.writeInt(-1);
            } else {
                writeBytes(output, journal.previousArtifact());
            }
        }
        atomicFileWriter.write(journalPath, bytes.toByteArray());
    }

    private WriteJournal readJournal(Path journalPath) throws IOException {
        byte[] bytes = Files.readAllBytes(journalPath);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != JOURNAL_MAGIC || input.readInt() != JOURNAL_VERSION) {
                throw new IOException("Unsupported Analytics write journal");
            }
            String relativePath = new String(readBytes(input, false), StandardCharsets.UTF_8);
            byte[] previousManifest = readBytes(input, false);
            byte[] previousArtifact = readBytes(input, true);
            if (input.available() != 0) {
                throw new IOException("Analytics write journal has trailing data");
            }
            return new WriteJournal(relativePath, previousManifest, previousArtifact);
        } catch (EOFException truncated) {
            throw new IOException("Analytics write journal is truncated", truncated);
        }
    }

    private static void writeBytes(DataOutputStream output, byte[] bytes) throws IOException {
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static byte[] readBytes(DataInputStream input, boolean allowMissing) throws IOException {
        int length = input.readInt();
        if (allowMissing && length == -1) {
            return null;
        }
        if (length < 0 || length > input.available()) {
            throw new IOException("Analytics write journal contains an invalid length");
        }
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("Analytics write journal is truncated");
        }
        return value;
    }

    private <T> T withControlLock(
            AnalyticsBundleRegistration registration,
            StoreOperation<T> operation) {
        Path lockPath = lockPath(registration);
        ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(lockPath, ignored -> new ReentrantLock());
        jvmLock.lock();
        try {
            verifyControlParent(registration.root().getParent());
            rejectControlSymlink(lockPath);
            try (FileChannel channel = FileChannel.open(
                    lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS);
                 FileLock ignored = channel.lock()) {
                return operation.run();
            }
        } catch (AnalyticsBundleStoreException known) {
            throw known;
        } catch (IOException failure) {
            throw new AnalyticsBundleStoreException(
                    BUNDLE_UNAVAILABLE,
                    "Analytics Bundle filesystem operation failed",
                    failure);
        } finally {
            jvmLock.unlock();
        }
    }

    private <T> T callUnchecked(
            AnalyticsBundleRegistration registration,
            StoreOperation<T> operation) {
        try {
            return operation.run();
        } catch (AnalyticsBundleStoreException known) {
            throw known;
        } catch (IOException failure) {
            throw new AnalyticsBundleStoreException(
                    BUNDLE_UNAVAILABLE,
                    "Analytics Bundle is unavailable: " + registration.bundleRef().value(),
                    failure);
        }
    }

    private AnalyticsBundleRegistration registration(AnalyticsBundleRef bundleRef) {
        Objects.requireNonNull(bundleRef, "bundleRef");
        AnalyticsBundleRegistration registration = registrations.get(bundleRef);
        if (registration == null) {
            throw new AnalyticsBundleStoreException(
                    BUNDLE_NOT_REGISTERED,
                    "Analytics Bundle is not registered: " + bundleRef.value());
        }
        return registration;
    }

    private static void requireExpectedRevision(
            ResolvedAnalyticsBundle resolved,
            AnalyticsBundleRevision expectedRevision) {
        if (!resolved.bundleRevision().equals(expectedRevision)) {
            throw new AnalyticsBundleStoreException(
                    REVISION_CONFLICT,
                    "Analytics Bundle revision changed; resolve and retry");
        }
    }

    private static Map<AnalyticsBundleRef, AnalyticsBundleRegistration> immutableRegistrations(
            Collection<AnalyticsBundleRegistration> registrations) {
        Objects.requireNonNull(registrations, "registrations");
        Map<AnalyticsBundleRef, AnalyticsBundleRegistration> byRef = new LinkedHashMap<>();
        Set<Path> roots = new HashSet<>();
        for (AnalyticsBundleRegistration registration : registrations) {
            Objects.requireNonNull(registration, "registration");
            if (byRef.put(registration.bundleRef(), registration) != null) {
                throw new IllegalArgumentException(
                        "Duplicate Analytics bundleRef registration: "
                                + registration.bundleRef().value());
            }
            if (!roots.add(registration.root())) {
                throw new IllegalArgumentException(
                        "An Analytics Bundle root can only have one registration");
            }
        }
        return Map.copyOf(byRef);
    }

    private static void verifyControlParent(Path parent) throws IOException {
        if (Files.isSymbolicLink(parent)
                || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                || !parent.equals(parent.toRealPath())) {
            throw new AnalyticsBundleStoreException(
                    UNSAFE_PATH,
                    "Analytics Bundle control directory must be a real directory");
        }
    }

    private static void rejectControlSymlink(Path path) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw new AnalyticsBundleStoreException(
                    UNSAFE_PATH,
                    "Analytics Bundle control files must not be symbolic links");
        }
    }

    private static Path lockPath(AnalyticsBundleRegistration registration) {
        return controlPath(registration, ".analytics-write-lock");
    }

    private static Path journalPath(AnalyticsBundleRegistration registration) {
        Path path = controlPath(registration, ".analytics-write-journal");
        rejectControlSymlink(path);
        return path;
    }

    private static Path controlPath(
            AnalyticsBundleRegistration registration,
            String suffix) {
        String rootName = registration.root().getFileName().toString();
        return registration.root().resolveSibling("." + rootName + suffix);
    }

    private static String portable(Path root, Path target) {
        return root.relativize(target).toString().replace('\\', '/');
    }

    @FunctionalInterface
    private interface StoreOperation<T> {
        T run() throws IOException;
    }

    private record WriteJournal(
            String relativePath,
            byte[] previousManifest,
            byte[] previousArtifact) {
    }
}
