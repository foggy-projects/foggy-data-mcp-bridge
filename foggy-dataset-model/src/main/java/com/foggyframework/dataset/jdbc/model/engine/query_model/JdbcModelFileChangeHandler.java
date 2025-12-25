package com.foggyframework.dataset.jdbc.model.engine.query_model;

import com.foggyframework.dataset.jdbc.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.dataset.jdbc.model.impl.model.JdbcModelImpl;
import com.foggyframework.dataset.jdbc.model.spi.JdbcModel;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryModel;
import com.foggyframework.fsscript.loadder.FsscriptRemoveEvent;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
public class JdbcModelFileChangeHandler implements ApplicationListener<FsscriptRemoveEvent> {

    JdbcQueryModelLoaderImpl jdbcQueryModelLoader;
    TableModelLoaderManagerImpl jdbcModelLoader;

    public JdbcModelFileChangeHandler(JdbcQueryModelLoaderImpl jdbcQueryModelLoader, TableModelLoaderManagerImpl jdbcModelLoader) {
        this.jdbcQueryModelLoader = jdbcQueryModelLoader;
        this.jdbcModelLoader = jdbcModelLoader;
        jdbcQueryModelLoader.setFileChangeHandler(this);
        jdbcModelLoader.setFileChangeHandler(this);

    }

//    @Override
//    public void fileChanged(File source) {
//        log.debug("收到文件变化: "+source);
//        clean(source);
//    }
//
//    @Override
//    public void fileDeleted(File f) {
//        log.debug("收到文件删除: "+f);
//        clean(f);
//    }
//
//    public void clean(File f) {
//        String filePath = ResourceFsscriptClosureDefinitionSpace.getResourcePath(f);
//
//        log.debug("准备清理数据模型: "+filePath);
//      List   jdbcModelLoader.removePath(filePath);
//
//        List<Fsscript> ll = rootFsscriptLoader.getWhoImportMe(filePath);
//        log.debug("一共找到: "+ll.size()+"个依赖它的Fsscript");
//
//        for (Fsscript fScript : ll) {
//            log.debug("开始移除:"+fScript);
//            rootFsscriptLoader.removePath(fScript.getPath());
//        }
//
//
//    }
//
//    public void addFile(File file) {
//        fileTracer.addFile(file);
//    }

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
        Map<String, JdbcModel> mm = new HashMap<>(jdbcModelLoader.getName2JdbcModel());
        Map<String, JdbcQueryModel> qmm = new HashMap<>(jdbcQueryModelLoader.getName2JdbcQueryModel());
        List<JdbcModelImpl> removedTm = new ArrayList<>();
        for (Fsscript removedFsscript : fsscriptRemoveEvent.getRemovedFsscripts()) {
            for (Map.Entry<String, JdbcModel> stringJdbcModelEntry : mm.entrySet()) {
                JdbcModelImpl tm = stringJdbcModelEntry.getValue().getDecorate(JdbcModelImpl.class);
                if (tm != null && tm.getFScript().getPath().equals(removedFsscript.getPath())) {
                    if (log.isDebugEnabled()) {
                        log.debug("移除模型" + tm.getName());
                    }
                    removedTm.add(tm);
                    mm.remove(stringJdbcModelEntry.getKey());

                }
            }

            for (Map.Entry<String, JdbcQueryModel> stringJdbcQueryModelEntry : qmm.entrySet()) {
                JdbcQueryModelImpl qtm = stringJdbcQueryModelEntry.getValue().getDecorate(JdbcQueryModelImpl.class);
                if (qtm != null && qtm.getFsscript().getPath().equals(removedFsscript.getPath())) {
                    if (log.isDebugEnabled()) {
                        log.debug("s1.移除查询模型" + qtm.getName());
                    }
                    qmm.remove(stringJdbcQueryModelEntry.getKey());
                }

                for (JdbcModelImpl jdbcModel : removedTm) {
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
