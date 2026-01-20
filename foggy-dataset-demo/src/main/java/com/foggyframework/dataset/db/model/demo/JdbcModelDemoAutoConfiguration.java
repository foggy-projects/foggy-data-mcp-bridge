package com.foggyframework.dataset.db.model.demo;

import com.foggyframework.core.annotates.EnableFoggyFramework;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Demo 数据模型自动配置
 *
 * <p>通过配置 {@code foggy.demo.enabled=false} 可禁用此模块的加载。
 * 默认启用。
 */
@Configuration
@ConditionalOnProperty(name = "foggy.demo.enabled", havingValue = "true", matchIfMissing = true)
@EnableFoggyFramework(bundleName = "foggy-dataset-demo")
public class JdbcModelDemoAutoConfiguration {


}
