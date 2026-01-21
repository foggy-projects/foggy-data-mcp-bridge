package com.foggyframework.dataset.db.model.engine.query_model;

import com.foggyframework.dataset.db.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.dataset.db.model.impl.model.DbTableModelImpl;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.fsscript.loadder.FsscriptRemoveEvent;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
public class DbModelFileChangeHandler implements ApplicationListener<FsscriptRemoveEvent> {

    QueryModelLoaderImpl jdbcQueryModelLoader;
    TableModelLoaderManagerImpl jdbcModelLoader;

    public DbModelFileChangeHandler(QueryModelLoaderImpl jdbcQueryModelLoader, TableModelLoaderManagerImpl jdbcModelLoader) {
        this.jdbcQueryModelLoader = jdbcQueryModelLoader;
        this.jdbcModelLoader = jdbcModelLoader;
        jdbcQueryModelLoader.setFileChangeHandler(this);
        jdbcModelLoader.setFileChangeHandler(this);

    }

    @Override
    public void onApplicationEvent(FsscriptRemoveEvent fsscriptRemoveEvent) {
        if (log.isDebugEnabled()) {
            log.debug("收到Fsscript变化事件");
            log.debug(fsscriptRemoveEvent.getRemovedFsscripts().toString());
        }

        // 由于目前只会在开发环境发生模型变化，因此我们简单粗暴的全清
        // 注意：这会清除所有命名空间的缓存
        log.debug("清除所有模型缓存（包括所有命名空间）");
        jdbcModelLoader.clearAll();
        jdbcQueryModelLoader.clearAll();
    }
}
