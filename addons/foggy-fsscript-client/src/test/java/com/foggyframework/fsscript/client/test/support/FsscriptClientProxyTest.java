package com.foggyframework.fsscript.client.test.support;

import com.foggyframework.core.common.MapBuilder;
import com.foggyframework.core.thread.MultiThreadExecutor;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.UuidUtils;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.client.test.FsscriptClientTestSupport;
import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinition;
import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinitionSpace;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.context.ApplicationContext;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FsscriptClientProxyTest extends FsscriptClientTestSupport {

    @Resource
    FsscriptClientTest fsscriptClientTest;
    @Resource
    ApplicationContext appCtx;

    @Test
    public void test() {
        DemoRet d = fsscriptClientTest.demo("aaa");

        Assertions.assertEquals(d.getAa(), "aaa");

        Assertions.assertNotNull(appCtx.getBean("fsscriptClientTest"));
    }

    @Test
    public void test2() {
        DemoRet d = fsscriptClientTest.demo2("aaabb");

        Assertions.assertEquals(d.getAa(), "aaabb");
    }

    @Test
    public void test3() {
        MultiThreadExecutor executor = new MultiThreadExecutor(1000);
        String[] iix = new String[100000];
        for (int i = 0; i < iix.length; i++) {
            iix[i] = UuidUtils.newUuid();
        }

        List<Boolean> oo = new ArrayList<>();

        for (String s : iix) {
            executor.execute(() -> {
                DemoRet d = fsscriptClientTest.demo2(s);

                oo.add(d.getAa().equals(s));

            });
        }

        executor.waitAllCompleted(true);
        for (Boolean aBoolean : oo) {
            Assertions.assertTrue(aBoolean);
        }


    }


    @Test
    public void test4() {
        Map mm = MapBuilder.builder().put("orderLength", 100).put("a", 200).build();
        Map d = fsscriptClientTest.build(mm);
        Assertions.assertEquals(d, mm);
    }

    @Test
    public void test4_1() {
        Map mm = MapBuilder.builder().put("orderLength", 100).put("a", 200).build();
        Map d = fsscriptClientTest.build2("aaaa", mm);
        mm.put("aa", "aaaa");
        Assertions.assertEquals(d, mm);
    }

    @Test
    public void test4_2() {
        Map mm = MapBuilder.builder().put("d4", 100).put("a", 200).build();
        Map d = fsscriptClientTest.build3("ffff", new X1(1, "hhhh"), "cccccccc", mm);
        Assertions.assertEquals(1, d.get("orderLength"));
        Assertions.assertEquals("hhhh", d.get("a"));
        Assertions.assertEquals("cccccccc", d.get("cc"));
        Assertions.assertEquals(100, d.get("d4"));
        Assertions.assertEquals("ffff", d.get("aa"));

    }

    @Test
    public void test4_3() {
        Map mm = MapBuilder.builder().put("d4", 100).put("a", 200).build();
        Map d = fsscriptClientTest.build4("ffff", new X1(1, "hhhh"), "cccccccc", mm);
        Assertions.assertEquals(1, d.get("orderLength"));
        Assertions.assertEquals("hhhh", d.get("a"));
        Assertions.assertEquals("cccccccc", d.get("cc"));
        Assertions.assertEquals(100, d.get("d4"));
        Assertions.assertEquals("ffff", d.get("aa"));
        Assertions.assertEquals(1, d.get("i4"));

        Map d2 = fsscriptClientTest.build4("ffff3", new X1(2, "hhhh6"), "cccccccc", mm);
        Assertions.assertEquals(2, d2.get("orderLength"));
        Assertions.assertEquals("hhhh6", d2.get("a"));
        Assertions.assertEquals("cccccccc", d2.get("cc"));
        Assertions.assertEquals(100, d2.get("d4"));
        Assertions.assertEquals("ffff3", d2.get("aa"));
        Assertions.assertEquals(2, d2.get("i4"));

    }

    int test4_3_FX_ERROR = 0;

    int test4_3X_FX_ERROR = 0;
    @Test
    public void test4_3_FX() {

        int max = 10000;

        MultiThreadExecutor executor = new MultiThreadExecutor(200);
        Map mm = MapBuilder.builder().put("d4", 100).put("a", 200).build();

        fsscriptClientTest.build4("ffff", new X1(1, "hhhh"), "cccccccc", mm);
        for (int i = 0; i < max; i++) {

            executor.execute( () -> {

                try {
                    Map d = fsscriptClientTest.build4("ffff", new X1(1, "hhhh"), "cccccccc", mm);
                    Assertions.assertEquals(1, d.get("orderLength"));
                    Assertions.assertEquals("hhhh", d.get("a"));
                    Assertions.assertEquals("cccccccc", d.get("cc"));
                    Assertions.assertEquals(100, d.get("d4"));
                    Assertions.assertEquals("ffff", d.get("aa"));
//                    Assertions.assertEquals(1, d.get("i4"));

                    Map d2 = fsscriptClientTest.build4("ffff3", new X1(2, "hhhh6"), "cccccccc", mm);
                    Assertions.assertEquals(2, d2.get("orderLength"));
                    Assertions.assertEquals("hhhh6", d2.get("a"));
                    Assertions.assertEquals("cccccccc", d2.get("cc"));
                    Assertions.assertEquals(100, d2.get("d4"));
                    Assertions.assertEquals("ffff3", d2.get("aa"));
//                    Assertions.assertEquals(2, d2.get("i4"));
                } catch (Throwable e) {
                    synchronized ("1") {
                        e.printStackTrace();
                        test4_3_FX_ERROR++;
                    }
                }

            });

        }
        executor.waitAllCompleted(true);
        Assertions.assertEquals(0, test4_3_FX_ERROR);

    }

    @Test
    public void test4_3X() {
        Map mm = MapBuilder.builder().put("d4", 100).put("a", 200).build();
        Map d = fsscriptClientTest.build4X("ffff", new X1(1, "hhhh"), "cccccccc", mm);
        Assertions.assertEquals(1, d.get("orderLength"));
        Assertions.assertEquals("hhhh", d.get("a"));
        Assertions.assertEquals("cccccccc", d.get("cc"));
        Assertions.assertEquals(100, d.get("d4"));
        Assertions.assertEquals("ffff", d.get("aa"));
        Assertions.assertEquals(1, d.get("i4"));

        Map d2 = fsscriptClientTest.build4X("ffff", new X1(2, "hhhh"), "cccccccc", mm);
        Assertions.assertEquals(2, d2.get("orderLength"));
        Assertions.assertEquals("hhhh", d2.get("a"));
        Assertions.assertEquals("cccccccc", d2.get("cc"));
        Assertions.assertEquals(100, d2.get("d4"));
        Assertions.assertEquals("ffff", d2.get("aa"));
        Assertions.assertEquals(1, d2.get("i4"));

    }

    @Test
    public void test4_3X_FX() {

        int max = 10000;

        MultiThreadExecutor executor = new MultiThreadExecutor(200);
        for (int i = 0; i < max; i++) {

            executor.execute(() -> {
                Map mm = MapBuilder.builder().put("d4", 100).put("a", 200).build();
                try {
                    Map d = fsscriptClientTest.build4X("ffff", new X1(1, "hhhh"), "cccccccc", mm);
                    Assertions.assertEquals(1, d.get("orderLength"));
                    Assertions.assertEquals("hhhh", d.get("a"));
                    Assertions.assertEquals("cccccccc", d.get("cc"));
                    Assertions.assertEquals(100, d.get("d4"));
                    Assertions.assertEquals("ffff", d.get("aa"));
//                    Assertions.assertEquals(1, d.get("i4"));

                    Map d2 = fsscriptClientTest.build4X("ffff1", new X1(3, "hhhh11"), "cccccccc2", mm);
                    Assertions.assertEquals(3, d2.get("orderLength"));
                    Assertions.assertEquals("hhhh11", d2.get("a"));
                    Assertions.assertEquals("cccccccc2", d2.get("cc"));
                    Assertions.assertEquals(100, d2.get("d4"));
                    Assertions.assertEquals("ffff1", d2.get("aa"));
//                    Assertions.assertEquals(2, d2.get("i4"));
                } catch (Throwable e) {
                    synchronized ("1") {
                        test4_3X_FX_ERROR++;
                    }
                }

            });

        }
        executor.waitAllCompleted(true);
        Assertions.assertEquals(0, test4_3X_FX_ERROR);

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class X1 {
        int orderLength;
        String a;
    }
}