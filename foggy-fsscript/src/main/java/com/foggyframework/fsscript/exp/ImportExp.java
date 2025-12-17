package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.closure.ExportVarDef;
import com.foggyframework.fsscript.parser.spi.*;

import java.util.List;
import java.util.Map;

public interface ImportExp extends Exp {


    void setName(String value);

    void setNames(List<String> names);

    /**
     * 支持AsExp
     * @param names
     */
    void setExtNames(List<Object> names);
}