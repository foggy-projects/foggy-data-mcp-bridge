package com.foggyframework.dataset.model.spi;

import java.util.Map;

/**
 * 安全身份解析器 SPI
 * <p>
 * 从 authorization 字符串（如 JWT token）解析出用户身份信息。
 * 使用方需实现此接口并注册为 Spring Bean。
 * 如未提供实现，保存查询功能将不可用。
 */
public interface SecurityIdentityResolver {

    /**
     * 从 authorization 解析用户身份
     *
     * @param authorization 授权字符串（如 Bearer token）
     * @return 解析后的身份信息
     */
    ResolvedIdentity resolve(String authorization);

    /**
     * 解析后的用户身份
     */
    record ResolvedIdentity(
            String userId,
            String deptId,
            String tenantId,
            Map<String, String> attributes
    ) {}
}
