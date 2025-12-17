package com.foggyframework.core;

import com.foggyframework.core.annotates.EnableFoggyFramework;
import com.foggyframework.core.spring.bean.TestFoggyMethodFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFoggyFramework
public class FoggyFrameworkTestConfiguration {
    @Bean
    public TestFoggyMethodFilter testFoggyMethodFilter() {

        return new TestFoggyMethodFilter();
    }
}
