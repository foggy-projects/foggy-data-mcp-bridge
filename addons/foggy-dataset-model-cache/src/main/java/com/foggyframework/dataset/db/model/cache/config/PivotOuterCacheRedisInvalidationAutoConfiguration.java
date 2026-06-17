package com.foggyframework.dataset.db.model.cache.config;

import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheInvalidationBroadcaster;
import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheInvalidationCodec;
import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheInvalidationConsumer;
import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheInvalidationListenerLifecycle;
import com.foggyframework.dataset.db.model.cache.pivot.RedisPivotOuterCacheInvalidationMessageListener;
import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationBroadcaster;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Slf4j
@Configuration
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
@AutoConfigureBefore(name = "com.foggyframework.dataset.db.model.DbModelAutoConfiguration")
@EnableConfigurationProperties(PivotOuterCacheRedisProperties.class)
@ConditionalOnProperty(name = "foggy.dataset.pivot.outer-cache.redis.invalidation-enabled", havingValue = "true")
public class PivotOuterCacheRedisInvalidationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisPivotOuterCacheInvalidationCodec redisPivotOuterCacheInvalidationCodec() {
        return new RedisPivotOuterCacheInvalidationCodec();
    }

    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(PivotOuterCacheInvalidationBroadcaster.class)
    public RedisPivotOuterCacheInvalidationBroadcaster redisPivotOuterCacheInvalidationBroadcaster(
            StringRedisTemplate redisTemplate,
            ObjectProvider<SemanticQueryServiceV3> semanticQueryServiceProvider,
            RedisPivotOuterCacheInvalidationCodec codec,
            PivotOuterCacheRedisProperties redisProperties) {
        log.info("Initializing Redis Pivot outer-cache invalidation broadcaster, channel={}, nodeId={}",
                redisProperties.resolvedInvalidationChannel(), redisProperties.resolvedNodeId());
        return new RedisPivotOuterCacheInvalidationBroadcaster(
                redisTemplate,
                semanticQueryServiceProvider,
                codec,
                redisProperties.resolvedInvalidationChannel(),
                redisProperties.resolvedNodeId());
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisPivotOuterCacheInvalidationConsumer redisPivotOuterCacheInvalidationConsumer(
            ObjectProvider<SemanticQueryServiceV3> semanticQueryServiceProvider,
            RedisPivotOuterCacheInvalidationCodec codec,
            PivotOuterCacheRedisProperties redisProperties) {
        return new RedisPivotOuterCacheInvalidationConsumer(
                semanticQueryServiceProvider,
                codec,
                redisProperties.resolvedNodeId(),
                redisProperties.resolvedReplayWindowMillis(),
                redisProperties.resolvedReplayWindowMaximumEntries());
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisPivotOuterCacheInvalidationMessageListener redisPivotOuterCacheInvalidationMessageListener(
            RedisPivotOuterCacheInvalidationConsumer consumer) {
        return new RedisPivotOuterCacheInvalidationMessageListener(consumer);
    }

    @Bean
    @ConditionalOnClass(RedisMessageListenerContainer.class)
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = "redisPivotOuterCacheInvalidationListenerContainer")
    @ConditionalOnProperty(
            name = "foggy.dataset.pivot.outer-cache.redis.invalidation-listener-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public RedisMessageListenerContainer redisPivotOuterCacheInvalidationListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            RedisPivotOuterCacheInvalidationMessageListener messageListener,
            PivotOuterCacheRedisProperties redisProperties) {
        RedisMessageListenerContainer container = new ConfigurableAutoStartupRedisMessageListenerContainer(false);
        container.setConnectionFactory(redisConnectionFactory);
        container.setRecoveryInterval(redisProperties.resolvedInvalidationListenerRecoveryIntervalMillis());
        container.setErrorHandler(error -> log.warn(
                "Redis Pivot outer-cache invalidation listener error: {}",
                error.getMessage()));
        container.addMessageListener(
                messageListener,
                new ChannelTopic(redisProperties.resolvedInvalidationChannel()));
        log.info("Initializing Redis Pivot outer-cache invalidation listener, channel={}, autoStartup={}",
                redisProperties.resolvedInvalidationChannel(),
                redisProperties.isInvalidationListenerAutoStartup());
        return container;
    }

    @Bean
    @ConditionalOnBean(name = "redisPivotOuterCacheInvalidationListenerContainer")
    @ConditionalOnMissingBean
    public RedisPivotOuterCacheInvalidationListenerLifecycle redisPivotOuterCacheInvalidationListenerLifecycle(
            @Qualifier("redisPivotOuterCacheInvalidationListenerContainer")
                    RedisMessageListenerContainer redisPivotOuterCacheInvalidationListenerContainer,
            PivotOuterCacheRedisProperties redisProperties) {
        return new RedisPivotOuterCacheInvalidationListenerLifecycle(
                redisPivotOuterCacheInvalidationListenerContainer,
                redisProperties.isInvalidationListenerAutoStartup());
    }

    private static final class ConfigurableAutoStartupRedisMessageListenerContainer
            extends RedisMessageListenerContainer {

        private final boolean autoStartup;

        private ConfigurableAutoStartupRedisMessageListenerContainer(boolean autoStartup) {
            this.autoStartup = autoStartup;
        }

        @Override
        public boolean isAutoStartup() {
            return autoStartup;
        }
    }
}
