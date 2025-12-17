/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser;

import com.foggyframework.core.utils.ErrorUtils;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpFactory;
import java_cup.runtime.Symbol;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Stack;

public class ElExpScanner implements BaseScanner {

    protected enum State {
        leftOfPoint, rightOfPoint, inExponent,
    }

    protected static Hashtable<String, Integer> m_resWordsTable = new Hashtable<String, Integer>();

    private static int iMaxResword;

    static {
//        initResword(ExpSymbols.AND, "AND");
        // initResword(ExpSymbols.OR, "|");
        initResword(ExpSymbols.NOT, "NOT");
        initResword(ExpSymbols.LIKE, "LIKE");
        initResword(ExpSymbols.IN, "IN");
        initResword(ExpSymbols.NULL, "NULL");
        initResword(ExpSymbols.FUNCTION, "FUNCTION");
        initResword(ExpSymbols.VAR, "VAR");
        initResword(ExpSymbols.LET, "LET");
        initResword(ExpSymbols.NEW, "NEW");
        initResword(ExpSymbols.IF, "IF");
        initResword(ExpSymbols.ELSE, "ELSE");
        initResword(ExpSymbols.RETURN, "RETURN");
        initResword(ExpSymbols.TRY, "TRY");
        initResword(ExpSymbols.CATCH, "CATCH");
        initResword(ExpSymbols.FINALLY, "FINALLY");
        initResword(ExpSymbols.THROW, "THROW");
        initResword(ExpSymbols.FOR, "FOR");

        initResword(ExpSymbols.TRUE, "TRUE");
        initResword(ExpSymbols.FALSE, "FALSE");
        initResword(ExpSymbols.THIS, "THIS");
        initResword(ExpSymbols.REQUEST, "REQUEST");
        initResword(ExpSymbols._EE, "_EE");
        initResword(ExpSymbols._EVALUATOR, "_EVALUATOR");
        initResword(ExpSymbols.ORR, "OR");
        initResword(ExpSymbols.WHILE, "WHILE");
        initResword(ExpSymbols.BREAK, "BREAK");

        initResword(ExpSymbols.IMPORT, "IMPORT");
        initResword(ExpSymbols.CIMPORT, "CIMPORT");
        initResword(ExpSymbols.EXPORT, "EXPORT");
        initResword(ExpSymbols.DEFAULT, "DEFAULT");
        initResword(ExpSymbols.FROM, "FROM");
        initResword(ExpSymbols.CONST, "CONST");
        initResword(ExpSymbols.AS, "AS");
        initResword(ExpSymbols.CONTINUE, "CONTINUE");

        initResword(ExpSymbols.DEFAULT_COLON, "DEFAULT:");
        initResword(ExpSymbols.CASE, "CASE");
        initResword(ExpSymbols.SWITCH, "SWITCH");
        initResword(ExpSymbols.DELETE, "DELETE");
        initResword(ExpSymbols.OF, "OF");
//		initResword(ExpSymbols.SWITCH, "SWITCH");
//		initResword(ExpSymbols.CASE, "CASE");
//		initResword(ExpSymbols.DEFAULT, "DEFAULT");
    }

    /**
     * The {@link BigDecimal} value 0. Note that BigDecimal.ZERO does not
     * exist until JDK 1.5.
     */
    protected static final BigDecimal BigDecimalZero = BigDecimal.valueOf(0);

    private static void initResword(int id, String s) {
        m_resWordsTable.put(s, id);
        if (id > iMaxResword) {
            iMaxResword = id;
        }
    }

