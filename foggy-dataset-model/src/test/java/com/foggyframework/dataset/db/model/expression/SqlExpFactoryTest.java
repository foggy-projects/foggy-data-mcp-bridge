package com.foggyframework.dataset.db.model.expression;

import com.foggyframework.dataset.db.model.engine.expression.AllowedFunctions;
import com.foggyframework.dataset.db.model.engine.expression.SqlExpFactory;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlBinaryExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlColumnRefExp;
import com.foggyframework.dataset.db.model.engine.expression.sql.SqlLiteralExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.Parser;
import com.foggyframework.fsscript.parser.spi.ParserFactory;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SqlExpFactory 表达式解析测试
 *
 * <p>覆盖：标识符、字面量、二元/一元运算符、函数调用、安全检查</p>
 */
@DisplayName("SqlExpFactory 表达式解析测试")
public class SqlExpFactoryTest {

    private Parser parser;
    private SqlExpFactory expFactory;

    @BeforeEach
    void setUp() {
        expFactory = new SqlExpFactory();
        parser = ParserFactory.newInstance().newExpParser(expFactory);
    }

    // ==========================================
    // 标识符测试
    // ==========================================

    @Test
    @DisplayName("createId - 生成 SqlColumnRefExp")
    void testCreateId() {
        Exp exp = expFactory.createId("salesAmount");
        assertInstanceOf(SqlColumnRefExp.class, exp);
        assertEquals("salesAmount", ((SqlColumnRefExp) exp).getColumnName());
    }

    @Test
    @DisplayName("解析简单标识符")
    void testParseSimpleIdentifier() throws Exception {
        Exp exp = parser.compileEl(null, "salesAmount");
        assertInstanceOf(SqlColumnRefExp.class, exp);
    }

    // ==========================================
    // 字面量测试
    // ==========================================

    @Test
    @DisplayName("创建数字字面量")
    void testCreateNumberLiteral() {
        Exp exp = expFactory.createNumber(123);
        assertInstanceOf(SqlLiteralExp.class, exp);
        assertNotNull(exp.toString());
    }

    @Test
    @DisplayName("创建字符串字面量 - SQL 转义")
    void testCreateStringLiteral() {
        Exp exp = expFactory.createString("hello");
        assertInstanceOf(SqlLiteralExp.class, exp);
        // 应该包含 SQL 引号
        assertTrue(exp.toString().contains("'hello'"));
    }

    @Test
    @DisplayName("字符串字面量 - 单引号转义")
    void testCreateStringLiteralWithQuotes() {
        Exp exp = expFactory.createString("it's a test");
        assertInstanceOf(SqlLiteralExp.class, exp);
        // SQL 中单引号应被转义为 ''
        assertTrue(exp.toString().contains("''"));
    }

    @Test
    @DisplayName("字符串字面量 - 反斜杠转义")
    void testCreateStringLiteralWithBackslash() {
        Exp exp = expFactory.createString("path\\to\\file");
        assertInstanceOf(SqlLiteralExp.class, exp);
        assertTrue(exp.toString().contains("\\\\"));
    }

    @Test
    @DisplayName("解析数字和字符串字面量")
    void testParseLiterals() throws Exception {
        Exp numExp = parser.compile(null, "123");
        assertNotNull(numExp);

        Exp strExp = parser.compile(null, "'hello'");
        assertNotNull(strExp);
    }

    // ==========================================
    // 二元运算符测试
    // ==========================================

    @Test
    @DisplayName("解析减法表达式")
    void testParseSubtraction() throws Exception {
        Exp exp = parser.compileEl(null, "salesAmount - discountAmount");
        String typeName = exp.getClass().getName();
        assertTrue(typeName.contains("SqlExpWrapper") || typeName.contains("SqlBinaryExp"),
                "Expected SqlExpWrapper or SqlBinaryExp but got " + typeName);
    }

