package com.foggyframework.dataset.model.impl.vector;

import com.foggyframework.dataset.model.impl.model.TableModelSupport;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;
import lombok.Setter;

/**
 * 向量数据库表模型实现
 * <p>
 * 用于支持 Milvus 等向量数据库的相似度检索。
 * 不支持聚合等复杂特性，专注于向量相似度搜索场景。
 */
@Getter
@Setter
public class VectorTableModelImpl extends TableModelSupport {

    /**
     * FSScript 脚本对象
     */
    private Fsscript fScript;

    /**
     * 向量数据库连接配置
     */
    private VectorDbConfig vectorDbConfig;

    /**
     * 集合名称（对应 Milvus 的 collection）
     */
    private String collectionName;

    /**
     * 向量字段名称
     */
    private String vectorFieldName;

    /**
     * 向量字段元数据（从 Milvus 自动发现）
     */
    private VectorFieldMetadata vectorFieldMetadata;

    /**
     * 向量维度
     */
    private int vectorDimensions;

    /**
     * 相似度度量类型: cosine, euclidean, dotProduct
     */
    private String metricType = "cosine";

    public VectorTableModelImpl() {
    }

    public VectorTableModelImpl(VectorDbConfig vectorDbConfig, Fsscript fScript) {
        this.vectorDbConfig = vectorDbConfig;
        this.fScript = fScript;
    }
}
