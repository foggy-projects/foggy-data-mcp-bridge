package com.foggyframework.dataset.model.engine.query;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.engine.formula.ForceEqSqlFormula;
import com.foggyframework.dataset.model.impl.query.DbQueryOrderColumnImpl;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.QueryObject;
import com.foggyframework.dataset.model.spi.support.SimpleQueryObject;
import com.foggyframework.dataset.model.spi.support.SimpleSqlJdbcColumn;
import com.foggyframework.dataset.db.table.SqlColumn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServerIdentifierQuotingTest {

    @Test
    void sqlServerVisitorQuotesPhysicalColumnNamesWithSpaces() {
        QueryObject sale = SimpleQueryObject.of("[Fact].[Sale]", "t1", null);
        DbColumn totalIncludingTax = column(sale, "Total Including Tax", "totalIncludingTax");
        DbColumn invoiceDateKey = column(sale, "Invoice Date Key", "invoiceDateKey");
        DbColumn saleKey = column(sale, "Sale Key", "saleKey");

        JdbcQuery query = new JdbcQuery();
        query.from(sale);
        query.select(totalIncludingTax);
        query.getWhere().and(invoiceDateKey.buildSqlFragment(null, "t1", FDialect.SQLSERVER_DIALECT, ">=?"), 20240101);
        query.addOrder(new DbQueryOrderColumnImpl(saleKey, "desc"));

        SimpleSqlJdbcQueryVisitor visitor = new SimpleSqlJdbcQueryVisitor(null, sqlServerQueryModel(), null);
        query.accept(visitor);

        String sql = visitor.getSql();
        assertTrue(sql.contains("t1.[Total Including Tax] [totalIncludingTax]"));
        assertTrue(sql.contains("t1.[Invoice Date Key] >=?"));
        assertTrue(sql.contains("order by t1.[Sale Key] desc"));
        assertFalse(sql.contains("t1.Total Including Tax"));
        assertFalse(sql.contains("t1.Invoice Date Key"));
        assertFalse(sql.contains("t1.Sale Key"));
    }

    @Test
    void formulaRenderingUsesDialectContextForPhysicalColumnNames() {
        QueryObject sale = SimpleQueryObject.of("[Fact].[Sale]", "t1", null);
        DbColumn invoiceDateKey = column(sale, "Invoice Date Key", "invoiceDateKey");
        JdbcQuery query = new JdbcQuery();

        new ForceEqSqlFormula(null).buildAndAddToJdbcCond(
                query.getWhere(), "===", invoiceDateKey, "t1", null, "AND", FDialect.SQLSERVER_DIALECT);

        JdbcQuery.ValueCond condition = (JdbcQuery.ValueCond) query.getWhere().getConds().get(0);
        assertEquals("t1.[Invoice Date Key] =? ", condition.getSqlFragment());
    }

    @Test
    void simplePhysicalColumnNamesStayUnquoted() {
        QueryObject sale = SimpleQueryObject.of("fact_sale", "t1", null);
        DbColumn totalAmount = column(sale, "total_amount", "totalAmount");

        assertEquals("total_amount", FDialect.SQLSERVER_DIALECT.quoteIdentifierIfNeeded("total_amount"));
        assertEquals("t1.total_amount", totalAmount.getDeclare(null, "t1", FDialect.SQLSERVER_DIALECT));
    }

    private static DbColumn column(QueryObject queryObject, String physicalName, String alias) {
        SqlColumn sqlColumn = new SqlColumn(physicalName, physicalName, Types.INTEGER);
        return new SimpleSqlJdbcColumn(queryObject, sqlColumn, alias, alias, alias);
    }

    private static JdbcQueryModel sqlServerQueryModel() {
        return (JdbcQueryModel) Proxy.newProxyInstance(
                JdbcQueryModel.class.getClassLoader(),
                new Class<?>[]{JdbcQueryModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDialect" -> FDialect.SQLSERVER_DIALECT;
                    case "getAlias" -> ((QueryObject) args[0]).getAlias();
                    case "toString" -> "sqlServerQueryModel";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == void.class) {
            return null;
        }
        return 0;
    }
}
