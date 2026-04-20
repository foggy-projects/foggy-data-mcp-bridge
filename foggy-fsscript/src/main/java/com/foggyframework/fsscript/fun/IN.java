package com.foggyframework.fsscript.fun;

import com.foggyframework.fsscript.exp.EmptyExp;
import com.foggyframework.fsscript.exp.IdExp;
import com.foggyframework.fsscript.exp.UnresolvedFunCall;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * `IN` 算子同时承担两种语义：
 *
 * 1. 历史的元组迭代形态 {@code (item, index) in collection}：
 *    由 grammar 中 {@code LPAREN arg_list RPAREN -> UnresolvedFunCall("()")} 生成左值，
 *    返回 {@link InResult} 供外层调用方 {@link InResult#forEach} 迭代。
 *
 * 2. SQL 风格成员测试 {@code value in (v1, v2, ...)} / {@code value in [v1, v2]} /
 *    {@code value in someList}：
 *    返回 boolean。
 *
 * 两种形态按左值是否为 (id, id) 二元组区分。
 */
public class IN implements FunDef {

    @Override
    public Object execute(ExpEvaluator ee, Exp[] args) {
        Assert.isTrue(args.length == 2, "in 表达式的参数必须是 2 个");

        if (isTupleOfIds(args[0])) {
            UnresolvedFunCall tuple = (UnresolvedFunCall) args[0];
            List<Exp> ll = tuple.getArgs();
            String itemName = ((IdExp) ll.get(0)).value;
            String indexName = ((IdExp) ll.get(1)).value;
            Object obj = args[1].evalResult(ee);
            return new InResult(itemName, indexName, obj);
        }

        return containsMember(ee, args[0], args[1]);
    }

    /**
     * 成员测试。package-private，供 {@link NOT_IN} 复用。
     *
     * <p>等值语义与 fsscript 的 {@code ==} 算子一致：直接走 {@link Equal#eq} ——
     * null / BigDecimal × 任意类型跨类型兼容，其他类型按 {@code Object.equals}。
     * 例如 {@code 1 in (1L)} / {@code 1 in (1.0)} 与 {@code 1 == 1L} / {@code 1 == 1.0}
     * 行为一致（均为 false，Java 装箱语义），需要跨类型匹配请显式转换或使用 BigDecimal。
     */
    static boolean containsMember(ExpEvaluator ee, Exp left, Exp right) {
        Object value = left.evalResult(ee);
        Iterable<?> haystack = resolveHaystack(ee, right);
        if (haystack == null) {
            return false;
        }
        for (Object item : haystack) {
            if (Equal.eq(value, item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTupleOfIds(Exp e) {
        if (!(e instanceof UnresolvedFunCall)) {
            return false;
        }
        UnresolvedFunCall u = (UnresolvedFunCall) e;
        if (!"()".equals(u.value)) {
            return false;
        }
        List<Exp> items = u.getArgs();
        if (items == null || items.size() != 2) {
            return false;
        }
        return items.get(0) instanceof IdExp && items.get(1) instanceof IdExp;
    }

    /**
     * 把右侧 Exp 解析成可遍历集合。
     *
     * - {@code (a, b, c)} 形式的 UnresolvedFunCall("()") 直接展开子元素（绕开 Brackets
     *   只返回第一个元素的语义）。
     * - 其他情况先 evalResult 再按 Collection / array / Iterable / Map / 标量归一化。
     * - null 返回 null，调用方据此直接返回 false。
     */
    private static Iterable<?> resolveHaystack(ExpEvaluator ee, Exp right) {
        if (right instanceof UnresolvedFunCall) {
            UnresolvedFunCall u = (UnresolvedFunCall) right;
            if ("()".equals(u.value)) {
                List<Exp> items = u.getArgs();
                List<Object> list = new ArrayList<>(items.size());
                for (Exp e : items) {
                    // 空括号 `()` 的 arg_list 会生成一个 EmptyExp 占位项，此时代表空集合。
                    if (e instanceof EmptyExp) {
                        continue;
                    }
                    list.add(e.evalResult(ee));
                }
                return list;
            }
        }
        Object v = right.evalResult(ee);
        return toIterable(v);
    }

    private static Iterable<?> toIterable(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Iterable) {
            return (Iterable<?>) v;
        }
        if (v instanceof Map) {
            return ((Map<?, ?>) v).keySet();
        }
        if (v instanceof Object[]) {
            return Arrays.asList((Object[]) v);
        }
        if (v.getClass().isArray()) {
            int len = Array.getLength(v);
            List<Object> list = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                list.add(Array.get(v, i));
            }
            return list;
        }
        // 标量按单元素集合处理：保持宽松语义，如 "a" in "a" → true。
        return Collections.singletonList(v);
    }

    @Override
    public String getName() {
        return "in";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InResult {
        String itemName;
        String indexName;
        Object inValue;

        public void forEach(ExpEvaluator ee, BiConsumer<Object, Integer> consumer) {
            Iterator iterator;
            if (inValue instanceof Collection) {
                iterator = ((Collection<?>) inValue).iterator();
            } else if (inValue instanceof Iterator) {
                iterator = (Iterator) inValue;
            } else if (inValue instanceof Integer) {
                int length = (int) inValue;
                for (int i = 0; i < length; i++) {
                    ee.setVar(indexName, i);
                    ee.setVar(itemName, i);
                    consumer.accept(i, i);
                }
                return;
            } else if (inValue == null) {
                return;
            } else {
                throw new UnsupportedOperationException("不支持的inValue:" + inValue);
            }
            int idx = 0;
            while (iterator.hasNext()) {
                Object v = iterator.next();
                ee.setVar(indexName, idx);
                ee.setVar(itemName, v);
                consumer.accept(v, idx);
                idx++;
            }
        }
    }
}
