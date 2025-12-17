package com.foggyframework.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.core.ex.RX;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        // 禁用循环引用检测（类似 fastjson 的 DisableCircularReferenceDetect）
        OBJECT_MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        // 忽略未知属性
        OBJECT_MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // 允许序列化空的POJO
        OBJECT_MAPPER.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Object转json字符串
     *
     * @param object 对象
     * @return json字符串
     */
    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw RX.throwB(e);
        }
    }

    public static String toJsonPrettyFormat(Object object) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw RX.throwB(e);
        }
    }

    public static byte[] toBytes(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            throw RX.throwB(e);
        }
    }

    public static <T> T fromJson(InputStream inputStream, Type type) {
        try {
            JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructType(type);
            return OBJECT_MAPPER.readValue(inputStream, javaType);
        } catch (IOException e) {
            throw RX.throwB(e);
        }
    }

    public static <T> T fromJson(byte[] bb, Type type) {
        if (type == void.class || type == Void.class) {
            return null;
        }
        try {
            JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructType(type);
            return OBJECT_MAPPER.readValue(bb, javaType);
        } catch (IOException e) {
            throw RX.throwB(e);
        }
    }

    public static <T> T fromJson(String str, Type type) {
        try {
            JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructType(type);
            return OBJECT_MAPPER.readValue(str, javaType);
        } catch (IOException e) {
            throw RX.throwB(e);
        }
    }

    /**
     * 如果cls是基础类型，尝试跳过转换来处理
     * 没啥用，fromJson能够很好的完成工作了
     *
     * @param bb  字节数组
     * @param cls 类型
     * @return 对象
     * @param <T> 泛型
     */
    @Deprecated
    public static <T> T fromJsonAuto(byte[] bb, Type cls) {
        if (cls == void.class || cls == Void.class) {
            return null;
        }
        return fromJson(bb, cls);
    }

    public static <T> T autoFromJson(Object obj) {
        if (obj instanceof String) {
            try {
                return (T) OBJECT_MAPPER.readValue((String) obj, Object.class);
            } catch (JsonProcessingException e) {
                throw RX.throwB(e);
            }
        } else {
            return (T) obj;
        }
    }

    /**
     * 提供一个方法用来转为json 但是不忽略空值 如果字段为空则返回类型默认值
     *
     * @param object 对象
     * @return json字符串
     */
    public static String toJsonNotIgnoreNull(Object object) {
        try {
            ObjectMapper mapper = OBJECT_MAPPER.copy();
            mapper.enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw RX.throwB(e);
        }
    }

    /**
     * json字符串转Object
     *
     * @param jsonString json字符串
     * @param className  对象的类名
     * @return 对象
     */
    public static <T> T toObject(String jsonString, Class<?> className) {
        try {
            return (T) OBJECT_MAPPER.readValue(jsonString, className);
        } catch (IOException e) {
            throw RX.throwB(e);
        }
    }

    /**
     * json字符串转ArrayList
     *
     * @param jsonString json字符串
     * @param className  类名
     * @return 数组
     */
    public static <T> List<T> toList(String jsonString, Class<?> className) {
        try {
            if (className == null) {
                return (List<T>) OBJECT_MAPPER.readValue(jsonString, new TypeReference<List<Object>>() {});
            }
            JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, className);
            return OBJECT_MAPPER.readValue(jsonString, javaType);
        } catch (IOException e) {
            throw RX.throwB(e);
        }
    }

    public static List toList(String jsonString) {
        try {
            return OBJECT_MAPPER.readValue(jsonString, new TypeReference<List<Object>>() {});
        } catch (IOException e) {
            throw RX.throwB(e);
        }
    }

    /**
     * 将字符串转为map
     *
     * @param jsonStr json字符串
     * @return Map对象
     */
    public static Map toMap(String jsonStr) {
        if (StringUtils.isBlank(jsonStr)) {
            return new HashMap();
        }
        try {
            return OBJECT_MAPPER.readValue(jsonStr, Map.class);
        } catch (Throwable t) {
            throw RX.throwA(t.getMessage(), jsonStr, t);
        }
    }
}
