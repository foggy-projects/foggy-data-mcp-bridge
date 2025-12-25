package com.foggyframework.dataset.client.test.support;

import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.client.proxy.ReturnConverterManagerImpl;
import com.foggyframework.dataset.client.proxy.converter.*;
import com.foggyframework.dataset.model.PagingResult;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;

/**
 * ReturnConverterManager 单元测试
 */
public class ReturnConverterManagerTest {

    private ReturnConverterManagerImpl converterManager;

    @BeforeEach
    void setUp() {
        converterManager = new ReturnConverterManagerImpl();

        // 注册常用转换器
        converterManager.register(List.class, new ListReturnConverter<>());
        converterManager.register(Map.class, new MapReturnConverter<>());
        converterManager.register(PagingResult.class, new PagingReturnConverter<>(PagingResultImpl.class));
        converterManager.register(PagingResultImpl.class, new PagingReturnConverter<>(PagingResultImpl.class));

        // 注册简单类型转换器
        converterManager.register(String.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.STRING_TRANSFORMATTERINSTANCE));
        converterManager.register(Integer.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.INTEGER_TRANSFORMATTERINSTANCE));
        converterManager.register(Long.class, new SimpleObjectReturnConverter<>(ObjectTransFormatter.LONG_TRANSFORMATTERINSTANCE));
    }

    @Test
    void testGetReturnConverter_List() throws NoSuchMethodException {
        Method method = TestDataSetClient.class.getMethod("findUser", Map.class);
        ReturnConverter converter = converterManager.getReturnConverter(method);

        Assertions.assertNotNull(converter);
        Assertions.assertTrue(converter instanceof ListReturnConverter);
    }

    @Test
    void testGetReturnConverter_Map() throws NoSuchMethodException {
        Method method = TestDataSetClient.class.getMethod("queryOrder", Map.class);
        ReturnConverter converter = converterManager.getReturnConverter(method);

        Assertions.assertNotNull(converter);
        // 返回类型是 List<Map>，应该匹配 ListReturnConverter
        Assertions.assertTrue(converter instanceof ListReturnConverter);
    }

    @Test
    void testGetReturnConverter_PagingResult() throws NoSuchMethodException {
        Method method = TestDataSetClient.class.getMethod("findUserDetail", Map.class);
        ReturnConverter converter = converterManager.getReturnConverter(method);

        Assertions.assertNotNull(converter);
        Assertions.assertTrue(converter instanceof PagingReturnConverter);
    }

    @Test
    void testGetReturnConverter_SimpleType_Long() throws NoSuchMethodException {
        Method method = TestDataSetClient.class.getMethod("countUsers");
        ReturnConverter converter = converterManager.getReturnConverter(method);

        Assertions.assertNotNull(converter);
        Assertions.assertTrue(converter instanceof SimpleObjectReturnConverter);
    }

    @Test
    void testGetReturnConverter_Bean() throws NoSuchMethodException {
        Method method = TestDataSetClient.class.getMethod("getUser", Long.class);
        ReturnConverter converter = converterManager.getReturnConverter(method);

        Assertions.assertNotNull(converter);
        // 未注册的类型返回 BeanReturnConverter
        Assertions.assertTrue(converter instanceof BeanReturnConverter);
    }

    @Test
    void testListReturnConverter_ConvertList() {
        ListReturnConverter<List> converter = new ListReturnConverter<>();

        List<String> items = Arrays.asList("a", "b", "c");
        Object result = converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof List);
        Assertions.assertEquals(3, ((List<?>) result).size());
    }

    @Test
    void testListReturnConverter_ConvertPagingResult() {
        ListReturnConverter<List> converter = new ListReturnConverter<>();

        List<String> items = Arrays.asList("a", "b", "c");
        PagingResultImpl<String> pagingResult = new PagingResultImpl<>();
        pagingResult.setItems(items);
        pagingResult.setTotal(100L);
        pagingResult.setStart(0);
        pagingResult.setLimit(10);

        Object result = converter.convertPagingResult(pagingResult);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof List);
        Assertions.assertEquals(3, ((List<?>) result).size());
    }

    @Test
    void testPagingReturnConverter_ConvertList() {
        PagingReturnConverter<PagingResultImpl> converter = new PagingReturnConverter<>(PagingResultImpl.class);

        List<String> items = Arrays.asList("a", "b", "c");
        Object result = converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof PagingResult);

        PagingResult<?> pagingResult = (PagingResult<?>) result;
        Assertions.assertEquals(0, pagingResult.getStart());
        Assertions.assertEquals(10, pagingResult.getLimit());
        Assertions.assertEquals(3, pagingResult.getItems().size());
    }

    @Test
    void testPagingReturnConverter_ConvertPagingResult() {
        PagingReturnConverter<PagingResultImpl> converter = new PagingReturnConverter<>(PagingResultImpl.class);

        List<String> items = Arrays.asList("a", "b", "c");
        PagingResultImpl<String> pagingResult = new PagingResultImpl<>();
        pagingResult.setItems(items);
        pagingResult.setTotal(100L);
        pagingResult.setStart(0);
        pagingResult.setLimit(10);

        Object result = converter.convertPagingResult(pagingResult);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof PagingResult);

        PagingResult<?> pr = (PagingResult<?>) result;
        Assertions.assertEquals(100L, pr.getTotal());
    }

    @Test
    void testMapReturnConverter_ConvertList() {
        MapReturnConverter<Map> converter = new MapReturnConverter<>();

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("id", 1L);
        item1.put("name", "test");
        items.add(item1);

        Object result = converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof Map);
        Assertions.assertEquals(1L, ((Map<?, ?>) result).get("id"));
    }

    @Test
    void testDefaultMaxLimit() {
        ListReturnConverter<List> listConverter = new ListReturnConverter<>();
        Assertions.assertEquals(99999, listConverter.getDefaultMaxLimit());

        PagingReturnConverter<PagingResultImpl> pagingConverter = new PagingReturnConverter<>(PagingResultImpl.class);
        Assertions.assertEquals(10, pagingConverter.getDefaultMaxLimit());
    }

    @Test
    void testDefaultReturnTotal() {
        ListReturnConverter<List> listConverter = new ListReturnConverter<>();
        Assertions.assertFalse(listConverter.getDefaultReturnTotal());

        PagingReturnConverter<PagingResultImpl> pagingConverter = new PagingReturnConverter<>(PagingResultImpl.class);
        Assertions.assertTrue(pagingConverter.getDefaultReturnTotal());
    }
}
