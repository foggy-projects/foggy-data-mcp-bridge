package com.foggyframework.dataset.db.model.impl.vector;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 * Milvus 客户端工厂
 * <p>
 * 用于 Apache Commons Pool2 创建和管理 Milvus 客户端实例。
 *
 * @author foggy-dataset
 * @since 2.0.0
 */
@Slf4j
public class MilvusClientFactory extends BasePooledObjectFactory<MilvusClientV2> {

    private final VectorDbConfig config;

    public MilvusClientFactory(VectorDbConfig config) {
        this.config = config;
    }

    @Override
    public MilvusClientV2 create() throws Exception {
        String uri = String.format("http://%s:%d", config.getHost(), config.getPort());

        ConnectConfig.ConnectConfigBuilder connectBuilder = ConnectConfig.builder()
                .uri(uri)
                .connectTimeoutMs(config.getConnectTimeoutMs());

        if (config.getDatabase() != null && !config.getDatabase().isEmpty()) {
            connectBuilder.dbName(config.getDatabase());
        }
        if (config.getUsername() != null && !config.getUsername().isEmpty()) {
            connectBuilder.token(config.getUsername() + ":" + config.getPassword());
        }

        MilvusClientV2 client = new MilvusClientV2(connectBuilder.build());
        log.debug("Created new Milvus client connection to {}", uri);
        return client;
    }

    @Override
    public PooledObject<MilvusClientV2> wrap(MilvusClientV2 client) {
        return new DefaultPooledObject<>(client);
    }

    @Override
    public void destroyObject(PooledObject<MilvusClientV2> p) throws Exception {
        MilvusClientV2 client = p.getObject();
        if (client != null) {
            try {
                client.close();
                log.debug("Destroyed Milvus client connection");
            } catch (Exception e) {
                log.warn("Error closing Milvus client: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean validateObject(PooledObject<MilvusClientV2> p) {
        // Milvus SDK 没有直接的 ping 方法，返回 true 信任连接有效
        // 实际使用时如果连接断开，会在查询时抛出异常
        return p.getObject() != null;
    }

    @Override
    public void passivateObject(PooledObject<MilvusClientV2> p) throws Exception {
        // 归还连接时的清理操作（如果需要）
    }

    @Override
    public void activateObject(PooledObject<MilvusClientV2> p) throws Exception {
        // 获取连接时的激活操作（如果需要）
    }
}
