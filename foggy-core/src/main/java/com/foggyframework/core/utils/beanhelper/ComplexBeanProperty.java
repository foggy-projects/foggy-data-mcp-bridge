package com.foggyframework.core.utils.beanhelper;

import com.foggyframework.core.trans.ObjectTransFormatter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Field;

@Data@NoArgsConstructor@AllArgsConstructor
public class ComplexBeanProperty<T> implements BeanProperty{
    String name;
    BeanProperty[] bpPaths;

//    ObjectTransFormatter formatter ;//= RequestBeanInjecter.getInstance().getObjectTransFormatter(type);

    @Override
    public Object format(Object v) {
        return bpPaths[bpPaths.length-1].format(v);
    }

    @Override
    public <T> T getAnnotation(Class<T> annotationClass) {
        return bpPaths[bpPaths.length-1].getAnnotation(annotationClass);
    }

    @Override
    public Object getBeanValue(Object bean) {

        Object value = bean;
        for (BeanProperty bpPath : bpPaths) {
            Object bpValue = bpPath.getBeanValue(value);
            if(bpValue == null){
                return null;
//                bpValue = bpPath.newInstance();
//                bpPath.setBeanValue(value,bpValue);
            }else{
                value = bpValue;
            }
        }

        return value;
    }

    @Override
    public Field getField() {
        return bpPaths[bpPaths.length-1].getField();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Class<?> getType() {
        return   bpPaths[bpPaths.length-1].getType();
    }

    @Override
    public boolean hasReader() {
        return bpPaths[bpPaths.length-1].hasReader();
    }

    @Override
    public boolean hasWriter() {
        return bpPaths[bpPaths.length-1].hasWriter();
    }

    @Override
    public Object newInstance() {
        return bpPaths[bpPaths.length-1].newInstance();
    }

    @Override
    public void setBeanValue(Object bean, Object value) {
        setBeanValue(bean,value,true);
    }

    @Override
    public void setBeanValue(Object bean, Object value, boolean errorIfNotFound) {
        Object beanValue = bean;

        for (int i = 0; i < bpPaths.length-1; i++) {
            BeanProperty bpPath = bpPaths[i];
            Object fieldValue = bpPath.getBeanValue(beanValue);
            if(fieldValue == null){
                fieldValue = bpPath.newInstance();
                bpPath.setBeanValue(beanValue,fieldValue);
            }
                beanValue = fieldValue;
        }
        bpPaths[bpPaths.length-1].setBeanValue(beanValue,value,errorIfNotFound);

    }
}
