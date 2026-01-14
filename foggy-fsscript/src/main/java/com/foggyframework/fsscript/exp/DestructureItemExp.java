/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved.
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * 解构赋值项表达式
 * 用于支持 JavaScript 风格的解构语法：
 * - { name } - 简单解构
 * - { name = 'default' } - 带默认值
 * - { name: alias } - 重命名（暂不支持）
 *
 * @author Foggy
 * @since foggy-8.1.1
 */
public class DestructureItemExp implements Exp, NamedExp {

    /**
     * 源对象中的属性名
     */
    private final String sourceName;

    /**
     * 目标变量名（如果有别名则为别名，否则与 sourceName 相同）
     */
    private final String targetName;

    /**
     * 默认值表达式（可为 null）
     */
    private final Exp defaultValue;

    /**
     * 创建简单解构项 { name }
     */
    public DestructureItemExp(String name) {
        this(name, name, null);
    }

    /**
     * 创建带默认值的解构项 { name = defaultValue }
     */
    public DestructureItemExp(String name, Exp defaultValue) {
        this(name, name, defaultValue);
    }

    /**
     * 创建完整解构项 { sourceName: targetName = defaultValue }
     */
    public DestructureItemExp(String sourceName, String targetName, Exp defaultValue) {
        this.sourceName = sourceName;
        this.targetName = targetName;
        this.defaultValue = defaultValue;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getTargetName() {
        return targetName;
    }

    public Exp getDefaultValue() {
        return defaultValue;
    }

    public boolean hasDefaultValue() {
        return defaultValue != null;
    }

    @Override
    public String getValue() {
        return targetName;
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        // 解构项本身不直接求值，由 DestructurePatternExp 统一处理
        return null;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return Object.class;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(sourceName);
        if (!sourceName.equals(targetName)) {
            sb.append(": ").append(targetName);
        }
        if (defaultValue != null) {
            sb.append(" = ").append(defaultValue);
        }
        return sb.toString();
    }
}
