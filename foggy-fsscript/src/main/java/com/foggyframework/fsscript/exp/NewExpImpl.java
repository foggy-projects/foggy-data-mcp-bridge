package com.foggyframework.fsscript.exp;

import com.foggyframework.core.ex.RX;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.ListExp;
import com.foggyframework.fsscript.parser.spi.NewExp;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class NewExpImpl extends AbstractExp<String> implements NewExp {
    List<Exp> args;

    public NewExpImpl(String value, List<Exp> args) {
        super(value);
        this.args = args;
    }

    @Override
    public String toString() {
        return "NewExpImpl{" +
                "args=" + args +
                ", value=" + value +
                '}';
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        return newInstance(ee, null);
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return Object.class;
    }

    @Override
    public <T> T newInstance(ExpEvaluator evaluator, Class<T> retType) {
        Object obj = evaluator.getVar(value);
        if (obj instanceof ImportStaticClassExp.StaticClassPropertyFunction) {

            Class clazz = ((ImportStaticClassExp.StaticClassPropertyFunction) obj).getBeanClass();
            try {
                if (args == null || args.isEmpty()) {
                    //默认无参构建函数

                    return (T) clazz.getConstructor().newInstance();

                } else {
                    Object[] objs = new Object[args.size()];
                    Class[] clss = new Class[args.size()];
                    for (int i = 0; i < args.size(); i++) {
                        objs[i] = args.get(i).evalResult(evaluator);
                        if (objs[i] == null) {
                            clss[i] = Object.class;
                        } else {
                            clss[i] = objs[i].getClass();
                        }
                    }
                    return (T) clazz.getConstructor(clss).newInstance(objs);
                }
            } catch (InstantiationException e) {
                throw RX.throwB(e);
            } catch (IllegalAccessException e) {
                throw RX.throwB(e);
            } catch (InvocationTargetException e) {
                throw RX.throwB(e);
            } catch (NoSuchMethodException e) {
                throw RX.throwB(e);
            }
        } else {
            if (value.equalsIgnoreCase("Date")) {
                if (args == null || args.isEmpty()) {
                    //默认无参构建函数
                    return (T) new Date();
                } else {
                    Number n = (Number) args.get(0).evalResult(evaluator);
                    if (n == null) {
                        return (T) new Date();
                    }
                    return (T) new Date(n.longValue());
                }
            }else if (value.equalsIgnoreCase("Set")) {
                if (args == null || args.isEmpty()) {
                    //默认无参构建函数
                    return (T) new HashSet<>();
                } else {
                    Collection n = (Collection) args.get(0).evalResult(evaluator);
                    if (n == null) {
                        return (T) new HashSet<>();
                    }
                    return (T) new HashSet(n);
                }
            }
            throw new UnsupportedOperationException(toString());
        }
    }

//    private static final XX xx = new XX();

}
