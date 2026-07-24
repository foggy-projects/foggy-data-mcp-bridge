package com.foggyframework.dataset.model.impl.vector;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Milvus 元数据服务
 * <p>
 * 从 Milvus 自动发现集合的元数据信息，包括：
 * <ul>
 *   <li>向量字段的维度</li>
 *   <li>索引类型和参数</li>
 *   <li>距离度量类型</li>
 *   <li>集合加载状态</li>
 * </ul>
 *
 * @author foggy-dataset
 * @since 2.0.0
 */
@Slf4j
public class MilvusMetadataService {

    private final VectorDbConfig config;
    private MilvusClientV2 client;

    public MilvusMetadataService(VectorDbConfig config) {
        this.config = config;
    }

    /**
     * 获取指定集合中向量字段的元数据
     *
     * @param collectionName 集合名称
     * @param fieldName      向量字段名
     * @return 向量字段元数据，如果字段不存在或不是向量字段则返回 null
     */
    public VectorFieldMetadata getVectorFieldMetadata(String collectionName, String fieldName) {
        try {
            initClient();

            // 获取集合描述
            DescribeCollectionResp collectionResp = client.describeCollection(
                    DescribeCollectionReq.builder()
                            .collectionName(collectionName)
                            .build()
            );

            // 查找向量字段
            for (var field : collectionResp.getCollectionSchema().getFieldSchemaList()) {
                if (field.getName().equals(fieldName) && isVectorType(field.getDataType())) {
                    return buildVectorFieldMetadata(collectionName, collectionResp, field);
                }
            }

            log.warn("Field '{}' not found or is not a vector field in collection '{}'", fieldName, collectionName);
            return null;

        } catch (Exception e) {
            log.error("Failed to get vector field metadata for {}.{}", collectionName, fieldName, e);
            return null;
        }
    }

    /**
     * 自动发现集合中的所有向量字段
     *
     * @param collectionName 集合名称
     * @return 向量字段元数据列表
     */
    public List<VectorFieldMetadata> discoverVectorFields(String collectionName) {
        List<VectorFieldMetadata> result = new ArrayList<>();

        try {
            initClient();

            // 获取集合描述
            DescribeCollectionResp collectionResp = client.describeCollection(
                    DescribeCollectionReq.builder()
                            .collectionName(collectionName)
                            .build()
            );

            // 遍历所有字段，找出向量字段
            for (var field : collectionResp.getCollectionSchema().getFieldSchemaList()) {
                if (isVectorType(field.getDataType())) {
                    VectorFieldMetadata metadata = buildVectorFieldMetadata(collectionName, collectionResp, field);
                    if (metadata != null) {
                        result.add(metadata);
                    }
                }
            }

            log.debug("Discovered {} vector fields in collection '{}'", result.size(), collectionName);
            return result;

        } catch (Exception e) {
            log.error("Failed to discover vector fields in collection '{}'", collectionName, e);
            return result;
        }
    }

    /**
     * 获取集合的第一个向量字段元数据
     *
     * @param collectionName 集合名称
     * @return 第一个向量字段的元数据，如果没有向量字段则返回 null
     */
    public VectorFieldMetadata getFirstVectorField(String collectionName) {
        List<VectorFieldMetadata> fields = discoverVectorFields(collectionName);
        return fields.isEmpty() ? null : fields.get(0);
    }

    /**
     * 检查集合是否存在
     *
     * @param collectionName 集合名称
     * @return 是否存在
     */
    public boolean collectionExists(String collectionName) {
        try {
            initClient();
            return client.hasCollection(
                    io.milvus.v2.service.collection.request.HasCollectionReq.builder()
                            .collectionName(collectionName)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to check collection existence: {}", collectionName, e);
            return false;
        }
    }

    /**
     * 检查集合是否已加载到内存
     *
     * @param collectionName 集合名称
     * @return 是否已加载
     */
    public boolean isCollectionLoaded(String collectionName) {
        try {
            initClient();
            return client.getLoadState(
                    GetLoadStateReq.builder()
                            .collectionName(collectionName)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to check collection load state: {}", collectionName, e);
            return false;
        }
    }

    /**
     * 构建向量字段元数据
     */
    private VectorFieldMetadata buildVectorFieldMetadata(
            String collectionName,
            DescribeCollectionResp collectionResp,
            CreateCollectionReq.FieldSchema field) {

        VectorFieldMetadata.VectorFieldMetadataBuilder builder = VectorFieldMetadata.builder()
                .collectionName(collectionName)
                .fieldName(field.getName())
                .dimension(field.getDimension())
                .vectorType(field.getDataType().name());

        // 查找主键字段
        for (var f : collectionResp.getCollectionSchema().getFieldSchemaList()) {
            if (f.getIsPrimaryKey()) {
                builder.primaryKeyField(f.getName());
                break;
            }
        }

        // 获取索引信息
        try {
            DescribeIndexResp indexResp = client.describeIndex(
                    DescribeIndexReq.builder()
                            .collectionName(collectionName)
                            .fieldName(field.getName())
                            .build()
            );

            if (indexResp != null && indexResp.getIndexDescriptions() != null
                    && !indexResp.getIndexDescriptions().isEmpty()) {
                var indexDesc = indexResp.getIndexDescriptions().get(0);
                builder.indexed(true)
                        .indexType(indexDesc.getIndexType() != null ? indexDesc.getIndexType().name() : null)
                        .metricType(indexDesc.getMetricType() != null ? indexDesc.getMetricType().name() : null);

                // 提取索引参数
                Map<String, Object> indexParams = new HashMap<>();
                if (indexDesc.getExtraParams() != null) {
                    indexParams.putAll(indexDesc.getExtraParams());
                }
                builder.indexParams(indexParams);
            } else {
                builder.indexed(false);
            }
        } catch (Exception e) {
            log.debug("No index found for field '{}.{}': {}", collectionName, field.getName(), e.getMessage());
            builder.indexed(false);
        }

        // 检查加载状态
        builder.loaded(isCollectionLoaded(collectionName));

        return builder.build();
    }

    /**
     * 判断数据类型是否为向量类型
     */
    private boolean isVectorType(DataType dataType) {
        return dataType == DataType.FloatVector
                || dataType == DataType.BinaryVector
                || dataType == DataType.Float16Vector
                || dataType == DataType.BFloat16Vector
                || dataType == DataType.SparseFloatVector;
    }

    /**
     * 初始化 Milvus 客户端
     */
    private synchronized void initClient() {
        if (client != null) {
            return;
        }

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

        client = new MilvusClientV2(connectBuilder.build());
        log.debug("MilvusMetadataService connected to {}", uri);
    }

    /**
     * 关闭客户端连接
     */
    public void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
