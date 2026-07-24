package io.foggytest.autoconfigure.cache;

import com.foggyframework.dataset.model.cache.config.QueryCacheAutoConfiguration;
import com.foggyframework.dataset.model.cache.config.QueryCacheEvictionAutoConfiguration;
import com.foggyframework.dataset.model.cache.config.QueryCacheWebAutoConfiguration;
import com.foggyframework.dataset.model.cache.controller.QueryCacheController;
import com.foggyframework.dataset.model.cache.fingerprint.QueryFingerprintBuilder;
import com.foggyframework.dataset.model.cache.provider.CaffeineQueryCacheProvider;
import com.foggyframework.dataset.model.cache.provider.RedisQueryCacheProvider;
import com.foggyframework.dataset.model.spi.QueryCacheProvider;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QueryCacheAutoConfigurationContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(QueryCacheAutoConfiguration.class));

    @Test
    void disabledConfigurationCreatesNoCacheInfrastructure() {
        contextRunner
                .withPropertyValues("foggy.query-cache.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(QueryCacheProvider.class);
                    assertThat(context).doesNotHaveBean(QueryFingerprintBuilder.class);
                    assertThat(context).doesNotHaveBean(QueryCacheController.class);
                });
    }

    @Test
    void missingRedisConnectionFactoryBacksOffWithoutLeavingBuilderBehind() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RedisQueryCacheProvider.class);
            assertThat(context).doesNotHaveBean(QueryFingerprintBuilder.class);
        });
    }

    @Test
    void missingRedisDependencySkipsTheWholeProviderBranch() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.data.redis"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RedisQueryCacheProvider.class);
                    assertThat(context).doesNotHaveBean(QueryFingerprintBuilder.class);
                });
    }

    @Test
    void missingCaffeineDependencySkipsTheWholeProviderBranch() {
        contextRunner
                .withPropertyValues("foggy.query-cache.type=caffeine")
                .withClassLoader(new FilteredClassLoader(Caffeine.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CaffeineQueryCacheProvider.class);
                    assertThat(context).doesNotHaveBean(QueryFingerprintBuilder.class);
                });
    }

    @Test
    void caffeineConditionsCreateExactlyOneProviderAndBuilder() {
        contextRunner
                .withPropertyValues("foggy.query-cache.type=caffeine")
                .run(context -> {
                    assertThat(context).hasSingleBean(QueryCacheProvider.class);
                    assertThat(context).hasSingleBean(CaffeineQueryCacheProvider.class);
                    assertThat(context).hasSingleBean(QueryFingerprintBuilder.class);
                });
    }

    @Test
    void redisConditionsCreateExactlyOneProviderAndBuilder() {
        contextRunner
                .withBean(RedisConnectionFactory.class,
                        () -> mock(RedisConnectionFactory.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(QueryCacheProvider.class);
                    assertThat(context).hasSingleBean(RedisQueryCacheProvider.class);
                    assertThat(context).hasSingleBean(QueryFingerprintBuilder.class);
                    assertThat(context).hasSingleBean(RedisTemplate.class);
                    RedisTemplate<?, ?> template = context.getBean(
                            "foggyQueryCacheRedisTemplate", RedisTemplate.class);
                    assertThat(template.getKeySerializer())
                            .isInstanceOf(StringRedisSerializer.class);
                    assertThat(template.getValueSerializer())
                            .isInstanceOf(GenericJackson2JsonRedisSerializer.class);
                });
    }

    @Test
    void userProviderBacksOffDefaultProviderAndBuilder() {
        QueryCacheProvider customProvider = mock(QueryCacheProvider.class);

        contextRunner
                .withPropertyValues("foggy.query-cache.type=caffeine")
                .withBean(QueryCacheProvider.class, () -> customProvider)
                .run(context -> {
                    assertThat(context).hasSingleBean(QueryCacheProvider.class);
                    assertThat(context.getBean(QueryCacheProvider.class)).isSameAs(customProvider);
                    assertThat(context).doesNotHaveBean(CaffeineQueryCacheProvider.class);
                    assertThat(context).doesNotHaveBean(QueryFingerprintBuilder.class);
                });
    }

    @Test
    void userBuilderBacksOffDefaultBuilderAndFeedsTheDefaultProvider() {
        QueryFingerprintBuilder customBuilder = mock(QueryFingerprintBuilder.class);

        contextRunner
                .withPropertyValues("foggy.query-cache.type=caffeine")
                .withBean(QueryFingerprintBuilder.class, () -> customBuilder)
                .run(context -> {
                    assertThat(context).hasSingleBean(QueryFingerprintBuilder.class);
                    assertThat(context.getBean(QueryFingerprintBuilder.class)).isSameAs(customBuilder);
                    assertThat(context).hasSingleBean(CaffeineQueryCacheProvider.class);
                });
    }

    @Test
    void apiControllerRequiresAProvider() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        QueryCacheAutoConfiguration.class,
                        QueryCacheWebAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(QueryCacheController.class);
                });
    }

    @Test
    void apiControllerIsCreatedOnceWhenProviderIsAvailable() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        QueryCacheAutoConfiguration.class,
                        QueryCacheWebAutoConfiguration.class))
                .withPropertyValues("foggy.query-cache.type=caffeine")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(QueryCacheProvider.class);
                    assertThat(context).hasSingleBean(QueryCacheController.class);
                });
    }

    @Test
    void disabledApiCreatesNoControllerEvenWhenProviderIsAvailable() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        QueryCacheAutoConfiguration.class,
                        QueryCacheWebAutoConfiguration.class))
                .withPropertyValues(
                        "foggy.query-cache.type=caffeine",
                        "foggy.query-cache.api.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(QueryCacheProvider.class);
                    assertThat(context).doesNotHaveBean(QueryCacheController.class);
                });
    }

    @Test
    void userControllerBacksOffDefaultController() {
        QueryCacheController customController = mock(QueryCacheController.class);
        QueryCacheProvider customProvider = mock(QueryCacheProvider.class);

        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        QueryCacheAutoConfiguration.class,
                        QueryCacheWebAutoConfiguration.class))
                .withPropertyValues("foggy.query-cache.type=caffeine")
                .withBean(QueryCacheProvider.class, () -> customProvider)
                .withBean("customQueryCacheController", QueryCacheController.class, () -> customController)
                .run(context -> {
                    assertThat(context).hasSingleBean(QueryCacheController.class);
                    assertThat(context.getBean(QueryCacheController.class)).isSameAs(customController);
                });
    }

    @Test
    void missingWebDependencySkipsControllerWithoutLinkageFailure() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        QueryCacheAutoConfiguration.class,
                        QueryCacheWebAutoConfiguration.class))
                .withPropertyValues("foggy.query-cache.type=caffeine")
                .withClassLoader(new FilteredClassLoader("org.springframework.web"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(QueryCacheProvider.class);
                    assertThat(context).doesNotHaveBean("queryCacheController");
                });
    }

    @Test
    void missingAspectjDependencySkipsEvictionWithoutLinkageFailure() {
        contextRunner
                .withConfiguration(AutoConfigurations.of(QueryCacheEvictionAutoConfiguration.class))
                .withPropertyValues("foggy.query-cache.type=caffeine")
                .withClassLoader(new FilteredClassLoader("org.aspectj"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(QueryCacheProvider.class);
                    assertThat(context).doesNotHaveBean("cacheEvictionAspect");
                });
    }
}
