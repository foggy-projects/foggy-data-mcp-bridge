package com.foggyframework.fsscript.exp;

import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.ExpFactory;
import com.foggyframework.fsscript.parser.spi.ListExp;
import com.foggyframework.fsscript.parser.spi.SubHolder;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

/** Compound assignment to a property or subscript, resolving its receiver and key once. */
public class CompoundAssignExp implements Exp, Serializable {
    private static final long serialVersionUID = 1L;

    private final Exp target;
    private final String operator;
    private final Exp right;
    private final transient ExpFactory factory;

    public CompoundAssignExp(Exp target, String operator, Exp right, ExpFactory factory) {
        this.target = target;
        this.operator = operator;
        this.right = right;
        this.factory = factory;
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        if (target instanceof PropertyExp) {
            return assignProperty((PropertyExp) target, evaluator);
        }
        return assignSubscript((SubExp) target, evaluator);
    }

    private Object assignProperty(PropertyExp property, ExpEvaluator evaluator) {
        Object receiver = property.exp.evalResult(evaluator);
        Object oldValue = receiver instanceof Map
                ? ((Map<?, ?>) receiver).get(property.value)
                : property.getPropertyValue(receiver);
        Object rightValue = right.evalResult(evaluator);
        Object newValue = apply(evaluator, oldValue, rightValue);

        if (receiver instanceof Map) {
            ((Map<Object, Object>) receiver).put(property.value, newValue);
        } else {
            BeanInfoHelper.getClassHelper(receiver.getClass())
                    .getBeanProperty(property.value, true).setBeanValue(receiver, newValue);
        }
        return newValue;
    }

    private Object assignSubscript(SubExp subscript, ExpEvaluator evaluator) {
        Object receiver = subscript.value.evalResult(evaluator);
        if (receiver == null) {
            return null;
        }
        Object key = subscript.sub.evalResult(evaluator);
        if (key == null) {
            return null;
        }
        if (receiver instanceof SubHolder) {
            throw new UnsupportedOperationException("SubHolder does not support compound assignment");
        }

        Object oldValue = new SubExp(new ObjectExp<>(receiver), new ObjectExp<>(key)).evalResult(evaluator);
        Object rightValue = right.evalResult(evaluator);
        Object newValue = apply(evaluator, oldValue, rightValue);
        writeSubscript(receiver, key, newValue);
        return newValue;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeSubscript(Object receiver, Object key, Object value) {
        if (receiver instanceof Map) {
            ((Map<Object, Object>) receiver).put(key, value);
        } else if (receiver.getClass().isArray() && key instanceof Number) {
            Array.set(receiver, ((Number) key).intValue(), value);
        } else if (receiver instanceof List && key instanceof Number) {
            ((List) receiver).set(((Number) key).intValue(), value);
        } else {
            BeanInfoHelper.setObjectProperty(receiver, key.toString(), value);
        }
    }

    private Object apply(ExpEvaluator evaluator, Object oldValue, Object rightValue) {
        ListExp args = new ListExp(2);
        args.add(new ObjectExp<>(oldValue));
        args.add(new ObjectExp<>(rightValue));
        ExpFactory activeFactory = factory != null ? factory : evaluator.getExpFactory();
        if (activeFactory == null) {
            activeFactory = DefaultExpFactory.DEFAULT;
        }
        return activeFactory.createUnresolvedFunCall(operator, args, false).evalResult(evaluator);
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return Object.class;
    }
}
