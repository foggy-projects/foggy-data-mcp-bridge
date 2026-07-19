package com.foggyframework.core.utils.file;

import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WatchServiceFileTracer 单元测试
 */
class WatchServiceFileTracerTest {

    private Path tempDir;
    private WatchServiceFileTracer tracer;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("watch-test-");
        tracer = WatchServiceFileTracer.getInstance();
    }

    @AfterEach
    void tearDown() throws IOException {
        // 清理临时目录
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // ignore
                        }
                    });
        }
    }

    @Test
    @DisplayName("WatchService 应该可用")
    void testWatchServiceAvailable() throws ReflectiveOperationException {
        assertTrue(tracer.isAvailable(), "WatchService 应该可用");

        Constructor<WatchServiceFileTracer> constructor =
                WatchServiceFileTracer.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        WatchServiceFileTracer isolated = constructor.newInstance();
        try {
            assertTrue(isolated.isAvailable(), "隔离 WatchService 应该可用");
        } finally {
            isolated.shutdown();
        }

        assertFalse(isolated.isAvailable(), "显式关闭后隔离 WatchService 应该不可用");
        assertTrue(tracer.isAvailable(), "隔离关闭不得污染全局 WatchService");
    }

    @Test
    @DisplayName("监听文件修改事件")
    void testFileModificationDetection() throws Exception {
        // 创建测试文件
        File testFile = tempDir.resolve("test.txt").toFile();
        Files.writeString(testFile.toPath(), "initial content");

        // 设置监听器
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean fileChanged = new AtomicBoolean(false);

        tracer.watchFile(testFile, new FileChangeListener() {
            @Override
            public void fileChanged(File source) {
                fileChanged.set(true);
                latch.countDown();
            }

            @Override
            public void fileDeleted(File f) {
            }
        });

        // 等待监听器注册完成
        Thread.sleep(500);

        // 修改文件
        Files.writeString(testFile.toPath(), "modified content");

        // 等待事件触发（最多 5 秒）
        boolean received = latch.await(5, TimeUnit.SECONDS);

        assertTrue(received, "应该收到文件修改事件");
        assertTrue(fileChanged.get(), "fileChanged 标志应该为 true");
    }

    @Test
    @DisplayName("监听文件删除事件")
    void testFileDeletionDetection() throws Exception {
        // 创建测试文件
        File testFile = tempDir.resolve("to-delete.txt").toFile();
        Files.writeString(testFile.toPath(), "content");

        // 设置监听器
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean fileDeleted = new AtomicBoolean(false);

        tracer.watchFile(testFile, new FileChangeListener() {
            @Override
            public void fileChanged(File source) {
            }

            @Override
            public void fileDeleted(File f) {
                fileDeleted.set(true);
                latch.countDown();
            }
        });

        // 等待监听器注册完成
        Thread.sleep(500);

        // 删除文件
        Files.delete(testFile.toPath());

        // 等待事件触发（最多 5 秒）
        boolean received = latch.await(5, TimeUnit.SECONDS);

        assertTrue(received, "应该收到文件删除事件");
        assertTrue(fileDeleted.get(), "fileDeleted 标志应该为 true");
    }

    @Test
    @DisplayName("监听目录中的新文件创建")
    void testDirectoryNewFileDetection() throws Exception {
        // 设置目录监听器
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> createdFileName = new AtomicReference<>();

        boolean success = tracer.watchDirectory(tempDir.toFile(), Set.of(".qm"), new DirectoryChangeListener() {
            @Override
            public void onFileCreated(File file) {
                createdFileName.set(file.getName());
                latch.countDown();
            }
        });

        assertTrue(success, "目录监听应该成功");

        // 等待监听器注册完成
        Thread.sleep(500);

        // 创建新文件
        File newFile = tempDir.resolve("new-model.qm").toFile();
        Files.writeString(newFile.toPath(), "new qm content");

        // 等待事件触发（最多 5 秒）
        boolean received = latch.await(5, TimeUnit.SECONDS);

        assertTrue(received, "应该收到文件创建事件");
        assertEquals("new-model.qm", createdFileName.get(), "文件名应该匹配");
    }

    @Test
    @DisplayName("目录监听应该过滤扩展名")
    void testDirectoryExtensionFilter() throws Exception {
        // 设置只监听 .qm 文件
        CountDownLatch qmLatch = new CountDownLatch(1);
        AtomicBoolean txtCreated = new AtomicBoolean(false);

        tracer.watchDirectory(tempDir.toFile(), Set.of(".qm"), new DirectoryChangeListener() {
            @Override
            public void onFileCreated(File file) {
                if (file.getName().endsWith(".txt")) {
                    txtCreated.set(true);
                }
                if (file.getName().endsWith(".qm")) {
                    qmLatch.countDown();
                }
            }
        });

        // 等待监听器注册完成
        Thread.sleep(500);

        // 创建 .txt 文件（应该被过滤）
        Files.writeString(tempDir.resolve("ignored.txt"), "txt content");

        // 等待一小段时间确认 .txt 不会触发
        Thread.sleep(1000);
        assertFalse(txtCreated.get(), ".txt 文件不应该触发回调");

        // 创建 .qm 文件（应该触发）
        Files.writeString(tempDir.resolve("model.qm"), "qm content");

        boolean received = qmLatch.await(5, TimeUnit.SECONDS);
        assertTrue(received, ".qm 文件应该触发回调");
    }

    @Test
    @DisplayName("不监听不存在的目录")
    void testWatchNonExistentDirectory() {
        File nonExistent = new File(tempDir.toFile(), "non-existent");
        boolean success = tracer.watchDirectory(nonExistent, null, file -> {
        });
        assertFalse(success, "不应该成功监听不存在的目录");
    }

    @Test
    @DisplayName("不监听文件（而非目录）")
    void testWatchFileAsDirectory() throws Exception {
        File file = tempDir.resolve("file.txt").toFile();
        Files.writeString(file.toPath(), "content");

        boolean success = tracer.watchDirectory(file, null, f -> {
        });
        assertFalse(success, "不应该成功将文件作为目录监听");
    }

    @Test
    @DisplayName("OVERFLOW 必须发出 authority-loss 并清理 watcher")
    void overflowMustSignalAuthorityLossAndCleanWatchers() {
        AtomicReference<WatchAuthorityLossReason> loss = new AtomicReference<>();
        DirectoryChangeListener listener = authorityListener(loss);
        WatchKey key = installFakeWatchKey(tempDir, listener);
        WatchEvent<?> overflow = mock(WatchEvent.class);
        when(overflow.kind()).thenReturn((WatchEvent.Kind) StandardWatchEventKinds.OVERFLOW);
        when(key.pollEvents()).thenReturn((List) List.of(overflow));
        when(key.reset()).thenReturn(true);

        tracer.processWatchKey(key);

        assertEquals(WatchAuthorityLossReason.EVENT_OVERFLOW, loss.get());
        assertFalse(directoryListeners().containsKey(normalize(tempDir)));
        assertFalse(watchedDirs().containsKey(normalize(tempDir)));
    }

    @Test
    @DisplayName("无效 WatchKey 必须发出 authority-loss 并清理 watcher")
    void invalidWatchKeyMustSignalAuthorityLossAndCleanWatchers() {
        AtomicReference<WatchAuthorityLossReason> loss = new AtomicReference<>();
        WatchKey key = installFakeWatchKey(tempDir, authorityListener(loss));
        when(key.pollEvents()).thenReturn(List.of());
        when(key.reset()).thenReturn(false);

        tracer.processWatchKey(key);

        assertEquals(WatchAuthorityLossReason.WATCH_KEY_INVALID, loss.get());
        assertFalse(directoryListeners().containsKey(normalize(tempDir)));
        assertFalse(watchedDirs().containsKey(normalize(tempDir)));
    }

    @Test
    @DisplayName("已监听子目录删除必须发出 authority-loss 并只清理子树")
    void watchedChildDeletionMustSignalAuthorityLossAndCleanOnlyChildTree()
            throws IOException {
        Path child = Files.createDirectory(tempDir.resolve("child"));
        AtomicReference<WatchAuthorityLossReason> childLoss = new AtomicReference<>();
        AtomicInteger parentDeleteCallbacks = new AtomicInteger();
        DirectoryChangeListener parentListener = new DirectoryChangeListener() {
            @Override
            public void onFileCreated(File file) {
            }

            @Override
            public void onFileDeleted(File file) {
                parentDeleteCallbacks.incrementAndGet();
            }
        };
        WatchKey parentKey = installFakeWatchKey(tempDir, parentListener);
        installFakeWatchKey(child, authorityListener(childLoss));
        WatchEvent<Path> deleted = mock(WatchEvent.class);
        when(deleted.kind()).thenReturn(StandardWatchEventKinds.ENTRY_DELETE);
        when(deleted.context()).thenReturn(child.getFileName());
        WatchEvent<Path> filteredDelete = mock(WatchEvent.class);
        when(filteredDelete.kind()).thenReturn(StandardWatchEventKinds.ENTRY_DELETE);
        when(filteredDelete.context()).thenReturn(Path.of("ignored.txt"));
        WatchEvent<Path> matchingDelete = mock(WatchEvent.class);
        when(matchingDelete.kind()).thenReturn(StandardWatchEventKinds.ENTRY_DELETE);
        when(matchingDelete.context()).thenReturn(Path.of("included.qm"));
        when(parentKey.pollEvents()).thenReturn(
                List.of(deleted), List.of(filteredDelete), List.of(matchingDelete));
        when(parentKey.reset()).thenReturn(true);

        tracer.processWatchKey(parentKey);
        assertEquals(1, parentDeleteCallbacks.get());
        parentDeleteCallbacks.set(0);

        directoryExtensionFilters().put(normalize(tempDir), Set.of(".qm"));
        try {
            tracer.processWatchKey(parentKey);
            assertEquals(0, parentDeleteCallbacks.get(),
                    "a deleted file outside the extension filter must not notify the listener");

            tracer.processWatchKey(parentKey);
            assertEquals(1, parentDeleteCallbacks.get(),
                    "a deleted file inside the extension filter must notify the listener");
        } finally {
            directoryExtensionFilters().remove(normalize(tempDir));
        }

        assertEquals(WatchAuthorityLossReason.WATCHED_DIRECTORY_DELETED, childLoss.get());
        assertTrue(directoryListeners().containsKey(normalize(tempDir)),
                "parent authority must remain registered");
        assertFalse(directoryListeners().containsKey(normalize(child)));
        assertFalse(watchedDirs().containsKey(normalize(child)));
    }

    @Test
    @DisplayName("移除文件监听")
    void testUnwatchFile() throws Exception {
        File testFile = tempDir.resolve("unwatch.txt").toFile();
        Files.writeString(testFile.toPath(), "content");

        AtomicBoolean changed = new AtomicBoolean(false);
        tracer.watchFile(testFile, new FileChangeListener() {
            @Override
            public void fileChanged(File source) {
                changed.set(true);
            }

            @Override
            public void fileDeleted(File f) {
            }
        });

        // 移除监听
        tracer.unwatchFile(testFile);

        // 等待一下
        Thread.sleep(500);

        // 修改文件
        Files.writeString(testFile.toPath(), "modified");

        // 等待，确认不会触发
        Thread.sleep(2000);
        assertFalse(changed.get(), "移除监听后不应该收到事件");

        assertEquals(0, tracer.unwatchFilesUnderRoot(null),
                "null root must not remove unrelated listeners");

        Path root = Files.createDirectories(tempDir.resolve("batch-root"));
        Path fileOnlyDir = Files.createDirectories(root.resolve("file-only"));
        Path directoryOwnedDir = Files.createDirectories(root.resolve("directory-owned"));
        Path outsideDir = Files.createDirectories(tempDir.resolve("outside-root"));
        Path fileOnly = Files.writeString(fileOnlyDir.resolve("model.qm"), "model");
        Path directoryOwned = Files.writeString(
                directoryOwnedDir.resolve("catalog.tm"), "catalog");
        Path outside = Files.writeString(outsideDir.resolve("outside.qm"), "outside");
        FileChangeListener batchListener = mock(FileChangeListener.class);
        fileListeners().put(normalize(fileOnly), batchListener);
        fileListeners().put(normalize(directoryOwned), batchListener);
        fileListeners().put(normalize(outside), batchListener);

        WatchKey fileOnlyKey = installFakeWatchKey(fileOnlyDir, file -> {
        });
        directoryListeners().remove(normalize(fileOnlyDir));
        WatchKey directoryOwnedKey = installFakeWatchKey(directoryOwnedDir, file -> {
        });
        WatchKey outsideKey = installFakeWatchKey(outsideDir, file -> {
        });
        directoryListeners().remove(normalize(outsideDir));

        int removed = tracer.unwatchFilesUnderRoot(root.toFile());

        assertEquals(2, removed);
        assertFalse(fileListeners().containsKey(normalize(fileOnly)));
        assertFalse(fileListeners().containsKey(normalize(directoryOwned)));
        assertTrue(fileListeners().containsKey(normalize(outside)),
                "a sibling root must retain its file listener");
        assertFalse(watchedDirs().containsKey(normalize(fileOnlyDir)),
                "a file-only directory must release its WatchKey");
        assertTrue(watchedDirs().containsKey(normalize(directoryOwnedDir)),
                "an explicit directory listener still owns its WatchKey");
        assertTrue(watchedDirs().containsKey(normalize(outsideDir)),
                "a sibling root must retain its WatchKey");
        verify(fileOnlyKey).cancel();
        verify(directoryOwnedKey, never()).cancel();
        verify(outsideKey, never()).cancel();

        tracer.unwatchDirectory(directoryOwnedDir.toFile());
        tracer.unwatchFile(outside.toFile());
    }

    private DirectoryChangeListener authorityListener(
            AtomicReference<WatchAuthorityLossReason> observed
    ) {
        return new DirectoryChangeListener() {
            @Override
            public void onFileCreated(File file) {
            }

            @Override
            public void onWatchAuthorityLost(
                    File directory,
                    WatchAuthorityLossReason reason
            ) {
                observed.compareAndSet(null, reason);
            }
        };
    }

    private WatchKey installFakeWatchKey(
            Path directory,
            DirectoryChangeListener listener
    ) {
        Path normalized = normalize(directory);
        directoryListeners().put(normalized, listener);
        WatchKey key = mock(WatchKey.class);
        WatchKey replaced = watchedDirs().put(normalized, key);
        if (replaced != null) {
            replaced.cancel();
            keyToDirMap().remove(replaced);
        }
        keyToDirMap().put(key, normalized);
        return key;
    }

    private Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    @SuppressWarnings("unchecked")
    private Map<Path, DirectoryChangeListener> directoryListeners() {
        return (Map<Path, DirectoryChangeListener>) readField("directoryListeners");
    }

    @SuppressWarnings("unchecked")
    private Map<Path, WatchKey> watchedDirs() {
        return (Map<Path, WatchKey>) readField("watchedDirs");
    }

    @SuppressWarnings("unchecked")
    private Map<Path, Set<String>> directoryExtensionFilters() {
        return (Map<Path, Set<String>>) readField("directoryExtensionFilters");
    }

    @SuppressWarnings("unchecked")
    private Map<WatchKey, Path> keyToDirMap() {
        return (Map<WatchKey, Path>) readField("keyToDirMap");
    }

    @SuppressWarnings("unchecked")
    private Map<Path, FileChangeListener> fileListeners() {
        return (Map<Path, FileChangeListener>) readField("fileListeners");
    }

    private Object readField(String name) {
        try {
            java.lang.reflect.Field field = WatchServiceFileTracer.class
                    .getDeclaredField(name);
            field.setAccessible(true);
            return field.get(tracer);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read tracer field " + name, e);
        }
    }
}