    @Test
    @DisplayName("解析加法表达式")
    void testParseAddition() throws Exception {
        Exp exp = parser.compileEl(null, "price + tax");
        assertNotNull(exp);
        String typeName = exp.getClass().getName();
        assertTrue(typeName.contains("SqlExpWrapper") || typeName.contains("SqlBinaryExp"));
    }

    @Test
    @DisplayName("解析乘法表达式")
    void testParseMultiplication() throws Exception {
        Exp exp = parser.compileEl(null, "quantity * unitPrice");
        assertNotNull(exp);
    }

    @Test
    @DisplayName("解析除法表达式")
    void testParseDivision() throws Exception {
        Exp exp = parser.compileEl(null, "total / count");
        assertNotNull(exp);
    }

    @Test
    @DisplayName("解析取模表达式")
    void testParseModulo() throws Exception {
        Exp exp = parser.compileEl(null, "value % 10");
        assertNotNull(exp);
    }

    @Test
    @DisplayName("解析复合算术表达式 (a + b) * c")
    void testParseCompoundArithmetic() throws Exception {
        Exp exp = parser.compileEl(null, "(price + tax) * quantity");
        assertNotNull(exp);
    }

    @Test
    @DisplayName("解析嵌套括号 ((a + b) * (c - d))")
    void testParseNestedParentheses() throws Exception {
        Exp exp = parser.compileEl(null, "((a + b) * (c - d))");
        assertNotNull(exp);
    }

    @Test
    @DisplayName("解析比较运算符")
    void testParseComparisonOperators() throws Exception {
        String[] operators = {"==", "!=", ">", "<", ">=", "<="};
        for (String op : operators) {
            Exp exp = parser.compileEl(null, "a " + op + " b");
            assertNotNull(exp, "Should parse comparison: a " + op + " b");
        }
    }

    @Test
    @DisplayName("解析逻辑运算符 && ||")
    void testParseLogicalOperators() throws Exception {
        Exp andExp = parser.compileEl(null, "a > 0 && b > 0");
        assertNotNull(andExp);

        Exp orExp = parser.compileEl(null, "a > 0 || b > 0");
        assertNotNull(orExp);
    }

    // ==========================================
    // 一元运算符测试
    // ==========================================

    @Test
    @DisplayName("解析一元负号 -amount")
    void testParseUnaryNegation() throws Exception {
        Exp exp = parser.compileEl(null, "-amount");
        assertNotNull(exp);
    }

    @Test
    @DisplayName("解析逻辑非 !isActive")
    void testParseLogicalNot() throws Exception {
        Exp exp = parser.compileEl(null, "!isActive");
        assertNotNull(exp);
    }

    // ==========================================
    // 函数调用测试
    // ==========================================

    @Test
    @DisplayName("解析 ROUND 函数")
    void testParseFunctionRound() throws Exception {
        Exp exp = parser.compileEl(null, "ROUND(amount, 2)");
        assertNotNull(exp, "Should parse ROUND function");
    }

    @Test
    @DisplayName("解析 COALESCE 函数")
    void testParseFunctionCoalesce() throws Exception {
        Exp exp = parser.compileEl(null, "COALESCE(a, b, 0)");
        assertNotNull(exp, "Should parse COALESCE function");
    }

    @Test
    @DisplayName("解析 ABS 函数")
    void testParseFunctionAbs() throws Exception {
        Exp exp = parser.compileEl(null, "ABS(amount)");
        assertNotNull(exp, "Should parse ABS function");
    }

    @Test
    @DisplayName("解析嵌套函数 ROUND(ABS(amount), 2)")
    void testParseNestedFunction() throws Exception {
        Exp exp = parser.compileEl(null, "ROUND(ABS(amount), 2)");
        assertNotNull(exp, "Should parse nested function");
    }

    @Test
    @DisplayName("解析 IIF 函数")
    void testParseFunctionIif() throws Exception {
        Exp exp = parser.compileEl(null, "IIF(flag == 1, amount, 0)");
        assertNotNull(exp, "Should parse IIF function");
    }

