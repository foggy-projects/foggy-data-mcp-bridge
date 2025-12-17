package com.foggyframework.core.utils;

public class ClazzUtils {
    /**
     * 根据类获取类名，
     * g.e 匿名类  返回  ClazzUtilsTest$1
     * g.e 内部类  返回  ClazzUtilsTest$MyTest
     *
     * @param cls
     * @return
     */
    public static String getClazzName(Class<?> cls) {

        String name = cls.getName();
        if (name.indexOf("$") > 0) {
            //匿名类，特殊处理
            return name.substring(name.lastIndexOf(".") + 1);
        } else {
            return cls.getSimpleName();
        }

    }

    public static boolean isObjectClassMethod(String method) {
        return StringUtils.equals(method, "toString");
    }
}
