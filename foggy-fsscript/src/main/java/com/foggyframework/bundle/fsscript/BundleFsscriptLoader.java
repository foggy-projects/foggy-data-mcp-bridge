package com.foggyframework.bundle.fsscript;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BundleFsscriptLoader {
    FileFsscriptLoader fileFsscriptLoader;

    public BundleFsscriptLoader(FileFsscriptLoader fileFsscriptLoader) {
        this.fileFsscriptLoader = fileFsscriptLoader;
    }

    /**
     * 加载脚本
     *
     * @param bundle
     * @param fscriptPath
     * @return
     */
    public BundleFsscript loadBundleFsscript(Bundle bundle, String fscriptPath) {

//         fileFsscriptLoader.findLoadFsscript(fscriptPath);

        return null;
    }

}
