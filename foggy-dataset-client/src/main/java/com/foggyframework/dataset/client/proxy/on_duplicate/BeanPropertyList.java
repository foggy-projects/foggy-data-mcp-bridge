package com.foggyframework.dataset.client.proxy.on_duplicate;

import com.foggyframework.core.utils.beanhelper.BeanProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.AbstractList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BeanPropertyList extends AbstractList {

    Object bean;

    List<BeanProperty> beanProperties;

    @Override
    public Object get(int index) {
        return beanProperties.get(index).getBeanValue(bean);
    }

    @Override
    public int size() {
        return beanProperties.size();
    }
}
