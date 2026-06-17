package com.foggyframework.dataset.db.model.cache.pivot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Objects;

@Slf4j
public final class RedisPivotOuterCacheInvalidationListenerLifecycle implements SmartLifecycle {

    private final RedisMessageListenerContainer container;
    private final boolean autoStartup;
    private volatile boolean running;

    public RedisPivotOuterCacheInvalidationListenerLifecycle(
            RedisMessageListenerContainer container,
            boolean autoStartup) {
        this.container = Objects.requireNonNull(container, "container must not be null");
        this.autoStartup = autoStartup;
    }

    @Override
    public void start() {
        if (running || container.isRunning()) {
            running = true;
            return;
        }
        try {
            container.start();
            running = container.isRunning();
        } catch (RuntimeException e) {
            running = false;
            log.warn("Redis Pivot outer-cache invalidation listener did not start: {}", errorSummary(e));
        }
    }

    @Override
    public void stop() {
        try {
            container.stop();
        } catch (RuntimeException e) {
            log.warn("Redis Pivot outer-cache invalidation listener did not stop cleanly: {}", errorSummary(e));
        } finally {
            running = false;
        }
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running && container.isRunning();
    }

    @Override
    public boolean isAutoStartup() {
        return autoStartup;
    }

    @Override
    public int getPhase() {
        return container.getPhase();
    }

    private String errorSummary(RuntimeException e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
