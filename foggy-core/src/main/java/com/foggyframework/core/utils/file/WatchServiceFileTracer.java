/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved.
 * You must accept the terms of that agreement to use this software.
 ******************************************************************************/
package com.foggyframework.core.utils.file;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 基于 JDK WatchService 的文件变化跟踪器
 *
 * <p>相比轮询方式的 {@link FileTracer}，此实现：
 * <ul>
 *   <li>使用操作系统原生的文件系统事件通知</li>
 *   <li>更低的 CPU 开销</li>
 *   <li>更快的响应速度</li>
 *   <li>支持监听目录下的新文件创建</li>
 * </ul>
 *
 * <p>限制：
 * <ul>
 *   <li>只能监听真实文件系统目录，不支持 JAR 内部资源</li>
 *   <li>Windows/Linux/Mac 行为可能略有差异</li>
 * </ul>
 *
 * @author Foggy
 * @since 2.0.0
 */
@Slf4j
public class WatchServiceFileTracer {

    private static final WatchServiceFileTracer INSTANCE = new WatchServiceFileTracer();

    private WatchService watchService;
    private final ExecutorService executor;
    private volatile boolean running = true;

    /**
     * 目录 -> WatchKey 映射
     */
    private final Map<Path, WatchKey> watchedDirs = new ConcurrentHashMap<>();

    /**
     * WatchKey -> 目录 映射（反向查找）
     */
    private final Map<WatchKey, Path> keyToDirMap = new ConcurrentHashMap<>();

    /**
     * 文件路径 -> 监听器 映射
     */
    private final Map<Path, FileChangeListener> fileListeners = new ConcurrentHashMap<>();

    /**
     * 目录路径 -> 目录监听器 映射（用于监听新文件创建）
     */
    private final Map<Path, DirectoryChangeListener> directoryListeners = new ConcurrentHashMap<>();

    /**
     * 目录监听的文件扩展名过滤器
     */
    private final Map<Path, Set<String>> directoryExtensionFilters = new ConcurrentHashMap<>();

