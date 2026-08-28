---
doc_role: workitem
doc_type: feature
version: 9.5.5
priority: P1
status: ACCEPTED
recorded_at: 2026-08-27
accepted_at: 2026-08-28
---

# MCP JWT/JWKS 生产认证适配器

## 决策

Foggy 不建设用户注册、密码、MFA 或账号生命周期系统。身份继续由客户选择的 OAuth/OIDC
Authorization Server 管理；Foggy 仅作为 Resource Server 验证 access token，并把已验证 claims
投影为引擎可消费的可信身份。

`foggy-mcp-auth-jwt` 是独立模块。标准 Launcher 打包该模块，但只有
`foggy.auth.mode=oauth-resource-server` 时才激活。宿主提供自己的 `McpAccessTokenVerifier` Bean 时，
内置 decoder、verifier 和 identity bridge 全部退让。

## 安全契约

- 必须配置 audience；issuer URI 或 JWKS URI 至少一个。
- issuer/JWKS 默认只允许 HTTPS；本地测试必须显式打开 `allow-insecure-http`。
- 默认算法白名单仅 `RS256`，部署方可显式扩展。
- 验证签名、issuer、audience、`exp`、`nbf`，clock skew 最大 10 分钟。
- 支持点路径 claim，例如 Keycloak 的 `realm_access.roles`。
- 支持 subject、tenant、department、roles 和 scopes claim 映射。
- OAuth 模式下丢弃调用方自带的用户、租户、角色和权限 Header，改用验证后的 claims。
- `/mcp/admin|analyst|business` 默认要求对应角色；ADMIN 可以访问其他角色入口。
- 入站 access token 不能作为数据面 credential 透传。

## 示例

```yaml
foggy:
  auth:
    mode: oauth-resource-server
    resource-uri: https://data.example.com/mcp
    authorization-servers:
      - https://id.example.com
    scopes-supported: [mcp:read, data:query]
    required-scopes: [mcp:read]
    jwt:
      issuer-uri: https://id.example.com
      audiences: [foggy-data]
      roles-claim: realm_access.roles
      tenant-claim: tenant_id
      require-tenant: true
      role-mappings:
        ADMIN: [foggy-admin]
        ANALYST: [data-analyst]
        BUSINESS: [business-user]
```

## 验收

1. 真实 RSA/JWKS 测试覆盖签名、issuer、audience、过期、算法白名单与未知 `kid` 触发的密钥轮换。
2. focused 测试覆盖缺失 tenant、嵌套角色映射、scope/角色拒绝和伪造身份 Header 覆盖。
3. 宿主 verifier 与 `SecurityIdentityResolver` 均可覆盖内置 Bean。
4. OAuth 未配置时行为与历史版本一致，metadata 端点返回 404。
5. 本地执行完整 Maven reactor；CLI 独立执行本地测试与构建。跨平台兼容由 current-main 校验和
   Windows 路径回归测试覆盖，不要求 GitHub Actions 运行。

## 签收结果

- 真实 Keycloak RSA/JWKS + PostgreSQL 生产冒烟 8/8 通过，包括错误 audience、缺失 scope、
  ANALYST/ADMIN 角色隔离、伪造身份 Header 覆盖和未知 `kid` 后 JWKS 刷新。
- Launcher 默认 OAuth 关闭；未配置时 metadata 路由返回 404，启用生产模式后缺失 Bearer 返回 401。
- 本地完整 Maven reactor：5484 tests，0 failures，0 errors，11 skipped。
- 用户于 2026-08-28 确认只采用本地测试签收，Bridge 仓库级 GitHub Actions 保持关闭。

## 后续边界

- Authorization Server 可用性、用户生命周期、登录 UI、授权同意和 token 签发不属于 Foggy。
- 细粒度工具级 ABAC、动态组织关系和跨租户委托需由独立策略工作项定义。
- opaque token introspection 可通过宿主 `McpAccessTokenVerifier` 实现，不由 JWT 模块伪装支持。