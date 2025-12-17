package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.closure.ExportVarDef;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.ExpFactory;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Data
@Slf4j
public class ImportDefaultFunTableExp implements ImportExp {
    /**
     * 导入的方法
     */
    List extNames;

    ExpFactory expFactory;

    public ImportDefaultFunTableExp(ExpFactory expFactory) {
        this.expFactory = expFactory;
    }

    @Override
    public void setName(String value) {

    }

    public void setNames(List<String> names) {

    }

    public ImportDefaultFunTableExp() {

    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        if (extNames != null) {
            FsscriptClosure fs = ee.getCurrentFsscriptClosure();
            for (Object s : extNames) {

                if (s instanceof AsExp) {
                    String name = ((AsExp) s).value;
                    String as = ((AsExp) s).getAsTring();
                    FunDef fd = expFactory.getFunctionSet().getFun(name);
                    fs.setVarDef(new ExportVarDef(as, fd));
                }

            }
        }
        return null;
    }


    @Override
    public Class<?> getReturnType(ExpEvaluator ee) {
        return null;
    }

    @Override
    public void setExtNames(List<Object> names) {
        this.extNames = names;
    }

}