    public static void main(final String[] args) {
        // String x = "xxxx #io(df,wer)asdf (ewqrwer";

        final ExpParser p = new ExpParser((ExpFactory) null);
        try {
            // implements Parser
            // final Exp o = (Exp)
            // p.parseInternal("ds1.sum(MAP_LEVEL,MARKER_TYPE like '%0%' and
            // MARKER_FILTER='syr')");
            // final Exp o =
            // p.compileEl("controller.showStockOutList(stockroomId)");
            // final Exp o = p.compileEl("_rs_agg_xor(rs1.permission,0x100)");
            // final Exp o = p.compile("_rs_agg_xor(rs1.permission,0x100)");
            // final Exp o = p.compileEl("'Team管理'+($.userId?(' -
            // '+eche('user',$.userId).caption):'')");

            // final Exp o = p.compileEl("true+false+request+_ee");
            // final Exp o = p.compileEl("3 + 1 == 1 ? 1 : 4");
            final Exp a17 = p.compileEl("export default @xx(1,2)");
            System.out.println(a17);

            final Exp a16 = p.compileEl("@xx(1,2)");
            System.out.println(a16);
//			System.out.println(a16.evalResult(new DefaultExpEvaluator()));

            final Exp a15 = p.compileEl("!a||b==0");
            System.out.println(a15);
//			System.out.println(a15.evalResult(new DefaultExpEvaluator()));

            final Exp a = p.compileEl("switch (a){" + "case 1: var x=1;" + "}");
            System.out.println(a);
            System.out.println(a.evalResult(DefaultExpEvaluator.newInstance()));

            System.out.println("----------o14");
            final Exp o14 = p.compileEl("1+1");
            System.out.println(o14);

            final Exp o13 = p.compileEl("a--");
            System.out.println(o13);
            final Exp o12 = p.compileEl("var x= 'sfasdf '");
            System.out.println(o12);

            final Exp o7 = p.compileEl("a.b.c=1");
            System.out.println(o7);
            final Exp o6 = p.compileEl("import '123';a.b=1;");
            System.out.println(o6);


            final Exp o = p.compileEl("var x = {" + "	'2019-01-01':100," + "	'2019-02-05':100" + "};return x;");
            final Exp o2 = p.compileEl("var b=1;d=1;bb.cc.dd.x = x;");
            System.out.println(o2);
            final Exp o3 = p.compileEl("{$match:1}");
            System.out.println(o3);

            final Exp o4 = p.compileEl("1 or 2");
            System.out.println(o4);

            System.out.println("----------");
            final Exp o5 = p.compileEl("/**\t*/werwr");
            System.out.println(o5);

            System.out.println("----------o8");
            final Exp o8 = p.compileEl("q[a]='b'");
            System.out.println(o8);

            System.out.println("----------o9");
            final Exp o9 = p.compileEl("while(1){}");
            System.out.println(o9);

//			HashMap mm = new HashMap<>();
//			DefaultExpEvaluator ee = new DefaultExpEvaluator();
//			ee.setVar("a", mm);
//			o9.evalValue(ee);
//			System.out.println(12);
            // int x = 3 + 1 == 1 ? 1 : 4;
//			System.err.println(o.evalResult(new DefaultExpEvaluator()));
            // final Exp o =
            // p.compileEl("{select : ['group1','_group2',{as : 'xxxx',column :
            // function(recordList){return
            // recordList.sum('v1');}}],groupBy:['group1','group2']}");
//			System.err.println(o);
            // System.out.println(o.evalResult(new DefaultExpEvaluator()));

            System.out.println("----------o10");
            final Exp o10 = p.compileEl("-1");
            System.out.println(o10);
            System.out.println(o10.evalResult(DefaultExpEvaluator.newInstance()));

            System.out.println("@xx.dd(_ee.r,1,a,null)");
            final Exp o11 = p.compileEl("@xx.dd(_ee.r,1,a,null)");
            System.out.println(o11);
            System.out.println(o11.evalResult(DefaultExpEvaluator.newInstance()));

//			System.out.println("@xx.dd(_ee.r,1,a,null)");


        } catch (final Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    String s = "s#sdfdf(ff,,,)s";// ;"#io(sfd,#sdf())

    // adfsdfdf#xasdf(df,df,dsaf,wer,wer)aerwer)";
    /**
     * next lookahead character
     */
    protected int lookaheadChars[] = new int[16];

    protected int firstLookaheadChar = 0;

    protected int lastLookaheadChar = 0;

    protected int nextChar;
    /**
     * lines[x] is the start of the x'th line
     */
    protected List<Integer> lines;

    /**
     * number of times advance() has been called
     */
    protected int iChar;
    /**
     * end of previous token
     */
    protected int iPrevChar;

    /**
     * previous symbol returned
     */
    protected int previousSymbol;

    protected int i = 0;
    protected StringBuilder sb = new StringBuilder();
    // 0 = jump,-1 exit,1 function,2 args
    protected int type;
    protected String next_string;

    protected boolean inFormula;

    private class NcountFix {
        int count;
    }

    private class NcountFixCtx {
        protected Stack<NcountFix> ncountFixStack = new Stack<>();

        public void startFunction() {
            ncountFixStack.push(new NcountFix());
        }

        public void add() {
            if (ncountFixStack.isEmpty()) {
                return;
            } else {
                ncountFixStack.peek().count++;
            }
        }

        public int remove() {
            if (ncountFixStack.isEmpty()) {
                return -1;
            } else {
                NcountFix f = ncountFixStack.peek();
                f.count--;
                if (f.count == 0) {
                    ncountFixStack.pop();
                    return 0;
                }
                return f.count;
            }

        }
    }

    protected NcountFixCtx ncountFixCtx = new NcountFixCtx();

    protected NcountFixCtx arrayXFixCtx = new NcountFixCtx();

    /**
     * ASI (Automatic Semicolon Insertion) 支持
     * 追踪是否遇到换行符
     */
    protected boolean newlineEncountered = false;

    public ElExpScanner(final String s) {
        this.s = s;
    }

    /**
     * 判断前一个 token 是否可以结束语句
     * 这些 token 后面如果遇到换行且下一个 token 不能继续语句，则需要插入分号
     */
    private boolean canEndStatement(int tokenType) {
        switch (tokenType) {
            case ExpSymbols.ID:
            case ExpSymbols.NUMBER:
            case ExpSymbols.LONG:
            case ExpSymbols.STRING:
            case ExpSymbols.EXP_STRING:
            case ExpSymbols.RPAREN:      // )
            case ExpSymbols.RSBRACE:     // ]
            case ExpSymbols.RBRACE:      // }
            case ExpSymbols.TRUE:
            case ExpSymbols.FALSE:
            case ExpSymbols.NULL:
            case ExpSymbols.THIS:
            case ExpSymbols.BREAK:
            case ExpSymbols.CONTINUE:
                return true;
            default:
                return false;
        }
    }

    /**
     * 判断当前 token 是否不能继续前一条语句（即应该开始新语句）
     * 如果返回 true，且前一个 token 可以结束语句，则需要在前面插入分号
     */
    private boolean cannotContinueStatement(int tokenType) {
        switch (tokenType) {
            case ExpSymbols.ID:
            case ExpSymbols.NUMBER:
            case ExpSymbols.LONG:
            case ExpSymbols.STRING:
            case ExpSymbols.EXP_STRING:
            case ExpSymbols.FUNCTION:
            case ExpSymbols.IF:
            case ExpSymbols.FOR:
            case ExpSymbols.WHILE:
            case ExpSymbols.SWITCH:
            case ExpSymbols.RETURN:
            case ExpSymbols.VAR:
            case ExpSymbols.LET:
            case ExpSymbols.CONST:
            case ExpSymbols.EXPORT:
            case ExpSymbols.IMPORT:
            case ExpSymbols.CIMPORT:
            case ExpSymbols.TRY:
            case ExpSymbols.THROW:
            case ExpSymbols.BREAK:
            case ExpSymbols.CONTINUE:
            case ExpSymbols.DELETE:
            case ExpSymbols.TRUE:
            case ExpSymbols.FALSE:
            case ExpSymbols.NULL:
            case ExpSymbols.THIS:
            case ExpSymbols.LBRACE:      // { 开始新的代码块或对象
            case ExpSymbols.LSBRACE:     // [ 开始新的数组（注意：也可能是下标访问）
            case ExpSymbols.AT:          // @ Spring Bean
            case ExpSymbols.NO:          // # 请求属性
                return true;
            default:
                return false;
        }
    }

    /**
     * 判断当前 token 是否可以继续前一条语句（不应该插入分号）
     * 如：运算符、点号、问号等
     */
    private boolean canContinueStatement(int tokenType) {
        switch (tokenType) {
            case ExpSymbols.DOT:         // .
            case ExpSymbols.QMARK_DOT:   // ?.
            case ExpSymbols.COMMA:       // ,
            case ExpSymbols.PLUS:        // +
            case ExpSymbols.MINUS:       // -
            case ExpSymbols.MULTI:       // *
            case ExpSymbols.DIVISION:    // /
            case ExpSymbols.PERCENT:     // %
            case ExpSymbols.EQ:          // =
            case ExpSymbols.EQ2:         // ==
            case ExpSymbols.NE:          // <> !=
            case ExpSymbols.LT:          // <
            case ExpSymbols.GT:          // >
            case ExpSymbols.LE:          // <=
            case ExpSymbols.GE:          // >=
            case ExpSymbols.AND:         // &
            case ExpSymbols.OR:          // |
            case ExpSymbols.XOR:         // ^
            case ExpSymbols.AA:          // &&
            case ExpSymbols.CONCAT:      // ||
            case ExpSymbols.ORR:         // or
            case ExpSymbols.QMARK:       // ?
            case ExpSymbols.COLON:       // :
            case ExpSymbols.NF:          // =>
            case ExpSymbols.LPAREN:      // ( 函数调用
            case ExpSymbols.IN:          // in
            case ExpSymbols.LIKE:        // like
                return true;
            default:
                return false;
        }
    }

    protected void advance() throws IOException {
        // if (s.length() > i) {
        //
        // nextChar = s.charAt(i);
        // i++;
        // } else {
        // nextChar = -1;
        // }

        if (firstLookaheadChar == lastLookaheadChar) {
            // We have nothing in the lookahead buffer.
            nextChar = getChar();
        } else {
            // We have called lookahead(); advance to the next character it got.
            nextChar = lookaheadChars[firstLookaheadChar++];
            if (firstLookaheadChar == lastLookaheadChar) {
                firstLookaheadChar = 0;
                lastLookaheadChar = 0;
            }
        }
        if (nextChar == '\012') {
            lines.add(iChar);
        }
        iChar++;
    }

    @Override
    public ParseRegion createRegion(final int left, final int right) {
        int target = left;
        int line = -1;
        int lineEnd = 0;
        int lineStart;
        do {
            line++;
            lineStart = lineEnd;
            lineEnd = Integer.MAX_VALUE;
            if (line < lines.size()) {
                lineEnd = lines.get(line);
            }
        } while (lineEnd < target);

        int startLine = line;
        int startColumn = target - lineStart;

        if (right == left) {
            return new ParseRegion(startLine + 1, startColumn + 1);
        }

        target = right - 1;
        if (target > left)
            --target; // don't know why
        line = -1;
        lineEnd = 0;
        do {
            line++;
            lineStart = lineEnd;
            lineEnd = Integer.MAX_VALUE;
            if (line < lines.size()) {
                lineEnd = lines.get(line);
            }
        } while (lineEnd < target);

        int endLine = line;
        int endColumn = target - lineStart;

        return new ParseRegion(startLine + 1, startColumn + 1, endLine + 1, endColumn + 1);
    }

    protected Symbol doLBRACE() throws IOException {
        advance();

        return makeToken(ExpSymbols.LBRACE, "{");
    }

    protected Symbol doRBRACE() throws IOException {
        advance();

        Symbol s = makeToken(ExpSymbols.RBRACE, "}");

        return s;
    }

    // Override Scanner.getChar().
    protected int getChar() {
        return (i >= s.length()) ? -1 : s.charAt(i++);
    }

    @Override
    public String getExpession() {
        return s;
    }

    @Override
    public void init() {
        try {
            lines = new ArrayList<Integer>();
            iChar = iPrevChar = 0;
            advance();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            throw ErrorUtils.toRuntimeException(e);
        }
    }

    /**
     * Peek at the character after {@link #nextChar} without advancing.
     */
    protected int lookahead() throws IOException {
        return lookahead(1);
    }

    protected int lookahead(int n) throws IOException {
        if (n == 0) {
            return nextChar;
        } else {
            // if the desired character not in lookahead buffer, read it in
            if (n > lastLookaheadChar - firstLookaheadChar) {
                int len = lastLookaheadChar - firstLookaheadChar;
                int t[];

                // make sure we do not go off the end of the buffer
                if (n + firstLookaheadChar > lookaheadChars.length) {
                    if (n > lookaheadChars.length) {
                        // the array is too small; make it bigger and shift
                        // everything to the beginning.
                        t = new int[n * 2];
                    } else {
                        // the array is big enough, so just shift everything
                        // to the beginning of it.
                        t = lookaheadChars;
                    }

                    System.arraycopy(lookaheadChars, firstLookaheadChar, t, 0, len);
                    lookaheadChars = t;
                    firstLookaheadChar = 0;
                    lastLookaheadChar = len;
                }

                // read ahead enough
                while (n > lastLookaheadChar - firstLookaheadChar) {
                    lookaheadChars[lastLookaheadChar++] = getChar();
                }
            }

            return lookaheadChars[n - 1 + firstLookaheadChar];
        }
    }

    protected Symbol makeNumber(BigDecimal mantissa, int exponent, boolean hasDot) {
        if (hasDot) {
            double d = mantissa.movePointRight(exponent).doubleValue();
            return makeSymbol(ExpSymbols.NUMBER, d);
        } else {
            int d = mantissa.movePointRight(exponent).intValue();
            return makeSymbol(ExpSymbols.NUMBER, d);
        }
    }

    protected Symbol makeString(String s) {
        // if (inFormula) {
        // inFormula = false;
        // return makeSymbol(DefaultMdxParserSym.FORMULA_STRING, s);
        // // } else {
        return makeSymbol(ExpSymbols.STRING, s);
        // }
    }

    protected Symbol makeSymbol(int id, Object o) {
        int iPrevPrevChar = iPrevChar;
        this.iPrevChar = iChar;
        this.previousSymbol = id;

        return new Symbol(id, iPrevPrevChar, iChar, o);
    }

    protected Symbol makeToken(int i, String s) {
        return makeSymbol(i, s);
    }

    protected int next() {
        return s.length() > (i + 0) ? s.charAt(i + 0) : -1;
    }

    Symbol tmpSymbol;

    boolean inNf = false;
    int nfLRCount = 0;

    @Override
    public final Symbol next_token() throws Exception {
        /**
         * 这段代码，解决 export 不用非得加;的问题
         * 单元测试见AutoNcountExpTest
         */
        if (tmpSymbol != null) {
            try {
                return tmpSymbol;
            } finally {
                tmpSymbol = null;
            }
        }
        int previousTmp = this.previousSymbol;

        Symbol symbol = next_token1();

        // ASI: 检查是否在这次扫描中遇到了换行符（在上一个 token 和当前 token 之间）
        // 必须在 next_token1() 返回后检查，因为换行符是在扫描过程中遇到的
        boolean hadNewline = this.newlineEncountered;
        // 重置换行标志
        this.newlineEncountered = false;

        if (this.previousSymbol == ExpSymbols.NCOUNT) {
            return symbol;
        }

        /**
         * ASI (Automatic Semicolon Insertion) 核心逻辑
         * 条件：
         * 1. 遇到了换行符
         * 2. 前一个 token 可以结束语句
         * 3. 当前 token 不能继续前一条语句（且不是显式可继续的运算符）
         */
        if (hadNewline && canEndStatement(previousTmp) && !canContinueStatement(symbol.sym)) {
            // 特殊情况：如果当前是 EOF，不需要插入分号
            if (symbol.sym != ExpSymbols.EOF) {
                // 需要在当前 token 前插入分号
                if (cannotContinueStatement(symbol.sym)) {
                    tmpSymbol = symbol;
                    return makeSymbol(ExpSymbols.NCOUNT, "ASI;");
                }
            }
        }
        /** end ASI **********************/

        /**
         * 用于处理allResults.filter(r => r.code === 0 || r.code === 200) 这样的表达式
         */
        if (previousTmp == ExpSymbols.NF) {
            //
            if (symbol.sym != ExpSymbols.LBRACE) {
                tmpSymbol = symbol;
                inNf = true;
                nfLRCount++;
                return makeSymbol(ExpSymbols.LBRACE, "auto fix => {");
            }
        }
        if (inNf) {
            if (symbol.sym == ExpSymbols.LPAREN) {
                nfLRCount++;
            } else if (symbol.sym == ExpSymbols.RPAREN) {
                nfLRCount--;
                if (nfLRCount == 0) {
                    inNf = false;
                    tmpSymbol = symbol;
                    return makeSymbol(ExpSymbols.RBRACE, "auto fix}");
                }
            }
        }
        /**end **********************/

        /**
         * const endTime = allTimes[allTimes.length - 1]; ->
         * const endTime = allTimes[v(allTimes.length - 1v)];
         */
        if (symbol.sym == ExpSymbols.LSBRACE && previousTmp == ExpSymbols.ID) {
            arrayXFixCtx.startFunction();
            arrayXFixCtx.add();
            tmpSymbol = makeSymbol(ExpSymbols.LPAREN, "auto fix (");
            return symbol;
        }
        if (symbol.sym == ExpSymbols.RSBRACE) {
            if (arrayXFixCtx.remove() == 0) {
                tmpSymbol = symbol;
                return makeSymbol(ExpSymbols.RPAREN, "auto fix)");
            }
        }

        /**end **********************/

        if (symbol.sym == ExpSymbols.EXPORT) {
            tmpSymbol = symbol;
            return makeSymbol(ExpSymbols.NCOUNT, "auto fix;");
        } else if (symbol.sym == ExpSymbols.FUNCTION) {
            ncountFixCtx.startFunction();
        } else if (symbol.sym == ExpSymbols.LBRACE) {
            ncountFixCtx.add();
        } else if (symbol.sym == ExpSymbols.RBRACE) {
            if (ncountFixCtx.remove() == 0) {
                //补下;号
                tmpSymbol = makeSymbol(ExpSymbols.NCOUNT, "auto fix;");
                return symbol;
            }
        }

        return symbol;
    }

    public Symbol next_token1() throws Exception {
        StringBuilder id = null;
        for (; ; ) {
            switch (nextChar) {
                case -1:
                    return makeToken(ExpSymbols.EOF, "EOF");
                case '.':

                    switch (lookahead()) {
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                        case '8':
                        case '9':

                            // We're looking at the '.' on the start of a number,
                            // e.g. .1; fall through to parse a number.
                            break;
                        case '.':
                            if (lookahead(2) == '.') {
                                advance();
                                advance();
                                advance();
                                return makeToken(ExpSymbols.DDDOT, "...");
                            }
                        default:
                            advance();
                            return makeToken(ExpSymbols.DOT, ".");
                    }
                    // fall through

                case '0':
                    switch (lookahead()) {
                        case 'x':
                            // 读取十六进制
                            StringBuilder sb = new StringBuilder();
                            advance();
                            for (; ; ) {
                                advance();
                                switch (nextChar) {
                                    case '0':
                                    case '1':
                                    case '2':
                                    case '3':
                                    case '4':
                                    case '5':
                                    case '6':
                                    case '7':
                                    case '8':
                                    case '9':
                                    case 'A':
                                    case 'a':
                                    case 'B':
                                    case 'b':
                                    case 'C':
                                    case 'c':
                                    case 'D':
                                    case 'd':
                                    case 'E':
                                    case 'e':
                                    case 'F':
                                    case 'f':
                                        sb.append((char) nextChar);
                                        continue;
                                    case 'l':
                                        // 呃？转成long型
                                        advance();
                                        return makeSymbol(ExpSymbols.LONG, Long.parseLong(sb.toString(), 16));
                                    default:
                                        return makeSymbol(ExpSymbols.NUMBER, Integer.parseInt(sb.toString(), 16));
                                }
                            }
                    }
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':

                    // Parse a number. Valid examples include 1, 1.2, 0.1, .1,
                    // 1e2, 1E2, 1e-2, 1e+2. Invalid examples include e2, 1.2.3,
                    // 1e2e3, 1e2.3.
                    //
                    // Signs preceding numbers (e.g. -1, + 1E-5) are valid, but are
                    // handled by the parser.
                    //
                    BigDecimal n = BigDecimalZero;
                    int digitCount = 0, exponent = 0;
                    boolean positive = true;
                    BigDecimal mantissa = BigDecimalZero;
                    State state = State.leftOfPoint;
                    boolean hasDot = false;

                    for (; ; ) {
                        switch (nextChar) {
                            case '.':
                                hasDot = true;
                                switch (state) {
                                    case leftOfPoint:
                                        state = State.rightOfPoint;
                                        mantissa = n;
                                        n = BigDecimalZero;
                                        digitCount = 0;
                                        positive = true;
                                        advance();
                                        break;
                                    // Error: we are seeing a point in the exponent
                                    // (e.g. 1E2.3 or 1.2E3.4) or a second point in the
                                    // mantissa (e.g. 1.2.3). Return what we've got
                                    // and let the parser raise the error.
                                    case rightOfPoint:
                                        mantissa = mantissa.add(n.movePointRight(-digitCount));
                                        return makeNumber(mantissa, exponent, hasDot);
                                    case inExponent:
                                        if (!positive) {
                                            n = n.negate();
                                        }
                                        exponent = n.intValue();
                                        return makeNumber(mantissa, exponent, hasDot);
                                }
                                break;

                            case 'E':
                            case 'e':
                                switch (state) {
                                    case inExponent:
                                        // Error: we are seeing an 'e' in the exponent
                                        // (e.g. 1.2e3e4). Return what we've got and let
                                        // the parser raise the error.
                                        if (!positive) {
                                            n = n.negate();
                                        }
                                        exponent = n.intValue();
                                        return makeNumber(mantissa, exponent, hasDot);
                                    case leftOfPoint:
                                        mantissa = n;
                                        break;
                                    default:
                                        mantissa = mantissa.add(n.movePointRight(-digitCount));
                                        break;
                                }

                                digitCount = 0;
                                n = BigDecimalZero;
                                positive = true;
                                advance();
                                state = State.inExponent;
                                break;

                            case '0':
                            case '1':
                            case '2':
                            case '3':
                            case '4':
                            case '5':
                            case '6':
                            case '7':
                            case '8':
                            case '9':
                                n = n.movePointRight(1);
                                n = n.add(BigDecimal.valueOf(nextChar - '0'));
                                digitCount++;
                                advance();
                                break;

                            case '+':
                            case '-':
                                if (state == State.inExponent && digitCount == 0) {
                                    // We're looking at the sign after the 'e'.
                                    positive = !positive;
                                    advance();
                                    break;
                                }
                                // fall through - end of number

                            default:
                                // Reached end of number.
                                switch (state) {
                                    case leftOfPoint:
                                        mantissa = n;
                                        break;
                                    case rightOfPoint:
                                        mantissa = mantissa.add(n.movePointRight(-digitCount));
                                        break;
                                    default:
                                        if (!positive) {
                                            n = n.negate();
                                        }
                                        exponent = n.intValue();
                                        break;
                                }
                                return makeNumber(mantissa, exponent, hasDot);
                        }
                    }
                case '_':
                case 'a':
                case 'b':
                case 'c':
                case 'd':
                case 'e':
                case 'f':
                case 'g':
                case 'h':
                case 'i':
                case 'j':
                case 'k':
                case 'l':
                case 'm':
                case 'n':
                case 'o':
                case 'p':
                case 'q':
                case 'r':
                case 's':
                case 't':
                case 'u':
                case 'v':
                case 'w':
                case 'x':
                case 'y':
                case 'z':
                case 'A':
                case 'B':
                case 'C':
                case 'D':
                case 'E':
                case 'F':
                case 'G':
                case 'H':
                case 'I':
                case 'J':
                case 'K':
                case 'L':
                case 'M':
                case 'N':
                case 'O':
                case 'P':
                case 'Q':
                case 'R':
                case 'S':
                case 'T':
                case 'U':
                case 'V':
                case 'W':
                case 'X':
                case 'Y':
                case 'Z':
                case '$':
                    /* parse an identifier */
                    id = new StringBuilder();
                    for (; ; ) {
                        id.append((char) nextChar);
                        advance();
                        switch (nextChar) {
                            case 'a':
                            case 'b':
                            case 'c':
                            case 'd':
                            case 'e':
                            case 'f':
                            case 'g':
                            case 'h':
                            case 'i':
                            case 'j':
                            case 'k':
                            case 'l':
                            case 'm':
                            case 'n':
                            case 'o':
                            case 'p':
                            case 'q':
                            case 'r':
                            case 's':
                            case 't':
                            case 'u':
                            case 'v':
                            case 'w':
                            case 'x':
                            case 'y':
                            case 'z':
                            case 'A':
                            case 'B':
                            case 'C':
                            case 'D':
                            case 'E':
                            case 'F':
                            case 'G':
                            case 'H':
                            case 'I':
                            case 'J':
                            case 'K':
                            case 'L':
                            case 'M':
                            case 'N':
                            case 'O':
                            case 'P':
                            case 'Q':
                            case 'R':
                            case 'S':
                            case 'T':
                            case 'U':
                            case 'V':
                            case 'W':
                            case 'X':
                            case 'Y':
                            case 'Z':
                            case '0':
                            case '1':
                            case '2':
                            case '3':
                            case '4':
                            case '5':
                            case '6':
                            case '7':
                            case '8':
                            case '9':
                            case '_':
                            case '$':
                                break;
                            default:
                                String strId = id.toString();
                                Integer i = m_resWordsTable.get(strId.toUpperCase());

                                if (i == null) {
                                    // identifier
                                    return makeToken(ExpSymbols.ID, strId);
                                } else {
                                    if (i == ExpSymbols.DEFAULT && nextChar == ':') {
                                        //把[default:]转换成一个特殊符号
                                        advance();
                                        i = ExpSymbols.DEFAULT_COLON;
                                        strId = "DEFAULT:";
                                    }
                                    return makeToken(i, strId);
                                    // reserved word
                                }
                        }
                    }
                case ';':
                    advance();
                    return makeToken(ExpSymbols.NCOUNT, ";");
                case '[':
                    advance();
                    return makeToken(ExpSymbols.LSBRACE, "[");
                case ']':
                    advance();
                    return makeToken(ExpSymbols.RSBRACE, "]");
                case '{':
                    return doLBRACE();

                case '(':
                    advance();
                    return makeToken(ExpSymbols.LPAREN, "(");
                case '}':
                    // advance();
                    // return makeToken(ExpSymbols.RBRACE, "}");
                    return doRBRACE();
                case ')':
                    advance();
                    return makeToken(ExpSymbols.RPAREN, ")");
                case '<':
                    advance();
                    switch (nextChar) {
                        case '>':
                            advance();
                            return makeToken(ExpSymbols.NE, "<>");
                        case '=':
                            advance();
                            return makeToken(ExpSymbols.LE, "<=");
                        default:
                            return makeToken(ExpSymbols.LT, "<");
                    }
                case '>':
                    advance();
                    switch (nextChar) {
                        case '=':
                            advance();
                            return makeToken(ExpSymbols.GE, ">=");
                        default:
                            return makeToken(ExpSymbols.GT, ">");
                    }
                case '&':
                    advance();
                    switch (nextChar) {
                        case '&':
                            advance();
                            return makeToken(ExpSymbols.AA, "&&");
                        default:
                            return makeToken(ExpSymbols.AND, "&");
                    }
                case '|':
                    advance();
                    switch (nextChar) {
                        case '|':
                            advance();
                            return makeToken(ExpSymbols.CONCAT, "||");
                        default:
                            return makeToken(ExpSymbols.OR, "|");
                    }
                case '~':
                    advance();
                    return makeToken(ExpSymbols.OR, "~");
                case '^':
                    advance();
                    return makeToken(ExpSymbols.XOR, "^");
                case '+':
                    advance();
                    return makeToken(ExpSymbols.PLUS, "/");
                case '-':
                    advance();
                    return makeToken(ExpSymbols.MINUS, "-");
                case '*':
                    advance();
                    return makeToken(ExpSymbols.MULTI, "*");
                case '%':
                    advance();
                    return makeToken(ExpSymbols.PERCENT, "%");
                case '/':
                    advance();

                    switch (nextChar) {
                        case '/':
                            // 注释
                            C:
                            for (; ; ) {
                                advance();
                                switch (nextChar) {
                                    case -1:
                                    case '\t':
                                    case '\n':
                                    case '\r':
                                        break C;
                                }
                            }
                            continue;
                        case '*':
                            // 一定以*/结束
                            C:
                            for (; ; ) {
                                advance();
                                switch (nextChar) {
                                    case -1:
                                        break C;
                                    case '*':
                                        advance();
                                        if (nextChar == '/') {
                                            // 注释结束
                                            break C;
                                        }

                                }
                            }
                            advance();
                            continue;
                        default:
                            return makeToken(ExpSymbols.DIVISION, "/");
                    }
                case '!':
                    advance();
                    switch (nextChar) {
                        case '=':
                            advance();

                            if (nextChar == '=') {
                                //处理===的情况~~
                                advance();
                            }
                            return makeToken(ExpSymbols.NE, "!=");
                        default:
                            return makeToken(ExpSymbols.BANG, "!");
                    }
                case '#':
                    advance();
                    return makeToken(ExpSymbols.NO, "#");
                case '?':
                    advance();
                    switch (nextChar) {
                        case '.':
                            advance();
                            return makeToken(ExpSymbols.QMARK_DOT, "?.");
                    }
                    return makeToken(ExpSymbols.QMARK, "?");
                case ':':
                    advance();
                    return makeToken(ExpSymbols.COLON, ":");

                case ',':
                    advance();
                    return makeToken(ExpSymbols.COMMA, ",");
//                case '$':
//                    advance();
//                    return makeToken(ExpSymbols.DOLLAR, "$");
                case '@':
                    advance();
                    return makeToken(ExpSymbols.AT, "@");
                case '=':
                    advance();
                    switch (nextChar) {
                        case '=':
                            advance();
                            if (nextChar == '=') {
                                //处理===的情况~~
                                advance();
                            }
                            return makeToken(ExpSymbols.EQ2, "==");
                        case '>':
                            advance();
                            return makeToken(ExpSymbols.NF, "=>");
                        default:
                            return makeToken(ExpSymbols.EQ, "=");
                    }
                case '"':
                    /* parse a double-quoted string */
                    id = new StringBuilder();
                    for (; ; ) {
                        advance();
                        switch (nextChar) {
                            case '"':
                                advance();
                                if (nextChar == '"') {
                                    // " escaped with "
                                    id.append('"');
                                    break;
                                } else {
                                    // end of string
                                    return makeString(id.toString());
                                }
                            case -1:
                                return makeString(id.toString());
                            default:
                                id.append((char) nextChar);
                        }
                    }
                case '\'':
                    /* parse a double-quoted string */
                    id = new StringBuilder();
                    for (; ; ) {
                        advance();
                        switch (nextChar) {
                            case '\'':
                                advance();
                                // end of string
                                return makeString(id.toString());
                            case -1:
                                return makeString(id.toString());
                            default:
                                id.append((char) nextChar);
                        }
                    }
                case '`':
                    /* parse a double-quoted string */
                    id = new StringBuilder();
                    for (; ; ) {
                        advance();
                        switch (nextChar) {
                            case '`':
                                advance();
                                // end of string
                                return makeSymbol(ExpSymbols.EXP_STRING, id.toString());
                            case -1:
                                return makeSymbol(ExpSymbols.EXP_STRING, id.toString());
                            case '\\':
                                if (lookahead(1) == '`') {
                                    advance();
                                    id.append((char) nextChar);
                                    continue;
                                }
                            default:
                                id.append((char) nextChar);
                        }
                    }
                default:
                case ' ':
                case '\t':
                    iPrevChar = iChar;
                    advance();
                    break;
                case '\n':
                case '\r':
                    // ASI: 记录遇到换行符
                    newlineEncountered = true;
                    iPrevChar = iChar;
                    advance();
                    break;

            }
        }
    }

    private boolean isInExport() {
        for (int i1 = this.lines.size() - 1; i1 >= 0; i1--) {

        }
        return false;
    }

    protected int previous() {
        return s.charAt(i < 2 ? 0 : (i - 2));
    }
}