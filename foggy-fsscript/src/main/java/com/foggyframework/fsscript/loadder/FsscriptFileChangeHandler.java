package com.foggyframework.fsscript.loadder;

import com.foggyframework.core.utils.file.FileChangeListener;
import com.foggyframework.core.utils.file.FileTracer;
import com.foggyframework.fsscript.closure.file.ResourceFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class FsscriptFileChangeHandler implements FileChangeListener {

    FileTracer fileTracer = new FileTracer(this);

    RootFsscriptLoader rootFsscriptLoader;

    public FsscriptFileChangeHandler(RootFsscriptLoader rootFsscriptLoader) {
        this.rootFsscriptLoader = rootFsscriptLoader;
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
        fileTracer.addFile(file);
    }
}
