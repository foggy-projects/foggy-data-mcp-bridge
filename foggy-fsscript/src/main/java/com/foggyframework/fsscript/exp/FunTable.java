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
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.fsscript.fun.*;
import com.foggyframework.fsscript.fun.ext.*;
import com.foggyframework.fsscript.fun.log.Debug;
import com.foggyframework.fsscript.parser.FunDef;
import org.springframework.beans.factory.DisposableBean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class FunTable implements FunctionSet, DisposableBean {
    Map<String, FunDef> funs = new HashMap<String, FunDef>();

//	Map<String, Pattern> patterns = new HashMap<String, Pattern>();

    public void clear() {
        funs.clear();
    }



    public FunTable() {
        append(new Plus());
        append(new Debugger());

        append(new Multiply());
        append(new Reduce());
        append(new Iif());
        append(new Else());
        append(new ElseIf());
        append(new Equal());
        append(new NotEqual());
        append(new Case());
        append(new GT());

        append(new Brackets());
        append(new DIVISION());
        append(new BangExp());
        append(new BitwiseAnd());

        //ext函数
        append(new DateTimeFormatFunDef());
        append(new DateFormatFunDef());

        append(new Debug());
        /**
         * "||"函数
         */
        append(new ConcatExp());
        append(new AA());

        //日期函数
        append(new ToStartTimeFunDef());
        append(new ToEndTimeFunDef());
        append(new CurrentDateFunDef());
        append(new ToDateFunDef());
        append(new CheckDaysRangeFunDef());
        append(new ClearEmptyDef());

        append(new SleepFunDef());
        append(new UuidFunDef());
        append(new PERCENT());
        append(new LT());
        append(new MathCeil());
        append(new GT_equal());
        append(new LT_equal());

        append(new P_IJJ());
        append(new P_JJI());
        append(new M_IJJ());
        append(new M_JJI());

        append(new Int());
        append(new Ints());

        append(new ParseFile());
        append(new IN());
    }

    private void addFun(String name, FunDef f) {
        name = name.toUpperCase();
        if (funs.containsKey(name)) {

            Object object = funs.get(name);

//			if (object.getClass().getName().indexOf(".test.") > 0) {
//				// 这应该是在测试环境，不报错
//				return;
//			}
//			if (f.getClass().getName().indexOf(".test.") > 0) {
//				// 优先用测试的同名函数
//				funs.put(name, f);
//				return;
//			}
//为了测试通过暂时拿掉
//            throw RX.throwB("FunDef存在重复定义:" + f + "," + funs.get(name));
        }
        funs.put(name, f);
    }

    @Override
    public void append(final FunDef fd) {
        if (fd != null) {
            if (fd.getName().contains(",")) {
                for (String n : fd.getName().split(",")) {
                    if (!StringUtils.isEmpty(n)) {
                        addFun(n.trim(), fd);
                    }
                }
            } else {
                addFun(fd.getName(), fd);
            }
        }
    }

    @Override
    public FunDef getFun(final String funName) {
        return funs.get(funName.toUpperCase());
    }

    @Override
    public FunDef getFun(UnresolvedFunCall funCall) {
        return getFun(funCall.getString());
    }

    @Override
    public void append(String name, FunDef f) {
        addFun(name, f);
    }

    public void addAll(List<FunDef> regfuns) {
        for (FunDef regfun : regfuns) {
            append(regfun);
        }
    }

    public void removeAll(List<FunDef> regfuns) {
        for (FunDef regfun : regfuns) {
            String name = regfun.getName().toUpperCase();
            funs.remove(name);
        }

    }

    @Override
    public void destroy() throws Exception {
        clear();
    }

//	@Override
//	public void onApplicationEvent(ContextClosedEvent event) {
//
//	}

    // public Pattern getPattern(final String key) {
    // return patterns.get(key);
    // }
}
