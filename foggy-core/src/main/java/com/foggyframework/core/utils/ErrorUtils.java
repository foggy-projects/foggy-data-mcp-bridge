/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.utils;

import com.foggyframework.core.ex.ExRuntimeExceptionImpl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.sql.ResultSetMetaData;

/**
 * <pre>
 * 		fui用户可能接触到异常的地方有
 *
 * 		>>Component Build 的时候
 *
 * 		>>通信的时候
 *
 * 		>>tpl与fcomponent的两个入口处
 *
 * </pre>
 *
 * @author Foggy
 */
public final class ErrorUtils {

    public static String getMsg(Throwable e) {
        if (e instanceof NullPointerException) {
            return "NULL";
        }

        String msg = e.getMessage();
        Throwable ex = e;
        int times = 10;

        while (times > 0) {
            if (ex instanceof InvocationTargetException) {
                ex = ((InvocationTargetException) ex).getTargetException();
                times--;
            } else if (ex instanceof UndeclaredThrowableException) {
                ex = ((UndeclaredThrowableException) ex).getUndeclaredThrowable();
                times--;
            } else {
                break;
            }
        }

        if (ex != null) {
            msg = ex.getMessage();
        }
        return msg;

    }

    public static RuntimeException columNotFound(ResultSetMetaData meta, String columName) {

        return new RuntimeException("colum name : [" + columName + "] not found int meta : " + meta);
    }

    // Moved to foggy-framework-core-xml module
    // public static RuntimeException elementProcessorError(String msg, Node ele) {
    //     return new RuntimeException(msg + "\t" + XmlUtils.toString(ele.getParentNode()));
    // }

//	public static void main(String[] args) {
//		System.getProperty("fui-debug234234");
//		System.out.println("were");
//
//	}
    // public static RuntimeException castError(String name , Class<?> cls){
    //
    // }

    public static String getMessage(Throwable t) {
        Throwable cause = t;
        StringBuilder sb = new StringBuilder();
        while (cause != null) {
            sb.append(cause.getMessage()).append("\t");
            cause = cause.getCause();
        }
        return sb.toString();
    }

    public static RuntimeException loadNoMatchError(Object obj) {
        return new RuntimeException("unknow Object " + obj);
    }

    public static RuntimeException toRuntimeException(InvocationTargetException t) {
        if (t.getCause() instanceof RuntimeException) {
            return (RuntimeException) t.getCause();
        }
        return new RuntimeException(t.getCause());
    }

    // public static final RuntimeException toRuntimeException(String
    // msg,InvocationTargetException t) {
    // // if (t.getCause() instanceof RuntimeException) {
    // // return (RuntimeException) t.getCause();
    // // }
    // return new RuntimeException(msg , t.getCause());
    // }

    public static RuntimeException toRuntimeException(String msg, Throwable t) {
        // if (t instanceof RuntimeException) {
        // return (RuntimeException) t;
        // }
        if (t instanceof InvocationTargetException) {
            return toRuntimeException((InvocationTargetException) t);
        }
        return new RuntimeException(msg + "\n" + t.getMessage(), t);
    }

    public static RuntimeException toRuntimeException(Throwable t) {
        if (t instanceof RuntimeException) {
            return (RuntimeException) t;
        }
        if (t instanceof InvocationTargetException) {
            return toRuntimeException((InvocationTargetException) t);
        }
        return new RuntimeException(t);
    }

    public static void throwIfExRuntimeExceptionImpl(Throwable e) {
        if (e instanceof ExRuntimeExceptionImpl) {
            throw (ExRuntimeExceptionImpl) e;
        }
        if (e instanceof InvocationTargetException) {
            throwIfExRuntimeExceptionImpl1((InvocationTargetException) e);
        }
    }

    public static void throwIfExRuntimeExceptionImpl1(InvocationTargetException e) {

        throwIfExRuntimeExceptionImpl(e.getCause());
    }

    public static String toString(Throwable t) {
        if (t == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        try {
            sw.close();
            pw.close();
        } catch (IOException e) {
            throw ErrorUtils.toRuntimeException(e);
        }

        return sw.toString();
    }


//    public static Throwable stateError(BaseDO obj) {
//        throw new RuntimeException(String.format("对象【%s】的状态不正确【%s】", obj.getId(), obj.getState()));
//    }
//    public static RuntimeException stateErrorV2(BaseDO obj) {
//        return R.throwA(String.format("对象【%s】的状态不正确【%s】", obj.getId(), obj.getState()));
//    }
}
