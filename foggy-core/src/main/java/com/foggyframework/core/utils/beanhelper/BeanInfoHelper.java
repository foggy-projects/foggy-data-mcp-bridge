/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.utils.beanhelper;


import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.ErrorUtils;
import com.foggyframework.core.utils.StringUtils;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;

/**
 * 从2013-02-28开始支持对Element返回BeanInfoHelper（根据XSD配置决定其属性）， 同时建议在能够拿到bean实例的情况下使用
 *
 * <pre>
 * public static BeanInfoHelper getClassHelper(final Object bean)
 * 而不是
 * public static BeanInfoHelper getClassHelper(final Class clazz)
 * </pre>
 *
 * @author Foggy
 * @since v1.0 2011-10-12
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class BeanInfoHelper {

    public static final void setObjectProperty(Object ctxObj, String name, Object value) {

        if (ctxObj instanceof Map<?, ?>) {
            ((Map<Object, Object>) ctxObj).put(name, value);
        } else {
            BeanInfoHelper.setProperty(ctxObj, name, value);
        }
    }


//    /**
//     * @param x  23.99566
//     * @param y  125.54658
//     * @param q1 100000
//     * @param q2
//     * @return
//     */
//    public static String xx(double x, double y, int q1, int q2) {
//        int xx = (int) (x * q1);
//        int yy = (int) (y * q1);
//
//        String xk = "X" + ((int) (xx / q2));
//        String yk = "Y" + ((int) (yy / q2));
//
//        String key = xk + yk + "Q" + q2;
//
//        return key;
//    }
//    public static void main(String[] args) {
//
//        System.out.println(xx(23.99566,125.54658,10000,100));
//
//    }

//	public  <T> T resolve(Request request,  Class<T> formCls) {
//
//		try {
//			T result = formCls.newInstance();
//			for (BeanProperty p : getWriteMethods()) {
//
//				p.setBeanValueFromRequest(result, request);
//			}
//			return result;
//		} catch (InstantiationException | IllegalAccessException e) {
//			throw ErrorUtils.toRuntimeException(e);
//		}
//	}
//
//	public  <T> T resolve(Request request, Class<T> formCls, ValidatorForm validatorForm) {
//
//
//		T result = resolve(request,  formCls);
//		//所有的值都写入后，开始验证
//		validatorForm.valid(result,true);
//		return result;
//
//	}

