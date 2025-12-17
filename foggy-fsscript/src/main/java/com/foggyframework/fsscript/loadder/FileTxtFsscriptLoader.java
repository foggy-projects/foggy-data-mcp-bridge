package com.foggyframework.fsscript.loadder;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.FileUtils;
import com.foggyframework.fsscript.closure.file.ResourceFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpFactory;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.support.FsscriptImpl;
import com.foggyframework.fsscript.utils.ExpUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import java.io.IOException;
import java.net.URL;

/**
 *
 */
public class FileTxtFsscriptLoader extends AbstractFileFsscriptLoader {
    private static FileTxtFsscriptLoader instance;

    public final static FileTxtFsscriptLoader getInstance() {
        return instance;
    }

    public static void setInstance(FileTxtFsscriptLoader instance) {
        if (FileTxtFsscriptLoader.instance != null) {
//            throw new UnsupportedOperationException("instance只能被初始化一次");
        }
        FileTxtFsscriptLoader.instance = instance;
    }

    public FileTxtFsscriptLoader(ApplicationContext appCtx, FsscriptLoader parent, FsscriptFileChangeHandler changeHandler) {
        super(appCtx, parent, changeHandler);
    }

    @Override
    protected Exp compile(FsscriptClosureDefinition d, String str, ExpFactory expFactory) {
        return ExpUtils.compile(d, str,expFactory);
    }


}
