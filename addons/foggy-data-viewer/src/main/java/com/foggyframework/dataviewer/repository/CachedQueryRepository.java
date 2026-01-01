package com.foggyframework.dataviewer.repository;

import com.foggyframework.dataviewer.domain.CachedQueryContext;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * 缓存查询仓库
 */
@Repository
public interface CachedQueryRepository extends MongoRepository<CachedQueryContext, String> {

    /**
     * 根据queryId查找未过期的查询上下文
     *
     * @param queryId 查询ID
     * @param now     当前时间
     * @return 查询上下文
     */
    Optional<CachedQueryContext> findByQueryIdAndExpiresAtAfter(String queryId, Instant now);

    /**
     * 删除过期的查询
     *
     * @param cutoff 截止时间
     */
    void deleteByExpiresAtBefore(Instant cutoff);
}
