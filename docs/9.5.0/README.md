---
doc_role: version-readme
version: 9.5.0
theme: model-legacy-exit
status: ACCEPTED
recorded_at: 2026-07-24
---

# 9.5.0 Model Legacy Exit

9.5.0 完成 9.4.0/9.4.1 之后的第一方 Model SPI legacy exit。它删除旧聚合身份与旧 Java 包根，
把稳定查询、模型加载和原子刷新能力固定到 SPI v2 小型 port，并保留查询、权限和数据结果语义。

这是有意的 source/binary breaking 版本，不是无破坏升级。

## 本次结果

- Maven 模块 `foggy-dataset-model` 物理归位为 `foggy-dataset-model-engine`；
  第一方旧聚合直接依赖 `14 -> 0`。
- 生产 Java 的旧包根 `com.foggyframework.dataset.db.model.*`：
  `11 modules / 110 files -> 0`；engine 与 addon 实现统一迁移到
  `com.foggyframework.dataset.model.*`。
- 删除 `LegacyQueryFacadeAdapter` 和旧 engine `service.QueryFacade`；
  engine 高级查询入口改名为 `AdvancedQueryFacade`，公共稳定入口仍为
  `com.foggyframework.dataset.model.api.QueryFacade`。
- `DatasetClientProxy` 不再构成 model SPI `model.query()` bypass；
  旧坐标、旧包、兼容 adapter 和受治理 bypass 守卫均为零 allowlist。
- model-api 新增 JDK-only `ModelLoadPort`、`AtomicRefreshPort` 及不可变 DTO；
  JDBC engine provider 真实发布 `QUERY`、`MODEL_LOAD`、`ATOMIC_REFRESH`。
- provider catalog 与 TCK 对 QUERY、MODEL_LOAD、ATOMIC_REFRESH、
  CACHE_INVALIDATION 的 capability-role 不匹配均 fail closed。
- query-cache 继续只发布 CACHE_INVALIDATION；Mongo/vector/preagg 等未接入角色不声明新能力。
- launcher、runtime、MCP、memory-grid、addons、顶层验证脚本和 Spring 元数据已迁移到新身份。

## Breaking 说明

- 外部 POM 必须把 `com.foggysource:foggy-dataset-model` 替换为所需的
  `foggy-dataset-model-api`、`-core`、`-engine` 或 adapter 模块。
- 外部源码必须把 `com.foggyframework.dataset.db.model.*` import 迁移到
  `com.foggyframework.dataset.model.*`，并重新编译。
- 依赖 `LegacyQueryFacadeAdapter` 或旧 engine `service.QueryFacade` 的调用方必须切换到稳定
  `model-api QueryFacade`，或在 engine 内部使用 `AdvancedQueryFacade`。
- 不提供旧坐标空壳、双包运行时或二进制转发层。

完整迁移表和回滚方式见
`docs/9.5.0/model-spi-v2-legacy-exit.md`。

## Provider 能力边界

| Provider | QUERY | MODEL_LOAD | ATOMIC_REFRESH | CACHE_INVALIDATION |
|---|---:|---:|---:|---:|
| JDBC engine | yes | yes | yes | no |
| query-cache | no | no | no | yes |
| Mongo/vector/preagg 及其他未接入 backend | no new claim | no | no | no new claim |

namespace isolation 是 load/query/refresh 请求和 catalog identity 的不变量，不作为可选 capability。
duplicate identity、missing provider、unsupported capability 和 capability-role mismatch 均显式失败。

## 验证边界

本候选运行了受影响 Maven reactor 的 compile、test-compile、focused unit/context/TCK 和独立 addon
test-compile；全部使用 `-pl ... -am`，未运行 `mvn install`。

`scripts/v950/**` 已新增并加固当前布局的 canonical release authority。最终 authority
候选为 `03af64da7330d70d3364c9e92992710a2ed3a111`：

- root reactor 32/32 projects 与 launcher JAR 边界通过；
- SQLite semantic replay 63/63；
- SQLite、MySQL 5.7、MySQL 8、PostgreSQL 15、SQL Server 2022 共
  7/7 variants、370/370 tests；
- 四个外部数据库 fixture seal 前后一致且 cleanup passed；
- 4432 个 tracked files 的确定性源码归档在 `/dev/shm` 跨文件系统解压；
- archive portable replay 63/63；
- source seal 前后 SHA-256 完全一致；
- 最终 17 个收据绑定、敏感证据扫描和 manifest 均通过。

本次 authority hardening 仅修改治理脚本、聚焦测试和对应 BUG workitem。root、semantic、
七个数据库 variant 与四个数据库 cell 从产品树未变化的已通过祖先候选复用；wrapper 对祖先
关系、完整 changed-path allowlist、源 receipt hash 与精确语义重新绑定。归档、跨文件系统
解压、portable replay、source seal、敏感扫描和 final manifest 均在最终候选上新跑。
新证据包不复制复用 lane 的 Maven 日志或原始报告。

finalizer 现已精确校验 lane/database、JUnit report identity 与 totals、fixture seal/cleanup、
archive/extraction/source-seal 交叉绑定及完整 17-receipt 集合；14/14 聚焦工具测试通过。
正式结论为 `ACCEPTED` / `READY_FOR_TAG`。review 与实现位于同一 Codex 会话，不声明组织
独立性；这属于可选流程选择，不是当前批准范围内的技术 blocker。

GitHub CI、tag、release、publish 和 remote push 均未运行。

## 文档入口

- canonical workitem：`docs/9.5.0/workitems/FEATURE-v950-legacy-exit.md`
- release authority workitem：
  `docs/9.5.0/workitems/FEATURE-v950-release-authority-modernization.md`
- authority hardening workitem：
  `docs/9.5.0/workitems/BUG-v950-release-authority-evidence-hardening.md`
- migration / breaking / rollback：`docs/9.5.0/model-spi-v2-legacy-exit.md`
- acceptance：`docs/9.5.0/acceptance/version-signoff.md`
