/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.exp;


import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.NumberUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.fsscript.exp.switch_exp.SwitchCaseExp;
import com.foggyframework.fsscript.exp.switch_exp.SwitchDefaultExp;
import com.foggyframework.fsscript.exp.switch_exp.SwitchExp;
import com.foggyframework.fsscript.parser.spi.*;
import com.foggyframework.fsscript.utils.ExpUtils;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
public class DefaultExpFactory implements ExpFactory, ApplicationRunner, DisposableBean {
    public static DefaultExpFactory DEFAULT = new DefaultExpFactory();

    FunctionSet functionSet = new FunTable();

    //    @Value("${foggy.fsscript.force-check-left:false}")
    boolean forceCheckLeft;
    @Resource
    ApplicationContext appCtx;

    @Override
    public Exp createArgmentsExp(String name) {
        return new ArgmentExp(name);
    }

    @Override
    public Exp createArray(ListExp is) {
        fixArray(is);
        return new ArrayExp(is);
    }

    public void fixArray(List is) {
        if (!is.isEmpty()) {
            if (is.get(is.size() - 1) == EmptyExp.EMPTY) {
                //如果数组最后一位是EMPTY，移掉它！
                is.remove(is.size() - 1);
            }
        }
    }

    public void checkFunctionArgDefs(String name, List is) {
        if (is == null || is.isEmpty()) {
            return;
        }
        for (Object i : is) {
            if (i instanceof MapExp) {
                /**
                 * 格式
                 * ({d1, d2, maxTimes, msg}) => {
                 *      ...
                 * }
                 */
                continue;
            }
            if (!(i instanceof IdExp || i instanceof AsExp)) {
                throw RX.throwB("参数的定义必须是IdExp/AsExp表达式: " + is + (name == null ? "" : name));
            }
        }
    }

    @Override
    public Exp createBooleanFalse() {
        return FalseExp.FALSE_EXP;
    }

    @Override
    public Exp createBooleanTrue() {
        return TrueExp.TRUE_EXP;
    }

    public void clear() {
        functionSet.clear();
    }

    /**
     * @
     */
    @Override
    public Exp createContextObjectExp(String name) {
        return new ContextObjectExp(name);
    }

    @Override
    public Exp createDollar(Exp value) {
        return new DollarExp(value);
    }

    @Override
    public Exp createEl(Exp l, Exp r) {
        if (r instanceof NamedExp) {
            // 普通属性访问 (.)，optional = false
            return createPropertyExp(l, ((NamedExp) r).getValue(), false);
        }
        throw new UnsupportedOperationException("createEl 不支持的类型: " + r.getClass());
    }

    @Override
    public Exp createEl(Exp l, Exp r, boolean optional) {
        if (r instanceof NamedExp) {
            // optional = true 表示可选链 (?.)
            return createPropertyExp(l, ((NamedExp) r).getValue(), optional);
        }
        throw new UnsupportedOperationException("createEl 不支持的类型: " + r.getClass());
    }

    private Exp createPropertyExp(Exp e, String i, boolean optional) {
        // optional = true 表示可选链 (?.)，左值为 null 时返回 null
        // optional = false 表示普通访问 (.)，左值为 null 时抛出异常
        if (optional) {
            // 可选链语法 ?.
            if (i.equals("length")) {
                return new OptionalLengthPropertyExp(e, i);
            } else {
                return new OptionalPropertyExp(e, i);
            }
        } else {
            // 普通属性访问 .
            if (i.equals("length")) {
                return new LengthPropertyExp(e, i);
            } else {
                return new PropertyExp(e, i);
            }
        }
    }


    @Override
    public Exp createEmpty() {
        return EmptyExp.EMPTY;
    }

    @Override
    public Exp createNull() {
        return NullExp.NULL;
    }

    @Override
    public Exp createEqDef(String id, Exp e) {
        return new EqExp(id, e);
    }

    @Override
    public Exp createVarDef(String id, Exp e) {
        return new VarExp(id, e);
    }

