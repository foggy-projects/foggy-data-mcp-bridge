package com.foggyframework.dataset.db.model.preagg.config;

import com.foggyframework.dataset.db.model.preagg.refresh.PreAggRefreshService;
import com.foggyframework.dataset.db.model.preagg.scheduler.PreAggScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 预聚合模块自动配置
 * <p>
 * 通过 {@code foggy.preagg.enabled=true} 启用预聚合功能。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "foggy.preagg", name = "enabled", havingValue = "true", matchIfMissing = false)
@ComponentScan(basePackages = "com.foggyframework.dataset.db.model.preagg.controller")
public class PreAggAutoConfiguration {

    /**
     * 预聚合刷新服务
     */
    @Bean
    @ConditionalOnMissingBean
    public PreAggRefreshService preAggRefreshService() {
        log.info("Creating PreAggRefreshService bean");
        return new PreAggRefreshService();
    }

    /**
     * 预聚合调度器
     */
    @Bean
    @ConditionalOnMissingBean
    public PreAggScheduler preAggScheduler(TaskScheduler preAggTaskScheduler,
                                            PreAggRefreshService refreshService) {
        log.info("Creating PreAggScheduler bean");
        return new PreAggScheduler(preAggTaskScheduler, refreshService);
    }

    /**
     * 任务调度器（专用于预聚合）
     */
    @Bean
    @ConditionalOnMissingBean(name = "preAggTaskScheduler")
    public TaskScheduler preAggTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("preagg-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        log.info("Created pre-aggregation TaskScheduler with pool size 2");
        return scheduler;
    }
}
