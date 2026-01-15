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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解构赋值模式表达式
 * 用于支持 JavaScript 风格的解构语法：
 * <pre>
 * const { name, caption = 'default' } = options;
 * let { a, b = 10 } = obj;
 * var { x, y } = point;
 * </pre>
 *
 * @author Foggy
 * @since foggy-8.1.1
 */
public class DestructurePatternExp implements Exp {

    /**
     * 解构项列表
     */
    private final List<DestructureItemExp> items;

    /**
     * 源表达式（= 右边的表达式）
     */
    private final Exp sourceExp;

    /**
     * 变量声明类型：var, let, const, 或 null（普通赋值）
     */
    private final String declarationType;

    public DestructurePatternExp(List<DestructureItemExp> items, Exp sourceExp, String declarationType) {
        this.items = items != null ? items : new ArrayList<>();
        this.sourceExp = sourceExp;
        this.declarationType = declarationType;
    }

    public List<DestructureItemExp> getItems() {
        return items;
    }

    public Exp getSourceExp() {
        return sourceExp;
    }

    public String getDeclarationType() {
        return declarationType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object evalValue(ExpEvaluator evaluator) {
        // 1. 求值源表达式，获取源对象
        Object source = sourceExp.evalValue(evaluator);

        if (source == null) {
            // 源对象为 null，所有解构项使用默认值或 null
            for (DestructureItemExp item : items) {
                Object value = null;
                if (item.hasDefaultValue()) {
                    value = item.getDefaultValue().evalValue(evaluator);
                }
                evaluator.setVar(item.getTargetName(), value);
            }
            return null;
        }

        // 2. 从源对象中提取值
        if (source instanceof Map) {
            Map<String, Object> sourceMap = (Map<String, Object>) source;
            for (DestructureItemExp item : items) {
                Object value = sourceMap.get(item.getSourceName());

                // 如果值为 null 且有默认值，则使用默认值
                if (value == null && item.hasDefaultValue()) {
                    value = item.getDefaultValue().evalValue(evaluator);
                }

                // 设置变量
                evaluator.setVar(item.getTargetName(), value);
            }
        } else {
            // 尝试通过反射或属性访问获取值
            for (DestructureItemExp item : items) {
                Object value = getPropertyValue(source, item.getSourceName(), evaluator);

                // 如果值为 null 且有默认值，则使用默认值
                if (value == null && item.hasDefaultValue()) {
                    value = item.getDefaultValue().evalValue(evaluator);
                }

                // 设置变量
                evaluator.setVar(item.getTargetName(), value);
            }
        }

        return source;
    }

    /**
     * 从对象中获取属性值
     */
    private Object getPropertyValue(Object obj, String propertyName, ExpEvaluator evaluator) {
        if (obj == null) {
            return null;
        }

        // 如果是 Map，直接获取
        if (obj instanceof Map) {
            return ((Map<?, ?>) obj).get(propertyName);
        }

        // 尝试通过 PropertyExp 获取
        try {
            PropertyExp propertyExp = new PropertyExp(new ObjectExp<>(obj), propertyName);
            return propertyExp.evalValue(evaluator);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return Object.class;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (declarationType != null) {
            sb.append(declarationType).append(" ");
        }
        sb.append("{ ");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(items.get(i));
        }
        sb.append(" } = ").append(sourceExp);
        return sb.toString();
    }
}
