package com.foggyframework.fsscript.exp;

import com.foggyframework.bean.copy.utils.Map2BeanUtils;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 方法查找工具类
 * 从 PropertyFunctionExp 迁移
 */
public class MethodFinder {

    /**
     * 根据方法名和参数查找匹配的方法
     */
    public static Method findMethod(Class<?> cls, String methodName, Object[] args) {
        Method[] mm = BeanInfoHelper.getClassHelper(cls).getMethods(methodName);
        Method m = null;
        int lastScore = -1;
        X:
        for (Method m1 : mm) {
            int matchScore = 0;
            if (m1.getParameterTypes().length != args.length) {
                continue;
            }

            int j = 0;
            for (Class<?> pt : m1.getParameterTypes()) {
                if (args[j] == null) {
                    matchScore = matchScore + 1;
                    continue;
                }
                if (pt.isPrimitive()) {
                    pt = BeanInfoHelper.getPrimitiveClass(pt);
                }

                if (pt == args[j].getClass()) {
                    matchScore = matchScore + 2;
                } else if (!pt.isInstance(args[j])) {
                    continue X;
                } else {
                    matchScore = matchScore + 1;
                }

                j++;
            }
            if (lastScore < matchScore) {
                m = m1;
                lastScore = matchScore;
            }
        }
        if (m != null && !m.isAccessible()) {
            m.setAccessible(true);
        }
        return m;
    }

    /**
     * 尝试将 map 自动转换成 bean 后查找方法
     */
    public static Method autoFixArgsAndFindMethod(Class<?> cls, String methodName, Object[] args) {
        Method[] mm = BeanInfoHelper.getClassHelper(cls).getMethods(methodName);
        Method m = null;

        int lastScore = -1;
        X:
        for (Method m1 : mm) {
            int matchScore = 0;
            if (m1.getParameterTypes().length != args.length) {
                continue;
            }

            int j = 0;
            for (Class<?> pt : m1.getParameterTypes()) {
                if (args[j] == null) {
                    matchScore = matchScore + 1;
                    continue;
                }
                if (pt.isPrimitive()) {
                    pt = BeanInfoHelper.getPrimitiveClass(pt);
                }

                if (pt == args[j].getClass()) {
                    matchScore = matchScore + 2;
                } else if (!pt.isInstance(args[j])) {
                    if (args[j] instanceof Map && !BeanInfoHelper.isBaseClass(pt)) {
                        // 尝试转换
                        args[j] = Map2BeanUtils.fromMap((Map) args[j], pt);
                        matchScore = matchScore + 1;
                    } else {
                        continue X;
                    }
                } else {
                    matchScore = matchScore + 1;
                }

                j++;
            }
            if (lastScore < matchScore) {
                m = m1;
                lastScore = matchScore;
            }
        }
        if (m != null && !m.isAccessible()) {
            m.setAccessible(true);
        }
        return m;
    }
}
