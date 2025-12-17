package com.foggyframework.fsscript.exp;

import com.foggyframework.core.ex.RX;
import com.foggyframework.fsscript.closure.ExportVarDef;
import com.foggyframework.fsscript.parser.spi.*;

import java.util.List;
import java.util.Map;

public class ImportFsscriptExp implements ImportExp {
    String file;

    Fsscript fsscript;
    /**
     * 例如 import A ''
     */
    String name;

    List extNames;

    public void setFile(String file) {
        this.file = file;
    }

    public void setFsscript(Fsscript fsscript) {
        this.fsscript = fsscript;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

//    public List<String> getNames() {
//        return names;
//    }

    public void setNames(List<String> names) {
        this.extNames = names;
    }

    @Override
    public void setExtNames(List<Object> names) {
        this.extNames = names;
    }

    public String getFile() {
        return file;
    }

    public Fsscript getFsscript() {
        return fsscript;
    }

    public ImportFsscriptExp(String file) {
        this.file = file;
    }

    public boolean hasImport(Fsscript importFscript, boolean containMe, boolean deep) {
        if (fsscript == null) {
            //还执行eval呢，没啥事
            return false;
        }
        if (containMe) {
            if (importFscript == fsscript) {
                return true;
            }
        }
        if (deep) {
            return fsscript.hasImport(importFscript);
        } else {
            return false;
        }

    }

    @Override
    public Object evalValue(ExpEvaluator ee) {

        //得到当前闭包对象FoggyClosure
        //得到对应的定义FoggyClosureDefinition
        FsscriptClosureDefinitionSpace space = ee.getCurrentFsscriptClosure().getBeanDefinitionSpace();

        //通过space的方法**加载Fscript
        fsscript = space.loadFsscript(ee, file);
        try {
            ImportedFsscript importedFsscript = ee.addImport(ee,fsscript.getPath(), fsscript);

            if (name != null) {
                FsscriptClosure fs = ee.getCurrentFsscriptClosure();
                fs.setVarDef(new ExportVarDef(name, importedFsscript.getExportMap()));
            }
            if (extNames != null) {
                Map<String, Object> exportMap = importedFsscript.getExportMap();
                FsscriptClosure fs = ee.getCurrentFsscriptClosure();
                for (Object s : extNames) {
                    if (s instanceof String) {
                        fs.setVarDef(new ExportVarDef((String) s, exportMap.get(s)));
                    } else if (s instanceof AsExp) {
                        fs.setVarDef(new ExportVarDef(((AsExp) s).getAsTring(), exportMap.get(((AsExp) s).getValue())));
                    }

                }
            }

            return importedFsscript;
        } catch (Throwable t) {
            throw RX.throwB("导入" + fsscript.getPath() + "出现异常", null, t);
        }
    }


    @Override
    public Class<?> getReturnType(ExpEvaluator ee) {
        return null;
    }

}