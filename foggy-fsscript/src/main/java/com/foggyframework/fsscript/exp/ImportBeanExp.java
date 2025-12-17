package com.foggyframework.fsscript.exp;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.ErrorUtils;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import com.foggyframework.fsscript.closure.ExportVarDef;
import com.foggyframework.fsscript.parser.spi.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Data
@Slf4j
public class ImportBeanExp implements ImportExp {
    String beanName;
    /**
     * 导入的方法
     */
    List extNames;
    /**
     * 例如 import A ''
     */
    String name;

    public ImportBeanExp(String beanName) {
        this.beanName = beanName;
    }

    @Override
    public void setNames(List<String> names) {
        this.extNames = names;
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        RX.notNull(ee.getApplicationContext(),"applicationContext不能为空");
        Object bean = ee.getApplicationContext().getBean(beanName);
        FsscriptClosure fs = ee.getCurrentFsscriptClosure();
        if (extNames != null) {

            for (Object s : extNames) {
                String name = null;
                String as = null;
                BeanInfoHelper beanInfoHelper = BeanInfoHelper.getClassHelper(bean.getClass());
                if (s instanceof String) {
                    name = (String) s;
                    as = name;

                } else if (s instanceof AsExp) {
                    name = ((AsExp) s).value;
                    as = ((AsExp) s).getAsTring();
                } else {
                    throw new RuntimeException(""+s);
                }

                BeanProperty beanProperty = beanInfoHelper.getBeanProperty(name);
                if (beanProperty != null && beanProperty.hasReader()) {
                    //属性?
                    fs.setVarDef(new ExportVarDef(as, beanProperty.getBeanValue(bean)));
                } else {
                    fs.setVarDef(new ExportVarDef(as, new BeanMethodFunction(bean, bean.getClass(), name)));
                }

            }
        } else if (name != null) {
            fs.setVarDef(new ExportVarDef(name, bean));
        } else {
            fs.setVarDef(new ExportVarDef(beanName, bean));
        }

        return bean;
    }


    @Override
    public void setExtNames(List<Object> names) {
        this.extNames = names;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BeanMethodFunction implements Function<Object[], Object> {

        Object bean;

        Class beanClass;

        String methodName;

        @Override
        public Object apply(Object[] objects) {
            return ImportBeanExp.apply(beanClass, bean, methodName, objects);
        }

    }

    public static final Object apply(Class beanClass, Object bean, String methodName, Object[] objects) {
        Method method = MethodFinder.findMethod(beanClass, methodName, objects);
        if (method == null) {
            //尝试使用自动匹配
            method = MethodFinder.autoFixArgsAndFindMethod(beanClass, methodName, objects);
        }
        if (method == null) {
            throw RX.throwB("未能在" + beanClass + "中找到方法" + methodName + "，参数: " + Arrays.toString(objects));
        }

        if (!method.isAccessible()) {
            method.setAccessible(true);
        }
        Object v;
        try {
            v = method.invoke(bean, objects);
        } catch (Throwable e) {
            String msg = getMsg(e);
            log.error("BeanMethodFunction ["+method+"] error: " + msg);
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
//            throw RX.throwB(method + "," + msg, null, e);
            throw ErrorUtils.toRuntimeException(e);
        }
        return v;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator ee) {
        return null;
    }

    private static String getMsg(Throwable e) {
        if (e instanceof NullPointerException) {
            return "NULL";
        }

        String msg = e.getMessage();
        Throwable ex = e;
        int times = 10;

        while (times > 0 && ex instanceof InvocationTargetException) {
            ex = ((InvocationTargetException) ex).getTargetException();
            times--;
            if (log.isDebugEnabled()) {
                ex.printStackTrace();
                ;
            }
        }

        if (ex != null) {
            msg = ex.getMessage();
        }
        return msg;

    }
}