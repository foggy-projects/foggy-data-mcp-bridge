package com.foggyframework.fsscript.loadder;

import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;
import org.springframework.context.ApplicationContext;

import java.net.URL;
import java.util.*;
@Getter
public class RootFsscriptLoader extends FsscriptLoader {

    Map<String, Fsscript> path2Fsscript = new HashMap<>();
    private static final Object KEY = new Object();
    ApplicationContext appCtx;

    public RootFsscriptLoader(ApplicationContext appCtx) {
        super(null);this.appCtx = appCtx;
    }

    @Override
    public Fsscript findLoadFsscript(String path) {
        return path2Fsscript.get(path);
    }

    @Override
    public Fsscript setFsscript(String path, Fsscript fScript) {
        synchronized (KEY) {
            return path2Fsscript.put(path, fScript);
        }
    }

    @Override
    public Fsscript findLoadFsscript(URL fscriptPath) {
        synchronized (KEY) {
            String path = fscriptPath.getPath();
            return path2Fsscript.get(path);
        }
    }

    public Fsscript removePath(String path) {
        synchronized (KEY) {
            return path2Fsscript.remove(path);
        }
    }
    /**
     * 清空所有的缓存
     */
    public void clear() {

        synchronized (KEY) {
            path2Fsscript.clear();
        }
    }

    /**
     * 获取都有谁导入path
     *
     * @param path
     * @return
     */
    public List<Fsscript> getWhoImportMe(String path) {
        Fsscript fscript = path2Fsscript.get(path);
        if (fscript == null) {
            return Collections.EMPTY_LIST;
        }

        List<Fsscript> list = new ArrayList<>();
        for (Fsscript item : path2Fsscript.values().toArray(new Fsscript[0])) {
            if (item.hasImport(fscript)) {
                list.add(item);
            }
        }
        return list;
    }


}
