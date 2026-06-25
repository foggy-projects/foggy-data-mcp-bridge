package com.foggyframework.fsscript.loadder;

import com.foggyframework.core.utils.file.FileChangeListener;
import com.foggyframework.core.utils.file.WatchServiceFileTracer;
import com.foggyframework.fsscript.closure.file.ResourceFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class FsscriptFileChangeHandler implements FileChangeListener {

    /**
     * 使用 WatchService 实现的文件监听器（替代轮询方式）
     */
    private final WatchServiceFileTracer watchServiceTracer = WatchServiceFileTracer.getInstance();

    /**
     * 保留旧的 FileTracer 作为回退方案（当 WatchService 不可用时）
     */
    private final com.foggyframework.core.utils.file.FileTracer legacyTracer;

    RootFsscriptLoader rootFsscriptLoader;

    public FsscriptFileChangeHandler(RootFsscriptLoader rootFsscriptLoader) {
        this.rootFsscriptLoader = rootFsscriptLoader;
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
        clean(f);
    }

    public void clean(File f) {
        String filePath = ResourceFsscriptClosureDefinitionSpace.getResourcePath(f);
        List<Fsscript> removed = new ArrayList<>();
        log.debug("准备清理: " + filePath);
        removed.add(rootFsscriptLoader.removePath(filePath));

        List<Fsscript> ll = rootFsscriptLoader.getWhoImportMe(filePath);
        removed.addAll(ll);
        log.debug("一共找到: " + ll.size() + "个依赖它的Fsscript");

        for (Fsscript fScript : ll) {
            log.debug("开始移除:" + fScript);
            rootFsscriptLoader.removePath(fScript.getPath());
        }

        rootFsscriptLoader.getAppCtx().publishEvent(new FsscriptRemoveEvent(removed));
    }

    public void addFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        if (watchServiceTracer.isAvailable()) {
            // 使用 WatchService
            watchServiceTracer.watchFile(file, this);
        } else if (legacyTracer != null) {
            // 回退到轮询方式
            legacyTracer.addFile(file);
        }
    }

    public int removeFilesUnderRoot(String rootPath) {
        File root = toFileRoot(rootPath);
        if (root == null) {
            return 0;
        }

        int removedCount = 0;
        if (watchServiceTracer.isAvailable()) {
            removedCount += removeWatchServiceFilesUnderRoot(root);
        }
        if (legacyTracer != null) {
            removedCount += removeLegacyFilesUnderRoot(root);
        }

        if (removedCount > 0) {
            log.debug("已清理FSScript文件监听: root={}, count={}", root, removedCount);
        }
        return removedCount;
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

    private int removeWatchServiceFilesUnderRoot(File root) {
        Integer apiResult = invokeIntFileMethod(watchServiceTracer, "unwatchFilesUnderRoot", root);
        if (apiResult != null) {
            return apiResult;
        }

        @SuppressWarnings("unchecked")
        Map<Path, ?> fileListeners = (Map<Path, ?>) readField(watchServiceTracer, "fileListeners");
        Path rootPath = root.toPath().toAbsolutePath().normalize();
        int removedCount = 0;
        for (Path filePath : new ArrayList<>(fileListeners.keySet())) {
            if (filePath.startsWith(rootPath) && fileListeners.remove(filePath) != null) {
                removedCount++;
            }
        }
        cleanupWatchServiceDirectories(rootPath);
        return removedCount;
    }

    private void cleanupWatchServiceDirectories(Path rootPath) {
        @SuppressWarnings("unchecked")
        Map<Path, WatchKey> watchedDirs = (Map<Path, WatchKey>) readField(watchServiceTracer, "watchedDirs");
        @SuppressWarnings("unchecked")
        Map<WatchKey, Path> keyToDirMap = (Map<WatchKey, Path>) readField(watchServiceTracer, "keyToDirMap");
        @SuppressWarnings("unchecked")
        Map<Path, ?> fileListeners = (Map<Path, ?>) readField(watchServiceTracer, "fileListeners");
        @SuppressWarnings("unchecked")
        Map<Path, ?> directoryListeners = (Map<Path, ?>) readField(watchServiceTracer, "directoryListeners");

        for (Path dirPath : new ArrayList<>(watchedDirs.keySet())) {
            if (!dirPath.startsWith(rootPath) || hasListenerForDirectory(dirPath, fileListeners, directoryListeners)) {
                continue;
            }
            WatchKey key = watchedDirs.remove(dirPath);
            if (key != null) {
                key.cancel();
                keyToDirMap.remove(key);
            }
        }
    }

    private boolean hasListenerForDirectory(Path dirPath, Map<Path, ?> fileListeners, Map<Path, ?> directoryListeners) {
        if (directoryListeners.containsKey(dirPath)) {
            return true;
        }
        return fileListeners.keySet().stream()
                .map(Path::getParent)
                .anyMatch(dirPath::equals);
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
