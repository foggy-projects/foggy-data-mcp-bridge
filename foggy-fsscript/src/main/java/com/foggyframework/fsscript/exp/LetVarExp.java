package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import lombok.Getter;

/**
 * let 声明表达式
 * 继承自 VarExp，用于在 for 循环中实现块级作用域
 *
 * 在 JavaScript 中，for (let i = 0; ...) 每次迭代会创建独立的 i 绑定
 * 而 for (var i = 0; ...) 所有迭代共享同一个 i
 */
@Getter
public class LetVarExp extends VarExp {

    private static final long serialVersionUID = 1L;

    public LetVarExp(String value, Exp exp) {
        super(value, exp);
    }

    @Override
    public String toString() {
        return "[let " + getValue() + " = " + getExp() + " ]";
    }
}
