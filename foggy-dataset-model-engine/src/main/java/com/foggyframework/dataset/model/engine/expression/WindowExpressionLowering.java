package com.foggyframework.dataset.model.engine.expression;

import com.foggyframework.dataset.model.engine.expression.sql.*;
import com.foggyframework.fsscript.parser.spi.Exp;
import java.util.function.Function;

/** Structural lowering: OVER belongs to function nodes, never to a surrounding scalar expression. */
final class WindowExpressionLowering {
    private WindowExpressionLowering() {}
    static Exp unwrap(Exp exp) {
        while (exp instanceof SqlExpHolder holder) exp = holder.getInnerSqlExp();
        return exp;
    }
    static boolean windowCall(Exp exp) {
        exp = unwrap(exp);
        return exp instanceof SqlFunctionExp fn && (AllowedFunctions.isWindowFunction(fn.getFunctionName())
                || AllowedFunctions.isAggregateFunction(fn.getFunctionName()));
    }
    static Exp lift(Exp exp, Function<SqlFunctionExp, Exp> lift) {
        exp = unwrap(exp);
        if (windowCall(exp)) return lift.apply((SqlFunctionExp) exp);
        if (exp instanceof SqlBinaryExp b) return new SqlBinaryExp(lift(b.getLeft(), lift), b.getOperator(), lift(b.getRight(), lift));
        if (exp instanceof SqlUnaryExp u) return new SqlUnaryExp(u.getOperator(), lift(u.getOperand(), lift));
        if (exp instanceof SqlFunctionExp f) return new SqlFunctionExp(f.getFunctionName(), f.getArgs().stream().map(a -> lift(a, lift)).toList());
        if (exp instanceof SqlListExp l) return new SqlListExp(l.getItems().stream().map(a -> lift(a, lift)).toList());
        return exp;
    }
    static boolean containsCall(Exp exp) {
        boolean[] found = {false};
        lift(exp, fn -> { found[0] = true; return fn; });
        return found[0];
    }
}
