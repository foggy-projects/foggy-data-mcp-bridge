package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.parser.spi.CompileException;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.utils.ExpUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class CompoundAssignableTargetTest {

    @Test
    void mapPropertyAddition() {
        Map<String, Object> obj = new HashMap<>();
        obj.put("key", 1);
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();
        evaluator.setVar("obj", obj);

        eval("obj.key += 2;", evaluator);

        Assertions.assertEquals(3, obj.get("key"));
    }

    @Test
    void beanPropertySubtraction() {
        ValueBean bean = new ValueBean(5);
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();
        evaluator.setVar("bean", bean);

        eval("bean.value -= 3;", evaluator);

        Assertions.assertEquals(2, bean.getValue());
    }

    @Test
    void propertyReceiverAndRightHandSideAreEvaluatedOnce() {
        Probe probe = new Probe();
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();
        evaluator.setVar("probe", probe);

        eval("probe.nextMap().key += probe.nextValue();", evaluator);

        Assertions.assertEquals(3, probe.map.get("key"));
        Assertions.assertEquals(1, probe.mapCalls);
        Assertions.assertEquals(1, probe.valueCalls);
    }

    @Test
    void listIndexAndRightHandSideAreEvaluatedOnce() {
        List<Object> arr = new ArrayList<>(List.of(1, 4));
        Probe probe = new Probe();
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();
        evaluator.setVar("arr", arr);
        evaluator.setVar("probe", probe);

        eval("arr[probe.nextIndex()] += probe.nextValue();", evaluator);

        Assertions.assertEquals(List.of(3, 4), arr);
        Assertions.assertEquals(1, probe.indexCalls);
        Assertions.assertEquals(1, probe.valueCalls);
    }

    @Test
    void arraySubtraction() {
        Object[] arr = {5, 8};
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();
        evaluator.setVar("arr", arr);

        eval("arr[1] -= 3;", evaluator);

        Assertions.assertArrayEquals(new Object[]{5, 5}, arr);
    }

    @Test
    void mapSubscriptAddition() {
        Map<String, Object> obj = new HashMap<>();
        obj.put("key", 1);
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();
        evaluator.setVar("obj", obj);

        eval("obj['key'] += 2;", evaluator);

        Assertions.assertEquals(3, obj.get("key"));
    }

    @Test
    void multiplyDivideAndModuloAssignmentsSupportVariablesAndProperties() {
        Map<String, Object> obj = new HashMap<>();
        obj.put("key", 6);
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();
        evaluator.setVar("itemIndex", 8);
        evaluator.setVar("obj", obj);

        eval("itemIndex *= 3; itemIndex /= 4; itemIndex %= 5;"
                + "obj.key *= 2; obj.key /= 3; obj.key %= 3;", evaluator);

        Assertions.assertEquals(1, evaluator.getVar("itemIndex"));
        Assertions.assertEquals(1, obj.get("key"));
    }

    @Test
    void multiplyAssignmentSubscriptEvaluatesTargetAndRightHandSideOnce() {
        List<Object> arr = new ArrayList<>(List.of(3));
        Probe probe = new Probe();
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();
        evaluator.setVar("arr", arr);
        evaluator.setVar("probe", probe);

        eval("arr[probe.nextIndex()] *= probe.nextValue();", evaluator);

        Assertions.assertEquals(6.0d, arr.get(0));
        Assertions.assertEquals(1, probe.indexCalls);
        Assertions.assertEquals(1, probe.valueCalls);
    }

    @Test
    void nonAssignableExpressionIsRejected() {
        Assertions.assertThrows(CompileException.class, () ->
                ExpUtils.compileEl(
                        new SimpleFsscriptClosureDefinitionSpace().newFsscriptClosureDefinition(),
                        "1 += 2;", null));
    }

    private void eval(String script, DefaultExpEvaluator evaluator) {
        Exp exp = ExpUtils.compileEl(
                new SimpleFsscriptClosureDefinitionSpace().newFsscriptClosureDefinition(), script, null);
        exp.evalValue(evaluator);
    }

    public static class ValueBean {
        private int value;

        public ValueBean(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    public static class Probe {
        private final Map<String, Object> map = new HashMap<>();
        private int mapCalls;
        private int indexCalls;
        private int valueCalls;

        public Probe() {
            map.put("key", 1);
        }

        public Map<String, Object> nextMap() {
            mapCalls++;
            return map;
        }

        public int nextIndex() {
            indexCalls++;
            return 0;
        }

        public int nextValue() {
            valueCalls++;
            return 2;
        }
    }
}
