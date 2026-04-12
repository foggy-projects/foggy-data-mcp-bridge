package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.Map;

/**
 * Debug test for destructuring and arrow functions
 */
public class DestructuringDebugTest {
    public static void main(String[] args) {
        System.out.println("=== Destructuring Tests ===");
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

        System.out.println("\n=== Arrow Function Tests ===");

        // Arrow function tests
        test("just identifier", "x");
        test("simple expr", "x * 2");
        test("arrow in parens", "(x) => { return x * 2; }");
        test("arrow block body", "x => { return x * 2; }");
        test("arrow expr body no semi", "x => x * 2");
        test("arrow property access", "x => obj[x]");
        test("const arrow block", "const fn = x => { return x * 2; }; export fn;");
        test("const arrow expr", "const fn = x => x * 2; export fn;");
        test("map arrow", "const result = [1,2].map(x => x * 2); export result;");
        test("filter Boolean", "const result = [1,null,2].filter(Boolean); export result;");

        // Parse-only tests (no execution)
        System.out.println("\n=== Parse-Only Tests (no execution) ===");
        testParseOnly("arrow expr parse", "x => x * 2");
        testParseOnly("arrow expr with semi", "x => x * 2;");
        testParseOnly("arrow prop parse", "x => obj[x]");
        testParseOnly("multi-param arrow", "(x, y) => x + y");
        testParseOnly("multi-param arrow semi", "(x, y) => x + y;");
        testParseOnly("arrow in map context", "[].map(x => x * 2)");
        testParseOnly("function expr parse", "x => xx()");

        // Export function tests
        System.out.println("\n=== Export Function Tests ===");
        testExport("export var via compileEl", "const x = 1; export x;", false);
        testExport("export var via compile", "const x = 1; export x;", true);
        testExport("simple export function", "export function foo() { return 1; }", true);
        testExport("export function with params", "export function add(a, b) { return a + b; }", true);
    }

    private static void testExport(String name, String code, boolean useCompile) {
        System.out.print(name + ": ");
        try {
            Exp exp = useCompile ? new ExpParser().compile(code) : new ExpParser().compileEl(code);
            ExpEvaluator ee = DefaultExpEvaluator.newInstance();
            exp.evalValue(ee);
            Map<String, Object> exports = ee.getExportMap();
            if (exports.isEmpty()) {
                System.out.println("FAIL - no exports");
            } else {
                System.out.println("PASS (exports: " + exports.keySet() + ")");
            }
        } catch (Exception e) {
            System.out.println("FAIL - " + e.getMessage());
        }
    }

    private static void testParseOnly(String name, String code) {
        System.out.print(name + ": ");
        try {
            Exp exp = new ExpParser().compileEl(code);
            System.out.println("PASS (parsed: " + exp.getClass().getSimpleName() + ")");
        } catch (Exception e) {
            System.out.println("FAIL - " + e.getMessage());
        }
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
