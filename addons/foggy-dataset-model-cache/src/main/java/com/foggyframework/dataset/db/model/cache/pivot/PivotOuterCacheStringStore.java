package com.foggyframework.dataset.db.model.cache.pivot;

import java.time.Duration;
import java.util.Set;

interface PivotOuterCacheStringStore {

    String get(String key);

    void set(String key, String value, Duration ttl);

    boolean delete(String key);

    Set<String> members(String key);

    void addMember(String key, String member, Duration ttl);

    void removeMember(String key, String member);
}
