package com.foggyframework.fsscript.exp;

import com.foggyframework.core.utils.ErrorUtils;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
@Slf4j
public class NCountExp extends AbstractExp<List<Exp>> implements Exp {

    /**
     *
     */
    private static final long serialVersionUID = 6473332152253517248L;

    public NCountExp(List<Exp> l) {
        super(l);
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        Object result = null;
        try {
            for (Exp v : value) {
                if (v == BreakExp.BREAK) {
                    return BreakExp.BREAK;
                }
                if (v == ContinueExp.CONTINUE) {
                    return ContinueExp.CONTINUE;
                }
                result = v.evalValue(evaluator);
                if (v instanceof ReturnExp) {
                    // 函数中止
                    if (result == null) {
                        return ReturnExp.EMPTY_RETURN_EXP_OBJECT;
                    }
                    if (result instanceof ReturnExpObject) {
                        return result;
                    }
                    return new ReturnExpObject(result);
                }
                if (result == BreakExp.BREAK) {
                    return BreakExp.BREAK;
                }
                if (result == ContinueExp.CONTINUE) {
                    return result;
                }
                // 2018-07-07 加入，现在ReturnExp返的对象，都是经过ReturnExpObject封装的
                if (result != null) {
                    if (result instanceof ReturnExpObject) {
                        return result;
                    }
//						return new ReturnExpObject(result);
                }

            }
        } catch (Throwable t) {
            // throw new RuntimeException("执行表达式\n" + this + "\n失败", t);
            if (log.isWarnEnabled()) {
                log.warn("执行表达式\n" + this + "\n失败 : " + t.getMessage());
            }
            throw ErrorUtils.toRuntimeException(t);
        }
        // return last
        return result;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return Object.class;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[NCountExp:\n");
        for (Exp v : value) {
            sb.append(v).append(";\n");
        }
        sb.append("]\n");
        return sb.toString();
    }
}