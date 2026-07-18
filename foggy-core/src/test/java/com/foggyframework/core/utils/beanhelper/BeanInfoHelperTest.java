package com.foggyframework.core.utils.beanhelper;

import lombok.Data;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

 class BeanInfoHelperTest {

     @Test
     void getClassHelper() throws InterruptedException {

        BeanInfoHelper bp =   BeanInfoHelper.getClassHelper(String[].class);

        Assertions.assertEquals(bp.getBeanProperty("length").getBeanValue(new String[]{"a"}),1);

        for (int iteration = 0; iteration < 100; iteration++) {
            String propertyName = "concurrent-" + iteration;
            BeanInfoHelper.MapBeanInfoHelper mapHelper =
                    new BeanInfoHelper.MapBeanInfoHelper(HashMap.class);
            BeanProperty created = mapHelper.getBeanProperty("created-" + iteration, false);
            Assertions.assertSame(
                    created,
                    mapHelper.getBeanProperty("created-" + iteration, false)
            );
            BeanProperty installed = new BeanInfoHelper.MapItemBeanProperty(propertyName);
            AtomicReference<BeanProperty> observed = new AtomicReference<>();
            Thread lookup = new Thread(
                    () -> observed.set(mapHelper.getBeanProperty(propertyName, false)),
                    "bean-info-map-double-check-" + iteration
            );

            synchronized (mapHelper) {
                lookup.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (lookup.getState() != Thread.State.BLOCKED
                        && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                Assertions.assertEquals(
                        Thread.State.BLOCKED,
                        lookup.getState(),
                        "lookup must complete its first miss before the cached value is installed"
                );
                mapHelper.mapBp.put(propertyName, installed);
            }

            lookup.join(TimeUnit.SECONDS.toMillis(5));
            Assertions.assertFalse(lookup.isAlive(), "lookup thread must terminate");
            Assertions.assertSame(installed, observed.get());
        }
    }

    @Test
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
     @Test
     void getComplexBeanProperty2() {
         BeanInfoHelper beanInfoHelper =   BeanInfoHelper.getClassHelper(ComplexTestBean.class);
         BeanProperty bp = beanInfoHelper.getComplexBeanProperty("l1.l2.l3.dd");
         ComplexTestBean bean = new ComplexTestBean();
         bp.setBeanValue(bean,2.0);

         Assertions.assertEquals(2.0, bean.getL1().getL2().l3.dd);
     }

     @Test
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
