package com.foggyframework.bean.copy.utils;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Filters
 *
 * @author fengjianguang
 */
@Slf4j
public final class Map2BeanUtils {


    public static <T> T fromMap(Map doc, T obj) {
        return (T) fromMap( doc,obj.getClass(), obj);
    }

    public static <T> T fromMap( Map doc,Class<T> cls) {
        return fromMap( doc,cls, null);
    }

    /**
     * 把doc中的值复制到obj中
     *
     * @param <T>
     * @param cls
     * @param doc
     * @param obj
     * @return
     */
    public static <T> T fromMap( Map doc,Class<T> cls, Object obj) {
        if (obj == null) {
            try {
            	if(cls==Map.class) {
            		obj = new HashMap<>();
            	}else {
            		obj = cls.newInstance();
            	}
                
            } catch (InstantiationException | IllegalAccessException e) {
                throw RX.throwB(e);
            }
        }
        obj = fromMap(cls, doc, obj);

        return (T) obj;
    }

    private static <T> T fromMap(Type cls, Map<String, Object> doc, Object obj) {

        if (cls == Object.class) {
            return (T) doc;
        }
        BeanInfoHelper h = BeanInfoHelper.getClassHelper(cls);
        for (Entry<String, Object> e : doc.entrySet()) {

            BeanProperty bp = h.getBeanProperty(e.getKey());

            if (bp == null) {
                continue;
            }

            Object value = e.getValue(); // mongodb中保存的数据对象

            if (bp.getField() == null) {
//				System.out.println("???");
                continue;
            }
            Type type = bp.getField().getGenericType();

            Object reulst = value == null ?null:fromObject(bp.getType(), value, bp.getBeanValue(obj), type);

            bp.setBeanValue(obj, reulst);

        }

        return (T) obj;
    }


    /**
     * 传入
     *
     * @param src map中保存的数据对象
     * @param dst 转换后的对象
     * @return
     */
    private static Object fromObject(Class cls, Object src, Object dst, Type type) {
        if(src ==null){
            return null;
        }

        if (src != null && cls == src.getClass()) {
            return src;
        } else if (BeanInfoHelper.isBaseObj(src)) {
            // 基本类型，不用管
            return src;
        } else if (src instanceof Map) {
            if (type instanceof ParameterizedType && ((ParameterizedType) type).getActualTypeArguments().length > 1) {
                Type vt = ((ParameterizedType) type).getActualTypeArguments()[1];
                String innerClsName = vt.getTypeName();

                if (innerClsName == null || StringUtils.equals(innerClsName, "T")) {
                    return src;
                }
                if (BeanInfoHelper.isBaseClassByStr(innerClsName)) {
                    // 基础类型，没啥事
                    return src;
                }

                try {
                    Class innerCls = Class.forName(innerClsName);

                    Map mm = new HashMap<>();

                    for (Entry<String, Object> entry : ((Map<String, Object>) src).entrySet()) {

                        mm.put(entry.getKey(), fromObject(innerCls, entry.getValue(), null, null));
                    }
                    return mm;
                } catch (ClassNotFoundException e) {

                    log.error(e.getMessage());
                    if(log.isDebugEnabled()){
                        e.printStackTrace();
                    }
                }

            }
            if(dst==null){
                try {
                    dst = cls.newInstance();

                } catch (Throwable e) {
                    throw RX.throwB(e);
                }
            }
            // 还是 Map?继续转换
            return fromMap(cls, (Map) src, dst);

        }  else if (cls.isArray()) {
//            System.err.println("test dasfasdfwerwerwr");
            List ll = (List) src;

            if (cls.getComponentType() == String.class) {
                return ll.toArray(new String[0]);
            } else if (cls.getComponentType() == int.class) {
                return ll.toArray(new Integer[0]);
            } else if (cls.getComponentType() == Integer.class) {
                return ll.toArray(new Integer[0]);
            } else if (cls.getComponentType() == Double.class || cls.getComponentType() == double.class) {
                return ll.toArray(new Double[0]);
            } else {
                return ll.toArray();
            }
        } else if (src instanceof List) {
            List ll = (List) src;
            int i = 0;
            Class innerCls = null;
            if (type instanceof ParameterizedType) {
                try {
                    String innerClsName = ((ParameterizedType) type).getActualTypeArguments()[0].getTypeName();

                    // TODO
                    // 判断innerClsName为java.util.List<java.lang.Double>时，肯定报ClassNotFoundException异常，需要特殊处理

                    if (innerClsName == null || StringUtils.equals(innerClsName, "T")) {
                        return ll;
                    }
                    innerCls = Class.forName(innerClsName);
                } catch (ClassNotFoundException e) {
//					throw ErrorUtils.toRuntimeException(e);
//					e.printStackTrace();
                    log.error(e.getMessage());
                    if(log.isDebugEnabled()){
                        e.printStackTrace();
                    }
                    innerCls = null;
                    return ll;
                }
            }
            List xxll = new ArrayList(ll.size());
            for (Object l : ll) {
                if (l == null) {
                    i++;
                    continue;
                }
                if (innerCls == null || innerCls == Object.class) {
                    xxll.add(l);
                } else {
                    xxll.add(fromObject(innerCls, l, null, innerCls));
                }

                i++;
            }
            return xxll;
        } else if (src != null) {
            // ??
            System.err.println("fromObject 未知的类型 :" + src);
            return src;
        } else {
            return src;
        }
    }

}
