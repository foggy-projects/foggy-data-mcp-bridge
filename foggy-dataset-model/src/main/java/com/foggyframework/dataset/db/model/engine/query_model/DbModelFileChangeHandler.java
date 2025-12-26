package com.foggyframework.dataset.db.model.engine.query_model;

import com.foggyframework.dataset.db.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.dataset.db.model.impl.model.DbTableModelImpl;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.QueryModel;
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
        if(true){
            log.debug("由于目前只会在开发环境发生模型变化 ，因此我们先简单粗暴的全清");
            jdbcModelLoader.clearAll();
            jdbcQueryModelLoader.clearAll();
            return;
        }
        Map<String, TableModel> mm = new HashMap<>(jdbcModelLoader.getName2JdbcModel());
        Map<String, QueryModel> qmm = new HashMap<>(jdbcQueryModelLoader.getName2JdbcQueryModel());
        List<DbTableModelImpl> removedTm = new ArrayList<>();
        for (Fsscript removedFsscript : fsscriptRemoveEvent.getRemovedFsscripts()) {
            for (Map.Entry<String, TableModel> stringJdbcModelEntry : mm.entrySet()) {
                DbTableModelImpl tm = stringJdbcModelEntry.getValue().getDecorate(DbTableModelImpl.class);
                if (tm != null && tm.getFScript().getPath().equals(removedFsscript.getPath())) {
                    if (log.isDebugEnabled()) {
                        log.debug("移除模型" + tm.getName());
                    }
                    removedTm.add(tm);
                    mm.remove(stringJdbcModelEntry.getKey());

                }
            }

            for (Map.Entry<String, QueryModel> stringJdbcQueryModelEntry : qmm.entrySet()) {
                DbQueryModelImpl qtm = stringJdbcQueryModelEntry.getValue().getDecorate(DbQueryModelImpl.class);
                if (qtm != null && qtm.getFsscript().getPath().equals(removedFsscript.getPath())) {
                    if (log.isDebugEnabled()) {
                        log.debug("s1.移除查询模型" + qtm.getName());
                    }
                    qmm.remove(stringJdbcQueryModelEntry.getKey());
                }

                for (DbTableModelImpl jdbcModel : removedTm) {
                    if(qtm.getJdbcModel().getName().equals(jdbcModel.getName())){
                        if (log.isDebugEnabled()) {
                            log.debug("s2.移除查询模型" + qtm.getName());
                        }
                    }
                }
            }

        }

        jdbcModelLoader.setName2JdbcModel(mm);
        jdbcQueryModelLoader.setName2JdbcQueryModel(qmm);

    }
}
