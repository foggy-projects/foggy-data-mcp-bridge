package com.foggyframework.dataset.model.semantic.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SemanticQueryRequest.OrderItem 简写格式反序列化测试
 *
 * <p>验证 Semantic API 层的 orderBy 输入与 DbQueryRequestDef 层保持一致：
 * 支持字符串简写、负号前缀、正号前缀、空格分隔、以及完整对象格式。</p>
 *
 * @since 1.4 B1a — Python parity: orderBy string shorthand
 */
@DisplayName("SemanticQueryRequest orderBy 简写格式反序列化测试")
class SemanticOrderByShorthandTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("字符串简写 → 默认升序")
    void testBareStringDefaultsToAsc() throws Exception {
        String json = """
            {
                "columns": ["amount"],
                "orderBy": ["invoiceDateDue"]
            }
            """;
        SemanticQueryRequest request = objectMapper.readValue(json, SemanticQueryRequest.class);
        List<SemanticQueryRequest.OrderItem> orderBy = request.getOrderBy();

        assertNotNull(orderBy);
        assertEquals(1, orderBy.size());
        assertEquals("invoiceDateDue", orderBy.get(0).getField());
        assertEquals("asc", orderBy.get(0).getDir());
    }

    @Test
    @DisplayName("负号前缀 → 降序")
    void testMinusPrefixDescending() throws Exception {
        String json = """
            {
                "columns": ["amount"],
                "orderBy": ["-amountTotal"]
            }
            """;
        SemanticQueryRequest request = objectMapper.readValue(json, SemanticQueryRequest.class);
        List<SemanticQueryRequest.OrderItem> orderBy = request.getOrderBy();

        assertNotNull(orderBy);
        assertEquals(1, orderBy.size());
        assertEquals("amountTotal", orderBy.get(0).getField());
        assertEquals("desc", orderBy.get(0).getDir());
    }

    @Test
    @DisplayName("正号前缀 → 升序")
    void testPlusPrefixAscending() throws Exception {
        String json = """
            {
                "columns": ["amount"],
                "orderBy": ["+amountTotal"]
            }
            """;
        SemanticQueryRequest request = objectMapper.readValue(json, SemanticQueryRequest.class);
        List<SemanticQueryRequest.OrderItem> orderBy = request.getOrderBy();

        assertNotNull(orderBy);
        assertEquals(1, orderBy.size());
        assertEquals("amountTotal", orderBy.get(0).getField());
        assertEquals("asc", orderBy.get(0).getDir());
    }

    @Test
    @DisplayName("空格分隔格式")
    void testSpaceSeparatedFormat() throws Exception {
        String json = """
            {
                "columns": ["amount"],
                "orderBy": ["amount desc", "orderId asc"]
            }
            """;
        SemanticQueryRequest request = objectMapper.readValue(json, SemanticQueryRequest.class);
        List<SemanticQueryRequest.OrderItem> orderBy = request.getOrderBy();

        assertNotNull(orderBy);
        assertEquals(2, orderBy.size());
        assertEquals("amount", orderBy.get(0).getField());
        assertEquals("desc", orderBy.get(0).getDir());
        assertEquals("orderId", orderBy.get(1).getField());
        assertEquals("asc", orderBy.get(1).getDir());
    }

    @Test
    @DisplayName("完整对象格式")
    void testFullObjectFormat() throws Exception {
        String json = """
            {
                "columns": ["amount"],
                "orderBy": [{"field": "amount", "dir": "desc"}]
            }
            """;
        SemanticQueryRequest request = objectMapper.readValue(json, SemanticQueryRequest.class);
        List<SemanticQueryRequest.OrderItem> orderBy = request.getOrderBy();

        assertNotNull(orderBy);
        assertEquals(1, orderBy.size());
        assertEquals("amount", orderBy.get(0).getField());
        assertEquals("desc", orderBy.get(0).getDir());
    }

    @Test
    @DisplayName("column/direction 别名格式")
    void testColumnDirectionAliases() throws Exception {
        String json = """
            {
                "columns": ["amount"],
                "orderBy": [{"column": "amount", "direction": "asc"}]
            }
            """;
        SemanticQueryRequest request = objectMapper.readValue(json, SemanticQueryRequest.class);
        List<SemanticQueryRequest.OrderItem> orderBy = request.getOrderBy();

        assertNotNull(orderBy);
        assertEquals(1, orderBy.size());
        assertEquals("amount", orderBy.get(0).getField());
        assertEquals("asc", orderBy.get(0).getDir());
    }

    @Test
    @DisplayName("混合格式：字符串简写 + 完整对象")
    void testMixedFormat() throws Exception {
        String json = """
            {
                "columns": ["amount"],
                "orderBy": [
                    "-totalAmount",
                    "invoiceDateDue",
                    {"field": "orderId", "dir": "asc"}
                ]
            }
            """;
        SemanticQueryRequest request = objectMapper.readValue(json, SemanticQueryRequest.class);
        List<SemanticQueryRequest.OrderItem> orderBy = request.getOrderBy();

        assertNotNull(orderBy);
        assertEquals(3, orderBy.size());
        assertEquals("totalAmount", orderBy.get(0).getField());
        assertEquals("desc", orderBy.get(0).getDir());
        assertEquals("invoiceDateDue", orderBy.get(1).getField());
        assertEquals("asc", orderBy.get(1).getDir());
        assertEquals("orderId", orderBy.get(2).getField());
        assertEquals("asc", orderBy.get(2).getDir());
    }

    @Test
    @DisplayName("对象格式无 dir 字段时默认升序")
    void testObjectWithoutDirDefaultsToAsc() throws Exception {
        String json = """
            {
                "columns": ["amount"],
                "orderBy": [{"field": "amount"}]
            }
            """;
        SemanticQueryRequest request = objectMapper.readValue(json, SemanticQueryRequest.class);
        List<SemanticQueryRequest.OrderItem> orderBy = request.getOrderBy();

        assertNotNull(orderBy);
        assertEquals(1, orderBy.size());
        assertEquals("amount", orderBy.get(0).getField());
        assertEquals("asc", orderBy.get(0).getDir());
    }
}
