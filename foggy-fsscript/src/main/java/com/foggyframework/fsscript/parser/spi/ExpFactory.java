/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.parser.spi;


import com.foggyframework.fsscript.exp.*;

import java.util.Arrays;
import java.util.List;

/**
 * @author Foggy
 * @since foggy-1.0
 */
public interface ExpFactory {


    void clear();

    Exp createArgmentsExp(String name);

    @SuppressWarnings("rawtypes")
    Exp createArray(ListExp is);

    Exp createBooleanFalse();

    Exp createBooleanTrue();

    /**
     * 创建一个从上下文取值的表达式，这里的上下文，一般指spring或其他IOC容器
     *
     * @param i
     * @return
     */
    Exp createContextObjectExp(String i);

    /**
     * @param value
     * @return
     */
    Exp createDollar(Exp value);

    /**
     * 用于创建ddd.xx或ddd.xx()此类表达式
     *
     * @param l
     * @param r
     * @return
     */
    Exp createEl(Exp l, Exp r);


    Exp createEl(Exp l, Exp r, boolean checkLeft);


    Exp createEmpty();

    Exp createNull();

    Exp createEqDef(String id, Exp e);

    Exp createEqDef(Exp id, Exp e);

    Exp createFor(Exp defExp, Exp booleanExp, Exp addExp, Exp forBodyExp);

    Exp createFor(String leftId, Exp rightExp, Exp forBodyExp);

    /**
     * 创建匿名函数
     *
     * @param
     * @param argDefs
     * @return
     */
    Exp createFunctionDef(FsscriptClosureDefinition fcDefinition, final Exp body, List<Exp> argDefs);

    default Exp createFunctionDef1(FsscriptClosureDefinition fcDefinition, final Exp body, Exp argDefs) {
        return createFunctionDef(fcDefinition, body, Arrays.asList(argDefs));
    }

    Exp createFunctionDef(FsscriptClosureDefinition fcDefinition, String name, final Exp body, List<Exp> argDefs);

    Exp createGetCollectionValueExp(Exp value, Exp sub);

    Exp createGetExpEvaluatorExp();

    Exp createGetRequestExp();

    Exp createImport(String file);

    Exp createImport(Object v, String file);

    @Deprecated
    Exp createImport(String file, boolean usingCache);

    Exp createGetThisExp();

    Exp createId(final String str);

    @SuppressWarnings("rawtypes")
    Exp createMap(List is);

    /**
     * 呃，开始支持“;”符号对表达式进行分割。。。
     *
     * @param l
     * @return
     */
    Exp createNCountExp(List<Exp> l);

    Exp createNewExp(String i);

    Exp createNewExp(String i, ListExp v);

    Exp createNumber(final Number n);

    Exp createLong(final Long n);

    Exp createRequestAttributeExp(String i);

    Exp createRequestParameterExp(String name);

    Exp createReturnDef(Exp e);

    Exp createDelete(Exp e);

    Exp createString(final String str);

    Exp createThrowDef(Exp e);

    /**
     * 创建try,catch,finally块
     *
     * @param tryExp
     * @param finalyExp
     * @param catchExp
     * @param catchArgName
     * @return
     */
    Exp createTryDef(Exp tryExp, Exp finalyExp, Exp catchExp, String catchArgName);

    UnresolvedFunCall createUnresolvedFunCall(Exp name, ListExp args, boolean fix);


    Exp createAtUnresolvedFunCall(String name, ListExp args, boolean fix);


    Exp createVarDef(String id, Exp e);

    Exp createVarDef(Exp id, Exp e);

    /**
     * 创建 let 变量定义
     * @param id 变量名
     * @param e 初始化表达式
     * @return LetVarExp
     */
    Exp createLetDef(String id, Exp e);

    /**
     * 创建 let 变量定义
     * @param id 变量标识符表达式
     * @param e 初始化表达式
     * @return LetVarExp
     */
    Exp createLetDef(Exp id, Exp e);

    UnresolvedFunCall createUnresolvedFunCall(String name, ListExp args, boolean fix);

    FunctionSet getFunctionSet();

    default Exp createBreak() {
        return BreakExp.BREAK;

    }

    default Exp createBreak(String i) {
        throw new UnsupportedOperationException();
    }

    default Exp createContinue(){
        return ContinueExp.CONTINUE;
    }

    default Exp createContinue(String i) {
        throw new UnsupportedOperationException();
    }


    @Deprecated
    Exp createNOExp(Exp l, String r);

    Exp createExportDef(Exp e);

    Exp createExportDefaultDef(Exp e);

    Exp createAtImport(List v, String i);

    Exp createExpString(FsscriptClosureDefinition fcDefinition, String s);

    Exp createAsExp(String i, String i2);

    MapEntry createDDDotMapEl(Exp r);

    Exp createSwitchDefault(List xx);

    Exp createSwitchCase(Exp x, List x1);

    Exp createSwitch(Exp e, List list);

    ListExp createListExp();

    Exp createDDDotListEl(Exp r);

    /**
     * 创建表达式函数调用，支持如 closures[0]()、obj.getHandler()() 等形式
     * @param funExp 函数表达式
     * @param args 参数列表
     * @return ExpFunCall
     */
    Exp createExpFunCall(Exp funExp, ListExp args);
}
