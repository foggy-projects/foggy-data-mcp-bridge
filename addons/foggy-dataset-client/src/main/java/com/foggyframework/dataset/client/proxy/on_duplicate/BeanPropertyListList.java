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
public class BeanPropertyListList extends AbstractList<List> {

    List beanList;

    List<BeanProperty> beanProperties;

    BeanPropertyList beanPropertyList;

    public BeanPropertyListList(List beanList, List<BeanProperty> beanProperties) {
        this.beanList = beanList;
        this.beanProperties = beanProperties;
    }

    @Override
    public List get(int index) {
        Object bean = beanList.get(index);
        if (beanPropertyList == null) {
            beanPropertyList = new BeanPropertyList(bean, beanProperties);
        } else {
            beanPropertyList.setBean(bean);
        }

        return beanPropertyList;
    }

    @Override
    public int size() {
        return beanList.size();
    }
}
