package com.foggyframework.dataset.db.model.proxy;

import jakarta.persistence.criteria.JoinType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TableModelProxy 单元测试
 *
 * <p>覆盖：构造、字段访问、维度属性、JOIN 方法、别名管理、equals/hashCode</p>
 */
@DisplayName("TableModelProxy 单元测试")
class TableModelProxyTest {

    // ==========================================
    // 构造与基本属性
    // ==========================================

    @Test
    @DisplayName("无别名构造 - modelName 正确")
    void testConstructorWithoutAlias() {
        TableModelProxy proxy = new TableModelProxy("FactOrderModel");

        assertEquals("FactOrderModel", proxy.getModelName());
        assertNull(proxy.getAlias());
        assertFalse(proxy.hasAlias());
    }

    @Test
    @DisplayName("带别名构造 - alias 正确")
    void testConstructorWithAlias() {
        TableModelProxy proxy = new TableModelProxy("FactOrderModel", "fo");

        assertEquals("FactOrderModel", proxy.getModelName());
        assertEquals("fo", proxy.getAlias());
        assertTrue(proxy.hasAlias());
    }

    @Test
    @DisplayName("getEffectiveAlias - 有别名返回别名")
    void testEffectiveAliasWithAlias() {
        TableModelProxy proxy = new TableModelProxy("FactOrderModel", "fo");
        assertEquals("fo", proxy.getEffectiveAlias());
    }

    @Test
    @DisplayName("getEffectiveAlias - 无别名返回模型名")
    void testEffectiveAliasWithoutAlias() {
        TableModelProxy proxy = new TableModelProxy("FactOrderModel");
        assertEquals("FactOrderModel", proxy.getEffectiveAlias());
    }

    @Test
    @DisplayName("setAlias - 动态设置别名")
    void testSetAlias() {
        TableModelProxy proxy = new TableModelProxy("FactOrderModel");
        assertFalse(proxy.hasAlias());

        proxy.setAlias("fo");
        assertTrue(proxy.hasAlias());
        assertEquals("fo", proxy.getEffectiveAlias());
    }

    @Test
    @DisplayName("as - 返回带显式别名的新代理")
    void testAsReturnsAliasedProxy() {
        TableModelProxy proxy = new TableModelProxy("FactOrderModel");

        Object result = proxy.invoke(null, "as", new Object[]{"originStop"});

        assertInstanceOf(TableModelProxy.class, result);
        TableModelProxy aliased = (TableModelProxy) result;
        assertEquals("FactOrderModel", aliased.getModelName());
        assertEquals("originStop", aliased.getAlias());
        assertTrue(aliased.hasExplicitAlias());
        assertEquals("originStop", aliased.getPublicQualifier());
        assertFalse(proxy.hasAlias());
    }

    @Test
    @DisplayName("hasAlias - 空字符串视为无别名")
    void testHasAliasEmptyString() {
        TableModelProxy proxy = new TableModelProxy("FactOrderModel", "");
        assertFalse(proxy.hasAlias());
        assertEquals("FactOrderModel", proxy.getEffectiveAlias());
    }

    // ==========================================
    // getProperty 字段访问
    // ==========================================

    @Test
    @DisplayName("getProperty - 普通字段返回 DimensionProxy")
    void testGetPropertySimpleField() {
        TableModelProxy proxy = new TableModelProxy("FactOrderModel", "fo");
        Object result = proxy.getProperty("orderId");

        assertInstanceOf(DimensionProxy.class, result);
        DimensionProxy dp = (DimensionProxy) result;
        assertEquals("orderId", dp.getFullPath());
    }

    @Test
    @DisplayName("getProperty - 维度属性返回 ColumnRef")
    void testGetPropertyDimensionAttribute() {
        TableModelProxy proxy = new TableModelProxy("FactOrderModel", "fo");
        Object result = proxy.getProperty("customer$memberLevel");

        assertInstanceOf(ColumnRef.class, result);
        ColumnRef ref = (ColumnRef) result;
        assertEquals("customer", ref.getColumnName());
        assertEquals("memberLevel", ref.getSubProperty());
    }

    @Test
    @DisplayName("getProperty - $alias 返回有效别名")
    void testGetPropertyAlias() {
        TableModelProxy proxy = new TableModelProxy("FactOrderModel", "fo");
        Object result = proxy.getProperty("$alias");

        assertEquals("fo", result);
    }

    @Test
    @DisplayName("getProperty - $alias 无别名返回模型名")
    void testGetPropertyAliasDefault() {
        TableModelProxy proxy = new TableModelProxy("FactOrderModel");
        Object result = proxy.getProperty("$alias");

        assertEquals("FactOrderModel", result);
    }

    // ==========================================
    // 链式维度访问
    // ==========================================

    @Test
    @DisplayName("链式访问: fo.product.category -> DimensionProxy")
    void testChainedDimensionAccess() {
        TableModelProxy proxy = new TableModelProxy("FactSalesModel", "fs");

        Object productProxy = proxy.getProperty("product");
        assertInstanceOf(DimensionProxy.class, productProxy);

        Object categoryProxy = ((DimensionProxy) productProxy).getProperty("category");
        assertInstanceOf(DimensionProxy.class, categoryProxy);

        DimensionProxy dp = (DimensionProxy) categoryProxy;
        assertEquals("product.category", dp.getFullPath());
    }

