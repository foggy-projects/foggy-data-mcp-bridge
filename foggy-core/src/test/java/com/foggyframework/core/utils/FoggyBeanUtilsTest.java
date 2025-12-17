package com.foggyframework.core.utils;

import com.foggyframework.core.common.MapBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FoggyBeanUtilsTest {

    @Test
    void apply() {
        TestApply test1 = new TestApply("1","2",null,"4",null);
        TestApply test2 = new TestApply("21","22",null,"24","25");
        TestApply test3 = new TestApply("31",null,"33","34",null);

        TestApply xx = (TestApply) FoggyBeanUtils.apply(test1,test2,test3);
        Assertions.assertEquals("31",xx.getA1());
        Assertions.assertEquals("22",xx.getA2());
        Assertions.assertEquals("33",xx.getA3());
        Assertions.assertEquals("34",xx.getA4());
        Assertions.assertEquals("25",xx.getA5());
    }

    @Test
    void bean2SmString() {

        Map mm = MapBuilder.builder().put("turnoverBoxTypeId","turnoverBoxTypeId")
                .put("turnoverBoxTypeName","turnoverBoxTypeName")
                .put("boxLength","boxLength")
                .put("boxWidth","boxWidth").build();
        Map r = FoggyBeanUtils.bean2SmString(mm);
        Assertions.assertEquals("turnoverBoxTypeId",r.get("turnover_box_type_id"));
        Assertions.assertEquals("turnoverBoxTypeName",r.get("turnover_box_type_name"));
        Assertions.assertEquals("boxLength",r.get("box_length"));
        Assertions.assertEquals("boxWidth",r.get("box_width"));


        TestApply2 a2 = new TestApply2("turnoverBoxTypeId2","turnoverBoxTypeName2","boxLength2","boxWidth2");
        Map rr = FoggyBeanUtils.bean2SmString(a2);
        Assertions.assertEquals("turnoverBoxTypeId2",rr.get("turnover_box_type_id"));
        Assertions.assertEquals("turnoverBoxTypeName2",rr.get("turnover_box_type_name"));
        Assertions.assertEquals("boxLength2",rr.get("box_length"));
        Assertions.assertEquals("boxWidth2",rr.get("box_width"));
    }
    @Test
    void sm2BeanString() {

        Map mm = MapBuilder.builder().put("turnover_box_type_id","turnoverBoxTypeId")
                .put("turnover_box_type_name","turnoverBoxTypeName")
                .put("box_length","boxLength")
                .put("box_width","boxWidth").build();
        Map r = FoggyBeanUtils.sm2BeanString(mm);
        Assertions.assertEquals("turnoverBoxTypeId",r.get("turnoverBoxTypeId"));
        Assertions.assertEquals("turnoverBoxTypeName",r.get("turnoverBoxTypeName"));
        Assertions.assertEquals("boxLength",r.get("boxLength"));
        Assertions.assertEquals("boxWidth",r.get("boxWidth"));


        TestApply2 a2 = new TestApply2("turnoverBoxTypeId2","turnoverBoxTypeName2","boxLength2","boxWidth2");
        Map rr = FoggyBeanUtils.sm2BeanString(a2);
        Assertions.assertEquals("turnoverBoxTypeId2",rr.get("turnoverBoxTypeId"));
        Assertions.assertEquals("turnoverBoxTypeName2",rr.get("turnoverBoxTypeName"));
        Assertions.assertEquals("boxLength2",rr.get("boxLength"));
        Assertions.assertEquals("boxWidth2",rr.get("boxWidth"));
    }
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestApply {

        String a1;
        String a2;
        String a3;
        String a4;
        String a5;
    }
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestApply2 {

        String turnoverBoxTypeId;
        String turnoverBoxTypeName;
        String boxLength;
        String boxWidth;
    }
}