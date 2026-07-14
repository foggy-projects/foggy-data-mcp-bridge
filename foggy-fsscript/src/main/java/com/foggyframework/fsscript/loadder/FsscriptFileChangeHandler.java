package com.foggyframework.fsscript.loadder;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.core.utils.file.DirectoryChangeListener;
import com.foggyframework.core.utils.file.FileChangeListener;
import com.foggyframework.core.utils.file.WatchAuthorityLossReason;
import com.foggyframework.core.utils.file.WatchServiceFileTracer;
import com.foggyframework.fsscript.closure.file.ResourceFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistry;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
public class FsscriptFileChangeHandler implements FileChangeListener, DirectoryChangeListener {

    private static final Set<String> MANAGED_SOURCE_EXTENSIONS =
            Set.of(".tm", ".qm", ".fsscript");
    private static final int MAX_DIRECTORY_RECONCILIATION_ATTEMPTS = 8;

    /**
     * 使用 WatchService 实现的文件监听器（替代轮询方式）
     */
    private final WatchServiceFileTracer watchServiceTracer = WatchServiceFileTracer.getInstance();

    /**
     * 保留旧的 FileTracer 作为回退方案（当 WatchService 不可用时）
     */
    private final com.foggyframework.core.utils.file.FileTracer legacyTracer;

    RootFsscriptLoader rootFsscriptLoader;
    private final CommittedSourceRevisionRegistry sourceRevisionRegistry;
    private final Object directoryWatchLock = new Object();
    private final Map<Path, Map<String, Integer>> watchedRootRegistrations =
            new LinkedHashMap<>();
    private final Set<Path> watchedDirectories = ConcurrentHashMap.newKeySet();
    private final Set<Path> watchedFiles = ConcurrentHashMap.newKeySet();
    private final Set<Path> committedCreatedFiles = ConcurrentHashMap.newKeySet();
    private final Set<Path> lostDirectoryAuthorities = ConcurrentHashMap.newKeySet();
    private final DirectoryTreeScanner directoryTreeScanner;

    @FunctionalInterface
    interface DirectoryTreeScanner {
        List<Path> scan(Path root) throws IOException;
    }

    public FsscriptFileChangeHandler(RootFsscriptLoader rootFsscriptLoader) {
        this(rootFsscriptLoader, new CommittedSourceRevisionRegistry());
    }

    public FsscriptFileChangeHandler(
            RootFsscriptLoader rootFsscriptLoader,
            CommittedSourceRevisionRegistry sourceRevisionRegistry
    ) {
        this(rootFsscriptLoader, sourceRevisionRegistry, FsscriptFileChangeHandler::scanTree);
    }

    FsscriptFileChangeHandler(
            RootFsscriptLoader rootFsscriptLoader,
            CommittedSourceRevisionRegistry sourceRevisionRegistry,
            DirectoryTreeScanner directoryTreeScanner
    ) {
        this.rootFsscriptLoader = rootFsscriptLoader;
        this.sourceRevisionRegistry = Objects.requireNonNull(
                sourceRevisionRegistry, "sourceRevisionRegistry");
        this.directoryTreeScanner = Objects.requireNonNull(
                directoryTreeScanner, "directoryTreeScanner");
        // 只有当 WatchService 不可用时才使用旧的轮询方式
        this.legacyTracer = watchServiceTracer.isAvailable() ? null
                : new com.foggyframework.core.utils.file.FileTracer(this);

        if (watchServiceTracer.isAvailable()) {
            log.info("使用 WatchService 进行文件变化监听");
        } else {
            log.warn("WatchService 不可用，回退到轮询模式");
        }
    }


    @Override
    public void fileChanged(File source) {
        log.debug("收到文件变化: " + source);
        clean(source);
    }

    @Override
    public void fileDeleted(File f) {
        log.debug("收到文件删除: " + f);
        try {
            clean(f);
        } finally {
            Path normalized = normalize(f);
            watchedFiles.remove(normalized);
            committedCreatedFiles.remove(normalized);
        }
    }

    public void clean(File f) {
        commitAndPublish(f, namespacesForPath(normalize(f)));
    }

