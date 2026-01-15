package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.Map;

/**
 * Quick verification test for common-dims.fsscript features.
 *
 * <p>Note: For full testing with Spring context and file loading,
 * use {@link CommonDimsParseTest} instead. This test verifies
 * individual features work in isolation.
 *
 * <p>For files with template literals, use FileFsscriptLoader
 * which sets up FsscriptClosureDefinition properly.
 */
public class CommonDimsDirectTest {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("FSScript Feature Verification Test");
        System.out.println("========================================\n");

        int passed = 0;
        int failed = 0;

        // Test 1: export function syntax
        System.out.print("export function syntax: ");
        try {
            Exp exp = new ExpParser().compileEl(
                "export function buildDateDim() { return { name: 'date' }; }");
            ExpEvaluator ee = DefaultExpEvaluator.newInstance();
            exp.evalValue(ee);
            Map<String, Object> exports = ee.getExportMap();
            if (exports.containsKey("buildDateDim")) {
                System.out.println("PASS");
                passed++;
            } else {
                System.out.println("FAIL (not exported)");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAIL - " + e.getMessage());
            failed++;
        }

        // Test 2: destructuring with defaults
        System.out.print("destructuring with defaults: ");
        try {
            Exp exp = new ExpParser().compileEl(
                "const { name = 'default', value = 42 } = {}; export { name, value };");
            ExpEvaluator ee = DefaultExpEvaluator.newInstance();
            exp.evalValue(ee);
            Map<String, Object> exports = ee.getExportMap();
            if ("default".equals(exports.get("name")) && Integer.valueOf(42).equals(exports.get("value"))) {
                System.out.println("PASS");
                passed++;
            } else {
                System.out.println("FAIL (wrong values: " + exports + ")");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAIL - " + e.getMessage());
            failed++;
        }

        // Test 3: arrow function in map
        System.out.print("arrow function in .map(): ");
        try {
            Exp exp = new ExpParser().compileEl(
                "const result = [1, 2, 3].map(x => x * 2); export result;");
            ExpEvaluator ee = DefaultExpEvaluator.newInstance();
            exp.evalValue(ee);
            Map<String, Object> exports = ee.getExportMap();
            java.util.List<?> result = (java.util.List<?>) exports.get("result");
            // Note: numeric operations return Double in FSScript
            if (result != null && result.size() == 3 &&
                ((Number)result.get(0)).intValue() == 2 &&
                ((Number)result.get(1)).intValue() == 4 &&
                ((Number)result.get(2)).intValue() == 6) {
                System.out.println("PASS");
                passed++;
            } else {
                System.out.println("FAIL (wrong result: " + result + ")");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAIL - " + e.getMessage());
            failed++;
        }

        // Test 4: filter(Boolean)
        System.out.print("filter(Boolean): ");
        try {
            Exp exp = new ExpParser().compileEl(
                "const result = [1, null, 2, '', 3].filter(Boolean); export result;");
            ExpEvaluator ee = DefaultExpEvaluator.newInstance();
            exp.evalValue(ee);
            Map<String, Object> exports = ee.getExportMap();
            java.util.List<?> result = (java.util.List<?>) exports.get("result");
            if (result != null && result.size() == 3) {
                System.out.println("PASS");
                passed++;
            } else {
                System.out.println("FAIL (wrong result: " + result + ")");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAIL - " + e.getMessage());
            failed++;
        }

        // Test 5: method chaining (map + filter)
        System.out.print("method chaining .map().filter(): ");
        try {
            Exp exp = new ExpParser().compileEl(
                "const result = ['a', 'b', 'c'].map(x => x).filter(Boolean); export result;");
            ExpEvaluator ee = DefaultExpEvaluator.newInstance();
            exp.evalValue(ee);
            Map<String, Object> exports = ee.getExportMap();
            java.util.List<?> result = (java.util.List<?>) exports.get("result");
            if (result != null && result.size() == 3) {
                System.out.println("PASS");
                passed++;
            } else {
                System.out.println("FAIL (wrong result: " + result + ")");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAIL - " + e.getMessage());
            failed++;
        }

        // Summary
        System.out.println("\n========================================");
        System.out.println("Results: " + passed + " passed, " + failed + " failed");
        System.out.println("========================================");

        System.out.println("\nNote: For full common-dims.fsscript testing,");
        System.out.println("run CommonDimsParseTest with Spring context.");

        if (failed > 0) {
            System.exit(1);
        }
    }
}