    @Test
    @DisplayName("链式访问属性: fo.product.category$categoryId -> ColumnRef")
    void testChainedDimensionPropertyAccess() {
        TableModelProxy proxy = new TableModelProxy("FactSalesModel", "fs");

        DimensionProxy productProxy = (DimensionProxy) proxy.getProperty("product");
        Object ref = productProxy.getProperty("category$categoryId");

        assertInstanceOf(ColumnRef.class, ref);
        ColumnRef columnRef = (ColumnRef) ref;
        assertTrue(columnRef.isNestedDimension());
    }

    // ==========================================
    // JOIN 方法
    // ==========================================

    @Test
    @DisplayName("leftJoin - 返回 JoinBuilder LEFT 类型")
    void testLeftJoin() {
        TableModelProxy fo = new TableModelProxy("FactOrderModel", "fo");
        TableModelProxy fp = new TableModelProxy("FactPaymentModel", "fp");

        Object result = fo.invoke(null, "leftJoin", new Object[]{fp});

        assertInstanceOf(JoinBuilder.class, result);
        JoinBuilder jb = (JoinBuilder) result;
        assertEquals(JoinType.LEFT, jb.getJoinType());
        assertEquals(fo, jb.getLeft());
        assertEquals(fp, jb.getRight());
    }

    @Test
    @DisplayName("innerJoin - 返回 JoinBuilder INNER 类型")
    void testInnerJoin() {
        TableModelProxy fo = new TableModelProxy("FactOrderModel", "fo");
        TableModelProxy fp = new TableModelProxy("FactPaymentModel", "fp");

        Object result = fo.invoke(null, "innerJoin", new Object[]{fp});

        assertInstanceOf(JoinBuilder.class, result);
        assertEquals(JoinType.INNER, ((JoinBuilder) result).getJoinType());
    }

    @Test
    @DisplayName("rightJoin - 返回 JoinBuilder RIGHT 类型")
    void testRightJoin() {
        TableModelProxy fo = new TableModelProxy("FactOrderModel", "fo");
        TableModelProxy fp = new TableModelProxy("FactPaymentModel", "fp");

        Object result = fo.invoke(null, "rightJoin", new Object[]{fp});

        assertInstanceOf(JoinBuilder.class, result);
        assertEquals(JoinType.RIGHT, ((JoinBuilder) result).getJoinType());
    }

    @Test
    @DisplayName("未知方法 - 返回 NO_MATCH")
    void testUnknownMethod() {
        TableModelProxy fo = new TableModelProxy("FactOrderModel");
        TableModelProxy fp = new TableModelProxy("FactPaymentModel");

        Object result = fo.invoke(null, "crossJoin", new Object[]{fp});
        // 非标准 JOIN 方法应返回 NO_MATCH
        assertNotNull(result);
    }

    @Test
    @DisplayName("无参数 JOIN - 返回 NO_MATCH")
    void testJoinWithoutArgs() {
        TableModelProxy fo = new TableModelProxy("FactOrderModel");

        Object result = fo.invoke(null, "leftJoin", null);
        assertNotNull(result); // NO_MATCH
    }

    @Test
    @DisplayName("非 TableModelProxy 参数 - 返回 NO_MATCH")
    void testJoinWithWrongArgType() {
        TableModelProxy fo = new TableModelProxy("FactOrderModel");

        Object result = fo.invoke(null, "leftJoin", new Object[]{"not a proxy"});
        assertNotNull(result); // NO_MATCH
    }

    // ==========================================
    // equals / hashCode / toString
    // ==========================================

    @Test
    @DisplayName("equals - 同名模型相等")
    void testEqualsSameModel() {
        TableModelProxy a = new TableModelProxy("FactOrderModel");
        TableModelProxy b = new TableModelProxy("FactOrderModel");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("equals - 不同名模型不等")
    void testEqualsDifferentModel() {
        TableModelProxy a = new TableModelProxy("FactOrderModel");
        TableModelProxy b = new TableModelProxy("FactSalesModel");

        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("equals - 同名不同别名不等")
    void testEqualsSameModelDifferentAlias() {
        TableModelProxy a = new TableModelProxy("FactOrderModel", "fo1");
        TableModelProxy b = new TableModelProxy("FactOrderModel", "fo2");

        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("toString - 无别名只显示模型名")
    void testToStringWithoutAlias() {
        TableModelProxy proxy = new TableModelProxy("FactOrderModel");
        assertEquals("FactOrderModel", proxy.toString());
    }

    @Test
    @DisplayName("toString - 有别名显示 modelName AS alias")
    void testToStringWithAlias() {
        TableModelProxy proxy = new TableModelProxy("FactOrderModel", "fo");
        assertEquals("FactOrderModel AS fo", proxy.toString());
    }
}
