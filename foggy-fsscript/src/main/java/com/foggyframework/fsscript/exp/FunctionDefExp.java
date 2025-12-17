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

            //TODO 这里似乎应该保存整个 stack
//            savedFss = ee.getCurrentFsscriptClosure();
            savedStack = new ArrayList<>(ee.getStack());
//            ee.getSt
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

            try {

                evaluator.pushFsscriptClosure(savedStack);
                evaluator.pushNewFoggyClosure();
                Object value = null;
                for (Exp e : argDefs) {
                    value = args.length > i? args[i]:null;
                    if(e instanceof IdExp) {
                        name = ((IdExp) e).getValue();
//                        if (args.length > i) {
//                            evaluator.setVar(name, args[i]);
//                        } else {
                            evaluator.setVar(name, value);
//                        }
                    }else if(e instanceof MapExp){
//                        if(value==null||BeanInfoHelper.isBaseClassByStr(value.getClass().getName())){
//                            //基本类型，如int 等，不参与
//                            for (MapEntry mapEntry : ((MapExp) e).getLl()) {
//                                evaluator.setVar( mapEntry.getKey(), null);
//                            }
//                        }else if(value instanceof Map){
//                            for (MapEntry mapEntry : ((MapExp) e).getLl()) {
//                                evaluator.setVar( mapEntry.getKey(), ((Map<?, ?>) value).get(mapEntry.getKey()));
//                            }
//                        }else {
//                            BeanInfoHelper h  = BeanInfoHelper.getClassHelper(value.getClass());
//                            for (MapEntry mapEntry : ((MapExp) e).getLl()) {
//                                evaluator.setVar( mapEntry.getKey(), h.getBeanProperty(mapEntry.getKey(),true).getBeanValue(value));
//                            }
//                        }
                        test(evaluator, (MapExp) e,value);
//                        if (args.length > i) {
//
//                        } else {
//                            evaluator.setVar(name, null);
//                        }
                    }
                    i++;
                }

                return evalValue(evaluator);
            } finally {
                evaluator.popFsscriptClosure();
                evaluator.popFsscriptClosure(savedStack.size());

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

                }
                i++;
            }
            try {


                evaluator.pushFsscriptClosure(savedStack);
                evaluator.pushNewFoggyClosure();

                evaluator.setMap2Var(mm);

                return evalValue(evaluator);
            } finally {
                evaluator.popFsscriptClosure();
                evaluator.popFsscriptClosure(savedStack.size());

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