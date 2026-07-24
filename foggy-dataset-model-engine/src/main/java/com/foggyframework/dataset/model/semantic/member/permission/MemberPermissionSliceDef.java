package com.foggyframework.dataset.model.semantic.member.permission;

import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.Data;

/**
 * forcedSlice 单项定义。
 * <p>支持静态 value 或动态 valueBuilder(context)。
 */
@Data
public class MemberPermissionSliceDef {

    /** 字段名（synthetic member-QM 字段空间内） */
    private String field;

    /** 操作符：=、in、!=、等 */
    private String op;

    /** 静态值（与 valueBuilder 二选一，valueBuilder 优先） */
    private Object value;

    /** 动态值构建函数 valueBuilder(context)。FSScript 函数引用，运行时求值 */
    private FsscriptFunction valueBuilder;

    /**
     * 运行时求值：如果有 valueBuilder 则执行求值，否则返回静态 value。
     */
    public Object resolveValue(Object context) {
        if (valueBuilder != null) {
            return valueBuilder.apply(new Object[]{context});
        }
        return value;
    }
}
