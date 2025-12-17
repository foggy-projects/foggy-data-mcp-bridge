package com.foggyframework.dataset.jdbc.model.spi.support;

import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import io.swagger.annotations.ApiModelProperty;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class ClassDictObjectTransFormatter implements ObjectTransFormatter {

    Class cls;

    Map<Object, String> value2Caption = new HashMap<>();

    public ClassDictObjectTransFormatter(String clazz) {
        try {
            cls = Class.forName(clazz);
            for (Field field : cls.getFields()) {
                if (BeanInfoHelper.isStaticField(field)) {
                    try {
                        ApiModelProperty amp = field.getAnnotation(ApiModelProperty.class);
                        if (amp != null) {
                            Object v = field.get(null);
                            String caption = StringUtils.isNotEmpty(amp.name()) ? amp.name() : amp.value();

                            value2Caption.put(v, caption);
                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }


        } catch (ClassNotFoundException e) {
            e.printStackTrace();
//            throw new RuntimeException(e);
        }
    }

    @Override
    public Object format(Object object) {
        Object v = value2Caption.get(object);
        return v == null ? object : v;
    }

    @Override
    public Class<?> type() {
        return Object.class;
    }
}
