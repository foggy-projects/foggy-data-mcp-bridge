package com.foggyframework.dataset.graphql.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.api.QueryFacadeRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GraphQL → DSL 转换器测试
 */
public class GraphqlToDslConverterTest {

    private final GraphqlToDslConverter converter = new GraphqlToDslConverter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSimpleQuery() throws Exception {
        String graphqlQuery = """
                query {
                    factOrder {
                        orderId
                        orderStatus
                        totalAmount
                    }
                }
                """;

        PagingRequest<DbQueryRequestDef> result = converter.convert(graphqlQuery, new HashMap<>());

        assertNotNull(result);
        assertEquals("FactOrderQueryModel", result.getParam().getQueryModel());
        assertEquals(3, result.getParam().getColumns().size());
        assertTrue(result.getParam().getColumns().contains("orderId"));
        assertTrue(result.getParam().getColumns().contains("orderStatus"));
        assertTrue(result.getParam().getColumns().contains("totalAmount"));

        System.out.println("转换结果: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

    @Test
    public void testQueryWithWhere() throws Exception {
        String graphqlQuery = """
                query {
                    factOrder(
                        where: {
                            orderStatus: { _eq: "COMPLETED" }
                            totalAmount: { _gte: 100 }
                        }
                    ) {
                        orderId
                        totalAmount
                    }
                }
                """;

        PagingRequest<DbQueryRequestDef> result = converter.convert(graphqlQuery, new HashMap<>());

        assertNotNull(result);
        assertEquals(2, result.getParam().getSlice().size());

        SliceRequestDef slice1 = result.getParam().getSlice().get(0);
        assertEquals("orderStatus", slice1.getField());
        assertEquals("=", slice1.getOp());
        assertEquals("COMPLETED", slice1.getValue());

        SliceRequestDef slice2 = result.getParam().getSlice().get(1);
        assertEquals("totalAmount", slice2.getField());
        assertEquals(">=", slice2.getOp());
        assertEquals(100, slice2.getValue());

        System.out.println("转换结果: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

    @Test
    public void testQueryWithOrCondition() throws Exception {
        String graphqlQuery = """
                query {
                    factOrder(
                        where: {
                            _or: [
                                { orderStatus: { _eq: "COMPLETED" } }
                                { orderStatus: { _eq: "SHIPPED" } }
                            ]
                        }
                    ) {
                        orderId
                    }
                }
                """;

        PagingRequest<DbQueryRequestDef> result = converter.convert(graphqlQuery, new HashMap<>());

        assertNotNull(result);
        assertEquals(1, result.getParam().getSlice().size());

        SliceRequestDef orSlice = result.getParam().getSlice().get(0);
        assertNotNull(orSlice.getValue());
        assertTrue(orSlice.getValue() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> orCondition = (Map<String, Object>) orSlice.getValue();
        assertTrue(orCondition.containsKey("$or"));

        System.out.println("转换结果: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

    @Test
    public void testQueryWithNestedDimension() throws Exception {
        String graphqlQuery = """
                query {
                    factOrder {
                        orderId
                        customer {
                            name
                            customerType
                        }
                    }
                }
                """;

        PagingRequest<DbQueryRequestDef> result = converter.convert(graphqlQuery, new HashMap<>());

        assertNotNull(result);
        assertTrue(result.getParam().getColumns().contains("orderId"));
        assertTrue(result.getParam().getColumns().contains("customer$caption"));
        assertTrue(result.getParam().getColumns().contains("customer$customerType"));

        System.out.println("转换结果: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

    @Test
    public void testQueryWithOrderBy() throws Exception {
        String graphqlQuery = """
                query {
                    factOrder(
                        orderBy: [
                            { totalAmount: desc }
                            { orderId: asc }
                        ]
                    ) {
                        orderId
                        totalAmount
                    }
                }
                """;

        PagingRequest<DbQueryRequestDef> result = converter.convert(graphqlQuery, new HashMap<>());

        assertNotNull(result);
        assertEquals(2, result.getParam().getOrderBy().size());
        assertEquals("totalAmount", result.getParam().getOrderBy().get(0).getField());
        assertEquals("desc", result.getParam().getOrderBy().get(0).getDir());
        assertEquals("orderId", result.getParam().getOrderBy().get(1).getField());
        assertEquals("asc", result.getParam().getOrderBy().get(1).getDir());

        System.out.println("转换结果: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

    @Test
    public void testQueryWithPagination() throws Exception {
        String graphqlQuery = """
                query {
                    factOrder(
                        limit: 20
                        offset: 40
                    ) {
                        orderId
                    }
                }
                """;

        PagingRequest<DbQueryRequestDef> result = converter.convert(graphqlQuery, new HashMap<>());

        assertNotNull(result);
        assertEquals(20, result.getLimit());
        assertEquals(40, result.getStart());

        System.out.println("转换结果: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

    @Test
    public void convertsExecutionPathToStableQueryFacadeRequest() {
        String graphqlQuery = """
                query {
                    factOrder(limit: 20, offset: 40) {
                        orderId
                    }
                }
                """;

        QueryFacadeRequest result = converter.convertRequest(graphqlQuery, Map.of());

        assertEquals("FactOrderQueryModel", result.getQuery().get("queryModel"));
        assertEquals(20, result.getLimit());
        assertEquals(40, result.getStart());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getQuery().put("queryModel", "mutated"));
    }

    @Test
    public void testComplexQuery() throws Exception {
        String graphqlQuery = """
                query {
                    factOrder(
                        where: {
                            orderStatus: { _in: ["COMPLETED", "SHIPPED"] }
                            totalAmount: { _gte: 100 }
                            _or: [
                                { customer: { customerType: { _eq: "VIP" } } }
                                { totalAmount: { _gte: 1000 } }
                            ]
                        }
                        orderBy: [{ totalAmount: desc }]
                        limit: 20
                    ) {
                        orderId
                        orderStatus
                        totalAmount
                        customer {
                            name
                            customerType
                        }
                    }
                }
                """;

        PagingRequest<DbQueryRequestDef> result = converter.convert(graphqlQuery, new HashMap<>());

        assertNotNull(result);
        assertEquals("FactOrderQueryModel", result.getParam().getQueryModel());
        // orderId, orderStatus, totalAmount, customer$caption, customer$customerType = 5
        assertEquals(5, result.getParam().getColumns().size());
        assertEquals(3, result.getParam().getSlice().size());
        assertEquals(1, result.getParam().getOrderBy().size());
        assertEquals(20, result.getLimit());

        System.out.println("====== 复杂查询转换结果 ======");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }
}