    private WatchServiceFileTracer() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            log.error("创建 WatchService 失败，将回退到轮询模式", e);
            this.watchService = null;
        }

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "WatchServiceFileTracer");
            t.setDaemon(true);
            return t;
        });

        if (watchService != null) {
            executor.submit(this::processEvents);
        }

        // 注册 JVM 关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    public static WatchServiceFileTracer getInstance() {
        return INSTANCE;
    }

    /**
     * 添加文件监听
     *
     * @param file     要监听的文件
     * @param listener 变化监听器
     */
    public void watchFile(File file, FileChangeListener listener) {
        if (watchService == null || file == null || !file.exists()) {
            log.debug("无法监听文件: {}", file);
            return;
        }

        Path filePath = file.toPath().toAbsolutePath().normalize();
        Path dirPath = filePath.getParent();

        if (dirPath == null) {
            log.warn("无法获取文件的父目录: {}", file);
            return;
        }

        // 注册监听器
        fileListeners.put(filePath, listener);

        // 确保目录被监听
        watchDirectory(dirPath);

        log.debug("已添加文件监听: {}", filePath);
    }

    /**
     * 添加目录监听（用于监听新文件创建）
     *
     * @param directory  要监听的目录
     * @param extensions 关注的文件扩展名（如 ".qm", ".tm"），null 表示所有文件
     * @param listener   变化监听器
     * @return true 如果成功添加监听，false 如果目录不存在或不是真实目录
     */
    public boolean watchDirectory(File directory, Set<String> extensions, DirectoryChangeListener listener) {
        if (watchService == null) {
            log.debug("WatchService 不可用，无法监听目录: {}", directory);
            return false;
        }

        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            log.debug("目录不存在或不是目录: {}", directory);
            return false;
        }

        Path dirPath = directory.toPath().toAbsolutePath().normalize();

        // 注册目录监听器
        directoryListeners.put(dirPath, listener);
        if (extensions != null && !extensions.isEmpty()) {
            directoryExtensionFilters.put(dirPath, extensions);
        }

        // 确保目录被监听
        watchDirectory(dirPath);

        log.info("已添加目录监听: {}, 扩展名过滤: {}", dirPath, extensions);
        return true;
    }

    /**
     * 监听目录
     */
    private void watchDirectory(Path dirPath) {
        if (watchedDirs.containsKey(dirPath)) {
            return;
        }

        try {
            WatchKey key = dirPath.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);

            watchedDirs.put(dirPath, key);
            keyToDirMap.put(key, dirPath);

            log.debug("已注册目录监听: {}", dirPath);
        } catch (IOException e) {
            log.warn("注册目录监听失败: {}, error: {}", dirPath, e.getMessage());
        }
    }

    /**
     * 移除文件监听
     */
    public void unwatchFile(File file) {
        if (file == null) return;
        Path filePath = file.toPath().toAbsolutePath().normalize();
        fileListeners.remove(filePath);
        log.debug("已移除文件监听: {}", filePath);
    }

    /**
     * 移除指定根目录下所有文件监听。
     *
     * @return 被移除的文件监听数量
     */
    public int unwatchFilesUnderRoot(File root) {
        if (root == null) {
            return 0;
        }

        Path rootPath = root.toPath().toAbsolutePath().normalize();
        List<Path> removedFiles = new ArrayList<>();
        for (Path filePath : fileListeners.keySet()) {
            if (filePath.startsWith(rootPath) && fileListeners.remove(filePath) != null) {
                removedFiles.add(filePath);
            }
        }

        cleanupWatchedDirectoriesUnderRoot(rootPath);

        if (!removedFiles.isEmpty()) {
            log.debug("已移除根目录下文件监听: root={}, count={}", rootPath, removedFiles.size());
        }
        return removedFiles.size();
    }

    /**
     * 移除目录监听
     */
    public void unwatchDirectory(File directory) {
        if (directory == null) return;
        Path dirPath = directory.toPath().toAbsolutePath().normalize();
        directoryListeners.remove(dirPath);
        directoryExtensionFilters.remove(dirPath);

        // 如果该目录下没有其他文件监听，则取消监听
        if (!hasListenerForDirectory(dirPath)) {
            WatchKey key = watchedDirs.remove(dirPath);
            if (key != null) {
                key.cancel();
                keyToDirMap.remove(key);
            }
        }

        log.debug("已移除目录监听: {}", dirPath);
    }

    private void cleanupWatchedDirectoriesUnderRoot(Path rootPath) {
        for (Path dirPath : new ArrayList<>(watchedDirs.keySet())) {
            if (!dirPath.startsWith(rootPath) || hasListenerForDirectory(dirPath)) {
                continue;
            }
            WatchKey key = watchedDirs.remove(dirPath);
            if (key != null) {
                key.cancel();
                keyToDirMap.remove(key);
            }
        }
    }

    private boolean hasListenerForDirectory(Path dirPath) {
        if (directoryListeners.containsKey(dirPath)) {
            return true;
        }
        return fileListeners.keySet().stream()
                .map(Path::getParent)
                .anyMatch(dirPath::equals);
    }

    /**
     * 事件处理循环
     */
    private void processEvents() {
        log.info("WatchServiceFileTracer 事件处理线程已启动");

        while (running) {
            WatchKey key;
            try {
                key = watchService.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            }

            if (key == null) {
                continue;
            }

            Path dir = keyToDirMap.get(key);
            if (dir == null) {
                key.cancel();
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    log.warn("文件系统事件溢出，可能丢失部分事件");
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path fileName = ev.context();
                Path fullPath = dir.resolve(fileName).normalize();
                File file = fullPath.toFile();

                try {
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        handleFileCreated(dir, fullPath, file);
                    } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        handleFileModified(fullPath, file);
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        handleFileDeleted(fullPath, file);
                    }
                } catch (Exception e) {
                    log.error("处理文件事件时出错: {} - {}", fullPath, e.getMessage(), e);
                }
            }

            // 重置 key，如果失败则该目录不再可访问
            boolean valid = key.reset();
            if (!valid) {
                log.warn("目录不再可访问，移除监听: {}", dir);
                watchedDirs.remove(dir);
                keyToDirMap.remove(key);
            }
        }

        log.info("WatchServiceFileTracer 事件处理线程已停止");
    }

    private void handleFileCreated(Path dir, Path fullPath, File file) {
        log.debug("检测到文件创建: {}", fullPath);

        // 检查目录监听器
        DirectoryChangeListener dirListener = directoryListeners.get(dir);
        if (dirListener != null) {
            Set<String> extensions = directoryExtensionFilters.get(dir);
            if (extensions == null || matchesExtension(file.getName(), extensions)) {
                log.debug("通知目录监听器 - 新文件: {}", fullPath);
                dirListener.onFileCreated(file);
            }
        }
    }

    private void handleFileModified(Path fullPath, File file) {
        log.debug("检测到文件修改: {}", fullPath);

        // 检查文件监听器
        FileChangeListener listener = fileListeners.get(fullPath);
        if (listener != null) {
            listener.fileChanged(file);
        }

        // 也通知目录监听器
        Path dir = fullPath.getParent();
        DirectoryChangeListener dirListener = directoryListeners.get(dir);
        if (dirListener != null) {
            Set<String> extensions = directoryExtensionFilters.get(dir);
            if (extensions == null || matchesExtension(file.getName(), extensions)) {
                dirListener.onFileModified(file);
            }
        }
    }

    private void handleFileDeleted(Path fullPath, File file) {
        log.debug("检测到文件删除: {}", fullPath);

        // 检查文件监听器
        FileChangeListener listener = fileListeners.get(fullPath);
        if (listener != null) {
            listener.fileDeleted(file);
            // 文件删除后移除监听
            fileListeners.remove(fullPath);
        }

        // 也通知目录监听器
        Path dir = fullPath.getParent();
        DirectoryChangeListener dirListener = directoryListeners.get(dir);
        if (dirListener != null) {
            Set<String> extensions = directoryExtensionFilters.get(dir);
            if (extensions == null || matchesExtension(file.getName(), extensions)) {
                dirListener.onFileDeleted(file);
            }
        }
    }

    private boolean matchesExtension(String fileName, Set<String> extensions) {
        for (String ext : extensions) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查 WatchService 是否可用
     */
    public boolean isAvailable() {
        return watchService != null && running;
    }

    /**
     * 关闭监听服务
     */
    public void shutdown() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("关闭 WatchService 时出错: {}", e.getMessage());
            }
        }

        log.info("WatchServiceFileTracer 已关闭");
    }
}
