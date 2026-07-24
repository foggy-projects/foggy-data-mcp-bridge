package com.foggyframework.dataset.model.impl.vector;

import io.milvus.v2.client.MilvusClientV2;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;

/**
 * Milvus 客户端连接池
 * <p>
 * 基于 Apache Commons Pool2 实现的 Milvus 客户端连接池，
 * 用于管理和复用 Milvus 连接，提高性能。
 *
 * @author foggy-dataset
 * @since 2.0.0
 */
@Slf4j
public class MilvusClientPool implements AutoCloseable {

    private final GenericObjectPool<MilvusClientV2> pool;
    private final VectorDbConfig config;

    /**
     * 创建连接池
     *
     * @param config 向量数据库配置
     */
    public MilvusClientPool(VectorDbConfig config) {
        this.config = config;

        MilvusClientFactory factory = new MilvusClientFactory(config);

        GenericObjectPoolConfig<MilvusClientV2> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(config.getPoolSize());
        poolConfig.setMaxIdle(config.getPoolSize());
        poolConfig.setMinIdle(1);
        poolConfig.setMaxWait(Duration.ofMillis(config.getPoolMaxWaitMs()));
        poolConfig.setTestOnBorrow(false);
        poolConfig.setTestOnReturn(false);
        poolConfig.setTestWhileIdle(false);
        poolConfig.setBlockWhenExhausted(true);

        // JMX 监控（可选）
        poolConfig.setJmxEnabled(false);

        this.pool = new GenericObjectPool<>(factory, poolConfig);

        log.info("MilvusClientPool initialized: poolSize={}, maxWait={}ms",
                config.getPoolSize(), config.getPoolMaxWaitMs());
    }

    /**
     * 从连接池借用客户端
     *
     * @return Milvus 客户端
     * @throws Exception 如果无法获取客户端
     */
    public MilvusClientV2 borrowClient() throws Exception {
        MilvusClientV2 client = pool.borrowObject();
        log.debug("Borrowed Milvus client from pool, active={}, idle={}",
                pool.getNumActive(), pool.getNumIdle());
        return client;
    }

    /**
     * 归还客户端到连接池
     *
     * @param client Milvus 客户端
     */
    public void returnClient(MilvusClientV2 client) {
        if (client != null) {
            pool.returnObject(client);
            log.debug("Returned Milvus client to pool, active={}, idle={}",
                    pool.getNumActive(), pool.getNumIdle());
        }
    }

    /**
     * 使无效的客户端失效
     *
     * @param client Milvus 客户端
     */
    public void invalidateClient(MilvusClientV2 client) {
        if (client != null) {
            try {
                pool.invalidateObject(client);
                log.debug("Invalidated Milvus client");
            } catch (Exception e) {
                log.warn("Error invalidating Milvus client: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取连接池状态信息
     *
     * @return 状态信息字符串
     */
    public String getPoolStatus() {
        return String.format("active=%d, idle=%d, maxTotal=%d",
                pool.getNumActive(), pool.getNumIdle(), pool.getMaxTotal());
    }

    /**
     * 获取活跃连接数
     *
     * @return 活跃连接数
     */
    public int getNumActive() {
        return pool.getNumActive();
    }

    /**
     * 获取空闲连接数
     *
     * @return 空闲连接数
     */
    public int getNumIdle() {
        return pool.getNumIdle();
    }

    /**
     * 关闭连接池
     */
    @Override
    public void close() {
        pool.close();
        log.info("MilvusClientPool closed");
    }

    /**
     * 执行带连接池管理的操作
     *
     * @param action 要执行的操作
     * @param <T>    返回值类型
     * @return 操作结果
     * @throws Exception 如果操作失败
     */
    public <T> T execute(MilvusClientAction<T> action) throws Exception {
        MilvusClientV2 client = null;
        try {
            client = borrowClient();
            return action.execute(client);
        } catch (Exception e) {
            // 发生异常时使连接失效
            if (client != null) {
                invalidateClient(client);
                client = null;  // 防止在 finally 中再次归还
            }
            throw e;
        } finally {
            if (client != null) {
                returnClient(client);
            }
        }
    }

    /**
     * Milvus 客户端操作接口
     *
     * @param <T> 返回值类型
     */
    @FunctionalInterface
    public interface MilvusClientAction<T> {
        T execute(MilvusClientV2 client) throws Exception;
    }
}
