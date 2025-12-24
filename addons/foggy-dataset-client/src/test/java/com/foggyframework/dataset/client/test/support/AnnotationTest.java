package com.foggyframework.dataset.client.test.support;

import com.foggyframework.dataset.client.annotates.DataSetClient;
import com.foggyframework.dataset.client.annotates.DataSetQuery;
import com.foggyframework.dataset.client.annotates.OnDuplicate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

/**
 * 注解单元测试
 */
public class AnnotationTest {

    @Test
    void testDataSetClientAnnotation() {
        DataSetClient annotation = TestDataSetClient.class.getAnnotation(DataSetClient.class);

        Assertions.assertNotNull(annotation);
        Assertions.assertEquals("", annotation.value());
        Assertions.assertEquals("", annotation.name());
        Assertions.assertEquals("", annotation.contextId());
        Assertions.assertTrue(annotation.primary());
    }

    @Test
    void testDataSetQueryAnnotation_Default() throws NoSuchMethodException {
        Method method = TestDataSetClient.class.getMethod("findUserDetail", java.util.Map.class);
        DataSetQuery annotation = method.getAnnotation(DataSetQuery.class);

        Assertions.assertNotNull(annotation);
        Assertions.assertEquals("UserDetail", annotation.name());
        Assertions.assertEquals(100, annotation.maxLimit());
        Assertions.assertTrue(annotation.returnTotal());
    }

    @Test
    void testDataSetQueryAnnotation_NoAnnotation() throws NoSuchMethodException {
        Method method = TestDataSetClient.class.getMethod("findUser", java.util.Map.class);
        DataSetQuery annotation = method.getAnnotation(DataSetQuery.class);

        Assertions.assertNull(annotation);
    }

    @Test
    void testOnDuplicateAnnotation_Simple() throws NoSuchMethodException {
        Method method = TestDataSetClient.class.getMethod("saveOrder", OrderForm.class);
        OnDuplicate annotation = method.getAnnotation(OnDuplicate.class);

        Assertions.assertNotNull(annotation);
        Assertions.assertEquals("t_order", annotation.table());
        Assertions.assertEquals("", annotation.versionColumn());
    }

    @Test
    void testOnDuplicateAnnotation_WithVersion() throws NoSuchMethodException {
        Method method = TestDataSetClient.class.getMethod("batchSaveOrders", java.util.List.class);
        OnDuplicate annotation = method.getAnnotation(OnDuplicate.class);

        Assertions.assertNotNull(annotation);
        Assertions.assertEquals("t_order", annotation.table());
        Assertions.assertEquals("version", annotation.versionColumn());
    }

    @Test
    void testAnnotationInheritance() {
        Assertions.assertTrue(TestDataSetClient.class.isInterface());
        Assertions.assertTrue(TestDataSetClient.class.isAnnotationPresent(DataSetClient.class));
    }

    @Test
    void testDataSetClientWithCustomName() {
        @DataSetClient(name = "customClient", contextId = "ctx1")
        interface CustomClient {
            void test();
        }

        DataSetClient annotation = CustomClient.class.getAnnotation(DataSetClient.class);
        Assertions.assertEquals("customClient", annotation.name());
        Assertions.assertEquals("ctx1", annotation.contextId());
    }

    @Test
    void testDataSetQueryDefaultValues() throws NoSuchMethodException {
        Method method = TestDataSetClient.class.getMethod("findUserDetailById", Long.class);
        DataSetQuery annotation = method.getAnnotation(DataSetQuery.class);

        Assertions.assertNotNull(annotation);
        Assertions.assertEquals("UserDetail", annotation.name());
        Assertions.assertEquals(999, annotation.maxLimit()); // default
        Assertions.assertTrue(annotation.returnTotal()); // default
    }
}
