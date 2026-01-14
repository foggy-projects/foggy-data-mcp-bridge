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
                // 支持三种形式：
                // 1. filter(fn) - 自定义函数
                // 2. filter(Boolean) - 过滤掉 null/undefined/false 等假值
                // 3. filter(java.util.function.Function) - Java Function 接口
                Object arg0 = args[0];

                // 处理 Boolean 特殊情况
                if (arg0 == Boolean.class || "Boolean".equals(String.valueOf(arg0))) {
                    List result = new ArrayList();
                    for (Object o : ll) {
                        if (isTruthy(o)) {
                            result.add(o);
                        }
                    }
                    return new JsCommonInvokeResult(true, result);
                }

                // 处理 FsscriptFunction（必须在 java.util.function.Function 之前检查，
                // 因为 FsscriptFunction 也实现了 Function 接口）
                if (arg0 instanceof FsscriptFunction) {
                    FsscriptFunction predicate = (FsscriptFunction) arg0;
                    List result = new ArrayList();
                    for (Object o : ll) {
                        Object r = predicate.executeFunction(evaluator, o);
                        if (Boolean.TRUE.equals(r) || isTruthy(r)) {
                            result.add(o);
                        }
                    }
                    return new JsCommonInvokeResult(true, result);
                }

                // 处理 Java Function 接口
                if (arg0 instanceof java.util.function.Function) {
                    java.util.function.Function fn = (java.util.function.Function) arg0;
                    List result = new ArrayList();
                    for (Object o : ll) {
                        Object r = fn.apply(o);
                        if (Boolean.TRUE.equals(r) || isTruthy(r)) {
                            result.add(o);
                        }
                    }
                    return new JsCommonInvokeResult(true, result);
                }

                throw RX.throwB("filter参数必须是函数，实际类型: " + (arg0 == null ? "null" : arg0.getClass().getName()));
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

    /**
     * 判断值是否为"真值"（JavaScript truthy 语义）
     * <p>在 JavaScript 中，以下值被视为假值（falsy）：
     * <ul>
     *   <li>null</li>
     *   <li>undefined（在 Java 中对应 null）</li>
     *   <li>false</li>
     *   <li>0</li>
     *   <li>空字符串 ""</li>
     *   <li>NaN（在 Java 中为 Double.NaN）</li>
     * </ul>
     * 其他所有值都是真值（truthy）。
     */
    public static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            return d != 0 && !Double.isNaN(d);
        }
        if (value instanceof String) {
            return !((String) value).isEmpty();
        }
        // 其他对象类型都视为 truthy
        return true;
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
