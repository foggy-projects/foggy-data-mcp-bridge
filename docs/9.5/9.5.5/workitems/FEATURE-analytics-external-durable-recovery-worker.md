---
doc_role: workitem
doc_type: feature
version: 9.5.5
priority: P1
status: PROPOSED
recorded_at: 2026-08-27
---

# Analytics external-durable 与多实例恢复 Worker

## 决策

`external-durable` 是 Analytics Console 的生产存储边界，不是把当前 JSON/JSONL 文件放到共享盘。
首个参考实现使用 PostgreSQL，同时保留宿主提供实现的 SPI。多实例恢复采用有界 lease、乐观版本和
数据库行锁协调；不承诺跨 FAP 与本地数据库的分布式事务，而是通过稳定幂等键实现 at-least-once
恢复和可证明的最终一致。

该工作项不建设用户系统，不存储 OAuth access token，也不把 FAP credential 写入恢复记录。

## 范围

1. 冻结 conversation、catalog 与 ask recovery 的 external-durable SPI。
2. 提供独立 PostgreSQL adapter 和显式 schema migration。
3. 提供可关闭的恢复 Worker，支持多实例抢占、lease 过期接管、退避和人工处置。
4. 提供只读恢复队列、受控重试入口、指标和健康状态。
5. 保留现有 `file-single-process` 作为本地开发实现，禁止用于多实例或 production-mode。

## 非目标

- 用户注册、密码、MFA、登录 UI 或 Authorization Server。
- exactly-once 跨系统事务、Kafka 强制依赖或共享文件锁。
- 自动忽略未知状态、删除恢复证据、无限重试或把异常堆栈暴露给 Console。
- 在没有 FAP 幂等查询证据时自动重发 PREPARED 请求。

## 稳定幂等键与租户边界

- 逻辑主键：`tenant_ref + conversation_id + ask_request_id`。
- FAP 调用必须携带相同 `ask_request_id` 和 `external_conversation_ref`；重试不得生成新键。
- 所有读取、claim、更新和运维 API 都必须显式携带 tenant；禁止无 tenant 的全表回退。
- `provider_ref + external_conversation_ref + ask_request_id` 用于 FAP 对账，不替代本地租户主键。

## 数据模型

### `analytics_ask_recovery_event`

append-only 证据账本：

- tenant/conversation/request/provider/external conversation 标识；
- `PREPARED`、`ACCEPTED`、`CATALOGED` 状态；
- FAP execution/task 引用和非敏感错误码；
- 递增序号、发生时间、payload digest；
- 唯一约束阻止同一状态的重复提交改变既有证据。

禁止保存 access token、Authorization header、问题正文、模型内容和原始异常堆栈。

### `analytics_ask_recovery_projection`

可重建的当前状态投影：

- current state、version、attempt count、next retry time；
- lease owner、lease acquired/expiry；
- accepted execution/task 引用；
- last error code、manual-review flag、updated time。

事件与投影必须在同一数据库事务内更新；投影丢失时可从 event ledger 重建。

## Repository SPI

SPI 至少提供以下语义，不把 JDBC 类型泄漏给 Console service：

- `appendTransition(key, expectedState, nextState, evidence)`：幂等且带 CAS；
- `findCurrent(key)` 与 cursor-based `findUnresolved(tenant, cursor, limit)`；
- `claimDue(owner, now, leaseDuration, limit)`：仅领取未被有效 lease 占用的记录；
- `renewLease(key, owner, expectedVersion, newExpiry)`；
- `completeLease(...)`、`releaseLease(...)` 与 `markManualReview(...)`；
- 所有 mutation 返回明确的 applied/already-applied/conflict/not-found 结果，不用异常猜测并发结果。

宿主提供 Repository Bean 时 PostgreSQL adapter 必须退让。production-mode 下没有 external-durable
Repository 或 schema 版本不兼容时拒绝启动。

## Worker 状态与恢复规则

1. 每批在短事务中通过 `FOR UPDATE SKIP LOCKED` 选取到期记录并写入 lease；网络调用不持有数据库锁。
2. `PREPARED` 必须先按稳定幂等键向 FAP 查询：
   - 已接受：追加 `ACCEPTED`；
   - 明确未接受且 FAP 支持相同键幂等提交：允许重试提交；
   - 结果未知或 FAP 无幂等保证：标记人工处置，不自动重发。
3. `ACCEPTED` 查询 FAP 结果；结果可用时以 CAS 写 catalog，再追加 `CATALOGED`。
4. callback、前台请求和 Worker 使用同一 CAS/幂等路径；重复 callback 返回 already-applied。
5. 失败使用带 jitter 的指数退避；达到上限后保留证据并进入 manual-review，不删除、不跳过租户边界。
6. 实例退出尽力释放 lease；异常退出由 lease expiry 接管。数据库时间是 lease 判定权威。

## 默认运维参数

- Worker 默认关闭；只有 `production-mode=true` 且显式启用时运行。
- lease 60 秒、每 20 秒轮询、batch 50、最大并发 4；均可配置并设安全上限。
- readiness 在 schema 不兼容或 Repository 不可用时失败；单条 backlog 不使 readiness 失败。
- 指标：unresolved 数量/最老年龄、claim/retry/conflict/manual-review、恢复延迟、lease expiry takeover。
- 日志只记录 tenant 的不可逆摘要、request id、状态和错误码。

## 实施切片

1. **4A：SPI + PostgreSQL store**：migration、event/projection、CAS、分页、双实例 repository tests。
2. **4B：Recovery Worker**：lease、FAP reconcile、退避、停机/接管、callback race tests。
3. **4C：Console 运维面**：只读队列、受控重试/manual-review、指标和 runbook。

每个切片独立提交和验收；4B 不与 4A 合并成不可审查的大提交。

## 验收

1. PostgreSQL 15/16 数据库矩阵通过，migration 可重复执行且错误版本 fail-closed。
2. 两个 Launcher 实例并发运行 30 分钟，同一 ask 不发生重复状态跃迁或重复 catalog。
3. 在 PREPARED 写后、FAP ACCEPT 后、catalog 写前分别强杀实例，lease 到期后可恢复或安全转人工。
4. 重复 callback、Worker/callback race、数据库重启和网络超时均保留单一逻辑结果与完整证据。
5. tenant A 无法读取、claim 或更新 tenant B；分页 cursor 不能跨 tenant 重放。
6. 数据库与日志扫描确认无 raw OAuth/FAP token、Authorization header、问题正文和模型内容。
7. `file-single-process` 回归通过；production-mode 缺少 external store/worker 必需配置时拒绝启动。

## 进入实现前需确认

- FAP 是否提供按 `external_conversation_ref + ask_request_id` 查询并幂等重提的稳定契约；若没有，
  PREPARED 只能自动对账，不能自动重发。
- migration 由 Launcher 内置执行还是交给部署方；建议提供 SQL artifact，并由部署流水线显式执行。
- 默认 retention 只作用于已 `CATALOGED` 且超出审计期的记录；具体时长由产品 owner 决定，当前不实现删除。