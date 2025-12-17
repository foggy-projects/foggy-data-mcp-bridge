package com.foggyframework.fsscript.client;


import com.foggyframework.fsscript.client.proxy.FsscriptReturnConverterManagerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class FoggyFscriptClientAutoConfiguration {

    @Bean
    public FsscriptReturnConverterManagerImpl fsscriptReturnConverterManager() {
        return new FsscriptReturnConverterManagerImpl();
    }


}
