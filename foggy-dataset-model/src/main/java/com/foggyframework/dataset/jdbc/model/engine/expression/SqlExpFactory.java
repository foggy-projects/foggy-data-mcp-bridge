package com.foggyframework.dataset.jdbc.model.engine.expression;

import com.foggyframework.dataset.jdbc.model.engine.expression.sql.SqlBinaryExp;
import com.foggyframework.dataset.jdbc.model.engine.expression.sql.SqlColumnRefExp;
import com.foggyframework.dataset.jdbc.model.engine.expression.sql.SqlFunctionExp;
import com.foggyframework.dataset.jdbc.model.engine.expression.sql.SqlLiteralExp;
import com.foggyframework.dataset.jdbc.model.engine.expression.sql.SqlUnaryExp;
import com.foggyframework.fsscript.exp.DefaultExpFactory;
import com.foggyframework.fsscript.exp.ExpFunCall;
import com.foggyframework.fsscript.exp.UnresolvedFunCall;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ListExp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 表达式工厂
 * <p>
 * 继承 DefaultExpFactory，为运算符和函数创建专门的 SqlExp。
 * 执行时直接生成 SQL 片段，而不是执行计算。
 * </p>
 *
 * <h3>支持的运算符</h3>
 * <ul>
 *     <li>算术: +, -, *, /, %</li>
 *     <li>比较: ==, !=, >, <, >=, <=</li>
 *     <li>逻辑: &&, ||, !</li>
 * </ul>
 *
 * <h3>支持的函数</h3>
 * <p>参见 {@link AllowedFunctions}</p>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
public class SqlExpFactory extends DefaultExpFactory {

    /**
     * 创建标识符表达式
     * <p>
     * 在 SQL 表达式上下文中，标识符被解释为列引用。
     * </p>
     */
    @Override
    public Exp createId(String str) {
        // 在 SQL 上下文中，标识符是列引用
        if (log.isDebugEnabled()) {
            log.debug("SqlExpFactory.createId('{}') -> SqlColumnRefExp", str);
        }
        return new SqlColumnRefExp(str);
    }

    /**
     * 创建数字字面量
     */
    @Override
    public Exp createNumber(Number n) {
        return new SqlLiteralExp(n.toString());
    }

    /**
     * 创建字符串字面量
     * <p>
     * 自动添加 SQL 引号并转义特殊字符。
     * </p>
     */
    @Override
    public Exp createString(String str) {
        // 转义 SQL 特殊字符防止注入
        String escaped = escapeSqlString(str);
        return new SqlLiteralExp("'" + escaped + "'");
    }

    /**
     * 创建函数调用表达式
     * <p>
     * 根据函数名类型创建不同的表达式：
     * <ul>
     *     <li>运算符（+, -, *, /, etc.）→ SqlBinaryExp</li>
     *     <li>一元运算符（-负号, !）→ SqlUnaryExp</li>
     *     <li>允许的函数（YEAR, ABS, etc.）→ SqlFunctionExp</li>
     *     <li>不允许的函数 → 抛出安全异常</li>
     * </ul>
     * </p>
     */
    @Override
    public UnresolvedFunCall createUnresolvedFunCall(String name, ListExp args, boolean fix) {
        if (fix) {
            fixArray(args);
        }

        // 转换 args 为 List<Exp>
        List<Exp> argList = new ArrayList<>(args);

        if (log.isDebugEnabled()) {
            log.debug("SqlExpFactory.createUnresolvedFunCall('{}', args.size={}, fix={})", name, args.size(), fix);
            for (int i = 0; i < argList.size(); i++) {
                Exp arg = argList.get(i);
                log.debug("  arg[{}] type: {}, value: {}", i, arg.getClass().getName(), arg);
            }
        }

        // 处理运算符
        Exp sqlExp = createSqlExp(name, argList);
        if (sqlExp != null) {
            if (log.isDebugEnabled()) {
                log.debug("  -> created SqlExp: {}", sqlExp.getClass().getName());
            }
            // 返回一个包装器，evalValue 时委托给 sqlExp
            return new SqlExpWrapper(this, name, args, sqlExp);
        }

        // 不在白名单中的函数
        throw new SecurityException("Function not allowed in calculated field expression: " + name);
    }

    @Override
    public UnresolvedFunCall createUnresolvedFunCall(Exp name, ListExp args, boolean fix) {
        if (fix) {
            fixArray(args);
        }

        // 获取函数名
        String funcName = extractFunctionName(name);
        List<Exp> argList = new ArrayList<>(args);

        // 处理运算符和函数
        Exp sqlExp = createSqlExp(funcName, argList);
        if (sqlExp != null) {
            return new SqlExpWrapper(this, name, args, sqlExp);
        }

        throw new SecurityException("Function not allowed in calculated field expression: " + funcName);
    }

    /**
     * 创建表达式函数调用
     * <p>
     * 处理新的表达式函数调用语法，如 YEAR(date_column)。
     * 在 SQL 上下文中，将函数名（SqlColumnRefExp）识别为 SQL 函数，
     * 而不是列引用。
     * </p>
     */
    @Override
    public Exp createExpFunCall(Exp funExp, ListExp args) {
        // 检查函数表达式是否是 SqlColumnRefExp（由 createId 创建）
        if (funExp instanceof SqlColumnRefExp) {
            String funcName = ((SqlColumnRefExp) funExp).getColumnName();
            String upperName = funcName.toUpperCase();

            if (log.isDebugEnabled()) {
                log.debug("SqlExpFactory.createExpFunCall: funcName='{}', args.size={}", funcName, args.size());
            }

            // 检查是否是允许的 SQL 函数
            if (AllowedFunctions.isAllowed(upperName)) {
                List<Exp> argList = new ArrayList<>(args);
                Exp sqlExp = new SqlFunctionExp(upperName, argList);
                return new SqlExpFunCallWrapper(sqlExp);
            }

            // 不允许的函数
            throw new SecurityException("Function not allowed in calculated field expression: " + funcName);
        }

        // 其他情况使用默认行为
        return super.createExpFunCall(funExp, args);
    }

