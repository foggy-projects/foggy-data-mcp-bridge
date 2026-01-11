package com.foggyframework.dataset.vector.funs;

import com.foggyframework.fsscript.loadder.AbstractFileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler;
import com.foggyframework.fsscript.loadder.FsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpFactory;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.utils.ExpUtils;
import org.springframework.context.ApplicationContext;

/**
 * 向量数据库 Fsscript 加载器
 * 类似于 MongoFileFsscriptLoader
 */
public class VectorFileFsscriptLoader extends AbstractFileFsscriptLoader {

    private static AbstractFileFsscriptLoader instance;

    public static AbstractFileFsscriptLoader getInstance() {
        return instance;
    }

    public static void setInstance(AbstractFileFsscriptLoader instance) {
        if (VectorFileFsscriptLoader.instance != null) {
            throw new UnsupportedOperationException("VectorFileFsscriptLoader只能被初始化一次");
        }
        VectorFileFsscriptLoader.instance = instance;
    }

    public VectorFileFsscriptLoader(ApplicationContext appCtx, FsscriptLoader parent, FsscriptFileChangeHandler changeHandler) {
        super(appCtx, parent, changeHandler);
    }

    @Override
    protected Exp compile(FsscriptClosureDefinition d, String str, ExpFactory expFactory) {
        return ExpUtils.compileEl(d, str, expFactory);
    }
}
