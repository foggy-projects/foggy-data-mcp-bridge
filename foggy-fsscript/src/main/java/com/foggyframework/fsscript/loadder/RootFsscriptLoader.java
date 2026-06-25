package com.foggyframework.fsscript.loadder;

import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;
import org.springframework.context.ApplicationContext;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
@Getter
public class RootFsscriptLoader extends FsscriptLoader {

    private final Map<String, Fsscript> path2Fsscript = new HashMap<>();
    private static final Object KEY = new Object();
    ApplicationContext appCtx;

    public RootFsscriptLoader(ApplicationContext appCtx) {
        super(null);this.appCtx = appCtx;
    }

    @Override
    public Fsscript findLoadFsscript(String path) {
        synchronized (KEY) {
            return path2Fsscript.get(path);
        }
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

    public List<Fsscript> removeByRootPath(String rootPath) {
        Path root = toComparablePath(rootPath);
        if (root == null) {
            return Collections.emptyList();
        }

        List<Fsscript> removed = new ArrayList<>();
        synchronized (KEY) {
            Iterator<Map.Entry<String, Fsscript>> iterator = path2Fsscript.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Fsscript> entry = iterator.next();
                Path scriptPath = toComparablePath(entry.getKey());
                if (scriptPath != null && scriptPath.startsWith(root)) {
                    removed.add(entry.getValue());
                    iterator.remove();
                }
            }
        }
        return removed;
    }

    private Path toComparablePath(String path) {
        if (path == null) {
            return null;
        }
        try {
            Path comparablePath;
            if (path.startsWith("file:")) {
                comparablePath = Paths.get(new URL(path).toURI());
            } else {
                comparablePath = Paths.get(path);
            }
            comparablePath = comparablePath.toAbsolutePath().normalize();
            if (Files.exists(comparablePath)) {
                return comparablePath.toRealPath();
            }
            return comparablePath;
        } catch (Exception e) {
            return null;
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
        Fsscript fscript;
        Fsscript[] loadedScripts;
        synchronized (KEY) {
            fscript = path2Fsscript.get(path);
            loadedScripts = path2Fsscript.values().toArray(new Fsscript[0]);
        }
        if (fscript == null) {
            return Collections.EMPTY_LIST;
        }

        List<Fsscript> list = new ArrayList<>();
        for (Fsscript item : loadedScripts) {
            if (item.hasImport(fscript)) {
                list.add(item);
            }
        }
        return list;
    }

    public Map<String, Fsscript> getPath2Fsscript() {
        synchronized (KEY) {
            return new HashMap<>(path2Fsscript);
        }
    }


}
