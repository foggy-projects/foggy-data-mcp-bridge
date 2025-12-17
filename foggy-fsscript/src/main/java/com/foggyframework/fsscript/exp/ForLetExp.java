package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.fun.Iif;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.io.Serializable;

/**
 * 支持 let 块级作用域的 for 循环表达式
 *
 * 根据 JavaScript 规范，for (let i = 0; ...) 中的 let 变量
 * 在每次迭代时都会创建一个新的绑定，使得闭包能够捕获每次迭代的值。
 *
 * 示例：
 * for (let i = 0; i < 3; i++) {
 *     setTimeout(() => console.log(i), 0);
 * }
 * // 输出: 0, 1, 2 （而不是 3, 3, 3）
 */
public class ForLetExp implements Exp, Serializable {

    private static final long serialVersionUID = 1L;

    final Exp defExp;
    final Exp booleanExp;
    final Exp addExp;
    final Exp forBodyExp;

    /**
     * let 声明的变量名，用于每次迭代复制
     */
    final String letVarName;

    public ForLetExp(Exp defExp, Exp booleanExp, Exp addExp, Exp forBodyExp) {
        this.defExp = defExp;
        this.booleanExp = booleanExp;
        this.addExp = addExp;
        this.forBodyExp = forBodyExp;

        // 提取 let 变量名
        if (defExp instanceof LetVarExp) {
            this.letVarName = ((LetVarExp) defExp).getValue();
        } else {
            this.letVarName = null;
        }
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        try {
            // 外层作用域 - 用于初始化和增量操作
            ee.pushNewFoggyClosure();

            if (defExp != null) {
                defExp.evalValue(ee);
            }

            Object obj;
            for (; Iif.check(booleanExp.evalValue(ee)); ) {

                // ★ 核心改动：为每次迭代创建独立的作用域
                if (letVarName != null) {
                    Object currentValue = ee.getVar(letVarName);
                    ee.pushNewFoggyClosure();  // 迭代作用域
                    ee.setVar(letVarName, currentValue);  // 复制变量值到新作用域
                }

                try {
                    obj = forBodyExp.evalValue(ee);

                    if (obj == BreakExp.BREAK) {
                        break;
                    }
                    if (obj instanceof ReturnExpObject) {
                        return obj;
                    }
                    if (obj == ContinueExp.CONTINUE) {
                        // continue 时继续执行增量表达式
                    }
                } finally {
                    // ★ 迭代结束后，将更新的值传回外层作用域
                    if (letVarName != null) {
                        Object updatedValue = ee.getVar(letVarName);
                        ee.popFsscriptClosure();  // 弹出迭代作用域
                        ee.setVar(letVarName, updatedValue);  // 更新外层值，供 i++ 使用
                    }
                }

                // i++ 在外层作用域执行
                if (addExp != null) {
                    addExp.evalValue(ee);
                }
            }
        } finally {
            ee.popFsscriptClosure();  // 弹出外层作用域
        }

        return null;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator ee) {
        return Object.class;
    }
}
