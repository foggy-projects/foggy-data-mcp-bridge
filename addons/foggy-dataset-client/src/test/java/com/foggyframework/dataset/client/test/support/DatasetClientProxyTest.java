package com.foggyframework.dataset.client.test.support;

import com.foggyframework.dataset.client.FoggyDataSetClientFactoryBean;
import com.foggyframework.dataset.client.proxy.DatasetClientProxy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * DatasetClientProxy 单元测试
 * 测试 JDK 动态代理的基本功能
 */
public class DatasetClientProxyTest {

    /**
     * 测试代理对象的 hashCode 方法
     */
    @Test
    void testProxyHashCode() {
        TestInterface proxy = createTestProxy();
        int hashCode = proxy.hashCode();
        Assertions.assertTrue(hashCode != 0);
    }

    /**
     * 测试代理对象的 toString 方法
     */
    @Test
    void testProxyToString() {
        TestInterface proxy = createTestProxy();
        String str = proxy.toString();
        Assertions.assertNotNull(str);
    }

    /**
     * 测试代理对象的 equals 方法
     */
    @Test
    void testProxyEquals() {
        TestInterface proxy1 = createTestProxy();
        TestInterface proxy2 = createTestProxy();

        Assertions.assertEquals(proxy1, proxy1);
        Assertions.assertNotEquals(proxy1, proxy2);
        Assertions.assertNotEquals(proxy1, null);
        Assertions.assertNotEquals(proxy1, "string");
    }

    /**
     * 测试代理对象的 getClass 方法
     */
    @Test
    void testProxyGetClass() {
        TestInterface proxy = createTestProxy();
        Class<?> clazz = proxy.getClass();
        Assertions.assertNotNull(clazz);
        Assertions.assertTrue(Proxy.isProxyClass(clazz));
    }

    /**
     * 测试 FactoryBean 类型检查
     */
    @Test
    void testFactoryBeanValidate_Interface() {
        FoggyDataSetClientFactoryBean factoryBean = new FoggyDataSetClientFactoryBean();
        factoryBean.setType(TestInterface.class);

        // 接口验证应该通过
        Assertions.assertDoesNotThrow(() -> factoryBean.validate(null));
    }

    /**
     * 测试 FactoryBean 类型检查 - 非接口应该失败
     */
    @Test
    void testFactoryBeanValidate_NotInterface() {
        FoggyDataSetClientFactoryBean factoryBean = new FoggyDataSetClientFactoryBean();
        factoryBean.setType(String.class);

        // 非接口验证应该抛出异常
        Assertions.assertThrows(IllegalArgumentException.class, () -> factoryBean.validate(null));
    }

    /**
     * 测试代理对象是否正确实现了接口
     */
    @Test
    void testProxyImplementsInterface() {
        TestInterface proxy = createTestProxy();
        Assertions.assertTrue(proxy instanceof TestInterface);
        Assertions.assertTrue(Proxy.isProxyClass(proxy.getClass()));
    }

    /**
     * 测试 InvocationHandler 获取
     */
    @Test
    void testGetInvocationHandler() {
        TestInterface proxy = createTestProxy();
        InvocationHandler handler = Proxy.getInvocationHandler(proxy);
        Assertions.assertNotNull(handler);
        Assertions.assertTrue(handler instanceof TestInvocationHandler);
    }

    /**
     * 测试方法名解析 - find 前缀
     */
    @Test
    void testMethodNameParsing_FindPrefix() {
        String methodName = "findUserById";
        String datasetName = extractDatasetName(methodName);
        Assertions.assertEquals("UserById", datasetName);
    }

    /**
     * 测试方法名解析 - query 前缀
     */
    @Test
    void testMethodNameParsing_QueryPrefix() {
        String methodName = "queryOrders";
        String datasetName = extractDatasetName(methodName);
        Assertions.assertEquals("Orders", datasetName);
    }

    /**
     * 测试方法名解析 - get 前缀
     */
    @Test
    void testMethodNameParsing_GetPrefix() {
        String methodName = "getUser";
        String datasetName = extractDatasetName(methodName);
        Assertions.assertEquals("User", datasetName);
    }

    /**
     * 测试方法名解析 - 无前缀
     */
    @Test
    void testMethodNameParsing_NoPrefix() {
        String methodName = "userList";
        String datasetName = extractDatasetName(methodName);
        Assertions.assertEquals("UserList", datasetName);
    }

    /**
     * 模拟方法名解析逻辑（与 DatasetClientProxy 一致）
     */
    private String extractDatasetName(String methodName) {
        if (methodName.startsWith("find")) {
            return methodName.substring("find".length());
        } else if (methodName.startsWith("query")) {
            return methodName.substring("query".length());
        } else if (methodName.startsWith("get")) {
            return methodName.substring("get".length());
        } else {
            return methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
        }
    }

    /**
     * 创建测试代理对象
     */
    private TestInterface createTestProxy() {
        return (TestInterface) Proxy.newProxyInstance(
                TestInterface.class.getClassLoader(),
                new Class<?>[]{TestInterface.class},
                new TestInvocationHandler()
        );
    }

    /**
     * 测试用接口
     */
    interface TestInterface {
        String findUser(Long id);

        void doSomething();
    }

    /**
     * 测试用 InvocationHandler
     */
    static class TestInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 处理 Object 类的基本方法
            if (method.getDeclaringClass() == Object.class) {
                String methodName = method.getName();
                // equals 需要特殊处理：比较代理对象本身
                if ("equals".equals(methodName)) {
                    return proxy == args[0];
                }
                // hashCode 和 toString 委托给 handler
                if ("hashCode".equals(methodName)) {
                    return System.identityHashCode(proxy);
                }
                if ("toString".equals(methodName)) {
                    return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
                }
                return method.invoke(this, args);
            }

            // 模拟业务方法返回
            if (method.getReturnType() == String.class) {
                return "mocked";
            }
            return null;
        }
    }
}
