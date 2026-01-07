package com.foggyframework.dataset.mcp.storage;

/**
 * 图表存储适配器接口
 * <p>
 * 支持多种存储后端：本地存储、阿里云 OSS、腾讯云 COS、华为云 OBS、AWS S3 等
 * </p>
 */
public interface ChartStorageAdapter {

    /**
     * 存储类型标识
     *
     * @return 类型标识，如 "local", "aliyun-oss", "tencent-cos", "huawei-obs", "aws-s3"
     */
    String getType();

    /**
     * 保存图片
     *
     * @param imageBytes 图片字节数组
     * @param format     图片格式（png, svg, jpg）
     * @param traceId    追踪 ID（用于生成文件名）
     * @return 可访问的 URL
     * @throws ChartStorageException 存储失败时抛出
     */
    String save(byte[] imageBytes, String format, String traceId) throws ChartStorageException;

    /**
     * 删除图片
     *
     * @param url 图片 URL
     * @return 是否删除成功
     */
    boolean delete(String url);

    /**
     * 检查存储是否可用
     *
     * @return 是否可用
     */
    boolean isAvailable();
}
