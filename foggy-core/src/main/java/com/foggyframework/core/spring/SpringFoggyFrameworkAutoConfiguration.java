
package com.foggyframework.core.spring;

import com.foggyframework.core.spring.bean.FoggyMethodFilterBuilder;
import com.foggyframework.core.spring.bean.ICGLibProxy;
import com.foggyframework.core.spring.proxy.SpringCGLibProxy;
import com.foggyframework.core.spring.proxy.SpringFoggyBeanPostProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

@Configuration
@Slf4j
public class SpringFoggyFrameworkAutoConfiguration {

    @Bean
//    @ConditionalOnMissingClass("net.sf.cglib.proxy.Factory")
    public SpringFoggyBeanPostProcessor springFoggyBeanPostProcessor(List<FoggyMethodFilterBuilder> list, ICGLibProxy cglibProxy, ApplicationContext appCtx) {
//        log.warn("缺失类:net.sf.cglib.proxy.Factory,我们使用spring提供的cglib");
        Collections.sort(list);
        return new SpringFoggyBeanPostProcessor(list, cglibProxy, appCtx);
    }

    @Bean
    @ConditionalOnBean(name = "springFoggyBeanPostProcessor")
    public SpringCGLibProxy springCGLibProxy() {
        return new SpringCGLibProxy();
    }

}
