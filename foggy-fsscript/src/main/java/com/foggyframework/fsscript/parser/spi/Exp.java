/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser.spi;


import com.foggyframework.fsscript.exp.EmptyExp;
import com.foggyframework.fsscript.exp.NullExp;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * @author Foggy
 * @since foggy-1.0
 */
@SuppressWarnings({"rawtypes"})
public interface Exp {

    Object NOT_MATCH = new Object() {

        @Override
        public String toString() {
            return "";
        }

    };

    class ReturnExpObject {
        public final Object value;

        public ReturnExpObject(Object v) {
            super();
            this.value = v;
        }

        @Override
        public String toString() {
            return value == null ? "null" : value.toString();
        }

    }

    default Object unWarpResult(Object obj) {
        if (obj == EmptyExp.EMPTY || obj == NOT_MATCH || obj == NullExp.NULL) {
            return null;
        }
        if (obj instanceof ReturnExpObject) {
            return ((ReturnExpObject) obj).value;
        }
        return obj;
    }

    /**
     * @param ee
     * @return
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    default Object evalResult(ExpEvaluator ee) {

        return unWarpResult(evalValue(ee));
    }

    /**
     * evalValue异常的分类 , <br/>
     * 一种是函数参数的错误,例如某些函数要求2个参数,但实际只传入一个<br/>
     * 一种属于函数业务逻辑上的异常<br/>
     * 可能还有权限方面的异常,但这个可暂时归类至逻辑异常<br/>
     * <p>
     * 业务异常我们使用RuntimeException
     *
     * @param ee
     * @return
     */
    Object evalValue(ExpEvaluator ee);

    default Object apply2List(List ll, ExpEvaluator ee) {
        Object v = evalValue(ee);
        ll.add(v);

        return v;
    }
    default Object apply2List2(List ll, ExpEvaluator ee) {
        Object v = evalResult(ee);
        ll.add(v);

        return v;
    }

    Class getReturnType(ExpEvaluator ee);

}
