package com.foggyframework.dataset.jdbc.model.impl;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;

public abstract class LoaderSupport {


    protected SystemBundlesContext systemBundlesContext;

    protected FileFsscriptLoader fileFsscriptLoader;

    public LoaderSupport(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader) {
        this.systemBundlesContext = systemBundlesContext;
        this.fileFsscriptLoader = fileFsscriptLoader;
    }

   protected Fsscript findFsscript(String name, String pref){
        if(!name.endsWith(pref)){
            name = name+"."+pref;
        }
       BundleResource br =systemBundlesContext.findResourceByName(name,true);

     return   fileFsscriptLoader.findLoadFsscript(br);

    }
}
