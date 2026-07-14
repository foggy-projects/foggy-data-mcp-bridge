---
doc_role: workitem
doc_purpose: Define the 9.3.3 model lifecycle and concurrency implementation contract.
version: 9.3.3
priority: P0
status: completed
acceptance_status: signed-off
acceptance_decision: accepted-with-risks
created_at: 2026-07-13
updated_at: 2026-07-14
---

# P0 模型生命周期与并发

## 文档作用

- doc_type: requirement
- intended_for: root-controller / execution agent / reviewer / signoff owner
- purpose: 冻结 9.3.3 的业务边界、并发不变量、完成标准和后续版本边界。

## 背景

9.3.1 已收紧 namespace、datasource、缓存和安全身份，9.3.2 已收紧 Boot 3 自动配置与 Addon 装配。但模型运行时仍存在以下结构性风险：

- TM/QM/catalog alias 分散在可变 `HashMap` 中，缺少同代一致视图。
- TM 加载使用全局 `synchronized`，不同 namespace/model 被无谓串行；QM 缓存和清理缺少并发保护。
- runtime refresh 先清空再逐个预热，失败会让旧可用目录消失。
- 文件变化全局清缓存，bundle remove 事件发生在源移除前，无法直接支撑正确 candidate rebuild。
- datasource rebind 没有可消费的 binding generation，旧模型/缓存身份可能继续引用变化后的逻辑数据源。
- `NamespaceContext.set/clear` 无法安全表达嵌套作用域，内层 finally 会清掉外层 namespace。

## 目标

1. 以每 namespace 一个不可变 `CatalogSnapshot` 统一 TM、QM、synthetic QM、alias、model discovery 和模型 provenance。
2. 明确 catalog generation 与 datasource binding generation；查询、single-flight 和缓存消费同一身份。
3. 同 key 100 并发请求只构建一次；不同 key 可并行，失败可传播、清理并重试。
4. refresh 离线构建、完整验证、一次切换；并发读只见完整 old 或完整 new。
5. source/model refresh 失败且 effective bindings 仍有效时，旧 generation、旧模型和旧真实查询保持可用；binding mutation 按第 12 项 fail closed。
6. namespace refresh 不影响其他 namespace；model scope 不丢失同 namespace 未变 sibling。
7. runtime refresh、bundle add/remove、file change、datasource save/remove/rebind 统一调用核心生命周期能力。
8. 用 `NamespaceScope` 替代生产链路的裸 set/clear，并验证嵌套、异常和线程池复用。
9. L1/L2/Redis/Pivot identity 消费 generation；身份不完整时继续 fail closed。
10. 以确定性并发测试和真实 SQL/返回数据证明语义，不以 sleep、SQL 字符串或对象存在代替。
11. source mutation 以 committed、不可复用的 `SourceRevision` 参与 candidate stale check；未知影响范围时阻断可能受影响目录的新查询，不能无限期服务旧目录。
12. datasource remove/disable/rebind commit 后，新查询只能获取新 binding 或稳定失败；不得因 candidate 失败重新获取旧 binding。

## 与版本路线的关系

- 上游路线：[9.3.1 → 9.4.0 迭代顺序评审](../../9.3.1/roadmap-9.3.1-to-9.4.0.md)。
- 继承 9.3.1 的 namespace/datasource/security fail-closed 身份和 9.3.2 的显式自动配置边界。
- 开工前只消费 9.3.4-A 最小测试门；完整 CI/数据库矩阵/覆盖率仍由 9.3.4 收口。
- 向 9.3.5 输出已验证的 CatalogIdentity/NamespaceScope，向 9.4.0 输出尚未抽模块的 lifecycle/binding port。

## 约束

- 保护 9.3.1/9.3.2 当前未提交成果；禁止 reset、checkout、clean 或覆盖式还原。
- 9.3.4-A 未通过前，不进入生产 lifecycle 实现。
- 不能用全局锁、全局 clear、先清后热或仅缩短 TTL 代替本需求。
- candidate 未完成全部目标构建与验证前不得对读路径可见；任何改变 TM/QM/alias/discovery/provenance 或查询结果的 lazy enrichment 都算一次新 candidate publish，并切换 catalog generation。
- 一次查询必须固定一个 snapshot identity；不得在同一执行中重新解析到另一 generation。
- generation 必须是不复用的 opaque identity，不能只用可回拨 wall clock；持久 registry 的 binding generation 必须跨重启保持单调或带不重复 epoch。
- 旧 generation 的 datasource handle 不得在原 identity 下静默路由到新物理目标；无法提供稳定身份的 routing datasource 保持不可缓存。
- “failed refresh keeps old”只适用于 source/model refresh 且其 binding 仍有效。remove/disable/rebind commit 后，旧 binding 停止接受新 lease；已持有 lease 的查询只允许在有界时间内 drain，不能重试或重新获取旧 handle。hard revoke 可立即关闭旧 handle，不保证在途查询完成。
- file/import/bundle mutation 必须先一致更新 source registry、script cache 和 reverse-dependency view，再发布 affected scope + `SourceRevision`。范围未知时把无法证明不受影响的 catalog 标为 stale，阻断新查询并持久化诊断，直到显式 rebuild 成功。
- 不泄露密码、JDBC 凭据或可逆连接信息到 generation、日志、缓存键和 evidence。
- 兼容入口可保留一个周期，但生产事件和 Runtime API 不再调用 clear-first 路径。

## 非目标