    private void commitAndPublish(File file, Collection<String> sourceOwnedNamespaces) {
        String filePath = ResourceFsscriptClosureDefinitionSpace.getResourcePath(file);
        Set<String> ownedNamespaces = canonicalNamespaces(sourceOwnedNamespaces);
        log.debug("准备清理: " + filePath);
        CommittedSourceRevisionRegistry.MutationCommit<List<Fsscript>> commit =
                sourceRevisionRegistry.commit(() -> {
                    List<Fsscript> removed = rootFsscriptLoader.removePathAndImporters(filePath);
                    TreeSet<String> namespaces = new TreeSet<>(ownedNamespaces);
                    namespaces.addAll(affectedNamespaces(removed));
                    boolean removedScopeKnown = !removed.isEmpty()
                            && removed.stream().allMatch(script -> namespaceOf(script) != null);
                    boolean scopeKnown = !ownedNamespaces.isEmpty() || removedScopeKnown;
                    return new CommittedSourceRevisionRegistry.ScopedMutation<>(
                            removed, scopeKnown, namespaces);
                });
        List<Fsscript> removed = commit.value();
        log.debug("一共移除 {} 个受影响的Fsscript", removed.size());
        TreeSet<String> resources = new TreeSet<>();
        resources.add(filePath);
        removed.stream()
                .filter(Objects::nonNull)
                .map(Fsscript::getPath)
                .filter(Objects::nonNull)
                .forEach(resources::add);
        rootFsscriptLoader.getAppCtx().publishEvent(new FsscriptRemoveEvent(
                removed,
                commit.scopeKnown(),
                commit.affectedNamespaces(),
                commit.committedRevisions(),
                List.copyOf(resources)));
    }

    private Set<String> canonicalNamespaces(Collection<String> namespaces) {
        TreeSet<String> canonical = new TreeSet<>();
        if (namespaces != null) {
            for (String namespace : namespaces) {
                canonical.add(namespace == null ? "" : namespace.trim());
            }
        }
        return Set.copyOf(canonical);
    }

    private Set<String> affectedNamespaces(List<Fsscript> scripts) {
        TreeSet<String> namespaces = new TreeSet<>();
        for (Fsscript script : scripts) {
            String namespace = namespaceOf(script);
            if (namespace != null) {
                namespaces.add(namespace);
            }
        }
        return Set.copyOf(namespaces);
    }

    private String namespaceOf(Fsscript script) {
        if (script == null
                || script.getFsscriptClosureDefinition() == null
                || script.getFsscriptClosureDefinition().getFsscriptClosureDefinitionSpace() == null
                || script.getFsscriptClosureDefinition().getFsscriptClosureDefinitionSpace().getBundle() == null
                || script.getFsscriptClosureDefinition().getFsscriptClosureDefinitionSpace()
                .getBundle().getDefinition() == null) {
            return null;
        }
        String namespace = script.getFsscriptClosureDefinition()
                .getFsscriptClosureDefinitionSpace()
                .getBundle()
                .getDefinition()
                .getNamespace();
        return namespace == null || namespace.trim().isEmpty() ? "" : namespace.trim();
    }

    /**
     * Registers a filesystem external bundle as a recursive source authority.
     * Existing source files are watched without publishing a second mutation;
     * the enclosing BundleAddedEvent already owns that source commit.
     */
    public boolean watchExternalBundle(String rootPath, String namespace) {
        File rootFile = toFileRoot(rootPath);
        if (rootFile == null || !rootFile.isDirectory() || !watchServiceTracer.isAvailable()) {
            log.error("无法为external bundle建立目录监听: root={}, watchServiceAvailable={}",
                    rootPath, watchServiceTracer.isAvailable());
            return false;
        }
        Path root = normalize(rootFile);
        String canonicalNamespace = namespace == null ? "" : namespace.trim();
        synchronized (directoryWatchLock) {
            Map<String, Integer> registrations = watchedRootRegistrations.get(root);
            if (registrations != null) {
                registrations.merge(canonicalNamespace, 1, Integer::sum);
                return true;
            }

            LinkedHashMap<String, Integer> firstRegistration = new LinkedHashMap<>();
            firstRegistration.put(canonicalNamespace, 1);
            watchedRootRegistrations.put(root, firstRegistration);
            if (registerDirectoryTree(root, false)) {
                return true;
            }

            watchedRootRegistrations.remove(root);
            cleanupUnusedWatchers(root);
            log.error("external bundle目录监听注册失败，拒绝watch=true: root={}, namespace={}",
                    root, canonicalNamespace);
            return false;
        }
    }

