package com.foggyframework.dataset.client.proxy.converter;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import com.foggyframework.dataset.resultset.spring.ComplexBeanRowMapper;
import com.foggyframework.dataset.resultset.spring.JavaColumnNameFixRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.Assert;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

public abstract class AbstractReturnConverter<T> implements ReturnConverter {

    @Override
    public RowMapper getRowMapper(Type genericReturnType) {
        Class<?> cls = getBeanClazz(genericReturnType);
        return cls == null ? new JavaColumnNameFixRowMapper() : new ComplexBeanRowMapper(cls);
    }

    @Override
    public Class getBeanClazz(Type genericReturnType) {
        if (genericReturnType instanceof ParameterizedType) {
            Type[] types = ((ParameterizedType) genericReturnType).getActualTypeArguments();

            Assert.isTrue(types.length == 1, "只支持一个参数的泛型: " + genericReturnType);
            Assert.isTrue(types[0] instanceof Class, "暂时不支持复杂泛型");
            Class type = (Class) types[0];

            if (type.isAssignableFrom(Map.class)) {
                return null;
            }
            return type;
        } else {
            return null;
        }
    }

    @Override
    public Object convertMap(Type genericReturnType, Map<String, Object> queryMap) {
        Class cls = getBeanClazz(genericReturnType);
        return convertClass2Map(cls, queryMap);
    }

    public Object convertClass2Map(Class cls, Map<String, Object> queryMap) {
        if (cls == null) {
            return queryMap;
        }
        try {
            Object inst = cls.newInstance();
            BeanInfoHelper info = BeanInfoHelper.getClassHelper(cls);

            for (Map.Entry<String, Object> e : queryMap.entrySet()) {
                String name = e.getKey();
                BeanProperty beanProperty = info.getBeanProperty(name);
                if (beanProperty == null) {
                    continue;
                }
                if (beanProperty.hasWriter()) {
                    beanProperty.setBeanValue(inst, e.getValue());
                }
                if (name.indexOf("_") > 0) {
                    String toName = StringUtils.to(name);
                    beanProperty = info.getBeanProperty(toName);
                    if (beanProperty.hasWriter()) {
                        beanProperty.setBeanValue(inst, e.getValue());
                    }
                }
            }
            return inst;

        } catch (InstantiationException e) {
            throw RX.throwB(e);
        } catch (IllegalAccessException e) {
            throw RX.throwB(e);
        }
    }

    @Override
    public boolean getDefaultReturnTotal() {
        return false;
    }
}
