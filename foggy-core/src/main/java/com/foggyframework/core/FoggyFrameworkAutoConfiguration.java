package com.foggyframework.core;


import com.foggyframework.core.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class FoggyFrameworkAutoConfiguration {
    @Bean
    public DateUtils dateUtils() {
        return new DateUtils();
    }

    @Bean
    public FileUtils fileUtils() {
        return new FileUtils();
    }

    @Bean
    public NumberUtils numberUtils() {
        return new NumberUtils();
    }


    @Bean
    @ConditionalOnMissingBean(name = "stringUtils")
    public StringUtils stringUtils() {
        return new StringUtils();
    }


}
