package com.foggyframework.bean.copy.utils;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;

import java.util.HashMap;
import java.util.Map;

public final class BeanCompareUtils {

    public static Object[] compare(Object v1, Object v2) {
        if (v1 == null || v2 == null) {
            return new Object[]{v1, v2};
        }

        BeanInfoHelper infoHelper = BeanInfoHelper.getClassHelper(v1.getClass());
        Map vv1 = new HashMap();
        Map vv2 = new HashMap();
        for (BeanProperty readMethod : infoHelper.getReadMethods()) {
            Object x1 = readMethod.getBeanValue(v1);
            Object x2 = readMethod.getBeanValue(v2);
            if (StringUtils.equals(x1, x2)) {
                continue;
            }

            vv1.put(readMethod.getName(), x1);
            vv2.put(readMethod.getName(), x2);
        }
        return new Object[]{vv1, vv2};
    }
}