//	public static <T> T _resolve(Request request, Class<T> formCls) {
//
//		return BeanInfoHelper.getClassHelper(formCls).resolve(request,  formCls);
//	}

    public class BeanPropertySupport extends ClassItemHelper implements BeanProperty {

        Field field;

        PropertyDescriptor propertyDescriptor;

        BeanPropertySupport(Field f, PropertyDescriptor pd) {
            super(BeanInfoHelper.this, f);
            field = f;
            propertyDescriptor = pd;
            if (pd != null) {
                writerMehod = pd.getWriteMethod();
                // bug fix
                // 当一个类实现一个接口的get方法后，可能导致这个类无法通过pd.getWriteMethod()得到setter方法
                if (writerMehod == null) {
                    writerMehod = ClassInspect.getMethod(clazz, ClassInspect.field2SetterMethodName(pd.getName()),
                            pd.getPropertyType(), false);
                }
                readerMethod = pd.getReadMethod();
                type = pd.getPropertyType();

            } else {
                type = field.getType();
            }
//			if (type != null && type.getName().indexOf(".Point") > 0) {
//				System.err.println("6678u6ytresdfg");
//			}
            formatter = RequestBeanInjecter.getInstance().getObjectTransFormatter(type);

        }

        @Override
        public Annotation getAnnotation(Class annotationClass) {
            Annotation ann = null;
            if (readerMethod != null) {
                ann = readerMethod.getAnnotation(annotationClass);
                if (ann != null)
                    return ann;
            }
            if (writerMehod != null) {
                ann = writerMehod.getAnnotation(annotationClass);
                if (ann != null)
                    return ann;
            }
            if (field != null) {
                ann = field.getAnnotation(annotationClass);
                return ann;
            }
            return null;
        }

        // public BeanPropertySupport() {
        // super(BeanInfoHelper.this, null);
        // }

        @Override
        public Field getField() {
            return field;
        }

        @Override
        public final String getItemName() {
            return field == null ? getName() : field.getName();
        }

        @Override
        public final String getName() {
            return propertyDescriptor == null ? field.getName() : propertyDescriptor.getName();
        }

        @Override
        public boolean hasAnnotation(Class annotationClass) {
            return getAnnotation(annotationClass) != null;
        }

        @Override
        public boolean hasReader() {
            return readerMethod != null;
        }

        @Override
        public boolean hasWriter() {
            return writerMehod != null;
        }

        @Override
        public boolean isTransient() {

            if (field == null || BeanInfoHelper.isStaticField(field)) {
                // 静态变量 ,不需要序列化
                return true;
            }
            // else if (field.getAnnotation(Transient.class) != null) {
            // // 带有Transient注释
            // return true;
            // }
            return super.isTransient();
        }

        @Override
        public Object newInstance() {
            try {
                return field.getType().newInstance();
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            return "BeanPropertySupport [field=" + field + "]" + super.toString();
        }

    }

    public interface FieldCallback {

        void doWith(BeanInfoHelper h, Field field, BeanProperty bp);

    }

    static class MapBeanInfoHelper extends BeanInfoHelper {

        Map<String, BeanProperty> mapBp = new HashMap<String, BeanProperty>();

        MapBeanInfoHelper(Class<?> cls) {
            super(cls);
        }

        @Override
        public BeanProperty getBeanProperty(String name, boolean errorIfNotFound) {
            BeanProperty bp = mapBp.get(name);
            if (bp == null) {
                synchronized (this) {
                    bp = mapBp.get(name);
                    if (bp == null) {
                        bp = new MapItemBeanProperty(name);
                        mapBp.put(name, bp);
                    }
                }
            }
            return bp;
        }

    }

    static class ArrayBeanInfoHelper extends BeanInfoHelper {


        ArrayBeanInfoHelper(Class<?> cls) {
            super(cls);
            BeanProperty bp = new BeanProperty() {
                @Override
                public Object format(Object v) {
                    return v;
                }

                @Override
                public <T> T getAnnotation(Class<T> annotationClass) {
                    return null;
                }

                @Override
                public Object getBeanValue(Object bean) {
                    return ((Object[]) bean).length;
                }

                @Override
                public Field getField() {
                    return null;
                }

                @Override
                public String getName() {
                    return "length";
                }

                @Override
                public Class<?> getType() {
                    return int.class;
                }

                @Override
                public boolean hasReader() {
                    return true;
                }

                @Override
                public boolean hasWriter() {
                    return false;
                }

                @Override
                public Object newInstance() {
                    return null;
                }

                @Override
                public void setBeanValue(Object bean, Object value) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void setBeanValue(Object bean, Object value, boolean errorIfNotFound) {
                    throw new UnsupportedOperationException();
                }
            };
            this.readProperties.add(bp);
            this.nameToItems.put("length", bp);
        }

    }


    static class MapItemBeanProperty implements BeanProperty {
        String name;

        public MapItemBeanProperty(String name) {
            super();
            this.name = name;
        }

        @Override
        public Object format(Object v) {
            // TODO Auto-generated method stub
            return v;
        }

        @Override
        public Annotation getAnnotation(Class annotationClass) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Object getBeanValue(Object bean) {
            return ((Map<?, ?>) bean).get(name);
        }

        @Override
        public Field getField() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Class<?> getType() {
            return Object.class;
        }

        @Override
        public boolean hasReader() {
            return true;
        }

        @Override
        public boolean hasWriter() {
            return true;
        }

        @Override
        public Object newInstance() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void setBeanValue(Object bean, Object value) {
            setBeanValue(bean, value, false);
        }

        @Override
        public void setBeanValue(Object bean, Object value, boolean errorIfNotFound) {
            ((Map) bean).put(name, value);
        }


    }

    public interface WriterCallback {

        void doWith(BeanInfoHelper h, Method writer, BeanProperty bp);

    }

    static final MapBeanInfoHelper shareMapBeanInfoHelper = new MapBeanInfoHelper(HashMap.class);

    private static final Map<Class, BeanInfoHelper> clsMap = new HashMap<Class, BeanInfoHelper>();

//	private static Map<String, ElementBeanInfoHelper> cache = new HashMap<String, ElementBeanInfoHelper>();

    private static final Map<Class, Class> primitive2Class = new HashMap<Class, Class>();

    static {
        primitive2Class.put(int.class, Integer.class);
        primitive2Class.put(double.class, Double.class);
        primitive2Class.put(float.class, Float.class);
        primitive2Class.put(char.class, Character.class);
        primitive2Class.put(boolean.class, Boolean.class);
        primitive2Class.put(long.class, Long.class);
        primitive2Class.put(short.class, Short.class);
        primitive2Class.put(byte.class, Byte.class);
    }

    public static final BeanInfoHelper OBJECT_BEAN_INFO_HELPER = BeanInfoHelper.getClassHelper(Object.class);

//	public static final ElementBeanInfoHelper OBJECT_ELEMENT_BEAN_INFO_HELPER = new ElementBeanInfoHelper(null);

    private static final Method[] EMPTY = new Method[0];

    public static final void doWithFields(Class<?> cls, FieldCallback cb) {
        Field[] fields = ClassInspect.getClassFields(cls);
        BeanInfoHelper h = BeanInfoHelper.getClassHelper(cls);

        for (Field f : fields) {
            cb.doWith(h, f, h.getBeanProperty(f.getName()));
        }

    }

    public static final void doWithWriters(Class<?> cls, WriterCallback cb) {
        BeanInfoHelper h = BeanInfoHelper.getClassHelper(cls);

        for (BeanProperty f : h.getWriteMethods()) {
            cb.doWith(h, ((BeanPropertySupport) f).getWriterMehod(), f);
        }

    }

//    private static final Object key = new Object();

    public static final BeanInfoHelper getClassHelper(final Class clazz) {
        if (clazz == null) {
            throw new RuntimeException("clazz can't be null");
        }
        BeanInfoHelper ec = null;
        try {
            synchronized (clazz) {
                if (BeanInfoHelper.clsMap.containsKey(clazz)) {
                    ec = BeanInfoHelper.clsMap.get(clazz);
                } else {
                    if (Map.class.isAssignableFrom(clazz)) {
                        ec = new MapBeanInfoHelper(clazz);
                    } else if (clazz.isArray()) {
                        ec = new ArrayBeanInfoHelper(clazz);
                    } else {
                        ec = new BeanInfoHelper(clazz);
                    }

                    BeanInfoHelper.clsMap.put(clazz, ec);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        }
        return ec;
    }

    public final static BeanInfoHelper getClassHelperByType(final Type type) {
        if (type instanceof ParameterizedType) {

            throw new UnsupportedOperationException();
        } else {
            throw new UnsupportedOperationException();
        }

//		return getClassHelper(clazz);
    }


    public final static BeanInfoHelper getClassHelper(final Object bean) {
        if (bean == null)
            return null;

//		if (bean instanceof Element) {
//			return getClassHelper(((Element) bean).getNamespaceURI(), ((Element) bean).getLocalName());
//		}
        Class<?> clazz = (Class<?>) (bean instanceof Class ? bean : bean.getClass());
        return getClassHelper(clazz);
    }


    public static final Class getPrimitiveClass(Class cls) {
        Class x = primitive2Class.get(cls);
        return x == null ? cls : x;
    }

    public static final Object getProperty(Object bean, String name) {
        BeanInfoHelper ec = getClassHelper(bean);
        if (ec == null) {
            // // Systemx.out.println(439875);
            throw new RuntimeException("can't find BeanInfoHelper for bean : " + bean);
        }
        BeanProperty bp = ec.getBeanProperty(name);
        // 如果bp为空或bp没有reader,则返回空
        return bp == null || !bp.hasReader() ? null : bp.getBeanValue(bean);
    }

    public static final Object getProperty(Object bean, String name, boolean errorIfNotFound) {
        BeanInfoHelper ec = getClassHelper(bean);
        BeanProperty bp = ec.getBeanProperty(name, errorIfNotFound);

        // 如果bp为空或bp没有reader,则返回空
        return bp == null || !bp.hasReader() ? null : bp.getBeanValue(bean);
    }

    public final static boolean hasSetter(Class<?> clazz, String name) {
        if (clazz == null) {
            return false;
        }
        BeanProperty bp = getClassHelper(clazz).getBeanProperty(name);
        return bp != null && bp.hasWriter();
    }

    public final static boolean hasSetter(Object object, String name) {
        if (object == null) {
            return false;
        }
        BeanProperty bp = getClassHelper(object).getBeanProperty(name);
        return bp != null && bp.hasWriter();
    }

    public static final boolean isStaticField(final Field f) {
        final String g = f.toGenericString();
        return g.indexOf(" static ") > 0;
    }

    public static final boolean isBaseObj(final Object v) {
        return v instanceof Number || v instanceof Date || v instanceof String || v instanceof Boolean;
    }

    public static boolean isNumber(Type v) {
        return v == int.class || v == long.class || v == double.class || v == Integer.class
                || v == Long.class || v == Double.class || v == BigDecimal.class;
    }

    public static final boolean isBaseClass(final Class v) {
        return v == int.class || v == long.class || v == double.class || v == float.class
                || v == boolean.class
                || v.getName().startsWith("java.");
    }


    public static final boolean isBaseClassByStr(final String v) {
        return v.startsWith("java.lang") || v.equals("int") || v.equals("long") || v.equals("double")
                || v.equals("float");
    }

    public static final boolean isStaticMethod(final Method f) {
        final String g = f.toGenericString();
        return g.indexOf(" static ") > 0;
    }


    public static final void setProperty(Object bean, String name, Object value) {
        Class cls = bean.getClass();
        BeanInfoHelper ec = getClassHelper(bean);
        BeanProperty bp = ec.getBeanProperty(name);
        // 如果bp为空或bp没有reader,则返回空
        if (bp == null) {
            // TODO error
            RX.notNull(bp, "no property [%s] found in class %s", name,  cls.getName());
        }
        // if(bp.getWriterMehod()==null){
        // TODO error
        // Asserts.assertNotNull(bp.getWriterMehod(),
        // "setter not be null! in class [" + bean.getClass()
        // + "],property [" + name + "]");
        // }
        try {
            bp.setBeanValue(bean, value);
        } catch (IllegalArgumentException e) {
            throw ErrorUtils.toRuntimeException(e);
        }
    }

    Map<String, BeanProperty> nameToItems = new HashMap<String, BeanProperty>();

    Class<?> clazz;
    BeanInfo beanInfo;
    /**
     * 全部属性
     */
    List<BeanProperty> properties = new ArrayList<BeanProperty>();

    /**
     * 拥有Field的属性
     */
    List<BeanProperty> fieldProperties = new ArrayList<BeanProperty>();

    /**
     * 拥有writer及Reader的属性
     */
    List<BeanProperty> rwProperties = new ArrayList<BeanProperty>();

    /**
     * 拥有Reader方法的属性
     */
    List<BeanProperty> readProperties = new ArrayList<BeanProperty>();
    /**
     * 拥有writer方法的属性
     */
    List<BeanProperty> writeProperties = new ArrayList<BeanProperty>();
    Map<String, Method[]> name2Methods = new HashMap<String, Method[]>();

    BeanInfoHelper(final Class<?> cls) {
        if (cls == null) {
            return;
        }
        clazz = cls;
        PropertyDescriptor[] pds = null;
        try {
            if (cls.isInterface()) {
                Map<String, PropertyDescriptor> mm = new HashMap<>();
                getInterfacePropertyDescriptors(mm, cls);
                pds = mm.values().toArray(new PropertyDescriptor[mm.size()]);
            } else {

                beanInfo = Introspector.getBeanInfo(cls, cls == Object.class ? null : Object.class);

                pds = beanInfo.getPropertyDescriptors();
            }
        } catch (IntrospectionException e) {
            throw ErrorUtils.toRuntimeException(e);
        }

        Field[] fields = ClassInspect.getClassFields(cls);
        for (PropertyDescriptor pd : pds) {
            if (pd.getName().equals("serialVersionUID")) {
                continue;
            }
            Field field = null;
            for (Field f : fields) {
                if (f.getName().equals(pd.getName())) {
                    field = f;
                    break;
                }
            }
            // if (!ClassPropertyHelper.isStaticField(field)) {
            BeanPropertySupport property = new BeanPropertySupport(field, pd);
            properties.add(property);
            nameToItems.put(property.getName(), property);

            if (field != null) {
                fieldProperties.add(property);
            }
            if (pd.getReadMethod() != null) {
                readProperties.add(property);
            }
            if (pd.getWriteMethod() != null) {
                writeProperties.add(property);
            }
            if (pd.getReadMethod() != null && pd.getWriteMethod() != null) {
                rwProperties.add(property);
            }
        }

        for (Field f : fields) {
            if (f.getName().equals("serialVersionUID")) {
                continue;
            }

            if (nameToItems.containsKey(f.getName())) {
                continue;
            }
            BeanPropertySupport property = new BeanPropertySupport(f, null);
            fieldProperties.add(property);
            nameToItems.put(property.getName(), property);
        }
        /**************************************************************************************/
        Map<String, List<Method>> tmp = new HashMap<String, List<Method>>();
        for (Method m : cls.getMethods()) {

            List<Method> mm = tmp.get(m.getName());
            if (mm == null) {
                mm = new ArrayList<Method>();
                tmp.put(m.getName(), mm);
            }
            mm.add(m);
        }

        for (Entry<String, List<Method>> e : tmp.entrySet()) {
            name2Methods.put(e.getKey(), e.getValue().toArray(new Method[]{}));
        }
        /**************************************************************************************/
    }

    public final Annotation getAnnotation(final Class annotationClass) {
        return clazz.getAnnotation(annotationClass);
    }

    public BeanProperty getBeanProperty(String name) {
        return getBeanProperty(name, false);
    }

    public BeanProperty getBeanProperty(String name, boolean errorIfNotFound) {
        if (nameToItems.containsKey(name))
            return nameToItems.get(name);
        else if (errorIfNotFound) {
//            if(this.clazz.isArray()&& StringUtils.equals(name,"length")){
////                String
////                return
//            }
//			try {
//				Field f = clazz.getField(name);
//				System.out.println(1);
//			} catch (NoSuchFieldException | SecurityException e) {
//				e.printStackTrace();
//			}
            throw new RuntimeException("未能找到属性[" + name + "] 在 class[" + clazz + "]中");
        } else {
            return null;
        }
    }

    public BeanProperty getComplexBeanProperty(String name) {
        String[] paths = name.split("\\.");
        if (paths.length == 1) {
            return getBeanProperty(name);
        }
        BeanProperty[] bpPaths = new BeanProperty[paths.length];

        BeanInfoHelper current = this;

        for (int i = 0; i < paths.length; i++) {
            BeanProperty beanProperty = current.getBeanProperty(paths[i], false);
            if (beanProperty == null) {
                beanProperty = current.getBeanProperty(StringUtils.to(paths[i]), true);
            }
            bpPaths[i] = beanProperty;
            if (i == paths.length - 1) {
                break;
            }
            current = BeanInfoHelper.getClassHelper(beanProperty.getType());
        }

        return new ComplexBeanProperty(name, bpPaths);

    }

    public BeanProperty getClassItem(String name) {
        return nameToItems.get(name);
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public final List<BeanProperty> getFieldProperties() {
        return properties;
    }

    private void getInterfacePropertyDescriptors(Map<String, PropertyDescriptor> result, Class<?> cls) {
        try {
            BeanInfo i = Introspector.getBeanInfo(cls);
            for (PropertyDescriptor d : i.getPropertyDescriptors()) {
                result.put(d.getName(), d);
            }
            for (Class<?> s : cls.getInterfaces()) {
                getInterfacePropertyDescriptors(result, s);
            }

        } catch (IntrospectionException e) {
            throw ErrorUtils.toRuntimeException(e);// .printStackTrace();
        }
    }

    public List<Method> getMethods() {
        List<Method> xx = new ArrayList<Method>();
        for (Method[] mm : name2Methods.values()) {
            Collections.addAll(xx, mm);
        }
        return xx;
    }

    // public static void setProperty(Object bean, String name, Object value) {
    // getClassHelper(bean.getClass()).getBeanProperty(name,true).s
    // }

    public final Method[] getMethods(String name) {
        Method[] x = name2Methods.get(name);
        if (x == null) {
            return EMPTY;
        }
        return x;
    }

    public final List<BeanProperty> getReadMethods() {
        return readProperties;
    }

    public URL getResource(final String path) {
        // clazz
        return clazz.getResource(path);
    }

    public final List<BeanProperty> getRwProperties() {
        return properties;
    }

    public final List<BeanProperty> getWriteMethods() {
        return writeProperties;
    }

    public Object newInstance() throws InstantiationException, IllegalAccessException {
        return clazz.newInstance();
    }

    @Override
    public String toString() {
        return " [clazz=" + clazz.getSimpleName() + "]";
    }

    /**
     * 当Integer->int时，若左侧为空，不抛异常，自动 转0
     * @param source
     * @param target
     * @param ignoreProperties
     */
    public static void copyProperties(Object source, Object target, String... ignoreProperties) {
        BeanInfoHelper sourceBeanInfoHelper = BeanInfoHelper.getClassHelper(source.getClass());
        BeanInfoHelper targetBeanInfoHelper = BeanInfoHelper.getClassHelper(target.getClass());

        for (BeanProperty fieldProperty : targetBeanInfoHelper.getFieldProperties()) {
            BeanProperty sourceFieldProperty = sourceBeanInfoHelper.getBeanProperty(fieldProperty.getName());
            if(ignoreProperties!=null){
                for (String ignoreProperty : ignoreProperties) {
                    if(StringUtils.equals(ignoreProperty,fieldProperty.getName())){
                        continue;
                    }
                }
            }
            if (sourceFieldProperty != null) {
                fieldProperty.setBeanValue(target,sourceFieldProperty.getBeanValue(source));
            }
        }
    }
}