    /**
     * 根据函数名和参数创建对应的 SqlExp
     */
    private Exp createSqlExp(String name, List<Exp> args) {
        // 括号表达式：直接返回内部表达式
        if ("()".equals(name) && args.size() == 1) {
            if (log.isDebugEnabled()) {
                log.debug("createSqlExp: handling parentheses, inner exp type={}", args.get(0).getClass().getName());
            }
            // 括号表达式，内部表达式已经是正确的类型
            // 直接返回内部表达式，不需要再包装
            return args.get(0);
        }

        // 二元运算符
        if (args.size() == 2) {
            switch (name) {
                case "+":
                case "-":
                case "*":
                case "/":
                case "%":
                    return new SqlBinaryExp(args.get(0), name, args.get(1));
                case "==":
                case "===":
                case "!=":
                case "!==":
                case ">":
                case "<":
                case ">=":
                case "<=":
                    return new SqlBinaryExp(args.get(0), name, args.get(1));
                case "&&":
                case "||":
                    return new SqlBinaryExp(args.get(0), name, args.get(1));
            }
        }

        // 一元运算符
        if (args.size() == 1) {
            // 负号（一元减）
            if ("-".equals(name) || "!".equals(name) || "NOT".equalsIgnoreCase(name)) {
                return new SqlUnaryExp(name, args.get(0));
            }
        }

        // 允许的函数
        String upperName = name.toUpperCase();
        if (AllowedFunctions.isAllowed(upperName)) {
            return new SqlFunctionExp(upperName, args);
        }

        return null;
    }

    /**
     * 从 Exp 中提取函数名
     */
    private String extractFunctionName(Exp name) {
        if (name instanceof SqlColumnRefExp) {
            return ((SqlColumnRefExp) name).getColumnName();
        }
        // 其他情况尝试 toString
        return name.toString();
    }

    /**
     * 转义 SQL 字符串
     */
    private String escapeSqlString(String str) {
        if (str == null) {
            return "";
        }
        // 转义单引号和反斜杠
        return str.replace("\\", "\\\\")
                  .replace("'", "''");
    }

    /**
     * SqlExp 包装器
     * <p>
     * 由于 createUnresolvedFunCall 必须返回 UnresolvedFunCall，
     * 但我们需要返回自定义的 SqlExp，所以使用这个包装器。
     * 在 evalValue 时委托给内部的 sqlExp。
     * </p>
     */
    private static class SqlExpWrapper extends UnresolvedFunCall {

        private final Exp sqlExp;

        public SqlExpWrapper(SqlExpFactory factory, String name, ListExp args, Exp sqlExp) {
            super(factory, name, args);
            this.sqlExp = sqlExp;
        }

        public SqlExpWrapper(SqlExpFactory factory, Exp name, ListExp args, Exp sqlExp) {
            super(factory, name, args);
            this.sqlExp = sqlExp;
        }

        @Override
        public Object evalValue(com.foggyframework.fsscript.parser.spi.ExpEvaluator context) {
            if (log.isDebugEnabled()) {
                log.debug("SqlExpWrapper.evalValue: delegating to sqlExp type={}", sqlExp.getClass().getName());
            }
            // 委托给 sqlExp
            Object result = sqlExp.evalValue(context);
            if (log.isDebugEnabled()) {
                log.debug("SqlExpWrapper.evalValue: result type={}, result={}",
                        result != null ? result.getClass().getName() : "null", result);
            }
            return result;
        }

        @Override
        public Class<?> getReturnType(com.foggyframework.fsscript.parser.spi.ExpEvaluator evaluator) {
            return SqlFragment.class;
        }

        @Override
        public String toString() {
            return "[SqlExpWrapper:" + value + " -> " + sqlExp + "]";
        }
    }

    /**
     * ExpFunCall 的 SQL 包装器
     * <p>
     * 用于 createExpFunCall 返回的 SQL 函数表达式。
     * 在 evalValue 时委托给内部的 sqlExp。
     * </p>
     */
    private static class SqlExpFunCallWrapper extends com.foggyframework.fsscript.exp.AbstractExp<Exp> {
        private static final long serialVersionUID = 1L;

        public SqlExpFunCallWrapper(Exp sqlExp) {
            super(sqlExp);
        }

        @Override
        public Object evalValue(com.foggyframework.fsscript.parser.spi.ExpEvaluator context) {
            if (log.isDebugEnabled()) {
                log.debug("SqlExpFunCallWrapper.evalValue: delegating to sqlExp type={}", value.getClass().getName());
            }
            // 委托给内部的 sqlExp
            Object result = value.evalValue(context);
            if (log.isDebugEnabled()) {
                log.debug("SqlExpFunCallWrapper.evalValue: result type={}, result={}",
                        result != null ? result.getClass().getName() : "null", result);
            }
            return result;
        }

        @Override
        public Class<?> getReturnType(com.foggyframework.fsscript.parser.spi.ExpEvaluator evaluator) {
            return SqlFragment.class;
        }

        @Override
        public String toString() {
            return "[SqlExpFunCallWrapper -> " + value + "]";
        }
    }
}
