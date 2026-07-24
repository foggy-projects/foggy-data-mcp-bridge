---
doc_role: version-readme
version: 9.5.0
theme: model-legacy-exit
status: ACCEPTED_WITH_RISKS
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

Owner 批准的一次 lean root authority 在前 28 个模块完成 4960 tests 后，由 launcher smoke
残留的旧 SQLite fixture 路径触发 1 个 test-context error。该 test-only 缺陷修复后，
`DataViewerApiSmokeTest` 6/6、受影响 reactor 27/27 及 32-module artifact verify 均通过。
没有重复完整 root `clean verify`，因此签收证据是最小失效半径组合证据，不描述为完整 post-fix
authority 绿线。

Step 5/Step 7、semantic/portable replay、source-seal、通用五库矩阵、GitHub CI、tag、release 和
publish 均未运行。正式结论为 `ACCEPTED_WITH_RISKS`。

## 文档入口

- canonical workitem：`docs/9.5.0/workitems/FEATURE-v950-legacy-exit.md`
- migration / breaking / rollback：`docs/9.5.0/model-spi-v2-legacy-exit.md`
- acceptance：`docs/9.5.0/acceptance/version-signoff.md`
