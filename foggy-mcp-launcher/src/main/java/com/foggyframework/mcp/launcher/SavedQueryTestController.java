package com.foggyframework.mcp.launcher;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.spi.SecurityIdentityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/**
 * 保存查询功能测试控制器
 * <p>
 * 提供简单的测试端点验证 SecurityIdentityResolver 是否正常工作
 * <p>
 * 仅在 foggy.test.enabled=true 时激活（默认不激活）
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "foggy.test", name = "enabled", havingValue = "true", matchIfMissing = false)
public class SavedQueryTestController {

    @Autowired(required = false)
    private final SecurityIdentityResolver identityResolver;

    /**
     * 测试身份解析
     */
    @GetMapping("/identity")
    public RX<?> testIdentity(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        if (identityResolver == null) {
            return RX.failB("SecurityIdentityResolver 未配置", null);
        }

        if (authorization == null || authorization.isBlank()) {
            return RX.failB("缺少 Authorization header", null);
        }

        SecurityIdentityResolver.ResolvedIdentity identity = identityResolver.resolve(authorization);
        if (identity == null) {
            return RX.failB("身份解析失败", null);
        }

        return RX.ok(new IdentityTestResponse(
                identity.userId(),
                identity.deptId(),
                identity.tenantId(),
                identity.attributes()
        ));
    }

    /**
     * 测试响应
     */
    public record IdentityTestResponse(
            String userId,
            String deptId,
            String tenantId,
            java.util.Map<String, String> attributes
    ) {}
}
