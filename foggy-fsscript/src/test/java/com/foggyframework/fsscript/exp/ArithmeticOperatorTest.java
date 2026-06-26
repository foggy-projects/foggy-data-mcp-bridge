package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArithmeticOperatorTest {

    @Test
    void divisionShouldKeepNumericBehavior() {
        assertEquals(2.0, eval("return 6 / 3;"));
        assertEquals(2.5, eval("return 5 / 2;"));
    }

    @Test
    void divisionShouldKeepExistingNullBehavior() {
        assertEquals(0.0, eval("return null / 2;"));
        assertTrue(Double.isNaN((Double) eval("return 2 / null;")));
        assertEquals(0.0, eval("return null / null;"));
    }

    @Test
    void divisionShouldKeepExistingZeroDivisionBehavior() {
        assertEquals(Double.POSITIVE_INFINITY, eval("return 2 / 0;"));
    }

    @Test
    void divisionShouldTreatNonNumberLeftOperandLikeMissingLeftOperand() {
        assertEquals(0.0, eval("return 'x' / 2;"));
    }

    @Test
    void divisionShouldTreatNonNumberRightOperandLikeMissingRightOperand() {
        assertTrue(Double.isNaN((Double) eval("return 2 / 'x';")));
    }

    private Object eval(String expStr) {
        Exp exp = new ExpParser().compileEl(expStr);
        return exp.evalResult(DefaultExpEvaluator.newInstance());
    }
}
