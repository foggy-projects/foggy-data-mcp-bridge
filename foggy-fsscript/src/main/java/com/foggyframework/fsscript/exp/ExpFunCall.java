package com.foggyframework.fsscript.exp;

import com.foggyframework.core.ex.RX;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.ExpFactory;
import com.foggyframework.fsscript.support.PropertyProxySupport;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 表达式函数调用，支持如 closures[0]()、obj.getHandler()() 等形式
 * 与 UnresolvedFunCall 不同，这里的函数本身是一个表达式，需要先求值
 */
public class ExpFunCall extends AbstractExp<Exp> {
    private static final long serialVersionUID = 1L;

    private final List<Exp> args;
    private transient ExpFactory expFactory;

    public ExpFunCall(ExpFactory expFactory, Exp funExp, List<Exp> args) {
        super(funExp);
        this.args = args;
        this.expFactory = expFactory;
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        // 如果是属性访问表达式，使用方法调用机制
        if (value instanceof PropertyExp) {
            return evalMethodCall(evaluator, (PropertyExp) value);
        }

        // 先对函数表达式求值
        Object funObj = value.evalResult(evaluator);

        // 如果变量查找返回 null，尝试从 FunctionSet 查找（支持内置函数如 checkDaysRange）
        if (funObj == null && value instanceof IdExp) {
            String funName = ((IdExp) value).getValue();
            FunDef fd = expFactory.getFunctionSet().getFun(funName);
            if (fd != null) {
                Exp[] expArgs = args.toArray(new Exp[0]);
                return fd.execute(evaluator, expArgs);
            }
            throw RX.throwB("未能找到函数 [" + funName + "]");
        }

        if (funObj == null) {
            throw RX.throwB("函数表达式求值为 null: " + value);
        }

        // 调用函数
        if (funObj instanceof FsscriptFunction) {
            return ((FsscriptFunction) funObj).apply(evalArgs(evaluator));
        } else if (funObj instanceof Function) {
            return ((Function<Object[], Object>) funObj).apply(evalArgs(evaluator));
        } else if (funObj instanceof FunDef) {
            // FunDef 需要传递 Exp[] 而不是 Object[]
            Exp[] expArgs = args.toArray(new Exp[0]);
            return ((FunDef) funObj).execute(evaluator, expArgs);
        } else {
            throw RX.throwB("表达式结果不是可调用的函数: " + funObj.getClass().getName());
        }
    }

    /**
     * 处理方法调用: obj.method(args) 或 obj?.method(args)
     */
    private Object evalMethodCall(ExpEvaluator evaluator, PropertyExp propExp) {
        // 获取对象
        Object obj = propExp.getExp().evalResult(evaluator);
        if (obj == null) {
            // 可选链调用 (?.) 返回 null，普通调用 (.) 抛出异常
            if (propExp instanceof OptionalPropertyExp) {
                return null;
            }
            throw RX.throwBUserTip("调用方法时左值为空: " + propExp.getExp() + "." + propExp.getValue() + "()", "系统异常");
        }

        String methodName = propExp.getValue();
        Object[] argValues = evalArgs(evaluator);

        // 如果对象实现了 PropertyFunction 接口
        if (obj instanceof PropertyFunction) {
            return ((PropertyFunction) obj).invoke(evaluator, methodName, argValues);
        }

        // 尝试查找并调用方法
        Method m = MethodFinder.findMethod(obj.getClass(), methodName, argValues);
        if (m == null) {
            // 尝试 Map 中的函数
            if (obj instanceof Map) {
                Object f = ((Map<?, ?>) obj).get(methodName);
                if (f instanceof Function) {
                    return ((Function<Object[], Object>) f).apply(argValues);
                }
            }

            // 尝试自动修复参数
            m = MethodFinder.autoFixArgsAndFindMethod(obj.getClass(), methodName, argValues);
            if (m == null) {
                // 尝试 JavaScript 常见方法（如 push, includes 等）
                PropertyProxySupport.JsCommonInvokeResult c = PropertyProxySupport.tryCommonInvoke(evaluator, obj, methodName, argValues);
                if (c != null) {
                    return c.getResult();
                }
                throw RX.throwB("未找到方法 [" + methodName + "] 在 class[" + obj.getClass() + "]中");
            }
        }

        try {
            if (!m.isAccessible()) {
                m.setAccessible(true);
            }
            return m.invoke(obj, argValues);
        } catch (Exception e) {
            throw RX.throwB("调用方法 [" + methodName + "] 失败: " + e.getMessage(), e);
        }
    }

    private Object[] evalArgs(ExpEvaluator evaluator) {
        // 只有当唯一参数是 EmptyExp 时才视为无参数调用（如 foo()）
        // 否则 EmptyExp 应该求值为 null（如 foo(1,,3) 中间的空参数）
        if (args.size() == 1 && args.get(0) instanceof EmptyExp) {
            return new Object[0];
        }

        Object[] argValues = new Object[args.size()];
        int i = 0;
        for (Exp exp : args) {
            argValues[i++] = exp.evalResult(evaluator);
        }
        return argValues;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return null;
    }

    @Override
    public String toString() {
        return "[ExpFunCall : " + value + ",args:" + args + "]";
    }
}
