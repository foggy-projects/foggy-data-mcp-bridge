package com.foggyframework.dataset.db.model.expression;

import com.foggyframework.dataset.db.model.engine.expression.SqlExpFactory;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlBinaryExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlColumnRefExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.Parser;
import com.foggyframework.fsscript.parser.spi.ParserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 SqlExpFactory 表达式解析
 */
public class SqlExpFactoryTest {

    private Parser parser;
    private SqlExpFactory expFactory;

    @BeforeEach
    void setUp() {
        expFactory = new SqlExpFactory();
        parser = ParserFactory.newInstance().newExpParser(expFactory);
    }

    @Test
    void testCreateId() {
        // 测试标识符是否被创建为 SqlColumnRefExp
        Exp exp = expFactory.createId("salesAmount");
        System.out.println("createId result type: " + exp.getClass().getName());
        System.out.println("createId result: " + exp);

        assertTrue(exp instanceof SqlColumnRefExp,
                "Expected SqlColumnRefExp but got " + exp.getClass().getName());
    }

    @Test
    void testParseSimpleIdentifier() throws Exception {
        // 测试解析简单标识符
        Exp exp = parser.compileEl(null, "salesAmount");
        System.out.println("Parsed identifier type: " + exp.getClass().getName());
        System.out.println("Parsed identifier: " + exp);

        assertTrue(exp instanceof SqlColumnRefExp,
                "Expected SqlColumnRefExp but got " + exp.getClass().getName());
    }

    @Test
    void testParseSubtraction() throws Exception {
        // 测试解析减法表达式
        Exp exp = parser.compileEl(null, "salesAmount - discountAmount");

        System.out.println("Parsed subtraction type: " + exp.getClass().getName());
        System.out.println("Parsed subtraction: " + exp);

        // 检查是否创建了 SqlExpWrapper（包含 SqlBinaryExp）
        // SqlExpWrapper 是 SqlExpFactory 的内部类，通过 toString 或类名检查
        String typeName = exp.getClass().getName();
        System.out.println("Full type name: " + typeName);

        // 应该是 SqlExpWrapper
        assertTrue(typeName.contains("SqlExpWrapper") || typeName.contains("SqlBinaryExp"),
                "Expected SqlExpWrapper or SqlBinaryExp but got " + typeName);
    }

    @Test
    void testParseLiteral() throws Exception {
        // 测试解析数字字面量
        Exp numExp = parser.compile(null, "123");
        System.out.println("Parsed number type: " + numExp.getClass().getName());
        System.out.println("Parsed number: " + numExp);

        // 测试解析字符串字面量
        Exp strExp = parser.compile(null, "'hello'");
        System.out.println("Parsed string type: " + strExp.getClass().getName());
        System.out.println("Parsed string: " + strExp);
    }

    @Test
    void testDirectSqlBinaryExpEvaluation() {
        // 直接创建并执行 SqlBinaryExp
        SqlColumnRefExp left = new SqlColumnRefExp("salesAmount");
        SqlColumnRefExp right = new SqlColumnRefExp("discountAmount");
        SqlBinaryExp binary = new SqlBinaryExp(left, "-", right);

        System.out.println("Direct SqlBinaryExp: " + binary);

        // 创建一个模拟的 evaluator
        // 注意：这需要 SqlExpContext 来解析列名
        // 在实际测试中需要正确设置上下文
    }
}
