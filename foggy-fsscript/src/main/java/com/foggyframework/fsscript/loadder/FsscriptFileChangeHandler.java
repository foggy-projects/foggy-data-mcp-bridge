package com.foggyframework.fsscript.loadder;

import com.foggyframework.core.utils.file.FileChangeListener;
import com.foggyframework.core.utils.file.WatchServiceFileTracer;
import com.foggyframework.fsscript.closure.file.ResourceFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
}
