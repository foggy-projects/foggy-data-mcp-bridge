package com.foggyframework.dataset.db.model.memorygrid.bridge;

import com.foggyframework.dataset.db.model.DbModelAutoConfiguration;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = DbModelAutoConfiguration.class)
@ConditionalOnProperty(prefix = "foggy.memory-grid", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MemoryGridBridgeConfiguration {

    @Bean
    @ConditionalOnMissingBean(MemoryGridEngine.class)
    public MemoryGridEngine memoryGridEngine() {
        return new BridgeMemoryGridEngine();
    }
}
