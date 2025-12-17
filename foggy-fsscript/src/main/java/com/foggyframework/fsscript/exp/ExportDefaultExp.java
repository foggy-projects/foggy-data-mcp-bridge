package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;

import java.util.Map;

/**
 * 处理 export default 表达式
 * 将值存储在 exportMap 的 "default" key 下
 */
public class ExportDefaultExp implements Exp {

    public static final String DEFAULT_KEY = "default";

    private final Exp exp;

    public ExportDefaultExp(Exp exp) {
        this.exp = exp;
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        FsscriptClosure fs = ee.getCurrentFsscriptClosure();
        Map<String, Object> exportMap = ExportExp.getExportMap(fs);

        // 计算表达式的值并存储在 "default" key 下
        Object value = exp.evalValue(ee);
        exportMap.put(DEFAULT_KEY, value);

        return value;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator ee) {
        return null;
    }
}
