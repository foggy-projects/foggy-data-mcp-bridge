package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * Debug test for destructuring
 */
public class DestructuringDebugTest {
    public static void main(String[] args) {
        // Test various combinations to find the exact issue

        // Case 1: All items with defaults (should pass based on previous test)
        test("All defaults", "const { a = 1, b = 2 } = {}; export a;");

        // Case 2: Single item without default
        test("Single without default", "const { a } = { a: 1 }; export a;");

        // Case 3: First without, second with default (failing case simplified)
        test("First without, second with", "const { a, b = 2 } = {}; export b;");

        // Case 4: First with, second without
        test("First with, second without", "const { a = 1, b } = {}; export a;");

        // Case 5: Number as default
        test("Number default", "const { x = 42 } = {}; export x;");

        // Case 6: String as default
        test("String default", "const { x = 'hello' } = {}; export x;");

        // Case 7: Empty object as default
        test("Object default", "const { x = {} } = {}; export x;");
    }

    private static void test(String name, String code) {
        System.out.print(name + ": ");
        try {
            Exp exp = new ExpParser().compileEl(code);
            ExpEvaluator ee = DefaultExpEvaluator.newInstance();
            exp.evalValue(ee);
            System.out.println("PASS");
        } catch (Exception e) {
            System.out.println("FAIL - " + e.getMessage());
        }
    }
}
