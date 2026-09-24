package com.foggyframework.fsscript.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.exp.MethodFinder;
import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.PropertyHolder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public abstract class PropertyProxySupport implements PropertyHolder, PropertyFunction {

    protected abstract Object getProxyObject();

    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {

        Object proxyObject = getProxyObject();
        Assert.notNull(proxyObject, "getProxyObject不能返回空,methodName: " + methodName);
        Method method = MethodFinder.findMethod(proxyObject.getClass(), methodName, args);
        if (method == null) {
            JsCommonInvokeResult c = tryCommonInvoke(evaluator, proxyObject, methodName, args);
            if (c == null) {
                throw RX.throwB("未能在" + proxyObject.getClass() + "中找到方法" + methodName + "，参数: " + Arrays.toString(args));
            }
            return c.result;
        }

        try {
            return method.invoke(proxyObject, args);
        } catch (IllegalAccessException e) {
            log.error(e.getMessage());
            throw RX.throwB(e);
        } catch (InvocationTargetException e) {
            log.error(e.getMessage());
            throw RX.throwB(e.getTargetException());
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JsCommonInvokeResult {
        boolean success;
        Object result;
    }

    public static JsCommonInvokeResult tryCommonInvoke(ExpEvaluator evaluator, Object proxyObject, String methodName, Object[] args) {
        if (proxyObject instanceof Number) {
            Number number = (Number) proxyObject;
            if ("toFixed".equals(methodName)) {
                return new JsCommonInvokeResult(true, numberToFixed(number, args));
            } else if ("toPrecision".equals(methodName)) {
                return new JsCommonInvokeResult(true, numberToPrecision(number, args));
            } else if ("toExponential".equals(methodName)) {
                return new JsCommonInvokeResult(true, numberToExponential(number, args));
            }
        }

        if (proxyObject instanceof List) {
            List ll = (List) proxyObject;
            if (methodName.equals("includes")) {
                return invoke1(proxyObject, "contains", args);
            } else if (methodName.equals("push")) {
                // JavaScript push() 对应 Java add()
                return invoke1(proxyObject, "add", args);
            } else if (methodName.equals("filter")) {
                // JavaScript filter() - 过滤数组
                // 支持三种形式：
                // 1. filter(fn) - 自定义函数
                // 2. filter(Boolean) - 过滤掉 null/undefined/false 等假值
                // 3. filter(java.util.function.Function) - Java Function 接口
                Object arg0 = args[0];

                // 处理 Boolean 特殊情况
                if (arg0 == Boolean.class || "Boolean".equals(String.valueOf(arg0))) {
                    List result = new ArrayList();
                    for (Object o : ll) {
                        if (isTruthy(o)) {
                            result.add(o);
                        }
                    }
                    return new JsCommonInvokeResult(true, result);
                }

                // 处理 FsscriptFunction（必须在 java.util.function.Function 之前检查，
                // 因为 FsscriptFunction 也实现了 Function 接口）
                if (arg0 instanceof FsscriptFunction) {
                    FsscriptFunction predicate = (FsscriptFunction) arg0;
                    List result = new ArrayList();
                    for (Object o : ll) {
                        Object r = predicate.executeFunction(evaluator, o);
                        if (Boolean.TRUE.equals(r) || isTruthy(r)) {
                            result.add(o);
                        }
                    }
                    return new JsCommonInvokeResult(true, result);
                }

                // 处理 Java Function 接口
                if (arg0 instanceof java.util.function.Function) {
                    java.util.function.Function fn = (java.util.function.Function) arg0;
                    List result = new ArrayList();
                    for (Object o : ll) {
                        Object r = fn.apply(o);
                        if (Boolean.TRUE.equals(r) || isTruthy(r)) {
                            result.add(o);
                        }
                    }
                    return new JsCommonInvokeResult(true, result);
                }

                throw RX.throwB("filter参数必须是函数，实际类型: " + (arg0 == null ? "null" : arg0.getClass().getName()));
            } else if (methodName.equals("map")) {
                if (!(args[0] instanceof FsscriptFunction)) {
                    throw RX.throwB("map参数必须是函数");
                }
                FsscriptFunction accept = (FsscriptFunction) args[0];
                List result = new ArrayList(ll.size());
                for (Object o : ll) {
                    result.add(accept.executeFunction(evaluator, o));
                }
                return new JsCommonInvokeResult(true, result);
            } else if (methodName.equals("join")) {
                if (args.length != 1) {
                    throw RX.throwB("join函数只能有一个参数");
                }
                Object join = args[0];
                join = join == null ? "null" : join;
                String result = StringUtils.join(ll, join.toString());
                return new JsCommonInvokeResult(true, result);
            }
        }
        return null;
    }

    /**
     * JavaScript Number.prototype.toFixed() compatibility for FSScript numbers.
     */
    private static String numberToFixed(Number number, Object[] args) {
        int fractionDigits = checkedIntegerArgument(args, 0, 100, "toFixed");
        double value = number.doubleValue();

        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value < 0 ? "-Infinity" : "Infinity";
        }
        if (Math.abs(value) >= 1e21) {
            return toJsNumberString(value);
        }

        boolean negative = value < 0;
        BigDecimal absolute = new BigDecimal(Math.abs(value));
        String fixed = absolute.setScale(fractionDigits, RoundingMode.HALF_UP).toPlainString();
        return negative ? "-" + fixed : fixed;
    }

    private static String numberToPrecision(Number number, Object[] args) {
        boolean hasPrecision = args != null && args.length > 0;
        double precisionValue = hasPrecision ? toIntegerArgument(args) : 0;
        double value = number.doubleValue();

        if (!hasPrecision || !Double.isFinite(value)) {
            return toJsNumberString(value);
        }

        int precision = checkedIntegerArgument(precisionValue, 1, 100, "toPrecision");
        if (value == 0) {
            return precision == 1 ? "0" : "0." + "0".repeat(precision - 1);
        }

        boolean negative = value < 0;
        BigDecimal rounded = new BigDecimal(Math.abs(value))
                .round(new MathContext(precision, RoundingMode.HALF_UP));
        int exponent = decimalExponent(rounded);
        String sign = negative ? "-" : "";

        if (exponent < -6 || exponent >= precision) {
            BigDecimal significand = rounded.movePointLeft(exponent)
                    .setScale(precision - 1, RoundingMode.UNNECESSARY);
            return sign + significand.toPlainString() + exponentSuffix(exponent);
        }

        int scale = precision - 1 - exponent;
        return sign + rounded.setScale(scale, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String numberToExponential(Number number, Object[] args) {
        boolean hasFractionDigits = args != null && args.length > 0;
        double fractionDigitsValue = hasFractionDigits ? toIntegerArgument(args) : 0;
        double value = number.doubleValue();

        if (!Double.isFinite(value)) {
            return toJsNumberString(value);
        }
        if (!hasFractionDigits) {
            return toJsExponentialString(value);
        }

        int fractionDigits = checkedIntegerArgument(fractionDigitsValue, 0, 100, "toExponential");
        boolean negative = value < 0;
        String sign = negative ? "-" : "";
        if (value == 0) {
            String significand = fractionDigits == 0
                    ? "0"
                    : "0." + "0".repeat(fractionDigits);
            return sign + significand + "e+0";
        }

        BigDecimal rounded = new BigDecimal(Math.abs(value))
                .round(new MathContext(fractionDigits + 1, RoundingMode.HALF_UP));
        int exponent = decimalExponent(rounded);
        BigDecimal significand = rounded.movePointLeft(exponent)
                .setScale(fractionDigits, RoundingMode.UNNECESSARY);
        return sign + significand.toPlainString() + exponentSuffix(exponent);
    }

    private static double toIntegerArgument(Object[] args) {
        Object arg = args[0];
        double value;
        if (arg == null) {
            value = 0;
        } else if (arg instanceof Number) {
            value = ((Number) arg).doubleValue();
        } else if (arg instanceof Boolean) {
            value = (Boolean) arg ? 1 : 0;
        } else if (arg instanceof String) {
            String text = ((String) arg).trim();
            if (text.isEmpty()) {
                value = 0;
            } else {
                try {
                    value = Double.parseDouble(text);
                } catch (NumberFormatException ignored) {
                    value = Double.NaN;
                }
            }
        } else {
            throw RX.throwB("数字格式方法的位数参数必须是数值");
        }

        if (Double.isNaN(value)) {
            return 0;
        }

        // ECMAScript converts fractionDigits to an integer by truncating toward zero.
        return value < 0 ? Math.ceil(value) : Math.floor(value);
    }

    private static int checkedIntegerArgument(Object[] args, int minimum, int maximum, String methodName) {
        double value = args == null || args.length == 0 ? 0 : toIntegerArgument(args);
        return checkedIntegerArgument(value, minimum, maximum, methodName);
    }

    private static int checkedIntegerArgument(double value, int minimum, int maximum, String methodName) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw RX.throwB(methodName + "的位数参数必须在" + minimum + "到" + maximum + "之间");
        }
        return (int) value;
    }

    private static String toJsNumberString(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value < 0 ? "-Infinity" : "Infinity";
        }
        if (value == 0) {
            return "0";
        }

        boolean negative = value < 0;
        BigDecimal shortest = new BigDecimal(Double.toString(Math.abs(value))).stripTrailingZeros();
        int exponent = decimalExponent(shortest);
        String result;
        if (exponent < -6 || exponent >= 21) {
            String significand = shortest.movePointLeft(exponent).stripTrailingZeros().toPlainString();
            result = significand + exponentSuffix(exponent);
        } else {
            result = shortest.toPlainString();
        }
        return negative ? "-" + result : result;
    }

    private static String toJsExponentialString(double value) {
        if (value == 0) {
            return "0e+0";
        }

        boolean negative = value < 0;
        String shortest = toJsNumberString(Math.abs(value));
        int exponentMarker = shortest.indexOf('e');
        if (exponentMarker >= 0) {
            return (negative ? "-" : "") + shortest;
        }

        BigDecimal decimal = new BigDecimal(shortest).stripTrailingZeros();
        int exponent = decimalExponent(decimal);
        String significand = decimal.movePointLeft(exponent).stripTrailingZeros().toPlainString();
        return (negative ? "-" : "") + significand + exponentSuffix(exponent);
    }

    private static int decimalExponent(BigDecimal value) {
        return value.precision() - value.scale() - 1;
    }

    private static String exponentSuffix(int exponent) {
        return "e" + (exponent >= 0 ? "+" : "") + exponent;
    }

    /**
     * 判断值是否为"真值"（JavaScript truthy 语义）
     * <p>在 JavaScript 中，以下值被视为假值（falsy）：
     * <ul>
     *   <li>null</li>
     *   <li>undefined（在 Java 中对应 null）</li>
     *   <li>false</li>
     *   <li>0</li>
     *   <li>空字符串 ""</li>
     *   <li>NaN（在 Java 中为 Double.NaN）</li>
     * </ul>
     * 其他所有值都是真值（truthy）。
     */
    public static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            return d != 0 && !Double.isNaN(d);
        }
        if (value instanceof String) {
            return !((String) value).isEmpty();
        }
        // 其他对象类型都视为 truthy
        return true;
    }

    public static JsCommonInvokeResult invoke1(Object proxyObject, String methodName, Object[] args) {

        Assert.notNull(proxyObject, "getProxyObject不能返回空,methodName: " + methodName);
        Method method = MethodFinder.findMethod(proxyObject.getClass(), methodName, args);

        try {
            return new JsCommonInvokeResult(true, method.invoke(proxyObject, args));
        } catch (IllegalAccessException e) {
            log.error(e.getMessage());
            throw RX.throwB(e);
        } catch (InvocationTargetException e) {
            log.error(e.getMessage());
            throw RX.throwB(e.getTargetException());
        }
    }

    @Override
    public Object getProperty(String name) {
        Object proxyObject = getProxyObject();
        Assert.notNull(proxyObject, "getProxyObject不能返回空,getProperty name: " + name);

        BeanInfoHelper beanInfoHelper = BeanInfoHelper.getClassHelper(proxyObject.getClass());

        return beanInfoHelper.getBeanProperty(name, true).getBeanValue(proxyObject);
    }
}
