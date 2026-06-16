package com.foggyframework.dataset.db.model.cache.pivot;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

final class StringRedisTemplatePivotOuterCacheStore implements PivotOuterCacheStringStore {

    private final StringRedisTemplate redisTemplate;

    StringRedisTemplatePivotOuterCacheStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, positiveTtl(ttl));
    }

    @Override
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    @Override
    public Set<String> members(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    @Override
    public void addMember(String key, String member, Duration ttl) {
        redisTemplate.opsForSet().add(key, member);
        redisTemplate.expire(key, positiveTtl(ttl));
    }

    @Override
    public void removeMember(String key, String member) {
        redisTemplate.opsForSet().remove(key, member);
    }

    private Duration positiveTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Duration.ofMillis(1L);
        }
        return ttl;
    }
}
