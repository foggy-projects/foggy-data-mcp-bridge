package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.utils.ExpUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumberFormattingMethodsTest {

    @Test
    void toPrecisionUsesSignificantDigitsAndJavaScriptNotationThresholds() {
        assertEquals("1234.50", evaluate(1234.5d, "value.toPrecision(6)"));
        assertEquals("1.23e+3", evaluate(1234.5d, "value.toPrecision(3)"));
        assertEquals("0.00000123", evaluate(1.2345e-6d, "value.toPrecision(3)"));
        assertEquals("1e-7", evaluate(1e-7d, "value.toPrecision(1)"));
        assertEquals("0.00", evaluate(-0.0d, "value.toPrecision(3)"));
        assertEquals("10000000", evaluate(1e7d, "value.toPrecision()"));
    }

    @Test
    void toPrecisionRoundsAndRejectsPrecisionOutsideItsRange() {
        assertEquals("-1.3", evaluate(-1.25d, "value.toPrecision(2)"));
        assertEquals("10.0", evaluate(9.999d, "value.toPrecision(3)"));
        assertThrows(RuntimeException.class, () -> evaluate(1.2d, "value.toPrecision(0)"));
        assertThrows(RuntimeException.class, () -> evaluate(1.2d, "value.toPrecision(101)"));
    }

    @Test
    void toExponentialFormatsWithOptionalFractionDigits() {
        assertEquals("1.23e+3", evaluate(1234.5d, "value.toExponential(2)"));
        assertEquals("1.2345e+3", evaluate(1234.5d, "value.toExponential()"));
        assertEquals("1e+1", evaluate(9.9d, "value.toExponential(0)"));
        assertEquals("0.000e+0", evaluate(0.0d, "value.toExponential(3)"));
        assertEquals("-Infinity", evaluate(Double.NEGATIVE_INFINITY, "value.toExponential(-1)"));
    }

    @Test
    void toExponentialRejectsFractionDigitsOutsideItsRange() {
        assertThrows(RuntimeException.class, () -> evaluate(1.2d, "value.toExponential(-1)"));
        assertThrows(RuntimeException.class, () -> evaluate(1.2d, "value.toExponential(101)"));
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
