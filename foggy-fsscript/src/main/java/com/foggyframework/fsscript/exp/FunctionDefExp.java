package com.foggyframework.fsscript.exp;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.fsscript.parser.spi.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 函数使用见UnresolvedFunCall
 */@Slf4j
public class FunctionDefExp extends AbstractExp<Exp> {
    @Deprecated
    final FsscriptClosureDefinition fcDefinition;

    private void test(ExpEvaluator evaluator,MapExp e,Object value){
        if(value==null||BeanInfoHelper.isBaseClassByStr(value.getClass().getName())){
            //基本类型，如int 等，不参与
            for (MapEntry mapEntry : (e).getLl()) {
                evaluator.setVar( mapEntry.getKey(), null);
            }
        }else if(value instanceof Map){
            for (MapEntry mapEntry : ( e).getLl()) {
                evaluator.setVar( mapEntry.getKey(), ((Map<?, ?>) value).get(mapEntry.getKey()));
            }
        }else {
            BeanInfoHelper h  = BeanInfoHelper.getClassHelper(value.getClass());
            for (MapEntry mapEntry : ( e).getLl()) {
                evaluator.setVar( mapEntry.getKey(), h.getBeanProperty(mapEntry.getKey(),true).getBeanValue(value));
            }
        }
    }

    class X implements Exp, FsscriptFunction {
        ExpEvaluator ee;
        //        FsscriptClosure savedFss;
        List<FsscriptClosure> savedStack;
        private final Object key = new Object();

        public X(ExpEvaluator ee) {
            this.ee = ee;
            savedStack = new ArrayList<>(ee.getStack());
        }

        @Override
        public Object evalValue(ExpEvaluator evaluator) {
            Object last = value.evalValue(evaluator);
            return unWarpResult(last);
        }

        /**
         * @param args the function argument
         * @return
         */
        @Override
        public Object apply(Object... args) {
            return executeFunction(ee.clone(), args);
        }

        @Override
        public Object threadSafeAccept(Object t) {
//            synchronized (key) {
                return executeFunction(ee.clone(),new Object[]{t});
//            }
        }
//
//        @Override
//        public Object threadSafeAcceptV2(Object t) {
////            new DelegateE
//            return executeFunction(ee, new Object[]{t});
//        }

        @Override
        public Class<?> getReturnType(ExpEvaluator evaluator) {
            return null;
        }

@Override
        public Object executeFunction(ExpEvaluator evaluator, Object... args) {

            String name;
            int i = 0;

            // Fix: 保存 evaluator 原始栈，替换为 savedStack 以消除栈冗余
            // 之前 ee.clone() 复制的栈与 savedStack 内容重叠，导致 savedStack.size = 2N-1
            Stack<FsscriptClosure> stack = evaluator.getStack();
            List<FsscriptClosure> originalStack = new ArrayList<>(stack);
            stack.clear();

            try {

                stack.addAll(savedStack);
                evaluator.pushNewFoggyClosure();
                Object value = null;
                for (Exp e : argDefs) {
                    value = args.length > i? args[i]:null;
                    if(e instanceof IdExp) {
                        name = ((IdExp) e).getValue();
                            evaluator.setVar(name, value);
                    }else if(e instanceof MapExp){
                        test(evaluator, (MapExp) e,value);
                    }else if(e instanceof DefaultArgExp){
                        // 处理带默认值的参数
                        DefaultArgExp defArg = (DefaultArgExp) e;
                        name = defArg.getParamName();
                        if (value == null) {
                            // 参数未传递或为null，使用默认值
                            value = defArg.getDefaultValue().evalValue(evaluator);
                        }
                        evaluator.setVar(name, value);
                    }
                    i++;
                }

                return evalValue(evaluator);
            } finally {
                // 恢复 evaluator 的原始栈
                stack.clear();
                stack.addAll(originalStack);
            }
        }

        @Override
        public List<Exp> getArgDefs() {
            return argDefs;
        }

        @Override
        public Object autoApply(ExpEvaluator evaluator) {
            String name;

            Map<String, Object> mm = new HashMap<>();
            int i=0;
            for (Exp e : argDefs) {
                if(e instanceof IdExp) {
                    name = ((IdExp) e).getValue();
                    mm.put(name, evaluator.getVar(name));
                }else if(e instanceof MapExp){
                   Object[]objects = (Object[]) evaluator.getVar(ExpEvaluator._argumentsKey);
                   if(objects !=null) {
                       if (i < objects.length) {
                           Object v = objects[i];
                           test(evaluator, (MapExp) e, v);
                       } else {
                           log.warn("数组长度超出，无视该参数");
                       }
                   }else{
                        throw RX.throwB("没有"+ExpEvaluator._argumentsKey+"参数?");
                   }

                }else if(e instanceof DefaultArgExp){
                    // 处理带默认值的参数
                    DefaultArgExp defArg = (DefaultArgExp) e;
                    name = defArg.getParamName();
                    Object value = evaluator.getVar(name);
                    if (value == null) {
                        // 使用默认值
                        value = defArg.getDefaultValue().evalValue(evaluator);
                    }
                    mm.put(name, value);
                }
                i++;
            }
            // Fix: 保存 evaluator 原始栈，替换为 savedStack 以消除栈冗余
            Stack<FsscriptClosure> stack = evaluator.getStack();
            List<FsscriptClosure> originalStack = new ArrayList<>(stack);
            stack.clear();

            try {
                stack.addAll(savedStack);
                evaluator.pushNewFoggyClosure();

                evaluator.setMap2Var(mm);

                return evalValue(evaluator);
            } finally {
                // 恢复 evaluator 的原始栈
                stack.clear();
                stack.addAll(originalStack);
            }
        }
    }

    List<Exp> argDefs;

    String name;

    public FunctionDefExp(FsscriptClosureDefinition fcDefinition, Exp value, List<Exp> argDefs) {
        super(value);
        this.fcDefinition = fcDefinition;
        this.argDefs = argDefs;
    }

    public FunctionDefExp(FsscriptClosureDefinition fcDefinition, String name, Exp value, List<Exp> argDefs) {
        super(value);
        this.fcDefinition = fcDefinition;
        this.argDefs = argDefs;
        this.name = name;
    }

    @Override
    public FsscriptFunction evalValue(ExpEvaluator evaluator) {
        FsscriptFunction c = new X(evaluator);
        evaluator.setVar(name, c);
        return c;
    }

    public String getName() {
        return name;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return null;
    }

    @Override
    public String toString() {
        return "[FunctionDefExp : " + name + "\n" + value.toString() + "]";
    }
}