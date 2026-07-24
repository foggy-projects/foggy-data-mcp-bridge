package com.foggyframework.dataset.model.impl.vector;

import lombok.Getter;

/**
 * 向量模块错误码
 * <p>
 * 定义向量数据库操作相关的错误码，便于问题定位和处理。
 *
 * @author foggy-dataset
 * @since 2.0.0
 */
@Getter
public enum VectorErrorCode {

    // ==========================================
    // 连接错误 (VEC_0xx)
    // ==========================================

    /**
     * 无法连接到 Milvus 服务
     */
    MILVUS_CONNECTION_FAILED("VEC_001", "无法连接到 Milvus 服务: {0}"),

    /**
     * Milvus 认证失败
     */
    MILVUS_AUTH_FAILED("VEC_002", "Milvus 认证失败"),

    /**
     * 连接池耗尽
     */
    CONNECTION_POOL_EXHAUSTED("VEC_003", "连接池耗尽，无法获取 Milvus 客户端"),

    /**
     * 连接超时
     */
    CONNECTION_TIMEOUT("VEC_004", "连接 Milvus 超时"),

    // ==========================================
    // 集合错误 (VEC_1xx)
    // ==========================================

    /**
     * 集合不存在
     */
    COLLECTION_NOT_FOUND("VEC_101", "集合不存在: {0}"),

    /**
     * 集合未加载到内存
     */
    COLLECTION_NOT_LOADED("VEC_102", "集合未加载到内存: {0}"),

    /**
     * 集合已存在
     */
    COLLECTION_ALREADY_EXISTS("VEC_103", "集合已存在: {0}"),

    /**
     * 集合操作失败
     */
    COLLECTION_OPERATION_FAILED("VEC_104", "集合操作失败: {0}"),

    // ==========================================
    // 向量字段错误 (VEC_2xx)
    // ==========================================

    /**
     * 未找到向量字段
     */
    VECTOR_FIELD_NOT_FOUND("VEC_201", "未找到向量字段: {0}"),

    /**
     * 向量维度不匹配
     */
    DIMENSION_MISMATCH("VEC_202", "向量维度不匹配，期望 {0}，实际 {1}"),

    /**
     * 向量字段未索引
     */
    VECTOR_FIELD_NOT_INDEXED("VEC_203", "向量字段未建立索引: {0}"),

    /**
     * 无效的向量数据
     */
    INVALID_VECTOR_DATA("VEC_204", "无效的向量数据"),

    // ==========================================
    // Embedding 错误 (VEC_3xx)
    // ==========================================

    /**
     * Embedding 服务调用失败
     */
    EMBEDDING_SERVICE_ERROR("VEC_301", "Embedding 服务调用失败: {0}"),

    /**
     * Embedding 服务返回空结果
     */
    EMBEDDING_EMPTY_RESULT("VEC_302", "Embedding 服务返回空结果"),

    /**
     * Embedding 服务未配置
     */
    EMBEDDING_NOT_CONFIGURED("VEC_303", "Embedding 服务未配置"),

    /**
     * Embedding API Key 无效
     */
    EMBEDDING_INVALID_API_KEY("VEC_304", "Embedding API Key 无效或已过期"),

    /**
     * Embedding 服务限流
     */
    EMBEDDING_RATE_LIMITED("VEC_305", "Embedding 服务限流，请稍后重试"),

    // ==========================================
    // 查询错误 (VEC_4xx)
    // ==========================================

    /**
     * 无效的向量查询条件
     */
    INVALID_VECTOR_QUERY("VEC_401", "无效的向量查询条件"),

    /**
     * 仅支持单个向量搜索条件
     */
    MULTIPLE_VECTOR_SEARCH("VEC_402", "仅支持单个向量搜索条件"),

    /**
     * 当前 Milvus 版本不支持混合搜索
     */
    HYBRID_SEARCH_NOT_SUPPORTED("VEC_403", "当前 Milvus 版本不支持混合搜索"),

    /**
     * 查询执行失败
     */
    QUERY_EXECUTION_FAILED("VEC_404", "查询执行失败: {0}"),

    /**
     * 无效的过滤表达式
     */
    INVALID_FILTER_EXPRESSION("VEC_405", "无效的过滤表达式: {0}"),

    /**
     * 不支持的操作符
     */
    UNSUPPORTED_OPERATOR("VEC_406", "不支持的操作符: {0}"),

    // ==========================================
    // 配置错误 (VEC_5xx)
    // ==========================================

    /**
     * 配置无效
     */
    INVALID_CONFIGURATION("VEC_501", "配置无效: {0}"),

    /**
     * 缺少必要配置
     */
    MISSING_CONFIGURATION("VEC_502", "缺少必要配置: {0}"),

    // ==========================================
    // 元数据错误 (VEC_6xx)
    // ==========================================

    /**
     * 元数据获取失败
     */
    METADATA_FETCH_FAILED("VEC_601", "元数据获取失败: {0}"),

    /**
     * 元数据自动发现失败
     */
    AUTO_DISCOVERY_FAILED("VEC_602", "元数据自动发现失败: {0}");

    /**
     * 错误码
     */
    private final String code;

    /**
     * 错误消息模板
     */
    private final String messageTemplate;

    VectorErrorCode(String code, String messageTemplate) {
        this.code = code;
        this.messageTemplate = messageTemplate;
    }

    /**
     * 格式化错误消息
     *
     * @param args 消息参数
     * @return 格式化后的消息
     */
    public String formatMessage(Object... args) {
        if (args == null || args.length == 0) {
            return messageTemplate;
        }

        String message = messageTemplate;
        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return message;
    }

    /**
     * 获取完整的错误信息（包含错误码）
     *
     * @param args 消息参数
     * @return 完整的错误信息
     */
    public String getFullMessage(Object... args) {
        return "[" + code + "] " + formatMessage(args);
    }
}
