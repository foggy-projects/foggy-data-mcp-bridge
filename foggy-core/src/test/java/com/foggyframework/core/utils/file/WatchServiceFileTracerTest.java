package com.foggyframework.core.utils.file;

import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

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
    void testWatchServiceAvailable() {
        assertTrue(tracer.isAvailable(), "WatchService 应该可用");
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
    }
}