- 不完成全仓所有历史 `*IntegrationTest` 的重命名/分层，不建立 9.3.4 完整五数据库与覆盖率/release 门禁。
- 不统一所有外部查询入口，不新建执行阶段枚举，不拆 QueryFacade/engine 大类；归 9.3.5。
- 不物理拆 `model-api/core/jdbc/starter/web`，不冻结 SPI v2，不实现 BackendProvider/Addon TCK；归 9.4.0。
- 不实现跨 JVM 的分布式 refresh 协调；本轮要求跨 JVM 缓存身份安全，不能误命中。
- 不顺带补 9.3.2 的 Cloud/DataViewer 次要切片或清理说明性 `spring.factories`。

## External Contract Boundary

- Runtime refresh/validate 响应只做向后兼容的 additive 扩展；现有字段和成功/失败 HTTP 语义不得改变。
- generation/source revision 在 DTO 中使用 opaque string，客户端只能比较相等/不等，不能解析数值或时间。
- success 至少追加 before/after catalog generation、受影响 binding generation 摘要、refreshed/preserved count；cold start 的 before 可为 null。
- failure 使用稳定 error code，并返回 before generation、失败目标/模型和 sanitized diagnostics；after generation 为 null，active catalog 保持 before 或进入明确 stale/admission-blocked 状态。
- 字段名、nullability 和错误码枚举在 Batch 1 contract review 中确认后才能修改 Controller/DTO；日志、响应和 evidence 不得包含凭据或可逆连接信息。

## 验收标准

| ID | 验收标准 | 严重级别 |
|---|---|---|
| PRE-934A | 0 tests、required DB 缺失/身份错误会失败；unit/IT 不重复不漏跑 | critical |
| NS-SCOPE | 嵌套、异常、默认/非默认覆盖和线程池复用无 namespace 泄漏 | critical |
| SNAPSHOT | 每 namespace 的 TM/QM/alias/catalog 来自不可变同代 snapshot | critical |
| GENERATION | 成功 publish 恰好切一代；read/失败不切代；namespace 互不影响 | critical |
| DS-GENERATION | datasource save/update/remove/disable/rebind 改变对应 binding generation，旧 identity 不换目标 | critical |
| BINDING-REVOKE | remove/disable/rebind commit 后新查询不再获取旧 binding；失败时稳定 fail closed；在途 lease 仅有界 drain | critical |
| SF-SAME | 100 并发同 key 的实际 build count=1 | critical |
| SF-ISOLATION | 不同 namespace/model/generation/binding key 可并行且不共享 future | critical |
| SF-FAIL | winner 失败唤醒 waiter、移除 in-flight，下一次可成功重试 | critical |
| REFRESH-ATOMIC | 并发读只观察到完整 old 或完整 new，无 empty/hybrid | critical |
| REFRESH-FAIL | source/model candidate 失败且 bindings 仍有效时，旧 generation 与旧真实查询完整可用 | critical |
| REFRESH-SCOPE | namespace A/model X 刷新不误伤 namespace B 或 sibling model | critical |
| EVENT-CONVERGENCE | Runtime、bundle、file、datasource 变更不再走全局 clear-first | critical |
| SOURCE-COMMIT | committed SourceRevision、affected scope 和 stale check 防止旧 source candidate 发布；未知 scope 阻断新查询 | critical |
| QM-COMPLETE | 任一关联 TM/QM dependency 或累计 builder error 使 candidate 整体失败，不发布 partial QM | critical |
| VALIDATE-ISOLATION | Runtime validate 的 valid/invalid 临时资源均不改变 live snapshot、generation、names 或 alias | critical |
| CATALOG-AUTHORITY | model 与 MCP catalog/resolver 只消费同一 namespace snapshot，不保留独立 names/alias authority | critical |
| CACHE-GEN | 同代可命中，切代/换 binding 后旧 L1/L2/Redis/Pivot key 不可达 | critical |
| CACHE-CROSS-JVM | 独立 JVM/重启下 key 不依赖对象地址；本轮冷缓存 epoch 不碰撞旧 Redis key | critical |
| REAL-QUERY | old/new 结果分别与对应原生 SQL 一致，且物理 datasource identity 正确 | critical |
| API-COMPAT | Runtime DTO 仅 additive；opaque/nullability/error code 与旧 consumer 兼容且 diagnostics 脱敏 | critical |
| REGRESSION | 9.3.1 fail-closed 与 9.3.2 自动装配/Launcher 契约不回退 | critical |
| POST-GATES | progress、自检、quality、coverage、version acceptance 顺序闭环 | major |

## 完成定义

- 以上 critical 项全部有直接、可复跑的自动化证据；critical 缺口不能降级为 accepted-with-risks。
- `mvn compile`、目标 unit test、Failsafe IT、SQLite、required external DB 和 Launcher smoke 全部通过。
- 测试记录包含实际 test/failure/error/skip 数、报告路径、并发 build count、generation 观察值和数据库产品/版本/物理身份。
- 实现完成后先做轻量 self-check，再执行正式 implementation quality gate、test coverage audit 和 version acceptance。
- 最终 README、requirement、progress 与 roadmap 回写同一签收状态和记录路径。

## Signoff

- status: completed
- acceptance: signed-off / accepted-with-risks
- authority: `target/v933-batch7-regression/runs/20260714T084351Z-3271604/`
- quality: `../quality/model-lifecycle-concurrency-implementation-quality.md`
- coverage: `../coverage/model-lifecycle-concurrency-coverage-audit.md`
- acceptance record: `../acceptance/version-signoff.md`
- next iteration: 9.3.4 测试与 CI 证据链