    /** Registers watch-enabled external bundles loaded from startup config. */
    public int watchExistingExternalBundles(SystemBundlesContext bundlesContext) {
        if (bundlesContext == null) {
            return 0;
        }
        int watched = 0;
        for (Bundle bundle : bundlesContext.getBundleList()) {
            if (!(bundle.getDefinition() instanceof ExternalBundleDefinition definition)
                    || !definition.isWatch()) {
                continue;
            }
            if (!watchExternalBundle(definition.getPath(), definition.getNamespace())) {
                throw new IllegalStateException(
                        "Unable to establish source directory authority for external bundle '"
                                + definition.getName() + "'");
            }
            watched++;
        }
        return watched;
    }

    /** Removes one root/namespace registration without disrupting shared roots. */
    public int unwatchExternalBundle(String rootPath, String namespace) {
        File rootFile = toFileRoot(rootPath);
        if (rootFile == null) {
            return 0;
        }
        Path root = normalize(rootFile);
        String canonicalNamespace = namespace == null ? "" : namespace.trim();
        synchronized (directoryWatchLock) {
            Map<String, Integer> registrations = watchedRootRegistrations.get(root);
            if (registrations == null) {
                return removeTrackedWatchersUnderRoot(root, false);
            }
            Integer count = registrations.get(canonicalNamespace);
            if (count == null) {
                return 0;
            }
            if (count > 1) {
                registrations.put(canonicalNamespace, count - 1);
                return 0;
            }
            registrations.remove(canonicalNamespace);
            if (!registrations.isEmpty()) {
                return 0;
            }
            watchedRootRegistrations.remove(root);
            lostDirectoryAuthorities.removeIf(path -> path.startsWith(root));
            return cleanupUnusedWatchers(root);
        }
    }

    @Override
    public void onFileCreated(File file) {
        if (file == null) {
            return;
        }
        if (file.isDirectory()) {
            boolean registered;
            synchronized (directoryWatchLock) {
                registered = registerDirectoryTree(normalize(file), true);
            }
            if (!registered) {
                // A watched subtree that cannot be followed makes its exact
                // namespace scope unknowable for future mutations. Commit an
                // unknown mutation so catalog admission fails closed.
                onWatchAuthorityLost(
                        file, WatchAuthorityLossReason.WATCH_KEY_INVALID);
            }
            return;
        }
        if (isManagedSource(file.toPath())) {
            processCreatedSource(file);
        }
    }

    @Override
    public void onFileModified(File file) {
        // The per-file listener owns modify events. Handling the directory
        // callback as well would commit the same source mutation twice.
    }

    @Override
    public void onFileDeleted(File file) {
        Path deleted = normalize(file);
        if (watchedDirectories.contains(deleted)) {
            onWatchAuthorityLost(
                    file, WatchAuthorityLossReason.WATCHED_DIRECTORY_DELETED);
        }
        // The per-file listener owns ordinary file delete events.
    }

    @Override
    public void onWatchAuthorityLost(
            File directory,
            WatchAuthorityLossReason reason
    ) {
        Path lostRoot = normalize(directory);
        if (lostRoot == null || !lostDirectoryAuthorities.add(lostRoot)) {
            return;
        }
        synchronized (directoryWatchLock) {
            removeTrackedWatchersUnderRoot(lostRoot, true);
        }
        log.error("external bundle source authority丢失，提交unknown scope: root={}, reason={}",
                lostRoot, reason);
        commitAndPublish(lostRoot.toFile(), Set.of());
    }

    private boolean registerDirectoryTree(Path root, boolean publishExistingSources) {
        if (root == null || !Files.isDirectory(root)) {
            return false;
        }
        // Establish the target directory WatchKey before taking any snapshot.
        // Events created during either scan are therefore observable, while the
        // reconciliation scan closes the pre-registration window for children.
        if (!registerDirectory(root)) {
            return false;
        }
        for (int attempt = 1;
                attempt <= MAX_DIRECTORY_RECONCILIATION_ATTEMPTS;
                attempt++) {
            DirectoryTreeSnapshot snapshot = scanDirectoryTree(root);
            if (snapshot == null) {
                return false;
            }
            List<Path> unregisteredDirectories = snapshot.directories().stream()
                    .filter(directory -> !watchedDirectories.contains(directory))
                    .toList();
            if (!unregisteredDirectories.isEmpty()) {
                if (!registerDirectories(unregisteredDirectories)) {
                    return false;
                }
                // The scan was taken before these directories had WatchKeys.
                // Rescan after registration; its source list is not authoritative.
                continue;
            }

            for (Path source : snapshot.sources()) {
                if (!tryAddFile(source.toFile())) {
                    Path parent = source.getParent() == null ? root : source.getParent();
                    onWatchAuthorityLost(
                            parent.toFile(),
                            WatchAuthorityLossReason.FILE_WATCH_REGISTRATION_FAILED);
                    return false;
                }
            }
            if (publishExistingSources) {
                snapshot.sources().forEach(
                        source -> publishCreatedSource(source.toFile()));
            }
            return true;
        }
        onWatchAuthorityLost(
                root.toFile(),
                WatchAuthorityLossReason.RECONCILIATION_LIMIT_EXCEEDED);
        return false;
    }

