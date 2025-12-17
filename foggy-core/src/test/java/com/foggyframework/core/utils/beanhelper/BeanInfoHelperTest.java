package com.foggyframework.core.utils.beanhelper;

import lombok.Data;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import static org.junit.Assert.*;

 class BeanInfoHelperTest {

     @org.junit.jupiter.api.Test
     void getClassHelper() {

        BeanInfoHelper bp =   BeanInfoHelper.getClassHelper(String[].class);

        Assertions.assertEquals(bp.getBeanProperty("length").getBeanValue(new String[]{"a"}),1);
    }

    @org.junit.jupiter.api.Test
    void getComplexBeanProperty() {
        BeanInfoHelper beanInfoHelper =   BeanInfoHelper.getClassHelper(ComplexTestBean.class);
        BeanProperty bp = beanInfoHelper.getComplexBeanProperty("l1.l2.l3.testAbc");
        ComplexTestBean bean = new ComplexTestBean();
        bp.setBeanValue(bean,"a");

       Assertions.assertEquals("a", bean.getL1().getL2().l3.testAbc);
    }

     /**
      * 测试基本对象
      */
     @org.junit.jupiter.api.Test
     void getComplexBeanProperty2() {
         BeanInfoHelper beanInfoHelper =   BeanInfoHelper.getClassHelper(ComplexTestBean.class);
         BeanProperty bp = beanInfoHelper.getComplexBeanProperty("l1.l2.l3.dd");
         ComplexTestBean bean = new ComplexTestBean();
         bp.setBeanValue(bean,2.0);

         Assertions.assertEquals(2.0, bean.getL1().getL2().l3.dd);
     }

     @org.junit.jupiter.api.Test
     void copyProperties() {

         SS ss = new SS();
         TT tt = new TT();

         ss.d2=2;
         BeanInfoHelper.copyProperties(ss,tt);
         Assertions.assertEquals(2,tt.d2);
         Assertions.assertEquals(0,tt.dd);
     }

     @Data
     public static class SS{
         Integer dd;
         Integer d2;
     }

     @Data
     public static class TT{
         int dd;
         int d2;
     }
}