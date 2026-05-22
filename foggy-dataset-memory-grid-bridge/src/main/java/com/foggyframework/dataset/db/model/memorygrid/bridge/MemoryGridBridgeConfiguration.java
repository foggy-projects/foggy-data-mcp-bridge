package com.foggyframework.dataset.db.model.memorygrid.bridge;

import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class MemoryGridBridgeConfiguration {

    @Bean
    @ConditionalOnMissingBean(MemoryGridEngine.class)
    public MemoryGridEngine memoryGridEngine() {
        return new BridgeMemoryGridEngine();
    }
}
