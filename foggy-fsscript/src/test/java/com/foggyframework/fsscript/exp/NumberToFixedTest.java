package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.utils.ExpUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumberToFixedTest {

    @Test
    void formatsNumbersWithTheRequestedFractionDigits() {
        assertEquals("1.20", evaluate(1.2d, "value.toFixed(2)"));
        assertEquals("12", evaluate(12.4d, "value.toFixed()"));
        assertEquals("1.3", evaluate(1.25d, "value.toFixed(1)"));
    }

    @Test
    void roundsUsingTheExactBinaryDoubleValue() {
        assertEquals("1.00", evaluate(1.005d, "value.toFixed(2)"));
    }

    @Test
    void preservesNegativeSignWhenTheRoundedValueIsZero() {
        assertEquals("-0.00", evaluate(-0.0001d, "value.toFixed(2)"));
        assertEquals("0.00", evaluate(-0.0d, "value.toFixed(2)"));
    }

    @Test
    void formatsSpecialAndVeryLargeNumbers() {
        assertEquals("NaN", evaluate(Double.NaN, "value.toFixed(2)"));
        assertEquals("Infinity", evaluate(Double.POSITIVE_INFINITY, "value.toFixed(2)"));
        assertEquals("-Infinity", evaluate(Double.NEGATIVE_INFINITY, "value.toFixed(2)"));
        assertEquals("1e+21", evaluate(1e21d, "value.toFixed(2)"));
    }

    @Test
    void rejectsFractionDigitsOutsideTheSupportedRange() {
        assertThrows(RuntimeException.class, () -> evaluate(1.2d, "value.toFixed(101)"));
        assertThrows(RuntimeException.class, () -> evaluate(1.2d, "value.toFixed(-1)"));
    }

    private Object evaluate(Object value, String script) {
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();
        evaluator.setVar("value", value);
        Exp exp = ExpUtils.compileEl(
                new SimpleFsscriptClosureDefinitionSpace().newFsscriptClosureDefinition(),
                script,
                null);
        return exp.evalValue(evaluator);
    }
}
