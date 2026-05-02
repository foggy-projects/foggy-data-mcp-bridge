package com.foggyframework.dataset.db.model.engine.expression.sql;

import com.foggyframework.dataset.db.model.engine.expression.SqlExpHolder;
import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.ArrayList;
import java.util.List;

/**
 * Marker node for REMOVE(...) inside CALCULATE.
 */
public class SqlRemoveExp extends AbstractExp<String> {

    private static final long serialVersionUID = 1L;

    private final List<Exp> args;

    public SqlRemoveExp(List<Exp> args) {
        super("REMOVE");
        this.args = args == null ? List.of() : List.copyOf(args);
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        throw new IllegalArgumentException("REMOVE can only be used as the second argument of CALCULATE");
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return Void.class;
    }

    public List<Exp> getArgs() {
        return args;
    }

    public List<String> getFieldNames() {
        List<String> fields = new ArrayList<>(args.size());
        for (Exp arg : args) {
            Exp unwrapped = unwrap(arg);
            if (!(unwrapped instanceof SqlColumnRefExp)) {
                throw new IllegalArgumentException("CALCULATE_REMOVE_FIELD_NOT_GROUPED: REMOVE only accepts groupBy field names");
            }
            fields.add(((SqlColumnRefExp) unwrapped).getColumnName());
        }
        return fields;
    }

    private static Exp unwrap(Exp exp) {
        Exp current = exp;
        while (current instanceof SqlExpHolder) {
            Exp inner = ((SqlExpHolder) current).getInnerSqlExp();
            if (inner == null || inner == current) {
                break;
            }
            current = inner;
        }
        return current;
    }
}
