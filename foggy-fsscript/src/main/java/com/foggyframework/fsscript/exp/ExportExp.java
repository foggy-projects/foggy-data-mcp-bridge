package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import com.foggyframework.fsscript.parser.spi.MapEntry;

import java.util.HashMap;
import java.util.Map;

public class ExportExp implements Exp {
    Exp exp;

    public ExportExp(Exp exp) {
        check(exp);
        this.exp = exp;
    }

    public static Map<String, Object> getExportMap(FsscriptClosure fs) {
//    FsscriptClosure fs = ee.getCurrentFsscriptClosure();
        Map<String, Object> exportMap = (Map<String, Object>) fs.getVar(FsscriptClosure.EXPORT_MAP_KEY);
        if (exportMap == null) {
            exportMap = new HashMap<>();
            fs.setVar(FsscriptClosure.EXPORT_MAP_KEY, exportMap);
        }
        return exportMap;
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {

        FsscriptClosure fs = ee.getCurrentFsscriptClosure();
//        Map<String, Object> exportMap = (Map<String, Object>) fs.getVar(FsscriptClosure.EXPORT_MAP_KEY);
//        if (exportMap == null) {
//            exportMap = new HashMap<>();
//            fs.setVar(FsscriptClosure.EXPORT_MAP_KEY, exportMap);
//        }

        Map<String, Object> exportMap = getExportMap(fs);

        Object exportValue;
        String exportName;
        if (exp instanceof IdExp) {
            exportName = ((IdExp) exp).getString();
            exportValue = exp.evalValue(ee);
        } else if (exp instanceof FunctionDefExp) {
            exportName = ((FunctionDefExp) exp).getName();
            exportValue = exp.evalValue(ee);
        } else if (exp instanceof VarExp) {
            exportName = ((VarExp) exp).value;
            exportValue = exp.evalValue(ee);
        } else if (exp instanceof MapExp) {
            for (MapEntry mapEntry : ((MapExp) exp).ll) {
//                Object v = mapEntry.getValue().evalValue(ee);
//                exportMap.put(mapEntry.getKey(), v);
                mapEntry.applyMap(exportMap,ee);
            }
            return exportMap;
        } else {
            throw new RuntimeException("不支持的export表达式: " + exp);
        }

        exportMap.put(exportName, exportValue);
        return exportValue;

    }

    private void check(Exp exp) {
        if (!((exp instanceof IdExp || exp instanceof FunctionDefExp || exp instanceof VarExp || exp instanceof MapExp))) {
            throw new RuntimeException("不支持的export表达式: " + exp);
        }
    }


    @Override
    public Class<?> getReturnType(ExpEvaluator ee) {
        return null;
    }

}