    @Override
    public Exp createVarDef(Exp id, Exp e) {
        if (id instanceof NamedExp) {
            return createVarDef(((NamedExp) id).getValue(), e);
        } else if (id instanceof MapExp) {
            return createMapVarDef(((MapExp) id), e);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public Exp createLetDef(String id, Exp e) {
        return new LetVarExp(id, e);
    }

    @Override
    public Exp createLetDef(Exp id, Exp e) {
        if (id instanceof NamedExp) {
            return createLetDef(((NamedExp) id).getValue(), e);
        } else if (id instanceof MapExp) {
            // let 解构赋值暂时使用 MapVarDefExp，后续可扩展
            return createMapVarDef(((MapExp) id), e);
        }
        throw new UnsupportedOperationException();
    }

    private Exp createMapVarDef(MapExp mapExp, Exp e) {
        return new MapVarDefExp(mapExp, e);
    }

    @Override
    public Exp createEqDef(Exp id, Exp e) {
        if (id instanceof NamedExp) {
            return createEqDef(((NamedExp) id).getValue(), e);
        } else if (id instanceof PropertyExp) {
            return new PropertySetExp((PropertyExp) id, e);
        } else if (id instanceof SubExp) {
            return new SubSetExp((SubExp) id, e);
        }
        return null;
    }


    @Override
    public Exp createFor(final Exp defExp, final Exp booleanExp, final Exp addExp, final Exp forBodyExp) {
        // 如果是 let 声明，使用 ForLetExp 实现块级作用域
        if (defExp instanceof LetVarExp) {
            return new ForLetExp(defExp, booleanExp, addExp, forBodyExp);
        }
        return new ForExp(defExp, booleanExp, addExp, forBodyExp);
    }

    @Override
    public Exp createFor(String leftId, Exp rightExp, Exp forBodyExp) {
        return new ForExp2(leftId, rightExp, forBodyExp);
    }

    @Override
    public Exp createFunctionDef(FsscriptClosureDefinition fcDefinition, Exp body, List<Exp> argDefs) {
        if (argDefs == null) {
            argDefs = Collections.EMPTY_LIST;
        } else {
            fixArray(argDefs);
            checkFunctionArgDefs(null, argDefs);
        }
        return new FunctionDefExp(fcDefinition, body, argDefs);
    }

    @Override
    public Exp createFunctionDef(FsscriptClosureDefinition fcDefinition, String name, Exp body, List<Exp> argDefs) {
        if (argDefs == null) {
            argDefs = Collections.EMPTY_LIST;
        } else {
            fixArray(argDefs);
            checkFunctionArgDefs(name, argDefs);
        }

        return new FunctionDefExp(fcDefinition, name, body, argDefs);
    }

    @Override
    public Exp createGetCollectionValueExp(Exp value, Exp sub) {
        return new SubExp(value, sub);
    }

    @Override
    public Exp createGetExpEvaluatorExp() {
        return EE_EXP.EE_EXP;
    }

    @Override
    public Exp createGetRequestExp() {
        return REQUEST_EXP.REQUEST_EXP;
    }

    @Override
    public Exp createGetThisExp() {
        return THIS_EXP.THIS_EXP;
    }

    @Override
    public Exp createId(String str) {
        // 检查是否为内置全局对象
        com.foggyframework.fsscript.builtin.BuiltinGlobalExp builtinExp =
                com.foggyframework.fsscript.builtin.BuiltinGlobalExp.get(str);
        if (builtinExp != null) {
            return builtinExp;
        }
        return new IdExp(str);
    }

    @Override
    public Exp createMap(List is) {
        return new MapExp(new ArrayList(is));
    }

    @Override
    public Exp createNCountExp(List<Exp> l) {
        if (l.size() == 1) {
            return l.get(0);
        }
        return new NCountExp(l);
    }

    @Override
    public Exp createNewExp(String i) {
//		return new NewExp(i);
        throw new UnsupportedOperationException();
    }

    @Override
    public Exp createNewExp(String i, ListExp v) {
        fixArray(v);
        return new NewExpImpl(i, v);
//        throw new UnsupportedOperationException();
    }

//	@Override
//	public Exp createNOExp(Exp l, String r) {
//		return new SubBeanClosureObjectExp(l, r);
//	}

    @Override
    public Exp createNumber(Number n) {
        return new NumberExp(n);
    }

    @Override
    public Exp createLong(Long n) {
        return new ObjectExp<Long>(n);
    }


    @Override
    public Exp createRequestAttributeExp(String name) {
        if (NumberUtils.isNumber(name)) {
            return new JavaMethodParamIdxValueExp(Integer.parseInt(name));
        }
        return new JavaMethodParamNameValueExp(name);
    }

    @Override
    public Exp createRequestParameterExp(String name) {
        return new RequestParameterExp(name);
    }

    @Override
    public Exp createReturnDef(Exp e) {
        return new ReturnExp(e);
    }

    @Override
    public Exp createDelete(Exp e) {
        return new DeleteExp(e);
    }

    @Override
    public Exp createString(String str) {
        return new StringExp(str);
    }

    @Override
    public Exp createThrowDef(Exp e) {
        return new ThrowExp(e);
    }

    @Override
    public Exp createTryDef(Exp tryExp, Exp finalyExp, Exp catchExp, String catchArgName) {
        return new TryCatch(tryExp, finalyExp, catchExp, catchArgName);
    }



    @Override
    public UnresolvedFunCall createUnresolvedFunCall(Exp name, ListExp args, boolean fix) {
        if (fix) {
            fixArray(args);
        }
        return new UnresolvedFunCall(this, name, args);
    }



    @Override
    public Exp createAtUnresolvedFunCall(String name, ListExp args, boolean fix) {
        if (fix) {
            fixArray(args);
        }
        return new AtUnresolvedFunCall(this, name, args);
    }



    @Override
    public UnresolvedFunCall createUnresolvedFunCall(String name, ListExp args, boolean fix) {
        if (fix) {
            fixArray(args);
        }
        return new UnresolvedFunCall(this, name, args);
    }

    @Override
    public FunctionSet getFunctionSet() {
        return functionSet;
    }

    public void setFunctionSet(FunctionSet functionSet) {
        this.functionSet = functionSet;
    }

    @Override
    public Exp createNOExp(Exp l, String r) {
        return null;
    }

    @Override
    public Exp createExportDef(Exp e) {
        return new ExportExp(e);
    }

    @Override
    public Exp createExportDefaultDef(Exp e) {
        return new ExportDefaultExp(e);
    }

    @Override
    public Exp createImport(String file) {
        return createImportExp(file, false);
    }

    private ImportExp createImportExp(String file, boolean usingCache) {
        if (file.startsWith("@")) {
            return new ImportBeanExp(file.substring(1));
        }
        if (file.startsWith("java:")) {
            //静态类导入
            return new ImportStaticClassExp(file.substring("java:".length()));
        }
        if (file.equals("FSS")) {
            //导入FSS的默认函数集
            return new ImportDefaultFunTableExp(this);
        }
        if (usingCache) {
            return new CImportExp(file);
        } else {
            return new ImportFsscriptExp(file);
        }
    }

    @Override
    public Exp createAtImport(List v, String beanName) {
//        Exp beanExp = createContextObjectExp(beanName);
        fixArray(v);
        checkFunctionArgDefs(beanName, v);

        ImportBeanExp importExp = new ImportBeanExp(beanName);
        List<IdExp> vv = v;
        List<String> names = vv.stream().map(e -> e.value).collect(Collectors.toList());
        importExp.setNames(names);
        return importExp;
    }

    @Override
    public Exp createExpString(FsscriptClosureDefinition fcDefinition, String s) {
        if (StringUtils.isEmpty(s)) {
            return StringExp.EMPTY_STRING;
        }
        return ExpUtils.compile(fcDefinition, s,this);
    }

    @Override
    public Exp createAsExp(String i, String i2) {
        return new AsExp(i, i2);
    }

    @Override
    public MapEntry createDDDotMapEl(Exp r) {
        return new DDDotMapExp(r);
    }

    @Override
    public Exp createSwitchDefault(List xx) {
        return new SwitchDefaultExp(xx);
    }

    @Override
    public Exp createSwitchCase(Exp x, List x1) {
        return new SwitchCaseExp(x,x1);
    }

    @Override
    public Exp createSwitch(Exp e, List list) {
        return new SwitchExp(e,list);
    }

    @Override
    public ListExp createListExp() {
        return new ListExp();
    }

    @Override
    public Exp createDDDotListEl(Exp r) {
        return new DDDotListExp(r);
    }

    @Override
    public Exp createExpFunCall(Exp funExp, ListExp args) {
        return new ExpFunCall(this, funExp, args);
    }

//    @Override
//    public Exp createContinue() {
//        return null;
//    }

    @Override
    public Exp createImport(Object v, String file) {

        ImportExp importExp = createImportExp(file, false);

        if (v instanceof IdExp) {
            importExp.setName(((IdExp) v).value);
        } else if (v instanceof AsExp) {
            importExp.setName(((AsExp) v).getAsTring());
        } else if (v instanceof String) {
            importExp.setName(((String) v));
        } else if (v instanceof List) {
            List vv = (List) v;
            fixArray(vv);
            checkFunctionArgDefs(file, vv);

            List<Object> names = new ArrayList<>();
            for (Object o : vv) {
                if (o instanceof String) {
                    names.add((String) o);
                }
                if (o instanceof IdExp) {
                    names.add(((IdExp) o).value);
                } else if (o instanceof AsExp) {
                    names.add(o);
                } else {
                    throw new UnsupportedOperationException("不支持的类型: " + o);
                }
            }
            importExp.setExtNames(names);
        } else {
            throw new UnsupportedOperationException("不支持的类型: " + v);
        }
        return importExp;
    }

    @Override
    @Deprecated
    public Exp createImport(String file, boolean usingCache) {
        ImportExp importExp = createImportExp(file, usingCache);
        return importExp;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        DEFAULT = (DefaultExpFactory) appCtx.getBean("fsscriptExpFactory");
    }


    @Override
    public void destroy() throws Exception {
        clear();
    }
}
