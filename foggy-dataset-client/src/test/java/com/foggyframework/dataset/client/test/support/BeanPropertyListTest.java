package com.foggyframework.dataset.client.test.support;

import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import com.foggyframework.dataset.client.proxy.on_duplicate.BeanPropertyList;
import com.foggyframework.dataset.client.proxy.on_duplicate.BeanPropertyListList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * BeanPropertyList 和 BeanPropertyListList 单元测试
 */
public class BeanPropertyListTest {

    @Test
    void testBeanPropertyList_Size() {
        OrderForm order = new OrderForm(1L, "ORD001", new BigDecimal("100.00"), 1, 1L);
        List<BeanProperty> properties = BeanInfoHelper.getClassHelper(OrderForm.class).getFieldProperties();

        BeanPropertyList propertyList = new BeanPropertyList(order, properties);

        Assertions.assertEquals(properties.size(), propertyList.size());
    }

    @Test
    void testBeanPropertyList_Get() {
        OrderForm order = new OrderForm(1L, "ORD001", new BigDecimal("100.00"), 1, 1L);
        List<BeanProperty> properties = BeanInfoHelper.getClassHelper(OrderForm.class).getFieldProperties();

        BeanPropertyList propertyList = new BeanPropertyList(order, properties);

        // 验证可以获取属性值（属性顺序可能不固定，所以检查是否包含期望值）
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < propertyList.size(); i++) {
            values.add(propertyList.get(i));
        }

        Assertions.assertTrue(values.contains(1L) || values.stream().anyMatch(v -> v instanceof Long));
        Assertions.assertTrue(values.contains("ORD001"));
    }

    @Test
    void testBeanPropertyList_EmptyBean() {
        OrderForm order = new OrderForm();
        List<BeanProperty> properties = BeanInfoHelper.getClassHelper(OrderForm.class).getFieldProperties();

        BeanPropertyList propertyList = new BeanPropertyList(order, properties);

        Assertions.assertEquals(properties.size(), propertyList.size());
        // 所有值应该为 null
        for (int i = 0; i < propertyList.size(); i++) {
            Assertions.assertNull(propertyList.get(i));
        }
    }

    @Test
    void testBeanPropertyListList_Size() {
        List<OrderForm> orders = new ArrayList<>();
        orders.add(new OrderForm(1L, "ORD001", new BigDecimal("100.00"), 1, 1L));
        orders.add(new OrderForm(2L, "ORD002", new BigDecimal("200.00"), 2, 1L));

        List<BeanProperty> properties = BeanInfoHelper.getClassHelper(OrderForm.class).getFieldProperties();

        BeanPropertyListList listList = new BeanPropertyListList(orders, properties);

        Assertions.assertEquals(2, listList.size());
    }

    @Test
    void testBeanPropertyListList_Get() {
        List<OrderForm> orders = new ArrayList<>();
        orders.add(new OrderForm(1L, "ORD001", new BigDecimal("100.00"), 1, 1L));
        orders.add(new OrderForm(2L, "ORD002", new BigDecimal("200.00"), 2, 1L));

        List<BeanProperty> properties = BeanInfoHelper.getClassHelper(OrderForm.class).getFieldProperties();

        BeanPropertyListList listList = new BeanPropertyListList(orders, properties);

        // 每个元素应该是一个 BeanPropertyList
        Object first = listList.get(0);
        Assertions.assertNotNull(first);
        Assertions.assertTrue(first instanceof BeanPropertyList);

        BeanPropertyList firstList = (BeanPropertyList) first;
        Assertions.assertEquals(properties.size(), firstList.size());
    }

    @Test
    void testBeanPropertyListList_Empty() {
        List<OrderForm> orders = new ArrayList<>();
        List<BeanProperty> properties = BeanInfoHelper.getClassHelper(OrderForm.class).getFieldProperties();

        BeanPropertyListList listList = new BeanPropertyListList(orders, properties);

        Assertions.assertEquals(0, listList.size());
    }

    @Test
    void testBeanPropertyList_Iteration() {
        OrderForm order = new OrderForm(1L, "ORD001", new BigDecimal("100.00"), 1, 1L);
        List<BeanProperty> properties = BeanInfoHelper.getClassHelper(OrderForm.class).getFieldProperties();

        BeanPropertyList propertyList = new BeanPropertyList(order, properties);

        int count = 0;
        for (Object value : propertyList) {
            count++;
        }

        Assertions.assertEquals(properties.size(), count);
    }

    @Test
    void testBeanPropertyListList_Iteration() {
        List<OrderForm> orders = new ArrayList<>();
        orders.add(new OrderForm(1L, "ORD001", new BigDecimal("100.00"), 1, 1L));
        orders.add(new OrderForm(2L, "ORD002", new BigDecimal("200.00"), 2, 1L));
        orders.add(new OrderForm(3L, "ORD003", new BigDecimal("300.00"), 3, 1L));

        List<BeanProperty> properties = BeanInfoHelper.getClassHelper(OrderForm.class).getFieldProperties();

        BeanPropertyListList listList = new BeanPropertyListList(orders, properties);

        int count = 0;
        for (Object item : listList) {
            Assertions.assertTrue(item instanceof BeanPropertyList);
            count++;
        }

        Assertions.assertEquals(3, count);
    }
}
