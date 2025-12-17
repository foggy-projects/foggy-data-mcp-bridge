package com.foggyframework.core.common;

import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;

import java.util.HashMap;
import java.util.Map;

public class MapBuilder<K, V> {
    Map<K, V> map = new HashMap<>();

    public static final MapBuilder builder() {
        return new MapBuilder();
    }

    public MapBuilder put(K key, V value) {
        map.put(key, value);
        return this;
    }

    public MapBuilder putObject(Object obj) {
        if (obj == null) {
            return this;
        }
//        BeanUtils.copyProperties(obj, map);
        for (BeanProperty readMethod : BeanInfoHelper.getClassHelper(obj.getClass()).getReadMethods()) {
            map.put((K) readMethod.getName(), (V) readMethod.getBeanValue(obj));
        }
        return this;
    }

    public MapBuilder remove(K key) {
        map.remove(key);
        return this;
    }

    public Map<K, V> build() {
        return map;
    }

}
