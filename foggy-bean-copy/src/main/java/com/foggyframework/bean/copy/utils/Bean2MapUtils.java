package com.foggyframework.bean.copy.utils;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.beans.PropertyDescriptor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filters
 *
 * @author fengjianguang
 */
@Slf4j
public final class Bean2MapUtils {

    /**
     * 缓存类的属性描述符信息，避免重复反射
     */
    private static final ConcurrentHashMap<Class<?>, PropertyDescriptorCache> PROPERTY_CACHE = new ConcurrentHashMap<>();

    /**
     * 属性描述符缓存类
     */
    private static class PropertyDescriptorCache {
        private final PropertyDescriptor[] propertyDescriptors;
        private final Set<String> primitiveProperties;
        private final BeanWrapper beanWrapper;

        PropertyDescriptorCache(Class<?> clazz) {
            // 创建一个临时的 BeanWrapper 用于获取类信息
            this.beanWrapper = new BeanWrapperImpl(clazz);
            this.propertyDescriptors = beanWrapper.getPropertyDescriptors();
            this.primitiveProperties = new HashSet<>();

            // 缓存所有基本类型的属性名
            for (PropertyDescriptor pd : propertyDescriptors) {
                Class<?> propertyType = pd.getPropertyType();
                if (propertyType != null && propertyType.isPrimitive()) {
                    primitiveProperties.add(pd.getName());
                }
            }
        }

        PropertyDescriptor[] getPropertyDescriptors() {
            return propertyDescriptors;
        }

        boolean isPrimitiveProperty(String propertyName) {
            return primitiveProperties.contains(propertyName);
        }

        boolean isReadableProperty(String propertyName) {
            return beanWrapper.isReadableProperty(propertyName);
        }

        boolean isWritableProperty(String propertyName) {
            return beanWrapper.isWritableProperty(propertyName);
        }
    }

    /**
     * 获取类的属性描述符缓存
     */
    private static PropertyDescriptorCache getPropertyCache(Class<?> clazz) {
        return PROPERTY_CACHE.computeIfAbsent(clazz, PropertyDescriptorCache::new);
    }

    public static Map toMap(Object bean) {
        return toMap(bean, null);
    }

    public static Map toMap(Object bean, Map mm) {
        if (bean == null) {
            return new HashMap();
        }
        if (mm == null) {
            mm = new HashMap();
        }
        BeanInfoHelper helper = BeanInfoHelper.getClassHelper(bean.getClass());

        for (BeanProperty readMethod : helper.getReadMethods()) {
            Object v = readMethod.getBeanValue(bean);
            if (v != null) {
                mm.put(readMethod.getName(),v);
            }
        }

        return mm;
    }

    /**
     * 安全的属性复制，将null值转换为基本类型的默认值
     * - Integer(null) -> int(0)
     * - Long(null) -> long(0L)
     * - Double(null) -> double(0.0)
     * - Boolean(null) -> boolean(false)
     *
     * 优化：
     * 1. 使用缓存避免重复反射
     * 2. 直接使用PropertyDescriptor读取/写入属性，避免创建BeanWrapper
     * 3. 缓存基本类型属性判断结果
     *
     * @throws RX 当属性复制失败时抛出异常，避免数据不一致
     */
    public static void copyPropertiesSafe(Object source, Object target) {
        if (source == null || target == null) {
            return;
        }

        Class<?> sourceClass = source.getClass();
        Class<?> targetClass = target.getClass();

        // 从缓存中获取源和目标类的属性信息
        PropertyDescriptorCache sourceCache = getPropertyCache(sourceClass);
        PropertyDescriptorCache targetCache = getPropertyCache(targetClass);

        Set<String> ignoreProperties = new HashSet<>();

        // 遍历源对象的属性描述符
        for (PropertyDescriptor srcPd : sourceCache.getPropertyDescriptors()) {
            String propertyName = srcPd.getName();

            // 检查目标对象是否有同名的可读写属性
            if (targetCache.isReadableProperty(propertyName) &&
                    targetCache.isWritableProperty(propertyName)) {

                try {
                    // 直接使用PropertyDescriptor的readMethod读取属性值
                    java.lang.reflect.Method readMethod = srcPd.getReadMethod();
                    if (readMethod != null) {
                        Object srcValue = readMethod.invoke(source);

                        // 如果源对象属性为null，且目标属性是基本类型
                        // 需要手动设置为基本类型的默认值（0, 0.0, false等）
                        if (srcValue == null && targetCache.isPrimitiveProperty(propertyName)) {
                            // 获取目标属性的PropertyDescriptor
                            PropertyDescriptor targetPd = findPropertyDescriptor(
                                targetCache.getPropertyDescriptors(), propertyName);

                            if (targetPd != null && targetPd.getWriteMethod() != null) {
                                // 根据基本类型设置默认值
                                Class<?> targetType = targetPd.getPropertyType();
                                Object defaultValue = getDefaultValue(targetType);
                                targetPd.getWriteMethod().invoke(target, defaultValue);
                            }

                            // 关键：无论是否成功设置默认值，都必须添加到 ignoreProperties
                            // 避免 BeanUtils.copyProperties 尝试复制 null 到基本类型导致 IllegalArgumentException
                            ignoreProperties.add(propertyName);
                        }
                    }
                } catch (Exception e) {
                    // 属性复制失败时立即抛出异常，避免数据不一致
                    String errorMsg = String.format("属性复制失败：[%s]，源对象类型：%s，目标对象类型：%s",
                        propertyName, sourceClass.getName(), targetClass.getName());
                    throw RX.throwB(errorMsg, e);
                }
            }
        }

        // 使用Spring的BeanUtils复制其他属性
        String[] ignoreArray = ignoreProperties.toArray(new String[0]);
        BeanUtils.copyProperties(source, target, ignoreArray);
    }

    /**
     * 从PropertyDescriptor数组中查找指定名称的属性
     */
    private static PropertyDescriptor findPropertyDescriptor(PropertyDescriptor[] pds, String propertyName) {
        for (PropertyDescriptor pd : pds) {
            if (pd.getName().equals(propertyName)) {
                return pd;
            }
        }
        return null;
    }

    /**
     * 获取基本类型的默认值
     */
    private static Object getDefaultValue(Class<?> primitiveType) {
        if (primitiveType == int.class) {
            return 0;
        } else if (primitiveType == long.class) {
            return 0L;
        } else if (primitiveType == double.class) {
            return 0.0;
        } else if (primitiveType == float.class) {
            return 0.0f;
        } else if (primitiveType == boolean.class) {
            return false;
        } else if (primitiveType == byte.class) {
            return (byte) 0;
        } else if (primitiveType == short.class) {
            return (short) 0;
        } else if (primitiveType == char.class) {
            return '\u0000';
        }
        return null;
    }


}
