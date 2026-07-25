package com.foggyframework.dataset.model.def.query.request.deserializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 简写格式反序列化器测试
 */
@DisplayName("Query DSL 简写格式反序列化测试")
class ShorthandDeserializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("groupBy 简写格式")
    class GroupByShorthandTest {

        @Test
        @DisplayName("字符串简写格式")
        void testStringShorthand() throws Exception {
            String json = """
                {
                    "groupBy": ["field1", "field2", "field3"]
                }
                """;

            DbQueryRequestDef request = objectMapper.readValue(json, DbQueryRequestDef.class);
            List<GroupRequestDef> groupBy = request.getGroupBy();

            assertNotNull(groupBy);
            assertEquals(3, groupBy.size());
            assertEquals("field1", groupBy.get(0).getField());
            assertEquals("field2", groupBy.get(1).getField());
            assertEquals("field3", groupBy.get(2).getField());
            assertNull(groupBy.get(0).getAgg());
        }

        @Test
        @DisplayName("对象完整格式")
        void testObjectFormat() throws Exception {
            String json = """
                {
                    "groupBy": [
                        {"field": "category"},
                        {"field": "amount", "agg": "SUM"}
                    ]
                }
                """;

            DbQueryRequestDef request = objectMapper.readValue(json, DbQueryRequestDef.class);
            List<GroupRequestDef> groupBy = request.getGroupBy();

            assertNotNull(groupBy);
            assertEquals(2, groupBy.size());
            assertEquals("category", groupBy.get(0).getField());
            assertNull(groupBy.get(0).getAgg());
            assertEquals("amount", groupBy.get(1).getField());
            assertEquals("SUM", groupBy.get(1).getAgg());
        }

        @Test
        @DisplayName("混合格式")
        void testMixedFormat() throws Exception {
            String json = """
                {
                    "groupBy": [
                        "category",
                        {"field": "amount", "agg": "AVG"}
                    ]
                }
                """;

            DbQueryRequestDef request = objectMapper.readValue(json, DbQueryRequestDef.class);
            List<GroupRequestDef> groupBy = request.getGroupBy();

            assertNotNull(groupBy);
            assertEquals(2, groupBy.size());
            assertEquals("category", groupBy.get(0).getField());
            assertEquals("amount", groupBy.get(1).getField());
            assertEquals("AVG", groupBy.get(1).getAgg());
        }
    }

    @Nested
    @DisplayName("orderBy 简写格式")
    class OrderByShorthandTest {

        @Test
        @DisplayName("默认升序")
        void testDefaultAsc() throws Exception {
            String json = """
                {
                    "orderBy": ["field1", "field2"]
                }
                """;

            DbQueryRequestDef request = objectMapper.readValue(json, DbQueryRequestDef.class);
            List<OrderRequestDef> orderBy = request.getOrderBy();

            assertNotNull(orderBy);
            assertEquals(2, orderBy.size());
            assertEquals("field1", orderBy.get(0).getField());
            assertEquals("asc", orderBy.get(0).getDir());
            assertEquals("field2", orderBy.get(1).getField());
            assertEquals("asc", orderBy.get(1).getDir());
        }

        @Test
        @DisplayName("空格分隔格式")
        void testSpaceSeparated() throws Exception {
            String json = """
                {
                    "orderBy": ["amount desc", "orderId asc"]
                }
                """;

            DbQueryRequestDef request = objectMapper.readValue(json, DbQueryRequestDef.class);
            List<OrderRequestDef> orderBy = request.getOrderBy();

            assertNotNull(orderBy);
            assertEquals(2, orderBy.size());
            assertEquals("amount", orderBy.get(0).getField());
            assertEquals("desc", orderBy.get(0).getDir());
            assertEquals("orderId", orderBy.get(1).getField());
            assertEquals("asc", orderBy.get(1).getDir());
        }

        @Test
        @DisplayName("负号前缀格式（降序）")
        void testMinusPrefix() throws Exception {
            String json = """
                {
                    "orderBy": ["-totalAmount", "orderId"]
                }
                """;

            DbQueryRequestDef request = objectMapper.readValue(json, DbQueryRequestDef.class);
            List<OrderRequestDef> orderBy = request.getOrderBy();

            assertNotNull(orderBy);
            assertEquals(2, orderBy.size());
            assertEquals("totalAmount", orderBy.get(0).getField());
            assertEquals("desc", orderBy.get(0).getDir());
            assertEquals("orderId", orderBy.get(1).getField());
            assertEquals("asc", orderBy.get(1).getDir());
        }

        @Test
        @DisplayName("混合格式")
        void testMixedFormat() throws Exception {
            String json = """
                {
                    "orderBy": [
                        "-totalAmount",
                        {"field": "orderId", "dir": "asc", "nullLast": true}
                    ]
                }
                """;

            DbQueryRequestDef request = objectMapper.readValue(json, DbQueryRequestDef.class);
            List<OrderRequestDef> orderBy = request.getOrderBy();

            assertNotNull(orderBy);
            assertEquals(2, orderBy.size());
            assertEquals("totalAmount", orderBy.get(0).getField());
            assertEquals("desc", orderBy.get(0).getDir());
            assertEquals("orderId", orderBy.get(1).getField());
            assertEquals("asc", orderBy.get(1).getDir());
            assertTrue(orderBy.get(1).isNullLast());
        }
    }

    @Nested
    @DisplayName("slice 简写格式")
    class SliceShorthandTest {

        @Test
        @DisplayName("等值条件简写")
        void testEqualityShorthand() throws Exception {
            String json = """
                {
                    "slice": [
                        {"orderStatus": "COMPLETED"},
                        {"customerType": "VIP"}
                    ]
                }
                """;

            DbQueryRequestDef request = objectMapper.readValue(json, DbQueryRequestDef.class);
            List<SliceRequestDef> slice = request.getSlice();

            assertNotNull(slice);
            assertEquals(2, slice.size());

            assertEquals("orderStatus", slice.get(0).getField());
            assertEquals("=", slice.get(0).getOp());
            assertEquals("COMPLETED", slice.get(0).getValue());

            assertEquals("customerType", slice.get(1).getField());
            assertEquals("=", slice.get(1).getOp());
            assertEquals("VIP", slice.get(1).getValue());
        }

        @Test
        @DisplayName("完整格式")
        void testFullFormat() throws Exception {
            String json = """
                {
                    "slice": [
                        {"field": "amount", "op": ">=", "value": 1000}
                    ]
                }
                """;

            DbQueryRequestDef request = objectMapper.readValue(json, DbQueryRequestDef.class);
            List<SliceRequestDef> slice = request.getSlice();

            assertNotNull(slice);
            assertEquals(1, slice.size());
            assertEquals("amount", slice.get(0).getField());
            assertEquals(">=", slice.get(0).getOp());
            assertEquals(1000, slice.get(0).getValue());
        }

        @Test
        @DisplayName("完整格式中的 null $expr 不应覆盖普通条件")
        void testNullExpressionDoesNotOverrideScalarCondition() throws Exception {
            String json = """
                {
                    "slice": [
                        {
                            "field": "level",
                            "op": "<",
                            "value": 4,
                            "maxDepth": null,
                            "$or": null,
                            "$and": null,
                            "$expr": null
                        }
                    ]
                }
                """;

            DbQueryRequestDef request = objectMapper.readValue(json, DbQueryRequestDef.class);
            SliceRequestDef slice = request.getSlice().get(0);

            assertEquals("level", slice.getField());
            assertEquals("<", slice.getOp());
            assertEquals(4, slice.getValue());
            assertNull(slice.getExpr());
            assertFalse(slice._isExpressionCondition());
        }

        @Test
        @DisplayName("$or 逻辑组")
        void testOrGroup() throws Exception {
            String json = """
                {
                    "slice": [
                        {
                            "$or": [
                                {"orderStatus": "COMPLETED"},
                                {"orderStatus": "SHIPPED"}
                            ]
                        }
                    ]
                }
                """;

            DbQueryRequestDef request = objectMapper.readValue(json, DbQueryRequestDef.class);
            List<SliceRequestDef> slice = request.getSlice();

            assertNotNull(slice);
            assertEquals(1, slice.size());
            assertTrue(slice.get(0)._isOrGroup());
            assertEquals(2, slice.get(0).getOr().size());
        }

        @Test
        @DisplayName("混合格式")
        void testMixedFormat() throws Exception {
            String json = """
                {
                    "slice": [
                        {"orderStatus": "COMPLETED"},
                        {"field": "amount", "op": ">=", "value": 1000},
                        {
                            "$or": [
                                {"customerType": "VIP"},
                                {"field": "orderCount", "op": ">", "value": 10}
                            ]
                        }
                    ]
                }
                """;

            DbQueryRequestDef request = objectMapper.readValue(json, DbQueryRequestDef.class);
            List<SliceRequestDef> slice = request.getSlice();

            assertNotNull(slice);
            assertEquals(3, slice.size());

            // 简写格式
            assertEquals("orderStatus", slice.get(0).getField());
            assertEquals("=", slice.get(0).getOp());

            // 完整格式
            assertEquals("amount", slice.get(1).getField());
            assertEquals(">=", slice.get(1).getOp());

            // $or 逻辑组
            assertTrue(slice.get(2)._isOrGroup());
        }
    }
}
