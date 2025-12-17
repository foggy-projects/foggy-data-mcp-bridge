package com.foggyframework.fsscript.closure;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;

public class MethodFilterFsscriptClosure extends AbstractFsscriptClosure {
    String[] parameterNames;
    Object[] args;

    public MethodFilterFsscriptClosure(String[] parameterNames, Object[] args) {
        super(null);
        this.parameterNames = parameterNames;
        this.args = args;
    }

    public Object getArgByName(String name) {
        int i = 0;
        for (String p : parameterNames) {
            if (StringUtils.equals(p, name)) {
                return args[i];
            }
            i++;
        }
        throw RX.throwB("方法中没有参数: " + name);
    }

    public Object getArgByName(int idx) {
        return args[idx];
    }
}
