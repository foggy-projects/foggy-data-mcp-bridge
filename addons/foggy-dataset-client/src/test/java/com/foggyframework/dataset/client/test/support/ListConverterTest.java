package com.foggyframework.dataset.client.test.support;

import com.foggyframework.dataset.client.proxy.converter.list.*;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * List 类型转换器单元测试
 */
public class ListConverterTest {

    /**
     * 测试 convertList - 实际使用中 RowMapper 已将数据转换为目标类型
     * convertList 接收的是已经转换好的 List<T>，而非 List<Map>
     */
    @Test
    void testListStringReturnConverter() {
        ListStringReturnConverter converter = new ListStringReturnConverter();

        // 模拟 RowMapper 已转换后的结果
        List<String> items = new ArrayList<>();
        items.add("Alice");
        items.add("Bob");

        List<String> result = (List<String>) converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals("Alice", result.get(0));
        Assertions.assertEquals("Bob", result.get(1));
    }

    @Test
    void testListIntegerReturnConverter() {
        ListIntegerReturnConverter converter = new ListIntegerReturnConverter();

        List<Integer> items = new ArrayList<>();
        items.add(100);
        items.add(200);

        List<Integer> result = (List<Integer>) converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(100, result.get(0));
        Assertions.assertEquals(200, result.get(1));
    }

    @Test
    void testListLongReturnConverter() {
        ListLongReturnConverter converter = new ListLongReturnConverter();

        List<Long> items = new ArrayList<>();
        items.add(1000L);
        items.add(2000L);

        List<Long> result = (List<Long>) converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(1000L, result.get(0));
        Assertions.assertEquals(2000L, result.get(1));
    }

    @Test
    void testListDoubleReturnConverter() {
        ListDoubleReturnConverter converter = new ListDoubleReturnConverter();

        List<Double> items = new ArrayList<>();
        items.add(3.14);

        List<Double> result = (List<Double>) converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(3.14, result.get(0), 0.001);
    }

    @Test
    void testListBigDecimalReturnConverter() {
        ListBigDecimalReturnConverter converter = new ListBigDecimalReturnConverter();

        List<BigDecimal> items = new ArrayList<>();
        items.add(new BigDecimal("100.50"));

        List<BigDecimal> result = (List<BigDecimal>) converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(new BigDecimal("100.50"), result.get(0));
    }

    @Test
    void testListBooleanReturnConverter() {
        ListBooleanReturnConverter converter = new ListBooleanReturnConverter();

        List<Boolean> items = new ArrayList<>();
        items.add(true);
        items.add(false);

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
        List<Date> items = new ArrayList<>();
        items.add(now);

        List<Date> result = (List<Date>) converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(now, result.get(0));
    }

    @Test
    void testListConverter_EmptyList() {
        ListStringReturnConverter converter = new ListStringReturnConverter();

        List<String> items = new ArrayList<>();
        List<String> result = (List<String>) converter.convertList(0, 10, items);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void testListConverter_ConvertPagingResult() {
        ListStringReturnConverter converter = new ListStringReturnConverter();

        List<String> items = new ArrayList<>();
        items.add("Test");

        PagingResultImpl<String> pagingResult = new PagingResultImpl<>();
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