    private DirectoryTreeSnapshot scanDirectoryTree(Path root) {
        try {
            List<Path> entries = directoryTreeScanner.scan(root).stream()
                    .map(this::normalize)
                    .toList();
            List<Path> directories = entries.stream()
                    .filter(Files::isDirectory)
                    .sorted((left, right) -> Integer.compare(
                            left.getNameCount(), right.getNameCount()))
                    .toList();
            List<Path> sources = entries.stream()
                    .filter(Files::isRegularFile)
                    .filter(this::isManagedSource)
                    .sorted()
                    .toList();
            return new DirectoryTreeSnapshot(directories, sources);
        } catch (IOException | RuntimeException e) {
            log.error("扫描external bundle目录失败: {}", root, e);
            return null;
        }
    }

    private boolean registerDirectories(Collection<Path> directories) {
        for (Path directory : directories) {
            if (!registerDirectory(directory)) {
                return false;
            }
        }
        return true;
    }

    private boolean registerDirectory(Path directory) {
        if (!watchServiceTracer.watchDirectory(directory.toFile(), null, this)) {
            return false;
        }
        watchedDirectories.add(directory);
        lostDirectoryAuthorities.remove(directory);
        return true;
    }

    private record DirectoryTreeSnapshot(
            List<Path> directories,
            List<Path> sources
    ) {
    }

