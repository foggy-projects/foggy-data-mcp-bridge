package com.foggyframework.dataset.client.test.support;

import com.foggyframework.dataset.client.proxy.converter.list.*;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

/**
 * List 类型转换器单元测试
 */
public class ListConverterTest {

    @Test
    void testListStringReturnConverter() {
        ListStringReturnConverter converter = new ListStringReturnConverter();

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("name", "Alice");
        items.add(item1);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("name", "Bob");
        items.add(item2);

        List<String> result = (List<String>) converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals("Alice", result.get(0));
        Assertions.assertEquals("Bob", result.get(1));
    }

    @Test
    void testListIntegerReturnConverter() {
        ListIntegerReturnConverter converter = new ListIntegerReturnConverter();

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("count", 100);
        items.add(item1);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("count", 200);
        items.add(item2);

        List<Integer> result = (List<Integer>) converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(100, result.get(0));
        Assertions.assertEquals(200, result.get(1));
    }

    @Test
    void testListLongReturnConverter() {
        ListLongReturnConverter converter = new ListLongReturnConverter();

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("id", 1000L);
        items.add(item1);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("id", 2000L);
        items.add(item2);

        List<Long> result = (List<Long>) converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(1000L, result.get(0));
        Assertions.assertEquals(2000L, result.get(1));
    }

    @Test
    void testListDoubleReturnConverter() {
        ListDoubleReturnConverter converter = new ListDoubleReturnConverter();

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("value", 3.14);
        items.add(item1);

        List<Double> result = (List<Double>) converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(3.14, result.get(0), 0.001);
    }

    @Test
    void testListBigDecimalReturnConverter() {
        ListBigDecimalReturnConverter converter = new ListBigDecimalReturnConverter();

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("amount", new BigDecimal("100.50"));
        items.add(item1);

        List<BigDecimal> result = (List<BigDecimal>) converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(new BigDecimal("100.50"), result.get(0));
    }

    @Test
    void testListBooleanReturnConverter() {
        ListBooleanReturnConverter converter = new ListBooleanReturnConverter();

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("enabled", true);
        items.add(item1);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("enabled", false);
        items.add(item2);

        List<Boolean> result = (List<Boolean>) converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertTrue(result.get(0));
        Assertions.assertFalse(result.get(1));
    }

    @Test
    void testListDateReturnConverter() {
        ListDateReturnConverter converter = new ListDateReturnConverter();

        Date now = new Date();
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("createTime", now);
        items.add(item1);

        List<Date> result = (List<Date>) converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(now, result.get(0));
    }

    @Test
    void testListConverter_EmptyList() {
        ListStringReturnConverter converter = new ListStringReturnConverter();

        List<Map<String, Object>> items = new ArrayList<>();
        List<String> result = (List<String>) converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void testListConverter_ConvertPagingResult() {
        ListStringReturnConverter converter = new ListStringReturnConverter();

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new HashMap<>();
        item1.put("name", "Test");
        items.add(item1);

        PagingResultImpl<Map<String, Object>> pagingResult = new PagingResultImpl<>();
        pagingResult.setItems(items);
        pagingResult.setTotal(100L);
        pagingResult.setStart(0);
        pagingResult.setLimit(10);

        List<String> result = (List<String>) converter.convertPagingResult(pagingResult);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals("Test", result.get(0));
    }

    @Test
    void testListConverter_DefaultMaxLimit() {
        ListStringReturnConverter converter = new ListStringReturnConverter();
        Assertions.assertEquals(99999, converter.getDefaultMaxLimit());
    }

    @Test
    void testListConverter_DefaultReturnTotal() {
        ListStringReturnConverter converter = new ListStringReturnConverter();
        Assertions.assertFalse(converter.getDefaultReturnTotal());
    }
}
