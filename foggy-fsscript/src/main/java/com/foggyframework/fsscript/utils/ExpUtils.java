/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.utils;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.fsscript.exp.EmptyExp;
import com.foggyframework.fsscript.exp.StringExp;
import com.foggyframework.fsscript.parser.spi.*;

import java.util.Map;

public class ExpUtils {

    private final static Exp errorStringExp = new StringExp("the exp here has an Error!");

    /**
     * 混合型的 awerwer,asdferwe--wre ${ee-2} werw
     *
     * @param exp
     * @return
     * @throws CompileException
     */
    public static final Exp compile(String exp) throws CompileException {
        if (StringUtils.isEmpty(exp)) {
            return EmptyExp.EMPTY;
        }
        return getParser().compile(null, exp);
    }

    public static final Exp compileTxt(String exp) throws CompileException {
        if (StringUtils.isEmpty(exp)) {
            return EmptyExp.EMPTY;
        }
        final Exp expxx = compile(exp);
        return new Exp() {
            @Override
            public Object evalValue(ExpEvaluator ee)  {
                if (expxx == null) {
                    return "";
                }
                Object obj = expxx.evalResult(ee);
                return obj == null ? null : obj.toString();
            }

            @Override
            public Class getReturnType(ExpEvaluator ee) {
                return String.class;
            }
        };
    }

    public static final Exp compile(FsscriptClosureDefinition fcDefinition, String exp) throws CompileException {
        if (StringUtils.isEmpty(exp)) {
            return null;
        }
        return getParser().compile(fcDefinition, exp);
    }
    public static final Exp compile(FsscriptClosureDefinition fcDefinition, String exp,ExpFactory expFactory) throws CompileException {
        if (StringUtils.isEmpty(exp)) {
            return null;
        }
        return getParser(expFactory).compile(fcDefinition, exp);
    }


    /**
     * 完整的表达式，
     *
     * <pre>
     * ds.select('a');
     * </pre>
     *
     * @param exp
     * @return
     * @throws CompileException
     */
    public static final Exp compileEl(String exp) throws CompileException {
        if (StringUtils.isEmpty(exp)) {
            return null;
        }
        return getParser().compileEl(null, exp);
    }

    public static final Exp compileEl(FsscriptClosureDefinition fcDefinition, String exp) throws CompileException {
        if (StringUtils.isEmpty(exp)) {
            return null;
        }
        return getParser().compileEl(fcDefinition, exp);
    }
    public static final Exp compileEl(FsscriptClosureDefinition fcDefinition, String exp,ExpFactory expFactory) throws CompileException {
        if (StringUtils.isEmpty(exp)) {
            return null;
        }
        return getParser(expFactory).compileEl(fcDefinition, exp);
    }
//    public static final Exp compileMongoEl(String exp) throws CompileException {
//        if (StringUtils.isEmpty(exp)) {
//            return null;
//        }
//
//        ExpParser expParser = new ExpParser(ExpFactory.MONGODB);
//        return expParser.compileEl(exp);
//    }

    /**
     * <pre>
     * ${ds.select('a')}
     * </pre>
     * <p>
     * 如果表达式不是包含在${}中，则返回空
     *
     * @param exp
     * @return
     * @throws CompileException
     */
    public static final Exp compileElInDollar(FsscriptClosureDefinition fcDefinition, String exp) throws CompileException {
        if (StringUtils.isEmpty(exp)) {
            return null;
        }
        exp = exp.trim();
        if (exp.startsWith("${") && exp.endsWith("}")) {
            return compileEl(fcDefinition, exp.substring(2, exp.length() - 1));
        } else {
            return null;
        }
    }

    public static final ExpEvaluator createDefaultExpEvaluator() {
//        return new LDefaultExpEvaluator();
        throw new RuntimeException();

    }

//	public static final ExpEvaluator newExpEvaluator() {
//		return new V2ExpEvaluator();
//	}

    public static final Object eval(ExpEvaluator ee, Exp exp) {
        try {
            return exp.evalResult(ee);
        } catch (Throwable t) {
            throw new RuntimeException("eval expression : [" + exp + "] has error!", t);
        }
    }

    public static final Object safeEval(ExpEvaluator ee, Exp exp) {
        try {
            return exp.evalResult(ee);
        } catch (Throwable t) {
            throw new RuntimeException("eval expression : [" + exp + "] has error!", t);
        }
    }

    public static final Object safeEval(ExpEvaluator ee, String expstr) {
        return safeEval(ee, compileEl(expstr));

    }

    public final static Exp getErrorStringExp() {
        return errorStringExp;
    }

    public static final Parser getParser() {
        return ParserFactory.newInstance().newExpParser();
    }
    public static final Parser getParser(ExpFactory expFactory) {
        return ParserFactory.newInstance().newExpParser(expFactory);
    }
    public static void main(String[] args) {
        Exp exp = ExpUtils.compile("${1}");
        System.out.println(exp);
    }

    public static Object evalStr(String exp, Map<String, Object> args) {
        Exp e = compileEl(exp);
        ExpEvaluator ee = createDefaultExpEvaluator();

        ee.setMap2Var(args);

            return e.evalResult(ee);


    }
}
