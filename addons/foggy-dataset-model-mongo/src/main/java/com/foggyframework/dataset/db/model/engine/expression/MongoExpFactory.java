package com.foggyframework.dataset.db.model.engine.expression;

import com.foggyframework.dataset.db.model.engine.expression.mongo.*;
import com.foggyframework.dataset.jdbc.model.engine.expression.mongo.*;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.exp.DefaultExpFactory;
import com.foggyframework.fsscript.exp.UnresolvedFunCall;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ListExp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB 表达式工厂
 * <p>
 * 继承 DefaultExpFactory，为运算符和函数创建专门的 MongoExp。
 * 执行时直接生成 MongoDB 聚合表达式，而不是执行计算。
 * </p>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
public class MongoExpFactory extends DefaultExpFactory {

    /**
     * 创建标识符表达式（列引用）
     */
    @Override
    public Exp createId(String str) {
        if (log.isDebugEnabled()) {
            log.debug("MongoExpFactory.createId('{}') -> MongoColumnRefExp", str);
        }
        return new MongoColumnRefExp(str);
    }

    /**
     * 创建数字字面量
     */
    @Override
    public Exp createNumber(Number n) {
        return new MongoLiteralExp(n);
    }

    /**
     * 创建字符串字面量
     */
    @Override
    public Exp createString(String str) {
        return new MongoLiteralExp(str);
    }

    /**
     * 创建函数调用表达式
     */
    @Override
    public UnresolvedFunCall createUnresolvedFunCall(String name, ListExp args, boolean fix) {
        if (fix) {
            fixArray(args);
        }

        List<Exp> argList = new ArrayList<>(args);

        if (log.isDebugEnabled()) {
            log.debug("MongoExpFactory.createUnresolvedFunCall('{}', args.size={})", name, args.size());
        }

        // 处理运算符和函数
        Exp mongoExp = createMongoExp(name, argList);
        if (mongoExp != null) {
            return new MongoExpWrapper(this, name, args, mongoExp);
        }

        // 不支持的函数
        throw new SecurityException("Function not allowed in MongoDB calculated field expression: " + name);
    }

    @Override
    public UnresolvedFunCall createUnresolvedFunCall(Exp name, ListExp args, boolean fix) {
        if (fix) {
            fixArray(args);
        }

        String funcName = extractFunctionName(name);
        List<Exp> argList = new ArrayList<>(args);

        Exp mongoExp = createMongoExp(funcName, argList);
        if (mongoExp != null) {
            return new MongoExpWrapper(this, name, args, mongoExp);
        }

        throw new SecurityException("Function not allowed in MongoDB calculated field expression: " + funcName);
    }

    /**
     * 创建表达式函数调用（如 YEAR(date_column)）
     */
    @Override
    public Exp createExpFunCall(Exp funExp, ListExp args) {
        if (funExp instanceof MongoColumnRefExp) {
            String funcName = ((MongoColumnRefExp) funExp).getColumnName();
            String upperName = funcName.toUpperCase();

            if (log.isDebugEnabled()) {
                log.debug("MongoExpFactory.createExpFunCall: funcName='{}', args.size={}", funcName, args.size());
            }

            // 检查是否是允许的函数
            if (MongoAllowedFunctions.isAllowed(upperName)) {
                List<Exp> argList = new ArrayList<>(args);
                Exp mongoExp = new MongoFunctionExp(upperName, argList);
                return new MongoExpFunCallWrapper(mongoExp);
            }

            throw new SecurityException("Function not allowed in MongoDB calculated field expression: " + funcName);
        }

        return super.createExpFunCall(funExp, args);
    }

    /**
     * 根据函数名和参数创建对应的 MongoExp
     */
    private Exp createMongoExp(String name, List<Exp> args) {
        // 括号表达式
        if ("()".equals(name) && args.size() == 1) {
            return args.get(0);
        }

        // 二元运算符
        if (args.size() == 2) {
            if (MongoAllowedFunctions.isArithmeticOperator(name) ||
                MongoAllowedFunctions.isComparisonOperator(name) ||
                MongoAllowedFunctions.isLogicalOperator(name)) {
                return new MongoBinaryExp(args.get(0), name, args.get(1));
            }
        }

        // 一元运算符
        if (args.size() == 1) {
            if ("-".equals(name) || "!".equals(name) || "NOT".equalsIgnoreCase(name)) {
                return new MongoUnaryExp(name, args.get(0));
            }
        }

        // 允许的函数
        String upperName = name.toUpperCase();
        if (MongoAllowedFunctions.isAllowed(upperName)) {
            return new MongoFunctionExp(upperName, args);
        }

        return null;
    }

    private String extractFunctionName(Exp name) {
        if (name instanceof MongoColumnRefExp) {
            return ((MongoColumnRefExp) name).getColumnName();
        }
        return name.toString();
    }

    /**
     * MongoExp 包装器
     */
    static class MongoExpWrapper extends UnresolvedFunCall {

        private final Exp mongoExp;

        public MongoExpWrapper(MongoExpFactory factory, String name, ListExp args, Exp mongoExp) {
            super(factory, name, args);
            this.mongoExp = mongoExp;
        }

        public MongoExpWrapper(MongoExpFactory factory, Exp name, ListExp args, Exp mongoExp) {
            super(factory, name, args);
            this.mongoExp = mongoExp;
        }

        @Override
        public Object evalValue(com.foggyframework.fsscript.parser.spi.ExpEvaluator context) {
            return mongoExp.evalValue(context);
        }

        @Override
        public Class<?> getReturnType(com.foggyframework.fsscript.parser.spi.ExpEvaluator evaluator) {
            return MongoFragment.class;
        }

        @Override
        public String toString() {
            return "[MongoExpWrapper:" + value + " -> " + mongoExp + "]";
        }
    }

    /**
     * ExpFunCall 的 Mongo 包装器
     */
    static class MongoExpFunCallWrapper extends AbstractExp<Exp> {
        private static final long serialVersionUID = 1L;

        public MongoExpFunCallWrapper(Exp mongoExp) {
            super(mongoExp);
        }

        @Override
        public Object evalValue(com.foggyframework.fsscript.parser.spi.ExpEvaluator context) {
            return value.evalValue(context);
        }

        @Override
        public Class<?> getReturnType(com.foggyframework.fsscript.parser.spi.ExpEvaluator evaluator) {
            return MongoFragment.class;
        }

        @Override
        public String toString() {
            return "[MongoExpFunCallWrapper -> " + value + "]";
        }
    }
}