    private static List<Path> scanTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.toList();
        }
    }

    private void processCreatedSource(File source) {
        if (!tryAddFile(source)) {
            Path normalized = normalize(source);
            Path parent = normalized == null ? null : normalized.getParent();
            onWatchAuthorityLost(
                    (parent == null ? source : parent.toFile()),
                    WatchAuthorityLossReason.FILE_WATCH_REGISTRATION_FAILED);
            return;
        }
        publishCreatedSource(source);
    }

    private void publishCreatedSource(File source) {
        Path normalized = normalize(source);
        if (!committedCreatedFiles.add(normalized)) {
            return;
        }
        try {
            commitAndPublish(source, namespacesForPath(normalized));
        } catch (RuntimeException failure) {
            committedCreatedFiles.remove(normalized);
            throw failure;
        }
    }

    private boolean isManagedSource(Path source) {
        if (source == null || source.getFileName() == null) {
            return false;
        }
        String fileName = source.getFileName().toString().toLowerCase(Locale.ROOT);
        return MANAGED_SOURCE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private Set<String> namespacesForPath(Path source) {
        if (source == null) {
            return Set.of();
        }
        TreeSet<String> namespaces = new TreeSet<>();
        synchronized (directoryWatchLock) {
            for (Map.Entry<Path, Map<String, Integer>> registration
                    : watchedRootRegistrations.entrySet()) {
                if (source.startsWith(registration.getKey())) {
                    registration.getValue().forEach((namespace, count) -> {
                        if (count != null && count > 0) {
                            namespaces.add(namespace);
                        }
                    });
                }
            }
        }
        return Set.copyOf(namespaces);
    }

    private Path normalize(File file) {
        return file == null ? null : normalize(file.toPath());
    }

    private Path normalize(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }

    public void addFile(File file) {
        if (file == null) {
            return;
        }

        if (tryAddFile(file)) {
            return;
        }
        Path normalized = normalize(file);
        Path parent = normalized == null ? null : normalized.getParent();
        onWatchAuthorityLost(
                (parent == null ? file : parent.toFile()),
                WatchAuthorityLossReason.FILE_WATCH_REGISTRATION_FAILED);
    }

    private boolean tryAddFile(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        if (watchServiceTracer.isAvailable()) {
            if (!watchServiceTracer.tryWatchFile(file, this)) {
                return false;
            }
            watchedFiles.add(normalize(file));
            return true;
        }
        if (legacyTracer != null) {
            legacyTracer.addFile(file);
            return true;
        }
        return false;
    }

    public int removeFilesUnderRoot(String rootPath) {
        File root = toFileRoot(rootPath);
        if (root == null) {
            return 0;
        }

        int removedCount = 0;
        if (watchServiceTracer.isAvailable()) {
            Path normalizedRoot = normalize(root);
            synchronized (directoryWatchLock) {
                removedCount += removeTrackedWatchersUnderRoot(normalizedRoot, false);
            }
        }
        if (legacyTracer != null) {
            removedCount += removeLegacyFilesUnderRoot(root);
        }

        if (removedCount > 0) {
            log.debug("已清理FSScript文件监听: root={}, count={}", root, removedCount);
        }
        return removedCount;
    }

    private int cleanupUnusedWatchers(Path root) {
        return removeTrackedWatchersUnderRoot(root, false);
    }

    private int removeTrackedWatchersUnderRoot(Path root, boolean force) {
        int removedCount = 0;
        for (Path watchedFile : new ArrayList<>(watchedFiles)) {
            if (!watchedFile.startsWith(root)
                    || (!force && isCoveredByRegisteredRoot(watchedFile))) {
                continue;
            }
            watchServiceTracer.unwatchFile(watchedFile.toFile());
            if (watchedFiles.remove(watchedFile)) {
                removedCount++;
            }
            committedCreatedFiles.remove(watchedFile);
        }
        // Remove directory listeners after file listeners so the core tracer
        // can cancel WatchKeys that no longer have either listener type.
        for (Path watchedDirectory : new ArrayList<>(watchedDirectories)) {
            if (!watchedDirectory.startsWith(root)
                    || (!force && isCoveredByRegisteredRoot(watchedDirectory))) {
                continue;
            }
            watchServiceTracer.unwatchDirectory(watchedDirectory.toFile());
            if (watchedDirectories.remove(watchedDirectory)) {
                removedCount++;
            }
        }
        return removedCount;
    }

    private boolean isCoveredByRegisteredRoot(Path source) {
        return watchedRootRegistrations.entrySet().stream()
                .anyMatch(registration -> source.startsWith(registration.getKey())
                        && registration.getValue().values().stream()
                        .anyMatch(count -> count != null && count > 0));
    }

    private File toFileRoot(String rootPath) {
        if (rootPath == null || rootPath.trim().isEmpty()) {
            return null;
        }
        try {
            if (rootPath.startsWith("file:")) {
                return new File(new URL(rootPath).toURI());
            }
            return new File(rootPath);
        } catch (Exception e) {
            log.debug("无法将rootPath转换为文件路径，跳过监听清理: {}", rootPath, e);
            return null;
        }
    }

    private int removeLegacyFilesUnderRoot(File root) {
        Integer apiResult = invokeIntFileMethod(legacyTracer, "removeFilesUnderRoot", root);
        if (apiResult != null) {
            return apiResult;
        }

        Object scanner = readStaticField(com.foggyframework.core.utils.file.FileTracer.class, "scaner");
        Object lock = readField(scanner, "lock");
        Path rootPath = toComparablePath(root);
        synchronized (lock) {
            return removeLegacyFilesFromList(scanner, "files", rootPath)
                    + removeLegacyFilesFromList(scanner, "tmpFiles", rootPath);
        }
    }

    private int removeLegacyFilesFromList(Object scanner, String fieldName, Path rootPath) {
        @SuppressWarnings("unchecked")
        List<Object> listeners = (List<Object>) readField(scanner, fieldName);
        int before = listeners.size();
        listeners.removeIf(listener -> {
            Object tracer = readField(listener, "tracer");
            File file = (File) readField(listener, "file");
            return tracer == legacyTracer
                    && file != null
                    && toComparablePath(file).startsWith(rootPath);
        });
        return before - listeners.size();
    }

    private Integer invokeIntFileMethod(Object target, String methodName, File root) {
        try {
            Method method = target.getClass().getMethod(methodName, File.class);
            Object result = method.invoke(target, root);
            return result instanceof Integer ? (Integer) result : 0;
        } catch (NoSuchMethodException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            log.debug("调用文件监听清理方法失败: {}#{}", target.getClass().getName(), methodName, e);
            return 0;
        }
    }

    private Path toComparablePath(File file) {
        try {
            return file.toPath().toRealPath();
        } catch (IOException e) {
            return file.toPath().toAbsolutePath().normalize();
        }
    }

    private Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取文件监听字段失败: " + fieldName, e);
        }
    }

    private Object readStaticField(Class<?> targetClass, String fieldName) {
        try {
            Field field = targetClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取文件监听静态字段失败: " + fieldName, e);
        }
    }
}
