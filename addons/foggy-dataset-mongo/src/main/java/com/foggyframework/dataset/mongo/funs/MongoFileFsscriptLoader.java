package com.foggyframework.dataset.mongo.funs;

import com.foggyframework.dataset.mongo.expression.MongoDbExpFactory;
import com.foggyframework.fsscript.loadder.AbstractFileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler;
import com.foggyframework.fsscript.loadder.FsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpFactory;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.utils.ExpUtils;
import org.springframework.context.ApplicationContext;

/**
 * MongoDB fsscript 文件加载器
 * 用于加载 .ms 格式的 MongoDB 脚本文件
 */
public class MongoFileFsscriptLoader extends AbstractFileFsscriptLoader {

    private static AbstractFileFsscriptLoader instance;

    public static AbstractFileFsscriptLoader getInstance() {
        return instance;
    }

    public static void setInstance(AbstractFileFsscriptLoader instance) {
        if (MongoFileFsscriptLoader.instance != null) {
            throw new UnsupportedOperationException("MongoFileFsscriptLoader只能被初始化一次");
        }
        MongoFileFsscriptLoader.instance = instance;
    }

    public MongoFileFsscriptLoader(ApplicationContext appCtx, FsscriptLoader parent, FsscriptFileChangeHandler changeHandler) {
        super(appCtx, parent, changeHandler);
    }

    @Override
    protected Exp compile(FsscriptClosureDefinition d, String str, ExpFactory expFactory) {
        return ExpUtils.compileEl(d, str, MongoDbExpFactory.MONGODB);
    }
}
