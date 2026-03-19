package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * for...in 循环表达式，符合 JavaScript 标准：
 * - 数组：遍历索引（0, 1, 2...）
 * - Map/对象：遍历键名
 *
 * 注意：与 for...of 和 for...: 不同，for...in 返回的是索引/键名，不是值。
 *
 * JavaScript 示例：
 * for (let i in [10, 20, 30]) {
 *   console.log(i);  // 输出: "0", "1", "2"（索引，字符串）
 * }
 */
public class ForInExp implements Exp, Serializable {

    private static final long serialVersionUID = 1L;

    final String leftId;
    final Exp rightExp;
    final Exp forBodyExp;

    public ForInExp(String leftId, Exp rightExp, Exp forBodyExp) {
        this.leftId = leftId;
        this.rightExp = rightExp;
        this.forBodyExp = forBodyExp;
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        Object obj = rightExp.evalValue(ee);
        if (obj == null) {
            return null;
        }

        Object tmp = ee.getVar(leftId);
        try {
            if (obj instanceof Object[]) {
                // 数组：遍历索引
                Object[] arr = (Object[]) obj;
                for (int i = 0; i < arr.length; i++) {
                    ee.setVar(leftId, i);
                    forBodyExp.evalValue(ee);
                }
            } else if (obj instanceof List) {
                // List：遍历索引
                List<?> list = (List<?>) obj;
                for (int i = 0; i < list.size(); i++) {
                    ee.setVar(leftId, i);
                    forBodyExp.evalValue(ee);
                }
            } else if (obj instanceof Map) {
                // Map：遍历键名
                Map<?, ?> map = (Map<?, ?>) obj;
                for (Object key : map.keySet()) {
                    ee.setVar(leftId, key);
                    forBodyExp.evalValue(ee);
                }
            } else if (obj instanceof Iterable) {
                // 其他 Iterable：遍历索引
                Iterable<?> iterable = (Iterable<?>) obj;
                int i = 0;
                for (Iterator<?> it = iterable.iterator(); it.hasNext(); i++) {
                    it.next();
                    ee.setVar(leftId, i);
                    forBodyExp.evalValue(ee);
                }
            }
        } finally {
            ee.setVar(leftId, tmp);
        }
        return null;
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return Object.class;
    }
}