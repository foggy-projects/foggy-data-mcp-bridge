---
doc_role: workitem
doc_type: feature
version: 9.5.5
priority: P0
status: ACCEPTED
recorded_at: 2026-08-27
accepted_at: 2026-08-28
---

# Current-main 生产基线

## 决策

本工作项由用户明确提升为 9.5.5 P0，作为原 Console Agent 非目标的范围修订。
不创建新版本，也不把 `9.3.0-SNAPSHOT` Maven 坐标误改为 9.5.x 正式发布。

## P0-1：版本与 CI 真相

- 根目录 `current-main.json` 是 current-main 兼容性清单，明确区分发布线、活跃迭代、
  Maven 坐标和独立交付物版本。
- `scripts/current-main/verify_current_main.py` 校验 Maven、两个 Console、MCP 协议和可选
  sibling CLI pins。
- bridge 与 CLI 保留可复用的 current-main 校验入口和 workflow 模板；当前签收由本地完整验证完成，
  GitHub Actions 不作为门禁。冻结的 9.3.4 evidence chain 保留，不再被误当作 current-main 全量门禁。

## P0-2：MCP 生产认证与协议兼容

- 旧 `AUTO`/静态 token 行为继续支持本地开发；生产选择
  `OAUTH_RESOURCE_SERVER` 后必须配置 resource URI、authorization server，并提供
  `McpAccessTokenVerifier`，缺一项即拒绝启动。
- 暴露 OAuth protected-resource metadata 和 `WWW-Authenticate` discovery。
- 入站 MCP Bearer 不再原样传入 namespace policy、工具 dispatcher 或远端 Compose；宿主只能
  显式返回一份不同的数据面凭据。
- `/mcp/{role}/rpc` 保留；`/mcp/{role}` 增加无状态入口和 `server/discover`。
  `initialize` 仅协商 legacy 版本，避免把现代无握手协议伪装成 legacy handshake。

## P0-3：Analytics Console 可信问数门禁

- Ask 在调用 FAP 前写 PREPARED，FAP 接受后写 ACCEPTED，catalog 落盘后写 CATALOGED；
  日志 append-only 且 fsync，catalog 失败时保留可人工对账的远端引用。
- 管理员只读端点 `/analytics-console/api/v1/agent/recovery/unresolved` 返回未解决项。
- `production-mode=true` 必须使用 host-managed security、external-durable storage、启用 FAP
  且至少配置一个 question profile。
- `file-single-process` 继续服务本地/单进程开发，但不能声明为 production-ready。

## 验收

1. current-main verifier 通过，并能在任一 pin 漂移时失败。
2. MCP focused tests 覆盖 legacy 协商、discovery、OAuth fail-closed 和凭据隔离。
3. Analytics focused tests 覆盖生产门禁、恢复日志状态和 catalog 失败后的 ACCEPTED backlog。
4. CLI 单测、构建和包版本一致性通过。

## 签收结果

- 本地完整 Maven reactor：5484 tests，0 failures，0 errors，11 skipped。
- 独立干净 CLI HEAD：126 passed，1 skipped，12 subtests；sdist/wheel 构建通过。
- 隔离 Keycloak 26 + PostgreSQL 15 生产冒烟：8/8 通过，覆盖真实数据库连接、OAuth metadata、
  audience/scope/role 拒绝、身份 Header 防伪和 JWKS `kid` 轮换刷新。
- 用户于 2026-08-28 明确采用本地验证作为签收依据；Bridge 仓库级 GitHub Actions 保持关闭。

## 后续边界

- `external-durable` 的数据库实现和多实例恢复 Worker 已冻结为 [独立工作项](FEATURE-analytics-external-durable-recovery-worker.md)；
  JWT/JWKS adapter 已由后续 [独立工作项](FEATURE-mcp-jwt-jwks-auth-adapter.md) 实现。
- 现代 MCP 的传输级 cache/TTL 与完整协议互操作矩阵仍需独立 conformance suite，不以本基础
  入口替代官方一致性认证。
