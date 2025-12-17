package com.foggyframework.core.utils.beanhelper;

import lombok.Data;

@Data
public class ComplexTestBean {

    ComplexTestBeanL1 l1;

    @Data
    public static class ComplexTestBeanL1{
        ComplexTestBeanL2 l2;
    }

    @Data
    public static class ComplexTestBeanL2{
        ComplexTestBeanL3 l3;
    }
    @Data
    public static class ComplexTestBeanL3{
        String testAbc;

        Integer test_1;

        double dd;
    }
}
