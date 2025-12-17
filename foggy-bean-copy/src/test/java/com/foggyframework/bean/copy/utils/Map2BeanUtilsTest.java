package com.foggyframework.bean.copy.utils;

import com.foggyframework.core.common.MapBuilder;
import lombok.Data;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.context.ApplicationContext;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class Map2BeanUtilsTest {
    @Resource
    ApplicationContext appCtx;

    @Test
    public void fromMap() {
        Map mm = buildTestMap();

        TestBean tb = Map2BeanUtils.fromMap(mm, TestBean.class);

        Assertions.assertEquals(tb.getA(), "12");
        Assertions.assertEquals(tb.getB(), 22);
        Assertions.assertEquals(tb.getC().getC1(), 223);
    }

    @Test
    public void testFromMap() {
    }

    @Test
    public void testFromMap1() {


    }

    private Map buildTestMap() {
        return MapBuilder.builder()
                .put("a", "12")
                .put("b", 22)
                .put("c", MapBuilder.builder().put("c1", 223).build()).build();
    }

    @Test
    public void fromMap_list() {
//        String str = "{list1: [{c1: 1}]}";
//        Map mm = (Map) ExpUtils.safeEval(DefaultExpEvaluator.newInstance(appCtx),str);
        Map mm = MapBuilder.builder().put("list1", Arrays.asList(
                MapBuilder.builder().put("c1", 1).build()
        )).build();
        TestList tb = Map2BeanUtils.fromMap(mm, TestList.class);

        Assertions.assertEquals(tb.getList1().size(), 1);
        Assertions.assertEquals(tb.getList1().get(0).c1, 1);
    }

    @Data
    public static class TestBean {
        String a;

        int b;

        TestBeanC c;

    }

    @Data
    public static class TestBeanC {
        int c1;
    }

    @Data
    public static class TestList {
        int c1;
        List<TestBeanC> list1;
    }
}