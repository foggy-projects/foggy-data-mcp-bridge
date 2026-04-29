package com.foggyframework.dataset.db.model.engine.expression;

import com.foggyframework.dataset.db.model.engine.expression.sql.*;
import com.foggyframework.dataset.db.model.engine.compose.capability.*;
import com.foggyframework.fsscript.exp.DefaultExpFactory;
import com.foggyframework.fsscript.exp.EmptyExp;
import com.foggyframework.fsscript.exp.UnresolvedFunCall;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ListExp;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

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

        // Check capability registry for sql_scalar functions
        Exp capabilityExp = tryCreateCapabilitySqlExp(name, argList);
        if (capabilityExp != null) {
            return new SqlExpWrapper(this, name, args, capabilityExp);
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

        // Check capability registry for sql_scalar functions
        Exp capabilityExp = tryCreateCapabilitySqlExp(funcName, argList);
        if (capabilityExp != null) {
            return new SqlExpWrapper(this, name, args, capabilityExp);
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

            // Check capability registry for sql_scalar functions
            Exp capabilityExp = tryCreateCapabilitySqlExp(funcName, new ArrayList<>(args));
            if (capabilityExp != null) {
                return new SqlExpFunCallWrapper(capabilityExp);
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
        // 括号表达式 `()`：空 / 单括号分组 / 多元素列表（用作 IN / NOT IN 的 RHS）
        if ("()".equals(name)) {
            if (args.isEmpty() || (args.size() == 1 && args.get(0) instanceof EmptyExp)) {
                return new SqlListExp(Collections.emptyList());
            }
            if (args.size() == 1) {
                return args.get(0);
            }
            return new SqlListExp(args);
        }

        // IN / NOT_IN 成员测试：RHS 规范化为 SqlListExp
        if (args.size() == 2 && AllowedFunctions.isMembershipOperator(name)) {
            String sqlOp = AllowedFunctions.toSqlOperator(name.toUpperCase());
            return new SqlBinaryExp(args.get(0), sqlOp, normalizeInRhs(sqlOp, args.get(1)));
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
                case "<>":  // SQL standard not-equal (parser may convert != to <>)
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
     * Try to resolve a function from the thread-local CapabilityExpContext.
     * Returns a CapabilitySqlFunctionExp if the function is a registered sql_scalar,
     * or null if no registry is set or the function is not registered.
     *
     * <p>Validates: registration, policy, allowed_in surface, dialect support,
     * and argument arity. All violations are fail-closed.</p>
     */
    private Exp tryCreateCapabilitySqlExp(String name, List<Exp> args) {
        CapabilityRegistry registry = CapabilityExpContext.getRegistry();
        CapabilityPolicy policy = CapabilityExpContext.getPolicy();
        String dialect = CapabilityExpContext.getDialect();

        if (registry == null || !registry.hasFunction(name)) {
            return null;
        }

        CapabilityRegistry.FunctionEntry entry = registry.getFunction(name);
        FunctionDescriptor descriptor = entry.getDescriptor();

        // Must be sql_scalar
        if (!"sql_scalar".equals(descriptor.getKind())) {
            return null;
        }

        // Policy check
        if (policy == null || !policy.isFunctionAllowed(name)) {
            throw new SecurityException(
                    "Function '" + name + "' is not allowed by the current policy");
        }

        // Surface check: must be allowed in formula or compose_column
        List<String> allowedIn = descriptor.getAllowedIn();
        if (!allowedIn.contains("formula") && !allowedIn.contains("compose_column")) {
            throw new SecurityException(
                    "Function '" + name + "' is not allowed in formula/compose_column");
        }

        // Arity check (filter EmptyExp — fsscript parser produces one for zero-arg calls)
        int requiredArgs = (int) descriptor.getArgsSchema().stream()
                .filter(a -> Boolean.TRUE.equals(a.get("required")))
                .count();
        int totalArgs = descriptor.getArgsSchema().size();
        long actualArgs = args.stream()
                .filter(a -> !(a instanceof com.foggyframework.fsscript.exp.EmptyExp))
                .count();
        if (actualArgs < requiredArgs || actualArgs > totalArgs) {
            throw new SecurityException(
                    "Function '" + name + "' expects " + totalArgs
                            + " arguments, got " + actualArgs);
        }

        // Dialect check
        if (dialect != null && !descriptor.getDialects().contains(dialect)) {
            throw new SecurityException(
                    "Function '" + name + "' does not support dialect '" + dialect + "'");
        }

        // Create a CapabilitySqlFunctionExp that will invoke the renderer at eval time
        return new CapabilitySqlFunctionExp(name, args, entry.getRenderer(), descriptor, dialect);
    }

    /**
     * 把 IN / NOT IN 的 RHS 规范化为 {@link SqlListExp}。
     *
     * <ul>
     *     <li>已经是 {@link SqlListExp} → 直接用；空列表抛编译错误</li>
     *     <li>被 {@link SqlExpHolder} 包裹（外层分组括号会叠多层）→ 循环解包</li>
     *     <li>其他单一 Exp → 包成单元素 {@code SqlListExp}，允许 {@code x in (1)} / {@code x in (col)} 写法</li>
     * </ul>
     */
    private Exp normalizeInRhs(String sqlOp, Exp rhs) {
        Exp inner = rhs;
        while (inner instanceof SqlExpHolder) {
            Exp unwrapped = ((SqlExpHolder) inner).getInnerSqlExp();
            if (unwrapped == null || unwrapped == inner) {
                break;
            }
            inner = unwrapped;
        }
        if (inner instanceof EmptyExp
                || (inner instanceof SqlListExp && ((SqlListExp) inner).isEmpty())) {
            throw emptyInListError(sqlOp);
        }
        if (inner instanceof SqlListExp) {
            return inner;
        }
        return new SqlListExp(Collections.singletonList(inner));
    }

    private static IllegalArgumentException emptyInListError(String sqlOp) {
        return new IllegalArgumentException(
                sqlOp + " 列表不能为空（空 IN 在多数数据库里是 SQL 语法错误；"
                        + "需要恒为 false 时请使用 '1 == 0'）");
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
    static class SqlExpWrapper extends UnresolvedFunCall implements SqlExpHolder {

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
        public Exp getInnerSqlExp() {
            return sqlExp;
        }

        @Override
        public Object evalValue(com.foggyframework.fsscript.parser.spi.ExpEvaluator context) {
            return sqlExp.evalValue(context);
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
    static class SqlExpFunCallWrapper extends com.foggyframework.fsscript.exp.AbstractExp<Exp> implements SqlExpHolder {
        private static final long serialVersionUID = 1L;

        public SqlExpFunCallWrapper(Exp sqlExp) {
            super(sqlExp);
        }

        @Override
        public Exp getInnerSqlExp() {
            return value;
        }

        @Override
        public Object evalValue(com.foggyframework.fsscript.parser.spi.ExpEvaluator context) {
            return value.evalValue(context);
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
