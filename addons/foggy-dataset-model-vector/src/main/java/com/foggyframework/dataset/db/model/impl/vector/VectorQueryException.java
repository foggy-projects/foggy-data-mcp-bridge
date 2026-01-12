package com.foggyframework.dataset.db.model.impl.vector;

import lombok.Getter;

/**
 * 向量查询异常
 * <p>
 * 向量数据库操作相关的异常，包含错误码便于问题定位。
 *
 * @author foggy-dataset
 * @since 2.0.0
 */
@Getter
public class VectorQueryException extends RuntimeException {

    /**
     * 错误码
     */
    private final VectorErrorCode errorCode;

    /**
     * 错误消息参数
     */
    private final Object[] args;

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param args      消息参数
     */
    public VectorQueryException(VectorErrorCode errorCode, Object... args) {
        super(errorCode.getFullMessage(args));
        this.errorCode = errorCode;
        this.args = args;
    }

    /**
     * 构造函数（带原因）
     *
     * @param errorCode 错误码
     * @param cause     原因
     * @param args      消息参数
     */
    public VectorQueryException(VectorErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.getFullMessage(args), cause);
        this.errorCode = errorCode;
        this.args = args;
    }

    /**
     * 获取格式化后的错误消息
     *
     * @return 格式化后的消息
     */
    public String getFormattedMessage() {
        return errorCode.formatMessage(args);
    }

    /**
     * 获取错误码字符串
     *
     * @return 错误码字符串
     */
    public String getCode() {
        return errorCode.getCode();
    }

    // ==========================================
    // 静态工厂方法
    // ==========================================

    /**
     * 创建连接失败异常
     */
    public static VectorQueryException connectionFailed(String uri) {
        return new VectorQueryException(VectorErrorCode.MILVUS_CONNECTION_FAILED, uri);
    }

    /**
     * 创建连接失败异常（带原因）
     */
    public static VectorQueryException connectionFailed(String uri, Throwable cause) {
        return new VectorQueryException(VectorErrorCode.MILVUS_CONNECTION_FAILED, cause, uri);
    }

    /**
     * 创建认证失败异常
     */
    public static VectorQueryException authFailed() {
        return new VectorQueryException(VectorErrorCode.MILVUS_AUTH_FAILED);
    }

    /**
     * 创建集合不存在异常
     */
    public static VectorQueryException collectionNotFound(String collectionName) {
        return new VectorQueryException(VectorErrorCode.COLLECTION_NOT_FOUND, collectionName);
    }

    /**
     * 创建集合未加载异常
     */
    public static VectorQueryException collectionNotLoaded(String collectionName) {
        return new VectorQueryException(VectorErrorCode.COLLECTION_NOT_LOADED, collectionName);
    }

    /**
     * 创建向量字段不存在异常
     */
    public static VectorQueryException vectorFieldNotFound(String fieldName) {
        return new VectorQueryException(VectorErrorCode.VECTOR_FIELD_NOT_FOUND, fieldName);
    }

    /**
     * 创建维度不匹配异常
     */
    public static VectorQueryException dimensionMismatch(int expected, int actual) {
        return new VectorQueryException(VectorErrorCode.DIMENSION_MISMATCH, expected, actual);
    }

    /**
     * 创建 Embedding 服务错误异常
     */
    public static VectorQueryException embeddingServiceError(String message) {
        return new VectorQueryException(VectorErrorCode.EMBEDDING_SERVICE_ERROR, message);
    }

    /**
     * 创建 Embedding 服务错误异常（带原因）
     */
    public static VectorQueryException embeddingServiceError(String message, Throwable cause) {
        return new VectorQueryException(VectorErrorCode.EMBEDDING_SERVICE_ERROR, cause, message);
    }

    /**
     * 创建 Embedding 未配置异常
     */
    public static VectorQueryException embeddingNotConfigured() {
        return new VectorQueryException(VectorErrorCode.EMBEDDING_NOT_CONFIGURED);
    }

    /**
     * 创建无效查询条件异常
     */
    public static VectorQueryException invalidQuery() {
        return new VectorQueryException(VectorErrorCode.INVALID_VECTOR_QUERY);
    }

    /**
     * 创建多向量搜索异常
     */
    public static VectorQueryException multipleVectorSearch() {
        return new VectorQueryException(VectorErrorCode.MULTIPLE_VECTOR_SEARCH);
    }

    /**
     * 创建混合搜索不支持异常
     */
    public static VectorQueryException hybridSearchNotSupported() {
        return new VectorQueryException(VectorErrorCode.HYBRID_SEARCH_NOT_SUPPORTED);
    }

    /**
     * 创建查询执行失败异常
     */
    public static VectorQueryException queryFailed(String message) {
        return new VectorQueryException(VectorErrorCode.QUERY_EXECUTION_FAILED, message);
    }

    /**
     * 创建查询执行失败异常（带原因）
     */
    public static VectorQueryException queryFailed(String message, Throwable cause) {
        return new VectorQueryException(VectorErrorCode.QUERY_EXECUTION_FAILED, cause, message);
    }

    /**
     * 创建元数据获取失败异常
     */
    public static VectorQueryException metadataFetchFailed(String message) {
        return new VectorQueryException(VectorErrorCode.METADATA_FETCH_FAILED, message);
    }

    /**
     * 创建自动发现失败异常
     */
    public static VectorQueryException autoDiscoveryFailed(String message) {
        return new VectorQueryException(VectorErrorCode.AUTO_DISCOVERY_FAILED, message);
    }
}
