package com.foggyframework.fsscript.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.exp.MethodFinder;
import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.PropertyHolder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public abstract class PropertyProxySupport implements PropertyHolder, PropertyFunction {

    protected abstract Object getProxyObject();

    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {

        Object proxyObject = getProxyObject();
        Assert.notNull(proxyObject, "getProxyObject不能返回空,methodName: " + methodName);
        Method method = MethodFinder.findMethod(proxyObject.getClass(), methodName, args);
        if (method == null) {
            JsCommonInvokeResult c = tryCommonInvoke(evaluator, proxyObject, methodName, args);
            if (c == null) {
                throw RX.throwB("未能在" + proxyObject.getClass() + "中找到方法" + methodName + "，参数: " + Arrays.toString(args));
            }
            return c.result;
        }

        try {
            return method.invoke(proxyObject, args);
        } catch (IllegalAccessException e) {
            log.error(e.getMessage());
            throw RX.throwB(e);
        } catch (InvocationTargetException e) {
            log.error(e.getMessage());
            throw RX.throwB(e.getTargetException());
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JsCommonInvokeResult {
        boolean success;
        Object result;
    }

    public static JsCommonInvokeResult tryCommonInvoke(ExpEvaluator evaluator, Object proxyObject, String methodName, Object[] args) {
        if (proxyObject instanceof List) {
            List ll = (List) proxyObject;
            if (methodName.equals("includes")) {
                return invoke1(proxyObject, "contains", args);
            } else if (methodName.equals("push")) {
                // JavaScript push() 对应 Java add()
                return invoke1(proxyObject, "add", args);
            } else if (methodName.equals("filter")) {
                // JavaScript filter() - 过滤数组
                if (!(args[0] instanceof FsscriptFunction)) {
                    throw RX.throwB("filter参数必须是函数");
                }
                FsscriptFunction predicate = (FsscriptFunction) args[0];
                List result = new ArrayList();
                for (Object o : ll) {
                    Object r = predicate.executeFunction(evaluator, o);
                    if (Boolean.TRUE.equals(r)) {
                        result.add(o);
                    }
                }
                return new JsCommonInvokeResult(true, result);
            } else if (methodName.equals("map")) {
                if (!(args[0] instanceof FsscriptFunction)) {
                    throw RX.throwB("map参数必须是函数");
                }
                FsscriptFunction accept = (FsscriptFunction) args[0];
                List result = new ArrayList(ll.size());
                for (Object o : ll) {
                    result.add(accept.executeFunction(evaluator, o));
                }
                return new JsCommonInvokeResult(true, result);
            } else if (methodName.equals("join")) {
                if (args.length != 1) {
                    throw RX.throwB("join函数只能有一个参数");
                }
                Object join = args[0];
                join = join == null ? "null" : join;
                String result = StringUtils.join(ll, join.toString());
                return new JsCommonInvokeResult(true, result);
            }
        }
        return null;
    }

    public static JsCommonInvokeResult invoke1(Object proxyObject, String methodName, Object[] args) {

        Assert.notNull(proxyObject, "getProxyObject不能返回空,methodName: " + methodName);
        Method method = MethodFinder.findMethod(proxyObject.getClass(), methodName, args);

        try {
            return new JsCommonInvokeResult(true, method.invoke(proxyObject, args));
        } catch (IllegalAccessException e) {
            log.error(e.getMessage());
            throw RX.throwB(e);
        } catch (InvocationTargetException e) {
            log.error(e.getMessage());
            throw RX.throwB(e.getTargetException());
        }
    }

    @Override
    public Object getProperty(String name) {
        Object proxyObject = getProxyObject();
        Assert.notNull(proxyObject, "getProxyObject不能返回空,getProperty name: " + name);

        BeanInfoHelper beanInfoHelper = BeanInfoHelper.getClassHelper(proxyObject.getClass());

        return beanInfoHelper.getBeanProperty(name, true).getBeanValue(proxyObject);
    }
}
