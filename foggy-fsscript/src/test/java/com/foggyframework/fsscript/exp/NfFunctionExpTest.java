package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
public class NfFunctionExpTest {

    @Autowired
    ApplicationContext appCtx;

    @Test
    public void nfFunctionTest() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/nf_function_test.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Map mm = ee.getExportMap();
        Assertions.assertEquals("b",mm.get("b"));
        Assertions.assertEquals(2,mm.get("cc"));
        Assertions.assertEquals(null,mm.get("d"));
        Assertions.assertEquals(1,mm.get("c"));
        Assertions.assertEquals(2,mm.get("ee"));

        Assertions.assertEquals(2,mm.get("dd"));
        Assertions.assertEquals("aa",mm.get("ff"));

        Function export1 = (Function) mm.get("export1");
        export1.apply(new Object[0]);

       Function export2 = (Function) mm.get("export2");
        Function export3 = (Function) mm.get("export3");


        Assertions.assertEquals(3,export2.apply(new Object[0]));
        Assertions.assertEquals(2,export3.apply(new Object[0]));

        Function export4 = (Function) mm.get("export4");
        Assertions.assertEquals(4,export4.apply(new Object[0]));
    }


    @Test
    public void nfFunctionTest2() {
        String expStr = "var b = e=>{return 'b';};b();";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object result = exp.evalValue(ee);

        Assertions.assertEquals("b",result);

//        Assertions.assertEquals(null,mm.get("bb"));
    }

    // ── arrow expression body tests (fix: ElExpScanner auto-close on ; and EOF) ──

    /**
     * const fn = x => x * 2 — expression body assigned and callable
     */
    @Test
    public void arrowExprBody_standalone() {
        String code = "const fn = x => x * 2; const r = fn(3); export r;";
        Exp exp = new ExpParser().compileEl(code);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance();
        exp.evalValue(ee);

        Assertions.assertEquals(6.0, ee.getExportMap().get("r"));
    }

    /**
     * x => fn() — function call inside expression body
     */
    @Test
    public void arrowExprBody_functionCall() {
        String code = "var counter = 0;"
                + "function inc() { counter = counter + 1; return counter; }"
                + "const fn = x => inc();"
                + "const r1 = fn(null); const r2 = fn(null);"
                + "export r1; export r2;";
        Exp exp = new ExpParser().compileEl(code);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance();
        exp.evalValue(ee);

        // counter + 1 yields Integer (int arithmetic)
        Assertions.assertEquals(1, ee.getExportMap().get("r1"));
        Assertions.assertEquals(2, ee.getExportMap().get("r2"));
    }

    /**
     * x => obj[x] — bracket property access in expression body
     */
    @Test
    public void arrowExprBody_propertyAccess() {
        String code = "const obj = { a: 10, b: 20 };"
                + "const fn = x => obj[x];"
                + "const r = fn('a');"
                + "export r;";
        Exp exp = new ExpParser().compileEl(code);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance();
        exp.evalValue(ee);

        Assertions.assertEquals(10, ee.getExportMap().get("r"));
    }

    /**
     * (x, y) => x + y — multi-param arrow expression body
     */
    @Test
    public void arrowExprBody_multiParam() {
        String code = "const add = (x, y) => x + y; const r = add(2, 3); export r;";
        Exp exp = new ExpParser().compileEl(code);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance();
        exp.evalValue(ee);

        // int + int yields Integer
        Assertions.assertEquals(5, ee.getExportMap().get("r"));
    }

    /**
     * [].map(x => x * 10) — arrow in callback (regression guard, worked before the fix)
     */
    @Test
    public void arrowExprBody_inMapCallback() {
        String code = "const result = [1, 2, 3].map(x => x * 10); export result;";
        Exp exp = new ExpParser().compileEl(code);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance();
        exp.evalValue(ee);

        Object result = ee.getExportMap().get("result");
        Assertions.assertNotNull(result);
        Assertions.assertEquals(Arrays.asList(10.0, 20.0, 30.0), result);
    }

    /**
     * [].filter(Boolean) — filter callback (regression guard)
     */
    @Test
    public void arrowExprBody_inFilterCallback() {
        String code = "const result = [1, null, 2, null, 3].filter(Boolean); export result;";
        Exp exp = new ExpParser().compileEl(code);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance();
        exp.evalValue(ee);

        Object result = ee.getExportMap().get("result");
        Assertions.assertNotNull(result);
        Assertions.assertEquals(Arrays.asList(1, 2, 3), result);
    }

    /**
     * var fn = x => x * 2; fn(5) — direct invocation returns result
     */
    @Test
    public void arrowExprBody_directCall() {
        Exp exp = new ExpParser().compileEl("var fn = x => x * 2; fn(5);");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance();
        Object result = exp.evalValue(ee);

        Assertions.assertEquals(10.0, result);
    }

    // ── arrow inside object / array literal (fix: inNf brace depth tracking) ──

    /**
     * { valueBuilder: (ctx) => fn() } — arrow expr body as object property value
     */
    @Test
    public void arrowExprBody_inObjectValue() {
        String code = "function getTenantId() { return 42; }\n"
                + "var v = { valueBuilder: (ctx) => getTenantId() };\n"
                + "export v;";
        Exp exp = new ExpParser().compileEl(code);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance();
        exp.evalValue(ee);

        Map<String, Object> v = (Map<String, Object>) ee.getExportMap().get("v");
        Assertions.assertNotNull(v);
        Function vb = (Function) v.get("valueBuilder");
        Assertions.assertNotNull(vb, "arrow in object value should be callable");
        Assertions.assertEquals(42, vb.apply(new Object[]{"ctx"}));
    }

    /**
     * [{ valueBuilder: (ctx) => fn() }] — arrow expr body inside array of objects
     */
    @Test
    public void arrowExprBody_inArrayObjectValue() {
        String code = "function getTenantId() { return 42; }\n"
                + "var v = [{ field: 'id', valueBuilder: (ctx) => getTenantId() }];\n"
                + "export v;";
        Exp exp = new ExpParser().compileEl(code);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance();
        exp.evalValue(ee);

        List<Map<String, Object>> v = (List<Map<String, Object>>) ee.getExportMap().get("v");
        Assertions.assertNotNull(v);
        Assertions.assertEquals(1, v.size());
        Assertions.assertEquals("id", v.get(0).get("field"));
        Function vb = (Function) v.get(0).get("valueBuilder");
        Assertions.assertEquals(42, vb.apply(new Object[]{"ctx"}));
    }

    /**
     * forcedSlice patch pattern — the real-world use case:
     * nested object > array > objects with mixed static values and arrow callbacks
     */
    @Test
    public void arrowExprBody_forcedSlicePatchPattern() {
        String code = "function requireTenantId() { return 99; }\n"
                + "var v = {\n"
                + "    patch: {\n"
                + "        forcedSlice: [\n"
                + "            { field: 'tenantFlag', op: '=', value: 1 },\n"
                + "            { field: 'id', op: '=', valueBuilder: (ctx) => requireTenantId() }\n"
                + "        ]\n"
                + "    }\n"
                + "};\n"
                + "export v;";
        Exp exp = new ExpParser().compileEl(code);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance();
        exp.evalValue(ee);

        Map<String, Object> v = (Map<String, Object>) ee.getExportMap().get("v");
        Map<String, Object> patch = (Map<String, Object>) v.get("patch");
        List<Map<String, Object>> slices = (List<Map<String, Object>>) patch.get("forcedSlice");
        Assertions.assertEquals(2, slices.size());

        // static entry
        Assertions.assertEquals("tenantFlag", slices.get(0).get("field"));
        Assertions.assertEquals(1, slices.get(0).get("value"));

        // arrow callback entry
        Assertions.assertEquals("id", slices.get(1).get("field"));
        Function vb = (Function) slices.get(1).get("valueBuilder");
        Assertions.assertNotNull(vb, "valueBuilder should be a callable function");
        Assertions.assertEquals(99, vb.apply(new Object[]{"ctx"}));
    }

}