package com.foggyframework.mcp.launcher;

import com.foggyframework.dataset.db.model.spi.SecurityIdentityResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Demo 环境的安全身份解析器
 * <p>
 * 从 Authorization header 解析用户身份。
 * 实际生产环境应解析 JWT token，这里使用简单的 mock 实现用于演示。
 * <p>
 * 仅在 foggy.test.enabled=true 时激活（默认不激活）
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "foggy.test", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DemoSecurityIdentityResolver implements SecurityIdentityResolver {

    @Override
    public ResolvedIdentity resolve(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            log.warn("Authorization header is empty");
            return null;
        }

        // 简单 mock 实现：根据 token 前缀返回不同用户身份
        // 实际生产环境应解析 JWT token 获取真实的 userId/deptId/tenantId

        String userId;
        String deptId;
        String tenantId;
        Map<String, String> attributes = new HashMap<>();

        if (authorization.contains("manager")) {
            // 模拟门店经理
            userId = "user_manager_001";
            deptId = "dept_sales";
            tenantId = "tenant_demo";
            attributes.put("role", "MANAGER");
            attributes.put("storeKey", "1");
            attributes.put("userName", "张三（经理）");
        } else if (authorization.contains("analyst")) {
            // 模拟数据分析师
            userId = "user_analyst_001";
            deptId = "dept_analytics";
            tenantId = "tenant_demo";
            attributes.put("role", "ANALYST");
            attributes.put("userName", "李四（分析师）");
        } else if (authorization.contains("admin")) {
            // 模拟管理员
            userId = "user_admin_001";
            deptId = "dept_it";
            tenantId = "tenant_demo";
            attributes.put("role", "ADMIN");
            attributes.put("userName", "王五（管理员）");
        } else {
            // 默认普通用户
            userId = "user_default_001";
            deptId = "dept_sales";
            tenantId = "tenant_demo";
            attributes.put("role", "USER");
            attributes.put("userName", "赵六（普通用户）");
        }

        log.info("Resolved identity from authorization: userId={}, deptId={}, tenantId={}",
                userId, deptId, tenantId);

        return new ResolvedIdentity(userId, deptId, tenantId, attributes);
    }
}
