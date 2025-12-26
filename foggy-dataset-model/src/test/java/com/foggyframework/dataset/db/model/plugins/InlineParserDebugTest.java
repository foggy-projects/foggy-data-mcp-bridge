package com.foggyframework.dataset.db.model.plugins;

import com.foggyframework.dataset.db.model.engine.expression.InlineExpressionParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InlineParserDebugTest {
    
    @Test
    void testParseInlineExpression() {
        String expr1 = "sum(totalAmount) as sumTotalAmount";
        InlineExpressionParser.InlineExpression result1 = InlineExpressionParser.parse(expr1);
        
        System.out.println("Input: " + expr1);
        System.out.println("Result: " + result1);
        
        assertNotNull(result1, "Should recognize 'sum(totalAmount) as sumTotalAmount' as inline expression");
        assertEquals("sum(totalAmount)", result1.getExpression());
        assertEquals("sumTotalAmount", result1.getAlias());
        
        String expr2 = "customer$customerType";
        InlineExpressionParser.InlineExpression result2 = InlineExpressionParser.parse(expr2);
        System.out.println("Input: " + expr2);
        System.out.println("Result: " + result2);
        assertNull(result2, "Should NOT recognize simple dimension as inline expression");
    }
}
