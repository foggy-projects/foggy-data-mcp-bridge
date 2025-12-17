package com.foggyframework.fsscript.exp;

import com.foggyframework.core.ex.RX;
import com.foggyframework.fsscript.closure.ExportVarDef;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Data
@Slf4j
public class ImportStaticClassExp implements ImportExp {
    Class clazz;
    /**
     * 导入的方法
     */
    List extNames;
    /**
     * 例如 import A ''
     */
    String name;

    public void setNames(List<String> names) {
        this.extNames = names;
    }

    public ImportStaticClassExp(String className) {
        try {
            this.clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw RX.throwB(e);
        }
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        FsscriptClosure fs = ee.getCurrentFsscriptClosure();
        if (extNames != null) {
            // import {方法名1,方法名2} from 'java:com.foggyframework.fsscript.support.ImportStaticClassTest'
            for (Object s : extNames) {

                if (s instanceof String) {
                    fs.setVarDef(new ExportVarDef((String) s, new ImportBeanExp.BeanMethodFunction(null, clazz, (String) s)));
                } else if (s instanceof AsExp) {
                    fs.setVarDef(new ExportVarDef(((AsExp) s).getAsTring(), new ImportBeanExp.BeanMethodFunction(null, clazz, ((AsExp) s).getValue())));
                } else {
                    throw new RuntimeException("" + s);
                }


            }
        } else if (name != null) {
            // import xxx from 'java:com.foggyframework.fsscript.support.ImportStaticClassTest'
            fs.setVarDef(new ExportVarDef(name, new StaticClassPropertyFunction(clazz)));
        } else {
            // import  '@com.foggyframework.fsscript.support.ImportStaticClassTest'
            //使用ImportStaticClassTest为名称
//            int idx = clazz.
            fs.setVarDef(new ExportVarDef(clazz.getSimpleName(), new StaticClassPropertyFunction(clazz)));
        }


        return clazz;
    }


    @Override
    public Class<?> getReturnType(ExpEvaluator ee) {
        return null;
    }

    @Override
    public void setExtNames(List<Object> names) {
        this.extNames = names;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    public static class StaticClassPropertyFunction implements PropertyFunction {
        Class beanClass;

        @Override
        public Object invoke(ExpEvaluator evaluator, String methodName, Object[] objects) {
            return ImportBeanExp.apply(beanClass, null, methodName, objects);
        }
    }

}