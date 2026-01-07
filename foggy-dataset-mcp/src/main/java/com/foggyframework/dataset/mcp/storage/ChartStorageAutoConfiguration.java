package com.foggyframework.dataset.mcp.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.Optional;

/**
 * 图表存储自动配置
 */
@Slf4j
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ChartStorageProperties.class)
@RequiredArgsConstructor
public class ChartStorageAutoConfiguration {

    private final ChartStorageProperties properties;

    /**
     * 主存储适配器
     * 根据配置选择合适的存储适配器
     */
    @Bean
    @Primary
    public ChartStorageAdapter primaryChartStorageAdapter(List<ChartStorageAdapter> adapters) {
        String configuredType = properties.getType();

        // 查找配置的存储类型
        Optional<ChartStorageAdapter> configured = adapters.stream()
                .filter(a -> a.getType().equals(configuredType))
                .findFirst();

        if (configured.isPresent()) {
            ChartStorageAdapter adapter = configured.get();
            log.info("Using chart storage adapter: {} ({})", adapter.getType(), adapter.getClass().getSimpleName());
            return adapter;
        }

        // 如果配置的类型不存在，尝试使用本地存储
        Optional<ChartStorageAdapter> local = adapters.stream()
                .filter(a -> "local".equals(a.getType()))
                .findFirst();

        if (local.isPresent()) {
            log.warn("Configured storage type '{}' not available, falling back to local storage", configuredType);
            return local.get();
        }

        throw new IllegalStateException("No chart storage adapter available. " +
                "Please configure foggy.chart.storage.type or ensure LocalChartStorageAdapter is enabled.");
    }
}
