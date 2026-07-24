package com.foggyframework.dataset.model.expression;

import com.foggyframework.dataset.model.engine.expression.AllowedFunctions;
import com.foggyframework.dataset.model.engine.expression.CalculatedFieldService;
import com.foggyframework.dataset.model.engine.expression.SqlExpFactory;
import com.foggyframework.dataset.model.engine.expression.SqlExpHolder;
import com.foggyframework.dataset.model.engine.expression.sql.SqlBinaryExp;
import com.foggyframework.dataset.model.engine.expression.sql.SqlColumnRefExp;
import com.foggyframework.dataset.model.engine.expression.sql.SqlListExp;
import com.foggyframework.dataset.model.engine.expression.sql.SqlLiteralExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.Parser;
import com.foggyframework.fsscript.parser.spi.ParserFactory;
import org.junit.jupiter.api.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SqlExpFactory 对 SQL 风格 IN / NOT IN 成员测试算子的单元测试。
 *
 * <p>验收点：
 * <ul>
 *     <li>grammar 产生式 {@code term3 IN term2} / {@code term3 NOT IN term2} 能被 SqlExpFactory 接收</li>
 *     <li>RHS 的 {@code (a, b, c)} 被翻译成 {@link SqlListExp}（绕开 Brackets 单元素语义）</li>
 *     <li>输出的 SqlBinaryExp 算子名为 {@code "IN"} / {@code "NOT IN"}</li>
 *     <li>空 IN 列表在编译期被拒绝</li>
 *     <li>{@link CalculatedFieldService#extractColumnReferences(String)} 对 IN RHS 的处理正确</li>
 *     <li>{@link AllowedFunctions#toSqlOperator(String)} 的 IN / NOT_IN 映射到位</li>
 * </ul>
 *
 * @since 8.1.11.beta
 */
@DisplayName("SqlExpFactory IN/NOT IN 算子测试 (v8.1.11.beta)")
class SqlExpFactoryInOperatorTest {

    private Parser parser;
    private SqlExpFactory expFactory;

    @BeforeEach
    void setUp() {
        expFactory = new SqlExpFactory();
        parser = ParserFactory.newInstance().newExpParser(expFactory);
    }

    /**
     * 从 compileEl 返回的外层包装里取出真正的 SqlBinaryExp。
     * SqlExpFactory 会用 SqlExpWrapper 包一层以兼容 UnresolvedFunCall 的返回类型。
     */
    private SqlBinaryExp unwrapBinary(Exp exp) {
        Exp cur = unwrapAll(exp);
        assertTrue(cur instanceof SqlBinaryExp,
                "expected SqlBinaryExp, got " + cur.getClass().getName() + " -> " + cur);
        return (SqlBinaryExp) cur;
    }

    /** 递归解包 SqlExpHolder —— `(x in (..))` 这种外层分组会叠多层 SqlExpWrapper */
    private Exp unwrapAll(Exp exp) {
        Exp cur = exp;
        while (cur instanceof SqlExpHolder) {
            Exp inner = ((SqlExpHolder) cur).getInnerSqlExp();
            if (inner == null || inner == cur) {
                break;
            }
            cur = inner;
        }
        return cur;
    }

    // ================================================================
    // AllowedFunctions 白名单与映射
    // ================================================================

    @Test
    @DisplayName("AllowedFunctions 白名单收录 IN / NOT_IN")
    void whitelistContainsInAndNotIn() {
        assertTrue(AllowedFunctions.isAllowed("IN"));
        assertTrue(AllowedFunctions.isAllowed("NOT_IN"));
        assertTrue(AllowedFunctions.isMembershipOperator("IN"));
        assertTrue(AllowedFunctions.isMembershipOperator("not_in"));
        assertFalse(AllowedFunctions.isMembershipOperator("=="));
    }

    @Test
    @DisplayName("toSqlOperator 把 IN/NOT_IN 映射到标准 SQL 算子")
    void toSqlOperatorMapsInAndNotIn() {
        assertEquals("IN", AllowedFunctions.toSqlOperator("IN"));
        assertEquals("NOT IN", AllowedFunctions.toSqlOperator("NOT_IN"));
    }

    // ================================================================
    // 解析 + AST 结构
    // ================================================================

    @Test
    @DisplayName("解析 `x in (1, 2, 3)` → SqlBinaryExp(IN) + SqlListExp")
    void parseInWithParenList() throws Exception {
        Exp exp = parser.compileEl(null, "x in (1, 2, 3)");
        SqlBinaryExp bin = unwrapBinary(exp);

        assertEquals("IN", bin.getOperator());
        assertTrue(bin.getLeft() instanceof SqlColumnRefExp);
        assertEquals("x", ((SqlColumnRefExp) bin.getLeft()).getColumnName());
        assertTrue(bin.getRight() instanceof SqlListExp);
        SqlListExp list = (SqlListExp) bin.getRight();
        assertEquals(3, list.size());
    }

    @Test
    @DisplayName("解析 `x not in (1, 2, 3)` → SqlBinaryExp(NOT IN) + SqlListExp")
    void parseNotInWithParenList() throws Exception {
        Exp exp = parser.compileEl(null, "x not in (1, 2, 3)");
        SqlBinaryExp bin = unwrapBinary(exp);
        assertEquals("NOT IN", bin.getOperator());
        assertTrue(bin.getRight() instanceof SqlListExp);
        assertEquals(3, ((SqlListExp) bin.getRight()).size());
    }

    @Test
    @DisplayName("单元素 `x in (1)` 也被规范化为 SqlListExp")
    void parseInWithSingletonList() throws Exception {
        Exp exp = parser.compileEl(null, "x in (1)");
        SqlBinaryExp bin = unwrapBinary(exp);
        assertEquals("IN", bin.getOperator());
        // 单括号在普通语境是分组语义 (= SqlLiteralExp)；IN 路径下 normalizeInRhs
        // 会再把它包成 SqlListExp([SqlLiteralExp])，避免输出 `x IN 1` 这种无效 SQL
        assertTrue(bin.getRight() instanceof SqlListExp,
                "singleton IN list must be wrapped as SqlListExp, got " + bin.getRight());
        assertEquals(1, ((SqlListExp) bin.getRight()).size());
    }

    @Test
    @DisplayName("空列表 `x in ()` 在编译期抛错")
    void parseInWithEmptyListThrows() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> parser.compileEl(null, "x in ()"),
                "empty IN list must be rejected at compile time");
        Throwable cause = ex;
        while (cause != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "expected IllegalArgumentException in cause chain, got: " + ex);
        assertTrue(cause.getMessage().contains("IN") && cause.getMessage().contains("空"),
                "error message should mention IN empty list, got: " + cause.getMessage());
    }

    @Test
    @DisplayName("NOT IN 空列表也被拒绝")
    void parseNotInWithEmptyListThrows() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> parser.compileEl(null, "x not in ()"));
        Throwable cause = ex;
        while (cause != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause);
        assertTrue(cause.getMessage().contains("NOT IN"),
                "error should name NOT IN, got: " + cause.getMessage());
    }

    @Test
    @DisplayName("字符串字面量在 IN 列表中按 SQL 单引号渲染")
    void parseInWithStringLiterals() throws Exception {
        Exp exp = parser.compileEl(null, "brand in ('Apple', 'Huawei')");
        SqlBinaryExp bin = unwrapBinary(exp);
        SqlListExp list = (SqlListExp) bin.getRight();
        assertEquals(2, list.size());
        // 每个元素都应当是 SqlLiteralExp 且 toString 含单引号
        for (Exp item : list.getItems()) {
            assertTrue(item instanceof SqlLiteralExp, "expected SqlLiteralExp, got " + item);
            String s = item.toString();
            assertTrue(s.contains("'") && s.contains("Apple") || s.contains("Huawei") || s.matches(".*'[A-Za-z]+'.*"),
                    "string literal should be single-quoted: " + s);
        }
    }

    @Test
    @DisplayName("方括号列表 `status in ['a', 'b']` 在 IN RHS 中规范化为 SqlListExp")
    void parseInWithSquareBracketList() throws Exception {
        Exp exp = parser.compileEl(null, "status in ['not_paid', 'partial', 'in_payment']");
        SqlBinaryExp bin = unwrapBinary(exp);
        assertEquals("IN", bin.getOperator());
        assertTrue(bin.getRight() instanceof SqlListExp);
        assertEquals(3, ((SqlListExp) bin.getRight()).size());
    }

    @Test
    @DisplayName("if(... in [...]) 不把 IN 列表逗号误拆为 IIF 参数")
    void compileIfWithSquareBracketInList() {
        Exp exp = CalculatedFieldService.compileExpression(
                "if(status in ['not_paid', 'partial', 'in_payment'], 1, 0)");
        String rendering = exp.toString();
        assertTrue(rendering.toUpperCase().contains("IF"));
        assertTrue(rendering.toUpperCase().contains("IN"));
        assertTrue(rendering.contains("'not_paid'"));
        assertTrue(rendering.contains("'partial'"));
        assertTrue(rendering.contains("'in_payment'"));
    }

    @Test
    @DisplayName("`(a + b)` 单元素表达式括号仍按分组语义处理（向后兼容）")
    void singleGroupingParenStillWorks() throws Exception {
        Exp exp = parser.compileEl(null, "(a + b) * c");
        // 只要不抛错即可，说明 `(a+b)` 没被误识别成 IN 列表
        assertNotNull(exp);
    }

    @Test
    @DisplayName("`x in (y + 1)` 把 RHS 的单表达式当单元素列表（合法 SQL）")
    void inWithSingleExpressionRhs() throws Exception {
        Exp exp = parser.compileEl(null, "x in (y + 1)");
        SqlBinaryExp bin = unwrapBinary(exp);
        assertEquals("IN", bin.getOperator());
        assertTrue(bin.getRight() instanceof SqlListExp);
        assertEquals(1, ((SqlListExp) bin.getRight()).size());
    }

    @Test
    @DisplayName("IN 可与 && / || 组合：`brand in ('Apple') && price > 100`")
    void inCombinedWithLogicalAnd() throws Exception {
        Exp exp = parser.compileEl(null, "brand in ('Apple', 'Nike') && price > 100");
        SqlBinaryExp top = unwrapBinary(exp);
        assertEquals("&&", top.getOperator());
        Exp leftInner = unwrapAll(top.getLeft());
        assertTrue(leftInner instanceof SqlBinaryExp);
        assertEquals("IN", ((SqlBinaryExp) leftInner).getOperator());
    }

    @Test
    @DisplayName("嵌套 `(a in (1,2)) && (b not in (3,4))`")
    void nestedInAndNotIn() throws Exception {
        Exp exp = parser.compileEl(null, "(a in (1, 2)) && (b not in (3, 4))");
        SqlBinaryExp top = unwrapBinary(exp);
        assertEquals("&&", top.getOperator());

        Exp l = unwrapAll(top.getLeft());
        Exp r = unwrapAll(top.getRight());
        assertEquals("IN", ((SqlBinaryExp) l).getOperator());
        assertEquals("NOT IN", ((SqlBinaryExp) r).getOperator());
    }

    // ================================================================
    // 列引用提取
    // ================================================================

    @Test
    @DisplayName("extractColumnReferences: `brand in ('Apple','Huawei')` → 只提取 brand")
    void extractRefs_inWithLiterals() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("brand in ('Apple', 'Huawei')");
        assertEquals(Set.of("brand"), refs,
                "IN 的字面量 RHS 不应当被当作列引用");
    }

    @Test
    @DisplayName("extractColumnReferences: `x in (a, b)` 把 RHS 列也收为依赖")
    void extractRefs_inWithColumnRhs() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("x in (a, b)");
        assertEquals(Set.of("x", "a", "b"), refs);
    }

    @Test
    @DisplayName("extractColumnReferences: `status not in ('cancelled','returned')` → 只提取 status")
    void extractRefs_notInWithLiterals() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences(
                "status not in ('cancelled', 'returned')");
        assertEquals(Set.of("status"), refs);
    }

    @Test
    @DisplayName("extractColumnReferences: 组合表达式 `brand in ('Apple') && price > 100`")
    void extractRefs_inCombined() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences(
                "brand in ('Apple', 'Nike') && price > 100");
        assertEquals(Set.of("brand", "price"), refs);
    }

    @Test
    @DisplayName("extractColumnReferences: IN 列表中的计算表达式 `x in (a + 1, b * 2)` 正确提取 a/b")
    void extractRefs_inWithArithmeticInList() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("x in (a + 1, b * 2)");
        assertEquals(Set.of("x", "a", "b"), refs);
    }

    @Test
    @DisplayName("extractColumnReferences: 嵌套 IN 在 IIF 条件里 `IIF(s in ('paid'), amt, 0)`")
    void extractRefs_inInsideIif() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences(
                "IIF(status in ('paid', 'shipped'), amount, 0)");
        assertEquals(Set.of("status", "amount"), refs);
    }

    // ================================================================
    // 运算符优先级与组合锁定
    // ================================================================

    /**
     * `a == 1 && b in (2, 3)` 必须解析为 {@code AND(EQ(a, 1), IN(b, (2, 3)))}，
     * 而不是 {@code IN(EQ(a, 1 && b), (2, 3))} 或其他错解。
     * 即 {@code ==} 与 {@code IN} 同属 term3 (同优先级, 左结合)，但 {@code &&}
     * 在 term4_logical 更低优先级，因此 AND 最外层。
     */
    @Test
    @DisplayName("AST 优先级: `a == 1 && b in (2, 3)` → AND(EQ, IN)")
    void parseTree_eqAndIn_precedence() throws Exception {
        Exp exp = parser.compileEl(null, "a == 1 && b in (2, 3)");
        SqlBinaryExp top = unwrapBinary(exp);
        assertEquals("&&", top.getOperator(), "顶层必须是 &&");

        Exp left = unwrapAll(top.getLeft());
        Exp right = unwrapAll(top.getRight());
        assertTrue(left instanceof SqlBinaryExp, "left 必须是 SqlBinaryExp");
        assertTrue(right instanceof SqlBinaryExp, "right 必须是 SqlBinaryExp");
        assertEquals("==", ((SqlBinaryExp) left).getOperator());
        assertEquals("IN", ((SqlBinaryExp) right).getOperator());
    }

    /**
     * 算术 LHS：`a + 1 in (2, 3)` 的 LHS 应是 {@code a + 1} 的二元 {@code +} 表达式。
     */
    @Test
    @DisplayName("AST 优先级: `a + 1 in (2, 3)` LHS 是 Plus")
    void parseTree_arithmeticLhs() throws Exception {
        Exp exp = parser.compileEl(null, "a + 1 in (2, 3)");
        SqlBinaryExp top = unwrapBinary(exp);
        assertEquals("IN", top.getOperator());
        Exp left = unwrapAll(top.getLeft());
        assertTrue(left instanceof SqlBinaryExp);
        assertEquals("+", ((SqlBinaryExp) left).getOperator());
    }

    /**
     * 算术 RHS 元素：`x in (a + 1, b * 2)` 列表里的元素各自是 Plus / Multi 二元表达式。
     */
    @Test
    @DisplayName("AST 结构: `x in (a+1, b*2)` 列表元素各自保留二元表达式")
    void parseTree_arithmeticInsideList() throws Exception {
        Exp exp = parser.compileEl(null, "x in (a + 1, b * 2)");
        SqlBinaryExp top = unwrapBinary(exp);
        assertEquals("IN", top.getOperator());
        SqlListExp list = (SqlListExp) top.getRight();
        assertEquals(2, list.size());
        // 列表里每个元素会被 SqlExpWrapper 包一层；解包后应当是 SqlBinaryExp（保留算术结构）
        Exp item0 = unwrapAll(list.getItems().get(0));
        Exp item1 = unwrapAll(list.getItems().get(1));
        assertTrue(item0 instanceof SqlBinaryExp, "item0 should be SqlBinaryExp, got " + item0);
        assertTrue(item1 instanceof SqlBinaryExp, "item1 should be SqlBinaryExp, got " + item1);
        assertEquals("+", ((SqlBinaryExp) item0).getOperator());
        assertEquals("*", ((SqlBinaryExp) item1).getOperator());
    }

    /**
     * 链式：`a in (1,2) && b in (3,4) && c not in (5,6)` 三个 term3 用 && 连起来，
     * 左结合挂成 {@code AND(AND(IN, IN), NOT IN)}。
     */
    @Test
    @DisplayName("AST 结构: 三个 IN/NOT IN 链式 && 左结合")
    void parseTree_chainedInLeftAssociative() throws Exception {
        Exp exp = parser.compileEl(null,
                "a in (1, 2) && b in (3, 4) && c not in (5, 6)");
        SqlBinaryExp top = unwrapBinary(exp);
        assertEquals("&&", top.getOperator());
        // RHS 是最右的 c not in ...
        Exp right = unwrapAll(top.getRight());
        assertTrue(right instanceof SqlBinaryExp);
        assertEquals("NOT IN", ((SqlBinaryExp) right).getOperator());
        // LHS 是 AND(IN, IN)
        Exp leftAnd = unwrapAll(top.getLeft());
        assertTrue(leftAnd instanceof SqlBinaryExp);
        assertEquals("&&", ((SqlBinaryExp) leftAnd).getOperator());
        assertEquals("IN", ((SqlBinaryExp) unwrapAll(((SqlBinaryExp) leftAnd).getLeft())).getOperator());
        assertEquals("IN", ((SqlBinaryExp) unwrapAll(((SqlBinaryExp) leftAnd).getRight())).getOperator());
    }
}
