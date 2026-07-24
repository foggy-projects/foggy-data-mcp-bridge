package com.foggyframework.dataset.model.proxy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DimensionProxy 单元测试
 *
 * <p>覆盖：路径构建、链式访问、ColumnRef 转换、路径格式化</p>
 */
@DisplayName("DimensionProxy 单元测试")
class DimensionProxyTest {

    private final TableModelProxy rootProxy = new TableModelProxy("FactSalesModel", "fs");

    // ==========================================
    // 构造与路径
    // ==========================================

    @Test
    @DisplayName("单层维度路径")
    void testSingleDimension() {
        DimensionProxy proxy = new DimensionProxy(rootProxy, "product");

        assertEquals("product", proxy.getFullPath());
        assertEquals("product", proxy.getAliasPath());
        assertEquals(rootProxy, proxy.getRootProxy());
    }

    @Test
    @DisplayName("链式维度 - getProperty 返回新 DimensionProxy")
    void testChainedDimensionAccess() {
        DimensionProxy product = new DimensionProxy(rootProxy, "product");
        Object category = product.getProperty("category");

        assertInstanceOf(DimensionProxy.class, category);
        DimensionProxy categoryProxy = (DimensionProxy) category;
        assertEquals("product.category", categoryProxy.getFullPath());
        assertEquals("product_category", categoryProxy.getAliasPath());
    }

    @Test
    @DisplayName("三层链式维度")
    void testThreeLevelChain() {
        DimensionProxy product = new DimensionProxy(rootProxy, "product");
        DimensionProxy category = (DimensionProxy) product.getProperty("category");
        DimensionProxy subCategory = (DimensionProxy) category.getProperty("subCategory");

        assertEquals("product.category.subCategory", subCategory.getFullPath());
        assertEquals("product_category_subCategory", subCategory.getAliasPath());
    }

    // ==========================================
    // 属性访问 → ColumnRef
    // ==========================================

    @Test
    @DisplayName("维度属性: category$categoryId → ColumnRef")
    void testDimensionProperty() {
        DimensionProxy product = new DimensionProxy(rootProxy, "product");
        Object result = product.getProperty("category$categoryId");

        assertInstanceOf(ColumnRef.class, result);
        ColumnRef ref = (ColumnRef) result;
        assertTrue(ref.hasSubProperty());
        assertEquals("categoryId", ref.getSubProperty());
    }

    @Test
    @DisplayName("直接属性: product$productName → ColumnRef")
    void testDirectProperty() {
        DimensionProxy product = new DimensionProxy(rootProxy, "product");
        // 通过根代理直接调用也应该能正确处理
        // 这里测试 DimensionProxy 本身
        ColumnRef ref = product.toColumnRef("productName");

        assertNotNull(ref);
        assertEquals(rootProxy, ref.getTableModelProxy());
    }

    // ==========================================
    // toColumnRef 转换
    // ==========================================

    @Test
    @DisplayName("toColumnRef() - 无属性")
    void testToColumnRefNoProperty() {
        DimensionProxy proxy = new DimensionProxy(rootProxy, "customer");
        ColumnRef ref = proxy.toColumnRef();

        assertNotNull(ref);
        assertEquals(rootProxy, ref.getTableModelProxy());
    }

    @Test
    @DisplayName("toColumnRef(property) - 带属性")
    void testToColumnRefWithProperty() {
        DimensionProxy proxy = new DimensionProxy(rootProxy, "customer");
        ColumnRef ref = proxy.toColumnRef("email");

        assertNotNull(ref);
        assertEquals(rootProxy, ref.getTableModelProxy());
    }

    // ==========================================
    // toString
    // ==========================================

    @Test
    @DisplayName("toString - 有别名时使用别名前缀")
    void testToStringWithAlias() {
        DimensionProxy proxy = new DimensionProxy(rootProxy, "product");
        String str = proxy.toString();

        assertTrue(str.startsWith("fs."), "Should start with alias 'fs.'");
        assertTrue(str.contains("product"));
    }

    @Test
    @DisplayName("toString - 无别名时使用模型名前缀")
    void testToStringWithoutAlias() {
        TableModelProxy noAliasProxy = new TableModelProxy("FactSalesModel");
        DimensionProxy proxy = new DimensionProxy(noAliasProxy, "product");
        String str = proxy.toString();

        assertTrue(str.startsWith("FactSalesModel."));
    }

    @Test
    @DisplayName("toString - 链式路径完整展示")
    void testToStringChained() {
        DimensionProxy product = new DimensionProxy(rootProxy, "product");
        DimensionProxy category = (DimensionProxy) product.getProperty("category");
        String str = category.toString();

        assertTrue(str.contains("product.category"),
                "toString should contain full path: " + str);
    }
}