    // ==========================================
    // 安全检查测试
    // ==========================================

    @Test
    @DisplayName("禁止非白名单函数 - 抛出 SecurityException")
    void testDisallowedFunctionThrowsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            parser.compileEl(null, "EXEC('DROP TABLE users')");
        }, "Should throw SecurityException for disallowed function EXEC");
    }

    @Test
    @DisplayName("禁止 SLEEP 函数")
    void testDisallowedSleepFunction() {
        assertThrows(SecurityException.class, () -> {
            parser.compileEl(null, "SLEEP(5)");
        }, "Should throw SecurityException for SLEEP");
    }

    // ==========================================
    // AllowedFunctions 白名单验证
    // ==========================================

    @Test
    @DisplayName("白名单包含常用数学函数")
    void testAllowedMathFunctions() {
        assertTrue(AllowedFunctions.isAllowed("ABS"));
        assertTrue(AllowedFunctions.isAllowed("ROUND"));
        assertTrue(AllowedFunctions.isAllowed("CEIL"));
        assertTrue(AllowedFunctions.isAllowed("FLOOR"));
        assertTrue(AllowedFunctions.isAllowed("SQRT"));
    }

    @Test
    @DisplayName("白名单包含常用日期函数")
    void testAllowedDateFunctions() {
        assertTrue(AllowedFunctions.isAllowed("YEAR"));
        assertTrue(AllowedFunctions.isAllowed("MONTH"));
        assertTrue(AllowedFunctions.isAllowed("DAY"));
        assertTrue(AllowedFunctions.isAllowed("DATE_FORMAT"));
    }

    @Test
    @DisplayName("白名单包含聚合函数")
    void testAllowedAggregateFunctions() {
        assertTrue(AllowedFunctions.isAggregateFunction("SUM"));
        assertTrue(AllowedFunctions.isAggregateFunction("AVG"));
        assertTrue(AllowedFunctions.isAggregateFunction("COUNT"));
        assertTrue(AllowedFunctions.isAggregateFunction("MAX"));
        assertTrue(AllowedFunctions.isAggregateFunction("MIN"));
    }

    @Test
    @DisplayName("白名单包含窗口函数")
    void testAllowedWindowFunctions() {
        assertTrue(AllowedFunctions.isWindowFunction("ROW_NUMBER"));
        assertTrue(AllowedFunctions.isWindowFunction("RANK"));
        assertTrue(AllowedFunctions.isWindowFunction("LAG"));
        assertTrue(AllowedFunctions.isWindowFunction("LEAD"));
    }

    @Test
    @DisplayName("非白名单函数被拒绝")
    void testDisallowedFunctions() {
        assertFalse(AllowedFunctions.isAllowed("EXEC"));
        assertFalse(AllowedFunctions.isAllowed("SLEEP"));
        assertFalse(AllowedFunctions.isAllowed("LOAD_FILE"));
        assertFalse(AllowedFunctions.isAllowed("SYSTEM"));
    }

    @Test
    @DisplayName("运算符到 SQL 转换")
    void testOperatorToSql() {
        assertEquals("=", AllowedFunctions.toSqlOperator("=="));
        assertEquals("<>", AllowedFunctions.toSqlOperator("!="));
        assertEquals("AND", AllowedFunctions.toSqlOperator("&&"));
        assertEquals("OR", AllowedFunctions.toSqlOperator("||"));
    }

    // ==========================================
    // 直接创建表达式对象
    // ==========================================

    @Test
    @DisplayName("直接创建 SqlBinaryExp 并验证结构")
    void testDirectSqlBinaryExpCreation() {
        SqlColumnRefExp left = new SqlColumnRefExp("salesAmount");
        SqlColumnRefExp right = new SqlColumnRefExp("discountAmount");
        SqlBinaryExp binary = new SqlBinaryExp(left, "-", right);

        assertNotNull(binary);
        String str = binary.toString();
        assertTrue(str.contains("salesAmount"));
        assertTrue(str.contains("discountAmount"));
    }
}
