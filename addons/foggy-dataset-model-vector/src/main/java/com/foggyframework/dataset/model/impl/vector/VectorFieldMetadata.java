package com.foggyframework.dataset.model.impl.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 向量字段元数据
 * <p>
 * 存储从 Milvus 自动发现的向量字段信息，包括维度、索引类型、距离度量等。
 *
 * @author foggy-dataset
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorFieldMetadata {

    /**
     * 字段名
     */
    private String fieldName;

    /**
     * 向量维度
     */
    private int dimension;

    /**
     * 索引类型: IVF_FLAT, IVF_SQ8, HNSW, AUTOINDEX 等
     */
    private String indexType;

    /**
     * 距离度量: COSINE, L2, IP
     */
    private String metricType;

    /**
     * 向量数据类型: FloatVector, BinaryVector, Float16Vector, BFloat16Vector, SparseFloatVector
     */
    private String vectorType;

    /**
     * 索引参数（如 nlist, M, efConstruction 等）
     */
    private Map<String, Object> indexParams;

    /**
     * 是否已建立索引
     */
    private boolean indexed;

    /**
     * 集合名称
     */
    private String collectionName;

    /**
     * 主键字段名
     */
    private String primaryKeyField;

    /**
     * 是否已加载到内存
     */
    private boolean loaded;
}
